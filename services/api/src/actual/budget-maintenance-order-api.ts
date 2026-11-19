import {
  BudgetMaintenanceInputError,
  type BudgetMaintenanceApiDependencies,
  type BudgetMaintenanceApiPort,
  writeOptions,
} from "./budget-maintenance-contracts.js";
import type { ActualInternalApiPort } from "./actual-session.js";
import {
  planOrderMoves,
  rebaseOrder,
  type OrderMove,
} from "./budget-order-merge.js";

interface OrderedRow {
  id: string;
  isIncome?: boolean;
}

/**
 * Batched reorders. Each operation merges the device's intended order into
 * Actual's live order (see `rebaseOrder`), applies the fewest moves, and
 * confirms the result by reading Actual's order back.
 */
export interface BudgetOrderChange {
  baseOrder: readonly string[];
  order: readonly string[];
}

export interface BudgetOrderBatch {
  categoryGroups?: BudgetOrderChange | undefined;
  categories?:
    ReadonlyArray<BudgetOrderChange & { groupId: string }> | undefined;
  accounts?: BudgetOrderChange | undefined;
}

export interface BudgetOrderBatchResult {
  categoryGroups?: string[];
  categories?: Array<{ groupId: string; order: string[] }>;
  accounts?: string[];
}

/**
 * Batched reorders. All lists in a batch are applied under one budget lock and
 * saved with one Actual sync. Each list merges the device's intended order into
 * Actual's live order (see `rebaseOrder`), applies the fewest moves, and is
 * confirmed by reading Actual's order back.
 */
export function createOrderOperations({
  locks,
  session,
  client,
}: BudgetMaintenanceApiDependencies): Pick<
  BudgetMaintenanceApiPort,
  "setOrders"
> {
  const requireInternal = (): ActualInternalApiPort => {
    const internal = session.internal;
    if (!internal) {
      throw new BudgetMaintenanceInputError(
        "The Tandem Actual connection cannot issue ordering commands",
      );
    }
    return internal;
  };

  const readCategories = async (groupId: string): Promise<string[]> =>
    readOrderedIds(
      client,
      client
        .q("categories")
        .filter({ group: groupId })
        .select(["id", "sort_order"])
        .orderBy(["sort_order", "id"]),
    ).then((rows) => rows.map((row) => row.id));

  const readGroups = async (): Promise<OrderedRow[]> =>
    readOrderedIds(
      client,
      client
        .q("category_groups")
        .select(["id", "is_income", "sort_order"])
        .orderBy(["sort_order", "id"]),
    );

  const applyGroupOrder = async (
    internal: ActualInternalApiPort,
    change: BudgetOrderChange,
  ): Promise<string[]> => {
    const groups = await readGroups();
    const isIncome = new Map(
      groups.map((group) => [group.id, group.isIncome === true]),
    );
    // Actual always lists income groups after expense groups, so each section
    // is merged and moved on its own.
    const targets: string[][] = [];
    for (const income of [false, true]) {
      const inSection = (id: string) => isIncome.get(id) === income;
      const live = groups
        .filter((group) => (group.isIncome === true) === income)
        .map((group) => group.id);
      // IDs for deleted groups are dropped from both lists alike.
      const target = rebaseOrder(
        change.baseOrder.filter(inSection),
        change.order.filter(inSection),
        live,
      );
      await applyMoves(planOrderMoves(live, target), (move) =>
        internal.send("category-group-move", {
          id: move.id,
          targetId: move.beforeId,
        }),
      );
      targets.push(target);
    }
    const after = await readGroups();
    for (const [index, income] of [false, true].entries()) {
      confirmOrder(
        after
          .filter((group) => (group.isIncome === true) === income)
          .map((group) => group.id),
        targets[index]!,
      );
    }
    return targets.flat();
  };

  const applyCategoryOrder = async (
    internal: ActualInternalApiPort,
    groupIds: ReadonlySet<string>,
    change: BudgetOrderChange & { groupId: string },
  ): Promise<string[]> => {
    if (!groupIds.has(change.groupId)) {
      throw new BudgetMaintenanceInputError(
        "Category group does not exist in this budget",
      );
    }
    const live = await readCategories(change.groupId);
    const target = rebaseOrder(change.baseOrder, change.order, live);
    await applyMoves(planOrderMoves(live, target), (move) =>
      internal.send("category-move", {
        id: move.id,
        groupId: change.groupId,
        targetId: move.beforeId,
      }),
    );
    confirmOrder(await readCategories(change.groupId), target);
    return target;
  };

  const applyAccountOrder = async (
    internal: ActualInternalApiPort,
    change: BudgetOrderChange,
  ): Promise<string[]> => {
    const accounts = await client.getAccounts();
    const byId = new Map(accounts.map((account) => [account.id, account]));
    const saved: string[] = [];
    // Actual keeps a separate order per section, so each section is merged on its own.
    for (const section of ACCOUNT_SECTIONS) {
      const inSection = (id: string) => {
        const account = byId.get(id);
        return account !== undefined && accountSection(account) === section;
      };
      // IDs for deleted accounts are dropped from both lists alike.
      const base = change.baseOrder.filter(inSection);
      const order = change.order.filter(inSection);
      if (order.length === 0) continue;

      // Actual orders open accounts together with closed accounts of the same
      // budget type, so moves are planned over that wider list and the closed
      // accounts keep their slots.
      const moveScope = accounts
        .filter((account) => inAccountMoveScope(account, section))
        .map((account) => account.id);
      const live = moveScope.filter(inSection);
      const sectionTarget = rebaseOrder(base, order, live);
      const remaining = [...sectionTarget];
      const scopeTarget = moveScope.map((id) =>
        inSection(id) ? remaining.shift()! : id,
      );
      // Accounts outside the section live in a different Actual move list, so
      // they are never moved here, only used as positions.
      const pinned = new Set(moveScope.filter((id) => !inSection(id)));
      await applyMoves(planOrderMoves(moveScope, scopeTarget, pinned), (move) =>
        internal.send("account-move", { id: move.id, targetId: move.beforeId }),
      );
      saved.push(...sectionTarget);
    }
    const after = (await client.getAccounts()).map((account) => account.id);
    for (const section of ACCOUNT_SECTIONS) {
      const expected = saved.filter(
        (id) => accountSection(byId.get(id)!) === section,
      );
      if (expected.length === 0) continue;
      confirmOrder(
        after.filter((id) => {
          const account = byId.get(id);
          return account !== undefined && accountSection(account) === section;
        }),
        expected,
      );
    }
    return saved;
  };

  return {
    setOrders(budgetId, batch, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const internal = requireInternal();
            const result: BudgetOrderBatchResult = {};
            // Groups first, so category lists refer to groups that exist.
            if (batch.categoryGroups) {
              result.categoryGroups = await applyGroupOrder(
                internal,
                batch.categoryGroups,
              );
            }
            if (batch.categories && batch.categories.length > 0) {
              const groupIds = new Set(
                (await readGroups()).map((group) => group.id),
              );
              result.categories = [];
              for (const change of batch.categories) {
                result.categories.push({
                  groupId: change.groupId,
                  order: await applyCategoryOrder(internal, groupIds, change),
                });
              }
            }
            if (batch.accounts) {
              result.accounts = await applyAccountOrder(
                internal,
                batch.accounts,
              );
            }
            return result;
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
  };
}

const ACCOUNT_SECTIONS = ["on-budget", "off-budget", "closed"] as const;

type AccountLike = { closed?: boolean | null; offbudget?: boolean | null };

function accountSection(account: AccountLike): string {
  if (account.closed) return "closed";
  return account.offbudget ? "off-budget" : "on-budget";
}

/** Mirrors the list Actual's `account-move` reorders for an account in `section`. */
function inAccountMoveScope(account: AccountLike, section: string): boolean {
  if (section === "closed") return Boolean(account.closed);
  return Boolean(account.offbudget) === (section === "off-budget");
}

async function applyMoves(
  moves: readonly OrderMove[],
  send: (move: OrderMove) => Promise<unknown>,
): Promise<void> {
  for (const move of moves) await send(move);
}

function confirmOrder(
  actual: readonly string[],
  expected: readonly string[],
): void {
  if (
    actual.length !== expected.length ||
    actual.some((id, index) => id !== expected[index])
  ) {
    // Not an input error: Actual may have applied some moves, so the result is unknown.
    throw new Error("Actual did not save the requested order");
  }
}

async function readOrderedIds(
  client: BudgetMaintenanceApiDependencies["client"],
  query: unknown,
): Promise<OrderedRow[]> {
  const result = (await client.aqlQuery(query as never)) as {
    data?: unknown;
  } | null;
  const data = result?.data;
  if (!Array.isArray(data)) {
    throw new Error("Actual returned an invalid ordering query result");
  }
  return data.map((row: unknown) => {
    if (typeof (row as { id?: unknown } | null)?.id !== "string") {
      throw new Error("Actual returned an invalid ordering row");
    }
    const value = row as { id: string; is_income?: unknown };
    return {
      id: value.id,
      isIncome: value.is_income === true || value.is_income === 1,
    };
  });
}
