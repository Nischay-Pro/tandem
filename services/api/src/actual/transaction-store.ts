import * as actualApi from "@actual-app/api";
import {
  isTandemTransactionId,
  snapshotActualTransaction,
  type TransactionCreateInput,
  type TransactionPatch,
  type TransactionSnapshot,
} from "./transaction-reconciliation.js";

export class ActualTransactionIdempotencyConflictError extends Error {
  constructor(readonly conflictingFields: readonly string[]) {
    super("Transaction ID is already used by a different Actual transaction");
    this.name = "ActualTransactionIdempotencyConflictError";
  }
}

export interface ActualTransactionStore {
  read(id: string): Promise<unknown | undefined>;
  validateCreateInput(
    input: TransactionCreateInput,
  ): Promise<TransactionCreateValidationIssue | undefined>;
  validateUpdateInput(
    fields: TransactionPatch,
    current: Pick<TransactionSnapshot, "account" | "payee">,
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
    | "transfer-account-closed"
    | "transfer-account-same-as-source"
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

  async function validateCategoryReference(
    categoryId: string,
  ): Promise<TransactionCreateValidationIssue | undefined> {
    const visibleCategories = await client.getCategories({ hidden: false });
    if (visibleCategories.some((candidate) => candidate.id === categoryId)) {
      return undefined;
    }

    const hiddenCategories = await client.getCategories({ hidden: true });
    return hiddenCategories.some((candidate) => candidate.id === categoryId)
      ? {
          code: "category-hidden",
          message: "Choose a visible category for this transaction",
        }
      : {
          code: "category-not-found",
          message: "Category does not exist in this budget",
        };
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
      const transferDestinationIssue = validateTransferDestination(
        input.account,
        payee.transfer_acct,
        accounts,
      );
      if (transferDestinationIssue) {
        validatedPayeeTransfers.delete(input);
        return transferDestinationIssue;
      }
      validatedPayeeTransfers.set(input, {
        id: payee.id,
        transferAccount: Boolean(payee.transfer_acct),
      });
    } else {
      validatedPayeeTransfers.delete(input);
    }

    if (input.category !== null) {
      const categoryIssue = await validateCategoryReference(input.category);
      if (categoryIssue) return categoryIssue;
    }
    return undefined;
  }

  async function validateUpdateInput(
    fields: TransactionPatch,
    current: Pick<TransactionSnapshot, "account" | "payee">,
  ): Promise<TransactionCreateValidationIssue | undefined> {
    let accounts: Awaited<ReturnType<typeof client.getAccounts>> | undefined;
    if (fields.account !== undefined) {
      accounts = await client.getAccounts();
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

    const payeeChanged = fields.payee !== undefined;
    const accountChanged = fields.account !== undefined;
    const effectivePayeeId = payeeChanged ? fields.payee : current.payee;
    const effectiveAccountId = fields.account ?? current.account;
    let transferDestinationIssue: TransactionCreateValidationIssue | undefined;
    if (
      (payeeChanged && fields.payee !== null) ||
      (accountChanged && current.payee !== null)
    ) {
      const payees = await client.getPayees();
      const payee = payees.find(
        (candidate) => candidate.id === effectivePayeeId,
      );
      if (payeeChanged && fields.payee !== null && !payee) {
        return {
          code: "payee-not-found",
          message: "Payee does not exist in this budget",
        };
      }
      if (payee?.transfer_acct) {
        accounts ??= await client.getAccounts();
        transferDestinationIssue = validateTransferDestination(
          effectiveAccountId,
          payee.transfer_acct,
          accounts,
        );
      }
    }

    if (transferDestinationIssue) {
      return transferDestinationIssue;
    } else if (fields.category !== undefined && fields.category !== null) {
      return validateCategoryReference(fields.category);
    } else {
      return undefined;
    }
  }

  return {
    read,
    validateCreateInput,
    validateUpdateInput,
    async create(id, input) {
      if (!isTandemTransactionId(id)) {
        throw new Error("New transactions require a Tandem transaction ID");
      }
      const existing = await read(id);
      if (existing !== undefined) {
        const existingSnapshot = snapshotActualTransaction(existing);
        const conflictingFields = transactionCreateMismatchFields(
          existingSnapshot,
          input,
          id,
        );
        if (conflictingFields.length > 0) {
          throw new ActualTransactionIdempotencyConflictError(
            conflictingFields,
          );
        }
        return existing;
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

function transactionCreateMismatchFields(
  snapshot: TransactionSnapshot,
  input: TransactionCreateInput,
  transactionId: string,
): string[] {
  if (snapshot.id !== transactionId && snapshot.importedId !== transactionId) {
    return ["$entity"];
  }

  const mismatches: string[] = [];
  if (snapshot.account !== input.account) mismatches.push("account");
  if (snapshot.amount !== input.amount) mismatches.push("amount");
  if (snapshot.date !== input.date) mismatches.push("date");
  if (snapshot.payee !== input.payee) mismatches.push("payee");
  if (snapshot.category !== input.category) mismatches.push("category");
  if (snapshot.notes !== (input.notes === "" ? null : input.notes)) {
    mismatches.push("notes");
  }
  if (snapshot.cleared !== input.cleared) mismatches.push("cleared");
  return mismatches;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && !Array.isArray(value) && typeof value === "object";
}

function validateTransferDestination(
  sourceAccountId: string,
  destinationAccountId: string | null | undefined,
  accounts: Array<{ id: string; closed?: boolean }>,
): TransactionCreateValidationIssue | undefined {
  if (!destinationAccountId) return undefined;

  const destination = accounts.find(
    (candidate) => candidate.id === destinationAccountId,
  );
  if (!destination) {
    return {
      code: "account-not-found",
      message: "Account does not exist in this budget",
    };
  }
  if (destination.closed) {
    return {
      code: "transfer-account-closed",
      message: "Choose an open destination account for this transfer",
    };
  }
  if (destination.id === sourceAccountId) {
    return {
      code: "transfer-account-same-as-source",
      message: "Choose a different destination account for this transfer",
    };
  }
  return undefined;
}
