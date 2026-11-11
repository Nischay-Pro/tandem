import {
  createTransactionRevision,
  snapshotActualTransaction,
  type TransactionSnapshot,
} from "./transaction-reconciliation.js";
import type { ActualBudgetSessionPort } from "./transaction-reconciler.js";
import {
  TransactionReconciler,
  type TransactionReconciliationRequest,
  type TransactionReconciliationResult,
} from "./transaction-reconciler.js";
import type { ActualTransactionStore } from "./transaction-store.js";
import type { DurableWriteJournal } from "../write-journal/contracts.js";

export interface BudgetTransactionSnapshot {
  snapshot: TransactionSnapshot;
  revision: string;
}

export interface TransactionApiPort {
  readTransaction(
    budgetId: string,
    transactionId: string,
    encryptionPassword?: string,
  ): Promise<BudgetTransactionSnapshot | undefined>;
  submit(
    request: TransactionReconciliationRequest,
  ): Promise<TransactionReconciliationResult>;
  getOperation(
    principalId: string,
    operationId: string,
    encryptionPassword?: string,
  ): Promise<TransactionReconciliationResult | undefined>;
  discardUncertainOperation(
    principalId: string,
    operationId: string,
  ): Promise<TransactionReconciliationResult | undefined>;
}

export function createTransactionApi(
  session: ActualBudgetSessionPort,
  transactions: ActualTransactionStore,
  reconciler: TransactionReconciler,
  journal: Pick<DurableWriteJournal, "get" | "getConflict">,
): TransactionApiPort {
  return {
    async readTransaction(budgetId, transactionId, encryptionPassword) {
      return session.runBudget(
        budgetId,
        async () => {
          const actual = await transactions.read(transactionId);
          if (actual === undefined) {
            return undefined;
          }
          const snapshot = snapshotActualTransaction(actual);
          if (snapshot.id !== transactionId) {
            throw new Error("Actual returned a different transaction ID");
          }
          return {
            snapshot,
            revision: createTransactionRevision(snapshot),
          };
        },
        encryptionPassword === undefined ? undefined : { encryptionPassword },
      );
    },
    submit(request) {
      return reconciler.submit(request);
    },
    async getOperation(principalId, operationId, encryptionPassword) {
      const operation = await journal.get(operationId);
      if (
        !operation ||
        operation.principalId !== principalId ||
        operation.entityType !== "transaction"
      ) {
        return undefined;
      }
      if (operation.status === "uncertain") {
        return reconciler.checkOperationStatus(operationId, encryptionPassword);
      }
      const conflict =
        operation.status === "conflict"
          ? ((await journal.getConflict(operationId)) ?? null)
          : null;
      return { operation, conflict };
    },
    async discardUncertainOperation(principalId, operationId) {
      const operation = await journal.get(operationId);
      if (
        !operation ||
        operation.principalId !== principalId ||
        operation.entityType !== "transaction"
      ) {
        return undefined;
      }
      return reconciler.discardUncertainOperation(operationId);
    },
  };
}
