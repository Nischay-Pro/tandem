import { Pool } from "pg";
import { withBudgetLock } from "./database/budget-lock.js";
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
