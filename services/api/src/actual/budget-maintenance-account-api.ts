import {
  BudgetMaintenanceInputError,
  type BudgetMaintenanceApiDependencies,
  type BudgetMaintenanceApiPort,
  writeOptions,
} from "./budget-maintenance-contracts.js";

export function createAccountOperations({
  locks,
  session,
  client,
}: BudgetMaintenanceApiDependencies): Pick<
  BudgetMaintenanceApiPort,
  | "createAccount"
  | "closeAccount"
  | "renameAccount"
  | "reopenAccount"
  | "moveAccount"
> {
  return {
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
            return accountId;
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
  };
}
