import * as actualApi from "@actual-app/api";
import type { BudgetOperationOptions } from "./actual-session.js";
import type { BudgetLockPort } from "./transaction-reconciler.js";

/** Maximum category assignment: 1,000,000,000 major currency units. */
export const MAX_BUDGET_AMOUNT_MINOR = 100_000_000_000;
export const BANK_SYNC_COOLDOWN_MS = 60_000;
export const BANK_SYNC_TIMEOUT_MS = 120_000;

export interface BudgetMaintenanceApiPort {
  runBankSync(budgetId: string, encryptionPassword?: string): Promise<void>;
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
  renameCategory(
    budgetId: string,
    categoryId: string,
    name: string,
    encryptionPassword?: string,
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
  setBudgetAmount(
    budgetId: string,
    categoryId: string,
    month: string,
    amountMinor: number,
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
  skipSchedule(
    budgetId: string,
    scheduleId: string,
    encryptionPassword?: string,
  ): Promise<void>;
  postSchedule(
    budgetId: string,
    scheduleId: string,
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
  | "createAccount"
  | "updateAccount"
  | "getAccountGroups"
  | "closeAccount"
  | "reopenAccount"
  | "getAccounts"
  | "getAccountBalance"
  | "getCategories"
  | "getCategoryGroups"
  | "setBudgetAmount"
  | "getBudgetMonth"
  | "getPreferences"
  | "batchBudgetUpdates"
  | "createCategory"
  | "createPayee"
  | "getPayees"
  | "updateCategory"
  | "deleteCategory"
  | "getSchedules"
  | "updateSchedule"
  | "deleteSchedule"
  | "internal"
>;

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
            const categories = await client.getCategories({ hidden: true });
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
    skipSchedule(budgetId, scheduleId, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const schedule = (await client.getSchedules()).find(
              (candidate) => candidate.id === scheduleId,
            );
            if (!schedule || schedule.completed) {
              throw new BudgetMaintenanceInputError(
                "Choose an active schedule in this budget",
              );
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
    postSchedule(budgetId, scheduleId, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const schedule = (await client.getSchedules()).find(
              (candidate) => candidate.id === scheduleId,
            );
            if (!schedule || schedule.completed) {
              throw new BudgetMaintenanceInputError(
                "Choose an active schedule in this budget",
              );
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
