import { readFile } from "node:fs/promises";
import { describe, expect, it, vi } from "vitest";
import {
  ActualCompatibilityError,
  ActualSession,
  SUPPORTED_ACTUAL_SERVER_VERSION,
  createActualSession,
  type ActualApiPort,
  type ActualConnectionConfig,
} from "../src/actual/actual-session.js";

const config: ActualConnectionConfig = {
  serverURL: "https://actual.example.test",
  dataDir: "/tmp/actual-api",
  password: "server-secret",
};

interface FakeClient {
  api: ActualApiPort;
  calls: string[];
  setVersion(
    result: Awaited<ReturnType<ActualApiPort["getServerVersion"]>>,
  ): void;
  failInit(error: Error): void;
  failShutdown(error: Error): void;
  failSync(error: Error): void;
}

function createFakeClient(): FakeClient {
  const calls: string[] = [];
  let version: Awaited<ReturnType<ActualApiPort["getServerVersion"]>> = {
    version: SUPPORTED_ACTUAL_SERVER_VERSION,
  };
  let initError: Error | undefined;
  let shutdownError: Error | undefined;
  let syncError: Error | undefined;

  const api: ActualApiPort = {
    init: vi.fn(async () => {
      calls.push("init");
      if (initError) {
        throw initError;
      }
    }),
    getServerVersion: vi.fn(async () => {
      calls.push("version");
      return version;
    }),
    downloadBudget: vi.fn(async (syncId, options) => {
      calls.push(`download:${syncId}:${options?.password ?? ""}`);
    }),
    sync: vi.fn(async () => {
      calls.push("sync");
      if (syncError) {
        throw syncError;
      }
    }),
    shutdown: vi.fn(async () => {
      calls.push("shutdown");
      if (shutdownError) {
        throw shutdownError;
      }
    }),
  };

  return {
    api,
    calls,
    setVersion(result) {
      version = result;
    },
    failInit(error) {
      initError = error;
    },
    failShutdown(error) {
      shutdownError = error;
    },
    failSync(error) {
      syncError = error;
    },
  };
}

describe("ActualSession", () => {
  it("pins the published API client and compatibility container to one release", async () => {
    const packageManifest = JSON.parse(
      await readFile(new URL("../package.json", import.meta.url), "utf8"),
    ) as { dependencies: Record<string, string> };
    const compose = await readFile(
      new URL("../../../docker-compose.dev.yml", import.meta.url),
      "utf8",
    );

    expect(packageManifest.dependencies["@actual-app/api"]).toBe(
      SUPPORTED_ACTUAL_SERVER_VERSION,
    );
    expect(compose).toContain(
      `image: actualbudget/actual-server:${SUPPORTED_ACTUAL_SERVER_VERSION}`,
    );
  });

  it("initializes once and verifies the supported server release", async () => {
    const fake = createFakeClient();
    const session = createActualSession(config, fake.api);

    await session.start();
    await session.start();

    expect(fake.calls).toEqual(["init", "version"]);
    await session.close();
  });

  it("downloads a budget with its optional encryption password before the operation", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);
    const operation = vi.fn(async () => "read-result");

    await expect(
      session.runBudget("budget-sync-id", operation, {
        encryptionPassword: "budget-secret",
      }),
    ).resolves.toBe("read-result");

    expect(fake.calls).toEqual([
      "init",
      "version",
      "download:budget-sync-id:budget-secret",
    ]);
    expect(operation).toHaveBeenCalledOnce();
    await session.close();
  });

  it("synchronizes a successful write after its operation", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);

    await session.runBudget(
      "budget-sync-id",
      async () => {
        fake.calls.push("write");
      },
      { synchronizeAfterWrite: true },
    );

    expect(fake.calls).toEqual([
      "init",
      "version",
      "download:budget-sync-id:",
      "write",
      "sync",
    ]);
    await session.close();
  });

  it("rejects blank budget IDs before touching the Actual client", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);

    expect(() => session.runBudget("  ", async () => undefined)).toThrow(
      "Actual budget sync ID is required",
    );
    expect(fake.calls).toEqual([]);
    await session.close();
  });

  it("serializes operations and continues after an operation rejects", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);
    let releaseFirst: (() => void) | undefined;
    let signalFirstStarted: (() => void) | undefined;
    const firstStarted = new Promise<void>((resolve) => {
      signalFirstStarted = resolve;
    });
    const firstOperation = new Promise<void>((resolve) => {
      releaseFirst = resolve;
    });

    const first = session.execute(async () => {
      fake.calls.push("first-start");
      signalFirstStarted?.();
      await firstOperation;
      fake.calls.push("first-end");
      throw new Error("first operation failed");
    });
    const second = session.execute(async () => {
      fake.calls.push("second");
    });

    await firstStarted;
    expect(fake.calls).toEqual(["init", "version", "first-start"]);
    releaseFirst?.();
    await expect(first).rejects.toThrow("first operation failed");
    await expect(second).resolves.toBeUndefined();
    expect(fake.calls).toEqual([
      "init",
      "version",
      "first-start",
      "first-end",
      "second",
    ]);
    await session.close();
  });

  it("does not synchronize when an operation fails", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);

    await expect(
      session.execute(
        async () => {
          throw new Error("write failed");
        },
        { synchronizeAfterWrite: true },
      ),
    ).rejects.toThrow("write failed");
    expect(fake.calls).not.toContain("sync");
    await session.close();
  });

  it("fails closed when the server version cannot be checked", async () => {
    const fake = createFakeClient();
    fake.setVersion({ error: "network-failure" });
    const session = new ActualSession(fake.api, config);

    await expect(session.start()).rejects.toMatchObject<
      Partial<ActualCompatibilityError>
    >({
      kind: "version-check-failed",
      expectedVersion: SUPPORTED_ACTUAL_SERVER_VERSION,
    });
    expect(fake.calls).toEqual(["init", "version", "shutdown"]);
  });

  it("fails closed and reports a mismatched server version", async () => {
    const fake = createFakeClient();
    fake.setVersion({ version: "26.9.0" });
    const session = new ActualSession(fake.api, config);

    await expect(session.start()).rejects.toMatchObject({
      name: "ActualCompatibilityError",
      kind: "server-version-mismatch",
      expectedVersion: SUPPORTED_ACTUAL_SERVER_VERSION,
      actualVersion: "26.9.0",
    });
    expect(fake.calls).toEqual(["init", "version", "shutdown"]);
  });

  it("shuts down after initialization fails", async () => {
    const fake = createFakeClient();
    fake.failInit(new Error("authentication failed"));
    const session = new ActualSession(fake.api, config);

    await expect(session.start()).rejects.toThrow("authentication failed");
    expect(fake.calls).toEqual(["init", "shutdown"]);
  });

  it("preserves both version and cleanup failures", async () => {
    const fake = createFakeClient();
    fake.setVersion({ error: "no-server" });
    fake.failShutdown(new Error("shutdown failed"));
    const session = new ActualSession(fake.api, config);

    await expect(session.start()).rejects.toMatchObject({
      name: "AggregateError",
      errors: [
        expect.objectContaining({ kind: "version-check-failed" }),
        new Error("shutdown failed"),
      ],
    });
  });

  it("closes once and rejects work after close", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);
    await session.start();

    await Promise.all([session.close(), session.close()]);

    expect(fake.calls).toEqual(["init", "version", "shutdown"]);
    expect(() => session.execute(async () => undefined)).toThrow(
      "Actual session is closing",
    );
  });

  it("does not shut down an uninitialized client", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);

    await session.close();

    expect(fake.calls).toEqual([]);
  });

  it("keeps the session closed even when shutdown fails", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);
    await session.start();
    fake.failShutdown(new Error("shutdown failed"));

    await expect(session.close()).rejects.toThrow("shutdown failed");
    await expect(session.close()).rejects.toThrow("shutdown failed");
    expect(() => session.start()).toThrow("Actual session is closing");
    expect(fake.calls.filter((call) => call === "shutdown")).toHaveLength(1);
  });

  it("continues serial processing after a sync failure", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);
    fake.failSync(new Error("sync outcome uncertain"));

    await expect(
      session.execute(async () => "local-write", {
        synchronizeAfterWrite: true,
      }),
    ).rejects.toThrow("sync outcome uncertain");
    fake.failSync(new Error("sync outcome uncertain"));
    await expect(session.execute(async () => "read")).resolves.toBe("read");
    await session.close();
  });
});
