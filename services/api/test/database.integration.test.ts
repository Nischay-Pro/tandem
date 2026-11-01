import { describe, expect, it, vi } from "vitest";
import { createPostgresDatabase, withBudgetLock } from "../src/database.js";
import { applyMigrations, MIGRATIONS } from "../src/schema/migrator.js";
import type { Pool } from "pg";

describe("PostgreSQL connection integration", () => {
  it("connects, executes the readiness query, and closes its pool", async () => {
    const connectionString = process.env.DATABASE_URL;
    if (!connectionString) {
      throw new Error(
        "DATABASE_URL is required; run this test with docker compose",
      );
    }

    const database = createPostgresDatabase(connectionString);
    await expect(database.ping()).resolves.toBeUndefined();
    await expect(database.close()).resolves.toBeUndefined();
  });

  it("releases the dedicated connection when acquiring the budget lock fails", async () => {
    const lockError = new Error("synthetic lock failure");
    const query = vi.fn().mockRejectedValue(lockError);
    const release = vi.fn();
    const pool = {
      connect: vi.fn().mockResolvedValue({ query, release }),
    } as unknown as Pool;

    await expect(
      withBudgetLock(pool, "synthetic-budget", async () => "unreachable"),
    ).rejects.toBe(lockError);
    expect(query).toHaveBeenCalledOnce();
    expect(release).toHaveBeenCalledWith(true);
  });

  it("evicts a connection when releasing the budget lock fails", async () => {
    const unlockError = new Error("synthetic unlock failure");
    const query = vi.fn().mockImplementation(async (sql: string) => {
      if (sql.includes("pg_advisory_unlock")) {
        throw unlockError;
      }
      return { rows: [], rowCount: 1 };
    });
    const release = vi.fn();
    const pool = {
      connect: vi.fn().mockResolvedValue({ query, release }),
    } as unknown as Pool;

    await expect(
      withBudgetLock(pool, "synthetic-budget", async () => "completed"),
    ).rejects.toBe(unlockError);
    expect(query).toHaveBeenCalledTimes(2);
    expect(release).toHaveBeenCalledWith(true);
  });

  it("preserves non-Error values thrown while releasing the budget lock", async () => {
    const query = vi.fn().mockImplementation(async (sql: string) => {
      if (sql.includes("pg_advisory_unlock")) {
        throw null;
      }
      return { rows: [], rowCount: 1 };
    });
    const release = vi.fn();
    const pool = {
      connect: vi.fn().mockResolvedValue({ query, release }),
    } as unknown as Pool;

    await expect(
      withBudgetLock(pool, "synthetic-budget", async () => "completed"),
    ).rejects.toBeNull();
    expect(release).toHaveBeenCalledWith(true);
  });

  it("preserves both the operation error and unlock error", async () => {
    const operationError = new Error("synthetic operation failure");
    const unlockError = new Error("synthetic unlock failure");
    const query = vi.fn().mockImplementation(async (sql: string) => {
      if (sql.includes("pg_advisory_unlock")) {
        throw unlockError;
      }
      return { rows: [], rowCount: 1 };
    });
    const release = vi.fn();
    const pool = {
      connect: vi.fn().mockResolvedValue({ query, release }),
    } as unknown as Pool;

    const result = withBudgetLock(pool, "synthetic-budget", async () => {
      throw operationError;
    });
    await expect(result).rejects.toMatchObject({
      errors: [operationError, unlockError],
    });
    expect(release).toHaveBeenCalledWith(true);
  });

  it("destroys a connection when PostgreSQL says the budget lock was not held", async () => {
    const query = vi
      .fn()
      .mockImplementation(async (sql: string) =>
        sql.includes("pg_advisory_unlock")
          ? { rows: [{ unlocked: false }], rowCount: 1 }
          : { rows: [], rowCount: 1 },
      );
    const release = vi.fn();
    const pool = {
      connect: vi.fn().mockResolvedValue({ query, release }),
    } as unknown as Pool;

    await expect(
      withBudgetLock(pool, "synthetic-budget", async () => "completed"),
    ).rejects.toThrow("Could not confirm release of the budget advisory lock");
    expect(release).toHaveBeenCalledWith(true);
  });

  it("releases the migration connection when the advisory lock cannot be acquired", async () => {
    const lockError = new Error("synthetic migration lock failure");
    const query = vi.fn().mockRejectedValue(lockError);
    const release = vi.fn();
    const pool = {
      connect: vi.fn().mockResolvedValue({ query, release }),
    } as unknown as Pool;
    const migrationName = MIGRATIONS[0]?.name;
    if (!migrationName) {
      throw new Error("Expected the single point-release migration");
    }

    await expect(
      applyMigrations(pool, MIGRATIONS, [migrationName]),
    ).rejects.toBe(lockError);
    expect(query).toHaveBeenCalledOnce();
    expect(release).toHaveBeenCalledWith(true);
  });

  it("destroys the migration connection when lock release is not confirmed", async () => {
    const query = vi.fn().mockImplementation(async (sql: string) => {
      if (sql.includes("pg_advisory_unlock")) {
        return { rows: [{ unlocked: false }], rowCount: 1 };
      }
      if (sql.includes("SELECT position")) {
        return { rows: [], rowCount: 0 };
      }
      return { rows: [], rowCount: 1 };
    });
    const release = vi.fn();
    const pool = {
      connect: vi.fn().mockResolvedValue({ query, release }),
    } as unknown as Pool;

    await expect(applyMigrations(pool, [], [])).rejects.toThrow(
      "Could not confirm release of the migration advisory lock",
    );
    expect(release).toHaveBeenCalledWith(true);
  });

  it("preserves a migration error and reports lock cleanup failure separately", async () => {
    const primaryError = new Error("migration inspection failed");
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    const query = vi.fn().mockImplementation(async (sql: string) => {
      if (sql.includes("SELECT position")) {
        throw primaryError;
      }
      if (sql.includes("pg_advisory_unlock")) {
        return { rows: [{ unlocked: false }], rowCount: 1 };
      }
      return { rows: [], rowCount: 1 };
    });
    const release = vi.fn();
    const pool = {
      connect: vi.fn().mockResolvedValue({ query, release }),
    } as unknown as Pool;

    await expect(applyMigrations(pool, [], [])).rejects.toBe(primaryError);
    expect(consoleError).toHaveBeenCalledWith(
      "Migration lock cleanup also failed",
      { primaryError: "Error", releaseError: "Error" },
    );
    expect(release).toHaveBeenCalledWith(true);
    consoleError.mockRestore();
  });
});
