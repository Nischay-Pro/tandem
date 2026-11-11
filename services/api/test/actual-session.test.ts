import { describe, expect, it, vi } from "vitest";
import {
  type ActualSecondaryFailure,
  ActualCompatibilityError,
  ActualPrincipalSessionExpiredError,
  ActualSession,
  SUPPORTED_ACTUAL_SERVER_VERSION,
  type ActualApiPort,
  type ActualConnectionConfig,
} from "../src/actual/actual-session.js";
import { runAsActualPrincipal } from "../src/auth/actual-principal-context.js";

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
  failInit(error: unknown): void;
  failShutdown(error?: Error): void;
  failDownload(error?: Error): void;
  failSync(error: unknown): void;
  setSyncTables(tables: string[] | undefined): void;
  setInitialized(value: unknown): void;
  setNextSyncEvent(event: unknown): void;
}

function createFakeClient(): FakeClient {
  const calls: string[] = [];
  let version: Awaited<ReturnType<ActualApiPort["getServerVersion"]>> = {
    version: SUPPORTED_ACTUAL_SERVER_VERSION,
  };
  let initError: unknown;
  let shutdownError: Error | undefined;
  let downloadError: Error | undefined;
  let syncError: unknown;
  let syncTables: string[] | undefined = [];
  let syncEventListener: ((event: unknown) => void) | undefined;
  let initialized: unknown;
  let hasNextSyncEvent = false;
  let nextSyncEvent: unknown;

  const api: ActualApiPort = {
    init: vi.fn(async () => {
      calls.push("init");
      if (initError) {
        throw initError;
      }
      return (
        initialized ?? {
          on: (eventName: string, listener: (event: unknown) => void) => {
            if (eventName === "sync-event") syncEventListener = listener;
          },
        }
      );
    }),
    getServerVersion: vi.fn(async () => {
      calls.push("version");
      return version;
    }),
    downloadBudget: vi.fn(async (syncId, options) => {
      calls.push(`download:${syncId}:${options?.password ?? ""}`);
      if (downloadError) {
        throw downloadError;
      }
    }),
    sync: vi.fn(async () => {
      calls.push("sync");
      if (syncError) {
        throw syncError;
      }
      if (hasNextSyncEvent) {
        hasNextSyncEvent = false;
        syncEventListener?.(nextSyncEvent);
        return;
      }
      if (syncTables !== undefined) {
        syncEventListener?.({ type: "success", tables: syncTables });
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
    failDownload(error) {
      downloadError = error;
    },
    failSync(error) {
      syncError = error;
    },
    setSyncTables(tables) {
      syncTables = tables;
    },
    setInitialized(value) {
      initialized = value;
    },
    setNextSyncEvent(event) {
      nextSyncEvent = event;
      hasNextSyncEvent = true;
    },
  };
}

describe("ActualSession", () => {
  it("keeps the API package version pinned to the tested release", () => {
    expect(SUPPORTED_ACTUAL_SERVER_VERSION).toBe("26.11.0");
  });

  it("requires a positive integer operation timeout", () => {
    const fake = createFakeClient();
    expect(
      () => new ActualSession(fake.api, config, undefined, undefined, 0),
    ).toThrow("Actual operation timeout must be a positive integer");
  });

  it("initializes once and verifies the supported server release", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);

    await session.start();
    await session.start();

    expect(fake.calls).toEqual(["init", "version"]);
    await session.close();
  });

  it("skips startup when no Actual credential is configured", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, {
      serverURL: config.serverURL,
      dataDir: config.dataDir,
    });

    await session.start();

    expect(fake.calls).toEqual([]);
    expect(session.isReady).toBe(false);
    await session.close();
  });

  it("marks a rejected session token unready and logs only a safe instruction", async () => {
    const fake = createFakeClient();
    fake.failInit(Object.assign(new Error("unauthorized"), { status: 401 }));
    const session = new ActualSession(fake.api, {
      serverURL: "https://actual.example.test",
      dataDir: "/tmp/actual-api",
      sessionToken: "private-session-token",
    });
    const log = vi.spyOn(console, "error").mockImplementation(() => {});

    try {
      await session.start();

      expect(session.isReady).toBe(false);
      expect(session.readinessMessage).toBe(
        "Actual rejected ACTUAL_SESSION_TOKEN. Replace the token and restart Tandem.",
      );
      expect(log).toHaveBeenCalledWith(session.readinessMessage);
      expect(JSON.stringify(log.mock.calls)).not.toContain(
        "private-session-token",
      );
    } finally {
      log.mockRestore();
      await session.close();
    }
  });

  it.each([
    ["HTTP 401", Object.assign(new Error("unauthorized"), { status: 401 })],
    [
      "HTTP 403 statusCode",
      Object.assign(new Error("forbidden"), { statusCode: 403 }),
    ],
    [
      "unauthorized reason",
      Object.assign(new Error("denied"), { reason: "unauthorized" }),
    ],
    [
      "invalid-token code",
      Object.assign(new Error("denied"), { code: "invalid-token" }),
    ],
    [
      "expired-token reason",
      Object.assign(new Error("denied"), { reason: "token-expired" }),
    ],
    [
      "nested unauthorized reason",
      Object.assign(new Error("denied"), { error: { reason: "unauthorized" } }),
    ],
    ["unauthorized message", new Error("unauthorized")],
    ["expired-token message", new Error("token expired")],
    ["rejected-token message", new Error("token rejected")],
    ["invalid-token message", new Error("token invalid")],
  ] as const)(
    "maps Actual's %s response to a safe operator error",
    async (_label, error) => {
      const fake = createFakeClient();
      fake.failInit(error);
      const session = new ActualSession(fake.api, {
        serverURL: "https://actual.example.test",
        dataDir: "/tmp/actual-api",
        sessionToken: "private-session-token",
      });
      const log = vi.spyOn(console, "error").mockImplementation(() => {});

      try {
        await session.start();

        expect(session.isReady).toBe(false);
        expect(session.readinessMessage).toBe(
          "Actual rejected ACTUAL_SESSION_TOKEN. Replace the token and restart Tandem.",
        );
        expect(log).toHaveBeenCalledOnce();
        expect(JSON.stringify(log.mock.calls)).not.toContain(
          "private-session-token",
        );
      } finally {
        log.mockRestore();
        await session.close();
      }
    },
  );

  it.each([
    ["plain text errors", "unauthorized"],
    [
      "unrelated HTTP error with non-text detail",
      Object.assign(new Error("unavailable"), {
        status: 400,
        message: 17,
        error: { reason: "unknown" },
      }),
    ],
  ] as const)(
    "preserves %s for session-token configuration",
    async (_label, error) => {
      const fake = createFakeClient();
      fake.failInit(error);
      const session = new ActualSession(fake.api, {
        serverURL: "https://actual.example.test",
        dataDir: "/tmp/actual-api",
        sessionToken: "private-session-token",
      });

      try {
        await expect(session.start()).rejects.toBe(error);
        expect(session.readinessMessage).toBeUndefined();
      } finally {
        await session.close();
      }
    },
  );

  it("marks readiness false when an active session token expires", async () => {
    const fake = createFakeClient();
    const sessionToken = "private-session-token";
    const session = new ActualSession(fake.api, {
      serverURL: "https://actual.example.test",
      dataDir: "/tmp/actual-api",
      sessionToken,
    });
    const log = vi.spyOn(console, "error").mockImplementation(() => {});

    try {
      await session.start();
      await session.runBudget("budget-a", async () => undefined);
      fake.failSync(
        Object.assign(new Error(`unauthorized ${sessionToken}`), {
          status: 401,
        }),
      );

      await expect(
        session.runBudget("budget-a", async () => undefined),
      ).rejects.toThrow(
        "Actual rejected ACTUAL_SESSION_TOKEN. Replace the token and restart Tandem.",
      );
      expect(session.isReady).toBe(false);
      expect(log).toHaveBeenCalledOnce();
      await expect(
        session.runBudget("budget-a", async () => undefined),
      ).rejects.toThrow(
        "Actual rejected ACTUAL_SESSION_TOKEN. Replace the token and restart Tandem.",
      );
      expect(log).toHaveBeenCalledOnce();
      expect(JSON.stringify(log.mock.calls)).not.toContain(sessionToken);
    } finally {
      log.mockRestore();
      await session.close();
    }
  });

  it("downloads a budget with its optional encryption password before the operation", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);
    const operation = vi.fn(async () => "read-result");

    await expect(
      session.runBudget("  budget-sync-id  ", operation, {
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

  it("uses incremental sync for an already-loaded unencrypted budget", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);

    await session.runBudget("budget-a", async () => undefined);
    await session.runBudget("budget-a", async () => undefined);
    await session.runBudget("budget-b", async () => undefined);
    await session.runBudget("budget-a", async () => undefined);

    expect(fake.calls).toEqual([
      "init",
      "version",
      "download:budget-a:",
      "sync",
      "download:budget-b:",
      "download:budget-a:",
    ]);
    await session.close();
  });

  it("reports whether incremental sync changed the budget", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);
    const changed: boolean[] = [];
    const read = (didChange: boolean) => {
      changed.push(didChange);
      return Promise.resolve("read");
    };

    await session.runBudgetSnapshot("budget-a", read);
    await session.runBudgetSnapshot("budget-a", read);
    fake.setSyncTables(["transactions"]);
    await session.runBudgetSnapshot("budget-a", read);
    fake.setSyncTables([]);
    await session.runBudget("budget-a", async () => "written", {
      synchronizeAfterWrite: true,
    });
    await session.runBudgetSnapshot("budget-a", read);
    await session.runBudgetSnapshot("budget-a", read);

    expect(changed).toEqual([true, false, true, true, false]);
    await session.close();
  });

  it("handles old initialization results and malformed sync events", async () => {
    const withoutListener = createFakeClient();
    withoutListener.setInitialized({});
    const oldClientSession = new ActualSession(withoutListener.api, config);
    const oldClientChanges: boolean[] = [];
    await oldClientSession.runBudgetSnapshot("budget-a", async (changed) => {
      oldClientChanges.push(changed);
      return undefined;
    });
    await oldClientSession.runBudgetSnapshot("budget-a", async (changed) => {
      oldClientChanges.push(changed);
      return undefined;
    });
    expect(oldClientChanges).toEqual([true, true]);
    await oldClientSession.close();

    const malformedEventClient = createFakeClient();
    const malformedEventSession = new ActualSession(
      malformedEventClient.api,
      config,
    );
    const malformedEventChanges: boolean[] = [];
    await malformedEventSession.runBudgetSnapshot(
      "budget-a",
      async (changed) => {
        malformedEventChanges.push(changed);
        return undefined;
      },
    );
    malformedEventClient.setNextSyncEvent({ type: 42, tables: [] });
    await malformedEventSession.runBudgetSnapshot(
      "budget-a",
      async (changed) => {
        malformedEventChanges.push(changed);
        return undefined;
      },
    );
    expect(malformedEventChanges).toEqual([true, true]);
    await malformedEventSession.close();
  });

  it("keeps only string table names from sync events", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);
    await session.runBudgetSnapshot("budget-a", async () => undefined);

    fake.setNextSyncEvent({ type: "success", tables: ["transactions", 42] });
    await session.runBudgetSnapshot("budget-a", async () => undefined);
    fake.setNextSyncEvent({ type: "success" });
    await session.runBudgetSnapshot("budget-a", async () => undefined);
    fake.setNextSyncEvent({ type: "success", tables: ["transactions"] });
    await session.runBudgetSnapshot("budget-a", async () => undefined);

    expect(fake.calls.filter((call) => call === "sync")).toHaveLength(3);
    await session.close();
  });

  it("revalidates password-opened budgets instead of reusing a decrypted copy", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);

    await session.runBudget("encrypted-budget", async () => undefined, {
      encryptionPassword: "budget-secret",
    });
    await session.runBudget("encrypted-budget", async () => undefined);
    await session.runBudget("encrypted-budget", async () => undefined, {
      encryptionPassword: "budget-secret",
    });

    expect(
      fake.calls.filter((call) =>
        call.startsWith("download:encrypted-budget:"),
      ),
    ).toEqual([
      "download:encrypted-budget:budget-secret",
      "download:encrypted-budget:",
      "download:encrypted-budget:budget-secret",
    ]);
    expect(fake.calls).not.toContain("sync");
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

  it("synchronizes only when the successful result says a write occurred", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);
    const shouldSynchronize = vi.fn((result: unknown) => result === "written");

    await expect(
      session.execute(async () => "written", {
        synchronizeAfterWrite: shouldSynchronize,
      }),
    ).resolves.toBe("written");
    await expect(
      session.execute(async () => "unchanged", {
        synchronizeAfterWrite: shouldSynchronize,
      }),
    ).resolves.toBe("unchanged");

    expect(shouldSynchronize).toHaveBeenNthCalledWith(1, "written");
    expect(shouldSynchronize).toHaveBeenNthCalledWith(2, "unchanged");
    expect(fake.calls.filter((call) => call === "sync")).toHaveLength(1);
    await session.close();
  });

  it("syncs after a synchronization-policy failure because the operation may have written", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);
    const policyError = new Error("sync policy failed");

    await expect(
      session.execute(async () => "possibly-written", {
        synchronizeAfterWrite: () => {
          throw policyError;
        },
      }),
    ).rejects.toBe(policyError);

    expect(fake.calls).toContain("sync");
    await session.close();
  });

  it("syncs after an operation failure with conditional synchronization enabled", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);
    const operationError = new Error("write may have partially succeeded");
    const policy = vi.fn(() => false);

    await expect(
      session.execute(
        async () => {
          throw operationError;
        },
        { synchronizeAfterWrite: policy },
      ),
    ).rejects.toBe(operationError);

    expect(policy).not.toHaveBeenCalled();
    expect(fake.calls).toContain("sync");
    await session.close();
  });

  it("preserves a synchronization-policy failure if the follow-up sync also fails", async () => {
    const fake = createFakeClient();
    const policyError = new Error("sync policy failed");
    const syncError = new Error("Actual sync failed");
    const secondaryFailures: ActualSecondaryFailure[] = [];
    const session = new ActualSession(fake.api, config, undefined, (failure) =>
      secondaryFailures.push(failure),
    );
    fake.failSync(syncError);

    await expect(
      session.execute(async () => "possibly-written", {
        synchronizeAfterWrite: () => {
          throw policyError;
        },
      }),
    ).rejects.toBe(policyError);

    expect(secondaryFailures).toEqual([
      {
        phase: "post-write-sync",
        primaryError: policyError,
        secondaryError: syncError,
      },
    ]);
    await session.close();
  });

  it("rejects blank budget IDs asynchronously before touching the Actual client", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);

    let result: Promise<unknown> | undefined;
    expect(() => {
      result = session.runBudget("  ", async () => undefined);
    }).not.toThrow();
    await expect(result).rejects.toThrow("Actual budget sync ID is required");
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

  it("synchronizes after a partially failed write and preserves its error", async () => {
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
    expect(fake.calls).toContain("sync");
    await session.close();
  });

  it("does not synchronize when loading a budget fails before the write", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);
    const operation = vi.fn(async () => undefined);
    fake.failDownload(new Error("download failed"));

    await expect(
      session.runBudget("budget-sync-id", operation, {
        synchronizeAfterWrite: true,
      }),
    ).rejects.toThrow("download failed");
    expect(operation).not.toHaveBeenCalled();
    expect(fake.calls).not.toContain("sync");
    await session.close();
  });

  it("fails closed when the server version cannot be checked", async () => {
    const fake = createFakeClient();
    fake.setVersion({ error: "network-failure" });
    const session = new ActualSession(fake.api, config);
    const startPromise = session.start();

    await expect(startPromise).rejects.toMatchObject<
      Partial<ActualCompatibilityError>
    >({
      kind: "version-check-failed",
      expectedVersion: SUPPORTED_ACTUAL_SERVER_VERSION,
    });
    await expect(startPromise).rejects.toThrow(
      "Could not verify the Actual server version (network-failure); this build requires 26.11.0",
    );
    expect(fake.calls).toEqual(["init", "version", "shutdown"]);
  });

  it("fails closed and reports a mismatched server version", async () => {
    const fake = createFakeClient();
    fake.setVersion({ version: "26.9.0" });
    const session = new ActualSession(fake.api, config);
    const startPromise = session.start();

    await expect(startPromise).rejects.toMatchObject({
      name: "ActualCompatibilityError",
      kind: "server-version-mismatch",
      expectedVersion: SUPPORTED_ACTUAL_SERVER_VERSION,
      actualVersion: "26.9.0",
    });
    await expect(startPromise).rejects.toThrow(
      "Unsupported Actual server version 26.9.0; this build supports 26.11.0",
    );
    expect(fake.calls).toEqual(["init", "version", "shutdown"]);
  });

  it("shuts down after initialization fails", async () => {
    const fake = createFakeClient();
    fake.failInit(new Error("authentication failed"));
    const session = new ActualSession(fake.api, config);

    await expect(session.start()).rejects.toThrow("authentication failed");
    expect(fake.calls).toEqual(["init", "shutdown"]);
  });

  it("preserves the compatibility error and reports cleanup failure", async () => {
    const fake = createFakeClient();
    fake.setVersion({ error: "no-server" });
    const shutdownError = new Error("shutdown failed");
    fake.failShutdown(shutdownError);
    const secondaryFailures: ActualSecondaryFailure[] = [];
    const session = new ActualSession(
      fake.api,
      config,
      undefined,
      (failure) => {
        secondaryFailures.push(failure);
      },
    );

    const startPromise = session.start();
    await expect(startPromise).rejects.toBeInstanceOf(ActualCompatibilityError);
    expect(secondaryFailures).toHaveLength(1);
    expect(secondaryFailures[0]).toMatchObject({
      phase: "initialization-cleanup",
      primaryError: expect.objectContaining({ kind: "version-check-failed" }),
      secondaryError: shutdownError,
    });
    await expect(session.start()).rejects.toThrow("cleanup previously failed");
    fake.failShutdown();
    await expect(session.close()).resolves.toBeUndefined();
    expect(fake.calls.filter((call) => call === "shutdown")).toHaveLength(2);
  });

  it("closes once and rejects work after close", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);
    await session.start();

    const firstClose = session.close();
    const secondClose = session.close();
    expect(secondClose).toBe(firstClose);
    await Promise.all([firstClose, secondClose]);

    expect(fake.calls).toEqual(["init", "version", "shutdown"]);
    await expect(session.execute(async () => undefined)).rejects.toThrow(
      "Actual session is closing",
    );
    await expect(session.start()).rejects.toThrow("Actual session is closing");
    await expect(
      session.runBudget("budget-sync-id", async () => undefined),
    ).rejects.toThrow("Actual session is closing");
  });

  it("does not shut down an uninitialized client", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);

    await session.close();

    expect(fake.calls).toEqual([]);
  });

  it("retains singleton ownership and allows cleanup retry after shutdown fails", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);
    await session.start();
    fake.failShutdown(new Error("shutdown failed"));

    await expect(session.close()).rejects.toThrow("shutdown failed");
    fake.failShutdown();
    await expect(session.close()).resolves.toBeUndefined();
    await expect(session.start()).rejects.toThrow("Actual session is closing");
    expect(fake.calls.filter((call) => call === "shutdown")).toHaveLength(2);
  });

  it("continues serial processing after a sync failure", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, config);
    const syncError = new Error("sync outcome uncertain");
    fake.failSync(syncError);

    await expect(
      session.execute(async () => "local-write", {
        synchronizeAfterWrite: true,
      }),
    ).rejects.toMatchObject({
      name: "ActualPostWriteSyncError",
      message:
        "Actual operation returned a result, but budget synchronization failed",
      kind: "post-write-sync-failed",
      operationResult: "local-write",
      syncError,
    });
    fake.failSync(new Error("sync outcome uncertain"));
    await expect(session.execute(async () => "read")).resolves.toBe("read");
    await session.close();
  });

  it("prevents two sessions from interleaving the process-wide Actual client", async () => {
    const firstFake = createFakeClient();
    const secondFake = createFakeClient();
    const firstSession = new ActualSession(firstFake.api, config);
    const secondSession = new ActualSession(secondFake.api, config);

    await firstSession.start();
    await expect(secondSession.start()).rejects.toThrow(
      "Another Actual session owns the process-wide API client",
    );
    expect(secondFake.calls).toEqual([]);

    await firstSession.close();
    await secondSession.start();
    expect(secondFake.calls).toEqual(["init", "version"]);
    await secondSession.close();
  });

  it("switches the serialized Actual client to the requesting account token", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(
      fake.api,
      { serverURL: config.serverURL, dataDir: config.dataDir },
      undefined,
      undefined,
      undefined,
      {
        sessionToken: async (principalId) => `${principalId}-session-token`,
        dataDir: async (principalId) => `/tmp/actual-api/${principalId}`,
      },
    );

    await runAsActualPrincipal("actual-user-a", () =>
      session.execute(async () => "user-a"),
    );
    await runAsActualPrincipal("actual-user-b", () =>
      session.execute(async () => "user-b"),
    );

    expect(fake.api.init).toHaveBeenNthCalledWith(1, {
      serverURL: config.serverURL,
      dataDir: "/tmp/actual-api/actual-user-a",
      sessionToken: "actual-user-a-session-token",
    });
    expect(fake.api.init).toHaveBeenNthCalledWith(2, {
      serverURL: config.serverURL,
      dataDir: "/tmp/actual-api/actual-user-b",
      sessionToken: "actual-user-b-session-token",
    });
    expect(fake.api.shutdown).toHaveBeenCalledOnce();
    await session.close();
  });

  it("restores the base session config when an operation has no principal", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(
      fake.api,
      { serverURL: config.serverURL, dataDir: config.dataDir },
      undefined,
      undefined,
      undefined,
      {
        sessionToken: async (principalId) => `${principalId}-session-token`,
        dataDir: async (principalId) => `/tmp/actual-api/${principalId}`,
      },
    );

    await runAsActualPrincipal("actual-user-a", () =>
      session.execute(async () => "user operation"),
    );
    expect(session.isReady).toBe(true);

    await expect(
      session.execute(async () => "anonymous operation"),
    ).resolves.toBe("anonymous operation");
    expect(session.isReady).toBe(false);
    expect(session.readinessMessage).toBe("Sign in to Actual to load budgets.");
    expect(fake.api.shutdown).toHaveBeenCalledOnce();

    await runAsActualPrincipal("actual-user-a", () =>
      session.execute(async () => "user operation again"),
    );
    expect(session.isReady).toBe(true);
    expect(fake.api.init).toHaveBeenCalledTimes(2);
    await session.close();
  });

  it("keeps the selected principal when the same account runs another operation", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(
      fake.api,
      { serverURL: config.serverURL, dataDir: config.dataDir },
      undefined,
      undefined,
      undefined,
      {
        sessionToken: async (principalId) => `${principalId}-session-token`,
        dataDir: async (principalId) => `/tmp/actual-api/${principalId}`,
      },
    );

    await runAsActualPrincipal("actual-user-a", () =>
      session.execute(async () => "first operation"),
    );
    await runAsActualPrincipal("actual-user-a", () =>
      session.execute(async () => "same principal"),
    );

    expect(fake.api.init).toHaveBeenCalledOnce();
    expect(fake.api.shutdown).not.toHaveBeenCalled();
    await session.close();
  });

  it("asks the principal to sign in again when no stored token exists", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(
      fake.api,
      { serverURL: config.serverURL, dataDir: config.dataDir },
      undefined,
      undefined,
      undefined,
      {
        sessionToken: async () => undefined,
        dataDir: async () => "/tmp/actual-api/actual-user-a",
      },
    );

    await expect(
      runAsActualPrincipal("actual-user-a", () =>
        session.execute(async () => "unreachable"),
      ),
    ).rejects.toBeInstanceOf(ActualPrincipalSessionExpiredError);
    expect(fake.calls).toEqual([]);
    expect(session.readinessMessage).toBeUndefined();
    await session.close();
  });

  it("rejects a principal when its account data directory is missing", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(
      fake.api,
      { serverURL: config.serverURL, dataDir: config.dataDir },
      undefined,
      undefined,
      undefined,
      {
        sessionToken: async () => "actual-user-a-session-token",
        dataDir: async () => "",
      },
    );

    await expect(
      runAsActualPrincipal("actual-user-a", () =>
        session.execute(async () => "unreachable"),
      ),
    ).rejects.toThrow("Actual account data directory is missing");
    expect(fake.calls).toEqual([]);
    expect(session.readinessMessage).toBeUndefined();
    await session.close();
  });

  it("does not turn a rejected principal token into a process readiness failure", async () => {
    const fake = createFakeClient();
    fake.failInit(Object.assign(new Error("unauthorized"), { status: 401 }));
    const session = new ActualSession(
      fake.api,
      { serverURL: config.serverURL, dataDir: config.dataDir },
      undefined,
      undefined,
      undefined,
      {
        sessionToken: async () => "expired-principal-session-token",
        dataDir: async () => "/tmp/actual-api/actual-user-a",
      },
    );

    await expect(
      runAsActualPrincipal("actual-user-a", () =>
        session.execute(async () => "unreachable"),
      ),
    ).rejects.toBeInstanceOf(ActualPrincipalSessionExpiredError);
    expect(session.readinessMessage).toBeUndefined();
    expect(fake.api.shutdown).toHaveBeenCalledOnce();
    await session.close();
  });

  it("can replace the session token on an active or closed session", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, {
      serverURL: config.serverURL,
      dataDir: config.dataDir,
      verbose: true,
      sessionToken: "initial-session-token",
    });

    await session.start();
    await expect(session.useSessionToken("  ")).rejects.toThrow(
      "Actual session token must not be empty",
    );
    await session.useSessionToken("replacement-session-token");
    expect(fake.api.init).toHaveBeenNthCalledWith(2, {
      serverURL: config.serverURL,
      dataDir: config.dataDir,
      verbose: true,
      sessionToken: "replacement-session-token",
    });

    await session.close();
    await session.useSessionToken("reopened-session-token");
    expect(fake.api.init).toHaveBeenNthCalledWith(3, {
      serverURL: config.serverURL,
      dataDir: config.dataDir,
      verbose: true,
      sessionToken: "reopened-session-token",
    });
    await session.close();
  });

  it("omits verbose config when replacing a token without verbose mode", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, {
      serverURL: config.serverURL,
      dataDir: config.dataDir,
      sessionToken: "initial-session-token",
    });

    await session.start();
    await session.useSessionToken("replacement-session-token");

    expect(fake.api.init).toHaveBeenNthCalledWith(2, {
      serverURL: config.serverURL,
      dataDir: config.dataDir,
      sessionToken: "replacement-session-token",
    });
    await session.close();
  });

  it("preserves verbose mode in a password connection", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, { ...config, verbose: true });

    await session.start();

    expect(fake.api.init).toHaveBeenCalledWith({ ...config, verbose: true });
    await session.close();
  });

  it("rejects a client configuration that mixes password and session token", async () => {
    const fake = createFakeClient();
    const session = new ActualSession(fake.api, {
      ...config,
      sessionToken: "session-token",
    });

    await expect(session.start()).rejects.toThrow(
      "Actual sign-in is required before accessing budgets",
    );
    expect(fake.calls).toEqual(["shutdown"]);
    await session.close();
  });

  it("reports write-sync failure without replacing a partially failed operation", async () => {
    const fake = createFakeClient();
    const primaryError = new Error("write failed");
    const syncError = new Error("sync failed");
    const secondaryFailures: ActualSecondaryFailure[] = [];
    const session = new ActualSession(
      fake.api,
      config,
      undefined,
      (failure) => {
        secondaryFailures.push(failure);
        throw new Error("reporter failed");
      },
    );
    fake.failSync(syncError);

    await expect(
      session.execute(
        async () => {
          throw primaryError;
        },
        { synchronizeAfterWrite: true },
      ),
    ).rejects.toBe(primaryError);
    expect(secondaryFailures).toEqual([
      {
        phase: "post-write-sync",
        primaryError,
        secondaryError: syncError,
      },
    ]);
    await session.close();
  });

  it("uses the default secondary-failure reporter without exposing error messages", async () => {
    const fake = createFakeClient();
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    const session = new ActualSession(fake.api, config);
    fake.failSync("private sync detail");
    const writeError = new Error("private write detail");

    await expect(
      session.execute(
        async () => {
          throw writeError;
        },
        { synchronizeAfterWrite: true },
      ),
    ).rejects.toBe(writeError);
    expect(consoleError).toHaveBeenCalledWith(
      "Actual session secondary failure",
      {
        phase: "post-write-sync",
        primaryError: "Error",
        secondaryError: "string",
      },
    );
    expect(JSON.stringify(consoleError.mock.calls)).not.toContain(
      "private sync detail",
    );
    expect(JSON.stringify(consoleError.mock.calls)).not.toContain(
      "private write detail",
    );
    consoleError.mockRestore();
    await session.close();
  });

  it("times out a stuck Actual call, releases the queue, and fails closed", async () => {
    vi.useFakeTimers();
    const fake = createFakeClient();
    const session = new ActualSession(
      fake.api,
      config,
      undefined,
      undefined,
      10,
    );
    let signalStarted: (() => void) | undefined;
    const operationStarted = new Promise<void>((resolve) => {
      signalStarted = resolve;
    });
    const stuck = session.execute(async () => {
      signalStarted?.();
      return new Promise<never>(() => {});
    });
    const queued = session.execute(async () => {
      fake.calls.push("queued-operation");
    });
    const queuedRejection = expect(queued).rejects.toThrow(
      "Actual session is unavailable after an operation timed out; restart the API process",
    );

    await operationStarted;
    const stuckRejection = expect(stuck).rejects.toMatchObject({
      name: "ActualSessionOperationTimeoutError",
      timeoutMs: 10,
    });
    await vi.advanceTimersByTimeAsync(10);
    await stuckRejection;
    await queuedRejection;

    expect(fake.calls).not.toContain("queued-operation");
    await expect(session.execute(async () => undefined)).rejects.toThrow(
      "Actual session is unavailable after an operation timed out; restart the API process",
    );
    await expect(
      session.useSessionToken("recovery-session-token"),
    ).rejects.toThrow(
      "Actual session is unavailable after an operation timed out; restart the API process",
    );
    const close = session.close();
    await expect(close).resolves.toBeUndefined();
    expect(fake.calls).not.toContain("shutdown");
  });
});
