import { randomUUID } from "node:crypto";
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { Pool } from "pg";
import type { Pool as PoolType } from "pg";
import {
  TransactionReconciler,
  type ActualBudgetSessionPort,
} from "../src/actual/transaction-reconciler.js";
import {
  createTransactionRevision,
  type TransactionSnapshot,
} from "../src/actual/transaction-reconciliation.js";
import type { ActualTransactionStore } from "../src/actual/transaction-store.js";
import { createPostgresDatabase, type Database } from "../src/database.js";
import {
  MIGRATIONS,
  applyMigrations,
  readMigrationOrder,
  validateMigrationOrder,
  type Migration,
} from "../src/schema/migrator.js";
import type {
  ActualWriteEvidence,
  ConflictInput,
  JsonValue,
  WriteOperationInput,
} from "../src/write-journal/contracts.js";
import { WriteLeaseReaper } from "../src/write-journal/lease-reaper.js";
import {
  MAX_TERMINAL_OPERATION_PAYLOADS_PER_BATCH,
  MAX_TERMINAL_OPERATION_PAYLOAD_BATCHES_PER_SWEEP,
  PostgresWriteJournal,
} from "../src/write-journal/postgres-write-journal.js";

let database: Database;
let inspectionPool: Pool;
let adminPool: Pool;
let writeJournalSchema: string;

function createOperation(
  overrides: Partial<WriteOperationInput> = {},
): WriteOperationInput {
  return {
    operationId: randomUUID(),
    principalId: `device-${randomUUID()}`,
    budgetId: `synthetic-budget-${randomUUID()}`,
    idempotencyKey: `request-${randomUUID()}`,
    entityType: "transaction",
    action: "update",
    entityId: "synthetic-transaction-1",
    baseRevision: "revision-1",
    baseSnapshot: { id: "synthetic-transaction-1", amount: 1500 },
    patch: { amount: 1200 },
    ...overrides,
  };
}

function createConflict(overrides: Partial<ConflictInput> = {}): ConflictInput {
  return {
    conflictId: randomUUID(),
    conflictType: "same-field-edit",
    baseSnapshot: { id: "synthetic-transaction-1", amount: 1500 },
    localPatch: { amount: 1200 },
    remoteSnapshot: { id: "synthetic-transaction-1", amount: 1800 },
    conflictingFields: ["amount"],
    ...overrides,
  };
}

function deferred(): { promise: Promise<void>; resolve: () => void } {
  let resolve: () => void = () => {};
  const promise = new Promise<void>((complete) => {
    resolve = complete;
  });
  return { promise, resolve };
}

describe("durable write journal integration", () => {
  beforeAll(async () => {
    const connectionString = process.env.DATABASE_URL;
    if (!connectionString) {
      throw new Error(
        "DATABASE_URL is required; run this test with docker compose",
      );
    }
    writeJournalSchema = `write_journal_${randomUUID().replaceAll("-", "")}`;
    adminPool = new Pool({ connectionString, max: 2 });
    await adminPool.query(`CREATE SCHEMA ${writeJournalSchema}`);
    const searchPath = `-c search_path=${writeJournalSchema}`;
    const isolatedConnection = new URL(connectionString);
    isolatedConnection.searchParams.set("options", searchPath);
    database = createPostgresDatabase(
      isolatedConnection.toString(),
      process.env.AUTH_TOKEN_ENCRYPTION_KEY ??
        "synthetic-session-token-encryption-key-32",
    );
    inspectionPool = new Pool({
      connectionString,
      max: 3,
      options: searchPath,
    });
    await database.migrate();
  }, 30_000);

  afterAll(async () => {
    await inspectionPool?.end();
    await database?.close();
    await adminPool?.query(`DROP SCHEMA ${writeJournalSchema} CASCADE`);
    await adminPool?.end();
  });

  it("applies ordered schema migrations idempotently", async () => {
    await database.migrate();
    const order = await readMigrationOrder();
    const result = await inspectionPool.query<{ count: string }>(
      "SELECT count(*)::text AS count FROM companion_migrations",
    );
    expect(order).toHaveLength(MIGRATIONS.length);
    expect(order).toEqual(MIGRATIONS.map(({ name }) => name));
    expect(result.rows[0]?.count).toBe(String(MIGRATIONS.length));
    const retentionColumn = await inspectionPool.query<{
      found_column: string | null;
    }>(
      `SELECT column_name AS found_column FROM information_schema.columns
       WHERE table_schema = current_schema()
         AND table_name = 'companion_operations'
         AND column_name = 'payload_compacted_at'`,
    );
    expect(retentionColumn.rows[0]?.found_column).toBe("payload_compacted_at");
  });

  it("rejects an earlier pre-release identity without skipping the expanded schema", async () => {
    const connectionString = process.env.DATABASE_URL;
    if (!connectionString) {
      throw new Error("Migration test configuration is incomplete");
    }
    const schemaName = `migration_pre_release_${randomUUID().replaceAll("-", "")}`;
    await inspectionPool.query(`CREATE SCHEMA ${schemaName}`);
    const isolatedPool = new Pool({
      connectionString,
      max: 2,
      options: `-c search_path=${schemaName}`,
    });

    try {
      await isolatedPool.query(`CREATE TABLE companion_migrations (
        position integer PRIMARY KEY CHECK (position > 0),
        name text NOT NULL UNIQUE,
        applied_at timestamptz NOT NULL DEFAULT clock_timestamp()
      )`);
      await isolatedPool.query(
        "INSERT INTO companion_migrations (position, name) VALUES (1, $1)",
        ["20261031120000-durable-write-journal"],
      );

      await expect(applyMigrations(isolatedPool)).rejects.toThrow(
        /pre-release Tandem database/i,
      );
      const tables = await isolatedPool.query<{
        devices: string | null;
        operations: string | null;
      }>(`SELECT to_regclass('companion_devices') AS devices,
        to_regclass('companion_operations') AS operations`);
      expect(tables.rows[0]).toEqual({
        devices: null,
        operations: null,
      });
    } finally {
      await isolatedPool.end();
      await inspectionPool.query(`DROP SCHEMA ${schemaName} CASCADE`);
    }
  });

  it("validates migration manifest ordering", async () => {
    const migration = MIGRATIONS[0];
    if (!migration) {
      throw new Error("Migration test configuration is incomplete");
    }
    expect(() =>
      validateMigrationOrder([migration], [migration.name]),
    ).not.toThrow();
    expect(() =>
      validateMigrationOrder([migration, migration], [migration.name]),
    ).toThrow("duplicate names");
    expect(() =>
      validateMigrationOrder([migration], [migration.name, migration.name]),
    ).toThrow("ORDER contains duplicate");
    expect(() => validateMigrationOrder([], [migration.name])).toThrow(
      "do not match",
    );
    expect(() => validateMigrationOrder([migration], ["unexpected"])).toThrow(
      "ORDER mismatch",
    );
  });

  it("runs and rolls back an ordered migration transactionally in an isolated schema", async () => {
    const connectionString = process.env.DATABASE_URL;
    if (!connectionString) {
      throw new Error("DATABASE_URL disappeared during migration test");
    }
    const schemaName = `migration_test_${randomUUID().replaceAll("-", "")}`;
    await inspectionPool.query(`CREATE SCHEMA ${schemaName}`);
    const isolatedPool = new Pool({
      connectionString,
      max: 2,
      options: `-c search_path=${schemaName}`,
    });
    const brokenMigration: Migration = {
      name: "20261031120001-synthetic-rollback-probe",
      async up(client) {
        await client.query(
          "CREATE TABLE companion_rollback_probe (id integer)",
        );
        throw new Error("synthetic migration failure");
      },
      async down() {
        await Promise.resolve();
      },
    };

    try {
      await expect(
        applyMigrations(
          isolatedPool,
          [brokenMigration],
          [brokenMigration.name],
        ),
      ).rejects.toThrow("synthetic migration failure");
      const result = await isolatedPool.query<{ relation: string | null }>(
        "SELECT to_regclass('companion_rollback_probe') AS relation",
      );
      expect(result.rows[0]?.relation).toBeNull();
      const history = await isolatedPool.query<{ count: string }>(
        "SELECT count(*)::text AS count FROM companion_migrations",
      );
      expect(history.rows[0]?.count).toBe("0");
    } finally {
      await isolatedPool.end();
      await inspectionPool.query(`DROP SCHEMA ${schemaName} CASCADE`);
    }
  });

  it("can reverse and reapply the single initial schema in an isolated schema", async () => {
    const connectionString = process.env.DATABASE_URL;
    const migration = MIGRATIONS[0];
    if (!connectionString || !migration) {
      throw new Error("Migration test configuration is incomplete");
    }
    const schemaName = `migration_revert_${randomUUID().replaceAll("-", "")}`;
    await inspectionPool.query(`CREATE SCHEMA ${schemaName}`);
    const isolatedPool = new Pool({
      connectionString,
      max: 2,
      options: `-c search_path=${schemaName}`,
    });

    try {
      await applyMigrations(isolatedPool, [migration], [migration.name]);
      const before = await isolatedPool.query<{ relation: string | null }>(
        "SELECT to_regclass('companion_operations') AS relation",
      );
      expect(before.rows[0]?.relation).toBe("companion_operations");
      const deviceTable = await isolatedPool.query<{
        relation: string | null;
      }>("SELECT to_regclass('companion_devices') AS relation");
      const actualSessionTable = await isolatedPool.query<{
        relation: string | null;
      }>("SELECT to_regclass('companion_actual_sessions') AS relation");
      expect(deviceTable.rows[0]?.relation).toBe("companion_devices");
      expect(actualSessionTable.rows[0]?.relation).toBe(
        "companion_actual_sessions",
      );

      const client = await isolatedPool.connect();
      try {
        await client.query("BEGIN");
        await migration.down(client);
        await client.query("DELETE FROM companion_migrations WHERE name = $1", [
          migration.name,
        ]);
        await client.query("COMMIT");
      } catch (error) {
        await client.query("ROLLBACK");
        throw error;
      } finally {
        client.release();
      }

      const after = await isolatedPool.query<{ relation: string | null }>(
        "SELECT to_regclass('companion_operations') AS relation",
      );
      expect(after.rows[0]?.relation).toBeNull();
      const afterDevice = await isolatedPool.query<{
        relation: string | null;
      }>("SELECT to_regclass('companion_devices') AS relation");
      const afterActualSession = await isolatedPool.query<{
        relation: string | null;
      }>("SELECT to_regclass('companion_actual_sessions') AS relation");
      expect(afterDevice.rows[0]?.relation).toBeNull();
      expect(afterActualSession.rows[0]?.relation).toBeNull();
      await applyMigrations(isolatedPool, [migration], [migration.name]);
      const reapplied = await isolatedPool.query<{ relation: string | null }>(
        "SELECT to_regclass('companion_operations') AS relation",
      );
      expect(reapplied.rows[0]?.relation).toBe("companion_operations");
    } finally {
      await isolatedPool.end();
      await inspectionPool.query(`DROP SCHEMA ${schemaName} CASCADE`);
    }
  });

  it("fails closed when stored migration history diverges from the ORDER manifest", async () => {
    const connectionString = process.env.DATABASE_URL;
    const migration = MIGRATIONS[0];
    if (!connectionString || !migration) {
      throw new Error("Migration test configuration is incomplete");
    }
    const schemaName = `migration_order_${randomUUID().replaceAll("-", "")}`;
    await inspectionPool.query(`CREATE SCHEMA ${schemaName}`);
    const isolatedPool = new Pool({
      connectionString,
      max: 2,
      options: `-c search_path=${schemaName}`,
    });

    try {
      await applyMigrations(isolatedPool, [migration], [migration.name]);
      await isolatedPool.query(
        "UPDATE companion_migrations SET name = 'unexpected-migration'",
      );
      await expect(
        applyMigrations(isolatedPool, [migration], [migration.name]),
      ).rejects.toThrow("Tandem migration history mismatch");

      await isolatedPool.query("UPDATE companion_migrations SET name = $1", [
        "sensitive migration name",
      ]);
      await expect(
        applyMigrations(isolatedPool, [migration], [migration.name]),
      ).rejects.toThrow("found [invalid migration name]");

      await isolatedPool.query(
        "UPDATE companion_migrations SET name = $1, position = 2",
        [migration.name],
      );
      await expect(
        applyMigrations(isolatedPool, [migration], [migration.name]),
      ).rejects.toThrow("Corrupted migrations");

      await isolatedPool.query("UPDATE companion_migrations SET position = 1");
      await isolatedPool.query(
        "INSERT INTO companion_migrations (position, name) VALUES (2, 'stale-migration')",
      );
      await expect(
        applyMigrations(isolatedPool, [migration], [migration.name]),
      ).rejects.toThrow("Tandem database has migrations newer than this API");

      await isolatedPool.query(
        "UPDATE companion_migrations SET position = 3 WHERE name = 'stale-migration'",
      );
      await expect(
        applyMigrations(isolatedPool, [migration], [migration.name]),
      ).rejects.toThrow("expected migration <end>");
    } finally {
      await isolatedPool.end();
      await inspectionPool.query(`DROP SCHEMA ${schemaName} CASCADE`);
    }
  });

  it("persists an operation before work and returns the original on an idempotent retry", async () => {
    const input = createOperation({
      baseSnapshot: { amount: 1500, notes: null },
      patch: { amount: 1200, notes: "offline edit" },
    });
    const first = await database.journal.enqueue(input);
    const retry = await database.journal.enqueue({
      ...input,
      operationId: randomUUID(),
      baseSnapshot: { notes: null, amount: 1500 },
      patch: { notes: "offline edit", amount: 1200 },
    });

    expect(first.replayed).toBe(false);
    expect(first.operation.status).toBe("queued");
    expect(retry.replayed).toBe(true);
    expect(retry.operation.operationId).toBe(input.operationId);
    expect(retry.operation.requestHash).toMatch(/^[a-f0-9]{64}$/);
    await expect(
      database.journal.get(input.operationId),
    ).resolves.toMatchObject({
      operationId: input.operationId,
      budgetId: input.budgetId,
      patch: { amount: 1200, notes: "offline edit" },
    });
  });

  it("recovers the durable pending operation after a service restart", async () => {
    const connectionString = process.env.DATABASE_URL;
    if (!connectionString) {
      throw new Error("DATABASE_URL is required to simulate a restart");
    }
    const input = createOperation();
    const isolatedConnection = new URL(connectionString);
    isolatedConnection.searchParams.set(
      "options",
      `-c search_path=${writeJournalSchema}`,
    );
    const beforeRestart = createPostgresDatabase(
      isolatedConnection.toString(),
      process.env.AUTH_TOKEN_ENCRYPTION_KEY ??
        "synthetic-session-token-encryption-key-32",
    );
    try {
      await beforeRestart.journal.enqueue(input);
    } finally {
      await beforeRestart.close();
    }

    const afterRestart = createPostgresDatabase(
      isolatedConnection.toString(),
      process.env.AUTH_TOKEN_ENCRYPTION_KEY ??
        "synthetic-session-token-encryption-key-32",
    );
    try {
      await expect(
        afterRestart.journal.get(input.operationId),
      ).resolves.toMatchObject({
        operationId: input.operationId,
        status: "queued",
        attemptCount: 0,
      });
    } finally {
      await afterRestart.close();
    }
  });

  it("rejects idempotency-key reuse when the requested financial patch changes", async () => {
    const input = createOperation();
    await database.journal.enqueue(input);

    await expect(
      database.journal.enqueue({ ...input, patch: { amount: 100 } }),
    ).rejects.toThrow("Idempotency key was already used");
  });

  it("rejects an operation ID reused with a different idempotency key", async () => {
    const input = createOperation();
    await database.journal.enqueue(input);

    await expect(
      database.journal.enqueue({
        ...input,
        idempotencyKey: `different-${randomUUID()}`,
      }),
    ).rejects.toThrow("Operation ID was already used");
  });

  it("reports an invariant error if a uniqueness conflict has no matching operation", async () => {
    const query = vi.fn(async () => ({ rows: [], rowCount: 0 }));
    const release = vi.fn();
    const pool = {
      connect: vi.fn().mockResolvedValue({ query, release }),
    } as unknown as PoolType;
    const journal = new PostgresWriteJournal(pool);

    await expect(journal.enqueue(createOperation())).rejects.toThrow(
      "uniqueness constraint failed without a matching row",
    );
    expect(query).toHaveBeenCalledTimes(4);
    expect(release).toHaveBeenCalledOnce();
  });

  it("treats an absent PostgreSQL affected-row count as zero expired leases", async () => {
    const pool = {
      query: vi.fn().mockResolvedValue({ rows: [], rowCount: null }),
    } as unknown as PoolType;
    const journal = new PostgresWriteJournal(pool);

    await expect(journal.expireLeases()).resolves.toBe(0);
  });

  it("rejects a retry that collides with two different stored identities", async () => {
    const first = createOperation();
    const second = createOperation({
      principalId: `other-device-${randomUUID()}`,
      budgetId: `other-budget-${randomUUID()}`,
    });
    await database.journal.enqueue(first);
    await database.journal.enqueue(second);

    await expect(
      database.journal.enqueue({
        ...first,
        operationId: second.operationId,
      }),
    ).rejects.toThrow("Operation ID was already used");
  });

  it("claims queued work once and commits idempotently without replaying the write", async () => {
    const input = createOperation();
    await database.journal.enqueue(input);

    const firstClaim = await database.journal.claim(input.operationId);
    const repeatedClaim = await database.journal.claim(input.operationId);
    expect(firstClaim.claimed).toBe(true);
    expect(firstClaim.operation.status).toBe("applying");
    expect(firstClaim.operation.attemptCount).toBe(1);
    expect(repeatedClaim.claimed).toBe(false);
    expect(repeatedClaim.operation.status).toBe("applying");
    await expect(database.journal.renewLease(input.operationId)).resolves.toBe(
      true,
    );

    const committed = await database.journal.markCommitted(input.operationId, {
      actualTransactionId: "actual-synthetic-id",
    });
    const repeatedCommit = await database.journal.markCommitted(
      input.operationId,
      { actualTransactionId: "actual-synthetic-id" },
    );
    expect(committed.status).toBe("committed");
    expect(repeatedCommit.status).toBe("committed");
    await expect(database.journal.renewLease(input.operationId)).resolves.toBe(
      false,
    );
    await expect(
      database.journal.markCommitted(input.operationId, { different: true }),
    ).rejects.toThrow("cannot transition from committed to committed");
    await expect(
      database.journal.markUncertain(input.operationId, "actual-timeout"),
    ).rejects.toThrow("cannot transition from committed to uncertain");
    await expect(
      database.journal.resolveUncertainAsCommitted(input.operationId, {
        actualTransactionId: "actual-synthetic-id",
      }),
    ).resolves.toMatchObject({ status: "committed" });
  });

  it("compacts old terminal payloads while preserving idempotency and live work", async () => {
    const committedInput = createOperation({
      principalId: `compact-device-${randomUUID()}`,
      budgetId: `compact-budget-${randomUUID()}`,
    });
    await database.journal.enqueue(committedInput);
    await database.journal.claim(committedInput.operationId);
    await database.journal.markCommitted(committedInput.operationId, {
      transaction: { id: committedInput.entityId, notes: "old result" },
    });

    const rejectedInput = createOperation({
      principalId: committedInput.principalId,
      budgetId: committedInput.budgetId,
    });
    await database.journal.enqueue(rejectedInput);
    await database.journal.claim(rejectedInput.operationId);
    await database.journal.markRejected(rejectedInput.operationId, "bad-input");

    const queuedInput = createOperation({
      principalId: committedInput.principalId,
      budgetId: committedInput.budgetId,
    });
    await database.journal.enqueue(queuedInput);

    const uncertainInput = createOperation({
      principalId: committedInput.principalId,
      budgetId: committedInput.budgetId,
    });
    await database.journal.enqueue(uncertainInput);
    await database.journal.claim(uncertainInput.operationId);
    await database.journal.markUncertain(
      uncertainInput.operationId,
      "actual-timeout",
      { kind: "transaction-create-returned" },
    );

    const conflictInput = createOperation({
      principalId: committedInput.principalId,
      budgetId: committedInput.budgetId,
    });
    await database.journal.enqueue(conflictInput);
    await database.journal.claim(conflictInput.operationId);
    await database.journal.recordConflict(
      conflictInput.operationId,
      createConflict(),
    );

    await inspectionPool.query(
      `UPDATE companion_operations
       SET created_at = clock_timestamp() - interval '91 days',
           completed_at = clock_timestamp() - interval '91 days'
       WHERE operation_id = ANY($1::uuid[])`,
      [
        [
          committedInput.operationId,
          rejectedInput.operationId,
          queuedInput.operationId,
          uncertainInput.operationId,
          conflictInput.operationId,
        ],
      ],
    );

    const compactTerminalPayloads = database.journal.compactTerminalPayloads;
    if (!compactTerminalPayloads) {
      throw new Error("Journal payload compaction is unavailable");
    }
    await expect(compactTerminalPayloads.call(database.journal)).resolves.toBe(
      2,
    );
    const compacted = await database.journal.get(committedInput.operationId);
    expect(compacted).toMatchObject({
      operationId: committedInput.operationId,
      status: "committed",
      requestHash: expect.stringMatching(/^[a-f0-9]{64}$/),
      baseSnapshot: {},
      patch: {},
      actualResult: null,
    });
    await expect(
      database.journal.get(rejectedInput.operationId),
    ).resolves.toMatchObject({
      status: "rejected",
      baseSnapshot: {},
      patch: {},
      actualResult: null,
    });
    await expect(
      database.journal.get(queuedInput.operationId),
    ).resolves.toMatchObject({
      status: "queued",
      patch: { amount: 1200 },
    });
    await expect(
      database.journal.get(uncertainInput.operationId),
    ).resolves.toMatchObject({
      status: "uncertain",
      writeEvidence: { kind: "transaction-create-returned" },
    });
    await expect(
      database.journal.get(conflictInput.operationId),
    ).resolves.toMatchObject({
      status: "conflict",
      patch: { amount: 1200 },
    });

    const retry = await database.journal.enqueue(committedInput);
    expect(retry).toMatchObject({
      replayed: true,
      operation: {
        operationId: committedInput.operationId,
        status: "committed",
        patch: {},
      },
    });
    await expect(compactTerminalPayloads.call(database.journal)).resolves.toBe(
      0,
    );
  });

  it("caps each journal compaction sweep to a bounded number of batches", async () => {
    const rows = Array.from(
      { length: MAX_TERMINAL_OPERATION_PAYLOADS_PER_BATCH },
      (_, index) => ({ operation_id: String(index) }),
    );
    const query = vi.fn().mockResolvedValue({ rows });
    const journal = new PostgresWriteJournal({ query } as unknown as PoolType);

    await expect(journal.compactTerminalPayloads()).resolves.toBe(
      MAX_TERMINAL_OPERATION_PAYLOADS_PER_BATCH *
        MAX_TERMINAL_OPERATION_PAYLOAD_BATCHES_PER_SWEEP,
    );
    expect(query).toHaveBeenCalledTimes(
      MAX_TERMINAL_OPERATION_PAYLOAD_BATCHES_PER_SWEEP,
    );
  });

  it("releases only pre-write claims back to the durable queue", async () => {
    const input = createOperation();
    await database.journal.enqueue(input);
    await database.journal.claim(input.operationId);

    const released = await database.journal.releaseClaim(
      input.operationId,
      "actual-read-failed",
    );
    const repeated = await database.journal.releaseClaim(
      input.operationId,
      "actual-read-failed",
    );
    expect(released).toMatchObject({
      status: "queued",
      errorCode: "actual-read-failed",
      leaseExpiresAt: null,
      completedAt: null,
    });
    expect(repeated.status).toBe("queued");
    await expect(
      database.journal.releaseClaim(input.operationId, "different-error"),
    ).rejects.toThrow("cannot transition from queued to queued");

    const retriedClaim = await database.journal.claim(input.operationId);
    expect(retriedClaim).toMatchObject({
      claimed: true,
      operation: {
        status: "applying",
        attemptCount: 2,
        errorCode: null,
      },
    });
    await expect(
      database.journal.releaseClaim(input.operationId, "actual-read-failed"),
    ).resolves.toMatchObject({ status: "queued" });
  });

  it("records a pre-write validation failure as a terminal rejection", async () => {
    const input = createOperation();
    await database.journal.enqueue(input);
    await database.journal.claim(input.operationId);

    const rejected = await database.journal.markRejected(
      input.operationId,
      "account-not-found",
    );
    const repeated = await database.journal.markRejected(
      input.operationId,
      "account-not-found",
    );

    expect(rejected).toMatchObject({
      status: "rejected",
      errorCode: "account-not-found",
      actualResult: null,
      leaseExpiresAt: null,
      completedAt: expect.any(Date),
    });
    expect(repeated.status).toBe("rejected");
    await expect(
      database.journal.markRejected(input.operationId, "category-not-found"),
    ).rejects.toThrow("cannot transition from rejected to rejected");
    expect((await database.journal.claim(input.operationId)).claimed).toBe(
      false,
    );
  });

  it("never automatically reclaims an uncertain write", async () => {
    const input = createOperation();
    await database.journal.enqueue(input);
    await database.journal.claim(input.operationId);

    const uncertain = await database.journal.markUncertain(
      input.operationId,
      "actual-timeout",
    );
    const repeated = await database.journal.markUncertain(
      input.operationId,
      "actual-timeout",
    );
    expect(uncertain.status).toBe("uncertain");
    expect(repeated.errorCode).toBe("actual-timeout");
    await expect(
      database.journal.markUncertain(input.operationId, "different-error"),
    ).rejects.toThrow("cannot transition from uncertain to uncertain");
    const claim = await database.journal.claim(input.operationId);
    expect(claim.claimed).toBe(false);
    expect(claim.operation.status).toBe("uncertain");
  });

  it("requeues an uncertain operation after a read-back proves it was not applied", async () => {
    const input = createOperation();
    await database.journal.enqueue(input);
    await database.journal.claim(input.operationId);
    await database.journal.markUncertain(
      input.operationId,
      "claim-lease-expired",
    );

    const requeued = await database.journal.requeueUncertain(input.operationId);

    expect(requeued).toMatchObject({
      status: "queued",
      errorCode: "actual-readback-not-applied",
      writeEvidence: null,
    });
    expect((await database.journal.claim(input.operationId)).claimed).toBe(
      true,
    );
  });

  it("clears matching write evidence when read-back proves the write was not applied", async () => {
    const input = createOperation();
    const evidence: ActualWriteEvidence = {
      kind: "transaction-update-returned",
      appliedPatch: { amount: 1200 },
    };
    await database.journal.enqueue(input);
    await database.journal.claim(input.operationId);
    await database.journal.markUncertain(
      input.operationId,
      "operation-finalization-failed",
      evidence,
    );

    const requeued = await database.journal.requeueUncertain(
      input.operationId,
      evidence,
    );

    expect(requeued).toMatchObject({
      status: "queued",
      errorCode: "actual-readback-not-applied",
      actualResult: null,
      writeEvidence: null,
    });
  });

  it("does not clear write evidence unless the read-back evidence matches", async () => {
    const input = createOperation();
    const evidence: ActualWriteEvidence = {
      kind: "transaction-update-returned",
      appliedPatch: { amount: 1200 },
    };
    await database.journal.enqueue(input);
    await database.journal.claim(input.operationId);
    await database.journal.markUncertain(
      input.operationId,
      "operation-finalization-failed",
      evidence,
    );

    await expect(
      database.journal.requeueUncertain(input.operationId, {
        kind: "transaction-update-returned",
        appliedPatch: { amount: 1500 },
      }),
    ).rejects.toThrow("cannot transition from uncertain to queued");
    await expect(
      database.journal.get(input.operationId),
    ).resolves.toMatchObject({ status: "uncertain", writeEvidence: evidence });
  });

  it("returns the queued operation when read-back requeue is retried", async () => {
    const input = createOperation();
    await database.journal.enqueue(input);
    await database.journal.claim(input.operationId);
    await database.journal.markUncertain(
      input.operationId,
      "claim-lease-expired",
    );

    const first = await database.journal.requeueUncertain(input.operationId);
    const retry = await database.journal.requeueUncertain(input.operationId);

    expect(retry).toEqual(first);
    expect(retry).toMatchObject({
      status: "queued",
      errorCode: "actual-readback-not-applied",
    });
  });

  it("reports a missing operation when asked to requeue it", async () => {
    const operationId = randomUUID();

    await expect(
      database.journal.requeueUncertain(operationId),
    ).rejects.toMatchObject({
      name: "OperationNotFoundError",
      message: `Operation ${operationId} does not exist`,
    });
  });

  it("rejects uncertain resolution when write completion was never acknowledged", async () => {
    const input = createOperation();
    const result = { transactionId: input.entityId, amount: 1200 };
    await database.journal.enqueue(input);
    await database.journal.claim(input.operationId);
    await database.journal.markUncertain(input.operationId, "actual-timeout");

    await expect(
      database.journal.resolveUncertainAsCommitted(input.operationId, result),
    ).rejects.toThrow("without Actual write evidence");
    await expect(
      database.journal.get(input.operationId),
    ).resolves.toMatchObject({
      status: "uncertain",
      actualResult: null,
      writeEvidence: null,
    });
  });

  it("does not treat an unknown stored result as write evidence", async () => {
    const input = createOperation();
    const unknownResult = { kind: "future-result-format", succeeded: true };
    await database.journal.enqueue(input);
    await database.journal.claim(input.operationId);
    await database.journal.markUncertain(input.operationId, "actual-timeout");
    await inspectionPool.query(
      "UPDATE companion_operations SET actual_result = $2::jsonb WHERE operation_id = $1",
      [input.operationId, JSON.stringify(unknownResult)],
    );

    await expect(
      database.journal.get(input.operationId),
    ).resolves.toMatchObject({
      status: "uncertain",
      actualResult: unknownResult,
      writeEvidence: null,
    });
    await expect(
      database.journal.requeueUncertain(input.operationId),
    ).rejects.toThrow("cannot transition from uncertain to queued");
  });

  it("persists write evidence before allowing uncertain resolution", async () => {
    const input = createOperation();
    const result = { transactionId: input.entityId, amount: 1200 };
    const evidence: ActualWriteEvidence = {
      kind: "transaction-update-returned",
      appliedPatch: { amount: 1200 },
    };
    await database.journal.enqueue(input);
    await database.journal.claim(input.operationId);
    const uncertain = await database.journal.markUncertain(
      input.operationId,
      "actual-timeout",
      evidence,
    );
    const repeatedUncertain = await database.journal.markUncertain(
      input.operationId,
      "actual-timeout",
      evidence,
    );

    expect(uncertain).toMatchObject({
      status: "uncertain",
      actualResult: null,
      writeEvidence: evidence,
    });
    expect(repeatedUncertain.writeEvidence).toEqual(evidence);
    await expect(
      database.journal.get(input.operationId),
    ).resolves.toMatchObject({ status: "uncertain", writeEvidence: evidence });

    await expect(
      database.journal.resolveUncertainAsCommitted(input.operationId, result, {
        kind: "transaction-update-returned",
        appliedPatch: { amount: 1500 },
      }),
    ).rejects.toThrow("does not match stored evidence");

    await expect(
      database.journal.markUncertain(input.operationId, "actual-timeout", {
        kind: "transaction-update-returned",
        appliedPatch: { amount: 1500 },
      }),
    ).rejects.toThrow("cannot transition from uncertain to uncertain");

    const committed = await database.journal.resolveUncertainAsCommitted(
      input.operationId,
      result,
    );
    const repeated = await database.journal.resolveUncertainAsCommitted(
      input.operationId,
      result,
    );
    expect(committed).toMatchObject({
      status: "committed",
      actualResult: result,
      writeEvidence: null,
    });
    expect(repeated.status).toBe("committed");
    await expect(
      database.journal.resolveUncertainAsCommitted(input.operationId, {
        ...result,
        amount: 1500,
      }),
    ).rejects.toThrow("cannot transition from committed to committed");
  });

  it("rejects write evidence for a different transaction action", async () => {
    const input = createOperation({ action: "delete", patch: {} });
    await database.journal.enqueue(input);
    await database.journal.claim(input.operationId);
    await database.journal.markUncertain(input.operationId, "actual-timeout");

    await expect(
      database.journal.resolveUncertainAsCommitted(input.operationId, null, {
        kind: "transaction-update-returned",
        appliedPatch: { amount: 1200 },
      }),
    ).rejects.toThrow("does not match the operation action");
  });

  it("accepts returned delete evidence for an uncertain delete", async () => {
    const input = createOperation({ action: "delete", patch: {} });
    const evidence: ActualWriteEvidence = {
      kind: "transaction-delete-returned",
    };
    await database.journal.enqueue(input);
    await database.journal.claim(input.operationId);
    await database.journal.markUncertain(
      input.operationId,
      "actual-timeout",
      evidence,
    );

    const committed = await database.journal.resolveUncertainAsCommitted(
      input.operationId,
      { transactionId: input.entityId, recoveredByReadback: true },
    );

    expect(committed).toMatchObject({
      status: "committed",
      writeEvidence: null,
    });
  });

  it("accepts returned create evidence for an uncertain create", async () => {
    const input = createOperation({
      action: "create",
      entityId: "tandem:8c88495a-65d9-4531-8392-93bff2c65374",
      baseRevision: null,
      baseSnapshot: {},
    });
    const evidence: ActualWriteEvidence = {
      kind: "transaction-create-returned",
    };
    await database.journal.enqueue(input);
    await database.journal.claim(input.operationId);
    await database.journal.markUncertain(
      input.operationId,
      "actual-timeout",
      evidence,
    );

    const committed = await database.journal.resolveUncertainAsCommitted(
      input.operationId,
      { transactionId: input.entityId, recoveredByReadback: true },
    );

    expect(committed).toMatchObject({
      action: "create",
      status: "committed",
      writeEvidence: null,
    });
  });

  it("stores a conflict and treats a matching conflict retry as idempotent", async () => {
    const input = createOperation();
    await database.journal.enqueue(input);
    await database.journal.claim(input.operationId);
    const conflict = createConflict();

    const first = await database.journal.recordConflict(
      input.operationId,
      conflict,
    );
    const retry = await database.journal.recordConflict(input.operationId, {
      ...conflict,
      conflictId: randomUUID(),
    });
    expect(first.conflictId).toBe(conflict.conflictId);
    expect(retry.conflictId).toBe(conflict.conflictId);
    await expect(
      database.journal.getConflict(input.operationId),
    ).resolves.toMatchObject({
      conflictId: conflict.conflictId,
      operationId: input.operationId,
    });
    await expect(
      database.journal.get(input.operationId),
    ).resolves.toMatchObject({
      status: "conflict",
      errorCode: "conflict-detected",
    });
    await expect(
      database.journal.recordConflict(input.operationId, {
        ...conflict,
        conflictType: "different-conflict",
      }),
    ).rejects.toThrow("different conflict record");
  });

  it("rejects a conflict on an operation that has not been claimed", async () => {
    const input = createOperation();
    await database.journal.enqueue(input);

    await expect(
      database.journal.recordConflict(input.operationId, createConflict()),
    ).rejects.toThrow("cannot transition from queued to conflict");
  });

  it("fails if a conflict insert cannot be read back", async () => {
    const input = createOperation();
    const operationRow = {
      operation_id: input.operationId,
      principal_id: input.principalId,
      budget_id: input.budgetId,
      idempotency_key: input.idempotencyKey,
      request_hash: "a".repeat(64),
      entity_type: input.entityType,
      action: input.action,
      entity_id: input.entityId,
      base_revision: input.baseRevision,
      base_snapshot: input.baseSnapshot,
      patch: input.patch,
      status: "applying",
      attempt_count: 1,
      error_code: null,
      actual_result: null,
      created_at: new Date(),
      updated_at: new Date(),
      started_at: new Date(),
      lease_expires_at: new Date(),
      completed_at: null,
    };
    const query = vi.fn().mockImplementation(async (sql: string) => {
      if (sql.includes("FROM companion_operations")) {
        return { rows: [operationRow], rowCount: 1 };
      }
      return { rows: [], rowCount: 0 };
    });
    const release = vi.fn();
    const pool = {
      connect: vi.fn().mockResolvedValue({ query, release }),
    } as unknown as PoolType;
    const journal = new PostgresWriteJournal(pool);

    await expect(
      journal.recordConflict(input.operationId, createConflict()),
    ).rejects.toThrow("Conflict record was not persisted");
    expect(query).toHaveBeenLastCalledWith("ROLLBACK");
    expect(release).toHaveBeenCalledOnce();
  });

  it("rejects claims and finalization for missing operations", async () => {
    const missingId = randomUUID();
    await expect(database.journal.get(missingId)).resolves.toBeUndefined();
    await expect(database.journal.claim(missingId)).rejects.toThrow(
      "does not exist",
    );
    await expect(
      database.journal.markCommitted(missingId, null),
    ).rejects.toThrow("does not exist");
    await expect(
      database.journal.recordConflict(missingId, createConflict()),
    ).rejects.toThrow("does not exist");
    await expect(
      database.journal.releaseClaim(missingId, "safe-to-retry"),
    ).rejects.toThrow("does not exist");
    await expect(
      database.journal.resolveUncertainAsCommitted(missingId, null),
    ).rejects.toThrow("does not exist");
    await expect(
      database.journal.getConflict(missingId),
    ).resolves.toBeUndefined();
  });

  it("expires an interrupted applying lease to uncertain instead of replaying it", async () => {
    await database.journal.expireLeases();
    const input = createOperation();
    await database.journal.enqueue(input);
    await database.journal.claim(input.operationId);
    await inspectionPool.query(
      `UPDATE companion_operations
      SET lease_expires_at = clock_timestamp() - interval '1 second'
      WHERE operation_id = $1`,
      [input.operationId],
    );

    await expect(database.journal.renewLease(input.operationId)).resolves.toBe(
      false,
    );
    await expect(database.journal.expireLeases()).resolves.toBe(1);
    await expect(database.journal.expireLeases()).resolves.toBe(0);
    await expect(
      database.journal.get(input.operationId),
    ).resolves.toMatchObject({
      status: "uncertain",
      errorCode: "claim-lease-expired",
    });
    await expect(
      database.journal.claim(input.operationId),
    ).resolves.toMatchObject({
      claimed: false,
      operation: { status: "uncertain" },
    });
  });

  it("recovers an expired lease during startup without replaying its write", async () => {
    const input = createOperation();
    await database.journal.enqueue(input);
    await database.journal.claim(input.operationId);
    await inspectionPool.query(
      `UPDATE companion_operations
      SET lease_expires_at = clock_timestamp() - interval '1 second'
      WHERE operation_id = $1`,
      [input.operationId],
    );
    const reaper = new WriteLeaseReaper(database.journal);

    await reaper.start();
    try {
      await expect(
        database.journal.get(input.operationId),
      ).resolves.toMatchObject({
        status: "uncertain",
        errorCode: "claim-lease-expired",
        writeEvidence: null,
      });
      await expect(
        database.journal.claim(input.operationId),
      ).resolves.toMatchObject({
        claimed: false,
        operation: { status: "uncertain" },
      });
    } finally {
      await reaper.stop();
    }
  });

  it("commits a late Actual success after read-back confirms the expired write", async () => {
    const base: TransactionSnapshot = {
      id: "transaction-late-success",
      account: "checking-account",
      amount: 1500,
      date: "2026-11-01",
      payee: null,
      category: null,
      notes: null,
      cleared: null,
      reconciled: null,
      importedId: null,
      importedPayee: null,
      startingBalance: null,
      schedule: null,
      transferId: null,
      isParent: false,
      isChild: false,
      parentId: null,
      subtransactions: [],
    };
    const request = {
      operationId: randomUUID(),
      principalId: `device-${randomUUID()}`,
      budgetId: `sync-${randomUUID()}`,
      idempotencyKey: `late-success-${randomUUID()}`,
      transactionId: base.id,
      baseRevision: createTransactionRevision(base),
      baseSnapshot: base as unknown as JsonValue,
      action: "update" as const,
      patch: { amount: 1200 },
    };

    let actual: Record<string, unknown> = {
      id: base.id,
      account: base.account,
      amount: base.amount,
      date: base.date,
    };
    let actualWrites = 0;
    const transactions: ActualTransactionStore = {
      async read() {
        return actual;
      },
      async validateCreateInput() {
        throw new Error("This test does not validate creates");
      },
      async create() {
        throw new Error("This test does not create transactions");
      },
      async update(_id, fields) {
        actualWrites += 1;
        actual = { ...actual, ...fields };
        await inspectionPool.query(
          `UPDATE companion_operations
          SET lease_expires_at = clock_timestamp() - interval '1 second'
          WHERE operation_id = $1`,
          [request.operationId],
        );
        await database.journal.expireLeases();
        return actual;
      },
      async delete() {
        actualWrites += 1;
        return undefined;
      },
    };
    const session: ActualBudgetSessionPort = {
      async runBudget<T>(_syncId: string, operation: () => Promise<T>) {
        return operation();
      },
    };
    const reconciler = new TransactionReconciler(
      database,
      database.journal,
      session,
      transactions,
    );

    const first = await reconciler.submit(request);
    const retry = await reconciler.submit(request);

    expect(first.operation).toMatchObject({
      status: "committed",
      actualResult: {
        transactionId: base.id,
        appliedPatch: { amount: 1200 },
      },
    });
    expect(retry.operation.status).toBe("committed");
    expect(actualWrites).toBe(1);
    expect(actual.amount).toBe(1200);
  });

  it("serializes the same budget across PostgreSQL connections and releases locks after failure", async () => {
    const budgetId = `lock-budget-${randomUUID()}`;
    const entered = deferred();
    const release = deferred();
    const sameBudgetSecondEntered = deferred();
    let secondEntered = false;
    const first = database.withBudgetLock(budgetId, async () => {
      entered.resolve();
      await release.promise;
    });
    await entered.promise;
    const second = database.withBudgetLock(budgetId, async () => {
      secondEntered = true;
      sameBudgetSecondEntered.resolve();
    });

    const otherBudgetEntered = deferred();
    await database.withBudgetLock(`${budgetId}-independent`, async () => {
      otherBudgetEntered.resolve();
    });
    await otherBudgetEntered.promise;
    await new Promise((resolve) => setTimeout(resolve, 30));
    expect(secondEntered).toBe(false);

    release.resolve();
    await Promise.all([first, second]);
    await sameBudgetSecondEntered.promise;

    await expect(
      database.withBudgetLock(budgetId, async () => {
        throw new Error("synthetic operation failure");
      }),
    ).rejects.toThrow("synthetic operation failure");
    await expect(
      database.withBudgetLock(budgetId, async () => "lock-released"),
    ).resolves.toBe("lock-released");
    await expect(
      database.withBudgetLock(" ", async () => undefined),
    ).rejects.toThrow("Budget ID is required");
  });

  it("validates identifiers, JSON values, conflict fields, and safe error codes", async () => {
    const input = createOperation();
    await database.journal.enqueue(
      createOperation({ entityId: null, baseRevision: null }),
    );
    await expect(
      database.journal.enqueue({ ...input, operationId: "not-a-uuid" }),
    ).rejects.toThrow("operationId must be a UUID");
    await expect(
      database.journal.enqueue({ ...input, principalId: " " }),
    ).rejects.toThrow("principalId must contain");
    await expect(
      database.journal.enqueue({ ...input, baseSnapshot: Number.NaN }),
    ).rejects.toThrow("JSON numbers must be finite");
    await expect(
      database.journal.enqueue({
        ...input,
        patch: [] as unknown as { [key: string]: never },
      }),
    ).rejects.toThrow("patch must be a JSON object");
    await expect(
      database.journal.enqueue({
        ...input,
        patch: { invalid: undefined } as unknown as { [key: string]: never },
      }),
    ).rejects.toThrow("Value is not valid JSON");

    const conflictOperation = createOperation();
    await database.journal.enqueue(conflictOperation);
    await database.journal.claim(conflictOperation.operationId);
    await expect(
      database.journal.recordConflict(conflictOperation.operationId, {
        ...createConflict(),
        conflictId: "not-a-uuid",
      }),
    ).rejects.toThrow("conflictId must be a UUID");
    await expect(
      database.journal.recordConflict(
        conflictOperation.operationId,
        createConflict({ conflictingFields: [""] }),
      ),
    ).rejects.toThrow("conflicting field must contain");
    await expect(
      database.journal.recordConflict(conflictOperation.operationId, {
        ...createConflict(),
        conflictingFields: "amount" as unknown as string[],
      }),
    ).rejects.toThrow("conflictingFields must be an array");
    await expect(
      database.journal.markUncertain(
        conflictOperation.operationId,
        "not safe!",
      ),
    ).rejects.toThrow("errorCode must be a lowercase identifier");
    await expect(
      database.journal.releaseClaim(conflictOperation.operationId, "bad code"),
    ).rejects.toThrow("errorCode must be a lowercase identifier");
    await expect(database.journal.getConflict("invalid-id")).rejects.toThrow(
      "operationId must be a UUID",
    );
    await expect(
      database.journal.releaseClaim("invalid-id", "safe-to-retry"),
    ).rejects.toThrow("operationId must be a UUID");
    await expect(
      database.journal.resolveUncertainAsCommitted("invalid-id", null),
    ).rejects.toThrow("operationId must be a UUID");
  });

  it("wraps unexpected non-Error values from journal input validation", async () => {
    const input = createOperation();
    Object.defineProperty(input, "operationId", {
      get() {
        throw "synthetic non-Error validation failure";
      },
    });

    await expect(database.journal.enqueue(input)).rejects.toMatchObject({
      name: "InvalidOperationInputError",
      message: "Write operation input is invalid",
      cause: "synthetic non-Error validation failure",
    });
  });

  it("applies the idempotency-key limit in Unicode code points", async () => {
    await expect(
      database.journal.enqueue({
        ...createOperation(),
        idempotencyKey: "😀".repeat(200),
      }),
    ).resolves.toMatchObject({ replayed: false });
    await expect(
      database.journal.enqueue({
        ...createOperation(),
        idempotencyKey: "😀".repeat(201),
      }),
    ).rejects.toThrow(
      "idempotencyKey must contain between 1 and 200 characters",
    );
  });

  it("rejects PostgreSQL-incompatible NUL characters before writing", async () => {
    const input = createOperation();

    await expect(
      database.journal.enqueue({ ...input, principalId: "device\u0000id" }),
    ).rejects.toThrow("principalId must not contain NUL characters");
    await expect(
      database.journal.enqueue({
        ...input,
        idempotencyKey: "request\u0000key",
      }),
    ).rejects.toThrow("idempotencyKey must not contain NUL characters");
    await expect(
      database.journal.enqueue({
        ...input,
        baseSnapshot: { notes: "\u0000" },
      }),
    ).rejects.toThrow("JSON strings and object keys must not contain NUL");
    await expect(
      database.journal.enqueue({
        ...input,
        patch: { notes: "\u0000" },
      }),
    ).rejects.toThrow("JSON strings and object keys must not contain NUL");
    await expect(
      database.journal.enqueue({
        ...input,
        patch: { ["notes\u0000"]: "text" },
      }),
    ).rejects.toThrow("JSON strings and object keys must not contain NUL");
  });
});
