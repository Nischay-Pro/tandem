import { randomUUID } from "node:crypto";
import {
  ActualPostWriteSyncError,
  type BudgetOperationOptions,
} from "./actual-session.js";
import type { ActualTransactionStore } from "./transaction-store.js";
import {
  createTransactionRevision,
  isStructuredTransaction,
  parseTransactionPatch,
  parseTransactionSnapshot,
  planTransactionDeletion,
  planTransactionUpdate,
  snapshotActualTransaction,
  type TransactionPatch,
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

export interface TransactionReconciliationRequest {
  operationId: string;
  principalId: string;
  budgetId: string;
  idempotencyKey: string;
  transactionId: string;
  baseRevision: string;
  baseSnapshot: JsonValue;
  action: "update" | "delete";
  patch: JsonValue;
  encryptionPassword?: string;
}

export interface BudgetLockPort {
  withBudgetLock<T>(budgetId: string, work: () => Promise<T>): Promise<T>;
}

export interface ActualBudgetSessionPort {
  runBudget<T>(
    syncId: string,
    operation: () => Promise<T>,
    options?: BudgetOperationOptions,
  ): Promise<T>;
}

export interface TransactionReconciliationResult {
  operation: WriteOperation;
  conflict: OperationConflict | null;
}

interface PreparedRequest {
  request: TransactionReconciliationRequest;
  base: TransactionSnapshot;
  patch: TransactionPatch;
  operationInput: WriteOperationInput;
}

type InitialPlan =
  | {
      kind: "conflict";
      conflictType: string;
      remoteSnapshot: JsonValue;
      conflictingFields: string[];
    }
  | { kind: "unchanged"; remoteSnapshot: TransactionSnapshot }
  | { kind: "already-absent" }
  | { kind: "write-update"; patch: TransactionPatch }
  | { kind: "write-delete" };

const SYNC_OPTIONS = (result: unknown): boolean =>
  isRecord(result) &&
  (result.kind === "write-update" || result.kind === "write-delete");

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
    const prepared = prepareRequest(request);
    const { operation } = await this.journal.enqueue(prepared.operationInput);

    if (operation.status === "uncertain") {
      return this.locks.withBudgetLock(operation.budgetId, () =>
        this.#recoverUncertain(operation, prepared),
      );
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

  async #applyClaimed(
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
          if (
            isStructuredTransaction(prepared.base) ||
            isStructuredTransaction(latest)
          ) {
            return {
              kind: "conflict",
              conflictType: "structured-transaction-unsupported",
              remoteSnapshot: toJsonValue(latest),
              conflictingFields: structuredFields(prepared.base, latest),
            };
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

    if (plan.kind === "conflict") {
      return this.#recordConflict(operation, plan);
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

    return this.#verifyWrite(operation, prepared, plan);
  }

  async #verifyWrite(
    operation: WriteOperation,
    prepared: PreparedRequest,
    plan: Extract<InitialPlan, { kind: "write-update" | "write-delete" }>,
  ): Promise<TransactionReconciliationResult> {
    let latestRaw: unknown | undefined;
    try {
      latestRaw = await this.session.runBudget(
        operation.budgetId,
        () => this.transactions.read(prepared.request.transactionId),
        runOptions(prepared.request.encryptionPassword),
      );
    } catch {
      return this.#markUncertain(operation);
    }

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
    if (isStructuredTransaction(latest)) {
      return this.#recordConflict(operation, {
        kind: "conflict",
        conflictType: "structured-transaction-after-write",
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
    if (!evidence) {
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

    if (evidence.kind === "transaction-delete-returned") {
      if (operation.action !== "delete" || current !== undefined) {
        return this.#existingResult(operation);
      }
    } else {
      if (operation.action !== "update" || current === undefined) {
        return this.#existingResult(operation);
      }
      let recoveredPatch: TransactionPatch;
      let recoveredSnapshot: TransactionSnapshot;
      try {
        recoveredPatch = parseTransactionPatch(evidence.appliedPatch);
        recoveredSnapshot = snapshotActualTransaction(current);
      } catch {
        return this.#existingResult(operation);
      }
      if (mismatchedPatchFields(recoveredSnapshot, recoveredPatch).length > 0) {
        return this.#existingResult(operation);
      }
    }

    try {
      const committed = await this.journal.resolveUncertainAsCommitted(
        operation.operationId,
        {
          transactionId: prepared.request.transactionId,
          recoveredByReadback: true,
        },
        evidence,
      );
      return { operation: committed, conflict: null };
    } catch {
      // Keep the durable state uncertain whenever the journal cannot record recovery.
    }
    return this.#existingResult(operation);
  }

  async #handleActualFailure(
    operation: WriteOperation,
    mutationStarted: boolean,
    error: unknown,
    writeEvidence?: ActualWriteEvidence,
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
      await this.journal.releaseClaim(
        operation.operationId,
        "actual-read-failed",
      );
    } catch (journalError) {
      reportJournalFailure(journalError);
    }
    throw error;
  }

  async #markUncertain(
    operation: WriteOperation,
  ): Promise<TransactionReconciliationResult> {
    try {
      return this.#resultForOperation(
        await this.journal.markUncertain(
          operation.operationId,
          "actual-outcome-uncertain",
        ),
      );
    } catch (error) {
      reportJournalFailure(error);
      throw error;
    }
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

function prepareRequest(
  request: TransactionReconciliationRequest,
): PreparedRequest {
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

function toJsonValue(value: TransactionSnapshot | TransactionPatch): JsonValue {
  return value as unknown as JsonValue;
}

function reportJournalFailure(error: unknown): void {
  console.error("Transaction journal transition failed", {
    errorType: error instanceof Error ? error.name : typeof error,
  });
}
