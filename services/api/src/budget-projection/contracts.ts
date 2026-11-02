import type { JsonValue } from "../write-journal/contracts.js";

export const BUDGET_ENTITY_TYPES = [
  "account",
  "account-group",
  "category",
  "category-group",
  "payee",
  "transaction",
  "budget-month",
] as const;

export type BudgetEntityType = (typeof BUDGET_ENTITY_TYPES)[number];
export type JsonObject = { [key: string]: JsonValue };

export interface BudgetDisplaySettings {
  currencyCode: string;
  currencySymbol: string;
  currencyFractionDigits: number;
  numberFormat: string | null;
  hideFraction: boolean;
  currencySymbolPosition: "before" | "after" | null;
  currencySpaceBetweenAmountAndSymbol: boolean;
}

export interface BudgetSnapshotReadResult {
  records: BudgetProjectionRecord[];
  displaySettings: BudgetDisplaySettings;
}

export interface BudgetProjectionRecord {
  type: BudgetEntityType;
  id: string;
  revision: string;
  body: JsonObject;
}

export interface BudgetProjectionSnapshot {
  budgetId: string;
  generation: string;
  cursor: string;
  records: BudgetProjectionRecord[];
  displaySettings?: BudgetDisplaySettings | null;
}

export interface BudgetProjectionChange {
  cursor: string;
  type: BudgetEntityType;
  id: string;
  operation: "upsert" | "delete";
  revision: string;
  body: JsonObject | null;
}

export interface BudgetProjectionChangePage {
  budgetId: string;
  generation: string;
  currentCursor: string;
  throughCursor: string;
  nextCursor: string;
  hasMore: boolean;
  resetRequired: boolean;
  changes: BudgetProjectionChange[];
  displaySettings?: BudgetDisplaySettings | null;
}

export interface BudgetProjectionStore {
  applySnapshot(
    budgetId: string,
    feedEpoch: string,
    records: readonly BudgetProjectionRecord[],
  ): Promise<BudgetProjectionSnapshot>;
  readSnapshot(budgetId: string): Promise<BudgetProjectionSnapshot | undefined>;
  readChanges(input: {
    budgetId: string;
    generation: string;
    cursor: string;
    throughCursor?: string;
    limit: number;
  }): Promise<BudgetProjectionChangePage | undefined>;
}

export interface BudgetSnapshotReader {
  read(
    budgetId: string,
    encryptionPassword?: string,
  ): Promise<BudgetProjectionRecord[]>;
  readWithSettings?(
    budgetId: string,
    encryptionPassword?: string,
  ): Promise<BudgetSnapshotReadResult>;
}

export interface BudgetLockPort {
  withBudgetLock<T>(budgetId: string, work: () => Promise<T>): Promise<T>;
}

export class BudgetProjectionInputError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "BudgetProjectionInputError";
  }
}
