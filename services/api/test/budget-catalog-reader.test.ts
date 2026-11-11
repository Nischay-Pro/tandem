import { describe, expect, it, vi } from "vitest";
import {
  ActualBudgetCatalogReader,
  type ActualBudgetCatalogApi,
} from "../src/actual/budget-catalog-reader.js";

function createReader(
  budgets: Array<{ groupId?: string; name?: string; encryptKeyId?: string }>,
) {
  const getBudgets = vi.fn(async () => budgets);
  const execute = vi.fn();
  const session = {
    execute: <T>(operation: () => Promise<T>): Promise<T> => {
      execute();
      return operation();
    },
  };
  const reader = new ActualBudgetCatalogReader(session, {
    getBudgets,
  } as unknown as ActualBudgetCatalogApi);
  return { reader, getBudgets, execute };
}

describe("Actual budget catalog reader", () => {
  it("returns cloud budget names and encryption state without local-only files", async () => {
    const fake = createReader([
      {
        groupId: "sync-household",
        name: "Household",
        encryptKeyId: "encrypted-key",
      },
      { groupId: "sync-travel", name: "Travel" },
      { name: "Local only" },
      { groupId: "  ", name: "Invalid sync ID" },
    ]);

    await expect(fake.reader.read()).resolves.toEqual([
      { budgetId: "sync-household", name: "Household", isEncrypted: true },
      { budgetId: "sync-travel", name: "Travel", isEncrypted: false },
    ]);
    expect(fake.execute).toHaveBeenCalledOnce();
    expect(fake.getBudgets).toHaveBeenCalledOnce();
  });

  it("rejects an Actual budget with no display name", async () => {
    const fake = createReader([{ groupId: "sync-id", name: " " }]);
    await expect(fake.reader.read()).rejects.toThrow(
      "Actual returned a budget without a valid name",
    );
  });

  it("returns one catalog entry when Actual lists a cached and remote copy", async () => {
    const fake = createReader([
      { groupId: "sync-household", name: "Old household name" },
      {
        groupId: "sync-household",
        name: "Household",
        encryptKeyId: "encrypted-key",
      },
    ]);

    await expect(fake.reader.read()).resolves.toEqual([
      {
        budgetId: "sync-household",
        name: "Household",
        isEncrypted: true,
      },
    ]);
  });
});
