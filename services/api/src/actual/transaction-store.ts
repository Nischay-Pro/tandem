import * as actualApi from "@actual-app/api";
import {
  isCompanionTransactionId,
  type TransactionCreateInput,
  type TransactionPatch,
} from "./transaction-reconciliation.js";

export interface ActualTransactionStore {
  read(id: string): Promise<unknown | undefined>;
  create(id: string, input: TransactionCreateInput): Promise<unknown>;
  update(id: string, fields: TransactionPatch): Promise<unknown>;
  delete(id: string): Promise<unknown>;
}

type ActualTransactionApiClient = Pick<
  typeof actualApi,
  | "aqlQuery"
  | "q"
  | "addTransactions"
  | "updateTransaction"
  | "deleteTransaction"
>;

export function createActualTransactionStore(
  client: ActualTransactionApiClient = actualApi,
): ActualTransactionStore {
  async function read(id: string): Promise<unknown | undefined> {
    const companionId = isCompanionTransactionId(id);
    const query = client
      .q("transactions")
      .filter(companionId ? { imported_id: id } : { id })
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
      if (
        transaction.id === id ||
        (companionId && transaction.imported_id === id)
      ) {
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
  }

  async function resolveActualId(id: string): Promise<string> {
    if (!isCompanionTransactionId(id)) {
      return id;
    }
    const transaction = await read(id);
    if (!isRecord(transaction) || typeof transaction.id !== "string") {
      throw new Error("Actual transaction no longer exists");
    }
    return transaction.id;
  }

  return {
    read,
    async create(id, input) {
      if (!isCompanionTransactionId(id)) {
        throw new Error("New transactions require a Companion transaction ID");
      }
      await client.addTransactions(
        input.account,
        [
          {
            date: input.date,
            amount: input.amount,
            payee: input.payee,
            ...(input.category === null ? {} : { category: input.category }),
            notes: input.notes,
            cleared: input.cleared,
            imported_id: id,
          },
        ],
        { runTransfers: false, learnCategories: false },
      );
      const created = await read(id);
      if (created === undefined) {
        throw new Error("Actual did not return the created transaction");
      }
      return created;
    },
    async update(id, fields) {
      return client.updateTransaction(
        await resolveActualId(id),
        fields as Parameters<typeof actualApi.updateTransaction>[1],
      );
    },
    async delete(id) {
      return client.deleteTransaction(await resolveActualId(id));
    },
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && !Array.isArray(value) && typeof value === "object";
}
