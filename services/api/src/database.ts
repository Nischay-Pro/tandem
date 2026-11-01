import { Pool } from "pg";
import type { Pool as PoolType } from "pg";
import { applyMigrations } from "./schema/migrator.js";
import type { DurableWriteJournal } from "./write-journal/contracts.js";
import { PostgresWriteJournal } from "./write-journal/postgres-write-journal.js";

export interface HealthDatabase {
  ping(): Promise<void>;
  close(): Promise<void>;
}

export interface ServerDatabase extends HealthDatabase {
  migrate(): Promise<void>;
}

export interface Database extends ServerDatabase {
  readonly journal: DurableWriteJournal;
  withBudgetLock<T>(budgetId: string, work: () => Promise<T>): Promise<T>;
}

export function createPostgresDatabase(connectionString: string): Database {
  const pool = new Pool({
    connectionString,
    max: 10,
    connectionTimeoutMillis: 5_000,
    idleTimeoutMillis: 30_000,
  });

  const journal = new PostgresWriteJournal(pool);

  return {
    async ping() {
      await pool.query("SELECT 1");
    },
    async close() {
      await pool.end();
    },
    async migrate() {
      await applyMigrations(pool);
    },
    journal,
    async withBudgetLock<T>(budgetId: string, work: () => Promise<T>) {
      return withBudgetLock(pool, budgetId, work);
    },
  };
}

export async function withBudgetLock<T>(
  pool: PoolType,
  budgetId: string,
  work: () => Promise<T>,
): Promise<T> {
  if (budgetId.trim().length === 0) {
    throw new Error("Budget ID is required for a write lock");
  }

  const client = await pool.connect();
  let lockAttempted = false;
  let lockAcquired = false;
  let workFailed = false;
  let workError: unknown;
  let result: T | undefined;
  let unlockFailed = false;
  let unlockError: unknown;
  let clientMustBeDestroyed = false;
  try {
    lockAttempted = true;
    await client.query("SELECT pg_advisory_lock(hashtextextended($1, 19753))", [
      `actual-budget-companion:${budgetId}`,
    ]);
    lockAcquired = true;
    result = await work();
  } catch (error) {
    workFailed = true;
    workError = error;
    if (lockAttempted && !lockAcquired) {
      // The server may have granted the session lock before the connection failed.
      clientMustBeDestroyed = true;
    }
  }

  try {
    if (lockAcquired) {
      const unlock = await client.query<{ unlocked: boolean }>(
        "SELECT pg_advisory_unlock(hashtextextended($1, 19753)) AS unlocked",
        [`actual-budget-companion:${budgetId}`],
      );
      if (unlock.rows[0]?.unlocked !== true) {
        throw new Error(
          "Could not confirm release of the budget advisory lock",
        );
      }
    }
  } catch (error) {
    unlockFailed = true;
    unlockError = error;
    clientMustBeDestroyed = true;
  } finally {
    client.release(clientMustBeDestroyed ? true : undefined);
  }

  if (workFailed && unlockFailed) {
    throw new AggregateError(
      [workError, unlockError],
      "Budget operation failed and its PostgreSQL lock could not be released",
    );
  }
  if (workFailed) {
    throw workError;
  }
  if (unlockFailed) {
    throw unlockError;
  }
  return result as T;
}
