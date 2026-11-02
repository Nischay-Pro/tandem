import { describe, expect, it, vi } from "vitest";
import { BudgetReadApi } from "../src/budget-projection/budget-read-api.js";
import type {
  BudgetProjectionChangePage,
  BudgetProjectionSnapshot,
  BudgetProjectionStore,
  BudgetSnapshotReader,
} from "../src/budget-projection/contracts.js";
import type { BudgetLockPort } from "../src/budget-projection/contracts.js";

const snapshot: BudgetProjectionSnapshot = {
  budgetId: "budget-1",
  generation: "cefb5a89-38b5-4dc9-a9a3-10db4478b2e2",
  cursor: "4",
  records: [],
};

const page: BudgetProjectionChangePage = {
  budgetId: "budget-1",
  generation: snapshot.generation,
  currentCursor: "4",
  throughCursor: "4",
  nextCursor: "4",
  hasMore: false,
  resetRequired: false,
  changes: [],
};

function createApi(
  overrides: {
    refreshPage?: BudgetProjectionChangePage | undefined;
    readPage?: BudgetProjectionChangePage | undefined;
  } = {},
) {
  const events: string[] = [];
  const lock = vi.fn(async <T>(budgetId: string, work: () => Promise<T>) => {
    events.push(`lock:${budgetId}`);
    return work();
  });
  const reader = {
    read: vi.fn(async () => {
      events.push("read");
      return snapshot.records;
    }),
  } as BudgetSnapshotReader;
  const applySnapshot = vi.fn(async () => {
    events.push("apply");
    return snapshot;
  });
  const queuedResults: Array<BudgetProjectionChangePage | undefined> = [];
  if (Object.hasOwn(overrides, "refreshPage")) {
    queuedResults.push(overrides.refreshPage);
  }
  const readChanges = vi.fn(async () => {
    events.push("changes");
    if (queuedResults.length > 0) {
      return queuedResults.shift();
    }
    return Object.hasOwn(overrides, "readPage") ? overrides.readPage : page;
  });
  const store = {
    applySnapshot,
    readChanges,
  } as unknown as BudgetProjectionStore;
  const api = new BudgetReadApi(
    { withBudgetLock: lock } as BudgetLockPort,
    reader,
    store,
    "feed-epoch-1",
  );
  return { api, lock, reader, applySnapshot, readChanges, events };
}

describe("budget read API", () => {
  it("exposes the Actual budget catalog reader when configured", async () => {
    const fake = createApi();
    const budgets = [
      { budgetId: "budget-1", name: "Household", isEncrypted: false },
    ];
    const listBudgets = vi.fn(async () => budgets);
    const api = new BudgetReadApi(
      { withBudgetLock: fake.lock } as BudgetLockPort,
      fake.reader,
      {
        applySnapshot: fake.applySnapshot,
        readChanges: fake.readChanges,
      } as unknown as BudgetProjectionStore,
      "feed-epoch-1",
      listBudgets,
    );

    await expect(api.listBudgets?.()).resolves.toBe(budgets);
    expect(listBudgets).toHaveBeenCalledOnce();
  });

  it("refreshes Actual and persists a complete snapshot under the budget lock", async () => {
    const fake = createApi();
    await expect(
      fake.api.getSnapshot("budget-1", "encrypted-password"),
    ).resolves.toBe(snapshot);
    expect(fake.events).toEqual(["lock:budget-1", "read", "apply"]);
    expect(fake.reader.read).toHaveBeenCalledWith(
      "budget-1",
      "encrypted-password",
    );
    expect(fake.applySnapshot).toHaveBeenCalledWith(
      "budget-1",
      "feed-epoch-1",
      snapshot.records,
    );
  });

  it("refreshes before beginning a new change page and passes its encryption password", async () => {
    const fake = createApi();
    await expect(
      fake.api.getChanges({
        budgetId: "budget-1",
        generation: snapshot.generation,
        cursor: "2",
        limit: 25,
        encryptionPassword: "encrypted-password",
      }),
    ).resolves.toBe(page);
    expect(fake.events).toEqual(["lock:budget-1", "read", "apply", "changes"]);
    expect(fake.readChanges).toHaveBeenCalledWith(
      expect.objectContaining({
        budgetId: "budget-1",
        generation: snapshot.generation,
        cursor: "2",
        limit: 25,
      }),
    );
  });

  it("pages through the fixed high-water mark without refreshing Actual again", async () => {
    const fake = createApi();
    const request = {
      budgetId: "budget-1",
      generation: snapshot.generation,
      cursor: "2",
      throughCursor: "4",
      limit: 1,
    };
    await expect(fake.api.getChanges(request)).resolves.toBe(page);
    expect(fake.events).toEqual(["lock:budget-1", "changes"]);
    expect(fake.reader.read).not.toHaveBeenCalled();
  });

  it("refreshes when a paged request refers to a budget without stored state", async () => {
    const fake = createApi({ refreshPage: undefined });
    const request = {
      budgetId: "budget-1",
      generation: snapshot.generation,
      cursor: "0",
      throughCursor: "0",
      limit: 10,
    };
    await expect(fake.api.getChanges(request)).resolves.toBe(page);
    expect(fake.events).toEqual([
      "lock:budget-1",
      "changes",
      "read",
      "apply",
      "changes",
    ]);
    expect(fake.readChanges).toHaveBeenCalledTimes(2);
  });

  it("fails closed if refreshing still does not create feed state", async () => {
    const fake = createApi({ readPage: undefined, refreshPage: undefined });
    await expect(
      fake.api.getChanges({
        budgetId: "budget-1",
        generation: snapshot.generation,
        cursor: "0",
        throughCursor: "0",
        limit: 10,
      }),
    ).rejects.toThrow("Budget change feed state was not created");
  });

  it("rejects unsafe feed epoch configuration", () => {
    const fake = createApi();
    expect(
      () =>
        new BudgetReadApi(
          { withBudgetLock: fake.lock } as unknown as BudgetLockPort,
          fake.reader,
          {
            applySnapshot: fake.applySnapshot,
            readChanges: fake.readChanges,
          } as unknown as BudgetProjectionStore,
          "bad epoch",
        ),
    ).toThrow("Change feed epoch is invalid");
  });
});
