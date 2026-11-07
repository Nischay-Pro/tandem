import { describe, expect, it, vi } from "vitest";
import {
  BudgetReadApi,
  DEFAULT_BUDGET_SNAPSHOT_FRESHNESS_MS,
  type BudgetSnapshotPageRequest,
} from "../src/budget-projection/budget-read-api.js";
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
    snapshotFresh?: boolean;
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
  const applySnapshotForRefresh = vi.fn(async () => {
    events.push("apply");
    return {
      budgetId: snapshot.budgetId,
      generation: snapshot.generation,
      cursor: snapshot.cursor,
    };
  });
  const readSnapshotPage = vi.fn(async () => ({
    budgetId: snapshot.budgetId,
    generation: snapshot.generation,
    cursor: snapshot.cursor,
    records: [],
    hasMore: false,
    resetRequired: false,
    nextAfterType: null,
    nextAfterId: null,
  }));
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
  const isSnapshotFresh = vi.fn(async () => overrides.snapshotFresh ?? false);
  const store = {
    isSnapshotFresh,
    applySnapshot,
    applySnapshotForRefresh,
    readSnapshotPage,
    readChanges,
  } as unknown as BudgetProjectionStore;
  const api = new BudgetReadApi(
    { withBudgetLock: lock } as BudgetLockPort,
    reader,
    store,
    "feed-epoch-1",
  );
  return {
    api,
    lock,
    reader,
    applySnapshot,
    applySnapshotForRefresh,
    readSnapshotPage,
    readChanges,
    isSnapshotFresh,
    events,
  };
}

describe("budget read API", () => {
  it("requires snapshot continuation fields to be complete", async () => {
    const fake = createApi();
    await expect(
      fake.api.getSnapshotPage({
        budgetId: "budget-1",
        generation: snapshot.generation,
        limit: 10,
      } as BudgetSnapshotPageRequest),
    ).rejects.toThrow("generation and cursor must be provided together");
    await expect(
      fake.api.getSnapshotPage({
        budgetId: "budget-1",
        generation: snapshot.generation,
        cursor: "4",
        afterType: "transaction",
        limit: 10,
      } as BudgetSnapshotPageRequest),
    ).rejects.toThrow("afterType and afterId must be provided together");
  });

  it("returns the stored snapshot when Actual sync reports no table changes", async () => {
    const fake = createApi();
    const displaySettings = {
      budgetType: "envelope" as const,
      currencyCode: "USD",
      currencySymbol: "$",
      currencyFractionDigits: 2,
      numberFormat: null,
      hideFraction: false,
      currencySymbolPosition: "before" as const,
      currencySpaceBetweenAmountAndSymbol: false,
    };
    const readWithSettings = vi.fn(async () => ({
      records: [],
      displaySettings,
      unchanged: true,
    }));
    const readSnapshot = vi.fn(async () => snapshot);
    const store = {
      applySnapshot: fake.applySnapshot,
      readSnapshot,
    } as unknown as BudgetProjectionStore;
    const api = new BudgetReadApi(
      { withBudgetLock: fake.lock } as BudgetLockPort,
      { read: vi.fn(), readWithSettings } as unknown as BudgetSnapshotReader,
      store,
      "feed-epoch-1",
    );

    await expect(api.getSnapshot("budget-1")).resolves.toMatchObject({
      generation: snapshot.generation,
      cursor: snapshot.cursor,
      displaySettings,
    });
    expect(readSnapshot).toHaveBeenCalledOnce();
    expect(fake.applySnapshot).not.toHaveBeenCalled();
  });

  it("supports stores without the refresh-only snapshot checkpoint method", async () => {
    const fake = createApi();
    const api = new BudgetReadApi(
      { withBudgetLock: fake.lock } as BudgetLockPort,
      fake.reader,
      {
        applySnapshot: fake.applySnapshot,
        readSnapshotPage: fake.readSnapshotPage,
      } as unknown as BudgetProjectionStore,
      "feed-epoch-1",
    );
    await expect(
      api.getSnapshotPage({ budgetId: "budget-1", limit: 10 }),
    ).resolves.toMatchObject({
      generation: snapshot.generation,
      cursor: snapshot.cursor,
    });
    expect(fake.applySnapshot).toHaveBeenCalledOnce();
  });

  it("fails clearly when a refresh does not create snapshot page state", async () => {
    const fake = createApi();
    const api = new BudgetReadApi(
      { withBudgetLock: fake.lock } as BudgetLockPort,
      fake.reader,
      {
        applySnapshotForRefresh: fake.applySnapshotForRefresh,
        readSnapshotPage: vi.fn(async () => undefined),
      } as unknown as BudgetProjectionStore,
      "feed-epoch-1",
    );
    await expect(
      api.getSnapshotPage({ budgetId: "budget-1", limit: 10 }),
    ).rejects.toThrow("Budget projection state was not created");
  });

  it("refreshes Actual once for the first snapshot page", async () => {
    const fake = createApi();
    await expect(
      fake.api.getSnapshotPage({ budgetId: "budget-1", limit: 2_000 }),
    ).resolves.toMatchObject({
      budgetId: "budget-1",
      generation: snapshot.generation,
      cursor: snapshot.cursor,
      displaySettings: null,
    });
    expect(fake.events).toEqual(["lock:budget-1", "read", "apply"]);
    expect(fake.readSnapshotPage).toHaveBeenCalledWith({
      budgetId: "budget-1",
      generation: snapshot.generation,
      cursor: snapshot.cursor,
      limit: 2_000,
    });
    expect(fake.applySnapshotForRefresh).toHaveBeenCalledOnce();
    expect(fake.applySnapshot).not.toHaveBeenCalled();
  });

  it("reads continuation pages without refreshing Actual", async () => {
    const fake = createApi();
    await fake.api.getSnapshotPage({
      budgetId: "budget-1",
      generation: snapshot.generation,
      cursor: "4",
      afterType: "transaction",
      afterId: "txn-1",
      limit: 500,
    });
    expect(fake.events).toEqual(["lock:budget-1"]);
    expect(fake.reader.read).not.toHaveBeenCalled();
    expect(fake.readSnapshotPage).toHaveBeenCalledWith({
      budgetId: "budget-1",
      generation: snapshot.generation,
      cursor: "4",
      afterType: "transaction",
      afterId: "txn-1",
      limit: 500,
    });
  });

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
        isSnapshotFresh: vi.fn(async () => false),
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
    ).resolves.toEqual({ ...snapshot, displaySettings: null });
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
    expect(fake.applySnapshotForRefresh).not.toHaveBeenCalled();
  });

  it("returns Actual display settings on snapshots and the first change page", async () => {
    const fake = createApi();
    const displaySettings = {
      currencyCode: "CAD",
      currencySymbol: "CA$",
      currencyFractionDigits: 2,
      numberFormat: "comma-dot",
      hideFraction: false,
      currencySymbolPosition: "before" as const,
      currencySpaceBetweenAmountAndSymbol: false,
    };
    const reader = {
      read: fake.reader.read,
      readWithSettings: vi.fn(async () => ({
        records: snapshot.records,
        displaySettings,
      })),
    } as unknown as BudgetSnapshotReader;
    const api = new BudgetReadApi(
      { withBudgetLock: fake.lock } as BudgetLockPort,
      reader,
      {
        isSnapshotFresh: vi.fn(async () => false),
        applySnapshot: fake.applySnapshot,
        readChanges: fake.readChanges,
      } as unknown as BudgetProjectionStore,
      "feed-epoch-1",
    );

    await expect(api.getSnapshot("budget-1")).resolves.toMatchObject({
      displaySettings,
    });
    await expect(
      api.getChanges({
        budgetId: "budget-1",
        generation: snapshot.generation,
        cursor: "2",
        limit: 25,
      }),
    ).resolves.toMatchObject({ displaySettings });
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
    ).resolves.toEqual({ ...page, displaySettings: null });
    expect(fake.events).toEqual(["lock:budget-1", "read", "apply", "changes"]);
    expect(fake.applySnapshotForRefresh).toHaveBeenCalledWith(
      "budget-1",
      "feed-epoch-1",
      snapshot.records,
    );
    expect(fake.applySnapshot).not.toHaveBeenCalled();
    expect(fake.isSnapshotFresh).toHaveBeenCalledWith(
      "budget-1",
      "feed-epoch-1",
      DEFAULT_BUDGET_SNAPSHOT_FRESHNESS_MS,
    );
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
    await expect(fake.api.getChanges(request)).resolves.toEqual({
      ...page,
      displaySettings: null,
    });
    expect(fake.events).toEqual(["lock:budget-1", "changes"]);
    expect(fake.reader.read).not.toHaveBeenCalled();
    expect(fake.isSnapshotFresh).not.toHaveBeenCalled();
  });

  it("serves a fresh projection without downloading or rereading Actual", async () => {
    const fake = createApi({ snapshotFresh: true });
    await expect(
      fake.api.getChanges({
        budgetId: "budget-1",
        generation: snapshot.generation,
        cursor: "2",
        limit: 25,
      }),
    ).resolves.toEqual({ ...page, displaySettings: null });
    expect(fake.isSnapshotFresh).toHaveBeenCalledOnce();
    expect(fake.reader.read).not.toHaveBeenCalled();
    expect(fake.applySnapshot).not.toHaveBeenCalled();
    expect(fake.readChanges).toHaveBeenCalledOnce();
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
    await expect(fake.api.getChanges(request)).resolves.toEqual({
      ...page,
      displaySettings: null,
    });
    expect(fake.events).toEqual([
      "lock:budget-1",
      "changes",
      "read",
      "apply",
      "changes",
    ]);
    expect(fake.applySnapshotForRefresh).toHaveBeenCalledOnce();
    expect(fake.applySnapshot).not.toHaveBeenCalled();
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
            isSnapshotFresh: vi.fn(async () => false),
            applySnapshot: fake.applySnapshot,
            readChanges: fake.readChanges,
          } as unknown as BudgetProjectionStore,
          "bad epoch",
        ),
    ).toThrow("Change feed epoch is invalid");
  });

  it("rejects an invalid snapshot freshness interval", () => {
    const fake = createApi();
    expect(
      () =>
        new BudgetReadApi(
          { withBudgetLock: fake.lock } as unknown as BudgetLockPort,
          fake.reader,
          {
            isSnapshotFresh: fake.isSnapshotFresh,
            applySnapshot: fake.applySnapshot,
            readChanges: fake.readChanges,
          } as unknown as BudgetProjectionStore,
          "feed-epoch-1",
          undefined,
          0,
        ),
    ).toThrow("Snapshot freshness interval must be a positive integer");
  });
});
