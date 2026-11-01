import { randomUUID } from "node:crypto";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  ActualPostWriteSyncError,
  type BudgetOperationOptions,
} from "../src/actual/actual-session.js";
import {
  TransactionReconciler,
  type ActualBudgetSessionPort,
  type TransactionReconciliationRequest,
} from "../src/actual/transaction-reconciler.js";
import {
  createTransactionRevision,
  type TransactionSnapshot,
} from "../src/actual/transaction-reconciliation.js";
import type { ActualTransactionStore } from "../src/actual/transaction-store.js";
import type {
  ActualWriteEvidence,
  ClaimResult,
  ConflictInput,
  DurableWriteJournal,
  EnqueueResult,
  JsonValue,
  OperationConflict,
  OperationStatus,
  WriteOperation,
  WriteOperationInput,
} from "../src/write-journal/contracts.js";
import { WRITE_OPERATION_LEASE_RENEWAL_INTERVAL_MS } from "../src/write-journal/contracts.js";

const USE_CURRENT = Symbol("use-current-actual-transaction");
const updateEvidence: ActualWriteEvidence = {
  kind: "transaction-update-returned",
  appliedPatch: { amount: 1200 },
};
const deleteEvidence: ActualWriteEvidence = {
  kind: "transaction-delete-returned",
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((accept) => {
    resolve = accept;
  });
  return { promise, resolve };
}

function createSnapshot(
  overrides: Partial<TransactionSnapshot> = {},
): TransactionSnapshot {
  return {
    id: "transaction-1",
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
    ...overrides,
  };
}

function actualFrom(snapshot: TransactionSnapshot): Record<string, unknown> {
  return {
    id: snapshot.id,
    account: snapshot.account,
    amount: snapshot.amount,
    date: snapshot.date,
    payee: snapshot.payee,
    category: snapshot.category,
    notes: snapshot.notes,
    cleared: snapshot.cleared,
    reconciled: snapshot.reconciled,
    imported_id: snapshot.importedId,
    imported_payee: snapshot.importedPayee,
    starting_balance_flag: snapshot.startingBalance,
    schedule: snapshot.schedule,
    transfer_id: snapshot.transferId,
    is_parent: snapshot.isParent,
    is_child: snapshot.isChild,
    parent_id: snapshot.parentId,
    subtransactions: snapshot.subtransactions.map(actualFrom),
  };
}

function createRequest(
  overrides: Partial<TransactionReconciliationRequest> = {},
): TransactionReconciliationRequest {
  const base = createSnapshot();
  return {
    operationId: randomUUID(),
    principalId: `device-${randomUUID()}`,
    budgetId: `budget-${randomUUID()}`,
    idempotencyKey: `request-${randomUUID()}`,
    transactionId: base.id,
    baseRevision: createTransactionRevision(base),
    baseSnapshot: base as unknown as JsonValue,
    action: "update",
    patch: { amount: 1200 },
    ...overrides,
  };
}

function createOperation(
  input: WriteOperationInput,
  status: OperationStatus,
  writeEvidence: ActualWriteEvidence | null = null,
): WriteOperation {
  const timestamp = new Date("2026-11-01T12:00:00.000Z");
  return {
    ...input,
    requestHash: "request-hash",
    status,
    attemptCount: 0,
    errorCode: status === "uncertain" ? "actual-outcome-uncertain" : null,
    actualResult: null,
    writeEvidence,
    createdAt: timestamp,
    updatedAt: timestamp,
    startedAt: null,
    leaseExpiresAt: null,
    completedAt: null,
  };
}

class MemoryWriteJournal implements DurableWriteJournal {
  operation: WriteOperation | undefined;
  conflict: OperationConflict | undefined;
  initialStatus: OperationStatus = "queued";
  initialWriteEvidence: ActualWriteEvidence | null = null;
  claimAllowed = true;
  missingOnGet = false;
  getError: unknown;
  readonly failures = new Map<string, unknown>();

  async enqueue(input: WriteOperationInput): Promise<EnqueueResult> {
    this.#throwIfFailed("enqueue");
    this.operation ??= createOperation(
      input,
      this.initialStatus,
      this.initialWriteEvidence,
    );
    return { operation: this.operation, replayed: false };
  }

  async get(): Promise<WriteOperation | undefined> {
    if (this.getError !== undefined) {
      throw this.getError;
    }
    return this.missingOnGet ? undefined : this.operation;
  }

  async getConflict(): Promise<OperationConflict | undefined> {
    this.#throwIfFailed("getConflict");
    return this.conflict;
  }

  async claim(): Promise<ClaimResult> {
    this.#throwIfFailed("claim");
    const operation = this.#requireOperation();
    if (!this.claimAllowed) {
      return { operation, claimed: false };
    }
    this.operation = {
      ...operation,
      status: "applying",
      attemptCount: operation.attemptCount + 1,
      errorCode: null,
    };
    return { operation: this.operation, claimed: true };
  }

  async renewLease(): Promise<boolean> {
    this.#throwIfFailed("renewLease");
    return true;
  }

  async releaseClaim(
    _operationId: string,
    errorCode: string,
  ): Promise<WriteOperation> {
    this.#throwIfFailed("releaseClaim");
    this.operation = {
      ...this.#requireOperation(),
      status: "queued",
      errorCode,
    };
    return this.operation;
  }

  async markCommitted(
    _operationId: string,
    actualResult: JsonValue,
  ): Promise<WriteOperation> {
    this.#throwIfFailed("markCommitted");
    const operation = this.#requireOperation();
    if (operation.status === "committed") {
      if (
        JSON.stringify(operation.actualResult) === JSON.stringify(actualResult)
      ) {
        return operation;
      }
      throw new Error("Committed result differs");
    }
    if (operation.status !== "applying") {
      throw new Error(`Cannot commit from ${operation.status}`);
    }
    this.operation = {
      ...operation,
      status: "committed",
      actualResult,
      errorCode: null,
    };
    return this.operation;
  }

  async markUncertain(
    _operationId: string,
    errorCode: string,
    writeEvidence?: ActualWriteEvidence,
  ): Promise<WriteOperation> {
    this.#throwIfFailed("markUncertain");
    const operation = this.#requireOperation();
    if (operation.status !== "applying") {
      throw new Error(`Cannot mark uncertain from ${operation.status}`);
    }
    this.operation = {
      ...operation,
      status: "uncertain",
      errorCode,
      writeEvidence: writeEvidence ?? operation.writeEvidence,
    };
    return this.operation;
  }

  async resolveUncertainAsCommitted(
    _operationId: string,
    actualResult: JsonValue,
    writeEvidence?: ActualWriteEvidence,
  ): Promise<WriteOperation> {
    this.#throwIfFailed("resolveUncertainAsCommitted");
    const operation = this.#requireOperation();
    if (operation.status !== "uncertain") {
      throw new Error(`Cannot resolve from ${operation.status}`);
    }
    const evidence = operation.writeEvidence ?? writeEvidence;
    if (!evidence) {
      throw new Error(
        "Cannot resolve uncertain operation without Actual write evidence",
      );
    }
    if (
      operation.writeEvidence &&
      writeEvidence &&
      JSON.stringify(operation.writeEvidence) !== JSON.stringify(writeEvidence)
    ) {
      throw new Error("Actual write evidence does not match stored evidence");
    }
    this.operation = {
      ...operation,
      status: "committed",
      actualResult,
      errorCode: null,
      writeEvidence: null,
    };
    return this.operation;
  }

  async recordConflict(
    _operationId: string,
    conflict: ConflictInput,
  ): Promise<OperationConflict> {
    this.#throwIfFailed("recordConflict");
    const operation = this.#requireOperation();
    this.operation = { ...operation, status: "conflict" };
    this.conflict = {
      ...conflict,
      operationId: operation.operationId,
      createdAt: new Date("2026-11-01T12:00:00.000Z"),
    };
    return this.conflict;
  }

  async expireLeases(): Promise<number> {
    this.#throwIfFailed("expireLeases");
    return 0;
  }

  #requireOperation(): WriteOperation {
    if (!this.operation) {
      throw new Error("Operation is missing");
    }
    return this.operation;
  }

  #throwIfFailed(method: string): void {
    if (this.failures.has(method)) {
      const error = this.failures.get(method);
      this.failures.delete(method);
      throw error;
    }
  }
}

interface Harness {
  journal: MemoryWriteJournal;
  reconciler: TransactionReconciler;
  sessionCalls: Array<{
    syncId: string;
    options?: BudgetOperationOptions;
  }>;
  store: ActualTransactionStore;
  get actual(): unknown | undefined;
  set actual(value: unknown | undefined);
  setReadResults(results: Array<unknown | undefined | Error>): void;
  setReadErrorAt(call: number, error: unknown): void;
  setSessionErrorAt(
    call: number,
    error: unknown,
    afterOperation?: boolean,
  ): void;
  pauseSessionAt(
    call: number,
    onEntered: () => void,
    wait: Promise<void>,
  ): void;
  setPredicateInputs(values: unknown[]): void;
  syncPredicateResults: boolean[];
}

function createHarness(
  request = createRequest(),
  options: {
    initialStatus?: OperationStatus;
    initialWriteEvidence?: ActualWriteEvidence;
    claimAllowed?: boolean;
  } = {},
): Harness {
  const journal = new MemoryWriteJournal();
  journal.initialStatus = options.initialStatus ?? "queued";
  journal.initialWriteEvidence = options.initialWriteEvidence ?? null;
  journal.claimAllowed = options.claimAllowed ?? true;
  const base = request.baseSnapshot as unknown as TransactionSnapshot;
  let actual: unknown | undefined = actualFrom(base);
  let readResults: Array<unknown | undefined | Error> = [];
  const readErrors = new Map<number, unknown>();
  const sessionErrors = new Map<
    number,
    { error: unknown; afterOperation: boolean }
  >();
  const sessionPauses = new Map<
    number,
    { onEntered: () => void; wait: Promise<void> }
  >();
  let readCount = 0;
  const sessionCalls: Harness["sessionCalls"] = [];
  const syncPredicateResults: boolean[] = [];
  let invokePredicateWith: unknown[] = [];

  const store: ActualTransactionStore = {
    async read() {
      readCount += 1;
      if (readErrors.has(readCount)) {
        throw readErrors.get(readCount);
      }
      if (readResults.length > 0) {
        const next = readResults.shift();
        if (next === USE_CURRENT) {
          return actual;
        }
        if (next instanceof Error) {
          throw next;
        }
        actual = next;
        return next;
      }
      return actual;
    },
    async update(_transactionId, patch) {
      const current = actual as Record<string, unknown>;
      const apiPatch: Record<string, unknown> = {};
      for (const [field, value] of Object.entries(patch)) {
        const actualField = field === "transferId" ? "transfer_id" : field;
        apiPatch[actualField] = value;
      }
      actual = { ...current, ...apiPatch };
      return actual;
    },
    async delete() {
      actual = undefined;
      return undefined;
    },
  };

  const session: ActualBudgetSessionPort = {
    async runBudget<T>(
      syncId: string,
      operation: () => Promise<T>,
      runOptions?: BudgetOperationOptions,
    ): Promise<T> {
      const call = sessionCalls.length + 1;
      sessionCalls.push({
        syncId,
        ...(runOptions === undefined ? {} : { options: runOptions }),
      });
      const pause = sessionPauses.get(call);
      if (pause) {
        pause.onEntered();
        await pause.wait;
      }
      const failure = sessionErrors.get(call);
      if (failure && !failure.afterOperation) {
        throw failure.error;
      }
      const result = await operation();
      if (typeof runOptions?.synchronizeAfterWrite === "function") {
        syncPredicateResults.push(runOptions.synchronizeAfterWrite(result));
        for (const value of invokePredicateWith) {
          syncPredicateResults.push(runOptions.synchronizeAfterWrite(value));
        }
        invokePredicateWith = [];
      }
      if (failure?.afterOperation) {
        throw failure.error;
      }
      return result;
    },
  };
  const reconciler = new TransactionReconciler(
    {
      async withBudgetLock(_budgetId, work) {
        return work();
      },
    },
    journal,
    session,
    store,
  );

  return {
    journal,
    reconciler,
    sessionCalls,
    store,
    get actual() {
      return actual;
    },
    set actual(value) {
      actual = value;
    },
    setReadResults(results) {
      readResults = [...results];
    },
    setReadErrorAt(call, error) {
      readErrors.set(call, error);
    },
    setSessionErrorAt(call, error, afterOperation = false) {
      sessionErrors.set(call, { error, afterOperation });
    },
    pauseSessionAt(call, onEntered, wait) {
      sessionPauses.set(call, { onEntered, wait });
    },
    setPredicateInputs(values) {
      invokePredicateWith = [...values];
    },
    syncPredicateResults,
  };
}

describe("Actual transaction reconciliation", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("renews a claimed operation while Actual work runs and stops after completion", async () => {
    vi.useFakeTimers();
    const request = createRequest();
    const harness = createHarness(request);
    const entered = deferred<void>();
    const gate = deferred<void>();
    const pendingRenewal = deferred<boolean>();
    harness.pauseSessionAt(1, () => entered.resolve(), gate.promise);
    const renewLease = vi
      .spyOn(harness.journal, "renewLease")
      .mockReturnValueOnce(pendingRenewal.promise);

    const submitting = harness.reconciler.submit(request);
    await entered.promise;
    await vi.advanceTimersByTimeAsync(
      WRITE_OPERATION_LEASE_RENEWAL_INTERVAL_MS,
    );
    await vi.advanceTimersByTimeAsync(
      WRITE_OPERATION_LEASE_RENEWAL_INTERVAL_MS,
    );
    expect(renewLease).toHaveBeenCalledOnce();

    pendingRenewal.resolve(true);
    await vi.advanceTimersByTimeAsync(0);
    await vi.advanceTimersByTimeAsync(
      WRITE_OPERATION_LEASE_RENEWAL_INTERVAL_MS,
    );
    expect(renewLease).toHaveBeenCalledTimes(2);

    gate.resolve();
    const result = await submitting;
    expect(result.operation.status).toBe("committed");
    await vi.advanceTimersByTimeAsync(
      WRITE_OPERATION_LEASE_RENEWAL_INTERVAL_MS,
    );
    expect(renewLease).toHaveBeenCalledTimes(2);
  });

  it("stops renewing when the journal reports that the lease was lost", async () => {
    vi.useFakeTimers();
    const request = createRequest();
    const harness = createHarness(request);
    const entered = deferred<void>();
    const gate = deferred<void>();
    harness.pauseSessionAt(1, () => entered.resolve(), gate.promise);
    const renewLease = vi
      .spyOn(harness.journal, "renewLease")
      .mockResolvedValue(false);

    const submitting = harness.reconciler.submit(request);
    await entered.promise;
    await vi.advanceTimersByTimeAsync(
      WRITE_OPERATION_LEASE_RENEWAL_INTERVAL_MS,
    );
    await vi.advanceTimersByTimeAsync(
      WRITE_OPERATION_LEASE_RENEWAL_INTERVAL_MS,
    );
    expect(renewLease).toHaveBeenCalledOnce();

    gate.resolve();
    await submitting;
  });

  it("reports renewal failures without losing the Actual operation result", async () => {
    vi.useFakeTimers();
    const request = createRequest();
    const harness = createHarness(request);
    const entered = deferred<void>();
    const gate = deferred<void>();
    harness.pauseSessionAt(1, () => entered.resolve(), gate.promise);
    const renewalError = new Error("database unavailable");
    const renewLease = vi
      .spyOn(harness.journal, "renewLease")
      .mockRejectedValueOnce(renewalError);
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});

    const submitting = harness.reconciler.submit(request);
    await entered.promise;
    await vi.advanceTimersByTimeAsync(
      WRITE_OPERATION_LEASE_RENEWAL_INTERVAL_MS,
    );
    expect(renewLease).toHaveBeenCalledOnce();
    expect(consoleError).toHaveBeenCalledWith(
      "Transaction journal transition failed",
      { errorType: "Error" },
    );

    gate.resolve();
    const result = await submitting;
    expect(result.operation.status).toBe("committed");
  });

  it("applies and verifies an update with encryption and sync intent", async () => {
    const request = createRequest({ encryptionPassword: "budget-secret" });
    const harness = createHarness(request);

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("committed");
    expect(harness.actual).toMatchObject({ amount: 1200 });
    expect(harness.sessionCalls).toHaveLength(2);
    expect(harness.sessionCalls[0]).toMatchObject({
      syncId: request.budgetId,
      options: { encryptionPassword: "budget-secret" },
    });
    expect(typeof harness.sessionCalls[0]?.options?.synchronizeAfterWrite).toBe(
      "function",
    );
    expect(harness.syncPredicateResults).toEqual([true]);
    expect(harness.sessionCalls[1]?.options).toEqual({
      encryptionPassword: "budget-secret",
    });
  });

  it("commits a cleared note normalized to null alongside another field", async () => {
    const base = createSnapshot({ notes: "Prior note" });
    const request = createRequest({
      baseSnapshot: base as unknown as JsonValue,
      baseRevision: createTransactionRevision(base),
      patch: { amount: 1200, notes: "" },
    });
    const harness = createHarness(request);

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      status: "committed",
      actualResult: { appliedPatch: { amount: 1200, notes: "" } },
    });
    expect(result.conflict).toBeNull();
    expect(harness.actual).toMatchObject({ amount: 1200, notes: "" });
  });

  it("commits a non-empty note patch without normalizing it", async () => {
    const request = createRequest({ patch: { notes: "Updated note" } });
    const harness = createHarness(request);

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      status: "committed",
      actualResult: { appliedPatch: { notes: "Updated note" } },
    });
    expect(harness.actual).toMatchObject({ notes: "Updated note" });
  });

  it("records an update conflict when Actual deleted the transaction", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.actual = undefined;

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("conflict");
    expect(result.conflict).toMatchObject({
      conflictType: "deleted-remotely",
      conflictingFields: ["$entity"],
      remoteSnapshot: null,
    });
  });

  it("commits a deletion that is already absent", async () => {
    const request = createRequest({ action: "delete", patch: {} });
    const harness = createHarness(request);
    harness.actual = undefined;

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      status: "committed",
      actualResult: {
        transactionId: request.transactionId,
        deleted: true,
        alreadyAbsent: true,
      },
    });
  });

  it("rejects a mismatched Actual ID and releases a pre-write claim", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.actual = { ...actualFrom(createSnapshot()), id: "other-id" };

    await expect(harness.reconciler.submit(request)).rejects.toThrow(
      "Actual returned a different transaction ID",
    );
    expect(harness.journal.operation).toMatchObject({
      status: "queued",
      errorCode: "actual-read-failed",
    });
  });

  it("rejects structured transaction edits", async () => {
    const base = createSnapshot({ transferId: "transfer-1", isParent: true });
    const request = createRequest({
      baseSnapshot: base as unknown as JsonValue,
      baseRevision: createTransactionRevision(base),
    });
    const harness = createHarness(request);
    harness.actual = actualFrom(createSnapshot());

    const result = await harness.reconciler.submit(request);

    expect(result.conflict).toMatchObject({
      conflictType: "structured-transaction-unsupported",
      conflictingFields: ["transferId", "isParent"],
    });
  });

  it("records a same-field conflict without overwriting Actual", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.actual = { ...actualFrom(createSnapshot()), amount: 1800 };

    const result = await harness.reconciler.submit(request);

    expect(result.conflict).toMatchObject({
      conflictType: "same-field-edit",
      conflictingFields: ["amount"],
    });
    expect(harness.actual).toMatchObject({ amount: 1800 });
  });

  it("commits an update that already matches the requested value", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.actual = { ...actualFrom(createSnapshot()), amount: 1200 };

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({ status: "committed" });
    expect(result.operation.actualResult).toMatchObject({ unchanged: true });
    expect(harness.syncPredicateResults).toEqual([false]);
    expect(harness.sessionCalls).toHaveLength(1);
  });

  it("invokes sync selection safely for a non-record result", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.setPredicateInputs([null, [], "not-a-plan"]);

    await harness.reconciler.submit(request);

    expect(harness.syncPredicateResults).toEqual([true, false, false, false]);
    const selector = harness.sessionCalls[0]?.options?.synchronizeAfterWrite;
    expect(typeof selector).toBe("function");
    if (typeof selector === "function") {
      expect(selector({ kind: "write-update" })).toBe(true);
      expect(selector({ kind: "write-delete" })).toBe(true);
      expect(selector({ kind: "unchanged" })).toBe(false);
      expect(selector(null)).toBe(false);
      const arrayPlan = Object.assign([], { kind: "write-update" });
      expect(selector(arrayPlan)).toBe(false);
      const functionPlan = Object.assign(() => undefined, {
        kind: "write-update",
      });
      expect(selector(functionPlan)).toBe(false);
    }
  });

  it("reports split children as the structured conflict field", async () => {
    const child = createSnapshot({
      id: "transaction-child",
      amount: -300,
      isChild: true,
      parentId: "transaction-1",
    });
    const base = createSnapshot({ subtransactions: [child] });
    const request = createRequest({
      baseSnapshot: base as unknown as JsonValue,
      baseRevision: createTransactionRevision(base),
    });
    const harness = createHarness(request);
    harness.actual = actualFrom(createSnapshot());

    const result = await harness.reconciler.submit(request);

    expect(result.conflict).toMatchObject({
      conflictType: "structured-transaction-unsupported",
      conflictingFields: ["subtransactions"],
    });
  });

  it.each([
    {
      label: "a child marker",
      fields: { isChild: true },
      conflictingFields: ["isChild"],
    },
    {
      label: "a parent ID",
      fields: { parentId: "transaction-parent" },
      conflictingFields: ["parentId"],
    },
  ])("reports $label as a structural conflict", async (scenario) => {
    const base = createSnapshot(scenario.fields);
    const request = createRequest({
      baseSnapshot: base as unknown as JsonValue,
      baseRevision: createTransactionRevision(base),
    });
    const harness = createHarness(request);
    harness.actual = actualFrom(createSnapshot());

    const result = await harness.reconciler.submit(request);

    expect(result.conflict?.conflictingFields).toContain(
      scenario.conflictingFields[0],
    );
  });

  it("reports a parent marker introduced by Actual", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.actual = actualFrom(createSnapshot({ parentId: "parent-1" }));

    const result = await harness.reconciler.submit(request);

    expect(result.conflict?.conflictingFields).toContain("parentId");
  });

  it("reports split children introduced by Actual", async () => {
    const child = createSnapshot({
      id: "transaction-child",
      amount: -300,
      isChild: true,
      parentId: "transaction-1",
    });
    const request = createRequest();
    const harness = createHarness(request);
    harness.actual = actualFrom(createSnapshot({ subtransactions: [child] }));

    const result = await harness.reconciler.submit(request);

    expect(result.conflict?.conflictingFields).toContain("subtransactions");
  });

  it("detects delete-versus-edit conflicts", async () => {
    const request = createRequest({ action: "delete", patch: {} });
    const harness = createHarness(request);
    harness.actual = { ...actualFrom(createSnapshot()), amount: 1800 };

    const result = await harness.reconciler.submit(request);

    expect(result.conflict).toMatchObject({
      conflictType: "delete-versus-edit",
      conflictingFields: ["amount"],
    });
  });

  it("commits a delete after read-back confirms the transaction is absent", async () => {
    const request = createRequest({ action: "delete", patch: {} });
    const harness = createHarness(request);

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      status: "committed",
      actualResult: { deleted: true },
    });
    expect(harness.actual).toBeUndefined();
  });

  it("records a delete conflict when read-back still finds the transaction", async () => {
    const request = createRequest({ action: "delete", patch: {} });
    const harness = createHarness(request);
    harness.setReadResults([
      USE_CURRENT,
      actualFrom(createSnapshot({ amount: 1500 })),
    ]);

    const result = await harness.reconciler.submit(request);

    expect(result.conflict).toMatchObject({
      conflictType: "delete-not-visible-after-sync",
      conflictingFields: ["$entity"],
    });
  });

  it("records a conflict when an updated transaction disappears before read-back", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.setReadResults([USE_CURRENT, undefined]);

    const result = await harness.reconciler.submit(request);

    expect(result.conflict).toMatchObject({
      conflictType: "deleted-during-write",
      remoteSnapshot: null,
      conflictingFields: ["$entity"],
    });
  });

  it("records structured transactions discovered after an update", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.setReadResults([
      USE_CURRENT,
      actualFrom(createSnapshot({ transferId: "transfer-2" })),
    ]);

    const result = await harness.reconciler.submit(request);

    expect(result.conflict).toMatchObject({
      conflictType: "structured-transaction-after-write",
      conflictingFields: ["transferId"],
    });
  });

  it("records a conflict when Actual changes the written field before read-back", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.setReadResults([
      USE_CURRENT,
      actualFrom(createSnapshot({ amount: 1800 })),
    ]);

    const result = await harness.reconciler.submit(request);

    expect(result.conflict).toMatchObject({
      conflictType: "changed-during-write",
      conflictingFields: ["amount"],
    });
  });

  it("keeps the operation uncertain when read-back fails", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.setReadErrorAt(2, new Error("read-back unavailable"));

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      status: "uncertain",
      errorCode: "actual-outcome-uncertain",
    });
  });

  it("preserves a journal error when marking the write uncertain fails", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    const journalError = new Error("journal unavailable");
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    harness.setReadErrorAt(2, new Error("read-back unavailable"));
    harness.journal.failures.set("markUncertain", journalError);

    await expect(harness.reconciler.submit(request)).rejects.toBe(journalError);
    expect(consoleError).toHaveBeenCalledWith(
      "Transaction journal transition failed",
      { errorType: "Error" },
    );
  });

  it("marks a write uncertain when Actual fails after mutation starts", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.setSessionErrorAt(
      1,
      new ActualPostWriteSyncError(
        "local-write-result",
        new Error("sync failed"),
      ),
      true,
    );

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      status: "uncertain",
      errorCode: "actual-outcome-uncertain",
    });
  });

  it.each([
    {
      label: "an untyped failure that imitates a write receipt",
      error: Object.assign(new Error("sync failed"), {
        operationResult: {
          kind: "write-update",
          patch: { amount: 1200 },
        },
      }),
    },
    {
      label: "a typed failure without a result object",
      error: new ActualPostWriteSyncError(null, new Error("sync failed")),
    },
    {
      label: "an unrecognized write result",
      error: new ActualPostWriteSyncError(
        { kind: "unrecognized-write", patch: { amount: 1200 } },
        new Error("sync failed"),
      ),
    },
    {
      label: "an invalid returned patch",
      error: new ActualPostWriteSyncError(
        { kind: "write-update", patch: { unsupported: true } },
        new Error("sync failed"),
      ),
    },
  ])("does not create write evidence for $label", async ({ error }) => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.setSessionErrorAt(1, error, true);

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      status: "uncertain",
      writeEvidence: null,
    });
  });

  it("marks a delete uncertain when Actual fails after deletion starts", async () => {
    const request = createRequest({ action: "delete", patch: {} });
    const harness = createHarness(request);
    harness.setSessionErrorAt(
      1,
      new ActualPostWriteSyncError(
        { kind: "write-delete" },
        new Error("post-delete sync failed"),
      ),
      true,
    );

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      status: "uncertain",
      errorCode: "actual-outcome-uncertain",
      writeEvidence: deleteEvidence,
    });

    const recovered = await harness.reconciler.submit(request);

    expect(recovered.operation).toMatchObject({
      status: "committed",
      actualResult: {
        transactionId: request.transactionId,
        recoveredByReadback: true,
      },
      writeEvidence: null,
    });
  });

  it("releases a claim and preserves the original pre-write Actual error", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    const actualError = new Error("Actual read failed");
    harness.setSessionErrorAt(1, actualError);

    await expect(harness.reconciler.submit(request)).rejects.toBe(actualError);
    expect(harness.journal.operation).toMatchObject({
      status: "queued",
      errorCode: "actual-read-failed",
    });
  });

  it("logs journal transition failures without replacing the Actual error", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    const actualError = new Error("Actual read failed");
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    harness.setSessionErrorAt(1, actualError);
    harness.journal.failures.set("releaseClaim", "postgres disconnected");

    await expect(harness.reconciler.submit(request)).rejects.toBe(actualError);
    expect(consoleError).toHaveBeenCalledWith(
      "Transaction journal transition failed",
      { errorType: "string" },
    );
  });

  it("returns a stored committed result on an idempotent retry", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.journal.initialStatus = "committed";
    harness.journal.operation = createOperation(
      {
        operationId: request.operationId,
        principalId: request.principalId,
        budgetId: request.budgetId,
        idempotencyKey: request.idempotencyKey,
        entityType: "transaction",
        action: request.action,
        entityId: request.transactionId,
        baseRevision: request.baseRevision,
        baseSnapshot: request.baseSnapshot,
        patch: { amount: 1200 },
      },
      "committed",
    );

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("committed");
    expect(harness.sessionCalls).toHaveLength(0);
  });

  it("returns a stored conflict and its conflict record on retry", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.journal.initialStatus = "conflict";
    harness.journal.operation = createOperation(
      {
        operationId: request.operationId,
        principalId: request.principalId,
        budgetId: request.budgetId,
        idempotencyKey: request.idempotencyKey,
        entityType: "transaction",
        action: request.action,
        entityId: request.transactionId,
        baseRevision: request.baseRevision,
        baseSnapshot: request.baseSnapshot,
        patch: { amount: 1200 },
      },
      "conflict",
    );
    harness.journal.conflict = {
      conflictId: randomUUID(),
      operationId: request.operationId,
      conflictType: "same-field-edit",
      baseSnapshot: request.baseSnapshot,
      localPatch: { amount: 1200 },
      remoteSnapshot: { amount: 1800 },
      conflictingFields: ["amount"],
      createdAt: new Date(),
    };
    const result = await harness.reconciler.submit(request);

    expect(result.conflict?.conflictType).toBe("same-field-edit");
  });

  it("returns a stored conflict without a record when conflict lookup misses", async () => {
    const request = createRequest();
    const harness = createHarness(request, { initialStatus: "conflict" });

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("conflict");
    expect(result.conflict).toBeNull();
  });

  it("does not expose a stale conflict record for a committed operation", async () => {
    const request = createRequest();
    const harness = createHarness(request, { initialStatus: "committed" });
    harness.journal.conflict = {
      conflictId: randomUUID(),
      operationId: request.operationId,
      conflictType: "stale-conflict",
      baseSnapshot: request.baseSnapshot,
      localPatch: { amount: 1200 },
      remoteSnapshot: { amount: 1800 },
      conflictingFields: ["amount"],
      createdAt: new Date(),
    };
    const getConflict = vi.spyOn(harness.journal, "getConflict");

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("committed");
    expect(result.conflict).toBeNull();
    expect(getConflict).not.toHaveBeenCalled();
  });

  it("returns the queued operation if another worker already claimed it", async () => {
    const request = createRequest();
    const harness = createHarness(request, { claimAllowed: false });

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("queued");
    expect(harness.sessionCalls).toHaveLength(0);
  });

  it("does not call a matching uncertain update committed without write evidence", async () => {
    const request = createRequest();
    const harness = createHarness(request, { initialStatus: "uncertain" });
    harness.actual = actualFrom(createSnapshot({ amount: 1200 }));

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("uncertain");
    expect(result.operation.actualResult).toBeNull();
    expect(harness.sessionCalls).toHaveLength(0);
  });

  it("does not call an absent uncertain deletion committed without write evidence", async () => {
    const request = createRequest({ action: "delete", patch: {} });
    const harness = createHarness(request, { initialStatus: "uncertain" });
    harness.actual = undefined;

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("uncertain");
    expect(result.operation.actualResult).toBeNull();
    expect(harness.sessionCalls).toHaveLength(0);
  });

  it("recovers a matching uncertain update with Actual write evidence", async () => {
    const request = createRequest();
    const harness = createHarness(request, {
      initialStatus: "uncertain",
      initialWriteEvidence: updateEvidence,
    });
    harness.actual = actualFrom(createSnapshot({ amount: 1200 }));

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      status: "committed",
      actualResult: {
        transactionId: request.transactionId,
        recoveredByReadback: true,
      },
      writeEvidence: null,
    });
    expect(harness.sessionCalls).toHaveLength(1);
  });

  it("recovers an absent uncertain deletion with Actual write evidence", async () => {
    const request = createRequest({ action: "delete", patch: {} });
    const harness = createHarness(request, {
      initialStatus: "uncertain",
      initialWriteEvidence: deleteEvidence,
    });
    harness.actual = undefined;

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      status: "committed",
      actualResult: {
        transactionId: request.transactionId,
        recoveredByReadback: true,
      },
      writeEvidence: null,
    });
  });

  it("does not recover an update with delete write evidence", async () => {
    const request = createRequest();
    const harness = createHarness(request, {
      initialStatus: "uncertain",
      initialWriteEvidence: deleteEvidence,
    });
    harness.actual = undefined;

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("uncertain");
    expect(harness.sessionCalls).toHaveLength(1);
  });

  it("does not recover a deletion with update write evidence", async () => {
    const request = createRequest({ action: "delete", patch: {} });
    const harness = createHarness(request, {
      initialStatus: "uncertain",
      initialWriteEvidence: updateEvidence,
    });
    harness.actual = actualFrom(createSnapshot({ amount: 1200 }));

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("uncertain");
    expect(harness.sessionCalls).toHaveLength(1);
  });

  it("keeps uncertainty when stored update evidence has an invalid patch", async () => {
    const request = createRequest();
    const harness = createHarness(request, {
      initialStatus: "uncertain",
      initialWriteEvidence: {
        kind: "transaction-update-returned",
        appliedPatch: { unsupported: true },
      },
    });
    harness.actual = actualFrom(createSnapshot({ amount: 1200 }));

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("uncertain");
  });

  it("does not recover an uncertain deletion when Actual still has the row", async () => {
    const request = createRequest({ action: "delete", patch: {} });
    const harness = createHarness(request, {
      initialStatus: "uncertain",
      initialWriteEvidence: deleteEvidence,
    });

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("uncertain");
  });

  it("does not resolve an uncertain deletion when Actual cannot be read", async () => {
    const request = createRequest({ action: "delete", patch: {} });
    const harness = createHarness(request, {
      initialStatus: "uncertain",
      initialWriteEvidence: deleteEvidence,
    });
    harness.setSessionErrorAt(1, new Error("Actual unavailable"));

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("uncertain");
  });

  it("leaves a divergent uncertain operation unresolved", async () => {
    const request = createRequest();
    const harness = createHarness(request, {
      initialStatus: "uncertain",
      initialWriteEvidence: updateEvidence,
    });

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("uncertain");
  });

  it("does not replay lease-expired work without a confirmed Actual write receipt", async () => {
    const request = createRequest();
    const harness = createHarness(request, { initialStatus: "uncertain" });
    harness.actual = actualFrom(createSnapshot({ amount: 1200 }));
    const read = vi.spyOn(harness.store, "read");
    const update = vi.spyOn(harness.store, "update");

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("uncertain");
    expect(read).not.toHaveBeenCalled();
    expect(update).not.toHaveBeenCalled();
  });

  it("recovers a write when a no-op requested field changed remotely", async () => {
    const base = createSnapshot({ notes: null });
    const request = createRequest({
      baseSnapshot: base as unknown as JsonValue,
      baseRevision: createTransactionRevision(base),
      patch: { amount: 1200, notes: "" },
    });
    const harness = createHarness(request);
    harness.actual = actualFrom(createSnapshot({ notes: "Remote note" }));
    harness.setSessionErrorAt(
      1,
      new ActualPostWriteSyncError(
        { kind: "write-update", patch: { amount: 1200 } },
        new Error("sync failed"),
      ),
      true,
    );

    const first = await harness.reconciler.submit(request);
    expect(first.operation).toMatchObject({
      status: "uncertain",
      writeEvidence: {
        kind: "transaction-update-returned",
        appliedPatch: { amount: 1200 },
      },
    });

    const recovered = await harness.reconciler.submit(request);

    expect(recovered.operation).toMatchObject({
      status: "committed",
      actualResult: {
        transactionId: request.transactionId,
        recoveredByReadback: true,
      },
    });
    expect(harness.actual).toMatchObject({
      amount: 1200,
      notes: "Remote note",
    });
  });

  it("does not recover an uncertain update when Actual deleted the row", async () => {
    const request = createRequest();
    const harness = createHarness(request, {
      initialStatus: "uncertain",
      initialWriteEvidence: updateEvidence,
    });
    harness.actual = undefined;

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("uncertain");
  });

  it("keeps uncertainty when read-back or journal recovery fails", async () => {
    const request = createRequest();
    const harness = createHarness(request, {
      initialStatus: "uncertain",
      initialWriteEvidence: updateEvidence,
    });
    harness.actual = actualFrom(createSnapshot({ amount: 1200 }));
    harness.setSessionErrorAt(1, new Error("Actual unavailable"));

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("uncertain");
  });

  it("keeps uncertainty when verified read-back cannot be journaled", async () => {
    const request = createRequest();
    const harness = createHarness(request, {
      initialStatus: "uncertain",
      initialWriteEvidence: updateEvidence,
    });
    harness.actual = actualFrom(createSnapshot({ amount: 1200 }));
    harness.journal.failures.set(
      "resolveUncertainAsCommitted",
      new Error("journal write failed"),
    );

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("uncertain");
  });

  it("preserves the mark-commit error if reading journal state fails", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    const commitError = new Error("transition rejected");
    vi.spyOn(harness.journal, "markCommitted").mockRejectedValueOnce(
      commitError,
    );
    harness.journal.getError = new Error("journal unavailable");

    await expect(harness.reconciler.submit(request)).rejects.toBe(commitError);
  });

  it("preserves the mark-commit error if the operation is missing", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    const commitError = new Error("transition rejected");
    vi.spyOn(harness.journal, "markCommitted").mockRejectedValueOnce(
      commitError,
    );
    harness.journal.missingOnGet = true;

    await expect(harness.reconciler.submit(request)).rejects.toBe(commitError);
  });

  it("rechecks an already committed result after an interrupted journal response", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    const markCommitted = harness.journal.markCommitted.bind(harness.journal);
    vi.spyOn(harness.journal, "markCommitted").mockImplementationOnce(
      async (operationId, actualResult) => {
        await markCommitted(operationId, actualResult);
        throw new Error("connection closed after commit");
      },
    );

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("committed");
  });

  it("resolves a verified write after the lease expires during Actual work", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    vi.spyOn(harness.journal, "markCommitted").mockImplementationOnce(
      async () => {
        harness.journal.operation = {
          ...harness.journal.operation!,
          status: "uncertain",
          errorCode: "claim-lease-expired",
        };
        throw new Error("cannot transition from uncertain to committed");
      },
    );
    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("committed");
  });

  it("does not resolve a verified write for another uncertain cause", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    const commitError = new Error("transition rejected");
    vi.spyOn(harness.journal, "markCommitted").mockImplementationOnce(
      async () => {
        harness.journal.operation = {
          ...harness.journal.operation!,
          status: "uncertain",
          errorCode: "other-cause",
        };
        throw commitError;
      },
    );

    await expect(harness.reconciler.submit(request)).rejects.toBe(commitError);
  });

  it("fails clearly if a conflict cannot be read after it is recorded", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.actual = undefined;
    harness.journal.missingOnGet = true;

    await expect(harness.reconciler.submit(request)).rejects.toThrow(
      "Transaction conflict has no operation record",
    );
  });

  it.each([
    {
      label: "invalid action",
      overrides: { action: "create" },
      message: "Transaction action must be update or delete",
    },
    {
      label: "empty transaction ID",
      overrides: { transactionId: " " },
      message: "transactionId must be a non-empty string",
    },
    {
      label: "non-string transaction ID",
      overrides: { transactionId: 100 },
      message: "transactionId must be a non-empty string",
    },
    {
      label: "missing base revision",
      overrides: { baseRevision: "" },
      message: "baseRevision is required",
    },
    {
      label: "non-string base revision",
      overrides: { baseRevision: 100 },
      message: "baseRevision is required",
    },
    {
      label: "mismatched base snapshot ID",
      overrides: { transactionId: "another-id" },
      message: "baseSnapshot ID does not match transactionId",
    },
    {
      label: "invalid base revision",
      overrides: { baseRevision: "stale-revision" },
      message: "baseRevision does not match baseSnapshot",
    },
    {
      label: "empty update patch",
      overrides: { patch: {} },
      message: "transaction patch must contain at least one field",
    },
    {
      label: "unsupported update patch field",
      overrides: { patch: { transferId: "x" } },
      message: "transaction patch contains unsupported fields: transferId",
    },
    {
      label: "invalid update amount",
      overrides: { patch: { amount: 1.5 } },
      message: "amount must be a safe integer",
    },
    {
      label: "null delete patch",
      overrides: { action: "delete", patch: null },
      message: "delete patch must be an empty JSON object",
    },
    {
      label: "array delete patch",
      overrides: { action: "delete", patch: [] },
      message: "delete patch must be an empty JSON object",
    },
    {
      label: "primitive delete patch",
      overrides: { action: "delete", patch: "" },
      message: "delete patch must be an empty JSON object",
    },
    {
      label: "non-empty delete patch",
      overrides: { action: "delete", patch: { amount: 1 } },
      message: "delete patch must be an empty JSON object",
    },
  ] as Array<{
    label: string;
    overrides: Record<string, unknown>;
    message: string;
  }>)("rejects $label before enqueue", async ({ overrides, message }) => {
    const request = createRequest(overrides as never);
    const harness = createHarness(request);

    await expect(harness.reconciler.submit(request)).rejects.toThrow(message);
    expect(harness.journal.operation).toBeUndefined();
  });
});
