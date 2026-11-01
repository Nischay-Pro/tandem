import { createHash } from "node:crypto";
import * as Actual from "@actual-app/api";
import type { ActualBudgetSessionPort } from "./transaction-reconciler.js";
import type {
  BudgetEntityType,
  BudgetProjectionRecord,
  BudgetSnapshotReader,
  JsonObject,
} from "../budget-projection/contracts.js";
import type { JsonValue } from "../write-journal/contracts.js";
import {
  createTransactionRevision,
  snapshotActualTransaction,
} from "./transaction-reconciliation.js";

export const ACTUAL_TRANSACTION_PAGE_SIZE = 500;
export const ACTUAL_TRANSACTION_MAX_ROWS = 1_000_000;

export type ActualBudgetReadApi = Pick<
  typeof Actual,
  | "getAccounts"
  | "getAccountBalance"
  | "getAccountGroups"
  | "getCategories"
  | "getCategoryGroups"
  | "getPayees"
  | "getBudgetMonths"
  | "getBudgetMonth"
  | "q"
  | "aqlQuery"
>;

export class ActualBudgetSnapshotReader implements BudgetSnapshotReader {
  constructor(
    private readonly session: ActualBudgetSessionPort,
    private readonly actual: ActualBudgetReadApi = Actual,
    private readonly pageSize = ACTUAL_TRANSACTION_PAGE_SIZE,
    private readonly maxTransactionRows = ACTUAL_TRANSACTION_MAX_ROWS,
  ) {
    if (!Number.isSafeInteger(pageSize) || pageSize < 1) {
      throw new Error(
        "Actual transaction page size must be a positive integer",
      );
    }
    if (!Number.isSafeInteger(maxTransactionRows) || maxTransactionRows < 1) {
      throw new Error(
        "Actual transaction row limit must be a positive integer",
      );
    }
  }

  read(
    budgetId: string,
    encryptionPassword?: string,
  ): Promise<BudgetProjectionRecord[]> {
    return this.session.runBudget(
      budgetId,
      () => this.readLoadedBudget(),
      encryptionPassword === undefined ? undefined : { encryptionPassword },
    );
  }

  private async readLoadedBudget(): Promise<BudgetProjectionRecord[]> {
    const records: BudgetProjectionRecord[] = [];

    const accountGroups = requireArray(
      await this.actual.getAccountGroups(),
      "account groups",
    );
    records.push(
      ...accountGroups.map((value) => this.record("account-group", value)),
    );

    const accounts = requireArray(await this.actual.getAccounts(), "accounts");
    for (const value of accounts) {
      const account = requireRecord(value, "account");
      const id = requireId(account.id, "account");
      const balance = await this.actual.getAccountBalance(id);
      if (!Number.isSafeInteger(balance)) {
        throw new Error("Actual returned an invalid account balance");
      }
      records.push(
        this.record("account", { ...account, balance_current: balance }),
      );
    }

    const categoryGroups = requireArray(
      await this.actual.getCategoryGroups(),
      "category groups",
    );
    for (const value of categoryGroups) {
      const group = requireRecord(value, "category group");
      const body = { ...group };
      delete body.categories;
      records.push(this.record("category-group", body));
    }

    const categories = requireArray(
      await this.actual.getCategories(),
      "categories",
    );
    records.push(...categories.map((value) => this.record("category", value)));

    const payees = requireArray(await this.actual.getPayees(), "payees");
    records.push(...payees.map((value) => this.record("payee", value)));

    const months = requireArray(
      await this.actual.getBudgetMonths(),
      "budget months",
    );
    const seenMonths = new Set<string>();
    for (const value of months) {
      if (typeof value !== "string" || value.length === 0) {
        throw new Error("Actual returned an invalid budget month");
      }
      if (seenMonths.has(value)) {
        throw new Error("Actual returned a duplicate budget month");
      }
      seenMonths.add(value);
      const month = requireRecord(
        await this.actual.getBudgetMonth(value),
        "budget month data",
      );
      if (month.month !== value) {
        throw new Error("Actual returned data for a different budget month");
      }
      records.push(this.record("budget-month", month, value));
    }

    records.push(...(await this.readTransactions()));
    records.sort((left, right) =>
      left.type === right.type
        ? left.id.localeCompare(right.id)
        : left.type.localeCompare(right.type),
    );
    assertUniqueRecords(records);
    return records;
  }

  private async readTransactions(): Promise<BudgetProjectionRecord[]> {
    const records: BudgetProjectionRecord[] = [];
    let offset = 0;

    while (true) {
      const query = this.actual
        .q("transactions")
        .select("*")
        .options({ splits: "grouped" })
        .orderBy("id")
        .limit(this.pageSize)
        .offset(offset);
      const result = await this.actual.aqlQuery(query);
      if (!isRecord(result) || !Array.isArray(result.data)) {
        throw new Error("Actual transaction query returned an invalid result");
      }
      if (result.data.length > this.pageSize) {
        throw new Error(
          "Actual transaction query exceeded the requested page size",
        );
      }
      if (result.data.length === 0) {
        break;
      }

      for (const value of result.data) {
        const row = requireRecord(value, "transaction");
        const children = row.subtransactions;
        if (children !== undefined && !Array.isArray(children)) {
          throw new Error("Actual returned invalid transaction splits");
        }
        const splitRows =
          children?.map((child) => requireRecord(child, "split transaction")) ??
          [];
        const parent = snapshotActualTransaction({
          ...row,
          subtransactions: splitRows,
        });
        records.push(
          this.record(
            "transaction",
            parent,
            parent.id,
            createTransactionRevision(parent),
          ),
        );
        for (const splitRow of splitRows) {
          const split = snapshotActualTransaction(splitRow);
          records.push(
            this.record(
              "transaction",
              split,
              split.id,
              createTransactionRevision(split),
            ),
          );
        }
      }

      offset += result.data.length;
      if (offset > this.maxTransactionRows) {
        throw new Error(
          "Actual transaction result exceeded the configured row limit",
        );
      }
      if (result.data.length < this.pageSize) {
        break;
      }
    }
    return records;
  }

  private record(
    type: BudgetEntityType,
    value: unknown,
    idOverride?: string,
    revisionOverride?: string,
  ): BudgetProjectionRecord {
    const input = requireRecord(value, type);
    const id = idOverride ?? requireId(input.id, type);
    const body = toJsonObject(input, type);
    if (body.id !== undefined && body.id !== id) {
      throw new Error(`Actual returned a mismatched ${type} ID`);
    }
    if (type === "budget-month" && body.month !== id) {
      throw new Error("Actual returned a mismatched budget month ID");
    }
    return {
      type,
      id,
      body,
      revision: revisionOverride ?? createBudgetRecordRevision(body),
    };
  }
}

export function createBudgetRecordRevision(body: JsonObject): string {
  return createHash("sha256").update(canonicalJson(body)).digest("hex");
}

function requireArray(value: unknown, description: string): unknown[] {
  if (!Array.isArray(value)) {
    throw new Error(`Actual returned invalid ${description}`);
  }
  return value;
}

function requireRecord(
  value: unknown,
  description: string,
): Record<string, unknown> {
  if (!isRecord(value)) {
    throw new Error(`Actual returned an invalid ${description}`);
  }
  return value;
}

function requireId(value: unknown, description: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new Error(`Actual returned an invalid ${description} ID`);
  }
  return value;
}

function toJsonObject(
  value: Record<string, unknown>,
  description: string,
): JsonObject {
  const normalized = toJsonValue(value, description);
  return normalized as JsonObject;
}

function toJsonValue(value: unknown, description: string): JsonValue {
  if (
    value === null ||
    typeof value === "string" ||
    typeof value === "boolean"
  ) {
    return value;
  }
  if (typeof value === "number") {
    if (!Number.isFinite(value)) {
      throw new Error(`Actual returned a non-finite value in ${description}`);
    }
    return value;
  }
  if (Array.isArray(value)) {
    return value.map((item) => toJsonValue(item, description));
  }
  if (isRecord(value)) {
    const normalized: JsonObject = {};
    for (const [key, item] of Object.entries(value)) {
      if (item !== undefined) {
        normalized[key] = toJsonValue(item, description);
      }
    }
    return normalized;
  }
  throw new Error(`Actual returned non-JSON data in ${description}`);
}

function canonicalJson(value: JsonValue): string {
  if (value === null || typeof value !== "object") {
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map(canonicalJson).join(",")}]`;
  }
  const entries = Object.keys(value)
    .sort()
    .map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key]!)}`);
  return `{${entries.join(",")}}`;
}

function assertUniqueRecords(records: readonly BudgetProjectionRecord[]): void {
  const keys = new Set<string>();
  for (const record of records) {
    const key = `${record.type}\u0000${record.id}`;
    if (keys.has(key)) {
      throw new Error(`Actual returned duplicate ${record.type} IDs`);
    }
    keys.add(key);
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && !Array.isArray(value) && typeof value === "object";
}
