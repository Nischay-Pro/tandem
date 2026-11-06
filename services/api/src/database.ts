import { Pool } from "pg";
import { DeviceAuthorization } from "./auth/device-auth.js";
import { PostgresDeviceAuthStore } from "./auth/postgres-device-auth-store.js";
import { READINESS_TIMEOUT_MS } from "./config.js";
import { withBudgetLock } from "./database/budget-lock.js";
import { applyMigrations } from "./schema/migrator.js";
import type { DurableWriteJournal } from "./write-journal/contracts.js";
import { PostgresWriteJournal } from "./write-journal/postgres-write-journal.js";
import { createPostgresBudgetProjectionStore } from "./budget-projection/postgres-budget-projection-store.js";
import type { BudgetProjectionStore } from "./budget-projection/contracts.js";

export interface HealthDatabase {
  ping(): Promise<void>;
  close(): Promise<void>;
}

export interface ServerDatabase extends HealthDatabase {
  readonly journal: Pick<DurableWriteJournal, "expireLeases">;
  readonly deviceAuth: DeviceAuthorization;
  migrate(): Promise<void>;
}

export interface Database extends ServerDatabase {
  readonly journal: DurableWriteJournal;
  readonly budgetProjection: BudgetProjectionStore;
  withBudgetLock<T>(budgetId: string, work: () => Promise<T>): Promise<T>;
}

export function createPostgresDatabase(
  connectionString: string,
  pairingCodeHashKey: string,
): Database {
  const pool = new Pool({
    connectionString,
    max: 10,
    connectionTimeoutMillis: 5_000,
    idleTimeoutMillis: 30_000,
  });
  const readinessPool = new Pool({
    connectionString,
    max: 1,
    connectionTimeoutMillis: READINESS_TIMEOUT_MS,
    idleTimeoutMillis: 30_000,
    query_timeout: READINESS_TIMEOUT_MS,
  });

  const journal = new PostgresWriteJournal(pool);
  const budgetProjection = createPostgresBudgetProjectionStore(pool);
  const deviceAuth = new DeviceAuthorization(
    new PostgresDeviceAuthStore(pool),
    undefined,
    undefined,
    undefined,
    { pairingCodeHashKey },
  );
  let readinessCheck: Promise<void> | undefined;

  return {
    ping() {
      if (!readinessCheck) {
        readinessCheck = readinessPool
          .query("SELECT 1")
          .then(() => undefined)
          .finally(() => {
            readinessCheck = undefined;
          });
      }
      return readinessCheck;
    },
    async close() {
      await Promise.all([pool.end(), readinessPool.end()]);
    },
    async migrate() {
      await applyMigrations(pool);
    },
    journal,
    budgetProjection,
    deviceAuth,
    async withBudgetLock<T>(budgetId: string, work: () => Promise<T>) {
      return withBudgetLock(pool, budgetId, work);
    },
  };
}
