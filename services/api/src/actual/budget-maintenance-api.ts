import * as actualApi from "@actual-app/api";
import type { BudgetOperationOptions } from "./actual-session.js";
import type { BudgetLockPort } from "./transaction-reconciler.js";

export interface BudgetMaintenanceApiPort {
  runBankSync(budgetId: string, encryptionPassword?: string): Promise<void>;
  createAccount(
    budgetId: string,
    name: string,
    offbudget: boolean,
    encryptionPassword?: string,
  ): Promise<void>;
  closeAccount(
    budgetId: string,
    accountId: string,
    transferAccountId: string | null,
    transferCategoryId: string | null,
    encryptionPassword?: string,
  ): Promise<void>;
  renameCategory(
    budgetId: string,
    categoryId: string,
    name: string,
    encryptionPassword?: string,
  ): Promise<void>;
  deleteCategory(
    budgetId: string,
    categoryId: string,
    transferCategoryId: string | null,
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

type ActualBudgetMaintenanceClient = Pick<
  typeof actualApi,
  | "runBankSync"
  | "createAccount"
  | "closeAccount"
  | "getAccounts"
  | "getCategories"
  | "updateCategory"
  | "deleteCategory"
>;

export function createBudgetMaintenanceApi(
  locks: BudgetLockPort,
  session: ActualBudgetMaintenanceSession,
  client: ActualBudgetMaintenanceClient = actualApi,
): BudgetMaintenanceApiPort {
  return {
    runBankSync(budgetId, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            await client.runBankSync();
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    createAccount(budgetId, name, offbudget, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            await client.createAccount({ name, offbudget });
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
  };
}

function writeOptions(encryptionPassword?: string): BudgetOperationOptions {
  return {
    synchronizeAfterWrite: true,
    ...(encryptionPassword === undefined ? {} : { encryptionPassword }),
  };
}
