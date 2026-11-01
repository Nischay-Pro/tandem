import { describe, expect, it, vi } from "vitest";
import { createPostgresDatabase } from "../src/database.js";
import { withBudgetLock } from "../src/database/budget-lock.js";
import { applyMigrations, MIGRATIONS } from "../src/schema/migrator.js";
import { Pool as PostgresPool } from "pg";
import type { Pool } from "pg";

function createLockTestPool() {
  const client = {
    query: vi
      .fn()
      .mockImplementation(async (sql: string) =>
        sql.includes("pg_advisory_unlock")
          ? { rows: [{ unlocked: true }], rowCount: 1 }
          : { rows: [], rowCount: 1 },
      ),
    release: vi.fn(),
  };
  const pool = {
    connect: vi.fn().mockResolvedValue(client),
  } as unknown as Pool;
  return { pool, client };
}

function deferred() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

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

  it("keeps same-budget lock waiters out of the PostgreSQL pool", async () => {
    const connectionString = process.env.DATABASE_URL;
    if (!connectionString) {
      throw new Error("DATABASE_URL is required to test budget lock queuing");
    }
    const pool = new PostgresPool({
      connectionString,
      max: 2,
      connectionTimeoutMillis: 250,
    });
    let signalFirst: () => void = () => {};
    let releaseFirst: () => void = () => {};
    const firstEntered = new Promise<void>((resolve) => {
      signalFirst = resolve;
    });
    const allowFirstToQuery = new Promise<void>((resolve) => {
      releaseFirst = resolve;
    });
    const budgetId = `pool-reservation-${Date.now()}`;

    const first = withBudgetLock(pool, budgetId, async () => {
      signalFirst();
      await allowFirstToQuery;
      await pool.query("SELECT 1");
    });
    await firstEntered;
    const second = withBudgetLock(pool, budgetId, async () => undefined);

    await expect(pool.query("SELECT 1")).resolves.toMatchObject({
      rowCount: 1,
    });
    releaseFirst();
    await Promise.all([first, second]);
    await pool.end();
  });

  it("caps concurrent budget locks and hands permits to queued work", async () => {
    const { pool } = createLockTestPool();
    let active = 0;
    let maximumActive = 0;
    const fourEntered = deferred();
    const allowWork = deferred();
    const jobs = Array.from({ length: 5 }, (_, index) =>
      withBudgetLock(pool, `permit-budget-${Date.now()}-${index}`, async () => {
        active += 1;
        maximumActive = Math.max(maximumActive, active);
        if (active === 4) {
          fourEntered.resolve();
        }
        await allowWork.promise;
        active -= 1;
      }),
    );

    await fourEntered.promise;
    await new Promise((resolve) => setTimeout(resolve, 10));
    expect(active).toBe(4);
    expect(pool.connect).toHaveBeenCalledTimes(4);
    allowWork.resolve();
    await Promise.all(jobs);
    expect(maximumActive).toBe(4);
  });

  it("keeps queued same-budget work linked when an earlier turn finishes", async () => {
    const { pool } = createLockTestPool();
    const firstEntered = deferred();
    const releaseFirst = deferred();
    const secondEntered = deferred();
    const releaseSecond = deferred();
    const thirdEntered = deferred();
    const releaseThird = deferred();
    const entries: string[] = [];
    const budgetId = `local-queue-${Date.now()}`;

    const first = withBudgetLock(pool, budgetId, async () => {
      entries.push("first");
      firstEntered.resolve();
      await releaseFirst.promise;
    });
    await firstEntered.promise;
    const second = withBudgetLock(pool, budgetId, async () => {
      entries.push("second");
      secondEntered.resolve();
      await releaseSecond.promise;
    });

    releaseFirst.resolve();
    await secondEntered.promise;
    const third = withBudgetLock(pool, budgetId, async () => {
      entries.push("third");
      thirdEntered.resolve();
      await releaseThird.promise;
    });
    await new Promise((resolve) => setTimeout(resolve, 10));
    expect(entries).toEqual(["first", "second"]);

    releaseSecond.resolve();
    await thirdEntered.promise;
    expect(entries).toEqual(["first", "second", "third"]);
    releaseThird.resolve();
    await Promise.all([first, second, third]);
  });

  it("returns the work result and releases a confirmed lock to the pool", async () => {
    const { pool, client } = createLockTestPool();

    await expect(
      withBudgetLock(pool, "successful-budget", async () => "saved"),
    ).resolves.toBe("saved");
    expect(client.query).toHaveBeenCalledTimes(2);
    expect(client.release).toHaveBeenCalledWith(undefined);
  });

  it("preserves a failed operation and reuses its healthy connection", async () => {
    const { pool, client } = createLockTestPool();
    const operationError = new Error("synthetic work failure");

    await expect(
      withBudgetLock(pool, "failed-work-budget", async () => {
        throw operationError;
      }),
    ).rejects.toBe(operationError);
    expect(client.query).toHaveBeenCalledTimes(2);
    expect(client.release).toHaveBeenCalledWith(undefined);
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
      message:
        "Budget operation failed and its PostgreSQL lock could not be released",
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
    expect(consoleError).toHaveBeenCalledWith("Migration cleanup also failed", {
      primaryError: "Error",
      cleanupError: "Error",
    });
    expect(release).toHaveBeenCalledWith(true);
    consoleError.mockRestore();
  });
});
