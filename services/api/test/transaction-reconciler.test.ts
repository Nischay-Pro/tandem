import { randomUUID } from "node:crypto";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  ActualPostWriteSyncError,
  type BudgetOperationOptions,
} from "../src/actual/actual-session.js";
import {
  TransactionReconciler,
  TransactionRequestValidationError,
  type ActualBudgetSessionPort,
  type TransactionCreateRequest,
  type TransactionReconciliationRequest,
  type TransactionUpdateRequest,
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
import { InvalidOperationInputError } from "../src/write-journal/contracts.js";
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
  overrides: Partial<TransactionUpdateRequest> = {},
): TransactionUpdateRequest {
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

function createCreateRequest(
  overrides: Partial<TransactionCreateRequest> = {},
): TransactionCreateRequest {
  return {
    operationId: randomUUID(),
    principalId: `device-${randomUUID()}`,
    budgetId: `budget-${randomUUID()}`,
    idempotencyKey: `request-${randomUUID()}`,
    transactionId: `tandem:${randomUUID()}`,
    action: "create",
    patch: {
      account: "account-1",
      amount: -1250,
      date: "2026-11-01",
      payee: "payee-1",
      category: "category-1",
      notes: "Created from Companion",
      cleared: false,
    },
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
  resolveCalls = 0;
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

  async markRejected(
    _operationId: string,
    errorCode: string,
  ): Promise<WriteOperation> {
    this.#throwIfFailed("markRejected");
    const operation = this.#requireOperation();
    if (operation.status !== "applying") {
      throw new Error(`Cannot reject from ${operation.status}`);
    }
    this.operation = {
      ...operation,
      status: "rejected",
      errorCode,
      actualResult: null,
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
    if (this.missingOnGet) {
      throw new Error("Operation is missing");
    }
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
    this.resolveCalls += 1;
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
  request: TransactionReconciliationRequest = createRequest(),
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
  const base =
    request.action === "create"
      ? null
      : (request.baseSnapshot as unknown as TransactionSnapshot);
  let actual: unknown | undefined =
    base === null ? undefined : actualFrom(base);
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
    async validateCreateInput() {
      return undefined;
    },
    async create(id, input) {
      actual = {
        id: randomUUID(),
        account: input.account,
        amount: input.amount,
        date: input.date,
        payee: input.payee,
        category: input.category,
        notes: input.notes,
        cleared: input.cleared,
        imported_id: id,
      };
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

  it("creates a transaction once using its stable Companion ID", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request);

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      action: "create",
      entityId: request.transactionId,
      baseRevision: null,
      status: "committed",
      actualResult: {
        transactionId: request.transactionId,
        created: true,
      },
    });
    expect(harness.actual).toMatchObject({
      imported_id: request.transactionId,
      amount: -1250,
      account: "account-1",
    });
    expect(harness.syncPredicateResults).toEqual([true]);
  });

  it("rejects an invalid Actual reference before writing and journals it as rejected", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request);
    const create = vi.spyOn(harness.store, "create");
    vi.spyOn(harness.store, "validateCreateInput").mockResolvedValue({
      code: "account-not-found",
      message: "Account does not exist in this budget",
    });

    await expect(harness.reconciler.submit(request)).rejects.toMatchObject({
      name: "TransactionRequestValidationError",
      message: "Account does not exist in this budget",
      errorCode: "account-not-found",
    });

    expect(create).not.toHaveBeenCalled();
    expect(harness.actual).toBeUndefined();
    expect(harness.journal.operation).toMatchObject({
      status: "rejected",
      errorCode: "account-not-found",
    });
  });

  it("returns a previously committed create without applying it again", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request, { initialStatus: "committed" });

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("committed");
    expect(harness.actual).toBeUndefined();
    expect(harness.sessionCalls).toHaveLength(0);
  });

  it("returns a queued create when another worker already claimed it", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request, { claimAllowed: false });

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("queued");
    expect(harness.actual).toBeUndefined();
    expect(harness.sessionCalls).toHaveLength(0);
  });

  it("records a conflict instead of replacing an existing stable transaction", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request);
    harness.actual = {
      id: "actual-existing-id",
      account: "account-1",
      amount: -1250,
      date: "2026-11-01",
      imported_id: request.transactionId,
    };

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      action: "create",
      status: "conflict",
    });
    expect(result.conflict).toMatchObject({
      conflictType: "transaction-id-already-exists",
      conflictingFields: ["$entity"],
    });
    expect(harness.actual).toMatchObject({ id: "actual-existing-id" });
  });

  it("preserves a conflict journal error when a create ID already exists", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request);
    const conflictError = new Error("conflict journal failed");
    harness.actual = {
      id: "actual-existing-id",
      account: "account-1",
      amount: -1250,
      date: "2026-11-01",
      imported_id: request.transactionId,
    };
    harness.journal.failures.set("recordConflict", conflictError);

    await expect(harness.reconciler.submit(request)).rejects.toBe(
      conflictError,
    );
    expect(harness.journal.operation).toMatchObject({ status: "queued" });
  });

  it("keeps a create uncertain when Actual returns a different transaction ID", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request);
    vi.spyOn(harness.store, "create").mockResolvedValue({
      id: "actual-unexpected-id",
      account: "account-1",
      amount: -1250,
      date: "2026-11-01",
    });

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      action: "create",
      status: "uncertain",
      writeEvidence: { kind: "transaction-create-returned" },
    });
  });

  it("marks a completed create uncertain when its final journal commit fails", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request);
    vi.spyOn(harness.journal, "markCommitted").mockRejectedValueOnce(
      new Error("commit response was lost"),
    );

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      action: "create",
      status: "uncertain",
      writeEvidence: { kind: "transaction-create-returned" },
    });
  });

  it("recovers an uncertain create by reading its stable imported ID", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request, {
      initialStatus: "uncertain",
      initialWriteEvidence: { kind: "transaction-create-returned" },
    });
    harness.actual = {
      id: "actual-random-id",
      account: "account-1",
      amount: -1250,
      date: "2026-11-01",
      imported_id: request.transactionId,
    };

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      status: "committed",
      actualResult: {
        transactionId: request.transactionId,
        recoveredByReadback: true,
      },
    });
    expect(harness.journal.resolveCalls).toBe(1);
  });

  it("does not replay an uncertain create without read-back evidence", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request, { initialStatus: "uncertain" });

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("uncertain");
    expect(harness.actual).toBeUndefined();
    expect(harness.journal.resolveCalls).toBe(0);
  });

  it("keeps an uncertain create unresolved when Actual read-back fails", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request, {
      initialStatus: "uncertain",
      initialWriteEvidence: { kind: "transaction-create-returned" },
    });
    harness.setReadErrorAt(1, new Error("Actual is temporarily unavailable"));

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("uncertain");
    expect(harness.journal.resolveCalls).toBe(0);
  });

  it("leaves an uncertain create unresolved when Actual read-back is malformed", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request, {
      initialStatus: "uncertain",
      initialWriteEvidence: { kind: "transaction-create-returned" },
    });
    harness.actual = { id: request.transactionId };

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("uncertain");
    expect(harness.journal.resolveCalls).toBe(0);
  });

  it.each(["id", "account", "amount", "date"] as const)(
    "does not recover an uncertain create when Actual read-back %s differs",
    async (field) => {
      const request = createCreateRequest();
      const harness = createHarness(request, {
        initialStatus: "uncertain",
        initialWriteEvidence: { kind: "transaction-create-returned" },
      });
      const actual: Record<string, unknown> = {
        id: "actual-random-id",
        account: "account-1",
        amount: -1250,
        date: "2026-11-01",
        imported_id: request.transactionId,
      };
      if (field === "id")
        actual.imported_id = "tandem:00000000-0000-4000-8000-000000000000";
      if (field === "account") actual.account = "different-account";
      if (field === "amount") actual.amount = -1300;
      if (field === "date") actual.date = "2026-10-31";
      harness.actual = actual;

      const result = await harness.reconciler.submit(request);

      expect(result.operation.status).toBe("uncertain");
      expect(harness.journal.resolveCalls).toBe(0);
    },
  );

  it("keeps an uncertain create when the journal cannot record read-back", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request, {
      initialStatus: "uncertain",
      initialWriteEvidence: { kind: "transaction-create-returned" },
    });
    harness.actual = {
      id: "actual-random-id",
      account: "account-1",
      amount: -1250,
      date: "2026-11-01",
      imported_id: request.transactionId,
    };
    harness.journal.failures.set(
      "resolveUncertainAsCommitted",
      new Error("synthetic journal failure"),
    );

    const result = await harness.reconciler.submit(request);

    expect(result.operation.status).toBe("uncertain");
    expect(harness.journal.resolveCalls).toBe(1);
  });

  it("marks create as uncertain after Actual writes but sync fails", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request);
    harness.setSessionErrorAt(
      1,
      new ActualPostWriteSyncError(
        { kind: "write-create" },
        new Error("post-create sync failed"),
      ),
      true,
    );

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      action: "create",
      status: "uncertain",
      writeEvidence: { kind: "transaction-create-returned" },
    });
  });

  it("does not claim a create write receipt when Actual creation throws", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request);
    vi.spyOn(harness.store, "create").mockRejectedValue(
      new Error("Actual create timed out"),
    );

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      action: "create",
      status: "uncertain",
      writeEvidence: null,
    });
  });

  it("validates create idempotency keys before enqueue", async () => {
    for (const idempotencyKey of [" ", 42, "x".repeat(201)]) {
      const request = createCreateRequest({ idempotencyKey } as never);
      const harness = createHarness(request);

      await expect(harness.reconciler.submit(request)).rejects.toMatchObject({
        name: "TransactionRequestValidationError",
      });
      expect(harness.journal.operation).toBeUndefined();
    }
  });

  it("maps typed create journal input errors to transaction validation errors", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request);
    harness.journal.failures.set(
      "enqueue",
      new InvalidOperationInputError("create operation input is invalid"),
    );

    await expect(harness.reconciler.submit(request)).rejects.toMatchObject({
      name: "TransactionRequestValidationError",
      message: "create operation input is invalid",
    });
    expect(harness.journal.operation).toBeUndefined();
  });

  it("preserves non-validation journal errors from create enqueue", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request);
    const error = new Error("create journal unavailable");
    harness.journal.failures.set("enqueue", error);

    await expect(harness.reconciler.submit(request)).rejects.toBe(error);
    expect(harness.journal.operation).toBeUndefined();
  });

  it("preserves an existing validation error while preparing a create", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request);
    const validationError = new TransactionRequestValidationError(
      "prevalidated create failure",
    );
    Object.defineProperty(request, "transactionId", {
      get() {
        throw validationError;
      },
    });

    await expect(harness.reconciler.submit(request)).rejects.toBe(
      validationError,
    );
  });

  it("rejects create requests without a stable Companion transaction ID", async () => {
    const request = createCreateRequest({ transactionId: "unstable-id" });
    const harness = createHarness(request);

    await expect(harness.reconciler.submit(request)).rejects.toMatchObject({
      name: "TransactionRequestValidationError",
      message: "transactionId must be a stable Companion transaction ID",
    });
    expect(harness.journal.operation).toBeUndefined();
  });

  it("preserves an existing transaction validation error from malformed input", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    const validationError = new TransactionRequestValidationError(
      "prevalidated failure",
    );
    Object.defineProperty(request, "idempotencyKey", {
      get() {
        throw validationError;
      },
    });

    await expect(harness.reconciler.submit(request)).rejects.toBe(
      validationError,
    );
  });

  it("preserves a typed action error during request dispatch", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    const validationError = new TransactionRequestValidationError(
      "action could not be read",
    );
    Object.defineProperty(request, "action", {
      get() {
        throw validationError;
      },
    });

    await expect(harness.reconciler.submit(request)).rejects.toBe(
      validationError,
    );
  });

  it("uses the Error message when reading the request action fails", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    Object.defineProperty(request, "action", {
      get() {
        throw new Error("action getter failed");
      },
    });

    await expect(harness.reconciler.submit(request)).rejects.toMatchObject({
      name: "TransactionRequestValidationError",
      message: "action getter failed",
    });
  });

  it("wraps primitive errors while preparing an update request", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    Object.defineProperty(request, "idempotencyKey", {
      get() {
        throw "synthetic invalid update input";
      },
    });

    await expect(harness.reconciler.submit(request)).rejects.toMatchObject({
      name: "TransactionRequestValidationError",
      message: "Transaction request is invalid",
      cause: "synthetic invalid update input",
    });
  });

  it("wraps primitive errors while preparing a create request", async () => {
    const request = createCreateRequest();
    const harness = createHarness(request);
    Object.defineProperty(request, "idempotencyKey", {
      get() {
        throw "synthetic invalid create input";
      },
    });

    await expect(harness.reconciler.submit(request)).rejects.toMatchObject({
      name: "TransactionRequestValidationError",
      message: "Transaction request is invalid",
      cause: "synthetic invalid create input",
    });
  });

  it("wraps non-Error values thrown while reading malformed input", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    Object.defineProperty(request, "action", {
      get() {
        throw "synthetic invalid input";
      },
    });

    await expect(harness.reconciler.submit(request)).rejects.toMatchObject({
      name: "TransactionRequestValidationError",
      message: "Transaction request is invalid",
      cause: "synthetic invalid input",
    });
  });

  it("maps typed journal input failures to transaction validation errors", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.journal.failures.set(
      "enqueue",
      new InvalidOperationInputError("idempotencyKey is invalid"),
    );

    await expect(harness.reconciler.submit(request)).rejects.toMatchObject({
      name: "TransactionRequestValidationError",
      message: "idempotencyKey is invalid",
    });
    expect(harness.journal.operation).toBeUndefined();
  });

  it("preserves non-validation journal failures from enqueue", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    const storageFailure = new Error("synthetic database failure");
    harness.journal.failures.set("enqueue", storageFailure);

    await expect(harness.reconciler.submit(request)).rejects.toBe(
      storageFailure,
    );
  });

  it.each(["   ", "x".repeat(201), 123 as unknown as string])(
    "rejects invalid idempotency key %j before journal insertion",
    async (idempotencyKey) => {
      const request = createRequest({ idempotencyKey });
      const harness = createHarness(request);

      await expect(harness.reconciler.submit(request)).rejects.toMatchObject({
        name: "TransactionRequestValidationError",
        message: "idempotencyKey must contain between 1 and 200 characters",
      });
      expect(harness.journal.operation).toBeUndefined();
    },
  );

  it("accepts an idempotency key at the 200-character limit", async () => {
    const idempotencyKey = "x".repeat(200);
    const request = createRequest({ idempotencyKey });
    const harness = createHarness(request);

    const result = await harness.reconciler.submit(request);

    expect(result.operation.idempotencyKey).toBe(idempotencyKey);
    expect(result.operation.entityType).toBe("transaction");
    expect(result.operation.status).toBe("committed");
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

  it("keeps split transaction edits behind group review", async () => {
    const base = createSnapshot({ transferId: "transfer-1", isParent: true });
    const request = createRequest({
      baseSnapshot: base as unknown as JsonValue,
      baseRevision: createTransactionRevision(base),
    });
    const harness = createHarness(request);
    harness.actual = actualFrom(createSnapshot());

    const result = await harness.reconciler.submit(request);

    expect(result.conflict).toMatchObject({
      conflictType: "split-transaction-requires-group-edit",
      conflictingFields: ["transferId", "isParent"],
    });
  });

  it("requires a valid paired Actual leg before changing a transfer", async () => {
    const base = createSnapshot({ transferId: "transfer-1" });
    const request = createRequest({
      baseSnapshot: base as unknown as JsonValue,
      baseRevision: createTransactionRevision(base),
    });
    const harness = createHarness(request);
    harness.actual = actualFrom(base);

    const result = await harness.reconciler.submit(request);

    expect(result.conflict).toMatchObject({
      conflictType: "transfer-pair-changed",
      conflictingFields: ["transferId", "amount"],
    });
    expect(harness.actual).toEqual(actualFrom(base));
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

  it("reports split children as the group-edit conflict field", async () => {
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
      conflictType: "split-transaction-requires-group-edit",
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

  it("records a transfer pair that cannot be verified after an update", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.setReadResults([
      USE_CURRENT,
      actualFrom(createSnapshot({ amount: 1200, transferId: "transfer-2" })),
    ]);

    const result = await harness.reconciler.submit(request);

    expect(result.conflict).toMatchObject({
      conflictType: "transfer-pair-changed-after-write",
      conflictingFields: ["transferId", "amount"],
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
      writeEvidence: updateEvidence,
    });
  });

  it("does not leave a successful Actual write applying when read-back is malformed", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    harness.setReadResults([
      USE_CURRENT,
      {
        id: request.transactionId,
        account: "",
        amount: 1200,
        date: "2026-11-01",
      },
    ]);

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      status: "uncertain",
      writeEvidence: updateEvidence,
    });
  });

  it("marks a successful Actual write uncertain when its commit transition fails", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    vi.spyOn(harness.journal, "markCommitted").mockRejectedValueOnce(
      new Error("journal transition failed"),
    );

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      status: "uncertain",
      writeEvidence: updateEvidence,
    });
  });

  it("marks a successful Actual deletion uncertain when its commit transition fails", async () => {
    const request = createRequest({ action: "delete", patch: {} });
    const harness = createHarness(request);
    vi.spyOn(harness.journal, "markCommitted").mockRejectedValueOnce(
      new Error("journal transition failed"),
    );

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      status: "uncertain",
      writeEvidence: deleteEvidence,
    });
  });

  it("releases a no-op claim without creating write evidence when finalization fails", async () => {
    const request = createRequest({ action: "delete", patch: {} });
    const harness = createHarness(request);
    harness.actual = undefined;
    const commitError = new Error("journal transition failed");
    vi.spyOn(harness.journal, "markCommitted").mockRejectedValueOnce(
      commitError,
    );

    await expect(harness.reconciler.submit(request)).rejects.toBe(commitError);
    expect(harness.journal.operation).toMatchObject({
      status: "queued",
      errorCode: "operation-finalization-failed",
      writeEvidence: null,
    });
  });

  it("preserves a journal error when marking the write uncertain fails", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    const actualError = new Error("read-back unavailable");
    const journalError = new Error("journal unavailable");
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    harness.setReadErrorAt(2, actualError);
    harness.journal.failures.set("markUncertain", journalError);

    await expect(harness.reconciler.submit(request)).rejects.toBe(actualError);
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

  it("marks the operation uncertain if journal state cannot be read after a write", async () => {
    const request = createRequest();
    const harness = createHarness(request);
    const commitError = new Error("transition rejected");
    vi.spyOn(harness.journal, "markCommitted").mockRejectedValueOnce(
      commitError,
    );
    harness.journal.getError = new Error("journal unavailable");

    const result = await harness.reconciler.submit(request);

    expect(result.operation).toMatchObject({
      status: "uncertain",
      writeEvidence: updateEvidence,
    });
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
      overrides: { action: "archive" },
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
