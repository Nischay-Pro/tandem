import { describe, expect, it, vi } from "vitest";
import {
  BudgetMaintenanceInputError,
  createBudgetMaintenanceApi,
  type ActualBudgetMaintenanceClient,
  type ActualBudgetMaintenanceSession,
} from "../src/actual/budget-maintenance-api.js";
import type { BudgetLockPort } from "../src/actual/transaction-reconciler.js";

interface Row {
  id: string;
  sort_order: number;
  group?: string;
  is_income?: boolean;
  offbudget?: boolean;
  closed?: boolean;
}

interface Query {
  table: "categories" | "category_groups";
  filter?: { group: string };
}

/** A small Actual stand-in whose move commands reorder rows like Actual's `shoveSortOrders`. */
function createFakeActual(state: {
  groups: Row[];
  categories: Row[];
  accounts: Row[];
}) {
  const sorted = (rows: Row[]) =>
    [...rows].sort(
      (a, b) => a.sort_order - b.sort_order || a.id.localeCompare(b.id),
    );
  const moveBefore = (rows: Row[], id: string, targetId: string | null) => {
    const order = sorted(rows)
      .map((row) => row.id)
      .filter((rowId) => rowId !== id);
    const index = targetId === null ? order.length : order.indexOf(targetId);
    if (index < 0) throw new Error(`Unknown target ${targetId}`);
    order.splice(index, 0, id);
    order.forEach((rowId, position) => {
      rows.find((row) => row.id === rowId)!.sort_order = (position + 1) * 1024;
    });
  };
  const send = vi.fn(
    async (
      command: string,
      args: { id: string; targetId: string | null; groupId?: string },
    ) => {
      if (command === "category-move") {
        moveBefore(
          state.categories.filter((row) => row.group === args.groupId),
          args.id,
          args.targetId,
        );
      } else if (command === "category-group-move") {
        moveBefore(state.groups, args.id, args.targetId);
      } else if (command === "account-move") {
        const account = state.accounts.find((row) => row.id === args.id)!;
        const scope = account.closed
          ? state.accounts.filter((row) => row.closed)
          : state.accounts.filter(
              (row) => Boolean(row.offbudget) === Boolean(account.offbudget),
            );
        moveBefore(scope, args.id, args.targetId);
      } else {
        throw new Error(`Unexpected command ${command}`);
      }
      return undefined;
    },
  );

  const query = (table: Query["table"]) => {
    const built: Query = { table };
    const builder = {
      filter(filter: { group: string }) {
        built.filter = filter;
        return builder;
      },
      select: () => builder,
      orderBy: () => built,
    };
    return builder;
  };
  const client = {
    q: vi.fn(query),
    aqlQuery: vi.fn(async (built: Query) => {
      const rows =
        built.table === "categories"
          ? state.categories.filter(
              (row) => !built.filter || row.group === built.filter.group,
            )
          : state.groups;
      return { data: sorted(rows) };
    }),
    getAccounts: vi.fn(async () => sorted(state.accounts)),
  } as unknown as ActualBudgetMaintenanceClient;
  const session = {
    internal: { send },
    runBudget: vi.fn(
      async (_syncId: string, operation: () => Promise<unknown>) => operation(),
    ),
  } as unknown as ActualBudgetMaintenanceSession;
  const locks: BudgetLockPort = {
    withBudgetLock: vi.fn(async (_budgetId, work) => work()),
  };
  const api = createBudgetMaintenanceApi(locks, session, client);
  const order = (rows: Row[]) => sorted(rows).map((row) => row.id);
  return { api, send, session, locks, order, client };
}

const rows = (ids: string[], extra: Partial<Row> = {}) =>
  ids.map((id, index) => ({ id, sort_order: (index + 1) * 1024, ...extra }));

describe("batched reorders against Actual", () => {
  it("applies groups, categories, and accounts under one lock and one Actual session", async () => {
    const state = {
      groups: [
        ...rows(["bills", "fashion", "needs"]),
        ...rows(["income"], { is_income: true }).map((row) => ({
          ...row,
          sort_order: 512,
        })),
      ],
      categories: rows(["beauty", "clothing", "shoes"], { group: "fashion" }),
      accounts: rows(["checking", "savings", "card"]),
    };
    const fake = createFakeActual(state);

    const result = await fake.api.setOrders("budget-1", {
      categoryGroups: {
        baseOrder: ["bills", "fashion", "needs", "income"],
        // Dropping Income among expense groups is kept in Actual's income section.
        order: ["fashion", "income", "bills", "needs"],
      },
      categories: [
        {
          groupId: "fashion",
          baseOrder: ["beauty", "clothing", "shoes"],
          order: ["shoes", "beauty", "clothing"],
        },
      ],
      accounts: {
        baseOrder: ["checking", "savings", "card"],
        order: ["card", "checking", "savings"],
      },
    });

    expect(result).toEqual({
      categoryGroups: ["fashion", "bills", "needs", "income"],
      categories: [
        { groupId: "fashion", order: ["shoes", "beauty", "clothing"] },
      ],
      accounts: ["card", "checking", "savings"],
    });
    expect(fake.order(state.categories)).toEqual([
      "shoes",
      "beauty",
      "clothing",
    ]);
    expect(fake.locks.withBudgetLock).toHaveBeenCalledOnce();
    expect(fake.session.runBudget).toHaveBeenCalledOnce();
    // One move per list: the fewest moves that reach each order.
    expect(fake.send).toHaveBeenCalledTimes(3);
  });

  it("keeps another device's reorder of items this device did not move", async () => {
    // Another device already moved Shoes to the top.
    const state = {
      groups: rows(["fashion"]),
      categories: rows(["shoes", "beauty", "clothing", "jewelry"], {
        group: "fashion",
      }),
      accounts: [],
    };
    const fake = createFakeActual(state);

    await fake.api.setOrders("budget-1", {
      categories: [
        {
          groupId: "fashion",
          baseOrder: ["beauty", "clothing", "jewelry", "shoes"],
          // This device moved Jewelry above Clothing.
          order: ["beauty", "jewelry", "clothing", "shoes"],
        },
      ],
    });

    expect(fake.order(state.categories)).toEqual([
      "shoes",
      "beauty",
      "jewelry",
      "clothing",
    ]);
  });

  it("keeps closed accounts in place when reordering open accounts of the same type", async () => {
    const state = {
      groups: [],
      categories: [],
      accounts: [
        { id: "checking", sort_order: 1024 },
        { id: "old-card", sort_order: 2048, closed: true },
        { id: "savings", sort_order: 3072 },
      ],
    };
    const fake = createFakeActual(state);

    await fake.api.setOrders("budget-1", {
      accounts: {
        baseOrder: ["checking", "savings"],
        order: ["savings", "checking"],
      },
    });

    expect(fake.order(state.accounts)).toEqual([
      "savings",
      "old-card",
      "checking",
    ]);
  });

  it("reorders each account section on its own", async () => {
    const state = {
      groups: [],
      categories: [],
      accounts: [
        { id: "checking", sort_order: 1024 },
        { id: "savings", sort_order: 2048 },
        { id: "brokerage", sort_order: 1024, offbudget: true },
        { id: "house", sort_order: 2048, offbudget: true },
      ],
    };
    const fake = createFakeActual(state);

    const result = await fake.api.setOrders("budget-1", {
      accounts: {
        baseOrder: ["checking", "savings", "brokerage", "house"],
        // A list that interleaves sections still keeps each section separate.
        order: ["house", "savings", "brokerage", "checking"],
      },
    });

    expect(result.accounts).toEqual([
      "savings",
      "checking",
      "house",
      "brokerage",
    ]);
    expect(fake.order(state.accounts.filter((row) => !row.offbudget))).toEqual([
      "savings",
      "checking",
    ]);
    expect(fake.order(state.accounts.filter((row) => row.offbudget))).toEqual([
      "house",
      "brokerage",
    ]);
  });

  it("rejects unknown category groups before moving anything", async () => {
    const state = { groups: rows(["fashion"]), categories: [], accounts: [] };
    const fake = createFakeActual(state);

    await expect(
      fake.api.setOrders("budget-1", {
        categories: [{ groupId: "missing", baseOrder: [], order: [] }],
      }),
    ).rejects.toThrow("Category group does not exist in this budget");
    expect(fake.send).not.toHaveBeenCalled();
  });

  it("reports an unknown outcome when Actual does not end up in the planned order", async () => {
    const state = {
      groups: rows(["fashion"]),
      categories: rows(["a", "b"], { group: "fashion" }),
      accounts: [],
    };
    const fake = createFakeActual(state);
    fake.send.mockImplementationOnce(async () => undefined); // Actual ignores the move.

    const failure = fake.api.setOrders("budget-1", {
      categories: [
        { groupId: "fashion", baseOrder: ["a", "b"], order: ["b", "a"] },
      ],
    });
    await expect(failure).rejects.toThrow(
      "Actual did not save the requested order",
    );
    await expect(failure).rejects.not.toBeInstanceOf(
      BudgetMaintenanceInputError,
    );
  });

  it("reorders closed accounts among themselves", async () => {
    const state = {
      groups: [],
      categories: [],
      accounts: [
        { id: "checking", sort_order: 1024 },
        { id: "old-card", sort_order: 2048, closed: true },
        { id: "old-loan", sort_order: 3072, closed: true, offbudget: true },
      ],
    };
    const fake = createFakeActual(state);

    const result = await fake.api.setOrders("budget-1", {
      accounts: {
        baseOrder: ["checking", "old-card", "old-loan"],
        order: ["checking", "old-loan", "old-card"],
      },
    });

    expect(result.accounts).toEqual(["checking", "old-loan", "old-card"]);
    expect(fake.order(state.accounts.filter((row) => row.closed))).toEqual([
      "old-loan",
      "old-card",
    ]);
  });

  it("rejects a list that is not a rearrangement of its starting order", async () => {
    const state = {
      groups: rows(["fashion"]),
      categories: rows(["a", "b"], { group: "fashion" }),
      accounts: [],
    };
    const fake = createFakeActual(state);

    await expect(
      fake.api.setOrders("budget-1", {
        categories: [
          { groupId: "fashion", baseOrder: ["a", "b"], order: ["a", "c"] },
        ],
      }),
    ).rejects.toThrow(BudgetMaintenanceInputError);
  });

  it("refuses to reorder without Actual's command bus", async () => {
    const fake = createFakeActual({
      groups: rows(["fashion"]),
      categories: [],
      accounts: [],
    });
    (fake.session as { internal: unknown }).internal = undefined;

    await expect(
      fake.api.setOrders("budget-1", {
        categoryGroups: { baseOrder: ["fashion"], order: ["fashion"] },
      }),
    ).rejects.toThrow(
      "The Tandem Actual connection cannot issue ordering commands",
    );
  });

  it("fails clearly when Actual returns malformed ordering data", async () => {
    const fake = createFakeActual({
      groups: rows(["fashion"]),
      categories: [],
      accounts: [],
    });
    const batch = {
      categoryGroups: { baseOrder: ["fashion"], order: ["fashion"] },
    };

    vi.mocked(fake.client.aqlQuery).mockResolvedValueOnce(null as never);
    await expect(fake.api.setOrders("budget-1", batch)).rejects.toThrow(
      "Actual returned an invalid ordering query result",
    );
    vi.mocked(fake.client.aqlQuery).mockResolvedValueOnce({
      data: [null],
    } as never);
    await expect(fake.api.setOrders("budget-1", batch)).rejects.toThrow(
      "Actual returned an invalid ordering row",
    );
  });
});
