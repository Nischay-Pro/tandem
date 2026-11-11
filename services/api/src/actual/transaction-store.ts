import * as actualApi from "@actual-app/api";
import {
  isTandemTransactionId,
  type TransactionCreateInput,
  type TransactionPatch,
} from "./transaction-reconciliation.js";

export interface ActualTransactionStore {
  read(id: string): Promise<unknown | undefined>;
  validateCreateInput(
    input: TransactionCreateInput,
  ): Promise<TransactionCreateValidationIssue | undefined>;
  validateUpdateInput(
    fields: TransactionPatch,
  ): Promise<TransactionCreateValidationIssue | undefined>;
  create(id: string, input: TransactionCreateInput): Promise<unknown>;
  update(id: string, fields: TransactionPatch): Promise<unknown>;
  delete(id: string): Promise<unknown>;
}

type ActualTransactionApiClient = Pick<
  typeof actualApi,
  | "aqlQuery"
  | "q"
  | "addTransactions"
  | "getAccounts"
  | "getCategories"
  | "getPayees"
  | "updateTransaction"
  | "deleteTransaction"
>;

export interface TransactionCreateValidationIssue {
  code:
    | "account-not-found"
    | "account-closed"
    | "payee-not-found"
    | "category-not-found"
    | "category-hidden";
  message: string;
}

export function createActualTransactionStore(
  client: ActualTransactionApiClient = actualApi,
): ActualTransactionStore {
  const validatedPayeeTransfers = new WeakMap<
    TransactionCreateInput,
    { id: string; transferAccount: boolean }
  >();

  async function read(id: string): Promise<unknown | undefined> {
    const tandemId = isTandemTransactionId(id);
    const query = client
      .q("transactions")
      .filter(tandemId ? { imported_id: id } : { id })
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
        (tandemId && transaction.imported_id === id)
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
    if (!isTandemTransactionId(id)) {
      return id;
    }
    const transaction = await read(id);
    if (!isRecord(transaction) || typeof transaction.id !== "string") {
      throw new Error("Actual transaction no longer exists");
    }
    return transaction.id;
  }

  async function validateCreateInput(
    input: TransactionCreateInput,
  ): Promise<TransactionCreateValidationIssue | undefined> {
    const accounts = await client.getAccounts();
    const account = accounts.find(
      (candidate) => candidate.id === input.account,
    );
    if (!account) {
      return {
        code: "account-not-found",
        message: "Account does not exist in this budget",
      };
    }
    if (account.closed) {
      return {
        code: "account-closed",
        message: "Choose an open account for this transaction",
      };
    }

    if (input.payee !== null) {
      const payees = await client.getPayees();
      const payee = payees.find((candidate) => candidate.id === input.payee);
      if (!payee) {
        validatedPayeeTransfers.delete(input);
        return {
          code: "payee-not-found",
          message: "Payee does not exist in this budget",
        };
      }
      validatedPayeeTransfers.set(input, {
        id: payee.id,
        transferAccount: Boolean(payee.transfer_acct),
      });
    } else {
      validatedPayeeTransfers.delete(input);
    }

    if (input.category !== null) {
      const categories = await client.getCategories();
      let exists = categories.some(
        (candidate) => candidate.id === input.category,
      );
      if (!exists) {
        const hiddenCategories = await client.getCategories({ hidden: true });
        exists = hiddenCategories.some(
          (candidate) => candidate.id === input.category,
        );
      }
      if (!exists) {
        return {
          code: "category-not-found",
          message: "Category does not exist in this budget",
        };
      }
    }
    return undefined;
  }

  async function validateUpdateInput(
    fields: TransactionPatch,
  ): Promise<TransactionCreateValidationIssue | undefined> {
    if (fields.account !== undefined) {
      const accounts = await client.getAccounts();
      const account = accounts.find(
        (candidate) => candidate.id === fields.account,
      );
      if (!account) {
        return {
          code: "account-not-found",
          message: "Account does not exist in this budget",
        };
      }
      if (account.closed) {
        return {
          code: "account-closed",
          message: "Choose an open account for this transaction",
        };
      }
    }

    if (fields.payee !== undefined && fields.payee !== null) {
      const payees = await client.getPayees();
      if (!payees.some((candidate) => candidate.id === fields.payee)) {
        return {
          code: "payee-not-found",
          message: "Payee does not exist in this budget",
        };
      }
    }

    if (fields.category !== undefined && fields.category !== null) {
      const categories = await client.getCategories();
      if (categories.some((candidate) => candidate.id === fields.category)) {
        return undefined;
      }
      const hiddenCategories = await client.getCategories({ hidden: true });
      if (
        hiddenCategories.some((candidate) => candidate.id === fields.category)
      ) {
        return {
          code: "category-hidden",
          message: "Choose a visible category for this transaction",
        };
      }
      return {
        code: "category-not-found",
        message: "Category does not exist in this budget",
      };
    }

    return undefined;
  }

  return {
    read,
    validateCreateInput,
    validateUpdateInput,
    async create(id, input) {
      if (!isTandemTransactionId(id)) {
        throw new Error("New transactions require a Tandem transaction ID");
      }
      let transferPayee: boolean | undefined;
      if (input.payee !== null) {
        const validatedPayee = validatedPayeeTransfers.get(input);
        validatedPayeeTransfers.delete(input);
        if (validatedPayee?.id === input.payee) {
          transferPayee = validatedPayee.transferAccount;
        } else {
          transferPayee = Boolean(
            (await client.getPayees()).find((payee) => payee.id === input.payee)
              ?.transfer_acct,
          );
        }
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
        { runTransfers: transferPayee === true, learnCategories: false },
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
