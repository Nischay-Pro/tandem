import { describe, expect, it, vi } from "vitest";
import { parseApiConfig } from "../src/config.js";
import type { ServerDatabase } from "../src/database.js";
import { startApiServer } from "../src/server.js";

describe("API server lifecycle integration", () => {
  it("serves readiness and closes dependencies on SIGTERM", async () => {
    const handlers = new Map<string, () => Promise<void>>();
    const registerSignalHandler = (
      signal: "SIGINT" | "SIGTERM",
      handler: () => Promise<void>,
    ) => {
      handlers.set(signal, handler);
    };
    const close = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
    const database: ServerDatabase = {
      ping: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      close,
      migrate: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
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

    await handlers.get("SIGTERM")?.();
    expect(close).toHaveBeenCalledOnce();
  });

  it("closes the database when the listener cannot start", async () => {
    const close = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
    const database: ServerDatabase = {
      ping: vi.fn<() => Promise<void>>(),
      close,
      migrate: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
    };
    const config = {
      ...parseApiConfig({ DATABASE_URL: "postgres://local/test" }),
      port: 70000,
    };

    await expect(startApiServer(config, database, vi.fn())).rejects.toThrow();
    expect(close).toHaveBeenCalledOnce();
  });

  it("closes the database when startup migration fails", async () => {
    const migrationError = new Error("synthetic migration failure");
    const close = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
    const database: ServerDatabase = {
      ping: vi.fn<() => Promise<void>>(),
      close,
      migrate: vi.fn<() => Promise<void>>().mockRejectedValue(migrationError),
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
