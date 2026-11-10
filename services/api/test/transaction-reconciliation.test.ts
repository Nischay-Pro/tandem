import { describe, expect, it } from "vitest";
import type { JsonValue } from "../src/write-journal/contracts.js";
import {
  MAX_TRANSACTION_AMOUNT,
  MIN_TRANSACTION_DATE,
  createTransactionRevision,
  parseTransactionCreateInput,
  parseTransactionPatch,
  parseTransactionSnapshot,
  planTransactionDeletion,
  planTransactionUpdate,
  snapshotActualTransaction,
  type TransactionSnapshot,
} from "../src/actual/transaction-reconciliation.js";

function createSnapshot(
  overrides: Partial<TransactionSnapshot> = {},
): TransactionSnapshot {
  return {
    id: "actual-transaction-1",
    account: "actual-account-1",
    amount: -1500,
    date: "2026-10-31",
    payee: "actual-payee-1",
    category: "actual-category-1",
    notes: "Original note",
    cleared: false,
    reconciled: false,
    importedId: null,
    importedPayee: null,
    startingBalance: null,
    schedule: null,
    transferId: null,
    isParent: false,
    isChild: false,
    parentId: null,
    subtransactions: [],
    ...overrides,
  };
}

describe("transaction snapshots and merge planning", () => {
  it("normalizes an Actual transaction without optional values", () => {
    expect(
      snapshotActualTransaction({
        id: "transaction-1",
        account: "account-1",
        amount: -100,
        date: "2026-10-31",
      }),
    ).toEqual({
      id: "transaction-1",
      account: "account-1",
      amount: -100,
      date: "2026-10-31",
      payee: null,
      category: null,
      notes: null,
      cleared: null,
      reconciled: null,
      importedId: null,
      importedPayee: null,
      startingBalance: null,
      schedule: null,
      transferId: null,
      isParent: false,
      isChild: false,
      parentId: null,
      subtransactions: [],
    });
  });

  it("normalizes nullable fields, split markers, and Actual transfer identifiers", () => {
    expect(
      snapshotActualTransaction({
        id: "transaction-1",
        account: "account-1",
        amount: 100,
        date: "2026-10-31",
        payee: null,
        category: "category-1",
        notes: "note",
        cleared: true,
        reconciled: false,
        imported_id: "bank-id",
        imported_payee: "Bank payee",
        starting_balance_flag: false,
        schedule: "schedule-1",
        transfer_id: "transaction-2",
        is_parent: true,
        is_child: false,
        parent_id: "transaction-parent",
      }),
    ).toMatchObject({
      payee: null,
      category: "category-1",
      notes: "note",
      cleared: true,
      reconciled: false,
      importedId: "bank-id",
      importedPayee: "Bank payee",
      startingBalance: false,
      schedule: "schedule-1",
      transferId: "transaction-2",
      isParent: true,
      isChild: false,
      parentId: "transaction-parent",
    });
  });

  it("normalizes empty Actual identifier fields to null", () => {
    expect(
      snapshotActualTransaction({
        id: "transaction-1",
        account: "account-1",
        amount: 100,
        date: "2026-10-31",
        payee: "",
        category: "",
        imported_id: "",
        schedule: "",
        transfer_id: "",
      }),
    ).toMatchObject({
      payee: null,
      category: null,
      importedId: null,
      schedule: null,
      transferId: null,
    });
  });

  it("exposes Companion-created transactions by their stable imported ID", () => {
    const stableId = "tandem:8c88495a-65d9-4531-8392-93bff2c65374";
    expect(
      snapshotActualTransaction({
        id: "actual-random-id",
        imported_id: stableId,
        account: "account-1",
        amount: -100,
        date: "2026-10-31",
      }),
    ).toMatchObject({ id: stableId, importedId: null });
  });

  it("parses an exact transaction create payload", () => {
    expect(
      parseTransactionCreateInput({
        account: "account-1",
        amount: -100,
        date: "2026-10-31",
        payee: null,
        category: "category-1",
        notes: "",
        cleared: false,
      }),
    ).toEqual({
      account: "account-1",
      amount: -100,
      date: "2026-10-31",
      payee: null,
      category: "category-1",
      notes: "",
      cleared: false,
    });
    expect(() =>
      parseTransactionCreateInput({
        account: "account-1",
        amount: -100,
        date: "2026-10-31",
        payee: null,
        category: null,
        notes: "",
        cleared: false,
        importedId: "caller-controlled",
      }),
    ).toThrow(
      "transaction create input contains unsupported fields: importedId",
    );
  });

  it("bounds create and update amounts and transaction dates", () => {
    const validCreate = {
      account: "account-1",
      amount: MAX_TRANSACTION_AMOUNT,
      date: MIN_TRANSACTION_DATE,
      payee: null,
      category: null,
      notes: "Boundary values are accepted",
      cleared: false,
    };
    expect(parseTransactionCreateInput(validCreate).amount).toBe(
      MAX_TRANSACTION_AMOUNT,
    );
    expect(parseTransactionCreateInput(validCreate).date).toBe("1995-01-01");
    expect(() =>
      parseTransactionCreateInput({ ...validCreate, amount: 9007199254740991 }),
    ).toThrow(
      `amount must be between -${MAX_TRANSACTION_AMOUNT} and ${MAX_TRANSACTION_AMOUNT}`,
    );
    expect(() =>
      parseTransactionCreateInput({ ...validCreate, date: "1994-12-31" }),
    ).toThrow(`date must be on or after ${MIN_TRANSACTION_DATE}`);
    expect(() =>
      parseTransactionPatch({ amount: MAX_TRANSACTION_AMOUNT + 1 }),
    ).toThrow(
      `amount must be between -${MAX_TRANSACTION_AMOUNT} and ${MAX_TRANSACTION_AMOUNT}`,
    );
    expect(() => parseTransactionPatch({ date: "1994-12-31" })).toThrow(
      `date must be on or after ${MIN_TRANSACTION_DATE}`,
    );
  });

  it("rejects transaction create payloads with missing fields", () => {
    const input = {
      account: "account-1",
      amount: -100,
      date: "2026-10-31",
      payee: null,
      category: null,
      notes: "",
      cleared: false,
    };
    delete (input as Partial<typeof input>).notes;

    expect(() => parseTransactionCreateInput(input as never)).toThrow(
      "transaction create input is missing notes",
    );
  });

  it("preserves nulls in optional Actual boolean fields", () => {
    expect(
      snapshotActualTransaction({
        id: "transaction-1",
        account: "account-1",
        amount: 100,
        date: "2026-10-31",
        cleared: null,
        reconciled: null,
        starting_balance_flag: null,
        is_parent: null,
        is_child: null,
      }),
    ).toMatchObject({
      cleared: null,
      reconciled: null,
      startingBalance: null,
      isParent: false,
      isChild: false,
    });
  });

  it.each([
    ["payee", 42, "payee must be a non-empty string"],
    ["category", 42, "category must be a non-empty string"],
    ["imported_id", 42, "imported_id must be a non-empty string"],
    ["schedule", 42, "schedule must be a non-empty string"],
    ["transfer_id", 42, "transfer_id must be a non-empty string"],
    ["parent_id", 42, "parent_id must be a non-empty string"],
    ["payee", "  ", "payee must be a non-empty string"],
    ["notes", 42, "notes must be a string"],
    ["imported_payee", 42, "imported_payee must be a string"],
    ["cleared", "yes", "cleared must be a boolean"],
    ["reconciled", "yes", "reconciled must be a boolean"],
    ["starting_balance_flag", "yes", "starting_balance_flag must be a boolean"],
    ["is_parent", "yes", "is_parent must be a boolean"],
    ["is_child", "yes", "is_child must be a boolean"],
  ])("rejects malformed optional Actual field %s", (field, value, message) => {
    expect(() =>
      snapshotActualTransaction({
        id: "transaction-1",
        account: "account-1",
        amount: 100,
        date: "2026-10-31",
        [field]: value,
      }),
    ).toThrow(message);
  });

  it("treats null and empty notes as the same value during reconciliation", () => {
    const base = snapshotActualTransaction({
      id: "transaction-1",
      account: "account-1",
      amount: -100,
      date: "2026-10-31",
      notes: null,
    });
    const remote = snapshotActualTransaction({
      id: "transaction-1",
      account: "account-1",
      amount: -100,
      date: "2026-10-31",
      notes: "",
    });

    expect(base.notes).toBeNull();
    expect(remote.notes).toBeNull();
    expect(createTransactionRevision(base)).toBe(
      createTransactionRevision(remote),
    );
    expect(createTransactionRevision(createSnapshot({ notes: "" }))).toBe(
      createTransactionRevision(createSnapshot({ notes: null })),
    );
    expect(planTransactionUpdate(base, remote, { notes: "" })).toEqual({
      kind: "unchanged",
    });
    expect(
      planTransactionUpdate(
        createSnapshot({ notes: null }),
        createSnapshot({ notes: "Remote note" }),
        { notes: "" },
      ),
    ).toEqual({ kind: "unchanged" });
    const noteBase = createSnapshot({ notes: "Prior note" });
    expect(planTransactionUpdate(noteBase, noteBase, { notes: "" })).toEqual({
      kind: "apply",
      patch: { notes: "" },
    });
    const payeeBase = createSnapshot({ payee: "Payee" });
    expect(
      planTransactionUpdate(payeeBase, payeeBase, { payee: null }),
    ).toEqual({
      kind: "apply",
      patch: { payee: null },
    });
  });

  it("includes split children in snapshots, revisions, and delete conflict detection", () => {
    const parent = snapshotActualTransaction({
      id: "split-parent",
      account: "account-1",
      amount: -500,
      date: "2026-10-31",
      is_parent: true,
      subtransactions: [
        {
          id: "split-child",
          account: "account-1",
          amount: -500,
          date: "2026-10-31",
          is_child: true,
          parent_id: "split-parent",
        },
      ],
    });
    const changedParent = snapshotActualTransaction({
      id: "split-parent",
      account: "account-1",
      amount: -500,
      date: "2026-10-31",
      is_parent: true,
      subtransactions: [
        {
          id: "split-child",
          account: "account-1",
          amount: -400,
          date: "2026-10-31",
          is_child: true,
          parent_id: "split-parent",
        },
      ],
    });

    expect(parent.subtransactions).toHaveLength(1);
    expect(parseTransactionSnapshot(parent as unknown as JsonValue)).toEqual(
      parent,
    );
    expect(createTransactionRevision(changedParent)).not.toBe(
      createTransactionRevision(parent),
    );
    expect(planTransactionDeletion(parent, changedParent)).toMatchObject({
      kind: "conflict",
      conflictingFields: expect.arrayContaining(["subtransactions"]),
    });
  });

  it("validates Actual and journal split collections", () => {
    expect(() =>
      snapshotActualTransaction({
        id: "transaction-1",
        account: "account-1",
        amount: -100,
        date: "2026-10-31",
        notes: 10,
      }),
    ).toThrow("notes must be a string");
    expect(
      snapshotActualTransaction({
        id: "transaction-1",
        account: "account-1",
        amount: -100,
        date: "2026-10-31",
        subtransactions: null,
      }).subtransactions,
    ).toEqual([]);
    expect(() =>
      snapshotActualTransaction({
        id: "transaction-1",
        account: "account-1",
        amount: -100,
        date: "2026-10-31",
        subtransactions: {},
      }),
    ).toThrow("subtransactions must be an array");
    expect(() =>
      parseTransactionSnapshot({
        ...createSnapshot(),
        subtransactions: null,
      } as unknown as JsonValue),
    ).toThrow("baseSnapshot.subtransactions must be an array");
  });

  it("normalizes empty notes and preserves empty bank descriptions", () => {
    expect(
      snapshotActualTransaction({
        id: "transaction-1",
        account: "account-1",
        amount: 100,
        date: "2026-10-31",
        notes: "",
        imported_payee: "",
      }),
    ).toMatchObject({ notes: null, importedPayee: "" });
    expect(
      createTransactionRevision(createSnapshot({ importedPayee: "" })),
    ).not.toBe(
      createTransactionRevision(createSnapshot({ importedPayee: null })),
    );
    expect(
      parseTransactionSnapshot(
        createSnapshot({
          notes: "",
          importedPayee: "",
        }) as unknown as JsonValue,
      ),
    ).toMatchObject({ notes: null, importedPayee: "" });
  });

  it.each([null, [], "transaction"])(
    "rejects a non-object Actual transaction: %j",
    (value) => {
      expect(() => snapshotActualTransaction(value)).toThrow(
        "Actual transaction must be an object",
      );
    },
  );

  it.each([
    [{ id: "", account: "a", amount: 1, date: "2026-01-01" }, "id"],
    [{ id: "t", account: " ", amount: 1, date: "2026-01-01" }, "account"],
    [{ id: "t", account: "a", amount: 1.5, date: "2026-01-01" }, "amount"],
    [{ id: "t", account: "a", amount: 1, date: "not-a-date" }, "date"],
    [{ id: "t", account: "a", amount: 1, date: "x2026-01-01" }, "date"],
    [{ id: "t", account: "a", amount: 1, date: "2026-01-01x" }, "date"],
    [{ id: "t", account: "a", amount: 1, date: "2026-02-31" }, "date"],
    [
      { id: "t", account: "a", amount: 1, date: "2026-01-01", payee: 42 },
      "payee",
    ],
    [
      { id: "t", account: "a", amount: 1, date: "2026-01-01", cleared: "yes" },
      "cleared",
    ],
  ])("rejects invalid Actual transaction data %#", (value, errorField) => {
    expect(() => snapshotActualTransaction(value)).toThrow(errorField);
  });

  it("parses a complete durable base snapshot and rejects extra or missing fields", () => {
    const snapshot = createSnapshot();
    expect(parseTransactionSnapshot(snapshot as unknown as JsonValue)).toEqual(
      snapshot,
    );
    const legacySnapshot: Partial<TransactionSnapshot> = { ...snapshot };
    delete legacySnapshot.subtransactions;
    expect(
      parseTransactionSnapshot(legacySnapshot as unknown as JsonValue),
    ).toEqual(snapshot);
    expect(() =>
      parseTransactionSnapshot({
        ...snapshot,
        privateField: true,
        anotherPrivateField: true,
      } as unknown as JsonValue),
    ).toThrow(
      "baseSnapshot contains unsupported fields: privateField, anotherPrivateField",
    );
    const missingParentId: Partial<TransactionSnapshot> = { ...snapshot };
    delete missingParentId.parentId;
    expect(() =>
      parseTransactionSnapshot(missingParentId as unknown as JsonValue),
    ).toThrow("baseSnapshot is missing fields: parentId");
    const missingFields: Partial<TransactionSnapshot> = { ...snapshot };
    delete missingFields.parentId;
    delete missingFields.reconciled;
    expect(() =>
      parseTransactionSnapshot(missingFields as unknown as JsonValue),
    ).toThrow("baseSnapshot is missing fields: reconciled, parentId");
    expect(() =>
      parseTransactionSnapshot({
        ...snapshot,
        amount: 0.5,
      } as unknown as JsonValue),
    ).toThrow("baseSnapshot.amount must be a safe integer");
    expect(() =>
      parseTransactionSnapshot({
        ...snapshot,
        id: "",
      } as unknown as JsonValue),
    ).toThrow("baseSnapshot.id must be a non-empty string");
    expect(() => parseTransactionSnapshot(null)).toThrow(
      "baseSnapshot must be an object",
    );
  });

  it.each([
    ["account", "", "baseSnapshot.account must be a non-empty string"],
    ["payee", "", "baseSnapshot.payee must be a non-empty string"],
    ["category", "", "baseSnapshot.category must be a non-empty string"],
    ["notes", 1, "baseSnapshot.notes must be a string"],
    ["cleared", "yes", "baseSnapshot.cleared must be a boolean"],
    ["reconciled", "yes", "baseSnapshot.reconciled must be a boolean"],
    ["importedId", "", "baseSnapshot.importedId must be a non-empty string"],
    ["importedPayee", 1, "baseSnapshot.importedPayee must be a string"],
    ["startingBalance", 1, "baseSnapshot.startingBalance must be a boolean"],
    ["schedule", "", "baseSnapshot.schedule must be a non-empty string"],
    ["transferId", "", "baseSnapshot.transferId must be a non-empty string"],
    ["isParent", null, "baseSnapshot.isParent must be a boolean"],
    ["isChild", "no", "baseSnapshot.isChild must be a boolean"],
    ["parentId", "", "baseSnapshot.parentId must be a non-empty string"],
    ["subtransactions", {}, "baseSnapshot.subtransactions must be an array"],
  ])("rejects malformed journal snapshot field %s", (field, value, message) => {
    expect(() =>
      parseTransactionSnapshot({
        ...createSnapshot(),
        [field]: value,
      } as unknown as JsonValue),
    ).toThrow(message);
  });

  it("validates each editable transaction field", () => {
    expect(
      parseTransactionPatch({
        account: "account-2",
        amount: -1200,
        date: "2026-11-01",
        payee: null,
        category: "category-2",
        notes: "",
        cleared: true,
        reconciled: false,
      }),
    ).toEqual({
      account: "account-2",
      amount: -1200,
      date: "2026-11-01",
      payee: null,
      category: "category-2",
      notes: "",
      cleared: true,
      reconciled: false,
    });
    expect(() => parseTransactionPatch({})).toThrow(
      "transaction patch must contain at least one field",
    );
    expect(() => parseTransactionPatch({ transfer_id: "linked" })).toThrow(
      "transaction patch contains unsupported fields: transfer_id",
    );
    expect(() => parseTransactionPatch({ account: null })).toThrow(
      "account must be a non-empty string",
    );
    expect(() => parseTransactionPatch({ amount: 0.1 })).toThrow(
      "amount must be a safe integer",
    );
    expect(() => parseTransactionPatch({ date: "2026-02-31" })).toThrow(
      "date must use YYYY-MM-DD format",
    );
    expect(() => parseTransactionPatch({ date: "x2026-02-01" })).toThrow(
      "date must use YYYY-MM-DD format",
    );
    expect(() => parseTransactionPatch({ date: "2026-02-01x" })).toThrow(
      "date must use YYYY-MM-DD format",
    );
    expect(() => parseTransactionPatch({ payee: 1 })).toThrow(
      "payee must be a non-empty string",
    );
    expect(() => parseTransactionPatch({ category: false })).toThrow(
      "category must be a non-empty string",
    );
    expect(() => parseTransactionPatch({ account: "account\u0001" })).toThrow(
      "account must not contain control characters",
    );
    expect(() => parseTransactionPatch({ payee: "payee\u0001" })).toThrow(
      "payee must not contain control characters",
    );
    expect(() => parseTransactionPatch({ category: "category\u0001" })).toThrow(
      "category must not contain control characters",
    );
    expect(() => parseTransactionPatch({ notes: "note\u0000" })).toThrow(
      "notes must not contain control characters",
    );
    expect(() => parseTransactionPatch({ notes: "note\u0001" })).toThrow(
      "notes must not contain control characters",
    );
    expect(parseTransactionPatch({ notes: "line 1\nline 2\tend" })).toEqual({
      notes: "line 1\nline 2\tend",
    });
    expect(parseTransactionPatch({ notes: "" })).toEqual({ notes: "" });
    expect(() => parseTransactionPatch({ notes: 1 })).toThrow(
      "notes must be a string",
    );
    expect(() => parseTransactionPatch({ cleared: null })).toThrow(
      "cleared must be a boolean",
    );
    expect(() => parseTransactionPatch({ reconciled: 1 })).toThrow(
      "reconciled must be a boolean",
    );
    expect(() => parseTransactionPatch([] as unknown as JsonValue)).toThrow(
      "transaction patch must be an object",
    );
    expect(() =>
      parseTransactionPatch({ privateField: true, anotherPrivateField: true }),
    ).toThrow(
      "transaction patch contains unsupported fields: privateField, anotherPrivateField",
    );
  });

  it("computes stable revisions and rejects non-scalar corrupted snapshot fields", () => {
    const snapshot = createSnapshot();
    expect(createTransactionRevision(snapshot)).toBe(
      createTransactionRevision({ ...snapshot }),
    );
    expect(createTransactionRevision(snapshot)).not.toBe(
      createTransactionRevision({ ...snapshot, amount: -1501 }),
    );
    expect(() =>
      createTransactionRevision({ ...snapshot, amount: Number.NaN }),
    ).toThrow("JSON numbers must be finite");
    expect(() =>
      createTransactionRevision({
        ...snapshot,
        notes: [] as unknown as string,
      }),
    ).toThrow("Transaction snapshot values must be JSON scalars");
  });

  it("applies independent changes and treats already-applied fields as converged", () => {
    const base = createSnapshot();
    expect(
      planTransactionUpdate(base, base, { amount: -1200, notes: "Updated" }),
    ).toEqual({
      kind: "apply",
      patch: { amount: -1200, notes: "Updated" },
    });
    expect(
      planTransactionUpdate(base, createSnapshot({ amount: -1200 }), {
        amount: -1200,
        notes: "Updated",
      }),
    ).toEqual({ kind: "apply", patch: { notes: "Updated" } });
    expect(planTransactionUpdate(base, base, { amount: base.amount })).toEqual({
      kind: "unchanged",
    });
    expect(
      planTransactionUpdate(base, createSnapshot({ amount: -1200 }), {
        amount: -1200,
      }),
    ).toEqual({ kind: "unchanged" });
  });

  it("allows split parent changes when the group is unchanged", () => {
    const splitChild = createSnapshot({
      id: "split-child",
      amount: -1500,
      isChild: true,
      parentId: "split-parent",
    });
    const split = createSnapshot({
      id: "split-parent",
      isParent: true,
      subtransactions: [splitChild],
    });
    const transfer = createSnapshot({ transferId: "counterpart-transaction" });

    expect(planTransactionUpdate(split, split, { amount: -1200 })).toEqual({
      kind: "apply",
      patch: { amount: -1200 },
    });
    expect(planTransactionDeletion(split, split)).toEqual({
      kind: "delete",
    });
    expect(
      planTransactionUpdate(transfer, transfer, { amount: -1200 }),
    ).toEqual({
      kind: "apply",
      patch: { amount: -1200 },
    });
    expect(planTransactionDeletion(transfer, transfer)).toEqual({
      kind: "delete",
    });
  });

  it("blocks structured update and delete plans when either snapshot is structured", () => {
    const ordinary = createSnapshot();
    const structuredBases = [
      createSnapshot({ transferId: "peer" }),
      createSnapshot({ isParent: true }),
      createSnapshot({ isChild: true, parentId: "parent" }),
      createSnapshot({ parentId: "parent" }),
      createSnapshot({
        subtransactions: [
          createSnapshot({
            id: "child",
            isChild: true,
            parentId: "actual-transaction-1",
          }),
        ],
      }),
    ];

    for (const structured of structuredBases) {
      for (const [base, remote] of [
        [structured, ordinary],
        [ordinary, structured],
      ] as const) {
        if (structured.transferId !== null) {
          expect(
            planTransactionUpdate(base, remote, { amount: -1200 }).kind,
          ).toBe("conflict");
          expect(planTransactionDeletion(base, remote).kind).toBe("conflict");
          continue;
        }
        expect(
          planTransactionUpdate(base, remote, { amount: -1200 }).kind,
        ).toBe("conflict");
        expect(planTransactionDeletion(base, remote).kind).toBe("conflict");
      }
    }
  });

  it("allows stable split edits and reports changed structures for review", () => {
    const ordinary = createSnapshot();
    const transfer = createSnapshot({ transferId: "counterpart" });
    const child = createSnapshot({
      id: "child",
      isChild: true,
      parentId: "actual-transaction-1",
    });
    const split = createSnapshot({ isParent: true, subtransactions: [child] });

    expect(
      planTransactionUpdate(ordinary, transfer, { amount: -1200 }),
    ).toEqual({ kind: "conflict", conflictingFields: ["transferId"] });
    expect(planTransactionDeletion(ordinary, transfer)).toEqual({
      kind: "conflict",
      conflictingFields: ["transferId"],
    });
    expect(
      planTransactionUpdate(transfer, ordinary, { amount: -1200 }),
    ).toEqual({ kind: "conflict", conflictingFields: ["transferId"] });
    expect(
      planTransactionUpdate(transfer, transfer, { amount: -1200 }),
    ).toEqual({ kind: "apply", patch: { amount: -1200 } });
    expect(planTransactionUpdate(split, split, { amount: -1200 })).toEqual({
      kind: "apply",
      patch: { amount: -1200 },
    });
    expect(planTransactionDeletion(split, split)).toEqual({ kind: "delete" });
    const childTransaction = createSnapshot({
      isChild: true,
      parentId: "parent",
    });
    expect(
      planTransactionUpdate(childTransaction, childTransaction, {
        amount: -1200,
      }),
    ).toEqual({ kind: "apply", patch: { amount: -1200 } });
    const parentOnly = createSnapshot({ parentId: "parent" });
    expect(
      planTransactionUpdate(parentOnly, parentOnly, { amount: -1200 }),
    ).toEqual({ kind: "apply", patch: { amount: -1200 } });

    const splitAdded = createSnapshot({ isParent: true });
    expect(
      planTransactionUpdate(ordinary, splitAdded, { amount: -1200 }),
    ).toEqual({ kind: "conflict", conflictingFields: ["isParent"] });
    expect(
      planTransactionUpdate(splitAdded, ordinary, { amount: -1200 }),
    ).toEqual({ kind: "conflict", conflictingFields: ["isParent"] });
    expect(
      planTransactionUpdate(
        ordinary,
        createSnapshot({ subtransactions: [child] }),
        {
          amount: -1200,
        },
      ),
    ).toEqual({ kind: "conflict", conflictingFields: ["subtransactions"] });
    expect(
      planTransactionUpdate(
        createSnapshot({ subtransactions: [child] }),
        ordinary,
        {
          amount: -1200,
        },
      ),
    ).toEqual({ kind: "conflict", conflictingFields: ["subtransactions"] });
    expect(
      planTransactionDeletion(
        createSnapshot({ subtransactions: [child] }),
        ordinary,
      ),
    ).toEqual({ kind: "conflict", conflictingFields: ["subtransactions"] });
  });

  it("conflicts on parent category changes and marks derived split child fields unsupported", () => {
    const child = createSnapshot({
      id: "split-child",
      isChild: true,
      parentId: "split-parent",
    });
    const parent = createSnapshot({ isParent: true, subtransactions: [child] });
    expect(
      planTransactionUpdate(
        parent,
        createSnapshot({
          isParent: true,
          category: "remote-category",
          subtransactions: [child],
        }),
        { category: "local-category" },
      ),
    ).toEqual({ kind: "conflict", conflictingFields: ["category"] });

    const splitChild = createSnapshot({
      id: "split-child",
      isChild: true,
      parentId: "split-parent",
    });
    const childBase = createSnapshot({
      id: "split-child",
      isChild: true,
      parentId: "split-parent",
    });
    const cases = [
      {
        field: "account",
        local: "local-account",
        remote: "remote-account",
      },
      { field: "date", local: "2026-11-01", remote: "2026-11-02" },
      { field: "cleared", local: true, remote: null },
      { field: "reconciled", local: true, remote: null },
    ] as const;

    for (const scenario of cases) {
      const local = { [scenario.field]: scenario.local };
      const remote = createSnapshot({
        ...splitChild,
        [scenario.field]: scenario.remote,
      });
      expect(planTransactionUpdate(childBase, remote, local)).toEqual({
        kind: "unsupported",
        unsupportedFields: [scenario.field],
      });
    }

    expect(
      planTransactionUpdate(childBase, childBase, { date: childBase.date }),
    ).toEqual({ kind: "unchanged" });
  });

  it("reports overlapping same-field edits and does not apply the safe subset", () => {
    const base = createSnapshot();
    expect(
      planTransactionUpdate(
        base,
        createSnapshot({ amount: -1800, notes: "Remote note" }),
        { amount: -1200, notes: "Local note" },
      ),
    ).toEqual({
      kind: "conflict",
      conflictingFields: ["amount", "notes"],
    });
    expect(() =>
      planTransactionUpdate(base, createSnapshot({ id: "different" }), {
        amount: -1200,
      }),
    ).toThrow("Transaction snapshot IDs do not match");
  });

  it("detects deletion versus any changed managed field", () => {
    const base = createSnapshot();
    expect(planTransactionDeletion(base, base)).toEqual({ kind: "delete" });
    expect(
      planTransactionDeletion(
        createSnapshot({ notes: null }),
        createSnapshot({ notes: "" }),
      ),
    ).toEqual({ kind: "delete" });
    expect(
      planTransactionDeletion(base, createSnapshot({ reconciled: true })),
    ).toEqual({
      kind: "conflict",
      conflictingFields: ["reconciled"],
    });
    expect(() =>
      planTransactionDeletion(base, createSnapshot({ id: "different" })),
    ).toThrow("Transaction snapshot IDs do not match");
  });
});
