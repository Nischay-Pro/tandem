import { afterEach, describe, expect, it, vi } from "vitest";
import { parseApiConfig } from "../src/config.js";
import type { ServerDatabase } from "../src/database.js";
import type { DeviceAuthorization } from "../src/auth/device-auth.js";
import { startApiServer } from "../src/server.js";
import { WRITE_LEASE_SWEEP_INTERVAL_MS } from "../src/write-journal/lease-reaper.js";

const deviceAuth = {} as DeviceAuthorization;

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

  it("preserves listener and cleanup errors when both fail", async () => {
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

    await expect(
      startApiServer(config, database, vi.fn()),
    ).rejects.toMatchObject({
      errors: [expect.any(Error), closeError],
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

  it("preserves startup and cleanup errors when both fail", async () => {
    const migrationError = new Error("synthetic migration failure");
    const closeError = new Error("synthetic database close failure");
    const database: ServerDatabase = {
      ping: vi.fn<() => Promise<void>>(),
      close: vi.fn<() => Promise<void>>().mockRejectedValue(closeError),
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

    await expect(
      startApiServer(config, database, vi.fn()),
    ).rejects.toMatchObject({ errors: [migrationError, closeError] });
  });
});
