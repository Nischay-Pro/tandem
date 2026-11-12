import { createHash, randomUUID } from "node:crypto";
import type { FastifyReply, FastifyRequest } from "fastify";
import { DeviceAuthorization, type DeviceRecord } from "../auth/device-auth.js";
import { logSecurityAuditEvent } from "../security-audit.js";
import {
  BudgetMaintenanceBusyError,
  BudgetMaintenanceInputError,
  BudgetScheduleChangedError,
  type BudgetTemplateAssignment,
  type BudgetMaintenanceApiPort,
  type BudgetScheduleEdit,
  type StatementTransactionImport,
} from "../actual/budget-maintenance-api.js";
import {
  IdempotencyKeyReuseError,
  type DurableWriteJournal,
  type JsonValue,
} from "../write-journal/contracts.js";

export type { JsonValue } from "../write-journal/contracts.js";
export type { BudgetScheduleEdit } from "../actual/budget-maintenance-api.js";

export type BudgetMaintenanceJournal = Pick<
  DurableWriteJournal,
  "enqueue" | "claim" | "markCommitted" | "markRejected" | "markUncertain"
>;

export interface BudgetRouteParams {
  budgetId: string;
}

export interface BudgetAccountRouteParams extends BudgetRouteParams {
  accountId: string;
}

export interface BudgetCategoryRouteParams extends BudgetRouteParams {
  categoryId: string;
}

export interface BudgetCategoryGroupRouteParams extends BudgetRouteParams {
  groupId: string;
}

export interface BudgetScheduleRouteParams extends BudgetRouteParams {
  scheduleId: string;
}

export interface BudgetPayeeRouteParams extends BudgetRouteParams {
  payeeId: string;
}

export interface BudgetMonthRouteParams extends BudgetRouteParams {
  month: string;
}

export interface BudgetAccountCreateBody {
  name: string;
  offbudget: boolean;
  accountGroupId?: string | null;
}

export interface BudgetAccountCloseBody {
  transferAccountId: string | null;
  transferCategoryId: string | null;
}

export interface BudgetAccountRenameBody {
  name: string;
}

export interface BudgetAccountMoveBody {
  targetAccountId: string | null;
}

export interface BudgetCategoryRenameBody {
  name: string;
}

export interface BudgetCategoryGroupCreateBody {
  name: string;
  isIncome: boolean;
}

export interface BudgetCategoryGroupActionBody {
  action: "rename" | "hide" | "show" | "move" | "delete";
  name?: string;
  targetId?: string | null;
}

export interface BudgetCategoryActionBody {
  action: "hide" | "show" | "move";
  targetId?: string | null;
  targetGroupId?: string | null;
}

export interface BudgetCategoryCreateBody {
  name: string;
  categoryGroupId: string;
}

export interface BudgetPayeeCreateBody {
  name: string;
}

export interface BudgetPayeeActionBody {
  action: "rename" | "merge" | "delete" | "default-category";
  name?: string;
  targetPayeeId?: string;
  categoryId?: string | null;
}

export interface BudgetCategoryBudgetAmountBody {
  month: string;
  amountMinor: number;
}

export interface BudgetCategoryCarryoverBody {
  month: string;
  enabled: boolean;
}

export interface BudgetMonthActionBody {
  action:
    "copy-last-month" | "hold-next-month" | "reset-hold" | "apply-template";
  amountMinor?: number;
  assignments?: BudgetTemplateAssignment[];
}

export interface BudgetStatementImportBody {
  transactions: StatementTransactionImport[];
}

export interface BudgetStatementImportPreviewBody {
  importedIds: string[];
}

export interface BudgetMoveMoneyBody {
  month: string;
  fromCategoryId: string;
  toCategoryId: string;
  amountMinor: number;
}

export interface BudgetCategoryDeleteBody {
  transferCategoryId: string | null;
}

export interface BudgetScheduleActionBody extends BudgetScheduleEdit {
  action: "rename" | "edit" | "post" | "skip" | "delete";
  expectedNextDate?: string;
}

export interface BudgetReadHeaders {
  "x-budget-encryption-password"?: string;
  "idempotency-key"?: string;
}

export interface BudgetMaintenanceRouteDependencies {
  deviceAuth: DeviceAuthorization;
  maintenanceApi: BudgetMaintenanceApiPort | undefined;
  journal: BudgetMaintenanceJournal | undefined;
  requireDevice: (
    deviceAuth: DeviceAuthorization,
    request: FastifyRequest,
    reply: FastifyReply,
  ) => Promise<DeviceRecord | undefined>;
  invalidBudgetRequest: (reply: FastifyReply) => FastifyReply;
  transactionNotFound: (reply: FastifyReply) => FastifyReply;
  actualNotConfigured: (reply: FastifyReply) => FastifyReply;
  reportTransactionFailure: (context: string, error?: unknown) => void;
  actualRequestFailed: (reply: FastifyReply, error?: unknown) => FastifyReply;
}

export class MaintenanceOperationConflictError extends BudgetMaintenanceInputError {
  constructor(
    readonly code:
      | "idempotency_key_reused"
      | "operation_in_progress"
      | "operation_uncertain"
      | "operation_rejected"
      | "operation_result_unavailable"
      | "schedule_changed",
    message: string,
  ) {
    super(message);
    this.name = "MaintenanceOperationConflictError";
  }
}

export async function runMaintenanceOperation<T extends JsonValue>(
  journal: BudgetMaintenanceJournal,
  request: FastifyRequest,
  device: { deviceId: string; principalId: string },
  budgetId: string,
  action: string,
  entityId: string | null,
  patch: { [key: string]: JsonValue },
  work: () => Promise<T>,
): Promise<T> {
  const idempotencyKey = request.headers["idempotency-key"];
  // Fastify rejects missing and whitespace-only keys before this private handler.
  /* v8 ignore next -- retain a defensive check for direct/internal callers. */
  if (
    typeof idempotencyKey !== "string" ||
    idempotencyKey.trim().length === 0
  ) {
    throw new BudgetMaintenanceInputError("An idempotency key is required");
  }

  let queued;
  try {
    queued = await journal.enqueue({
      operationId: randomUUID(),
      principalId: device.principalId,
      budgetId,
      idempotencyKey,
      entityType: "budget-maintenance",
      action,
      entityId,
      baseRevision: null,
      baseSnapshot: {},
      patch,
    });
  } catch (error) {
    if (error instanceof IdempotencyKeyReuseError) {
      throw new MaintenanceOperationConflictError(
        "idempotency_key_reused",
        "This idempotency key was already used for a different change. Submit the change again.",
      );
    }
    throw error;
  }

  let operation = queued.operation;
  if (operation.status === "committed") {
    if (operation.actualResult !== null) return operation.actualResult as T;
    throw new MaintenanceOperationConflictError(
      "operation_result_unavailable",
      "This earlier change was applied, but its result has expired. Refresh the budget before continuing.",
    );
  }
  if (operation.status === "queued") {
    const claim = await journal.claim(operation.operationId);
    operation = claim.operation;
    if (claim.claimed) {
      try {
        const result = await work();
        await journal.markCommitted(operation.operationId, result);
        return result;
      } catch (error) {
        if (error instanceof BudgetMaintenanceBusyError) {
          await journal.markRejected(operation.operationId, "bank-sync-busy");
          throw error;
        }
        if (error instanceof BudgetScheduleChangedError) {
          await journal.markRejected(operation.operationId, error.code);
          throw error;
        }
        if (error instanceof BudgetMaintenanceInputError) {
          await journal.markRejected(operation.operationId, "invalid-request");
          throw error;
        }
        await journal.markUncertain(
          operation.operationId,
          "actual-result-unknown",
        );
        throw error;
      }
    }
  }

  if (operation.status === "applying" || operation.status === "queued") {
    throw new MaintenanceOperationConflictError(
      "operation_in_progress",
      "This change is still being confirmed. Check the budget before trying again.",
    );
  }
  if (operation.status === "uncertain") {
    throw new MaintenanceOperationConflictError(
      "operation_uncertain",
      "Tandem could not confirm whether this change reached Actual. Check the budget before submitting it again.",
    );
  }
  if (operation.status === "rejected") {
    if (operation.errorCode === "schedule_changed") {
      throw new MaintenanceOperationConflictError(
        "schedule_changed",
        "This schedule changed in Actual. Refresh it before trying again.",
      );
    }
    throw new MaintenanceOperationConflictError(
      "operation_rejected",
      "This change was rejected earlier. Correct it and submit it again with a new request.",
    );
  }
  throw new MaintenanceOperationConflictError(
    "operation_uncertain",
    "This change was not applied. Submit it again with a new request.",
  );
}

export function statementImportJournalPatch(
  transactions: readonly StatementTransactionImport[],
): { rowCount: number; rowsHash: string } {
  const canonicalRows = transactions
    .map((transaction) => ({
      date: transaction.date,
      amountMinor: transaction.amountMinor,
      payeeName: transaction.payeeName,
      ...(transaction.notes === undefined ? {} : { notes: transaction.notes }),
      importedId: transaction.importedId,
    }))
    .sort((left, right) => left.importedId.localeCompare(right.importedId));
  const rowsHash = createHash("sha256")
    .update(JSON.stringify(canonicalRows))
    .digest("hex");
  return { rowCount: transactions.length, rowsHash };
}

export function logBudgetMaintenanceEvent(
  request: FastifyRequest,
  device: { deviceId: string },
  budgetId: string,
  action: string,
  outcome: "success" | "failed" | "rejected",
  targetId?: string,
): void {
  logSecurityAuditEvent({
    event: `budget.${action}`,
    outcome,
    clientAddress: request.ip,
    deviceId: device.deviceId,
    budgetId,
    ...(targetId === undefined ? {} : { targetId }),
  });
}
