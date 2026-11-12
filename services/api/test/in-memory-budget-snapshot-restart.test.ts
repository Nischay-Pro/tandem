import { describe, expect, it, vi } from "vitest";
import { BudgetReadApi } from "../src/budget-projection/budget-read-api.js";
import { InMemoryBudgetProjectionStore } from "../src/budget-projection/in-memory-budget-projection-store.js";
import type {
  BudgetDisplaySettings,
  BudgetProjectionRecord,
  BudgetSnapshotReader,
} from "../src/budget-projection/contracts.js";

const displaySettings: BudgetDisplaySettings = {
  budgetType: "envelope",
  currencyCode: "USD",
  currencySymbol: "$",
  currencyFractionDigits: 2,
  numberFormat: "1,234.56",
  hideFraction: false,
  currencySymbolPosition: "before",
  currencySpaceBetweenAmountAndSymbol: false,
};

const record: BudgetProjectionRecord = {
  type: "transaction",
  id: "transaction-1",
  revision: "a".repeat(64),
  body: { id: "transaction-1" },
};

describe("in-memory snapshot restart recovery", () => {
  it("forces a full snapshot when an API restart loses the page cache", async () => {
    const previousStore = new InMemoryBudgetProjectionStore();
    const previousSnapshot = await previousStore.applySnapshot(
      "budget-1",
      "epoch",
      [record],
    );
    const readFullWithSettings = vi.fn(async () => ({
      records: [record],
      displaySettings,
    }));
    const reader: BudgetSnapshotReader = {
      read: vi.fn(async () => [record]),
      readWithSettings: vi.fn(async () => ({
        records: [],
        displaySettings,
        unchanged: true,
      })),
      readFullWithSettings,
    };
    const api = new BudgetReadApi(
      { withBudgetLock: async (_budgetId, work) => work() },
      reader,
      new InMemoryBudgetProjectionStore(),
      "epoch",
    );

    const page = await api.getSnapshotPage({
      budgetId: "budget-1",
      generation: previousSnapshot.generation,
      cursor: "0",
      afterType: "transaction",
      afterId: "transaction-1",
      limit: 100,
    });

    expect(readFullWithSettings).toHaveBeenCalledOnce();
    expect(page).toMatchObject({
      budgetId: "budget-1",
      cursor: "0",
      resetRequired: true,
      records: [],
      displaySettings,
    });
  });
});
