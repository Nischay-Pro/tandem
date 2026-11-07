import { describe, expect, it, vi } from "vitest";
import { createBudgetMaintenanceApi } from "../src/actual/budget-maintenance-api.js";
import type { BudgetOperationOptions } from "../src/actual/actual-session.js";
import type { ActualBudgetMaintenanceSession } from "../src/actual/budget-maintenance-api.js";
import type { BudgetLockPort } from "../src/actual/transaction-reconciler.js";

function createFake() {
  const events: string[] = [];
  const categories: Array<{ id: string; name: string; is_income: boolean; group_id: string; hidden: boolean }> = [
    { id: "expense", name: "Expense", is_income: false, group_id: "group-1", hidden: false },
    { id: "expense-2", name: "Expense 2", is_income: false, group_id: "group-1", hidden: false },
    { id: "income", name: "Income", is_income: true, group_id: "group-income", hidden: false },
  ];
  const runBudgetCalls: Array<{
    budgetId: string;
    options: BudgetOperationOptions | undefined;
  }> = [];
  const runBudgetImplementation: ActualBudgetMaintenanceSession["runBudget"] =
    async <T>(
      budgetId: string,
      operation: () => Promise<T>,
      options?: BudgetOperationOptions,
    ) => {
      events.push(`session:${budgetId}`);
      runBudgetCalls.push({ budgetId, options });
      return operation();
    };
  const runBudget = runBudgetImplementation;
  const withBudgetLock = vi.fn(
    async <T>(budgetId: string, work: () => Promise<T>) => {
      events.push(`lock:${budgetId}`);
      return work();
    },
  );
  const client = {
    runBankSync: vi.fn(async () => undefined),
    createAccount: vi.fn(async () => "new-account"),
    closeAccount: vi.fn(async () => undefined),
    getAccounts: vi.fn(async () => [
      { id: "source", closed: false },
      { id: "destination", closed: false },
      { id: "closed", closed: true },
    ]),
    getAccountBalance: vi.fn(async () => 0),
    getCategories: vi.fn(async () => [...categories]),
    getCategoryGroups: vi.fn(async () => [{ id: "group-1", name: "Food", is_income: false, hidden: false }]),
    createCategory: vi.fn(async () => "new-category"),
    setBudgetAmount: vi.fn(async () => undefined),
    updateCategory: vi.fn(async (id: string, fields: { name?: string }) => {
      const category = categories.find((candidate) => candidate.id === id);
      if (category && fields.name) category.name = fields.name;
    }),
    deleteCategory: vi.fn(async () => undefined),
  };
  const api = createBudgetMaintenanceApi(
    { withBudgetLock } as unknown as BudgetLockPort,
    { runBudget },
    client as unknown as Parameters<typeof createBudgetMaintenanceApi>[2],
  );
  return { api, client, events, runBudgetCalls, withBudgetLock };
}

describe("Actual budget maintenance API", () => {
  it("runs configured bank sync under the budget lock and forwards encryption only to Actual", async () => {
    const fake = createFake();

    await fake.api.runBankSync("budget-1", "secret");

    expect(fake.events).toEqual(["lock:budget-1", "session:budget-1"]);
    expect(fake.runBudgetCalls).toEqual([
      {
        budgetId: "budget-1",
        options: { synchronizeAfterWrite: true, encryptionPassword: "secret" },
      },
    ]);
    expect(fake.client.runBankSync).toHaveBeenCalledOnce();
  });

  it("creates accounts through Actual and refreshes the Actual projection", async () => {
    const fake = createFake();

    await fake.api.createAccount("budget-1", "Travel card", true);

    expect(fake.client.createAccount).toHaveBeenCalledWith({
      name: "Travel card",
      offbudget: true,
    });
    expect(fake.runBudgetCalls).toEqual([
      { budgetId: "budget-1", options: { synchronizeAfterWrite: true } },
    ]);
  });

  it("creates a category in an active group and rejects duplicate names", async () => {
    const fake = createFake();

    await expect(fake.api.createCategory("budget-1", "Dining", "group-1")).resolves.toBe("new-category");
    expect(fake.client.createCategory).toHaveBeenCalledWith({
      name: "Dining",
      group_id: "group-1",
      is_income: false,
      hidden: false,
    });
    expect(fake.runBudgetCalls.at(-1)).toEqual({ budgetId: "budget-1", options: { synchronizeAfterWrite: true } });

    fake.client.getCategories.mockResolvedValueOnce([
      { id: "existing", name: "Dining", group_id: "group-1", is_income: false, hidden: false },
    ]);
    await expect(fake.api.createCategory("budget-1", " dining ", "group-1")).rejects.toThrow(
      "A category with this name already exists in that group",
    );
    expect(fake.client.createCategory).toHaveBeenCalledOnce();
  });

  it("sets the selected month's assigned category amount through Actual", async () => {
    const fake = createFake();

    await fake.api.setBudgetAmount("budget-1", "expense", "2026-11", 125_000);

    expect(fake.client.setBudgetAmount).toHaveBeenCalledWith("2026-11", "expense", 125_000);
    expect(fake.runBudgetCalls.at(-1)).toEqual({ budgetId: "budget-1", options: { synchronizeAfterWrite: true } });
  });

  it("validates account close destinations before calling Actual", async () => {
    const fake = createFake();

    await expect(
      fake.api.closeAccount("budget-1", "source", "closed", null),
    ).rejects.toThrow("Choose a different open account in this budget");
    expect(fake.client.closeAccount).not.toHaveBeenCalled();

    await fake.api.closeAccount("budget-1", "source", "destination", null);
    expect(fake.client.closeAccount).toHaveBeenCalledWith(
      "source",
      "destination",
      undefined,
    );
  });

  it("rejects account closure when the source account is unknown", async () => {
    const fake = createFake();

    await expect(
      fake.api.closeAccount("budget-1", "missing", null, null),
    ).rejects.toThrow("Account does not exist in this budget");
    expect(fake.client.closeAccount).not.toHaveBeenCalled();
  });

  it("rejects an account close category that is outside the budget", async () => {
    const fake = createFake();

    await expect(
      fake.api.closeAccount("budget-1", "source", null, "missing"),
    ).rejects.toThrow("Choose a category in this budget");
    expect(fake.client.closeAccount).not.toHaveBeenCalled();
  });

  it("closes an account with both a valid transfer account and category", async () => {
    const fake = createFake();

    await fake.api.closeAccount(
      "budget-1",
      "source",
      "destination",
      "expense-2",
      "secret",
    );

    expect(fake.client.getCategories).toHaveBeenCalledOnce();
    expect(fake.client.closeAccount).toHaveBeenCalledWith(
      "source",
      "destination",
      "expense-2",
    );
    expect(fake.runBudgetCalls).toEqual([
      {
        budgetId: "budget-1",
        options: { synchronizeAfterWrite: true, encryptionPassword: "secret" },
      },
    ]);
  });

  it("closes an account without reassigning its balance", async () => {
    const fake = createFake();

    await fake.api.closeAccount("budget-1", "source", null, null);

    expect(fake.client.closeAccount).toHaveBeenCalledWith(
      "source",
      undefined,
      undefined,
    );
  });

  it("requires a transfer account when closing an account with a balance", async () => {
    const fake = createFake();
    fake.client.getAccountBalance.mockResolvedValueOnce(-12_000);

    await expect(
      fake.api.closeAccount("budget-1", "source", null, null),
    ).rejects.toThrow(
      "Choose an account to move this balance before closing it",
    );
    expect(fake.client.closeAccount).not.toHaveBeenCalled();
  });

  it("moves deleted categories only to a valid category of the same type", async () => {
    const fake = createFake();

    await expect(
      fake.api.deleteCategory("budget-1", "expense", "income"),
    ).rejects.toThrow(
      "Transactions can only move to a category of the same type",
    );
    expect(fake.client.deleteCategory).not.toHaveBeenCalled();

    await fake.api.deleteCategory("budget-1", "expense", "expense-2");
    expect(fake.client.deleteCategory).toHaveBeenCalledWith(
      "expense",
      "expense-2",
    );
  });

  it("deletes a category as unassigned when its destination is null", async () => {
    const fake = createFake();

    await fake.api.deleteCategory("budget-1", "expense", null);

    expect(fake.client.deleteCategory).toHaveBeenCalledWith(
      "expense",
      undefined,
    );
  });

  it("rejects a category deletion destination outside the budget", async () => {
    const fake = createFake();

    await expect(
      fake.api.deleteCategory("budget-1", "expense", "missing"),
    ).rejects.toThrow("Choose a different category in this budget");
    expect(fake.client.deleteCategory).not.toHaveBeenCalled();
  });

  it("ignores a delete request for a category that has already disappeared", async () => {
    const fake = createFake();

    await fake.api.deleteCategory("budget-1", "missing", null);

    expect(fake.client.deleteCategory).not.toHaveBeenCalled();
  });

  it("renames a category through Actual and avoids no-op writes", async () => {
    const fake = createFake();

    await fake.api.renameCategory("budget-1", "expense", "Home care");
    await fake.api.renameCategory("budget-1", "expense", "Home care");

    expect(fake.client.updateCategory).toHaveBeenCalledOnce();
    expect(fake.client.updateCategory).toHaveBeenCalledWith("expense", {
      name: "Home care",
    });
  });

  it("rejects a category rename when the category is outside the budget", async () => {
    const fake = createFake();

    await expect(
      fake.api.renameCategory("budget-1", "missing", "Home care"),
    ).rejects.toThrow("Category does not exist in this budget");
    expect(fake.client.updateCategory).not.toHaveBeenCalled();
  });
});
