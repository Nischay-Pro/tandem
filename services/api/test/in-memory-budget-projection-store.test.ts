import { describe, expect, it } from "vitest";
import { BudgetProjectionInputError } from "../src/budget-projection/contracts.js";
import { InMemoryBudgetProjectionStore } from "../src/budget-projection/in-memory-budget-projection-store.js";
import type {
  BudgetEntityType,
  BudgetProjectionRecord,
} from "../src/budget-projection/contracts.js";

const REVISION_A = "a".repeat(64);
const REVISION_B = "b".repeat(64);

function record(
  type: BudgetEntityType = "transaction",
  id = "record-1",
  revision = REVISION_A,
): BudgetProjectionRecord {
  return { type, id, revision, body: { id } };
}

describe("InMemoryBudgetProjectionStore", () => {
  it("serves stable bounded snapshot pages in record-key order", async () => {
    const store = new InMemoryBudgetProjectionStore();
    expect(await store.isSnapshotFresh("budget-a", "epoch", 1_000)).toBe(false);
    expect(await store.readSnapshot("budget-a")).toBeUndefined();
    expect(
      await store.readSnapshotPage({
        budgetId: "budget-a",
        generation: "11111111-1111-4111-8111-111111111111",
        cursor: "0",
        limit: 1,
      }),
    ).toBeUndefined();
    expect(
      await store.readChanges({
        budgetId: "budget-a",
        generation: "11111111-1111-4111-8111-111111111111",
        cursor: "0",
        limit: 10,
      }),
    ).toBeUndefined();

    const snapshot = await store.applySnapshot("budget-a", "epoch", [
      record("transaction", "txn-2"),
      record("category", "cat-b"),
      record("account", "acct-a"),
      record("category", "cat-a"),
    ]);
    expect(snapshot.cursor).toBe("0");
    expect(await store.readSnapshot("budget-a")).toMatchObject({
      generation: snapshot.generation,
      cursor: "0",
    });
    expect(await store.isSnapshotFresh("budget-a", "epoch", 1_000)).toBe(true);

    const firstPage = await store.readSnapshotPage({
      budgetId: "budget-a",
      generation: snapshot.generation,
      cursor: "0",
      limit: 2,
    });
    expect(firstPage).toMatchObject({
      hasMore: true,
      resetRequired: false,
      nextAfterType: "category",
      nextAfterId: "cat-a",
    });
    expect(firstPage?.records.map(({ type, id }) => `${type}:${id}`)).toEqual([
      "account:acct-a",
      "category:cat-a",
    ]);

    const secondPage = await store.readSnapshotPage({
      budgetId: "budget-a",
      generation: snapshot.generation,
      cursor: "0",
      afterType: firstPage!.nextAfterType!,
      afterId: firstPage!.nextAfterId!,
      limit: 2,
    });
    expect(secondPage).toMatchObject({
      hasMore: false,
      resetRequired: false,
      nextAfterType: null,
      nextAfterId: null,
    });
    expect(secondPage?.records.map(({ type, id }) => `${type}:${id}`)).toEqual([
      "category:cat-b",
      "transaction:txn-2",
    ]);
  });

  it("keeps the generation for identical snapshots and resets after a change", async () => {
    const store = new InMemoryBudgetProjectionStore();
    const original = await store.applySnapshot("budget-a", "epoch", [record()]);
    const repeated = await store.applySnapshot("budget-a", "epoch", [record()]);
    expect(repeated.generation).toBe(original.generation);

    const changed = await store.applySnapshot("budget-a", "epoch", [
      record("transaction", "record-1", REVISION_B),
    ]);
    expect(changed.generation).not.toBe(original.generation);
    expect(
      await store.readChanges({
        budgetId: "budget-a",
        generation: original.generation,
        cursor: "0",
        limit: 10,
      }),
    ).toMatchObject({
      generation: changed.generation,
      currentCursor: "0",
      resetRequired: true,
      changes: [],
    });
    expect(
      await store.readSnapshotPage({
        budgetId: "budget-a",
        generation: original.generation,
        cursor: "0",
        limit: 10,
      }),
    ).toMatchObject({ generation: changed.generation, resetRequired: true });
  });

  it("returns an empty change page for the current snapshot checkpoint", async () => {
    const store = new InMemoryBudgetProjectionStore();
    const snapshot = await store.applySnapshot("budget-a", "epoch", []);
    expect(
      await store.readChanges({
        budgetId: "budget-a",
        generation: snapshot.generation,
        cursor: "0",
        limit: 10,
      }),
    ).toMatchObject({
      generation: snapshot.generation,
      currentCursor: "0",
      throughCursor: "0",
      nextCursor: "0",
      hasMore: false,
      resetRequired: false,
      changes: [],
    });
    expect(
      await store.readSnapshotPage({
        budgetId: "budget-a",
        generation: snapshot.generation,
        cursor: "1",
        limit: 10,
      }),
    ).toMatchObject({ resetRequired: true, records: [] });
  });

  it("invalidates snapshots when the feed epoch changes and bounds cached budgets", async () => {
    const store = new InMemoryBudgetProjectionStore(1);
    const original = await store.applySnapshot("budget-a", "epoch-a", [
      record(),
    ]);
    const newEpoch = await store.applySnapshot("budget-a", "epoch-b", [
      record(),
    ]);
    expect(newEpoch.generation).not.toBe(original.generation);

    await store.applySnapshot("budget-b", "epoch-b", []);
    expect(await store.readSnapshot("budget-a")).toBeUndefined();
    expect(await store.isSnapshotFresh("budget-a", "epoch-b", 1_000)).toBe(
      false,
    );
    expect(
      await store.readChanges({
        budgetId: "budget-a",
        generation: newEpoch.generation,
        cursor: "0",
        limit: 10,
      }),
    ).toBeUndefined();
  });

  it.each([0, Number.NaN, Number.MAX_SAFE_INTEGER + 1])(
    "rejects invalid snapshot freshness limits (%s)",
    async (maxAgeMs) => {
      const store = new InMemoryBudgetProjectionStore();
      await expect(
        store.isSnapshotFresh("budget-a", "epoch", maxAgeMs),
      ).rejects.toThrow("maxAgeMs must be a positive integer");
    },
  );

  it("rejects invalid constructor limits, snapshots, cursors and pages", async () => {
    expect(() => new InMemoryBudgetProjectionStore(0)).toThrow(
      "Cached budget count must be a positive integer",
    );
    const store = new InMemoryBudgetProjectionStore();
    await expect(store.applySnapshot("", "epoch", [])).rejects.toThrow(
      BudgetProjectionInputError,
    );
    await expect(store.applySnapshot("budget-a", "", [])).rejects.toThrow(
      BudgetProjectionInputError,
    );
    await expect(
      store.applySnapshot("budget-a", "epoch", [
        record("transaction", "same"),
        record("transaction", "same"),
      ]),
    ).rejects.toThrow("snapshot records must have unique type and id pairs");
    await expect(
      store.applySnapshot("budget-a", "epoch", [
        { ...record(), type: "unknown" as BudgetEntityType },
      ]),
    ).rejects.toThrow("snapshot record is invalid");
    await expect(
      store.applySnapshot("budget-a", "epoch", [
        { ...record(), body: [] as unknown as BudgetProjectionRecord["body"] },
      ]),
    ).rejects.toThrow("snapshot record is invalid");

    const snapshot = await store.applySnapshot("budget-a", "epoch", [record()]);
    const valid = {
      budgetId: "budget-a",
      generation: snapshot.generation,
      cursor: "0",
      limit: 1,
    };
    await expect(
      store.readSnapshotPage({ ...valid, generation: "invalid" }),
    ).rejects.toThrow("generation is invalid");
    await expect(
      store.readSnapshotPage({ ...valid, cursor: "01" }),
    ).rejects.toThrow("snapshot cursor is invalid");
    await expect(
      store.readSnapshotPage({ ...valid, cursor: "9007199254740992" }),
    ).rejects.toThrow("snapshot cursor is invalid");
    await expect(
      store.readSnapshotPage({ ...valid, limit: MAX_PAGE_SIZE + 1 }),
    ).rejects.toThrow(`limit must be between 1 and ${MAX_PAGE_SIZE}`);
    await expect(
      store.readSnapshotPage({ ...valid, afterType: "category" }),
    ).rejects.toThrow("afterType and afterId must be provided together");
    await expect(
      store.readSnapshotPage({
        ...valid,
        afterType: "unknown" as BudgetEntityType,
        afterId: "cat-1",
      }),
    ).rejects.toThrow("afterType is invalid");
    await expect(
      store.readSnapshotPage({
        ...valid,
        afterType: "category",
        afterId: "",
      }),
    ).rejects.toThrow("afterId is invalid");
  });

  it("rejects malformed change checkpoints and bounds", async () => {
    const store = new InMemoryBudgetProjectionStore();
    const snapshot = await store.applySnapshot("budget-a", "epoch", []);
    const valid = {
      budgetId: "budget-a",
      generation: snapshot.generation,
      cursor: "0",
      limit: 10,
    };
    await expect(store.readChanges({ ...valid, cursor: "-1" })).rejects.toThrow(
      "change cursor is invalid",
    );
    await expect(store.readChanges({ ...valid, limit: 0 })).rejects.toThrow(
      "limit must be a positive integer",
    );
    await expect(
      store.readChanges({ ...valid, throughCursor: "not-a-cursor" }),
    ).rejects.toThrow("through cursor is invalid");
    await expect(
      store.readChanges({ ...valid, cursor: "2", throughCursor: "1" }),
    ).rejects.toThrow("throughCursor must be greater than or equal to cursor");
    expect(
      await store.readChanges({ ...valid, throughCursor: "1" }),
    ).toMatchObject({ resetRequired: true, throughCursor: "0" });
  });
});

const MAX_PAGE_SIZE = 2_000;
