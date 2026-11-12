import type * as actualApi from "@actual-app/api";
import type { BudgetOperationOptions } from "./actual-session.js";
import type { BudgetLockPort } from "./transaction-reconciler.js";
import {
  MAX_TRANSACTION_AMOUNT,
  validateTransactionDate,
} from "./transaction-reconciliation.js";

/** Maximum category assignment: 1,000,000,000 major currency units. */
export const MAX_BUDGET_AMOUNT_MINOR = 100_000_000_000;
export const BANK_SYNC_COOLDOWN_MS = 60_000;
export const BANK_SYNC_TIMEOUT_MS = 120_000;

export interface ScheduleAmountRange {
  num1: number;
  num2: number;
}

export interface ScheduleRecurrenceConfig {
  frequency: "daily" | "weekly" | "monthly" | "yearly";
  interval?: number;
  patterns?: Array<{
    value: number;
    type: "SU" | "MO" | "TU" | "WE" | "TH" | "FR" | "SA" | "day";
  }>;
  skipWeekend?: boolean;
  start: string;
  endMode?: "never" | "after_n_occurrences" | "on_date";
  endOccurrences?: number;
  endDate?: string;
  weekendSolveMode?: "before" | "after";
}

export interface BudgetScheduleEdit {
  name?: string;
  accountId?: string;
  payeeId?: string;
  amountMinor?: number;
  amountRangeMinor?: ScheduleAmountRange;
  amountOp?: "is" | "isapprox" | "isbetween";
  date?: string | ScheduleRecurrenceConfig;
}

export interface BudgetTemplateAssignment {
  categoryId: string;
  amountMinor: number;
}

export interface StatementTransactionImport {
  date: string;
  amountMinor: number;
  payeeName: string;
  notes?: string;
  importedId: string;
}

export interface StatementImportResult {
  added: number;
  updated: number;
  errors: string[];
}

export interface StatementImportPreviewResult {
  existingImportedIds: string[];
}

export function validateBudgetScheduleEdit(edit: BudgetScheduleEdit): void {
  if (Object.keys(edit).length === 0) {
    throw new BudgetMaintenanceInputError("Add at least one schedule change");
  }
  if (edit.accountId !== undefined && !edit.accountId.trim()) {
    throw new BudgetMaintenanceInputError("Choose an account");
  }
  if (edit.payeeId !== undefined && !edit.payeeId.trim()) {
    throw new BudgetMaintenanceInputError("Choose a payee");
  }
  if (edit.name !== undefined && containsControlCharacters(edit.name)) {
    throw new BudgetMaintenanceInputError(
      "Schedule names must not contain control characters",
    );
  }

  const hasAmount = edit.amountMinor !== undefined;
  const hasRange = edit.amountRangeMinor !== undefined;
  if (hasAmount && hasRange) {
    throw new BudgetMaintenanceInputError(
      "Choose a single amount or an amount range",
    );
  }
  if (hasAmount || hasRange) {
    if (!edit.amountOp) {
      throw new BudgetMaintenanceInputError("Choose an amount rule");
    }
    if (hasRange !== (edit.amountOp === "isbetween")) {
      throw new BudgetMaintenanceInputError(
        "The amount rule does not match the amount values",
      );
    }
    const values = hasRange
      ? [edit.amountRangeMinor!.num1, edit.amountRangeMinor!.num2]
      : [edit.amountMinor!];
    if (
      values.some(
        (value) =>
          !Number.isSafeInteger(value) ||
          Math.abs(value) > MAX_TRANSACTION_AMOUNT,
      )
    ) {
      throw new BudgetMaintenanceInputError(
        "Schedule amounts must be whole cents within the supported limit",
      );
    }
    if (hasRange && values[0]! > values[1]!) {
      throw new BudgetMaintenanceInputError(
        "The minimum amount must not exceed the maximum",
      );
    }
  } else if (edit.amountOp !== undefined) {
    throw new BudgetMaintenanceInputError("An amount rule requires an amount");
  }

  if (edit.date !== undefined) validateScheduleDate(edit.date);
}

function containsControlCharacters(value: string): boolean {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index);
    if (code < 0x20 || code === 0x7f) return true;
  }
  return false;
}

function validateScheduleDate(value: string | ScheduleRecurrenceConfig): void {
  if (typeof value === "string") {
    if (!isIsoDate(value)) {
      throw new BudgetMaintenanceInputError(
        "Enter a schedule date as YYYY-MM-DD",
      );
    }
    return;
  }

  const frequencies = new Set(["daily", "weekly", "monthly", "yearly"]);
  if (!frequencies.has(value.frequency) || !isIsoDate(value.start)) {
    throw new BudgetMaintenanceInputError(
      "Choose a valid recurrence and start date",
    );
  }
  if (
    value.interval !== undefined &&
    (!Number.isSafeInteger(value.interval) ||
      value.interval < 1 ||
      value.interval > 999)
  ) {
    throw new BudgetMaintenanceInputError(
      "Repeat interval must be between 1 and 999",
    );
  }
  if (
    value.skipWeekend !== undefined &&
    typeof value.skipWeekend !== "boolean"
  ) {
    throw new BudgetMaintenanceInputError("Invalid weekend setting");
  }
  if (
    value.weekendSolveMode !== undefined &&
    value.weekendSolveMode !== "before" &&
    value.weekendSolveMode !== "after"
  ) {
    throw new BudgetMaintenanceInputError("Choose a valid weekend adjustment");
  }
  if (value.patterns !== undefined) {
    if (
      !Array.isArray(value.patterns) ||
      value.patterns.length > 31 ||
      value.patterns.some(
        (pattern) =>
          !Number.isSafeInteger(pattern.value) ||
          pattern.value < -31 ||
          pattern.value > 31 ||
          !["SU", "MO", "TU", "WE", "TH", "FR", "SA", "day"].includes(
            pattern.type,
          ),
      )
    ) {
      throw new BudgetMaintenanceInputError("Choose a valid repeat pattern");
    }
  }
  if (
    value.endMode !== undefined &&
    !["never", "after_n_occurrences", "on_date"].includes(value.endMode)
  ) {
    throw new BudgetMaintenanceInputError("Choose a valid repeat end");
  }
  if (
    value.endMode === "after_n_occurrences" &&
    (!Number.isSafeInteger(value.endOccurrences) ||
      value.endOccurrences! < 1 ||
      value.endOccurrences! > 100_000)
  ) {
    throw new BudgetMaintenanceInputError(
      "Occurrence count must be between 1 and 100,000",
    );
  }
  if (
    value.endMode === "on_date" &&
    (!value.endDate || !isIsoDate(value.endDate) || value.endDate < value.start)
  ) {
    throw new BudgetMaintenanceInputError(
      "The end date must be on or after the start date",
    );
  }
}

export function isIsoDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/u.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return (
    !Number.isNaN(parsed.getTime()) &&
    parsed.toISOString().slice(0, 10) === value
  );
}

export interface BudgetMaintenanceApiPort {
  runBankSync(budgetId: string, encryptionPassword?: string): Promise<void>;
  previewStatementImportTransactions(
    budgetId: string,
    accountId: string,
    importedIds: readonly string[],
    encryptionPassword?: string,
  ): Promise<StatementImportPreviewResult>;
  importStatementTransactions(
    budgetId: string,
    accountId: string,
    transactions: readonly StatementTransactionImport[],
    encryptionPassword?: string,
  ): Promise<StatementImportResult>;
  createAccount(
    budgetId: string,
    name: string,
    offbudget: boolean,
    accountGroupId?: string | null,
    encryptionPassword?: string,
  ): Promise<string>;
  closeAccount(
    budgetId: string,
    accountId: string,
    transferAccountId: string | null,
    transferCategoryId: string | null,
    encryptionPassword?: string,
  ): Promise<void>;
  renameAccount(
    budgetId: string,
    accountId: string,
    name: string,
    encryptionPassword?: string,
  ): Promise<void>;
  reopenAccount(
    budgetId: string,
    accountId: string,
    encryptionPassword?: string,
  ): Promise<void>;
  moveAccount(
    budgetId: string,
    accountId: string,
    targetAccountId: string | null,
    encryptionPassword?: string,
  ): Promise<void>;
  renameCategory(
    budgetId: string,
    categoryId: string,
    name: string,
    encryptionPassword?: string,
  ): Promise<void>;
  createCategoryGroup(
    budgetId: string,
    name: string,
    isIncome: boolean,
    encryptionPassword?: string,
  ): Promise<string>;
  manageCategoryGroup(
    budgetId: string,
    groupId: string,
    action: "rename" | "hide" | "show" | "move" | "delete",
    name?: string,
    targetId?: string | null,
    encryptionPassword?: string,
  ): Promise<void>;
  manageCategory(
    budgetId: string,
    categoryId: string,
    action: "hide" | "show" | "move",
    targetCategoryId?: string | null,
    encryptionPassword?: string,
    targetGroupId?: string | null,
  ): Promise<void>;
  createCategory(
    budgetId: string,
    name: string,
    categoryGroupId: string,
    encryptionPassword?: string,
  ): Promise<string>;
  createPayee(
    budgetId: string,
    name: string,
    encryptionPassword?: string,
  ): Promise<string>;
  renamePayee(
    budgetId: string,
    payeeId: string,
    name: string,
    encryptionPassword?: string,
  ): Promise<void>;
  mergePayee(
    budgetId: string,
    payeeId: string,
    targetPayeeId: string,
    encryptionPassword?: string,
  ): Promise<void>;
  deletePayee(
    budgetId: string,
    payeeId: string,
    encryptionPassword?: string,
  ): Promise<void>;
  setPayeeDefaultCategory(
    budgetId: string,
    payeeId: string,
    categoryId: string | null,
    encryptionPassword?: string,
  ): Promise<void>;
  setBudgetAmount(
    budgetId: string,
    categoryId: string,
    month: string,
    amountMinor: number,
    encryptionPassword?: string,
  ): Promise<void>;
  setBudgetCarryover(
    budgetId: string,
    categoryId: string,
    month: string,
    enabled: boolean,
    encryptionPassword?: string,
  ): Promise<void>;
  copyBudgetMonth(
    budgetId: string,
    month: string,
    encryptionPassword?: string,
  ): Promise<void>;
  applyBudgetTemplate(
    budgetId: string,
    month: string,
    assignments: readonly BudgetTemplateAssignment[],
    encryptionPassword?: string,
  ): Promise<void>;
  holdBudgetForNextMonth(
    budgetId: string,
    month: string,
    amountMinor: number,
    encryptionPassword?: string,
  ): Promise<void>;
  resetBudgetHold(
    budgetId: string,
    month: string,
    encryptionPassword?: string,
  ): Promise<void>;
  moveMoney(
    budgetId: string,
    month: string,
    fromCategoryId: string,
    toCategoryId: string,
    amountMinor: number,
    encryptionPassword?: string,
  ): Promise<void>;
  deleteCategory(
    budgetId: string,
    categoryId: string,
    transferCategoryId: string | null,
    encryptionPassword?: string,
  ): Promise<void>;
  renameSchedule(
    budgetId: string,
    scheduleId: string,
    name: string,
    encryptionPassword?: string,
  ): Promise<void>;
  editSchedule(
    budgetId: string,
    scheduleId: string,
    edit: BudgetScheduleEdit,
    encryptionPassword?: string,
  ): Promise<void>;
  skipSchedule(
    budgetId: string,
    scheduleId: string,
    expectedNextDate: string,
    encryptionPassword?: string,
  ): Promise<void>;
  postSchedule(
    budgetId: string,
    scheduleId: string,
    expectedNextDate: string,
    encryptionPassword?: string,
  ): Promise<void>;
  deleteSchedule(
    budgetId: string,
    scheduleId: string,
    encryptionPassword?: string,
  ): Promise<void>;
}

export interface ActualBudgetMaintenanceSession {
  runBudget<T>(
    syncId: string,
    operation: () => Promise<T>,
    options?: BudgetOperationOptions,
  ): Promise<T>;
}

export class BudgetMaintenanceInputError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "BudgetMaintenanceInputError";
  }
}

export class BudgetScheduleChangedError extends BudgetMaintenanceInputError {
  readonly code = "schedule_changed";

  constructor() {
    super("This schedule changed in Actual. Refresh it before trying again.");
    this.name = "BudgetScheduleChangedError";
  }
}

export class BudgetMaintenanceBusyError extends Error {
  constructor(readonly retryAfterSeconds: number) {
    super(
      "A bank sync is already running or was just completed. Try again shortly.",
    );
    this.name = "BudgetMaintenanceBusyError";
  }
}

export type ActualBudgetMaintenanceClient = Pick<
  typeof actualApi,
  | "runBankSync"
  | "q"
  | "aqlQuery"
  | "importTransactions"
  | "createAccount"
  | "updateAccount"
  | "getAccountGroups"
  | "closeAccount"
  | "reopenAccount"
  | "getAccounts"
  | "getAccountBalance"
  | "getCategories"
  | "getCategoryGroups"
  | "createCategoryGroup"
  | "updateCategoryGroup"
  | "deleteCategoryGroup"
  | "setBudgetAmount"
  | "setBudgetCarryover"
  | "holdBudgetForNextMonth"
  | "resetBudgetHold"
  | "getBudgetMonth"
  | "getPreferences"
  | "batchBudgetUpdates"
  | "createCategory"
  | "createPayee"
  | "getPayees"
  | "getPayeeRules"
  | "updatePayee"
  | "mergePayees"
  | "deletePayee"
  | "createRule"
  | "updateRule"
  | "deleteRule"
  | "updateCategory"
  | "deleteCategory"
  | "getSchedules"
  | "updateSchedule"
  | "deleteSchedule"
  | "internal"
>;

export async function getAllCategoryGroups(
  client: ActualBudgetMaintenanceClient,
): Promise<Awaited<ReturnType<typeof actualApi.getCategoryGroups>>> {
  const [visible, hidden] = await Promise.all([
    client.getCategoryGroups(),
    client.getCategoryGroups({ hidden: true }),
  ]);
  return [
    ...new Map(
      [...visible, ...hidden].map((group) => [group.id, group]),
    ).values(),
  ];
}

export async function getAllCategories(
  client: ActualBudgetMaintenanceClient,
): Promise<Awaited<ReturnType<typeof actualApi.getCategories>>> {
  const [visible, hidden] = await Promise.all([
    client.getCategories(),
    client.getCategories({ hidden: true }),
  ]);
  return [
    ...new Map(
      [...visible, ...hidden].map((category) => [category.id, category]),
    ).values(),
  ];
}

export function waitForCompletion<T>(
  operation: Promise<T>,
  timeoutMs: number,
): Promise<T> {
  let timeout: ReturnType<typeof setTimeout> | undefined;
  return Promise.race([
    operation,
    new Promise<T>((_resolve, reject) => {
      timeout = setTimeout(
        () => reject(new Error("Actual bank sync exceeded its time limit")),
        timeoutMs,
      );
      timeout!.unref();
    }),
  ]).finally(() => {
    clearTimeout(timeout);
  });
}

export function writeOptions(
  encryptionPassword?: string,
): BudgetOperationOptions {
  return {
    synchronizeAfterWrite: true,
    ...(encryptionPassword === undefined ? {} : { encryptionPassword }),
  };
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

export function readSafeAmount(value: unknown, description: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value)) {
    throw new BudgetMaintenanceInputError(
      `Actual returned an invalid ${description}`,
    );
  }
  return value;
}

export interface BudgetMaintenanceApiDependencies {
  locks: BudgetLockPort;
  session: ActualBudgetMaintenanceSession;
  client: ActualBudgetMaintenanceClient;
}

export { MAX_TRANSACTION_AMOUNT, validateTransactionDate };
