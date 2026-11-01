import * as actualApi from "@actual-app/api";
import type { TransactionPatch } from "./transaction-reconciliation.js";

export interface ActualTransactionStore {
  read(id: string): Promise<unknown | undefined>;
  update(id: string, fields: TransactionPatch): Promise<unknown>;
  delete(id: string): Promise<unknown>;
}

type ActualTransactionApiClient = Pick<
  typeof actualApi,
  "aqlQuery" | "q" | "updateTransaction" | "deleteTransaction"
>;

export function createActualTransactionStore(
  client: ActualTransactionApiClient = actualApi,
): ActualTransactionStore {
  return {
    async read(id) {
      const query = client
        .q("transactions")
        .filter({ id })
        .select("*")
        .options({ splits: "grouped" });
      const result = await client.aqlQuery(query);
      if (!isRecord(result) || !Array.isArray(result.data)) {
        throw new Error("Actual transaction query returned an invalid result");
      }
      if (result.data.some((row) => !isRecord(row))) {
        throw new Error("Actual transaction query returned an invalid row");
      }

      const matches: Record<string, unknown>[] = [];
      for (const row of result.data) {
        const transaction = row as Record<string, unknown>;
        if (transaction.id === id) {
          matches.push(transaction);
        }

        const subtransactions = transaction.subtransactions;
        if (subtransactions === undefined) {
          continue;
        }
        if (!Array.isArray(subtransactions)) {
          throw new Error(
            "Actual transaction query returned invalid subtransactions",
          );
        }
        for (const child of subtransactions) {
          if (!isRecord(child)) {
            throw new Error(
              "Actual transaction query returned an invalid subtransaction",
            );
          }
          if (child.id === id) {
            matches.push(child);
          }
        }
      }
      if (matches.length > 1) {
        throw new Error("Actual transaction query returned duplicate IDs");
      }
      return matches[0];
    },
    update(id, fields) {
      return client.updateTransaction(
        id,
        fields as Parameters<typeof actualApi.updateTransaction>[1],
      );
    },
    delete(id) {
      return client.deleteTransaction(id);
    },
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && !Array.isArray(value) && typeof value === "object";
}
