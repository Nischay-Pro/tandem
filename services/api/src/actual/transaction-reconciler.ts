import { randomUUID } from "node:crypto";
import {
  ActualPostWriteSyncError,
  type BudgetOperationOptions,
} from "./actual-session.js";
import type { ActualTransactionStore } from "./transaction-store.js";
import {
  createTransactionRevision,
  isCompanionTransactionId,
  parseTransactionCreateInput,
  parseTransactionPatch,
  parseTransactionSnapshot,
  planTransactionDeletion,
  planTransactionUpdate,
  snapshotActualTransaction,
  type TransactionPatch,
  type TransactionCreateInput,
  type TransactionSnapshot,
} from "./transaction-reconciliation.js";
import type {
  ActualWriteEvidence,
  ConflictInput,
  DurableWriteJournal,
  JsonValue,
  OperationConflict,
  WriteOperation,
  WriteOperationInput,
} from "../write-journal/contracts.js";
import {
  IDEMPOTENCY_KEY_MAX_LENGTH,
  InvalidOperationInputError,
} from "../write-journal/contracts.js";
import { WRITE_OPERATION_LEASE_RENEWAL_INTERVAL_MS } from "../write-journal/contracts.js";

interface TransactionRequestBase {
  operationId: string;
  principalId: string;
  budgetId: string;
  idempotencyKey: string;
  transactionId: string;
  patch: JsonValue;
  encryptionPassword?: string;
}

export interface TransactionCreateRequest extends TransactionRequestBase {
  action: "create";
}

export interface TransactionUpdateRequest extends TransactionRequestBase {
  action: "update" | "delete";
  baseRevision: string;
  baseSnapshot: JsonValue;
}

export type TransactionReconciliationRequest =
  TransactionCreateRequest | TransactionUpdateRequest;

export interface BudgetLockPort {
  withBudgetLock<T>(budgetId: string, work: () => Promise<T>): Promise<T>;
}

export interface ActualBudgetSessionPort {
  runBudget<T>(
    syncId: string,
    operation: () => Promise<T>,
    options?: BudgetOperationOptions,
  ): Promise<T>;
  runBudgetSnapshot?<T>(
    syncId: string,
    operation: (changed: boolean) => Promise<T>,
    options?: BudgetOperationOptions,
  ): Promise<{ changed: boolean; value: T }>;
}

export interface TransactionReconciliationResult {
  operation: WriteOperation;
  conflict: OperationConflict | null;
}

export class TransactionRequestValidationError extends Error {
  constructor(
    message: string,
    options?: ErrorOptions,
    readonly errorCode = "invalid_request",
  ) {
    super(message, options);
    this.name = "TransactionRequestValidationError";
  }
}

interface PreparedRequest {
  request: TransactionUpdateRequest;
  base: TransactionSnapshot;
  patch: TransactionPatch;
  operationInput: WriteOperationInput;
}

interface PreparedCreateRequest {
  request: TransactionCreateRequest;
  input: TransactionCreateInput;
  operationInput: WriteOperationInput;
}

type InitialPlan =
  | {
      kind: "conflict";
      conflictType: string;
      remoteSnapshot: JsonValue;
      conflictingFields: string[];
    }
  | { kind: "rejected"; errorCode: string }
  | { kind: "unchanged"; remoteSnapshot: TransactionSnapshot }
  | { kind: "already-absent" }
  | { kind: "write-update"; patch: TransactionPatch }
  | { kind: "write-delete" };

const SYNC_OPTIONS = (result: unknown): boolean =>
  isRecord(result) &&
  (result.kind === "write-update" ||
    result.kind === "write-delete" ||
    result.kind === "write-create");

export class TransactionReconciler {
  constructor(
    private readonly locks: BudgetLockPort,
    private readonly journal: DurableWriteJournal,
    private readonly session: ActualBudgetSessionPort,
    private readonly transactions: ActualTransactionStore,
  ) {}

  async submit(
    request: TransactionReconciliationRequest,
  ): Promise<TransactionReconciliationResult> {
    let action: TransactionReconciliationRequest["action"];
    try {
      action = request.action;
    } catch (error) {
      if (error instanceof TransactionRequestValidationError) {
        throw error;
      }
      throw new TransactionRequestValidationError(
        error instanceof Error
          ? error.message
          : "Transaction request is invalid",
        { cause: error },
      );
    }
    if (action === "create") {
      return this.#submitCreate(request as TransactionCreateRequest);
    }
    const prepared = prepareRequest(request as TransactionUpdateRequest);
    let operation: WriteOperation;
    try {
      ({ operation } = await this.journal.enqueue(prepared.operationInput));
    } catch (error) {
      if (error instanceof InvalidOperationInputError) {
        throw new TransactionRequestValidationError(error.message, {
          cause: error,
        });
      }
      throw error;
    }

    if (operation.status === "uncertain") {
      return this.locks.withBudgetLock(operation.budgetId, async () => {
        const recovered = await this.#recoverUncertain(operation, prepared);
        if (recovered.operation.status !== "queued") return recovered;
        const claim = await this.journal.claim(operation.operationId);
        if (!claim.claimed) return this.#existingResult(claim.operation);
        return this.#applyClaimed(claim.operation, prepared);
      });
    }
    if (operation.status !== "queued") {
      return this.#existingResult(operation);
    }

    return this.locks.withBudgetLock(operation.budgetId, async () => {
      const claim = await this.journal.claim(operation.operationId);
      if (!claim.claimed) {
        return this.#existingResult(claim.operation);
      }
      return this.#applyClaimed(claim.operation, prepared);
    });
  }

  async #submitCreate(
    request: TransactionCreateRequest,
  ): Promise<TransactionReconciliationResult> {
    const prepared = prepareCreateRequest(request);
    let operation: WriteOperation;
    try {
      ({ operation } = await this.journal.enqueue(prepared.operationInput));
    } catch (error) {
      if (error instanceof InvalidOperationInputError) {
        throw new TransactionRequestValidationError(error.message, {
          cause: error,
        });
      }
      throw error;
    }

    if (operation.status === "uncertain") {
      return this.locks.withBudgetLock(operation.budgetId, async () => {
        const recovered = await this.#recoverUncertainCreate(
          operation,
          prepared,
        );
        if (recovered.operation.status !== "queued") return recovered;
        const claim = await this.journal.claim(operation.operationId);
        if (!claim.claimed) return this.#existingResult(claim.operation);
        return withLeaseRenewal(this.journal, operation.operationId, () =>
          this.#applyClaimedCreate(claim.operation, prepared),
        );
      });
    }
    if (operation.status !== "queued") {
      return this.#existingResult(operation);
    }

    return this.locks.withBudgetLock(operation.budgetId, async () => {
      const claim = await this.journal.claim(operation.operationId);
      if (!claim.claimed) {
        return this.#existingResult(claim.operation);
      }
      return withLeaseRenewal(this.journal, operation.operationId, () =>
        this.#applyClaimedCreate(claim.operation, prepared),
      );
    });
  }

  async checkOperationStatus(
    operationId: string,
    encryptionPassword?: string,
  ): Promise<TransactionReconciliationResult | undefined> {
    const operation = await this.journal.get(operationId);
    if (!operation) return undefined;
    if (operation.status !== "uncertain")
      return this.#existingResult(operation);

    return this.locks.withBudgetLock(operation.budgetId, async () => {
      const current = await this.journal.get(operationId);
      if (!current) return undefined;
      if (current.status !== "uncertain") return this.#existingResult(current);
      try {
        if (current.action === "create") {
          const prepared = prepareCreateRequest({
            operationId: current.operationId,
            principalId: current.principalId,
            budgetId: current.budgetId,
            idempotencyKey: current.idempotencyKey,
            transactionId: current.entityId ?? "",
            action: "create",
            patch: current.patch,
            ...(encryptionPassword === undefined ? {} : { encryptionPassword }),
          });
          return this.#recoverUncertainCreate(current, prepared);
        }
        if (current.action !== "update" && current.action !== "delete") {
          return this.#existingResult(current);
        }
        const prepared = prepareRequest({
          operationId: current.operationId,
          principalId: current.principalId,
          budgetId: current.budgetId,
          idempotencyKey: current.idempotencyKey,
          transactionId: current.entityId ?? "",
          action: current.action,
          baseRevision: current.baseRevision ?? "",
          baseSnapshot: current.baseSnapshot,
          patch: current.patch,
          ...(encryptionPassword === undefined ? {} : { encryptionPassword }),
        });
        return this.#recoverUncertain(current, prepared);
      } catch (error) {
        if (error instanceof TransactionRequestValidationError) {
          return this.#existingResult(current);
        }
        throw error;
      }
    });
  }

  async #applyClaimedCreate(
    operation: WriteOperation,
    prepared: PreparedCreateRequest,
  ): Promise<TransactionReconciliationResult> {
    let mutationStarted = false;
    let actualWriteReturned = false;
    let result:
      | {
          kind: "write-create";
          snapshot: TransactionSnapshot;
        }
      | {
          kind: "create-id-exists";
          snapshot: TransactionSnapshot;
        };
    try {
      result = await this.session.runBudget(
        operation.budgetId,
        async () => {
          const existing = await this.transactions.read(
            prepared.request.transactionId,
          );
          if (existing !== undefined) {
            return {
              kind: "create-id-exists",
              snapshot: snapshotActualTransaction(existing),
            };
          }

          const validationIssue = await this.transactions.validateCreateInput(
            prepared.input,
          );
          if (validationIssue) {
            throw new TransactionRequestValidationError(
              validationIssue.message,
              undefined,
              validationIssue.code,
            );
          }

          mutationStarted = true;
          const created = await this.transactions.create(
            prepared.request.transactionId,
            prepared.input,
          );
          actualWriteReturned = true;
          const snapshot = snapshotActualTransaction(created);
          if (snapshot.id !== prepared.request.transactionId) {
            throw new Error("Actual returned a different transaction ID");
          }
          return { kind: "write-create", snapshot };
        },
        runOptions(prepared.request.encryptionPassword, SYNC_OPTIONS),
      );
    } catch (error) {
      return this.#handleActualFailure(
        operation,
        mutationStarted,
        error,
        writeEvidenceFromPostWriteError(error) ??
          (actualWriteReturned
            ? { kind: "transaction-create-returned" }
            : undefined),
      );
    }

    if (result.kind === "create-id-exists") {
      try {
        return await this.#recordConflict(operation, {
          kind: "conflict",
          conflictType: "transaction-id-already-exists",
          remoteSnapshot: toJsonValue(result.snapshot),
          conflictingFields: ["$entity"],
        });
      } catch (error) {
        return this.#handleActualFailure(operation, false, error);
      }
    }

    try {
      const committed = await this.#markVerifiedCommitted(
        operation,
        {
          transactionId: prepared.request.transactionId,
          created: true,
          verifiedRevision: createTransactionRevision(result.snapshot),
        },
        { kind: "transaction-create-returned" },
      );
      return { operation: committed, conflict: null };
    } catch (error) {
      return this.#handleActualFailure(
        operation,
        true,
        error,
        { kind: "transaction-create-returned" },
        "operation-finalization-failed",
      );
    }
  }

  async #applyClaimed(
    operation: WriteOperation,
    prepared: PreparedRequest,
  ): Promise<TransactionReconciliationResult> {
    return withLeaseRenewal(this.journal, operation.operationId, () =>
      this.#applyClaimedOperation(operation, prepared),
    );
  }

  async #applyClaimedOperation(
    operation: WriteOperation,
    prepared: PreparedRequest,
  ): Promise<TransactionReconciliationResult> {
    let mutationStarted = false;
    let plan: InitialPlan;
    try {
      plan = await this.session.runBudget(
        operation.budgetId,
        async () => {
          const latestRaw = await this.transactions.read(
            prepared.request.transactionId,
          );
          if (latestRaw === undefined) {
            return operation.action === "delete"
              ? { kind: "already-absent" }
              : {
                  kind: "conflict",
                  conflictType: "deleted-remotely",
                  remoteSnapshot: null,
                  conflictingFields: ["$entity"],
                };
          }

          const latest = snapshotActualTransaction(latestRaw);
          if (latest.id !== prepared.request.transactionId) {
            throw new Error("Actual returned a different transaction ID");
          }
          if (prepared.base.transferId !== latest.transferId) {
            return {
              kind: "conflict",
              conflictType: "transfer-pair-changed",
              remoteSnapshot: toJsonValue(latest),
              conflictingFields: ["transferId"],
            };
          }
          if (latest.transferId !== null) {
            const counterpartRaw = await this.transactions.read(
              latest.transferId,
            );
            if (counterpartRaw === undefined) {
              return {
                kind: "conflict",
                conflictType: "transfer-pair-incomplete",
                remoteSnapshot: toJsonValue(latest),
                conflictingFields: ["transferId"],
              };
            }
            const counterpart = snapshotActualTransaction(counterpartRaw);
            if (
              counterpart.id !== latest.transferId ||
              counterpart.transferId !== latest.id ||
              counterpart.amount !== -latest.amount
            ) {
              return {
                kind: "conflict",
                conflictType: "transfer-pair-changed",
                remoteSnapshot: toJsonValue(latest),
                conflictingFields: ["transferId", "amount"],
              };
            }
          }

          if (operation.action === "update") {
            const updatePlan = planTransactionUpdate(
              prepared.base,
              latest,
              prepared.patch,
            );
            if (updatePlan.kind === "conflict") {
              return {
                kind: "conflict",
                conflictType: "same-field-edit",
                remoteSnapshot: toJsonValue(latest),
                conflictingFields: updatePlan.conflictingFields,
              };
            }
            if (updatePlan.kind === "unsupported") {
              return {
                kind: "rejected",
                errorCode: "split-child-field-not-editable",
              };
            }
            if (updatePlan.kind === "unchanged") {
              return { kind: "unchanged", remoteSnapshot: latest };
            }
            mutationStarted = true;
            await this.transactions.update(
              prepared.request.transactionId,
              updatePlan.patch,
            );
            return { kind: "write-update", patch: updatePlan.patch };
          }

          const deletePlan = planTransactionDeletion(prepared.base, latest);
          if (deletePlan.kind === "conflict") {
            return {
              kind: "conflict",
              conflictType: "delete-versus-edit",
              remoteSnapshot: toJsonValue(latest),
              conflictingFields: deletePlan.conflictingFields,
            };
          }
          mutationStarted = true;
          await this.transactions.delete(prepared.request.transactionId);
          return { kind: "write-delete" };
        },
        runOptions(prepared.request.encryptionPassword, SYNC_OPTIONS),
      );
    } catch (error) {
      return this.#handleActualFailure(
        operation,
        mutationStarted,
        error,
        mutationStarted ? writeEvidenceFromPostWriteError(error) : undefined,
      );
    }

    try {
      if (plan.kind === "rejected") {
        const rejected = await this.journal.markRejected(
          operation.operationId,
          plan.errorCode,
        );
        return { operation: rejected, conflict: null };
      }
      if (plan.kind === "conflict") {
        return await this.#recordConflict(operation, plan);
      }
      if (plan.kind === "already-absent") {
        const committed = await this.#markVerifiedCommitted(operation, {
          transactionId: prepared.request.transactionId,
          deleted: true,
          alreadyAbsent: true,
        });
        return { operation: committed, conflict: null };
      }
      if (plan.kind === "unchanged") {
        const committed = await this.#markVerifiedCommitted(operation, {
          transactionId: prepared.request.transactionId,
          unchanged: true,
          remoteRevision: createTransactionRevision(plan.remoteSnapshot),
        });
        return { operation: committed, conflict: null };
      }

      return await this.#verifyWrite(operation, prepared, plan);
    } catch (error) {
      const writeEvidence = writeEvidenceFromPlan(plan);
      return this.#handleActualFailure(
        operation,
        mutationStarted,
        error,
        mutationStarted ? writeEvidence : undefined,
        "operation-finalization-failed",
      );
    }
  }

  async #verifyWrite(
    operation: WriteOperation,
    prepared: PreparedRequest,
    plan: Extract<InitialPlan, { kind: "write-update" | "write-delete" }>,
  ): Promise<TransactionReconciliationResult> {
    const latestRaw = await this.session.runBudget(
      operation.budgetId,
      () => this.transactions.read(prepared.request.transactionId),
      runOptions(prepared.request.encryptionPassword),
    );

    if (plan.kind === "write-delete") {
      if (latestRaw === undefined) {
        const committed = await this.#markVerifiedCommitted(
          operation,
          {
            transactionId: prepared.request.transactionId,
            deleted: true,
          },
          { kind: "transaction-delete-returned" },
        );
        return { operation: committed, conflict: null };
      }
      const latest = snapshotActualTransaction(latestRaw);
      return this.#recordConflict(operation, {
        kind: "conflict",
        conflictType: "delete-not-visible-after-sync",
        remoteSnapshot: toJsonValue(latest),
        conflictingFields: ["$entity"],
      });
    }

    if (latestRaw === undefined) {
      return this.#recordConflict(operation, {
        kind: "conflict",
        conflictType: "deleted-during-write",
        remoteSnapshot: null,
        conflictingFields: ["$entity"],
      });
    }

    const latest = snapshotActualTransaction(latestRaw);
    if (transactionStructureChanged(prepared.base, latest)) {
      return this.#recordConflict(operation, {
        kind: "conflict",
        conflictType: "transaction-structure-changed-during-write",
        remoteSnapshot: toJsonValue(latest),
        conflictingFields: structuredFields(prepared.base, latest),
      });
    }
    const conflictingFields = mismatchedPatchFields(latest, plan.patch);
    if (conflictingFields.length > 0) {
      return this.#recordConflict(operation, {
        kind: "conflict",
        conflictType: "changed-during-write",
        remoteSnapshot: toJsonValue(latest),
        conflictingFields,
      });
    }

    if (latest.transferId !== null) {
      const counterpartRaw = await this.session.runBudget(
        operation.budgetId,
        () => this.transactions.read(latest.transferId!),
        runOptions(prepared.request.encryptionPassword),
      );
      if (counterpartRaw === undefined) {
        return this.#recordConflict(operation, {
          kind: "conflict",
          conflictType: "transfer-pair-incomplete-after-write",
          remoteSnapshot: toJsonValue(latest),
          conflictingFields: ["transferId"],
        });
      }
      const counterpart = snapshotActualTransaction(counterpartRaw);
      if (
        counterpart.id !== latest.transferId ||
        counterpart.transferId !== latest.id ||
        counterpart.amount !== -latest.amount
      ) {
        return this.#recordConflict(operation, {
          kind: "conflict",
          conflictType: "transfer-pair-changed-after-write",
          remoteSnapshot: toJsonValue(latest),
          conflictingFields: ["transferId", "amount"],
        });
      }
    }

    const committed = await this.#markVerifiedCommitted(
      operation,
      {
        transactionId: prepared.request.transactionId,
        appliedPatch: toJsonValue(plan.patch),
        verifiedRevision: createTransactionRevision(latest),
      },
      {
        kind: "transaction-update-returned",
        appliedPatch: toJsonValue(plan.patch) as { [key: string]: JsonValue },
      },
    );
    return { operation: committed, conflict: null };
  }

  async #markVerifiedCommitted(
    operation: WriteOperation,
    actualResult: JsonValue,
    writeEvidence?: ActualWriteEvidence,
  ): Promise<WriteOperation> {
    try {
      return await this.journal.markCommitted(
        operation.operationId,
        actualResult,
      );
    } catch (commitError) {
      let current: WriteOperation | undefined;
      try {
        current = await this.journal.get(operation.operationId);
      } catch {
        throw commitError;
      }

      if (current?.status === "committed") {
        return this.journal.markCommitted(operation.operationId, actualResult);
      }
      if (
        current?.status === "uncertain" &&
        current.errorCode === "claim-lease-expired"
      ) {
        return this.journal.resolveUncertainAsCommitted(
          operation.operationId,
          actualResult,
          writeEvidence,
        );
      }
      throw commitError;
    }
  }

  async #recoverUncertain(
    operation: WriteOperation,
    prepared: PreparedRequest,
  ): Promise<TransactionReconciliationResult> {
    const evidence = operation.writeEvidence;
    if (evidence?.kind === "transaction-create-returned") {
      return this.#existingResult(operation);
    }

    let current: unknown | undefined;
    try {
      current = await this.session.runBudget(
        operation.budgetId,
        () => this.transactions.read(prepared.request.transactionId),
        runOptions(prepared.request.encryptionPassword),
      );
    } catch {
      return this.#existingResult(operation);
    }

    let recoveredEvidence: ActualWriteEvidence;
    let actualResult: JsonValue;
    if (operation.action === "delete") {
      if (
        evidence !== null &&
        evidence.kind !== "transaction-delete-returned"
      ) {
        return this.#existingResult(operation);
      }
      if (current === undefined) {
        recoveredEvidence = evidence ?? {
          kind: "transaction-delete-returned",
        };
        actualResult = {
          transactionId: prepared.request.transactionId,
          deleted: true,
          recoveredByReadback: true,
        };
      } else {
        const snapshot = snapshotActualTransaction(current);
        if (
          createTransactionRevision(snapshot) === prepared.request.baseRevision
        ) {
          return this.#requeueAfterReadback(operation);
        }
        return this.#existingResult(operation);
      }
    } else {
      if (operation.action !== "update" || current === undefined) {
        return this.#existingResult(operation);
      }
      let snapshot: TransactionSnapshot;
      let patch: TransactionPatch;
      try {
        snapshot = snapshotActualTransaction(current);
        patch =
          evidence?.kind === "transaction-update-returned"
            ? parseTransactionPatch(evidence.appliedPatch)
            : prepared.patch;
      } catch {
        return this.#existingResult(operation);
      }
      if (evidence && evidence.kind !== "transaction-update-returned") {
        return this.#existingResult(operation);
      }
      if (mismatchedPatchFields(snapshot, patch).length === 0) {
        recoveredEvidence = evidence ?? {
          kind: "transaction-update-returned",
          appliedPatch: toJsonValue(patch) as { [key: string]: JsonValue },
        };
        actualResult = {
          transactionId: prepared.request.transactionId,
          recoveredByReadback: true,
          verifiedRevision: createTransactionRevision(snapshot),
        };
      } else if (
        transactionMatchesBaseFields(snapshot, prepared.base, prepared.patch)
      ) {
        return this.#requeueAfterReadback(operation);
      } else {
        return this.#existingResult(operation);
      }
    }

    try {
      const committed = await this.journal.resolveUncertainAsCommitted(
        operation.operationId,
        actualResult,
        recoveredEvidence,
      );
      return { operation: committed, conflict: null };
    } catch {
      // Keep the durable state uncertain whenever the journal cannot record recovery.
    }
    return this.#existingResult(operation);
  }

  async #recoverUncertainCreate(
    operation: WriteOperation,
    prepared: PreparedCreateRequest,
  ): Promise<TransactionReconciliationResult> {
    let current: unknown | undefined;
    try {
      current = await this.session.runBudget(
        operation.budgetId,
        () => this.transactions.read(prepared.request.transactionId),
        runOptions(prepared.request.encryptionPassword),
      );
    } catch {
      return this.#existingResult(operation);
    }
    if (current === undefined) {
      if (operation.writeEvidence === null) {
        return this.#requeueAfterReadback(operation);
      }
      return this.#existingResult(operation);
    }

    let snapshot: TransactionSnapshot;
    try {
      snapshot = snapshotActualTransaction(current);
    } catch {
      return this.#existingResult(operation);
    }
    if (
      operation.writeEvidence !== null &&
      operation.writeEvidence.kind !== "transaction-create-returned"
    ) {
      return this.#existingResult(operation);
    }
    const matchesCreate =
      (snapshot.id === prepared.request.transactionId ||
        snapshot.importedId === prepared.request.transactionId) &&
      snapshot.account === prepared.input.account &&
      snapshot.amount === prepared.input.amount &&
      snapshot.date === prepared.input.date;
    if (!matchesCreate) {
      return this.#existingResult(operation);
    }

    try {
      const committed = await this.journal.resolveUncertainAsCommitted(
        operation.operationId,
        {
          transactionId: prepared.request.transactionId,
          created: true,
          recoveredByReadback: true,
          verifiedRevision: createTransactionRevision(snapshot),
        },
        operation.writeEvidence ?? { kind: "transaction-create-returned" },
      );
      return { operation: committed, conflict: null };
    } catch {
      // Keep the durable state uncertain whenever the journal cannot record recovery.
    }
    return this.#existingResult(operation);
  }

  async #requeueAfterReadback(
    operation: WriteOperation,
  ): Promise<TransactionReconciliationResult> {
    try {
      const requeued = await this.journal.requeueUncertain(
        operation.operationId,
        operation.writeEvidence ?? undefined,
      );
      return { operation: requeued, conflict: null };
    } catch {
      return this.#existingResult(operation);
    }
  }

  async #handleActualFailure(
    operation: WriteOperation,
    mutationStarted: boolean,
    error: unknown,
    writeEvidence?: ActualWriteEvidence,
    preWriteErrorCode = "actual-read-failed",
  ): Promise<TransactionReconciliationResult> {
    try {
      if (mutationStarted) {
        return this.#resultForOperation(
          await this.journal.markUncertain(
            operation.operationId,
            "actual-outcome-uncertain",
            writeEvidence,
          ),
        );
      }
      if (error instanceof TransactionRequestValidationError) {
        await this.journal.markRejected(operation.operationId, error.errorCode);
      } else {
        await this.journal.releaseClaim(
          operation.operationId,
          preWriteErrorCode,
        );
      }
    } catch (journalError) {
      reportJournalFailure(journalError);
    }
    throw error;
  }

  async #recordConflict(
    operation: WriteOperation,
    plan: Extract<InitialPlan, { kind: "conflict" }>,
  ): Promise<TransactionReconciliationResult> {
    const conflict: ConflictInput = {
      conflictId: randomUUID(),
      conflictType: plan.conflictType,
      baseSnapshot: operation.baseSnapshot,
      localPatch: operation.patch,
      remoteSnapshot: plan.remoteSnapshot,
      conflictingFields: plan.conflictingFields,
    };
    const recorded = await this.journal.recordConflict(
      operation.operationId,
      conflict,
    );
    const current = await this.journal.get(operation.operationId);
    if (!current) {
      throw new Error("Transaction conflict has no operation record");
    }
    return { operation: current, conflict: recorded };
  }

  async #existingResult(
    operation: WriteOperation,
  ): Promise<TransactionReconciliationResult> {
    const conflict =
      operation.status === "conflict"
        ? ((await this.journal.getConflict(operation.operationId)) ?? null)
        : null;
    return { operation, conflict };
  }

  async #resultForOperation(
    operation: WriteOperation,
  ): Promise<TransactionReconciliationResult> {
    return this.#existingResult(operation);
  }
}

function prepareRequest(request: TransactionUpdateRequest): PreparedRequest {
  try {
    return prepareValidatedRequest(request);
  } catch (error) {
    if (error instanceof TransactionRequestValidationError) {
      throw error;
    }
    throw new TransactionRequestValidationError(
      error instanceof Error ? error.message : "Transaction request is invalid",
      { cause: error },
    );
  }
}

function prepareCreateRequest(
  request: TransactionCreateRequest,
): PreparedCreateRequest {
  try {
    if (
      typeof request.idempotencyKey !== "string" ||
      request.idempotencyKey.trim().length === 0 ||
      [...request.idempotencyKey].length > IDEMPOTENCY_KEY_MAX_LENGTH
    ) {
      throw new Error(
        `idempotencyKey must contain between 1 and ${IDEMPOTENCY_KEY_MAX_LENGTH} characters`,
      );
    }
    if (!isCompanionTransactionId(request.transactionId)) {
      throw new Error(
        "transactionId must be a stable Companion transaction ID",
      );
    }
    const input = parseTransactionCreateInput(request.patch);
    const operationInput: WriteOperationInput = {
      operationId: request.operationId,
      principalId: request.principalId,
      budgetId: request.budgetId,
      idempotencyKey: request.idempotencyKey,
      entityType: "transaction",
      action: "create",
      entityId: request.transactionId,
      baseRevision: null,
      baseSnapshot: {},
      patch: toJsonValue(input) as { [key: string]: JsonValue },
    };
    return { request, input, operationInput };
  } catch (error) {
    if (error instanceof TransactionRequestValidationError) {
      throw error;
    }
    throw new TransactionRequestValidationError(
      error instanceof Error ? error.message : "Transaction request is invalid",
      { cause: error },
    );
  }
}

function prepareValidatedRequest(
  request: TransactionUpdateRequest,
): PreparedRequest {
  if (
    typeof request.idempotencyKey !== "string" ||
    request.idempotencyKey.trim().length === 0 ||
    [...request.idempotencyKey].length > IDEMPOTENCY_KEY_MAX_LENGTH
  ) {
    throw new Error(
      `idempotencyKey must contain between 1 and ${IDEMPOTENCY_KEY_MAX_LENGTH} characters`,
    );
  }
  if (request.action !== "update" && request.action !== "delete") {
    throw new Error("Transaction action must be update or delete");
  }
  if (
    typeof request.transactionId !== "string" ||
    request.transactionId.trim().length === 0
  ) {
    throw new Error("transactionId must be a non-empty string");
  }
  if (
    typeof request.baseRevision !== "string" ||
    request.baseRevision.length === 0
  ) {
    throw new Error("baseRevision is required");
  }
  const base = parseTransactionSnapshot(request.baseSnapshot);
  if (base.id !== request.transactionId) {
    throw new Error("baseSnapshot ID does not match transactionId");
  }
  if (createTransactionRevision(base) !== request.baseRevision) {
    throw new Error("baseRevision does not match baseSnapshot");
  }
  const patch =
    request.action === "update"
      ? parseTransactionPatch(request.patch)
      : parseDeletePatch(request.patch);
  const operationInput: WriteOperationInput = {
    operationId: request.operationId,
    principalId: request.principalId,
    budgetId: request.budgetId,
    idempotencyKey: request.idempotencyKey,
    entityType: "transaction",
    action: request.action,
    entityId: request.transactionId,
    baseRevision: request.baseRevision,
    baseSnapshot: request.baseSnapshot,
    patch: patch as { [key: string]: JsonValue },
  };
  return { request, base, patch, operationInput };
}

function parseDeletePatch(value: JsonValue): TransactionPatch {
  if (value === null || Array.isArray(value) || typeof value !== "object") {
    throw new Error("delete patch must be an empty JSON object");
  }
  if (Object.keys(value).length > 0) {
    throw new Error("delete patch must be an empty JSON object");
  }
  return {};
}

function mismatchedPatchFields(
  snapshot: TransactionSnapshot,
  patch: TransactionPatch,
): string[] {
  return Object.keys(patch).filter((field) => {
    const patchValue =
      field === "notes" && patch.notes === ""
        ? null
        : patch[field as keyof TransactionPatch];
    return !Object.is(snapshot[field as keyof TransactionSnapshot], patchValue);
  });
}

function transactionMatchesBaseFields(
  snapshot: TransactionSnapshot,
  base: TransactionSnapshot,
  patch: TransactionPatch,
): boolean {
  return Object.keys(patch).every((field) => {
    const key = field as keyof TransactionPatch;
    if (key === "notes") {
      return snapshot.notes === base.notes;
    }
    return Object.is(
      snapshot[key as keyof TransactionSnapshot],
      base[key as keyof TransactionSnapshot],
    );
  });
}

function structuredFields(
  base: TransactionSnapshot,
  remote: TransactionSnapshot,
): string[] {
  const marked: string[] = [];
  if (base.transferId !== null || remote.transferId !== null) {
    marked.push("transferId");
  }
  if (base.isParent || remote.isParent) {
    marked.push("isParent");
  }
  if (base.isChild || remote.isChild) {
    marked.push("isChild");
  }
  if (base.parentId !== null || remote.parentId !== null) {
    marked.push("parentId");
  }
  if (base.subtransactions.length > 0 || remote.subtransactions.length > 0) {
    marked.push("subtransactions");
  }
  return [...new Set(marked)];
}

function transactionStructureChanged(
  expected: TransactionSnapshot,
  actual: TransactionSnapshot,
): boolean {
  if (
    expected.isParent !== actual.isParent ||
    expected.isChild !== actual.isChild ||
    expected.parentId !== actual.parentId
  ) {
    return true;
  }
  if (
    !expected.isParent &&
    !actual.isParent &&
    expected.subtransactions.length === 0 &&
    actual.subtransactions.length === 0
  ) {
    return false;
  }
  const expectedChildIds = expected.subtransactions.map(({ id }) => id).sort();
  const actualChildIds = actual.subtransactions.map(({ id }) => id).sort();
  return JSON.stringify(expectedChildIds) !== JSON.stringify(actualChildIds);
}

function runOptions(
  encryptionPassword: string | undefined,
  synchronizeAfterWrite?: BudgetOperationOptions["synchronizeAfterWrite"],
): BudgetOperationOptions {
  return {
    ...(encryptionPassword === undefined ? {} : { encryptionPassword }),
    ...(synchronizeAfterWrite === undefined ? {} : { synchronizeAfterWrite }),
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && !Array.isArray(value) && typeof value === "object";
}

function writeEvidenceFromPostWriteError(
  error: unknown,
): ActualWriteEvidence | undefined {
  if (!(error instanceof ActualPostWriteSyncError)) {
    return undefined;
  }
  const result = error.operationResult;
  if (!isRecord(result)) {
    return undefined;
  }
  if (result.kind === "write-create") {
    return { kind: "transaction-create-returned" };
  }
  if (result.kind === "write-delete") {
    return { kind: "transaction-delete-returned" };
  }
  if (result.kind !== "write-update") {
    return undefined;
  }
  try {
    const appliedPatch = parseTransactionPatch(result.patch as JsonValue);
    return {
      kind: "transaction-update-returned",
      appliedPatch: toJsonValue(appliedPatch) as { [key: string]: JsonValue },
    };
  } catch {
    return undefined;
  }
}

function writeEvidenceFromPlan(
  plan: InitialPlan,
): ActualWriteEvidence | undefined {
  if (plan.kind === "write-delete") {
    return { kind: "transaction-delete-returned" };
  }
  if (plan.kind === "write-update") {
    return {
      kind: "transaction-update-returned",
      appliedPatch: toJsonValue(plan.patch) as { [key: string]: JsonValue },
    };
  }
  return undefined;
}

async function withLeaseRenewal<T>(
  journal: Pick<DurableWriteJournal, "renewLease">,
  operationId: string,
  operation: () => Promise<T>,
): Promise<T> {
  let renewal: Promise<void> | undefined;
  const timer = setInterval(() => {
    if (renewal) {
      return;
    }

    renewal = Promise.resolve()
      .then(() => journal.renewLease(operationId))
      .then((renewed) => {
        if (!renewed) {
          clearInterval(timer);
        }
      })
      .catch(reportJournalFailure)
      .finally(() => {
        renewal = undefined;
      });
  }, WRITE_OPERATION_LEASE_RENEWAL_INTERVAL_MS);
  timer.unref?.();

  try {
    return await operation();
  } finally {
    clearInterval(timer);
    await renewal;
  }
}

function toJsonValue(
  value: TransactionSnapshot | TransactionPatch | TransactionCreateInput,
): JsonValue {
  return value as unknown as JsonValue;
}

function reportJournalFailure(error: unknown): void {
  try {
    console.error("Transaction journal transition failed", {
      errorType: error instanceof Error ? error.name : typeof error,
    });
  } catch {
    // The primary Actual operation remains authoritative if logging fails.
  }
}
