import { afterEach, describe, expect, it, vi } from "vitest";
import {
  parseApiConfig as parseRawApiConfig,
  type Environment,
} from "../src/config.js";
import type { ServerDatabase } from "../src/database.js";
import type { DeviceAuthorization } from "../src/auth/device-auth.js";
import { startApiServer } from "../src/server.js";
import { WRITE_LEASE_SWEEP_INTERVAL_MS } from "../src/write-journal/lease-reaper.js";
import { ActualCompatibilityError } from "../src/actual/actual-session.js";

const deviceAuth = {} as DeviceAuthorization;
const testPairingCodeHashKey = "synthetic-pairing-code-hash-key-32-bytes";

function parseApiConfig(environment: Environment) {
  return parseRawApiConfig({
    PAIRING_CODE_HASH_KEY: testPairingCodeHashKey,
    ...environment,
  });
}

describe("API server lifecycle integration", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("serves readiness and closes dependencies on SIGTERM", async () => {
    const handlers = new Map<string, () => Promise<void>>();
    const registerSignalHandler = (
      signal: "SIGINT" | "SIGTERM",
      handler: () => Promise<void>,
    ) => {
      handlers.set(signal, handler);
    };
    const close = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
    const expireLeases = vi.fn<() => Promise<number>>().mockResolvedValue(0);
    const database: ServerDatabase = {
      ping: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      close,
      migrate: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      journal: { expireLeases },
      deviceAuth,
    };
    const config = {
      ...parseApiConfig({ DATABASE_URL: "postgres://local/test" }),
      port: 0,
      host: "127.0.0.1",
    };
    const app = await startApiServer(config, database, registerSignalHandler);

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/health/ready",
    });
    expect(response.statusCode).toBe(200);
    expect(handlers.has("SIGINT")).toBe(true);
    expect(expireLeases).toHaveBeenCalledOnce();

    await handlers.get("SIGTERM")?.();
    expect(close).toHaveBeenCalledOnce();
  });

  it("starts the configured runtime before listening and closes it on shutdown", async () => {
    const runtimeStart = vi
      .fn<() => Promise<void>>()
      .mockResolvedValue(undefined);
    const runtimeClose = vi
      .fn<() => Promise<void>>()
      .mockResolvedValue(undefined);
    const database: ServerDatabase = {
      ping: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      close: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      migrate: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      journal: {
        expireLeases: vi.fn<() => Promise<number>>().mockResolvedValue(0),
      },
      deviceAuth,
    };
    const config = {
      ...parseApiConfig({ DATABASE_URL: "postgres://local/test" }),
      port: 0,
      host: "127.0.0.1",
    };

    const app = await startApiServer(config, database, vi.fn(), {
      start: runtimeStart,
      close: runtimeClose,
    });
    expect(runtimeStart).toHaveBeenCalledOnce();

    await app.close();
    expect(runtimeClose).toHaveBeenCalledOnce();
  });

  it("reaps leases periodically and stops its timer when the server closes", async () => {
    vi.useFakeTimers();
    const expireLeases = vi.fn<() => Promise<number>>().mockResolvedValue(0);
    const database: ServerDatabase = {
      ping: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      close: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      migrate: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      journal: { expireLeases },
      deviceAuth,
    };
    const config = {
      ...parseApiConfig({ DATABASE_URL: "postgres://local/test" }),
      port: 0,
      host: "127.0.0.1",
    };

    const app = await startApiServer(config, database, vi.fn());
    expect(expireLeases).toHaveBeenCalledOnce();

    await vi.advanceTimersByTimeAsync(WRITE_LEASE_SWEEP_INTERVAL_MS);
    expect(expireLeases).toHaveBeenCalledTimes(2);

    await app.close();
    await vi.advanceTimersByTimeAsync(WRITE_LEASE_SWEEP_INTERVAL_MS);
    expect(expireLeases).toHaveBeenCalledTimes(2);
  });

  it("closes the database and fails startup when lease recovery fails", async () => {
    const recoveryError = new Error("synthetic lease recovery failure");
    const close = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
    const expireLeases = vi
      .fn<() => Promise<number>>()
      .mockRejectedValue(recoveryError);
    const database: ServerDatabase = {
      ping: vi.fn<() => Promise<void>>(),
      close,
      migrate: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      journal: { expireLeases },
      deviceAuth,
    };
    const config = {
      ...parseApiConfig({ DATABASE_URL: "postgres://local/test" }),
      port: 0,
      host: "127.0.0.1",
    };

    await expect(startApiServer(config, database, vi.fn())).rejects.toBe(
      recoveryError,
    );
    expect(expireLeases).toHaveBeenCalledOnce();
    expect(close).toHaveBeenCalledOnce();
  });

  it("closes the database when the listener cannot start", async () => {
    const close = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
    const database: ServerDatabase = {
      ping: vi.fn<() => Promise<void>>(),
      close,
      migrate: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      journal: {
        expireLeases: vi.fn<() => Promise<number>>().mockResolvedValue(0),
      },
      deviceAuth,
    };
    const config = {
      ...parseApiConfig({ DATABASE_URL: "postgres://local/test" }),
      port: 70000,
    };

    await expect(startApiServer(config, database, vi.fn())).rejects.toThrow();
    expect(close).toHaveBeenCalledOnce();
  });

  it("preserves listener errors and reports cleanup failures", async () => {
    const closeError = new Error("synthetic database close failure");
    const close = vi.fn<() => Promise<void>>().mockRejectedValue(closeError);
    const database: ServerDatabase = {
      ping: vi.fn<() => Promise<void>>(),
      close,
      migrate: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      journal: {
        expireLeases: vi.fn<() => Promise<number>>().mockResolvedValue(0),
      },
      deviceAuth,
    };
    const config = {
      ...parseApiConfig({ DATABASE_URL: "postgres://local/test" }),
      port: 70000,
    };

    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    const startupError = await startApiServer(config, database, vi.fn()).catch(
      (error: unknown) => error,
    );
    expect(startupError).toBeInstanceOf(Error);
    expect(startupError).not.toBe(closeError);
    expect(consoleError).toHaveBeenCalledWith("API startup cleanup failed", {
      errorType: "Error",
    });
    expect(close).toHaveBeenCalledOnce();
  });

  it("closes the database when startup migration fails", async () => {
    const migrationError = new Error("synthetic migration failure");
    const close = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
    const database: ServerDatabase = {
      ping: vi.fn<() => Promise<void>>(),
      close,
      migrate: vi.fn<() => Promise<void>>().mockRejectedValue(migrationError),
      journal: {
        expireLeases: vi.fn<() => Promise<number>>().mockResolvedValue(0),
      },
      deviceAuth,
    };
    const config = {
      ...parseApiConfig({ DATABASE_URL: "postgres://local/test" }),
      port: 0,
      host: "127.0.0.1",
    };

    await expect(startApiServer(config, database, vi.fn())).rejects.toBe(
      migrationError,
    );
    expect(close).toHaveBeenCalledOnce();
  });

  it("preserves the Actual startup error when runtime cleanup and logging fail", async () => {
    const startupError = new ActualCompatibilityError(
      "server-version-mismatch",
      "26.11.0",
      "27.0.0",
    );
    const closeError = new Error("synthetic runtime close failure");
    const database: ServerDatabase = {
      ping: vi.fn<() => Promise<void>>(),
      close: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      migrate: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      journal: {
        expireLeases: vi.fn<() => Promise<number>>().mockResolvedValue(0),
      },
      deviceAuth,
    };
    const config = {
      ...parseApiConfig({ DATABASE_URL: "postgres://local/test" }),
      port: 0,
      host: "127.0.0.1",
    };
    const runtimeClose = vi
      .fn<() => Promise<void>>()
      .mockRejectedValue(closeError);
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {
      throw new Error("synthetic logger failure");
    });

    await expect(
      startApiServer(config, database, vi.fn(), {
        start: () => Promise.reject(startupError),
        close: runtimeClose,
      }),
    ).rejects.toBe(startupError);
    expect(runtimeClose).toHaveBeenCalledOnce();
    expect(consoleError).toHaveBeenCalledWith("API startup cleanup failed", {
      errorType: "Error",
    });
  });

  it("preserves startup errors when database cleanup rejects with a non-Error", async () => {
    const startupError = new Error("synthetic runtime startup failure");
    const database: ServerDatabase = {
      ping: vi.fn<() => Promise<void>>(),
      close: vi
        .fn<() => Promise<void>>()
        .mockRejectedValue("synthetic database close failure"),
      migrate: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      journal: {
        expireLeases: vi.fn<() => Promise<number>>().mockResolvedValue(0),
      },
      deviceAuth,
    };
    const config = {
      ...parseApiConfig({ DATABASE_URL: "postgres://local/test" }),
      port: 0,
      host: "127.0.0.1",
    };
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});

    await expect(
      startApiServer(config, database, vi.fn(), {
        start: () => Promise.reject(startupError),
      }),
    ).rejects.toBe(startupError);
    expect(consoleError).toHaveBeenCalledWith("API startup cleanup failed", {
      errorType: "string",
    });
  });
});
