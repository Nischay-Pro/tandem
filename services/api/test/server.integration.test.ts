import { describe, expect, it, vi } from "vitest";
import { parseApiConfig } from "../src/config.js";
import type { Database } from "../src/database.js";
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
    const database: Database = {
      ping: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      close,
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
    const database: Database = {
      ping: vi.fn<() => Promise<void>>(),
      close,
    };
    const config = {
      ...parseApiConfig({ DATABASE_URL: "postgres://local/test" }),
      port: 70000,
    };

    await expect(startApiServer(config, database, vi.fn())).rejects.toThrow();
    expect(close).toHaveBeenCalledOnce();
  });
});
