import { createHash } from "node:crypto";
import type { JsonValue } from "../write-journal/contracts.js";

export interface TransactionSnapshot {
  id: string;
  account: string;
  amount: number;
  date: string;
  payee: string | null;
  category: string | null;
  notes: string | null;
  cleared: boolean | null;
  reconciled: boolean | null;
  importedId: string | null;
  importedPayee: string | null;
  startingBalance: boolean | null;
  schedule: string | null;
  transferId: string | null;
  isParent: boolean;
  isChild: boolean;
  parentId: string | null;
  subtransactions: TransactionSnapshot[];
}

export type TransactionEditField =
  | "account"
  | "amount"
  | "date"
  | "payee"
  | "category"
  | "notes"
  | "cleared"
  | "reconciled";

export type TransactionConflictField =
  | TransactionEditField
  | "transferId"
  | "isParent"
  | "isChild"
  | "parentId"
  | "subtransactions";

export type TransactionPatch = Omit<
  Partial<Pick<TransactionSnapshot, TransactionEditField>>,
  "notes"
> & { notes?: string };

export type TransactionMergePlan =
  | { kind: "apply"; patch: TransactionPatch }
  | { kind: "unchanged" }
  | { kind: "conflict"; conflictingFields: TransactionConflictField[] };

const EDIT_FIELDS: readonly TransactionEditField[] = [
  "account",
  "amount",
  "date",
  "payee",
  "category",
  "notes",
  "cleared",
  "reconciled",
];

const SNAPSHOT_FIELDS: readonly (keyof TransactionSnapshot)[] = [
  "id",
  "account",
  "amount",
  "date",
  "payee",
  "category",
  "notes",
  "cleared",
  "reconciled",
  "importedId",
  "importedPayee",
  "startingBalance",
  "schedule",
  "transferId",
  "isParent",
  "isChild",
  "parentId",
  "subtransactions",
];

const DELETION_COMPARISON_FIELDS: readonly (keyof TransactionSnapshot)[] = [
  "account",
  "amount",
  "date",
  "payee",
  "category",
  "notes",
  "cleared",
  "reconciled",
  "importedId",
  "importedPayee",
  "startingBalance",
  "schedule",
];

export function snapshotActualTransaction(value: unknown): TransactionSnapshot {
  const record = asRecord(value, "Actual transaction");
  return {
    id: requiredString(record.id, "id"),
    account: requiredString(record.account, "account"),
    amount: requiredInteger(record.amount, "amount"),
    date: requiredDate(record.date),
    payee: optionalString(record.payee, "payee"),
    category: optionalString(record.category, "category"),
    notes: normalizeEmptyNote(optionalText(record.notes, "notes")),
    cleared: optionalBoolean(record.cleared, "cleared"),
    reconciled: optionalBoolean(record.reconciled, "reconciled"),
    importedId: optionalString(record.imported_id, "imported_id"),
    importedPayee: optionalText(record.imported_payee, "imported_payee"),
    startingBalance: optionalBoolean(
      record.starting_balance_flag,
      "starting_balance_flag",
    ),
    schedule: optionalString(record.schedule, "schedule"),
    transferId: optionalString(record.transfer_id, "transfer_id"),
    isParent: optionalBoolean(record.is_parent, "is_parent") ?? false,
    isChild: optionalBoolean(record.is_child, "is_child") ?? false,
    parentId: optionalString(record.parent_id, "parent_id"),
    subtransactions: optionalSubtransactions(
      record.subtransactions,
      "subtransactions",
    ),
  };
}

export function parseTransactionSnapshot(
  value: JsonValue,
): TransactionSnapshot {
  const record = asRecord(value, "baseSnapshot");
  const unexpected = Object.keys(record).filter(
    (field) => !SNAPSHOT_FIELDS.includes(field as keyof TransactionSnapshot),
  );
  if (unexpected.length > 0) {
    throw new Error(
      `baseSnapshot contains unsupported fields: ${unexpected.join(", ")}`,
    );
  }

  const missing = SNAPSHOT_FIELDS.filter(
    (field) => field !== "subtransactions" && !Object.hasOwn(record, field),
  );
  if (missing.length > 0) {
    throw new Error(`baseSnapshot is missing fields: ${missing.join(", ")}`);
  }

  return {
    id: requiredString(record.id, "baseSnapshot.id"),
    account: requiredString(record.account, "baseSnapshot.account"),
    amount: requiredInteger(record.amount, "baseSnapshot.amount"),
    date: requiredDate(record.date),
    payee: nullableString(record.payee, "baseSnapshot.payee"),
    category: nullableString(record.category, "baseSnapshot.category"),
    notes: normalizeEmptyNote(nullableText(record.notes, "baseSnapshot.notes")),
    cleared: nullableBoolean(record.cleared, "baseSnapshot.cleared"),
    reconciled: nullableBoolean(record.reconciled, "baseSnapshot.reconciled"),
    importedId: nullableString(record.importedId, "baseSnapshot.importedId"),
    importedPayee: nullableText(
      record.importedPayee,
      "baseSnapshot.importedPayee",
    ),
    startingBalance: nullableBoolean(
      record.startingBalance,
      "baseSnapshot.startingBalance",
    ),
    schedule: nullableString(record.schedule, "baseSnapshot.schedule"),
    transferId: nullableString(record.transferId, "baseSnapshot.transferId"),
    isParent: requiredBoolean(record.isParent, "baseSnapshot.isParent"),
    isChild: requiredBoolean(record.isChild, "baseSnapshot.isChild"),
    parentId: nullableString(record.parentId, "baseSnapshot.parentId"),
    subtransactions:
      record.subtransactions === undefined
        ? []
        : requiredSubtransactions(record.subtransactions),
  };
}

export function parseTransactionPatch(value: JsonValue): TransactionPatch {
  const record = asRecord(value, "transaction patch");
  const fields = Object.keys(record);
  if (fields.length === 0) {
    throw new Error("transaction patch must contain at least one field");
  }

  const unsupported = fields.filter(
    (field) => !EDIT_FIELDS.includes(field as TransactionEditField),
  );
  if (unsupported.length > 0) {
    throw new Error(
      `transaction patch contains unsupported fields: ${unsupported.join(", ")}`,
    );
  }

  const patch: TransactionPatch = {};
  for (const field of fields as TransactionEditField[]) {
    const valueForField = record[field];
    switch (field) {
      case "account":
        patch.account = patchText(requiredString(valueForField, field), field);
        break;
      case "amount":
        patch.amount = requiredInteger(valueForField, field);
        break;
      case "date":
        patch.date = requiredDate(valueForField);
        break;
      case "payee":
        patch.payee = nullablePatchString(valueForField, field);
        break;
      case "category":
        patch.category = nullablePatchString(valueForField, field);
        break;
      case "notes":
        if (typeof valueForField !== "string") {
          throw new Error("notes must be a string");
        }
        patch.notes = patchText(valueForField, field);
        break;
      case "cleared":
        patch.cleared = requiredBoolean(valueForField, field);
        break;
      case "reconciled":
        patch.reconciled = requiredBoolean(valueForField, field);
        break;
    }
  }
  return patch;
}

function nullablePatchString(value: unknown, name: string): string | null {
  const text = nullableString(value, name);
  return text === null ? null : patchText(text, name);
}

function patchText(value: string, name: string): string {
  // eslint-disable-next-line no-control-regex -- Reject unsafe C0 and DEL characters in financial text.
  if (/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/u.test(value)) {
    throw new Error(`${name} must not contain control characters`);
  }
  return value;
}

export function createTransactionRevision(
  snapshot: TransactionSnapshot,
): string {
  return createHash("sha256").update(canonicalSnapshot(snapshot)).digest("hex");
}

export function planTransactionUpdate(
  base: TransactionSnapshot,
  remote: TransactionSnapshot,
  local: TransactionPatch,
): TransactionMergePlan {
  if (base.id !== remote.id) {
    throw new Error("Transaction snapshot IDs do not match");
  }
  if (isStructuredTransaction(base) || isStructuredTransaction(remote)) {
    return {
      kind: "conflict",
      conflictingFields: structuredConflictFields(base, remote),
    };
  }

  const applicable: TransactionPatch = {};
  const conflicts: TransactionConflictField[] = [];

  for (const field of EDIT_FIELDS) {
    if (!Object.hasOwn(local, field)) {
      continue;
    }
    const localValue = local[field];
    const baseValue = base[field];
    const remoteValue = remote[field];
    if (sameSnapshotValue(field, localValue, baseValue)) {
      continue;
    }
    if (sameSnapshotValue(field, remoteValue, baseValue)) {
      Object.assign(applicable, { [field]: localValue });
      continue;
    }
    if (!sameSnapshotValue(field, remoteValue, localValue)) {
      conflicts.push(field);
    }
  }

  if (conflicts.length > 0) {
    return { kind: "conflict", conflictingFields: conflicts };
  }
  if (Object.keys(applicable).length === 0) {
    return { kind: "unchanged" };
  }
  return { kind: "apply", patch: applicable };
}

export function planTransactionDeletion(
  base: TransactionSnapshot,
  remote: TransactionSnapshot,
): { kind: "delete" } | { kind: "conflict"; conflictingFields: string[] } {
  if (base.id !== remote.id) {
    throw new Error("Transaction snapshot IDs do not match");
  }
  if (isStructuredTransaction(base) || isStructuredTransaction(remote)) {
    return {
      kind: "conflict",
      conflictingFields: structuredConflictFields(base, remote),
    };
  }
  const changed = DELETION_COMPARISON_FIELDS.filter(
    (field) => !sameSnapshotValue(field, base[field], remote[field]),
  );
  return changed.length === 0
    ? { kind: "delete" }
    : { kind: "conflict", conflictingFields: changed };
}

export function isStructuredTransaction(
  snapshot: TransactionSnapshot,
): boolean {
  return (
    snapshot.transferId !== null ||
    snapshot.isParent ||
    snapshot.isChild ||
    snapshot.parentId !== null ||
    snapshot.subtransactions.length > 0
  );
}

function structuredConflictFields(
  base: TransactionSnapshot,
  remote: TransactionSnapshot,
): TransactionConflictField[] {
  const fields = ["transferId", "isParent", "isChild", "parentId"] as const;
  const conflicts: TransactionConflictField[] = fields.filter((field) =>
    field === "transferId" || field === "parentId"
      ? base[field] !== null || remote[field] !== null
      : base[field] || remote[field],
  );
  if (base.subtransactions.length > 0 || remote.subtransactions.length > 0) {
    conflicts.push("subtransactions");
  }
  return conflicts;
}

function asRecord(value: unknown, name: string): Record<string, unknown> {
  if (value === null || Array.isArray(value) || typeof value !== "object") {
    throw new Error(`${name} must be an object`);
  }
  return value as Record<string, unknown>;
}

function requiredString(value: unknown, name: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new Error(`${name} must be a non-empty string`);
  }
  return value;
}

function optionalString(value: unknown, name: string): string | null {
  return value === undefined || value === null || value === ""
    ? null
    : requiredString(value, name);
}

function optionalText(value: unknown, name: string): string | null {
  return value === undefined || value === null
    ? null
    : requiredText(value, name);
}

function nullableString(value: unknown, name: string): string | null {
  return value === null ? null : requiredString(value, name);
}

function nullableText(value: unknown, name: string): string | null {
  return value === null ? null : requiredText(value, name);
}

function normalizeEmptyNote(value: string | null): string | null {
  return value === "" ? null : value;
}

function optionalSubtransactions(
  value: unknown,
  name: string,
): TransactionSnapshot[] {
  if (value === undefined || value === null) {
    return [];
  }
  if (!Array.isArray(value)) {
    throw new Error(`${name} must be an array`);
  }
  return value.map((transaction) => snapshotActualTransaction(transaction));
}

function requiredSubtransactions(value: unknown): TransactionSnapshot[] {
  if (!Array.isArray(value)) {
    throw new Error("baseSnapshot.subtransactions must be an array");
  }
  return value.map((transaction) => parseTransactionSnapshot(transaction));
}

function requiredText(value: unknown, name: string): string {
  if (typeof value !== "string") {
    throw new Error(`${name} must be a string`);
  }
  return value;
}

function requiredInteger(value: unknown, name: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value)) {
    throw new Error(`${name} must be a safe integer`);
  }
  return value;
}

function requiredDate(value: unknown): string {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new Error("date must use YYYY-MM-DD format");
  }
  const parsed = new Date(`${value}T00:00:00Z`);
  if (
    Number.isNaN(parsed.getTime()) ||
    parsed.toISOString().slice(0, 10) !== value
  ) {
    throw new Error("date must use YYYY-MM-DD format");
  }
  return value;
}

function optionalBoolean(value: unknown, name: string): boolean | null {
  return value === undefined || value === null
    ? null
    : requiredBoolean(value, name);
}

function nullableBoolean(value: unknown, name: string): boolean | null {
  return value === null ? null : requiredBoolean(value, name);
}

function requiredBoolean(value: unknown, name: string): boolean {
  if (typeof value !== "boolean") {
    throw new Error(`${name} must be a boolean`);
  }
  return value;
}

function sameSnapshotValue(
  field: keyof TransactionSnapshot,
  left: unknown,
  right: unknown,
): boolean {
  return (
    canonicalScalar(normalizeSnapshotValue(field, left)) ===
    canonicalScalar(normalizeSnapshotValue(field, right))
  );
}

function normalizeSnapshotValue(
  field: keyof TransactionSnapshot,
  value: unknown,
): unknown {
  return field === "notes" && value === "" ? null : value;
}

function canonicalSnapshot(snapshot: TransactionSnapshot): string {
  return JSON.stringify(canonicalSnapshotValue(snapshot))!;
}

function canonicalSnapshotValue(
  snapshot: TransactionSnapshot,
): Record<string, unknown> {
  const canonical: Record<string, unknown> = {};
  for (const field of SNAPSHOT_FIELDS) {
    if (field === "subtransactions") {
      canonical[field] = snapshot.subtransactions.map((transaction) =>
        canonicalSnapshotValue(
          asRecord(
            transaction,
            "Subtransaction",
          ) as unknown as TransactionSnapshot,
        ),
      );
      continue;
    }

    const value = normalizeSnapshotValue(field, snapshot[field]);
    canonicalScalar(value);
    canonical[field] = value;
  }
  return canonical;
}

function canonicalScalar(value: unknown): string {
  if (
    value === null ||
    typeof value === "boolean" ||
    typeof value === "string"
  ) {
    return JSON.stringify(value);
  }
  if (typeof value === "number") {
    if (!Number.isFinite(value)) {
      throw new Error("JSON numbers must be finite");
    }
    return JSON.stringify(value);
  }
  throw new Error("Transaction snapshot values must be JSON scalars");
}
