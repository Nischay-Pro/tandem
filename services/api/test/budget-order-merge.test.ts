import { describe, expect, it } from "vitest";
import {
  applyOrderMoves,
  OrderMergeInputError,
  planOrderMoves,
  rebaseOrder,
} from "../src/actual/budget-order-merge.js";

const letters = (value: string) => value.split("");

describe("rebaseOrder", () => {
  it("applies the dropped order unchanged when nothing else moved", () => {
    // 1 -> 2, then 3 -> 2, then 2 -> 4 collapses to the final dropped order.
    const base = letters("ABCDE");
    // Drops on screen: A moved to position 2 (BACDE), then C moved to position 2
    // (BCADE), then the item at position 2 moved to position 4. Only the final
    // order is sent.
    const dropped = letters("BADEC");
    expect(rebaseOrder(base, dropped, base)).toEqual(dropped);
  });

  it("keeps another device's move of an item this device did not touch", () => {
    const base = letters("ABCDE");
    const live = letters("EABCD"); // another device moved E to the top
    const desired = letters("BACDE"); // this device moved A below B
    expect(rebaseOrder(base, desired, live)).toEqual(letters("EBACD"));
  });

  it("lets the later request decide where an item both devices moved goes", () => {
    const base = letters("ABCDE");
    const live = letters("BCDEA"); // another device moved A to the end
    const desired = letters("BCADE"); // this device moved A into the middle
    expect(rebaseOrder(base, desired, live)).toEqual(letters("BCADE"));
  });

  it("skips items deleted since the base order and keeps new items in place", () => {
    const base = letters("ABCD");
    const live = letters("AXBD"); // C deleted, X created
    const desired = letters("CDAB"); // move C and D to the top
    expect(rebaseOrder(base, desired, live)).toEqual(letters("DAXB"));
  });

  it("rejects orders that are not a rearrangement of the base order", () => {
    expect(() =>
      rebaseOrder(letters("ABC"), letters("ABD"), letters("ABC")),
    ).toThrow(OrderMergeInputError);
    expect(() =>
      rebaseOrder(letters("ABC"), letters("AAB"), letters("ABC")),
    ).toThrow(OrderMergeInputError);
  });

  it("reproduces any dropped order when the live order still matches the base", () => {
    const random = seededRandom(7);
    for (let trial = 0; trial < 500; trial += 1) {
      const base = Array.from(
        { length: 1 + Math.floor(random() * 12) },
        (_, i) => `item-${i}`,
      );
      const desired = shuffle(base, random);
      expect(rebaseOrder(base, desired, base)).toEqual(desired);
    }
  });

  it("always returns a rearrangement of the live order", () => {
    const random = seededRandom(11);
    for (let trial = 0; trial < 500; trial += 1) {
      const base = Array.from(
        { length: 2 + Math.floor(random() * 10) },
        (_, i) => `item-${i}`,
      );
      const desired = shuffle(base, random);
      const live = shuffle(
        [...base.filter(() => random() > 0.2), "created-1", "created-2"],
        random,
      );
      const result = rebaseOrder(base, desired, live);
      expect([...result].sort()).toEqual([...live].sort());
    }
  });
});

describe("planOrderMoves", () => {
  it("moves only the items outside the longest run already in order", () => {
    const moves = planOrderMoves(letters("ABCDE"), letters("BCDEA"));
    expect(moves).toEqual([{ id: "A", beforeId: null }]);
  });

  it("plans nothing when the order already matches", () => {
    expect(planOrderMoves(letters("ABC"), letters("ABC"))).toEqual([]);
  });

  it("reaches any target order with the fewest moves", () => {
    const random = seededRandom(3);
    for (let trial = 0; trial < 500; trial += 1) {
      const current = Array.from(
        { length: 1 + Math.floor(random() * 15) },
        (_, i) => `item-${i}`,
      );
      const target = shuffle(current, random);
      const moves = planOrderMoves(current, target);
      expect(applyOrderMoves(current, moves)).toEqual(target);
      expect(moves.length).toBe(
        current.length - longestCommonOrderLength(current, target),
      );
    }
  });

  it("never moves pinned items and still reaches the target", () => {
    const random = seededRandom(19);
    for (let trial = 0; trial < 500; trial += 1) {
      const current = Array.from(
        { length: 2 + Math.floor(random() * 12) },
        (_, i) => `item-${i}`,
      );
      const pinned = new Set(current.filter(() => random() < 0.3));
      // Shuffle only the unpinned items between the pinned slots.
      const free = shuffle(
        current.filter((id) => !pinned.has(id)),
        random,
      );
      const target = current.map((id) => (pinned.has(id) ? id : free.shift()!));
      const moves = planOrderMoves(current, target, pinned);
      expect(moves.some((move) => pinned.has(move.id))).toBe(false);
      expect(applyOrderMoves(current, moves)).toEqual(target);
    }
  });

  it("rejects pinned items that change their relative order", () => {
    expect(() =>
      planOrderMoves(letters("ABC"), letters("CBA"), new Set(["A", "C"])),
    ).toThrow("Pinned items must keep their relative order");
  });

  it("refuses to apply moves that refer to missing items", () => {
    expect(() =>
      applyOrderMoves(letters("AB"), [{ id: "X", beforeId: null }]),
    ).toThrow("Cannot move missing item X");
    expect(() =>
      applyOrderMoves(letters("AB"), [{ id: "A", beforeId: "X" }]),
    ).toThrow("Cannot move before missing item X");
  });

  it("rejects a target with different items", () => {
    expect(() => planOrderMoves(letters("AB"), letters("AC"))).toThrow(
      OrderMergeInputError,
    );
  });
});

function longestCommonOrderLength(a: string[], b: string[]): number {
  const table = Array.from({ length: a.length + 1 }, () =>
    new Array<number>(b.length + 1).fill(0),
  );
  for (let i = 1; i <= a.length; i += 1) {
    for (let j = 1; j <= b.length; j += 1) {
      table[i]![j] =
        a[i - 1] === b[j - 1]
          ? table[i - 1]![j - 1]! + 1
          : Math.max(table[i - 1]![j]!, table[i]![j - 1]!);
    }
  }
  return table[a.length]![b.length]!;
}

function shuffle<T>(values: readonly T[], random: () => number): T[] {
  const result = [...values];
  for (let index = result.length - 1; index > 0; index -= 1) {
    const swap = Math.floor(random() * (index + 1));
    [result[index], result[swap]] = [result[swap]!, result[index]!];
  }
  return result;
}

function seededRandom(seed: number): () => number {
  let state = seed;
  return () => {
    state = (state * 1_103_515_245 + 12_345) % 2_147_483_648;
    return state / 2_147_483_648;
  };
}
