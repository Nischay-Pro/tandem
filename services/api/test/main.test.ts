import { createPool, run } from "../src/main.js";
import { describe, expect, it, vi } from "vitest";

describe("startup", () => {
  it("bootstraps the schema, listens and stops on a signal", async () => {
    const query = vi.fn().mockResolvedValue({ rows: [] });
    const end = vi.fn().mockResolvedValue(undefined);
    const handlers = new Map<string, () => void>();
    const app = await run(
      {
        DATABASE_URL: "postgresql://db",
        ACTUAL_SERVER_URL: "https://actual.example",
        HOST: "127.0.0.1",
        PORT: "39847",
      },
      (signal, handler) => handlers.set(signal, handler),
      () => ({ query, end }) as never,
    );
    expect(query).toHaveBeenCalledWith(
      expect.stringContaining("CREATE TABLE IF NOT EXISTS planning_records"),
    );
    expect([...handlers.keys()]).toEqual(["SIGINT", "SIGTERM"]);
    expect(
      (await app.inject({ url: "/extension/v1/capabilities" })).statusCode,
    ).toBe(200);
    handlers.get("SIGTERM")!();
    await vi.waitFor(() => expect(end).toHaveBeenCalled());
    await expect(run({}, () => undefined)).rejects.toThrow("DATABASE_URL");
    const pool = createPool("postgresql://user@127.0.0.1:1/db");
    expect(pool.options.connectionString).toBe(
      "postgresql://user@127.0.0.1:1/db",
    );
    const stderr = vi
      .spyOn(process.stderr, "write")
      .mockImplementation(() => true);
    pool.emit("error", new Error("connection reset"));
    expect(stderr).toHaveBeenCalledWith(
      "Idle database connection failed: connection reset\n",
    );
    stderr.mockRestore();
    await pool.end();
  });
});
