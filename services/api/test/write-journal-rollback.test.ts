import { randomUUID } from "node:crypto";
import { describe, expect, it, vi } from "vitest";
import type { Pool } from "pg";
import { PostgresWriteJournal } from "../src/write-journal/postgres-write-journal.js";
import type {
  ConflictInput,
  WriteOperationInput,
} from "../src/write-journal/contracts.js";

const input: WriteOperationInput = {
  operationId: randomUUID(),
  principalId: "synthetic-device",
  budgetId: "synthetic-budget",
  idempotencyKey: "synthetic-request",
  entityType: "transaction",
  action: "update",
  entityId: "synthetic-transaction",
  baseRevision: "synthetic-revision",
  baseSnapshot: { id: "synthetic-transaction", amount: 1500 },
  patch: { amount: 1200 },
};

function createConflictInput(): ConflictInput {
  return {
    conflictId: randomUUID(),
    conflictType: "transaction-id-already-exists",
    baseSnapshot: {},
    localPatch: { amount: 1200 },
    remoteSnapshot: { amount: 1300 },
    conflictingFields: ["amount"],
  };
}

function createConflictJournalHarness(
  options: {
    operationStatus?: "queued" | "uncertain";
    persistedConflict?: "matching" | "different" | "missing";
    updateWins?: boolean;
    operationMissing?: boolean;
  } = {},
) {
  const conflict = createConflictInput();
  const operationRow = {
    operation_id: input.operationId,
    principal_id: input.principalId,
    budget_id: input.budgetId,
    idempotency_key: input.idempotencyKey,
    request_hash: "a".repeat(64),
    entity_type: input.entityType,
    action: "create",
    entity_id: input.entityId,
    base_revision: null,
    base_snapshot: {},
    patch: input.patch,
    status: options.operationStatus ?? "uncertain",
    attempt_count: 1,
    error_code: "actual-outcome-uncertain",
    actual_result: { kind: "transaction-create-returned" },
    created_at: new Date(),
    updated_at: new Date(),
    started_at: new Date(),
    lease_expires_at: new Date(),
    completed_at: null,
  };
  const persistedConflict =
    options.persistedConflict === "missing"
      ? undefined
      : {
          conflict_id: conflict.conflictId,
          operation_id: input.operationId,
          conflict_type:
            options.persistedConflict === "different"
              ? "different-conflict"
              : conflict.conflictType,
          base_snapshot: conflict.baseSnapshot,
          local_patch: conflict.localPatch,
          remote_snapshot: conflict.remoteSnapshot,
          conflicting_fields: conflict.conflictingFields,
          created_at: new Date(),
        };
  const query = vi.fn().mockImplementation(async (sql: string) => {
    if (sql === "BEGIN" || sql === "COMMIT" || sql === "ROLLBACK") {
      return { rows: [], rowCount: 0 };
    }
    if (sql.includes("FROM tandem_operations")) {
      return {
        rows: options.operationMissing ? [] : [operationRow],
        rowCount: options.operationMissing ? 0 : 1,
      };
    }
    if (sql.includes("INSERT INTO tandem_operation_conflicts")) {
      return { rows: [], rowCount: 1 };
    }
    if (sql.includes("FROM tandem_operation_conflicts")) {
      return {
        rows: persistedConflict ? [persistedConflict] : [],
        rowCount: persistedConflict ? 1 : 0,
      };
    }
    if (sql.includes("UPDATE tandem_operations")) {
      return {
        rows: options.updateWins ? [operationRow] : [],
        rowCount: options.updateWins ? 1 : 0,
      };
    }
    throw new Error(`Unexpected query: ${sql}`);
  });
  const release = vi.fn();
  const pool = {
    connect: vi.fn().mockResolvedValue({ query, release }),
  } as unknown as Pool;
  return { journal: new PostgresWriteJournal(pool), conflict, query, release };
}

describe("PostgreSQL journal rollback failures", () => {
  it("rejects uncertain conflict resolution for a missing operation", async () => {
    const harness = createConflictJournalHarness({ operationMissing: true });

    await expect(
      harness.journal.resolveUncertainAsConflict(
        input.operationId,
        harness.conflict,
      ),
    ).rejects.toThrow("does not exist");
    expect(harness.query).toHaveBeenCalledWith("ROLLBACK");
  });

  it("rejects conflict resolution from a non-uncertain operation", async () => {
    const harness = createConflictJournalHarness({ operationStatus: "queued" });

    await expect(
      harness.journal.resolveUncertainAsConflict(
        input.operationId,
        harness.conflict,
      ),
    ).rejects.toThrow("cannot transition from queued to conflict");
    expect(harness.query).toHaveBeenCalledWith("ROLLBACK");
    expect(harness.release).toHaveBeenCalledOnce();
  });

  it("rejects uncertain conflict resolution when the inserted conflict is missing", async () => {
    const harness = createConflictJournalHarness({
      persistedConflict: "missing",
    });

    await expect(
      harness.journal.resolveUncertainAsConflict(
        input.operationId,
        harness.conflict,
      ),
    ).rejects.toThrow("Conflict record was not persisted");
    expect(harness.query).toHaveBeenCalledWith("ROLLBACK");
  });

  it("rejects uncertain conflict resolution when an existing conflict differs", async () => {
    const harness = createConflictJournalHarness({
      persistedConflict: "different",
    });

    await expect(
      harness.journal.resolveUncertainAsConflict(
        input.operationId,
        harness.conflict,
      ),
    ).rejects.toThrow("Operation already has a different conflict record");
    expect(harness.query).toHaveBeenCalledWith("ROLLBACK");
  });

  it("reports a lost status transition when an uncertain conflict update returns no row", async () => {
    const harness = createConflictJournalHarness({ updateWins: false });

    await expect(
      harness.journal.resolveUncertainAsConflict(
        input.operationId,
        harness.conflict,
      ),
    ).rejects.toThrow("cannot transition from uncertain to conflict");
    expect(harness.query).toHaveBeenCalledWith("ROLLBACK");
  });

  it.each([
    {
      label: "an Error",
      rollbackError: new Error("rollback connection failed"),
    },
    {
      label: "a non-Error value",
      rollbackError: "rollback connection failed",
    },
  ])(
    "preserves the primary database error and discards the client when rollback throws $label",
    async ({ rollbackError }) => {
      const primaryError = new Error("financial operation insert failed");
      const consoleError = vi
        .spyOn(console, "error")
        .mockImplementation(() => {});
      const query = vi.fn().mockImplementation(async (sql: string) => {
        if (sql === "BEGIN") {
          return { rows: [], rowCount: 0 };
        }
        if (sql === "ROLLBACK") {
          throw rollbackError;
        }
        throw primaryError;
      });
      const release = vi.fn();
      const pool = {
        connect: vi.fn().mockResolvedValue({ query, release }),
      } as unknown as Pool;

      await expect(new PostgresWriteJournal(pool).enqueue(input)).rejects.toBe(
        primaryError,
      );
      expect(query).toHaveBeenCalledWith("ROLLBACK");
      expect(release).toHaveBeenCalledWith(
        rollbackError instanceof Error ? rollbackError : true,
      );
      expect(consoleError).toHaveBeenCalledWith(
        "PostgreSQL transaction rollback failed; client discarded",
        {
          errorType:
            rollbackError instanceof Error ? rollbackError.name : "string",
        },
      );
      vi.restoreAllMocks();
    },
  );

  it("preserves the transaction error if cleanup logging throws", async () => {
    const primaryError = new Error("financial operation insert failed");
    const rollbackError = new Error("rollback connection failed");
    const query = vi.fn().mockImplementation(async (sql: string) => {
      if (sql === "BEGIN") {
        return { rows: [], rowCount: 0 };
      }
      if (sql === "ROLLBACK") {
        throw rollbackError;
      }
      throw primaryError;
    });
    const release = vi.fn();
    const pool = {
      connect: vi.fn().mockResolvedValue({ query, release }),
    } as unknown as Pool;
    vi.spyOn(console, "error").mockImplementation(() => {
      throw new Error("logger unavailable");
    });

    await expect(new PostgresWriteJournal(pool).enqueue(input)).rejects.toBe(
      primaryError,
    );
    expect(release).toHaveBeenCalledWith(rollbackError);
    vi.restoreAllMocks();
  });
});
