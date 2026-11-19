import { BudgetMaintenanceInputError } from "./budget-maintenance-contracts.js";

/**
 * Order merging for batched reorders.
 *
 * A device sends the order it started from (`base`) and the order the user
 * dropped (`desired`). Applying `desired` verbatim would undo moves another
 * device made in the meantime, so the server replays only the user's intent:
 *
 * 1. The items the user moved are the fewest items whose removal leaves
 *    `base` and `desired` in the same relative order (the complement of their
 *    longest common subsequence).
 * 2. Those items are lifted out of the live order and reinserted in front of
 *    the item that follows them in `desired`.
 *
 * Items the user did not move keep their live order, so concurrent moves from
 * other devices survive. When two devices move the same item, the later
 * request decides where it goes. Items deleted since `base` are skipped, and
 * items created since `base` keep their live position.
 */

export interface OrderMove {
  /** The item to move. */
  id: string;
  /** The item it goes in front of, or `null` for the end of the list. */
  beforeId: string | null;
}

/** Returns the reordered live list that applies the user's moves from `base` to `desired`. */
export function rebaseOrder(
  base: readonly string[],
  desired: readonly string[],
  live: readonly string[],
): string[] {
  assertUnique(base, "base order");
  assertUnique(desired, "order");
  assertUnique(live, "live order");
  const baseSet = new Set(base);
  if (
    base.length !== desired.length ||
    desired.some((id) => !baseSet.has(id))
  ) {
    throw new OrderMergeInputError(
      "The order must contain the same items as the base order",
    );
  }

  const moved = movedItems(base, desired);
  const liveSet = new Set(live);
  const result = live.filter((id) => !moved.has(id));
  for (let index = desired.length - 1; index >= 0; index -= 1) {
    const id = desired[index]!;
    if (!moved.has(id) || !liveSet.has(id)) continue;
    let anchorIndex = -1;
    for (let next = index + 1; next < desired.length; next += 1) {
      anchorIndex = result.indexOf(desired[next]!);
      if (anchorIndex >= 0) break;
    }
    if (anchorIndex < 0) result.push(id);
    else result.splice(anchorIndex, 0, id);
  }
  return result;
}

/**
 * Returns the fewest "move before" steps that turn `current` into `target`.
 * Apply them in the returned order. `target` must contain the same items as `current`.
 *
 * Items in `pinned` are never moved (they can still be move targets). They must
 * keep the same relative order in `current` and `target`.
 */
export function planOrderMoves(
  current: readonly string[],
  target: readonly string[],
  pinned: ReadonlySet<string> = new Set(),
): OrderMove[] {
  assertUnique(current, "current order");
  assertUnique(target, "target order");
  const currentIndex = new Map(current.map((id, index) => [id, index]));
  if (
    current.length !== target.length ||
    target.some((id) => !currentIndex.has(id))
  ) {
    throw new OrderMergeInputError(
      "The target order must contain the same items as the current order",
    );
  }
  const positions = target.map((id) => currentIndex.get(id)!);
  const stableIds = new Set<string>();
  // Pinned items split the target into segments. Within each segment only items
  // whose current position lies between the neighbouring pinned items can stay.
  let segmentStart = 0;
  let lowerBound = -1;
  for (let index = 0; index <= target.length; index += 1) {
    const isPinned = index < target.length && pinned.has(target[index]!);
    if (!isPinned && index < target.length) continue;
    const upperBound =
      index < target.length ? positions[index]! : current.length;
    if (upperBound <= lowerBound) {
      throw new OrderMergeInputError(
        "Pinned items must keep their relative order",
      );
    }
    const candidates: number[] = [];
    for (let segment = segmentStart; segment < index; segment += 1) {
      const position = positions[segment]!;
      if (position > lowerBound && position < upperBound)
        candidates.push(segment);
    }
    const run = longestIncreasingRun(
      candidates.map((segment) => positions[segment]!),
    );
    for (const runIndex of run) stableIds.add(target[candidates[runIndex]!]!);
    if (index < target.length) {
      stableIds.add(target[index]!);
      lowerBound = upperBound;
    }
    segmentStart = index + 1;
  }
  const moves: OrderMove[] = [];
  // Working from the end keeps every later item already in its final place,
  // so placing an item in front of its successor is final for that item too.
  for (let index = target.length - 1; index >= 0; index -= 1) {
    const id = target[index]!;
    if (stableIds.has(id)) continue;
    moves.push({ id, beforeId: target[index + 1] ?? null });
  }
  return moves;
}

/** Applies planned moves to a list, as Actual's "move before" commands do. */
export function applyOrderMoves(
  order: readonly string[],
  moves: readonly OrderMove[],
): string[] {
  const result = [...order];
  for (const move of moves) {
    const from = result.indexOf(move.id);
    if (from < 0) throw new Error(`Cannot move missing item ${move.id}`);
    result.splice(from, 1);
    if (move.beforeId === null) {
      result.push(move.id);
    } else {
      const to = result.indexOf(move.beforeId);
      if (to < 0)
        throw new Error(`Cannot move before missing item ${move.beforeId}`);
      result.splice(to, 0, move.id);
    }
  }
  return result;
}

/** A batch that cannot be merged; reported to the client as an invalid request. */
export class OrderMergeInputError extends BudgetMaintenanceInputError {
  constructor(message: string) {
    super(message);
    this.name = "OrderMergeInputError";
  }
}

function movedItems(
  base: readonly string[],
  desired: readonly string[],
): Set<string> {
  const baseIndex = new Map(base.map((id, index) => [id, index]));
  const stable = longestIncreasingRun(desired.map((id) => baseIndex.get(id)!));
  return new Set(desired.filter((_, index) => !stable.has(index)));
}

/** Indexes (into `values`) of one longest strictly increasing subsequence. */
function longestIncreasingRun(values: readonly number[]): Set<number> {
  const tails: number[] = [];
  const previous = new Array<number>(values.length).fill(-1);
  for (let index = 0; index < values.length; index += 1) {
    const value = values[index]!;
    let low = 0;
    let high = tails.length;
    while (low < high) {
      const middle = (low + high) >> 1;
      if (values[tails[middle]!]! < value) low = middle + 1;
      else high = middle;
    }
    if (low > 0) previous[index] = tails[low - 1]!;
    tails[low] = index;
  }
  const run = new Set<number>();
  let cursor = tails.length > 0 ? tails[tails.length - 1]! : -1;
  while (cursor >= 0) {
    run.add(cursor);
    cursor = previous[cursor]!;
  }
  return run;
}

function assertUnique(ids: readonly string[], label: string): void {
  if (new Set(ids).size !== ids.length) {
    throw new OrderMergeInputError(
      `The ${label} contains an item more than once`,
    );
  }
}
