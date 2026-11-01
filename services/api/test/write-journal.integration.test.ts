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
import { PostgresWriteJournal } from "../src/write-journal/postgres-write-journal.js";

let database: Database;
let inspectionPool: Pool;

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
    database = createPostgresDatabase(connectionString);
    inspectionPool = new Pool({ connectionString, max: 3 });
    await database.migrate();
  }, 30_000);

  afterAll(async () => {
    await inspectionPool?.end();
    await database?.close();
  });

  it("keeps one current schema migration and applies it idempotently", async () => {
    await database.migrate();
    const order = await readMigrationOrder();
    const result = await inspectionPool.query<{ count: string }>(
      "SELECT count(*)::text AS count FROM companion_migrations",
    );
    expect(order).toHaveLength(1);
    expect(order).toEqual(MIGRATIONS.map(({ name }) => name));
    expect(result.rows[0]?.count).toBe("1");
  });

  it("validates a single ordered migration manifest", async () => {
    const migration = MIGRATIONS[0];
    if (!migration) {
      throw new Error("The point release requires exactly one migration");
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

  it("can reverse and reapply the point-release migration in an isolated schema", async () => {
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
      ).rejects.toThrow("Corrupted migrations");

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
      ).rejects.toThrow(
        "Companion database has migrations newer than this API",
      );

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
    const beforeRestart = createPostgresDatabase(connectionString);
    try {
      await beforeRestart.journal.enqueue(input);
    } finally {
      await beforeRestart.close();
    }

    const afterRestart = createPostgresDatabase(connectionString);
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
