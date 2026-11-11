import * as actualApi from "@actual-app/api";
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

function validateBudgetScheduleEdit(edit: BudgetScheduleEdit): void {
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

function isIsoDate(value: string): boolean {
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
  ): Promise<void>;
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
    action: "rename" | "hide" | "show" | "move",
    name?: string,
    targetGroupId?: string | null,
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

type ActualBudgetMaintenanceClient = Pick<
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

async function getAllCategoryGroups(
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

async function getAllCategories(
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

export function createBudgetMaintenanceApi(
  locks: BudgetLockPort,
  session: ActualBudgetMaintenanceSession,
  client: ActualBudgetMaintenanceClient = actualApi,
): BudgetMaintenanceApiPort {
  const bankSyncs = new Map<string, Promise<void>>();
  const bankSyncCooldowns = new Map<string, number>();
  return {
    async runBankSync(budgetId, encryptionPassword) {
      const active = bankSyncs.get(budgetId);
      if (active) throw new BudgetMaintenanceBusyError(5);
      const cooldownUntil = bankSyncCooldowns.get(budgetId) ?? 0;
      if (cooldownUntil > Date.now()) {
        throw new BudgetMaintenanceBusyError(
          Math.ceil((cooldownUntil - Date.now()) / 1_000),
        );
      }
      const sync = locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            await client.runBankSync();
          },
          writeOptions(encryptionPassword),
        ),
      );
      bankSyncs.set(budgetId, sync);
      void sync
        .then(
          () =>
            bankSyncCooldowns.set(budgetId, Date.now() + BANK_SYNC_COOLDOWN_MS),
          () =>
            bankSyncCooldowns.set(budgetId, Date.now() + BANK_SYNC_COOLDOWN_MS),
        )
        .finally(() => {
          bankSyncs.delete(budgetId);
        });
      return waitForCompletion(sync, BANK_SYNC_TIMEOUT_MS);
    },
    previewStatementImportTransactions(
      budgetId,
      accountId,
      importedIds,
      encryptionPassword,
    ) {
      if (!accountId.trim()) {
        return Promise.reject(
          new BudgetMaintenanceInputError("Choose an account"),
        );
      }
      if (
        importedIds.length === 0 ||
        importedIds.length > 1_000 ||
        importedIds.some((id) => !id.trim() || id.length > 200) ||
        new Set(importedIds).size !== importedIds.length
      ) {
        return Promise.reject(
          new BudgetMaintenanceInputError(
            "Choose up to 1,000 unique statement references",
          ),
        );
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const account = (await client.getAccounts()).find(
              (candidate) => candidate.id === accountId && !candidate.closed,
            );
            if (!account) {
              throw new BudgetMaintenanceInputError(
                "Choose an open account in this budget",
              );
            }
            const result = await client.aqlQuery(
              client
                .q("transactions")
                .filter({
                  account: accountId,
                  imported_id: { $oneof: [...importedIds] },
                })
                .select(["imported_id"])
                .options({ splits: "grouped" }),
            );
            if (
              !isRecord(result) ||
              !Array.isArray(result.data) ||
              result.data.some((row) => !isRecord(row))
            ) {
              throw new Error(
                "Actual returned an invalid statement-import preview",
              );
            }
            const requested = new Set(importedIds);
            const existingImportedIds = new Set<string>();
            for (const row of result.data) {
              const importedId = row.imported_id;
              if (
                typeof importedId !== "string" ||
                !requested.has(importedId)
              ) {
                throw new Error(
                  "Actual returned an invalid statement reference",
                );
              }
              existingImportedIds.add(importedId);
            }
            return { existingImportedIds: [...existingImportedIds] };
          },
          {
            synchronizeAfterWrite: false,
            ...(encryptionPassword === undefined ? {} : { encryptionPassword }),
          },
        ),
      );
    },
    importStatementTransactions(
      budgetId,
      accountId,
      transactions,
      encryptionPassword,
    ) {
      if (!accountId.trim()) {
        return Promise.reject(
          new BudgetMaintenanceInputError("Choose an account"),
        );
      }
      if (transactions.length === 0 || transactions.length > 1_000) {
        return Promise.reject(
          new BudgetMaintenanceInputError(
            "Import between 1 and 1,000 transactions at a time",
          ),
        );
      }
      const seenIds = new Set<string>();
      for (const transaction of transactions) {
        if (
          !isIsoDate(transaction.date) ||
          !Number.isSafeInteger(transaction.amountMinor) ||
          transaction.amountMinor === 0 ||
          Math.abs(transaction.amountMinor) > MAX_TRANSACTION_AMOUNT ||
          !transaction.payeeName.trim() ||
          transaction.payeeName.length > 200 ||
          (transaction.notes?.length ?? 0) > 500 ||
          !transaction.importedId.trim() ||
          transaction.importedId.length > 200 ||
          seenIds.has(transaction.importedId)
        ) {
          return Promise.reject(
            new BudgetMaintenanceInputError(
              "The statement contains an invalid or duplicate transaction row",
            ),
          );
        }
        try {
          validateTransactionDate(transaction.date);
        } catch (error) {
          return Promise.reject(
            new BudgetMaintenanceInputError(
              error instanceof Error
                ? error.message
                : "The statement contains an invalid transaction date",
            ),
          );
        }
        seenIds.add(transaction.importedId);
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const account = (await client.getAccounts()).find(
              (candidate) => candidate.id === accountId && !candidate.closed,
            );
            if (!account)
              throw new BudgetMaintenanceInputError(
                "Choose an open account in this budget",
              );
            const result = await client.importTransactions(
              accountId,
              transactions.map((transaction) => ({
                account: accountId,
                date: transaction.date,
                amount: transaction.amountMinor,
                payee_name: transaction.payeeName.trim(),
                imported_id: transaction.importedId,
                ...(transaction.notes === undefined
                  ? {}
                  : { notes: transaction.notes }),
              })),
              { defaultCleared: false },
            );
            return {
              added: result.added.length,
              updated: result.updated.length,
              errors: result.errors.map((error) => error.message),
            };
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    createAccount(
      budgetId,
      name,
      offbudget,
      accountGroupId = null,
      encryptionPassword,
    ) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            if (accountGroupId !== null) {
              const groups = await client.getAccountGroups();
              if (!groups.some((group) => group.id === accountGroupId)) {
                throw new BudgetMaintenanceInputError(
                  "Choose an account group in this budget",
                );
              }
            }
            const accountId = await client.createAccount({ name, offbudget });
            if (accountGroupId !== null) {
              await client.updateAccount(accountId, {
                account_group_id: accountGroupId,
              });
            }
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    createCategory(budgetId, name, categoryGroupId, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const groups = await client.getCategoryGroups();
            const group = groups.find(
              (candidate) => candidate.id === categoryGroupId,
            );
            if (!group || group.hidden) {
              throw new BudgetMaintenanceInputError(
                "Choose an active category group",
              );
            }
            const categories = await getAllCategories(client);
            const duplicate = categories.some(
              (candidate) =>
                candidate.group_id === categoryGroupId &&
                candidate.name.trim().toLocaleLowerCase() ===
                  name.trim().toLocaleLowerCase(),
            );
            if (duplicate) {
              throw new BudgetMaintenanceInputError(
                "A category with this name already exists in that group",
              );
            }
            return client.createCategory({
              name,
              group_id: categoryGroupId,
              is_income: group.is_income ?? false,
              hidden: false,
            });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    createPayee(budgetId, name, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const payees = await client.getPayees();
            const duplicate = payees.some(
              (candidate) =>
                candidate.name.trim().toLocaleLowerCase() ===
                name.trim().toLocaleLowerCase(),
            );
            if (duplicate) {
              throw new BudgetMaintenanceInputError(
                "A payee with this name already exists",
              );
            }
            return client.createPayee({ name, transfer_acct: "" });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    async renamePayee(budgetId, payeeId, name, encryptionPassword) {
      const normalizedName = name.trim();
      if (!normalizedName || normalizedName.length > 100) {
        throw new BudgetMaintenanceInputError(
          "Payee names must be between 1 and 100 characters",
        );
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const payee = (await client.getPayees()).find(
              (candidate) => candidate.id === payeeId,
            );
            if (!payee || payee.transfer_acct) {
              throw new BudgetMaintenanceInputError(
                "Choose a payee in this budget",
              );
            }
            await client.updatePayee(payeeId, { name: normalizedName });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    async mergePayee(budgetId, payeeId, targetPayeeId, encryptionPassword) {
      if (payeeId === targetPayeeId) {
        throw new BudgetMaintenanceInputError(
          "Choose a different payee to merge into",
        );
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const payees = await client.getPayees();
            const source = payees.find((candidate) => candidate.id === payeeId);
            const target = payees.find(
              (candidate) => candidate.id === targetPayeeId,
            );
            if (
              !source ||
              !target ||
              source.transfer_acct ||
              target.transfer_acct
            ) {
              throw new BudgetMaintenanceInputError(
                "Choose two payees in this budget",
              );
            }
            await client.mergePayees(targetPayeeId, [payeeId]);
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    deletePayee(budgetId, payeeId, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const payee = (await client.getPayees()).find(
              (candidate) => candidate.id === payeeId,
            );
            if (!payee || payee.transfer_acct) {
              throw new BudgetMaintenanceInputError(
                "Choose a payee in this budget",
              );
            }
            await client.deletePayee(payeeId);
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    setPayeeDefaultCategory(budgetId, payeeId, categoryId, encryptionPassword) {
      if (categoryId !== null && !categoryId.trim()) {
        return Promise.reject(
          new BudgetMaintenanceInputError(
            "Choose a category or clear the default",
          ),
        );
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const payee = (await client.getPayees()).find(
              (candidate) => candidate.id === payeeId,
            );
            if (!payee || payee.transfer_acct) {
              throw new BudgetMaintenanceInputError(
                "Choose a regular payee in this budget",
              );
            }
            if (categoryId !== null) {
              const category = (await client.getCategories()).find(
                (candidate) => candidate.id === categoryId,
              );
              if (!category) {
                throw new BudgetMaintenanceInputError(
                  "Choose an active category in this budget",
                );
              }
            }

            const rules = await client.getPayeeRules(payeeId);
            const matchingRules = rules.filter((rule) => {
              const conditions = rule.conditions;
              const stage = (rule as { stage: string | null }).stage;
              return (
                (stage === null || stage === "default") &&
                rule.conditionsOp === "and" &&
                conditions.length === 1 &&
                conditions[0]?.field === "payee" &&
                conditions[0]?.op === "is" &&
                conditions[0]?.value === payeeId &&
                rule.actions.some(
                  (action) =>
                    "field" in action &&
                    action.field === "category" &&
                    action.op === "set",
                )
              );
            });
            if (matchingRules.length > 1) {
              throw new BudgetMaintenanceInputError(
                "This payee has multiple category rules. Edit them in Actual first.",
              );
            }

            const rule = matchingRules[0];
            if (!rule) {
              if (categoryId === null) return;
              await client.createRule({
                stage: "default",
                conditionsOp: "and",
                conditions: [{ field: "payee", op: "is", value: payeeId }],
                actions: [{ field: "category", op: "set", value: categoryId }],
              });
              return;
            }

            const categoryActionIndexes = rule.actions.flatMap(
              (action, index) =>
                "field" in action &&
                action.field === "category" &&
                action.op === "set"
                  ? [index]
                  : [],
            );
            if (categoryActionIndexes.length > 1) {
              throw new BudgetMaintenanceInputError(
                "This payee has multiple category actions. Edit the rule in Actual first.",
              );
            }
            // matchingRules only retains rules with at least one category-set action.
            const categoryActionIndex = categoryActionIndexes[0]!;

            if (categoryId === null) {
              const actions = rule.actions.filter(
                (_action, index) => index !== categoryActionIndex,
              );
              if (actions.length === 0) {
                await client.deleteRule(rule.id);
              } else {
                await client.updateRule({ ...rule, actions });
              }
              return;
            }

            const actions = rule.actions.map((action, index) =>
              index === categoryActionIndex &&
              "field" in action &&
              action.field === "category" &&
              action.op === "set"
                ? { ...action, value: categoryId }
                : action,
            );
            await client.updateRule({ ...rule, actions });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    setBudgetAmount(
      budgetId,
      categoryId,
      month,
      amountMinor,
      encryptionPassword,
    ) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            if (
              !Number.isSafeInteger(amountMinor) ||
              amountMinor < 0 ||
              amountMinor > MAX_BUDGET_AMOUNT_MINOR
            ) {
              throw new BudgetMaintenanceInputError(
                "Enter a budget amount up to 1,000,000,000 major units",
              );
            }
            const categories = await client.getCategories({ hidden: true });
            if (!categories.some((candidate) => candidate.id === categoryId)) {
              throw new BudgetMaintenanceInputError(
                "Category does not exist in this budget",
              );
            }
            await client.setBudgetAmount(month, categoryId, amountMinor);
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    setBudgetCarryover(
      budgetId,
      categoryId,
      month,
      enabled,
      encryptionPassword,
    ) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const monthData = await client.getBudgetMonth(month);
            const categories = (
              monthData.categoryGroups as Array<{ categories?: unknown[] }>
            )
              .flatMap((group) => group.categories ?? [])
              .filter(isRecord);
            if (!categories.some((category) => category.id === categoryId)) {
              throw new BudgetMaintenanceInputError(
                "Choose a category in this budget month",
              );
            }
            await client.setBudgetCarryover(month, categoryId, enabled);
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    copyBudgetMonth(budgetId, month, encryptionPassword) {
      const year = Number(month.slice(0, 4));
      const monthNumber = Number(month.slice(5, 7));
      if (
        !/^\d{4}-(0[1-9]|1[0-2])$/.test(month) ||
        year < 1 ||
        (year === 1 && monthNumber === 1)
      ) {
        return Promise.reject(
          new BudgetMaintenanceInputError("Choose a valid budget month"),
        );
      }
      const previousMonth =
        monthNumber === 1
          ? `${String(year - 1).padStart(4, "0")}-12`
          : `${String(year).padStart(4, "0")}-${String(monthNumber - 1).padStart(2, "0")}`;
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const sourceMonth = await client.getBudgetMonth(previousMonth);
            const targetMonth = await client.getBudgetMonth(month);
            const activeCategories = await client.getCategories({
              hidden: false,
            });
            const sourceAmounts = new Map<string, number>();
            for (const group of sourceMonth.categoryGroups) {
              for (const category of group.categories ?? []) {
                const id = typeof category.id === "string" ? category.id : null;
                if (id === null) continue;
                sourceAmounts.set(
                  id,
                  readSafeAmount(
                    category.budgeted ?? category.assigned ?? 0,
                    "previous month's category assignment",
                  ),
                );
              }
            }
            const targetCategoryIds = new Set(
              targetMonth.categoryGroups.flatMap((group) =>
                (group.categories ?? [])
                  .map((category) => category.id)
                  .filter((id): id is string => typeof id === "string"),
              ),
            );
            const categories = activeCategories.filter((category) =>
              targetCategoryIds.has(category.id),
            );
            await client.batchBudgetUpdates(async () => {
              for (const category of categories) {
                await client.setBudgetAmount(
                  month,
                  category.id,
                  sourceAmounts.get(category.id) ?? 0,
                );
              }
            });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    applyBudgetTemplate(budgetId, month, assignments, encryptionPassword) {
      if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) {
        return Promise.reject(
          new BudgetMaintenanceInputError("Choose a valid budget month"),
        );
      }
      if (assignments.length === 0 || assignments.length > 500) {
        return Promise.reject(
          new BudgetMaintenanceInputError(
            "Choose a template with 1 to 500 categories",
          ),
        );
      }
      const seen = new Set<string>();
      for (const assignment of assignments) {
        if (
          !assignment.categoryId.trim() ||
          seen.has(assignment.categoryId) ||
          !Number.isSafeInteger(assignment.amountMinor) ||
          assignment.amountMinor < 0 ||
          assignment.amountMinor > MAX_BUDGET_AMOUNT_MINOR
        ) {
          return Promise.reject(
            new BudgetMaintenanceInputError(
              "The template contains an invalid category amount",
            ),
          );
        }
        seen.add(assignment.categoryId);
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const [monthData, categories] = await Promise.all([
              client.getBudgetMonth(month),
              client.getCategories({ hidden: false }),
            ]);
            const monthCategoryIds = new Set(
              monthData.categoryGroups.flatMap((group) =>
                (group.categories ?? [])
                  .map((category) => category.id)
                  .filter((id): id is string => typeof id === "string"),
              ),
            );
            const activeCategoryIds = new Set(
              categories.map((category) => category.id),
            );
            if (
              assignments.some(
                ({ categoryId }) =>
                  !activeCategoryIds.has(categoryId) ||
                  !monthCategoryIds.has(categoryId),
              )
            ) {
              throw new BudgetMaintenanceInputError(
                "A template category is no longer active in this month",
              );
            }
            await client.batchBudgetUpdates(async () => {
              for (const assignment of assignments) {
                await client.setBudgetAmount(
                  month,
                  assignment.categoryId,
                  assignment.amountMinor,
                );
              }
            });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    holdBudgetForNextMonth(budgetId, month, amountMinor, encryptionPassword) {
      if (
        !/^\d{4}-(0[1-9]|1[0-2])$/.test(month) ||
        !Number.isSafeInteger(amountMinor) ||
        amountMinor <= 0 ||
        amountMinor > MAX_BUDGET_AMOUNT_MINOR
      ) {
        return Promise.reject(
          new BudgetMaintenanceInputError(
            "Enter a positive amount to hold for next month",
          ),
        );
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const preferences = await client.getPreferences();
            if (preferences.budgetType === "tracking") {
              throw new BudgetMaintenanceInputError(
                "Hold for next month is only available in an envelope budget",
              );
            }
            const monthData = await client.getBudgetMonth(month);
            const available = readSafeAmount(
              monthData.toBudget ?? 0,
              "ready-to-assign amount",
            );
            if (amountMinor > available) {
              throw new BudgetMaintenanceInputError(
                "The hold cannot exceed the amount ready to assign",
              );
            }
            const held = await client.holdBudgetForNextMonth(
              month,
              amountMinor,
            );
            if (!held) {
              throw new BudgetMaintenanceInputError(
                "There is no money ready to assign in this month",
              );
            }
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    resetBudgetHold(budgetId, month, encryptionPassword) {
      if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) {
        return Promise.reject(
          new BudgetMaintenanceInputError("Choose a valid budget month"),
        );
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          () => client.resetBudgetHold(month),
          writeOptions(encryptionPassword),
        ),
      );
    },
    moveMoney(
      budgetId,
      month,
      fromCategoryId,
      toCategoryId,
      amountMinor,
      encryptionPassword,
    ) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            if (
              !Number.isSafeInteger(amountMinor) ||
              amountMinor <= 0 ||
              amountMinor > MAX_BUDGET_AMOUNT_MINOR
            ) {
              throw new BudgetMaintenanceInputError(
                "Enter a positive move amount up to 1,000,000,000 major units",
              );
            }
            if (fromCategoryId === toCategoryId) {
              throw new BudgetMaintenanceInputError(
                "Choose two different categories",
              );
            }
            const preferences = await client.getPreferences();
            if (preferences.budgetType === "tracking") {
              throw new BudgetMaintenanceInputError(
                "Money can only be moved in an envelope budget",
              );
            }
            const monthData = await client.getBudgetMonth(month);
            const categories = (
              monthData.categoryGroups as Array<{ categories?: unknown[] }>
            )
              .flatMap((group) => group.categories ?? [])
              .filter(isRecord);
            const source = categories.find(
              (category) => category.id === fromCategoryId,
            );
            const destination = categories.find(
              (category) => category.id === toCategoryId,
            );
            if (!source || !destination) {
              throw new BudgetMaintenanceInputError(
                "Choose categories in this budget month",
              );
            }
            if (source.is_income !== destination.is_income) {
              throw new BudgetMaintenanceInputError(
                "Move money between categories of the same type",
              );
            }
            const sourceBudgeted = readSafeAmount(
              source.budgeted ?? source.assigned,
              "source assignment",
            );
            const destinationBudgeted = readSafeAmount(
              destination.budgeted ?? destination.assigned,
              "destination assignment",
            );
            const sourceAvailable = readSafeAmount(
              source.balance ?? source.available,
              "source available amount",
            );
            if (sourceAvailable < amountMinor) {
              throw new BudgetMaintenanceInputError(
                "The source category does not have enough available money",
              );
            }
            await client.batchBudgetUpdates(async () => {
              await client.setBudgetAmount(
                month,
                fromCategoryId,
                sourceBudgeted - amountMinor,
              );
              await client.setBudgetAmount(
                month,
                toCategoryId,
                destinationBudgeted + amountMinor,
              );
            });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    closeAccount(
      budgetId,
      accountId,
      transferAccountId,
      transferCategoryId,
      encryptionPassword,
    ) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const accounts = await client.getAccounts();
            if (!accounts.some((account) => account.id === accountId)) {
              throw new BudgetMaintenanceInputError(
                "Account does not exist in this budget",
              );
            }
            if (
              transferAccountId === null &&
              (await client.getAccountBalance(accountId)) !== 0
            ) {
              throw new BudgetMaintenanceInputError(
                "Choose an account to move this balance before closing it",
              );
            }
            if (transferAccountId !== null) {
              const destination = accounts.find(
                (account) => account.id === transferAccountId,
              );
              if (
                !destination ||
                destination.id === accountId ||
                destination.closed
              ) {
                throw new BudgetMaintenanceInputError(
                  "Choose a different open account in this budget",
                );
              }
            }
            if (transferCategoryId !== null) {
              const categories = await client.getCategories();
              if (
                !categories.some(
                  (category) => category.id === transferCategoryId,
                )
              ) {
                throw new BudgetMaintenanceInputError(
                  "Choose a category in this budget",
                );
              }
            }
            await client.closeAccount(
              accountId,
              transferAccountId ?? undefined,
              transferCategoryId ?? undefined,
            );
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    renameAccount(budgetId, accountId, name, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const accounts = await client.getAccounts();
            if (!accounts.some((account) => account.id === accountId)) {
              throw new BudgetMaintenanceInputError(
                "Account does not exist in this budget",
              );
            }
            await client.updateAccount(accountId, { name });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    reopenAccount(budgetId, accountId, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const account = (await client.getAccounts()).find(
              (candidate) => candidate.id === accountId,
            );
            if (!account) {
              throw new BudgetMaintenanceInputError(
                "Account does not exist in this budget",
              );
            }
            if (!account.closed) {
              throw new BudgetMaintenanceInputError(
                "This account is already open",
              );
            }
            await client.reopenAccount(accountId);
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    moveAccount(budgetId, accountId, targetAccountId, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const accounts = await client.getAccounts();
            const account = accounts.find(
              (candidate) => candidate.id === accountId,
            );
            if (!account) {
              throw new BudgetMaintenanceInputError(
                "Account does not exist in this budget",
              );
            }
            const target =
              targetAccountId == null
                ? undefined
                : accounts.find(
                    (candidate) => candidate.id === targetAccountId,
                  );
            if (targetAccountId != null && !target) {
              throw new BudgetMaintenanceInputError(
                "Choose an account in this budget",
              );
            }
            if (target?.id === account.id) {
              throw new BudgetMaintenanceInputError(
                "Choose a different account position",
              );
            }
            if (
              target &&
              (Boolean(target.closed) !== Boolean(account.closed) ||
                Boolean(target.offbudget) !== Boolean(account.offbudget))
            ) {
              throw new BudgetMaintenanceInputError(
                "Accounts can only be reordered within the same section",
              );
            }
            const internal = client.internal;
            if (!internal) {
              throw new BudgetMaintenanceInputError(
                "This Actual version does not support account ordering",
              );
            }
            await internal.send("account-move", {
              id: accountId,
              targetId: targetAccountId,
              accountGroupId: account.account_group_id ?? null,
            });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    renameCategory(budgetId, categoryId, name, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const categories = await client.getCategories();
            const category = categories.find(
              (candidate) => candidate.id === categoryId,
            );
            if (!category)
              throw new BudgetMaintenanceInputError(
                "Category does not exist in this budget",
              );
            if (category.name !== name) {
              await client.updateCategory(categoryId, { name });
            }
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    createCategoryGroup(budgetId, name, isIncome, encryptionPassword) {
      const cleanName = name.trim();
      if (!cleanName || cleanName.length > 100) {
        throw new BudgetMaintenanceInputError(
          "Category group names must be between 1 and 100 characters",
        );
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const groups = await getAllCategoryGroups(client);
            if (
              groups.some(
                (group) =>
                  group.name.trim().toLocaleLowerCase() ===
                  cleanName.toLocaleLowerCase(),
              )
            ) {
              throw new BudgetMaintenanceInputError(
                "A category group with this name already exists",
              );
            }
            return client.createCategoryGroup({
              name: cleanName,
              is_income: isIncome,
              hidden: false,
            });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    manageCategoryGroup(
      budgetId,
      groupId,
      action,
      name,
      targetGroupId,
      encryptionPassword,
    ) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const groups = await getAllCategoryGroups(client);
            const group = groups.find((candidate) => candidate.id === groupId);
            if (!group) {
              throw new BudgetMaintenanceInputError(
                "Category group does not exist in this budget",
              );
            }
            if (action === "rename") {
              const cleanName = name?.trim() ?? "";
              if (!cleanName || cleanName.length > 100) {
                throw new BudgetMaintenanceInputError(
                  "Category group names must be between 1 and 100 characters",
                );
              }
              if (
                groups.some(
                  (candidate) =>
                    candidate.id !== groupId &&
                    candidate.name.trim().toLocaleLowerCase() ===
                      cleanName.toLocaleLowerCase(),
                )
              ) {
                throw new BudgetMaintenanceInputError(
                  "A category group with this name already exists",
                );
              }
              await client.updateCategoryGroup(groupId, { name: cleanName });
              return;
            }
            if (action === "hide" || action === "show") {
              await client.updateCategoryGroup(groupId, {
                hidden: action === "hide",
              });
              return;
            }
            if (targetGroupId === groupId) {
              throw new BudgetMaintenanceInputError(
                "Choose a different category group position",
              );
            }
            if (
              targetGroupId !== null &&
              targetGroupId !== undefined &&
              !groups.some((candidate) => candidate.id === targetGroupId)
            ) {
              throw new BudgetMaintenanceInputError(
                "Choose a category group in this budget",
              );
            }
            const internal = client.internal;
            if (!internal) {
              throw new BudgetMaintenanceInputError(
                "This Actual version does not support category group ordering",
              );
            }
            await internal.send("category-group-move", {
              id: groupId,
              targetId: targetGroupId ?? null,
            });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    manageCategory(
      budgetId,
      categoryId,
      action,
      targetCategoryId,
      encryptionPassword,
      targetGroupId,
    ) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const categories = await getAllCategories(client);
            const category = categories.find(
              (candidate) => candidate.id === categoryId,
            );
            if (!category) {
              throw new BudgetMaintenanceInputError(
                "Category does not exist in this budget",
              );
            }
            if (action === "hide" || action === "show") {
              await client.updateCategory(categoryId, {
                hidden: action === "hide",
              });
              return;
            }
            const destinationGroupId = targetGroupId ?? category.group_id;
            if (
              targetGroupId != null &&
              destinationGroupId !== category.group_id
            ) {
              const groups = await getAllCategoryGroups(client);
              const destinationGroup = groups.find(
                (candidate) =>
                  candidate.id === destinationGroupId && !candidate.hidden,
              );
              if (!destinationGroup) {
                throw new BudgetMaintenanceInputError(
                  "Choose a visible category group in this budget",
                );
              }
              if (destinationGroup.is_income !== category.is_income) {
                throw new BudgetMaintenanceInputError(
                  "Income categories must stay in an income group",
                );
              }
              if (targetCategoryId != null) {
                throw new BudgetMaintenanceInputError(
                  "A category moved to another group starts at the end of that group",
                );
              }
              const internal = client.internal;
              if (!internal) {
                throw new BudgetMaintenanceInputError(
                  "This Actual version does not support moving categories between groups",
                );
              }
              await internal.send("category-move", {
                id: categoryId,
                groupId: destinationGroupId,
                targetId: null,
              });
              return;
            }
            if (targetCategoryId === categoryId) {
              throw new BudgetMaintenanceInputError(
                "Choose a different category position",
              );
            }
            const target =
              targetCategoryId == null
                ? undefined
                : categories.find(
                    (candidate) => candidate.id === targetCategoryId,
                  );
            if (
              targetCategoryId != null &&
              (!target || target.group_id !== category.group_id)
            ) {
              throw new BudgetMaintenanceInputError(
                "Choose a category in the same group",
              );
            }
            const internal = client.internal;
            if (!internal) {
              throw new BudgetMaintenanceInputError(
                "This Actual version does not support category ordering",
              );
            }
            await internal.send("category-move", {
              id: categoryId,
              groupId: category.group_id,
              targetId: targetCategoryId ?? null,
            });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    deleteCategory(
      budgetId,
      categoryId,
      transferCategoryId,
      encryptionPassword,
    ) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const categories = await client.getCategories();
            const category = categories.find(
              (candidate) => candidate.id === categoryId,
            );
            if (!category) return;
            if (transferCategoryId !== null) {
              const destination = categories.find(
                (candidate) => candidate.id === transferCategoryId,
              );
              if (!destination || destination.id === category.id) {
                throw new BudgetMaintenanceInputError(
                  "Choose a different category in this budget",
                );
              }
              if (destination.is_income !== category.is_income) {
                throw new BudgetMaintenanceInputError(
                  "Transactions can only move to a category of the same type",
                );
              }
            }
            await client.deleteCategory(
              categoryId,
              transferCategoryId ?? undefined,
            );
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    async renameSchedule(budgetId, scheduleId, name, encryptionPassword) {
      const normalizedName = name.trim();
      if (!normalizedName || normalizedName.length > 100) {
        throw new BudgetMaintenanceInputError(
          "Schedule names must be between 1 and 100 characters",
        );
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const schedules = await client.getSchedules();
            const schedule = schedules.find(
              (candidate) => candidate.id === scheduleId,
            );
            if (!schedule || schedule.completed) {
              throw new BudgetMaintenanceInputError(
                "Choose an active schedule in this budget",
              );
            }
            const duplicate = schedules.some(
              (candidate) =>
                candidate.id !== scheduleId &&
                candidate.name?.trim() === normalizedName,
            );
            if (duplicate) {
              throw new BudgetMaintenanceInputError(
                "A schedule with this name already exists",
              );
            }
            // A name-only edit must keep an occurrence that was skipped.
            await client.updateSchedule(
              scheduleId,
              { name: normalizedName },
              false,
            );
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    async editSchedule(budgetId, scheduleId, edit, encryptionPassword) {
      validateBudgetScheduleEdit(edit);
      const normalizedName = edit.name?.trim();
      if (
        edit.name !== undefined &&
        (!normalizedName || normalizedName.length > 100)
      ) {
        throw new BudgetMaintenanceInputError(
          "Schedule names must be between 1 and 100 characters",
        );
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const schedules = await client.getSchedules();
            const schedule = schedules.find(
              (candidate) => candidate.id === scheduleId,
            );
            if (!schedule || schedule.completed) {
              throw new BudgetMaintenanceInputError(
                "Choose an active schedule in this budget",
              );
            }
            if (
              normalizedName &&
              schedules.some(
                (candidate) =>
                  candidate.id !== scheduleId &&
                  candidate.name?.trim() === normalizedName,
              )
            ) {
              throw new BudgetMaintenanceInputError(
                "A schedule with this name already exists",
              );
            }

            const fields: Parameters<typeof client.updateSchedule>[1] = {};
            if (normalizedName !== undefined) fields.name = normalizedName;
            if (edit.accountId !== undefined) {
              const account = (await client.getAccounts()).find(
                (candidate) => candidate.id === edit.accountId,
              );
              if (!account || account.closed) {
                throw new BudgetMaintenanceInputError(
                  "Choose an open account in this budget",
                );
              }
              fields.account = edit.accountId;
            }
            if (edit.payeeId !== undefined) {
              const payee = (await client.getPayees()).find(
                (candidate) => candidate.id === edit.payeeId,
              );
              if (!payee) {
                throw new BudgetMaintenanceInputError(
                  "Choose a payee in this budget",
                );
              }
              fields.payee = edit.payeeId;
            }
            if (edit.amountMinor !== undefined) {
              fields.amount = edit.amountMinor;
            } else if (edit.amountRangeMinor !== undefined) {
              fields.amount = edit.amountRangeMinor;
            }
            if (edit.amountOp !== undefined) fields.amountOp = edit.amountOp;
            if (edit.date !== undefined) {
              fields.date = edit.date as NonNullable<typeof fields.date>;
            }

            await client.updateSchedule(
              scheduleId,
              fields,
              edit.accountId !== undefined || edit.date !== undefined,
            );
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    skipSchedule(budgetId, scheduleId, expectedNextDate, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const schedule = (await client.getSchedules()).find(
              (candidate) => candidate.id === scheduleId,
            );
            if (!schedule || schedule.completed) {
              throw new BudgetScheduleChangedError();
            }
            if (
              typeof schedule.date !== "object" ||
              schedule.date === null ||
              !("frequency" in schedule.date)
            ) {
              throw new BudgetMaintenanceInputError(
                "A one-time schedule has no next date to skip",
              );
            }
            if (schedule.next_date !== expectedNextDate) {
              throw new BudgetScheduleChangedError();
            }
            const internal = client.internal;
            if (!internal) {
              throw new Error("Actual schedule actions are unavailable");
            }
            await internal.send("schedule/skip-next-date", { id: scheduleId });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    postSchedule(budgetId, scheduleId, expectedNextDate, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const schedule = (await client.getSchedules()).find(
              (candidate) => candidate.id === scheduleId,
            );
            if (!schedule || schedule.completed) {
              throw new BudgetScheduleChangedError();
            }
            if (schedule.next_date !== expectedNextDate) {
              throw new BudgetScheduleChangedError();
            }
            if (!schedule.account) {
              throw new BudgetMaintenanceInputError(
                "Choose an account before posting this schedule",
              );
            }
            const internal = client.internal;
            if (!internal) {
              throw new Error("Actual schedule actions are unavailable");
            }
            await internal.send("schedule/post-transaction", {
              id: scheduleId,
            });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    deleteSchedule(budgetId, scheduleId, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const schedules = await client.getSchedules();
            if (!schedules.some((candidate) => candidate.id === scheduleId)) {
              throw new BudgetMaintenanceInputError(
                "Schedule does not exist in this budget",
              );
            }
            await client.deleteSchedule(scheduleId);
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
  };
}

function waitForCompletion<T>(
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

function writeOptions(encryptionPassword?: string): BudgetOperationOptions {
  return {
    synchronizeAfterWrite: true,
    ...(encryptionPassword === undefined ? {} : { encryptionPassword }),
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function readSafeAmount(value: unknown, description: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value)) {
    throw new BudgetMaintenanceInputError(
      `Actual returned an invalid ${description}`,
    );
  }
  return value;
}
