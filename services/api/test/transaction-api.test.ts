import { randomUUID } from "node:crypto";
import { describe, expect, it, vi } from "vitest";
import { createTransactionApi } from "../src/actual/transaction-api.js";
import {
  createTransactionRevision,
  type TransactionSnapshot,
} from "../src/actual/transaction-reconciliation.js";
import type {
  ActualBudgetSessionPort,
  TransactionReconciliationRequest,
  TransactionReconciliationResult,
  TransactionReconciler,
} from "../src/actual/transaction-reconciler.js";
import type { BudgetOperationOptions } from "../src/actual/actual-session.js";
import type { ActualTransactionStore } from "../src/actual/transaction-store.js";
import type {
  DurableWriteJournal,
  OperationConflict,
  WriteOperation,
} from "../src/write-journal/contracts.js";

function createActualTransaction(overrides: Record<string, unknown> = {}) {
  return {
    id: "transaction-1",
    account: "account-1",
    amount: -1500,
    date: "2026-11-01",
    ...overrides,
  };
}

function createOperation(
  overrides: Partial<WriteOperation> = {},
): WriteOperation {
  const now = new Date("2026-11-01T00:00:00.000Z");
  return {
    operationId: randomUUID(),
    principalId: "owner",
    budgetId: "budget-1",
    idempotencyKey: randomUUID(),
    requestHash: "request-hash",
    entityType: "transaction",
    action: "update",
    entityId: "transaction-1",
    baseRevision: "base-revision",
    baseSnapshot: { id: "transaction-1" },
    patch: { amount: -1200 },
    status: "committed",
    attemptCount: 1,
    errorCode: null,
    actualResult: { transactionId: "transaction-1" },
    writeEvidence: null,
    createdAt: now,
    updatedAt: now,
    startedAt: now,
    leaseExpiresAt: null,
    completedAt: now,
    ...overrides,
  };
}

function createConflict(operationId: string): OperationConflict {
  return {
    conflictId: randomUUID(),
    operationId,
    conflictType: "same-field-edit",
    baseSnapshot: { id: "transaction-1", amount: -1500 },
    localPatch: { amount: -1200 },
    remoteSnapshot: { id: "transaction-1", amount: -1800 },
    conflictingFields: ["amount"],
    createdAt: new Date("2026-11-01T00:00:01.000Z"),
  };
}

function createApi(
  overrides: {
    actual?: unknown;
    operation?: WriteOperation | undefined;
    conflict?: OperationConflict | undefined;
  } = {},
) {
  const runBudgetCalls: unknown[] = [];
  const runBudget: ActualBudgetSessionPort["runBudget"] = async <T>(
    syncId: string,
    operation: () => Promise<T>,
    options?: BudgetOperationOptions,
  ): Promise<T> => {
    runBudgetCalls.push({ syncId, options });
    return operation();
  };
  const read = vi.fn(async () => overrides.actual);
  const session: ActualBudgetSessionPort = { runBudget };
  const transactions: ActualTransactionStore = {
    read,
    validateCreateInput: vi.fn(async () => undefined),
    validateUpdateInput: vi.fn(async () => undefined),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  };
  const submitted: TransactionReconciliationResult = {
    operation: createOperation(),
    conflict: null,
  };
  const submit = vi.fn(async () => submitted);
  const checkOperationStatus = vi.fn(async () => submitted);
  const operation = overrides.operation;
  const conflict = overrides.conflict;
  const journal = {
    get: vi.fn(async () => operation),
    getConflict: vi.fn(async () => conflict),
  } as Pick<DurableWriteJournal, "get" | "getConflict">;
  const reconciler = {
    submit,
    checkOperationStatus,
  } as unknown as TransactionReconciler;
  return {
    api: createTransactionApi(session, transactions, reconciler, journal),
    runBudgetCalls,
    read,
    submit,
    checkOperationStatus,
    journal,
    submitted,
  };
}

describe("transaction API adapter", () => {
  it("reads a normalized transaction and derives its stable revision", async () => {
    const actual = createActualTransaction({ notes: "" });
    const fake = createApi({ actual });
    const result = await fake.api.readTransaction("budget-1", "transaction-1");
    const expected: TransactionSnapshot = {
      id: "transaction-1",
      account: "account-1",
      amount: -1500,
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

    expect(result).toEqual({
      snapshot: expected,
      revision: createTransactionRevision(expected),
    });
    expect(fake.runBudgetCalls).toEqual([
      { syncId: "budget-1", options: undefined },
    ]);
    expect(fake.read).toHaveBeenCalledWith("transaction-1");
  });

  it("forwards an encrypted-budget password only to the Actual session", async () => {
    const fake = createApi({ actual: createActualTransaction() });
    await fake.api.readTransaction("budget-1", "transaction-1", "secret");

    expect(fake.runBudgetCalls).toEqual([
      { syncId: "budget-1", options: { encryptionPassword: "secret" } },
    ]);
    expect(fake.journal.get).not.toHaveBeenCalled();
  });

  it("returns a null conflict when a conflicting operation has no conflict row", async () => {
    const fake = createApi({
      operation: createOperation({ status: "conflict" }),
    });

    await expect(
      fake.api.getOperation("owner", "operation-1"),
    ).resolves.toMatchObject({
      operation: { status: "conflict" },
      conflict: null,
    });
    expect(fake.journal.getConflict).toHaveBeenCalledWith("operation-1");
  });

  it("returns no snapshot for a missing Actual transaction", async () => {
    const fake = createApi();
    await expect(
      fake.api.readTransaction("budget-1", "missing"),
    ).resolves.toBeUndefined();
    expect(fake.read).toHaveBeenCalledWith("missing");
  });

  it("rejects an Actual row whose stable ID differs from the requested ID", async () => {
    const fake = createApi({
      actual: createActualTransaction({ id: "other" }),
    });
    await expect(
      fake.api.readTransaction("budget-1", "transaction-1"),
    ).rejects.toThrow("Actual returned a different transaction ID");
  });

  it("delegates operation submission to the reconciler", async () => {
    const request = {} as TransactionReconciliationRequest;
    const fake = createApi();

    await expect(fake.api.submit(request)).resolves.toBe(fake.submitted);
    expect(fake.submit).toHaveBeenCalledWith(request);
  });

  it.each([undefined, createOperation({ principalId: "another-owner" })])(
    "hides missing and other-principal operation records",
    async (operation) => {
      const fake = createApi({ operation });
      await expect(
        fake.api.getOperation("owner", operation?.operationId ?? randomUUID()),
      ).resolves.toBeUndefined();
      expect(fake.journal.getConflict).not.toHaveBeenCalled();
    },
  );

  it("returns an owned operation and its conflict details", async () => {
    const operation = createOperation({ status: "conflict" });
    const conflict = createConflict(operation.operationId);
    const fake = createApi({ operation, conflict });

    await expect(
      fake.api.getOperation("owner", operation.operationId),
    ).resolves.toEqual({ operation, conflict });
    expect(fake.journal.get).toHaveBeenCalledWith(operation.operationId);
    expect(fake.journal.getConflict).toHaveBeenCalledWith(
      operation.operationId,
    );
  });

  it("returns an owned non-conflict operation without loading a conflict row", async () => {
    const operation = createOperation({ status: "queued" });
    const fake = createApi({ operation });

    await expect(
      fake.api.getOperation("owner", operation.operationId),
    ).resolves.toEqual({ operation, conflict: null });
    expect(fake.journal.getConflict).not.toHaveBeenCalled();
  });

  it("checks uncertain operation status against Actual using the current budget password", async () => {
    const operation = createOperation({ status: "uncertain" });
    const fake = createApi({ operation });

    await expect(
      fake.api.getOperation("owner", operation.operationId, "budget-password"),
    ).resolves.toBe(fake.submitted);
    expect(fake.checkOperationStatus).toHaveBeenCalledWith(
      operation.operationId,
      "budget-password",
    );
    expect(fake.journal.getConflict).not.toHaveBeenCalled();
  });
});
