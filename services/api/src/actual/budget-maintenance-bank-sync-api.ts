import {
  BANK_SYNC_COOLDOWN_MS,
  BANK_SYNC_TIMEOUT_MS,
  BudgetMaintenanceBusyError,
  BudgetMaintenanceInputError,
  type BudgetMaintenanceApiDependencies,
  type BudgetMaintenanceApiPort,
  isIsoDate,
  isRecord,
  waitForCompletion,
  writeOptions,
} from "./budget-maintenance-contracts.js";
import {
  MAX_TRANSACTION_AMOUNT,
  validateTransactionDate,
} from "./transaction-reconciliation.js";

export function createBankSyncOperations({
  locks,
  session,
  client,
}: BudgetMaintenanceApiDependencies): Pick<
  BudgetMaintenanceApiPort,
  | "runBankSync"
  | "previewStatementImportTransactions"
  | "importStatementTransactions"
> {
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
  };
}
