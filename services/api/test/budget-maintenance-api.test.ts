import { describe, expect, it, vi } from "vitest";
import {
  BANK_SYNC_TIMEOUT_MS,
  createBudgetMaintenanceApi,
} from "../src/actual/budget-maintenance-api.js";
import type { BudgetOperationOptions } from "../src/actual/actual-session.js";
import type {
  ActualBudgetMaintenanceSession,
  BudgetScheduleEdit,
  StatementTransactionImport,
} from "../src/actual/budget-maintenance-api.js";
import type { BudgetLockPort } from "../src/actual/transaction-reconciler.js";

function createFake() {
  const events: string[] = [];
  const categories: Array<{
    id: string;
    name: string;
    is_income: boolean;
    group_id: string;
    hidden: boolean;
  }> = [
    {
      id: "expense",
      name: "Expense",
      is_income: false,
      group_id: "group-1",
      hidden: false,
    },
    {
      id: "expense-2",
      name: "Expense 2",
      is_income: false,
      group_id: "group-1",
      hidden: false,
    },
    {
      id: "income",
      name: "Income",
      is_income: true,
      group_id: "group-income",
      hidden: false,
    },
  ];
  const payees: Array<{ id: string; name: string; transfer_acct: string }> = [];
  const rules: Array<{
    id: string;
    stage: "pre" | "post" | "default" | null;
    conditionsOp: "and" | "or";
    conditions: Array<{ field: string; op: string; value: string }>;
    actions: Array<{ field: string; op: string; value: unknown }>;
  }> = [];
  const schedules: Array<{
    id: string;
    name?: string;
    completed?: boolean;
    account?: string;
    next_date?: string;
    date: string | { frequency: string; start: string };
  }> = [
    {
      id: "monthly-rent",
      name: "Rent",
      completed: false,
      account: "checking",
      next_date: "2026-11-15",
      date: { frequency: "monthly", start: "2026-01-01" },
    },
    {
      id: "one-time",
      name: "One time",
      completed: false,
      account: "checking",
      next_date: "2026-12-01",
      date: "2026-12-01",
    },
    {
      id: "completed",
      name: "Completed",
      completed: true,
      account: "checking",
      next_date: "2026-11-15",
      date: { frequency: "monthly", start: "2026-01-01" },
    },
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
  const previewQuery = {
    filter: vi.fn(),
    select: vi.fn(),
    options: vi.fn(),
  };
  previewQuery.filter.mockReturnValue(previewQuery);
  previewQuery.select.mockReturnValue(previewQuery);
  previewQuery.options.mockReturnValue(previewQuery);
  const client = {
    runBankSync: vi.fn(async () => undefined),
    q: vi.fn(() => previewQuery),
    aqlQuery: vi.fn(async () => ({ data: [{ imported_id: "ref-existing" }] })),
    importTransactions: vi.fn(async () => ({
      added: ["imported-1"],
      updated: [] as string[],
      errors: [] as Array<{ message: string }>,
    })),
    createAccount: vi.fn(async () => "new-account"),
    updateAccount: vi.fn(async () => undefined),
    reopenAccount: vi.fn(async () => undefined),
    getAccountGroups: vi.fn(async () => [{ id: "credit", name: "Credit" }]),
    closeAccount: vi.fn(async () => undefined),
    getAccounts: vi.fn(async () => [
      { id: "account-1", closed: false },
      { id: "source", closed: false },
      { id: "destination", closed: false },
      { id: "closed", closed: true },
    ]),
    getAccountBalance: vi.fn(async () => 0),
    getCategories: vi.fn(async () => [...categories]),
    getCategoryGroups: vi.fn(async () => [
      { id: "group-1", name: "Food", is_income: false, hidden: false },
      { id: "group-2", name: "Travel", is_income: false, hidden: false },
    ]),
    createCategoryGroup: vi.fn(async () => "new-group"),
    updateCategoryGroup: vi.fn(async () => undefined),
    createCategory: vi.fn(async () => "new-category"),
    getPayees: vi.fn(async () => [...payees]),
    getPayeeRules: vi.fn(async () => [...rules]),
    createPayee: vi.fn(
      async (payee: { name: string; transfer_acct: string }) => {
        const id = `payee-${payees.length + 1}`;
        payees.push({ id, ...payee });
        return id;
      },
    ),
    updatePayee: vi.fn(async () => undefined),
    mergePayees: vi.fn(async () => undefined),
    deletePayee: vi.fn(async () => undefined),
    createRule: vi.fn(async () => ({ id: "default-category-rule" })),
    updateRule: vi.fn(async () => ({ id: "default-category-rule" })),
    deleteRule: vi.fn(async () => true),
    setBudgetAmount: vi.fn(async () => undefined),
    setBudgetCarryover: vi.fn(async () => undefined),
    holdBudgetForNextMonth: vi.fn(async () => true),
    resetBudgetHold: vi.fn(async () => undefined),
    getPreferences: vi.fn(async () => ({ budgetType: "envelope" })),
    getBudgetMonth: vi.fn(async () => ({
      toBudget: 50_000,
      categoryGroups: [
        {
          categories: [
            {
              id: "expense",
              is_income: false,
              budgeted: 50_000,
              balance: 30_000,
            },
            {
              id: "expense-2",
              is_income: false,
              budgeted: 20_000,
              balance: 15_000,
            },
          ],
        },
      ],
    })),
    batchBudgetUpdates: vi.fn(async (work: () => Promise<void>) => work()),
    updateCategory: vi.fn(
      async (id: string, fields: { name?: string; hidden?: boolean }) => {
        const category = categories.find((candidate) => candidate.id === id);
        if (category && fields.name) category.name = fields.name;
        if (category && fields.hidden !== undefined)
          category.hidden = fields.hidden;
      },
    ),
    deleteCategory: vi.fn(async () => undefined),
    getSchedules: vi.fn(async () => [...schedules]),
    updateSchedule: vi.fn(async () => "monthly-rent"),
    deleteSchedule: vi.fn(async () => undefined),
    internal: {
      send: vi.fn(async () => undefined),
    },
  };
  const api = createBudgetMaintenanceApi(
    { withBudgetLock } as unknown as BudgetLockPort,
    { runBudget },
    client as unknown as Parameters<typeof createBudgetMaintenanceApi>[2],
  );
  return { api, client, events, runBudgetCalls, withBudgetLock, payees, rules };
}

describe("Actual budget maintenance API", () => {
  it("checks statement references read-only, scoped to the account, and accepts encrypted budgets", async () => {
    const fake = createFake();

    await expect(
      fake.api.previewStatementImportTransactions(
        "budget-1",
        "account-1",
        ["ref-new", "ref-existing"],
        "budget-secret",
      ),
    ).resolves.toEqual({ existingImportedIds: ["ref-existing"] });

    expect(fake.events.slice(-2)).toEqual([
      "lock:budget-1",
      "session:budget-1",
    ]);
    expect(fake.runBudgetCalls.at(-1)?.options).toEqual({
      synchronizeAfterWrite: false,
      encryptionPassword: "budget-secret",
    });
    expect(fake.client.q).toHaveBeenCalledWith("transactions");
    expect(fake.client.aqlQuery).toHaveBeenCalledWith(
      fake.client.q.mock.results[0]?.value,
    );
    expect(fake.client.importTransactions).not.toHaveBeenCalled();
    expect(fake.client.q.mock.results[0]?.value.filter).toHaveBeenCalledWith({
      account: "account-1",
      imported_id: { $oneof: ["ref-new", "ref-existing"] },
    });
    expect(fake.client.q.mock.results[0]?.value.select).toHaveBeenCalledWith([
      "imported_id",
    ]);
    expect(fake.client.q.mock.results[0]?.value.options).toHaveBeenCalledWith({
      splits: "grouped",
    });
  });

  it("rejects empty, oversized, duplicate, and closed-account statement previews", async () => {
    const fake = createFake();

    await expect(
      fake.api.previewStatementImportTransactions("budget-1", " ", [
        "reference",
      ]),
    ).rejects.toThrow("Choose an account");
    await expect(
      fake.api.previewStatementImportTransactions("budget-1", "account-1", []),
    ).rejects.toThrow("Choose up to 1,000 unique statement references");
    await expect(
      fake.api.previewStatementImportTransactions("budget-1", "account-1", [
        "repeat",
        "repeat",
      ]),
    ).rejects.toThrow("Choose up to 1,000 unique statement references");
    await expect(
      fake.api.previewStatementImportTransactions(
        "budget-1",
        "account-1",
        Array(1_001).fill("reference"),
      ),
    ).rejects.toThrow("Choose up to 1,000 unique statement references");
    await expect(
      fake.api.previewStatementImportTransactions("budget-1", "closed", [
        "ref-existing",
      ]),
    ).rejects.toThrow("Choose an open account in this budget");
    expect(fake.client.aqlQuery).not.toHaveBeenCalled();
  });

  it("rejects malformed Actual rows in a statement preview", async () => {
    const invalidRow = createFake();
    invalidRow.client.aqlQuery.mockResolvedValueOnce({ data: [null] } as never);
    await expect(
      invalidRow.api.previewStatementImportTransactions(
        "budget-1",
        "account-1",
        ["reference"],
      ),
    ).rejects.toThrow("Actual returned an invalid statement-import preview");

    const invalidReference = createFake();
    invalidReference.client.aqlQuery.mockResolvedValueOnce({
      data: [{ imported_id: "different-reference" }],
    });
    await expect(
      invalidReference.api.previewStatementImportTransactions(
        "budget-1",
        "account-1",
        ["reference"],
      ),
    ).rejects.toThrow("Actual returned an invalid statement reference");
  });

  it("limits overlapping, failed, and recently completed bank syncs", async () => {
    const fake = createFake();
    let finishSync: (() => void) | undefined;
    fake.client.runBankSync.mockImplementationOnce(
      () =>
        new Promise<undefined>((resolve) => {
          finishSync = () => resolve(undefined);
        }),
    );

    const firstSync = fake.api.runBankSync("budget-1");
    await expect(fake.api.runBankSync("budget-1")).rejects.toThrow(
      "A bank sync is already running",
    );
    finishSync?.();
    await firstSync;
    await expect(fake.api.runBankSync("budget-1")).rejects.toThrow(
      "A bank sync is already running or was just completed",
    );

    const failedSync = createFake();
    failedSync.client.runBankSync.mockRejectedValueOnce(new Error("offline"));
    await expect(failedSync.api.runBankSync("budget-1")).rejects.toThrow(
      "offline",
    );
    await expect(failedSync.api.runBankSync("budget-1")).rejects.toThrow(
      "A bank sync is already running or was just completed",
    );
  });

  it("times out a bank sync that never settles", async () => {
    vi.useFakeTimers();
    try {
      const fake = createFake();
      fake.client.runBankSync.mockImplementationOnce(
        () => new Promise<undefined>(() => undefined),
      );
      const sync = fake.api.runBankSync("budget-1");
      const rejection = expect(sync).rejects.toThrow(
        "Actual bank sync exceeded its time limit",
      );
      await vi.advanceTimersByTimeAsync(BANK_SYNC_TIMEOUT_MS);
      await rejection;
    } finally {
      vi.useRealTimers();
    }
  });

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

  it("places a new account in the selected Actual account group", async () => {
    const fake = createFake();

    await fake.api.createAccount("budget-1", "Travel card", false, "credit");

    expect(fake.client.createAccount).toHaveBeenCalledWith({
      name: "Travel card",
      offbudget: false,
    });
    expect(fake.client.updateAccount).toHaveBeenCalledWith("new-account", {
      account_group_id: "credit",
    });
  });

  it("rejects account groups that do not belong to the budget", async () => {
    const fake = createFake();
    fake.client.getAccountGroups.mockResolvedValueOnce([]);

    await expect(
      fake.api.createAccount("budget-1", "Travel card", false, "foreign"),
    ).rejects.toThrow("Choose an account group in this budget");
    expect(fake.client.createAccount).not.toHaveBeenCalled();
  });

  it("renames and reopens only accounts in the selected Actual budget", async () => {
    const fake = createFake();

    await fake.api.renameAccount("budget-1", "source", "Daily checking");
    await fake.api.reopenAccount("budget-1", "closed");
    await fake.api.moveAccount("budget-1", "source", "destination");

    expect(fake.client.updateAccount).toHaveBeenCalledWith("source", {
      name: "Daily checking",
    });
    expect(fake.client.reopenAccount).toHaveBeenCalledWith("closed");
    expect(fake.client.internal?.send).toHaveBeenCalledWith("account-move", {
      id: "source",
      targetId: "destination",
      accountGroupId: null,
    });
    await expect(
      fake.api.renameAccount("budget-1", "missing", "Checking"),
    ).rejects.toThrow("Account does not exist in this budget");
    await expect(fake.api.reopenAccount("budget-1", "source")).rejects.toThrow(
      "This account is already open",
    );
    await expect(fake.api.reopenAccount("budget-1", "missing")).rejects.toThrow(
      "Account does not exist in this budget",
    );
    await expect(
      fake.api.moveAccount("budget-1", "source", "closed"),
    ).rejects.toThrow("Accounts can only be reordered within the same section");
  });

  it("validates account ordering targets and uses the account group", async () => {
    const fake = createFake();

    await expect(
      fake.api.moveAccount("budget-1", "missing", null),
    ).rejects.toThrow("Account does not exist in this budget");
    await expect(
      fake.api.moveAccount("budget-1", "source", "missing"),
    ).rejects.toThrow("Choose an account in this budget");
    await expect(
      fake.api.moveAccount("budget-1", "source", "source"),
    ).rejects.toThrow("Choose a different account position");

    fake.client.getAccounts.mockResolvedValueOnce([
      { id: "source", closed: false, offbudget: false },
      { id: "destination", closed: false, offbudget: true },
    ] as never);
    await expect(
      fake.api.moveAccount("budget-1", "source", "destination"),
    ).rejects.toThrow("same section");

    fake.client.getAccounts.mockResolvedValueOnce([
      {
        id: "source",
        closed: false,
        offbudget: false,
        account_group_id: "checking-group",
      },
      { id: "destination", closed: false, offbudget: false },
    ] as never);
    await fake.api.moveAccount("budget-1", "source", null);
    expect(fake.client.internal?.send).toHaveBeenLastCalledWith(
      "account-move",
      {
        id: "source",
        targetId: null,
        accountGroupId: "checking-group",
      },
    );

    fake.client.internal = undefined as never;
    await expect(
      fake.api.moveAccount("budget-1", "source", null),
    ).rejects.toThrow("does not support account ordering");
  });

  it("creates a category in an active group and rejects duplicate names", async () => {
    const fake = createFake();

    await expect(
      fake.api.createCategory("budget-1", "Dining", "group-1"),
    ).resolves.toBe("new-category");
    expect(fake.client.createCategory).toHaveBeenCalledWith({
      name: "Dining",
      group_id: "group-1",
      is_income: false,
      hidden: false,
    });
    expect(fake.runBudgetCalls.at(-1)).toEqual({
      budgetId: "budget-1",
      options: { synchronizeAfterWrite: true },
    });

    fake.client.getCategories.mockResolvedValueOnce([
      {
        id: "existing",
        name: "Dining",
        group_id: "group-1",
        is_income: false,
        hidden: false,
      },
    ]);
    await expect(
      fake.api.createCategory("budget-1", " dining ", "group-1"),
    ).rejects.toThrow("A category with this name already exists in that group");
    expect(fake.client.createCategory).toHaveBeenCalledOnce();
  });

  it("creates, renames, hides, and reorders category groups and categories", async () => {
    const fake = createFake();

    await expect(
      fake.api.createCategoryGroup("budget-1", "Bills", false),
    ).resolves.toBe("new-group");
    expect(fake.client.createCategoryGroup).toHaveBeenCalledWith({
      name: "Bills",
      is_income: false,
      hidden: false,
    });
    await fake.api.manageCategoryGroup(
      "budget-1",
      "group-1",
      "rename",
      "Needs",
    );
    expect(fake.client.updateCategoryGroup).toHaveBeenCalledWith("group-1", {
      name: "Needs",
    });
    await fake.api.manageCategoryGroup("budget-1", "group-1", "hide");
    expect(fake.client.updateCategoryGroup).toHaveBeenCalledWith("group-1", {
      hidden: true,
    });
    await fake.api.manageCategoryGroup(
      "budget-1",
      "group-1",
      "move",
      undefined,
      null,
    );
    expect(fake.client.internal?.send).toHaveBeenCalledWith(
      "category-group-move",
      {
        id: "group-1",
        targetId: null,
      },
    );

    await fake.api.manageCategory("budget-1", "expense", "hide");
    expect(fake.client.updateCategory).toHaveBeenCalledWith("expense", {
      hidden: true,
    });
    await fake.api.manageCategory("budget-1", "expense", "move", "expense-2");
    expect(fake.client.internal?.send).toHaveBeenCalledWith("category-move", {
      id: "expense",
      groupId: "group-1",
      targetId: "expense-2",
    });
    fake.client.getCategoryGroups
      .mockResolvedValueOnce([
        { id: "group-1", name: "Food", is_income: false, hidden: false },
        { id: "group-2", name: "Travel", is_income: false, hidden: false },
      ])
      .mockResolvedValueOnce([]);
    await fake.api.manageCategory(
      "budget-1",
      "expense",
      "move",
      null,
      undefined,
      "group-2",
    );
    expect(fake.client.internal?.send).toHaveBeenLastCalledWith(
      "category-move",
      {
        id: "expense",
        groupId: "group-2",
        targetId: null,
      },
    );
    await expect(
      fake.api.manageCategory("budget-1", "expense", "move", "income"),
    ).rejects.toThrow("Choose a category in the same group");
    await expect(
      fake.api.manageCategory(
        "budget-1",
        "income",
        "move",
        null,
        undefined,
        "group-2",
      ),
    ).rejects.toThrow("Income categories must stay in an income group");
  });

  it("rejects inactive category groups and defaults missing income metadata", async () => {
    const fake = createFake();
    fake.client.getCategoryGroups
      .mockResolvedValueOnce([
        { id: "hidden", name: "Hidden", is_income: false, hidden: true },
      ])
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([
        {
          id: "group-1",
          name: "Food",
          hidden: false,
        } as unknown as {
          id: string;
          name: string;
          is_income: boolean;
          hidden: boolean;
        },
      ]);

    await expect(
      fake.api.createCategory("budget-1", "Dining", "hidden"),
    ).rejects.toThrow("Choose an active category group");
    await expect(
      fake.api.createCategory("budget-1", "Dining", "missing"),
    ).rejects.toThrow("Choose an active category group");
    await fake.api.createCategory("budget-1", "Dining", "group-1");

    expect(fake.client.createCategory).toHaveBeenCalledWith({
      name: "Dining",
      group_id: "group-1",
      is_income: false,
      hidden: false,
    });
  });

  it("validates category group names, rename conflicts, and move targets", async () => {
    const fake = createFake();

    expect(() => fake.api.createCategoryGroup("budget-1", " ", false)).toThrow(
      "Category group names must be between 1 and 100 characters",
    );
    expect(() =>
      fake.api.createCategoryGroup("budget-1", "G".repeat(101), false),
    ).toThrow("Category group names must be between 1 and 100 characters");
    await expect(
      fake.api.createCategoryGroup("budget-1", " food ", false),
    ).rejects.toThrow("A category group with this name already exists");

    await expect(
      fake.api.manageCategoryGroup("budget-1", "missing", "rename", "Other"),
    ).rejects.toThrow("Category group does not exist in this budget");
    await expect(
      fake.api.manageCategoryGroup("budget-1", "group-1", "rename", " "),
    ).rejects.toThrow(
      "Category group names must be between 1 and 100 characters",
    );
    await expect(
      fake.api.manageCategoryGroup("budget-1", "group-1", "rename"),
    ).rejects.toThrow(
      "Category group names must be between 1 and 100 characters",
    );
    await expect(
      fake.api.manageCategoryGroup(
        "budget-1",
        "group-1",
        "rename",
        "G".repeat(101),
      ),
    ).rejects.toThrow(
      "Category group names must be between 1 and 100 characters",
    );
    await expect(
      fake.api.manageCategoryGroup("budget-1", "group-1", "rename", "travel"),
    ).rejects.toThrow("A category group with this name already exists");
    await fake.api.manageCategoryGroup("budget-1", "group-1", "show");
    expect(fake.client.updateCategoryGroup).toHaveBeenCalledWith("group-1", {
      hidden: false,
    });
    await expect(
      fake.api.manageCategoryGroup(
        "budget-1",
        "group-1",
        "move",
        undefined,
        "group-1",
      ),
    ).rejects.toThrow("Choose a different category group position");
    await expect(
      fake.api.manageCategoryGroup(
        "budget-1",
        "group-1",
        "move",
        undefined,
        "missing",
      ),
    ).rejects.toThrow("Choose a category group in this budget");

    fake.client.internal = undefined as never;
    await expect(
      fake.api.manageCategoryGroup("budget-1", "group-1", "move"),
    ).rejects.toThrow("does not support category group ordering");
  });

  it("validates category membership and cross-group move rules", async () => {
    const fake = createFake();

    await expect(
      fake.api.manageCategory("budget-1", "missing", "hide"),
    ).rejects.toThrow("Category does not exist in this budget");
    await fake.api.manageCategory("budget-1", "expense", "show");
    expect(fake.client.updateCategory).toHaveBeenCalledWith("expense", {
      hidden: false,
    });
    await expect(
      fake.api.manageCategory("budget-1", "expense", "move", "expense"),
    ).rejects.toThrow("Choose a different category position");
    await expect(
      fake.api.manageCategory("budget-1", "expense", "move", "missing"),
    ).rejects.toThrow("Choose a category in the same group");
    await fake.api.manageCategory(
      "budget-1",
      "expense",
      "move",
      null,
      undefined,
      "group-1",
    );
    expect(fake.client.internal?.send).toHaveBeenLastCalledWith(
      "category-move",
      {
        id: "expense",
        groupId: "group-1",
        targetId: null,
      },
    );
    await expect(
      fake.api.manageCategory(
        "budget-1",
        "expense",
        "move",
        null,
        undefined,
        "missing-group",
      ),
    ).rejects.toThrow("Choose a visible category group");
    fake.client.getCategoryGroups
      .mockResolvedValueOnce([
        { id: "group-2", name: "Hidden", is_income: false, hidden: true },
      ])
      .mockResolvedValueOnce([]);
    await expect(
      fake.api.manageCategory(
        "budget-1",
        "expense",
        "move",
        null,
        undefined,
        "group-2",
      ),
    ).rejects.toThrow("Choose a visible category group");
    await expect(
      fake.api.manageCategory(
        "budget-1",
        "expense",
        "move",
        "expense-2",
        undefined,
        "group-2",
      ),
    ).rejects.toThrow("starts at the end of that group");

    fake.client.internal = undefined as never;
    await expect(
      fake.api.manageCategory(
        "budget-1",
        "expense",
        "move",
        null,
        undefined,
        "group-2",
      ),
    ).rejects.toThrow("does not support moving categories between groups");
    await expect(
      fake.api.manageCategory("budget-1", "expense", "move", "expense-2"),
    ).rejects.toThrow("does not support category ordering");
  });

  it("creates payees in Actual and rejects duplicate names case-insensitively", async () => {
    const fake = createFake();

    await expect(
      fake.api.createPayee("budget-1", "Corner Market", "secret"),
    ).resolves.toBe("payee-1");
    expect(fake.client.createPayee).toHaveBeenCalledWith({
      name: "Corner Market",
      transfer_acct: "",
    });
    expect(fake.runBudgetCalls.at(-1)?.options).toEqual({
      encryptionPassword: "secret",
      synchronizeAfterWrite: true,
    });
    await expect(
      fake.api.createPayee("budget-1", " corner market "),
    ).rejects.toThrow("A payee with this name already exists");
    expect(fake.client.createPayee).toHaveBeenCalledOnce();
  });

  it("renames, merges, and deletes only regular payees in the selected budget", async () => {
    const fake = createFake();
    const payees = [
      { id: "source", name: "Old name", transfer_acct: "" },
      { id: "target", name: "New name", transfer_acct: "" },
      { id: "transfer", name: "Checking", transfer_acct: "account-1" },
    ];
    fake.client.getPayees.mockResolvedValue(payees);

    await fake.api.renamePayee("budget-1", "source", " New name ", "secret");
    await fake.api.mergePayee("budget-1", "source", "target");
    await fake.api.deletePayee("budget-1", "source");

    expect(fake.client.updatePayee).toHaveBeenCalledWith("source", {
      name: "New name",
    });
    expect(fake.client.mergePayees).toHaveBeenCalledWith("target", ["source"]);
    expect(fake.client.deletePayee).toHaveBeenCalledWith("source");
    expect(fake.runBudgetCalls.slice(-3).map(({ options }) => options)).toEqual(
      [
        { encryptionPassword: "secret", synchronizeAfterWrite: true },
        { synchronizeAfterWrite: true },
        { synchronizeAfterWrite: true },
      ],
    );

    await expect(
      fake.api.renamePayee("budget-1", "missing", "Name"),
    ).rejects.toThrow("Choose a payee in this budget");
    await expect(fake.api.deletePayee("budget-1", "transfer")).rejects.toThrow(
      "Choose a payee in this budget",
    );
    await expect(
      fake.api.mergePayee("budget-1", "source", "missing"),
    ).rejects.toThrow("Choose two payees in this budget");
    await expect(
      fake.api.mergePayee("budget-1", "source", "source"),
    ).rejects.toThrow("Choose a different payee to merge into");
    await expect(
      fake.api.renamePayee("budget-1", "source", " "),
    ).rejects.toThrow("Payee names must be between 1 and 100 characters");
  });

  it("renames schedules without resetting a skipped occurrence", async () => {
    const fake = createFake();

    await fake.api.renameSchedule(
      "budget-1",
      "monthly-rent",
      " Home rent ",
      "secret",
    );

    expect(fake.client.updateSchedule).toHaveBeenCalledWith(
      "monthly-rent",
      { name: "Home rent" },
      false,
    );
    expect(fake.runBudgetCalls.at(-1)?.options).toEqual({
      encryptionPassword: "secret",
      synchronizeAfterWrite: true,
    });
    await expect(
      fake.api.renameSchedule("budget-1", "monthly-rent", "One time"),
    ).rejects.toThrow("A schedule with this name already exists");
    await expect(
      fake.api.renameSchedule("budget-1", "missing", "New name"),
    ).rejects.toThrow("Choose an active schedule in this budget");
    await expect(
      fake.api.renameSchedule("budget-1", "monthly-rent", "  "),
    ).rejects.toThrow("Schedule names must be between 1 and 100 characters");

    const caseSensitiveName = createFake();
    await caseSensitiveName.api.renameSchedule(
      "budget-1",
      "monthly-rent",
      "one time",
    );
    expect(caseSensitiveName.client.updateSchedule).toHaveBeenCalledWith(
      "monthly-rent",
      { name: "one time" },
      false,
    );
  });

  it("edits a schedule rule through Actual and resets its next date when needed", async () => {
    const fake = createFake();
    fake.client.getPayees.mockResolvedValue([
      { id: "landlord", name: "Landlord", transfer_acct: "" },
    ]);
    const date = {
      frequency: "monthly" as const,
      interval: 1,
      start: "2026-12-01",
      endMode: "never" as const,
      skipWeekend: true,
      weekendSolveMode: "before" as const,
    };

    await fake.api.editSchedule(
      "budget-1",
      "monthly-rent",
      {
        name: "Home rent",
        accountId: "destination",
        payeeId: "landlord",
        amountMinor: -180_000,
        amountOp: "is",
        date,
      },
      "secret",
    );

    expect(fake.client.updateSchedule).toHaveBeenCalledWith(
      "monthly-rent",
      {
        name: "Home rent",
        account: "destination",
        payee: "landlord",
        amount: -180_000,
        amountOp: "is",
        date,
      },
      true,
    );
    expect(fake.runBudgetCalls.at(-1)?.options).toEqual({
      encryptionPassword: "secret",
      synchronizeAfterWrite: true,
    });
  });

  it.each([
    {
      label: "an empty edit",
      edit: {},
      message: "Add at least one schedule change",
    },
    {
      label: "a blank account",
      edit: { accountId: "  " },
      message: "Choose an account",
    },
    {
      label: "a blank payee",
      edit: { payeeId: "  " },
      message: "Choose a payee",
    },
    {
      label: "both amount formats",
      edit: {
        amountMinor: 100,
        amountRangeMinor: { num1: 100, num2: 200 },
        amountOp: "isbetween" as const,
      },
      message: "Choose a single amount or an amount range",
    },
    {
      label: "an amount without an amount rule",
      edit: { amountMinor: 100 },
      message: "Choose an amount rule",
    },
    {
      label: "an amount rule without an amount",
      edit: { amountOp: "is" as const },
      message: "An amount rule requires an amount",
    },
    {
      label: "an unsafe amount",
      edit: { amountMinor: Number.MAX_SAFE_INTEGER, amountOp: "is" as const },
      message:
        "Schedule amounts must be whole cents within the supported limit",
    },
    {
      label: "a reversed amount range",
      edit: {
        amountRangeMinor: { num1: 500, num2: 100 },
        amountOp: "isbetween" as const,
      },
      message: "The minimum amount must not exceed the maximum",
    },
    {
      label: "an invalid one-time date",
      edit: { date: "2026-02-30" },
      message: "Enter a schedule date as YYYY-MM-DD",
    },
    {
      label: "a one-time date with the wrong format",
      edit: { date: "11/5/2026" },
      message: "Enter a schedule date as YYYY-MM-DD",
    },
    {
      label: "an unsupported recurrence frequency",
      edit: {
        date: { frequency: "hourly", start: "2026-01-01" } as never,
      },
      message: "Choose a valid recurrence and start date",
    },
    {
      label: "an invalid recurrence interval",
      edit: {
        date: {
          frequency: "weekly" as const,
          start: "2026-01-01",
          interval: 0,
        },
      },
      message: "Repeat interval must be between 1 and 999",
    },
    {
      label: "an invalid weekend setting",
      edit: {
        date: {
          frequency: "weekly" as const,
          start: "2026-01-01",
          skipWeekend: "yes",
        } as never,
      },
      message: "Invalid weekend setting",
    },
    {
      label: "an unsupported weekend adjustment",
      edit: {
        date: {
          frequency: "weekly" as const,
          start: "2026-01-01",
          weekendSolveMode: "nearest",
        } as never,
      },
      message: "Choose a valid weekend adjustment",
    },
    {
      label: "a malformed repeat pattern",
      edit: {
        date: {
          frequency: "weekly" as const,
          start: "2026-01-01",
          patterns: [{ value: 32, type: "MO" }],
        },
      },
      message: "Choose a valid repeat pattern",
    },
    {
      label: "an unsupported repeat end mode",
      edit: {
        date: {
          frequency: "weekly" as const,
          start: "2026-01-01",
          endMode: "forever",
        } as never,
      },
      message: "Choose a valid repeat end",
    },
    {
      label: "a missing occurrence count",
      edit: {
        date: {
          frequency: "weekly" as const,
          start: "2026-01-01",
          endMode: "after_n_occurrences" as const,
        },
      },
      message: "Occurrence count must be between 1 and 100,000",
    },
    {
      label: "an occurrence count below one",
      edit: {
        date: {
          frequency: "weekly" as const,
          start: "2026-01-01",
          endMode: "after_n_occurrences" as const,
          endOccurrences: 0,
        },
      },
      message: "Occurrence count must be between 1 and 100,000",
    },
    {
      label: "an occurrence count above the maximum",
      edit: {
        date: {
          frequency: "weekly" as const,
          start: "2026-01-01",
          endMode: "after_n_occurrences" as const,
          endOccurrences: 100_001,
        },
      },
      message: "Occurrence count must be between 1 and 100,000",
    },
    {
      label: "an end date before the start date",
      edit: {
        date: {
          frequency: "monthly" as const,
          start: "2026-02-01",
          endMode: "on_date" as const,
          endDate: "2026-01-31",
        },
      },
      message: "The end date must be on or after the start date",
    },
    {
      label: "an end date with the wrong format",
      edit: {
        date: {
          frequency: "monthly" as const,
          start: "2026-02-01",
          endMode: "on_date" as const,
          endDate: "02/28/2026",
        },
      },
      message: "The end date must be on or after the start date",
    },
    {
      label: "an amount range with a single-value operator",
      edit: {
        amountRangeMinor: { num1: 100, num2: 500 },
        amountOp: "is" as const,
      },
      message: "The amount rule does not match the amount values",
    },
    {
      label: "an invalid recurrence date",
      edit: {
        date: { frequency: "monthly" as const, start: "2026-02-30" },
      },
      message: "Choose a valid recurrence and start date",
    },
    {
      label: "a schedule name with a C0 control character",
      edit: { name: "Rent\u0001" },
      message: "Schedule names must not contain control characters",
    },
    {
      label: "a schedule name with a DEL control character",
      edit: { name: "Rent\u007f" },
      message: "Schedule names must not contain control characters",
    },
  ])("rejects $label before calling Actual", async ({ edit, message }) => {
    const fake = createFake();

    await expect(
      fake.api.editSchedule(
        "budget-1",
        "monthly-rent",
        edit as BudgetScheduleEdit,
      ),
    ).rejects.toThrow(message);
    expect(fake.client.updateSchedule).not.toHaveBeenCalled();
  });

  it.each([
    {
      label: "a valid one-time date",
      edit: { date: "2026-11-05" },
      expectedDate: "2026-11-05",
    },
    {
      label: "an amount range with an is-between rule",
      edit: {
        amountRangeMinor: { num1: -500, num2: -100 },
        amountOp: "isbetween" as const,
      },
      expectedAmount: { num1: -500, num2: -100 },
    },
    {
      label: "a recurrence ending after occurrences",
      edit: {
        date: {
          frequency: "weekly" as const,
          start: "2026-01-01",
          endMode: "after_n_occurrences" as const,
          endOccurrences: 4,
          patterns: [{ value: 2, type: "TU" as const }],
        },
      },
      expectedDate: {
        frequency: "weekly",
        start: "2026-01-01",
        endMode: "after_n_occurrences",
        endOccurrences: 4,
        patterns: [{ value: 2, type: "TU" }],
      },
    },
  ])("accepts $label", async ({ edit, expectedAmount, expectedDate }) => {
    const fake = createFake();

    await fake.api.editSchedule(
      "budget-1",
      "monthly-rent",
      edit as BudgetScheduleEdit,
    );

    expect(fake.client.updateSchedule).toHaveBeenCalledWith(
      "monthly-rent",
      expect.objectContaining({
        ...(expectedAmount === undefined ? {} : { amount: expectedAmount }),
        ...(expectedDate === undefined ? {} : { date: expectedDate }),
      }),
      expectedDate !== undefined,
    );
  });

  it("requires a live schedule, open account, and existing payee for schedule edits", async () => {
    const fake = createFake();
    fake.client.getPayees.mockResolvedValue([
      { id: "landlord", name: "Landlord", transfer_acct: "" },
    ]);
    const validEdit = {
      accountId: "destination",
      payeeId: "landlord",
    };

    await expect(
      fake.api.editSchedule("budget-1", "completed", validEdit),
    ).rejects.toThrow("Choose an active schedule in this budget");
    await expect(
      fake.api.editSchedule("budget-1", "monthly-rent", {
        ...validEdit,
        accountId: "closed",
      }),
    ).rejects.toThrow("Choose an open account in this budget");
    await expect(
      fake.api.editSchedule("budget-1", "monthly-rent", {
        ...validEdit,
        payeeId: "missing",
      }),
    ).rejects.toThrow("Choose a payee in this budget");
    expect(fake.client.updateSchedule).not.toHaveBeenCalled();
  });

  it("rejects blank, oversized, missing, and duplicate schedule edits", async () => {
    const fake = createFake();

    await expect(
      fake.api.editSchedule("budget-1", "monthly-rent", { name: " " }),
    ).rejects.toThrow("Schedule names must be between 1 and 100 characters");
    await expect(
      fake.api.editSchedule("budget-1", "monthly-rent", {
        name: "R".repeat(101),
      }),
    ).rejects.toThrow("Schedule names must be between 1 and 100 characters");
    await expect(
      fake.api.editSchedule("budget-1", "missing", { name: "New rent" }),
    ).rejects.toThrow("Choose an active schedule in this budget");
    await expect(
      fake.api.editSchedule("budget-1", "monthly-rent", {
        name: "One time",
      }),
    ).rejects.toThrow("A schedule with this name already exists");
    expect(fake.client.updateSchedule).not.toHaveBeenCalled();
  });

  it("creates, updates, and clears a simple Actual payee category rule", async () => {
    const fake = createFake();
    fake.payees.push({ id: "payee-1", name: "Store", transfer_acct: "" });

    await fake.api.setPayeeDefaultCategory(
      "budget-1",
      "payee-1",
      "expense",
      "secret",
    );
    expect(fake.client.createRule).toHaveBeenCalledWith({
      stage: "default",
      conditionsOp: "and",
      conditions: [{ field: "payee", op: "is", value: "payee-1" }],
      actions: [{ field: "category", op: "set", value: "expense" }],
    });

    fake.rules.push({
      id: "rule-1",
      stage: null,
      conditionsOp: "and",
      conditions: [{ field: "payee", op: "is", value: "payee-1" }],
      actions: [
        { field: "category", op: "set", value: "expense" },
        { field: "notes", op: "set", value: "Keep this note action" },
      ],
    });
    await fake.api.setPayeeDefaultCategory("budget-1", "payee-1", "expense-2");
    expect(fake.client.updateRule).toHaveBeenLastCalledWith({
      ...fake.rules[0],
      actions: [
        { field: "category", op: "set", value: "expense-2" },
        { field: "notes", op: "set", value: "Keep this note action" },
      ],
    });

    await fake.api.setPayeeDefaultCategory("budget-1", "payee-1", null);
    expect(fake.client.updateRule).toHaveBeenLastCalledWith({
      ...fake.rules[0],
      actions: [{ field: "notes", op: "set", value: "Keep this note action" }],
    });
    expect(fake.runBudgetCalls[0]?.options).toEqual({
      encryptionPassword: "secret",
      synchronizeAfterWrite: true,
    });
  });

  it("ignores rules that do not define one simple default category", async () => {
    const fake = createFake();
    fake.payees.push({ id: "payee-1", name: "Store", transfer_acct: "" });
    const condition = { field: "payee", op: "is", value: "payee-1" };
    const categoryAction = { field: "category", op: "set", value: "expense" };
    fake.rules.push(
      {
        id: "pre-stage",
        stage: "pre",
        conditionsOp: "and",
        conditions: [condition],
        actions: [categoryAction],
      },
      {
        id: "or-conditions",
        stage: "default",
        conditionsOp: "or",
        conditions: [condition],
        actions: [categoryAction],
      },
      {
        id: "no-conditions",
        stage: "default",
        conditionsOp: "and",
        conditions: [],
        actions: [categoryAction],
      },
      {
        id: "wrong-field",
        stage: "default",
        conditionsOp: "and",
        conditions: [{ ...condition, field: "amount" }],
        actions: [categoryAction],
      },
      {
        id: "wrong-operator",
        stage: "default",
        conditionsOp: "and",
        conditions: [{ ...condition, op: "contains" }],
        actions: [categoryAction],
      },
      {
        id: "wrong-payee",
        stage: "default",
        conditionsOp: "and",
        conditions: [{ ...condition, value: "another-payee" }],
        actions: [categoryAction],
      },
      {
        id: "no-category-set",
        stage: "default",
        conditionsOp: "and",
        conditions: [condition],
        actions: [
          { field: "category", op: "clear", value: null },
          { field: "notes", op: "set", value: "Keep this note" },
        ],
      },
    );

    await fake.api.setPayeeDefaultCategory("budget-1", "payee-1", "expense");

    expect(fake.client.createRule).toHaveBeenCalledWith({
      stage: "default",
      conditionsOp: "and",
      conditions: [condition],
      actions: [{ field: "category", op: "set", value: "expense" }],
    });
  });

  it("rejects transfer payees, hidden categories, and ambiguous category rules", async () => {
    const fake = createFake();
    fake.payees.push({
      id: "transfer",
      name: "Transfer: Checking",
      transfer_acct: "checking",
    });
    await expect(
      fake.api.setPayeeDefaultCategory("budget-1", "transfer", "expense"),
    ).rejects.toThrow("Choose a regular payee in this budget");

    fake.payees.push({ id: "payee-1", name: "Store", transfer_acct: "" });
    await expect(
      fake.api.setPayeeDefaultCategory("budget-1", "payee-1", "missing"),
    ).rejects.toThrow("Choose an active category in this budget");

    const matchingRule = {
      id: "rule-1",
      stage: null,
      conditionsOp: "and" as const,
      conditions: [{ field: "payee", op: "is", value: "payee-1" }],
      actions: [{ field: "category", op: "set", value: "expense" }],
    };
    fake.rules.push(matchingRule, { ...matchingRule, id: "rule-2" });
    await expect(
      fake.api.setPayeeDefaultCategory("budget-1", "payee-1", "expense-2"),
    ).rejects.toThrow("This payee has multiple category rules");
  });

  it("uses Actual schedule handlers to skip and post scheduled transactions", async () => {
    const fake = createFake();

    await fake.api.skipSchedule("budget-1", "monthly-rent", "2026-11-15");
    await fake.api.postSchedule("budget-1", "monthly-rent", "2026-11-15");

    expect(fake.client.internal.send).toHaveBeenNthCalledWith(
      1,
      "schedule/skip-next-date",
      { id: "monthly-rent" },
    );
    expect(fake.client.internal.send).toHaveBeenNthCalledWith(
      2,
      "schedule/post-transaction",
      { id: "monthly-rent" },
    );
    expect(fake.runBudgetCalls.slice(-2).map(({ options }) => options)).toEqual(
      [{ synchronizeAfterWrite: true }, { synchronizeAfterWrite: true }],
    );

    await expect(
      fake.api.skipSchedule("budget-1", "one-time", "2026-12-01"),
    ).rejects.toThrow("A one-time schedule has no next date to skip");
    await expect(
      fake.api.postSchedule("budget-1", "missing", "2026-11-15"),
    ).rejects.toMatchObject({ code: "schedule_changed" });
    await expect(
      fake.api.skipSchedule("budget-1", "completed", "2026-11-15"),
    ).rejects.toMatchObject({ code: "schedule_changed" });
    expect(fake.client.internal.send).toHaveBeenCalledTimes(2);
  });

  it("rejects a second post from a stale copy after Actual advances the schedule", async () => {
    const fake = createFake();
    fake.client.getSchedules
      .mockResolvedValueOnce([
        {
          id: "monthly-rent",
          completed: false,
          account: "checking",
          next_date: "2026-11-15",
          date: { frequency: "monthly", start: "2026-01-01" },
        },
      ] as never)
      .mockResolvedValueOnce([
        {
          id: "monthly-rent",
          completed: false,
          account: "checking",
          next_date: "2026-12-15",
          date: { frequency: "monthly", start: "2026-01-01" },
        },
      ] as never);

    await fake.api.postSchedule("budget-1", "monthly-rent", "2026-11-15");
    await expect(
      fake.api.postSchedule("budget-1", "monthly-rent", "2026-11-15"),
    ).rejects.toMatchObject({ code: "schedule_changed" });

    expect(fake.client.internal.send).toHaveBeenCalledOnce();
    expect(fake.client.internal.send).toHaveBeenCalledWith(
      "schedule/post-transaction",
      { id: "monthly-rent" },
    );
  });

  it("rejects skipping a date already posted by another client", async () => {
    const fake = createFake();
    fake.client.getSchedules.mockResolvedValueOnce([
      {
        id: "monthly-rent",
        completed: false,
        account: "checking",
        next_date: "2026-12-15",
        date: { frequency: "monthly", start: "2026-01-01" },
      },
    ] as never);

    await expect(
      fake.api.skipSchedule("budget-1", "monthly-rent", "2026-11-15"),
    ).rejects.toMatchObject({ code: "schedule_changed" });

    expect(fake.client.internal.send).not.toHaveBeenCalled();
  });

  it("reports when this Actual client does not expose schedule handlers", async () => {
    const fake = createFake();
    Object.assign(fake.client, { internal: undefined });

    await expect(
      fake.api.skipSchedule("budget-1", "monthly-rent", "2026-11-15"),
    ).rejects.toThrow("Actual schedule actions are unavailable");
    await expect(
      fake.api.postSchedule("budget-1", "monthly-rent", "2026-11-15"),
    ).rejects.toThrow("Actual schedule actions are unavailable");
  });

  it("rejects posting a schedule without an account and deletes only existing schedules", async () => {
    const fake = createFake();
    fake.client.getSchedules.mockResolvedValueOnce([
      {
        id: "no-account",
        date: "2026-12-01",
        name: "No account",
        next_date: "2026-12-01",
      },
    ] as never);

    await expect(
      fake.api.postSchedule("budget-1", "no-account", "2026-12-01"),
    ).rejects.toThrow("Choose an account before posting this schedule");
    await fake.api.deleteSchedule("budget-1", "monthly-rent");
    await expect(
      fake.api.deleteSchedule("budget-1", "missing"),
    ).rejects.toThrow("Schedule does not exist in this budget");
    expect(fake.client.deleteSchedule).toHaveBeenCalledOnce();
  });

  it("sets the selected month's assigned category amount through Actual", async () => {
    const fake = createFake();

    await fake.api.setBudgetAmount("budget-1", "expense", "2026-11", 125_000);

    expect(fake.client.setBudgetAmount).toHaveBeenCalledWith(
      "2026-11",
      "expense",
      125_000,
    );
    expect(fake.runBudgetCalls.at(-1)).toEqual({
      budgetId: "budget-1",
      options: { synchronizeAfterWrite: true },
    });
  });

  it("sets carryover only for a category in the selected budget month", async () => {
    const fake = createFake();

    await fake.api.setBudgetCarryover("budget-1", "expense", "2026-11", true);

    expect(fake.client.setBudgetCarryover).toHaveBeenCalledWith(
      "2026-11",
      "expense",
      true,
    );
    fake.client.getBudgetMonth.mockResolvedValueOnce({
      categoryGroups: [{ categories: [{ id: "other-category" }] }],
    } as never);
    await expect(
      fake.api.setBudgetCarryover("budget-1", "expense", "2026-11", false),
    ).rejects.toThrow("Choose a category in this budget month");
    fake.client.getBudgetMonth.mockResolvedValueOnce({
      categoryGroups: [{}],
    } as never);
    await expect(
      fake.api.setBudgetCarryover("budget-1", "expense", "2026-11", false),
    ).rejects.toThrow("Choose a category in this budget month");
  });

  it("copies prior assignments for active categories in one Actual batch", async () => {
    const fake = createFake();
    fake.client.getBudgetMonth
      .mockResolvedValueOnce({
        categoryGroups: [{ categories: [{ id: "expense", budgeted: 60_000 }] }],
      } as never)
      .mockResolvedValueOnce({
        categoryGroups: [
          { categories: [{ id: "expense" }, { id: "expense-2" }] },
        ],
      } as never);

    await fake.api.copyBudgetMonth("budget-1", "2026-11");

    expect(fake.client.getBudgetMonth).toHaveBeenNthCalledWith(1, "2026-10");
    expect(fake.client.getBudgetMonth).toHaveBeenNthCalledWith(2, "2026-11");
    expect(fake.client.batchBudgetUpdates).toHaveBeenCalledOnce();
    expect(fake.client.setBudgetAmount).toHaveBeenNthCalledWith(
      1,
      "2026-11",
      "expense",
      60_000,
    );
    expect(fake.client.setBudgetAmount).toHaveBeenNthCalledWith(
      2,
      "2026-11",
      "expense-2",
      0,
    );
    await expect(
      fake.api.copyBudgetMonth("budget-1", "2026-00"),
    ).rejects.toThrow("Choose a valid budget month");
    await expect(
      fake.api.copyBudgetMonth("budget-1", "0000-05"),
    ).rejects.toThrow("Choose a valid budget month");
    await expect(
      fake.api.copyBudgetMonth("budget-1", "0001-01"),
    ).rejects.toThrow("Choose a valid budget month");

    const january = createFake();
    january.client.getBudgetMonth
      .mockResolvedValueOnce({
        categoryGroups: [
          {},
          {
            categories: [
              { id: 4, budgeted: 900 },
              { id: "expense", budgeted: null, assigned: 12_000 },
              { id: "expense-2" },
            ],
          },
        ],
      } as never)
      .mockResolvedValueOnce({
        categoryGroups: [
          {},
          { categories: [{ id: "expense" }, { id: "expense-2" }, { id: 4 }] },
        ],
      } as never);

    await january.api.copyBudgetMonth("budget-1", "2026-01");

    expect(january.client.getBudgetMonth).toHaveBeenNthCalledWith(1, "2025-12");
    expect(january.client.setBudgetAmount).toHaveBeenNthCalledWith(
      1,
      "2026-01",
      "expense",
      12_000,
    );
    expect(january.client.setBudgetAmount).toHaveBeenNthCalledWith(
      2,
      "2026-01",
      "expense-2",
      0,
    );
  });

  it("imports statement rows through Actual without rescaling minor units", async () => {
    const fake = createFake();
    const transactions = [
      {
        date: "2026-11-01",
        amountMinor: -1_234,
        payeeName: "Market",
        notes: "Groceries",
        importedId: "statement-row-1",
      },
      {
        date: "2026-11-02",
        amountMinor: 25_000,
        payeeName: "Employer",
        importedId: "statement-row-2",
      },
      {
        date: "2026-11-03",
        amountMinor: 1_500,
        payeeName: "Market",
        importedId: "statement-row-3",
      },
      {
        date: "2026-11-04",
        amountMinor: -12_345,
        payeeName: "Transit",
        importedId: "statement-row-4",
      },
    ];

    const result = await fake.api.importStatementTransactions(
      "budget-1",
      "source",
      transactions,
      "secret",
    );

    expect(result).toEqual({ added: 1, updated: 0, errors: [] });
    expect(fake.client.importTransactions).toHaveBeenCalledWith(
      "source",
      [
        {
          account: "source",
          date: "2026-11-01",
          amount: -1_234,
          payee_name: "Market",
          notes: "Groceries",
          imported_id: "statement-row-1",
        },
        {
          account: "source",
          date: "2026-11-02",
          amount: 25_000,
          payee_name: "Employer",
          notes: undefined,
          imported_id: "statement-row-2",
        },
        {
          account: "source",
          date: "2026-11-03",
          amount: 1_500,
          payee_name: "Market",
          notes: undefined,
          imported_id: "statement-row-3",
        },
        {
          account: "source",
          date: "2026-11-04",
          amount: -12_345,
          payee_name: "Transit",
          notes: undefined,
          imported_id: "statement-row-4",
        },
      ],
      { defaultCleared: false },
    );
    expect(fake.runBudgetCalls.at(-1)?.options).toEqual({
      encryptionPassword: "secret",
      synchronizeAfterWrite: true,
    });

    await expect(
      fake.api.importStatementTransactions("budget-1", "missing", transactions),
    ).rejects.toThrow("Choose an open account");
    await expect(
      fake.api.importStatementTransactions("budget-1", "source", [
        transactions[0]!,
        transactions[0]!,
      ]),
    ).rejects.toThrow("invalid or duplicate");
  });

  it("validates statement size, row contents, and Actual import errors", async () => {
    const row: StatementTransactionImport = {
      date: "2026-11-01",
      amountMinor: -1200,
      payeeName: "Market",
      importedId: "row-1",
    };
    const invalidCases: Array<{
      accountId: string;
      rows: (typeof row)[];
    }> = [
      { accountId: " ", rows: [row] },
      { accountId: "source", rows: [] },
      {
        accountId: "source",
        rows: Array.from({ length: 1001 }, (_, index) => ({
          ...row,
          importedId: `row-${index}`,
        })),
      },
      { accountId: "source", rows: [{ ...row, date: "2026-02-30" }] },
      { accountId: "source", rows: [{ ...row, amountMinor: 0 }] },
      {
        accountId: "source",
        rows: [{ ...row, amountMinor: Number.MAX_SAFE_INTEGER }],
      },
      { accountId: "source", rows: [{ ...row, payeeName: " " }] },
      { accountId: "source", rows: [{ ...row, payeeName: "M".repeat(201) }] },
      { accountId: "source", rows: [{ ...row, notes: "M".repeat(501) }] },
      { accountId: "source", rows: [{ ...row, importedId: " " }] },
      { accountId: "source", rows: [{ ...row, importedId: "i".repeat(201) }] },
      { accountId: "source", rows: [row, row] },
    ];

    for (const invalid of invalidCases) {
      const fake = createFake();
      await expect(
        fake.api.importStatementTransactions(
          "budget-1",
          invalid.accountId,
          invalid.rows,
        ),
      ).rejects.toThrow();
      expect(fake.client.importTransactions).not.toHaveBeenCalled();
    }

    const withErrors = createFake();
    withErrors.client.importTransactions.mockResolvedValueOnce({
      added: ["added"],
      updated: ["updated"] as string[],
      errors: [{ message: "Skipped unsupported row" }],
    });
    await expect(
      withErrors.api.importStatementTransactions("budget-1", "source", [row]),
    ).resolves.toEqual({
      added: 1,
      updated: 1,
      errors: ["Skipped unsupported row"],
    });
  });

  it("rejects statement dates before 1995 and allows far-future dates", async () => {
    const fake = createFake();
    const row: StatementTransactionImport = {
      date: "2062-03-04",
      amountMinor: -1200,
      payeeName: "Market",
      importedId: "row-far-future",
    };

    await fake.api.importStatementTransactions("budget-1", "source", [row]);
    expect(fake.client.importTransactions).toHaveBeenCalledTimes(1);

    const beforeMinimumFake = createFake();
    await expect(
      beforeMinimumFake.api.importStatementTransactions("budget-1", "source", [
        { ...row, date: "1994-12-31", importedId: "row-before-minimum" },
      ]),
    ).rejects.toThrow("date must be on or after 1995-01-01");
    expect(beforeMinimumFake.client.importTransactions).not.toHaveBeenCalled();
  });

  it("clears a sole payee category action and ignores rules without a default category", async () => {
    const fake = createFake();
    fake.payees.push({ id: "payee-1", name: "Store", transfer_acct: "" });

    await expect(
      fake.api.setPayeeDefaultCategory("budget-1", "payee-1", "  "),
    ).rejects.toThrow("Choose a category or clear the default");

    await fake.api.setPayeeDefaultCategory("budget-1", "payee-1", null);
    expect(fake.client.deleteRule).not.toHaveBeenCalled();

    fake.rules.push({
      id: "rule-1",
      stage: null,
      conditionsOp: "and",
      conditions: [{ field: "payee", op: "is", value: "payee-1" }],
      actions: [{ field: "category", op: "set", value: "expense" }],
    });
    await fake.api.setPayeeDefaultCategory("budget-1", "payee-1", null);
    expect(fake.client.deleteRule).toHaveBeenCalledWith("rule-1");
    fake.rules.length = 0;

    fake.rules.push({
      id: "ambiguous-actions",
      stage: null,
      conditionsOp: "and",
      conditions: [{ field: "payee", op: "is", value: "payee-1" }],
      actions: [
        { field: "category", op: "set", value: "expense" },
        { field: "category", op: "set", value: "expense-2" },
      ],
    });
    await expect(
      fake.api.setPayeeDefaultCategory("budget-1", "payee-1", "income"),
    ).rejects.toThrow("multiple category actions");
  });

  it("applies validated template assignments in one Actual batch", async () => {
    const fake = createFake();
    await expect(
      fake.api.applyBudgetTemplate("budget-1", "2026-13", [
        { categoryId: "expense", amountMinor: 1 },
      ]),
    ).rejects.toThrow("Choose a valid budget month");
    await expect(
      fake.api.applyBudgetTemplate("budget-1", "2026-11", []),
    ).rejects.toThrow("1 to 500 categories");
    await expect(
      fake.api.applyBudgetTemplate(
        "budget-1",
        "2026-11",
        Array.from({ length: 501 }, (_, index) => ({
          categoryId: `category-${index}`,
          amountMinor: 0,
        })),
      ),
    ).rejects.toThrow("1 to 500 categories");
    await expect(
      fake.api.applyBudgetTemplate("budget-1", "2026-11", [
        { categoryId: " ", amountMinor: 1 },
      ]),
    ).rejects.toThrow("invalid category amount");
    await expect(
      fake.api.applyBudgetTemplate("budget-1", "2026-11", [
        { categoryId: "expense", amountMinor: 1 },
        { categoryId: "expense", amountMinor: 2 },
      ]),
    ).rejects.toThrow("invalid category amount");
    await expect(
      fake.api.applyBudgetTemplate("budget-1", "2026-11", [
        { categoryId: "expense", amountMinor: 1.5 },
      ]),
    ).rejects.toThrow("invalid category amount");
    await expect(
      fake.api.applyBudgetTemplate("budget-1", "2026-11", [
        { categoryId: "expense", amountMinor: 100_000_000_001 },
      ]),
    ).rejects.toThrow("invalid category amount");

    await fake.api.applyBudgetTemplate(
      "budget-1",
      "2026-11",
      [
        { categoryId: "expense", amountMinor: 12_500 },
        { categoryId: "expense-2", amountMinor: 0 },
      ],
      "secret",
    );
    expect(fake.client.batchBudgetUpdates).toHaveBeenCalledOnce();
    expect(fake.client.setBudgetAmount).toHaveBeenNthCalledWith(
      1,
      "2026-11",
      "expense",
      12_500,
    );
    expect(fake.client.setBudgetAmount).toHaveBeenNthCalledWith(
      2,
      "2026-11",
      "expense-2",
      0,
    );
    expect(fake.runBudgetCalls.at(-1)?.options).toEqual({
      encryptionPassword: "secret",
      synchronizeAfterWrite: true,
    });

    await expect(
      fake.api.applyBudgetTemplate("budget-1", "2026-11", [
        { categoryId: "missing", amountMinor: 1 },
      ]),
    ).rejects.toThrow("no longer active");
    await expect(
      fake.api.applyBudgetTemplate("budget-1", "2026-11", [
        { categoryId: "expense", amountMinor: -1 },
      ]),
    ).rejects.toThrow("invalid category amount");

    fake.client.getBudgetMonth.mockResolvedValueOnce({
      categoryGroups: [{}],
    } as never);
    await expect(
      fake.api.applyBudgetTemplate("budget-1", "2026-11", [
        { categoryId: "expense", amountMinor: 100 },
      ]),
    ).rejects.toThrow("no longer active");
  });

  it("holds only available envelope money and resets the held amount", async () => {
    const fake = createFake();

    await fake.api.holdBudgetForNextMonth(
      "budget-1",
      "2026-11",
      12_500,
      "secret",
    );
    expect(fake.client.holdBudgetForNextMonth).toHaveBeenCalledWith(
      "2026-11",
      12_500,
    );
    expect(fake.runBudgetCalls.at(-1)?.options).toEqual({
      encryptionPassword: "secret",
      synchronizeAfterWrite: true,
    });

    fake.client.getBudgetMonth.mockResolvedValueOnce({
      toBudget: 10_000,
      categoryGroups: [],
    } as never);
    await expect(
      fake.api.holdBudgetForNextMonth("budget-1", "2026-11", 12_500),
    ).rejects.toThrow("The hold cannot exceed the amount ready to assign");
    expect(fake.client.holdBudgetForNextMonth).toHaveBeenCalledOnce();

    fake.client.getPreferences.mockResolvedValueOnce({
      budgetType: "tracking",
    } as never);
    await expect(
      fake.api.holdBudgetForNextMonth("budget-1", "2026-11", 100),
    ).rejects.toThrow("only available in an envelope budget");
    await expect(
      fake.api.holdBudgetForNextMonth("budget-1", "2026-11", 0),
    ).rejects.toThrow("Enter a positive amount");

    fake.client.getBudgetMonth.mockResolvedValueOnce({
      categoryGroups: [],
    } as never);
    await expect(
      fake.api.holdBudgetForNextMonth("budget-1", "2026-11", 1),
    ).rejects.toThrow("The hold cannot exceed the amount ready to assign");

    fake.client.holdBudgetForNextMonth.mockResolvedValueOnce(false);
    await expect(
      fake.api.holdBudgetForNextMonth("budget-1", "2026-11", 1),
    ).rejects.toThrow("There is no money ready to assign in this month");

    await fake.api.resetBudgetHold("budget-1", "2026-11");
    expect(fake.client.resetBudgetHold).toHaveBeenCalledWith("2026-11");
    await expect(
      fake.api.resetBudgetHold("budget-1", "2026-13"),
    ).rejects.toThrow("Choose a valid budget month");
  });

  it("moves category money in one Actual budget batch", async () => {
    const fake = createFake();

    await fake.api.moveMoney(
      "budget-1",
      "2026-11",
      "expense",
      "expense-2",
      10_000,
    );

    expect(fake.client.batchBudgetUpdates).toHaveBeenCalledOnce();
    expect(fake.client.setBudgetAmount).toHaveBeenNthCalledWith(
      1,
      "2026-11",
      "expense",
      40_000,
    );
    expect(fake.client.setBudgetAmount).toHaveBeenNthCalledWith(
      2,
      "2026-11",
      "expense-2",
      30_000,
    );
  });

  it("validates assigned amounts and category membership", async () => {
    const fake = createFake();

    await expect(
      fake.api.setBudgetAmount("budget-1", "expense", "2026-11", -1),
    ).rejects.toThrow("Enter a budget amount up to 1,000,000,000 major units");
    await expect(
      fake.api.setBudgetAmount("budget-1", "missing", "2026-11", 100),
    ).rejects.toThrow("Category does not exist in this budget");
    expect(fake.client.setBudgetAmount).not.toHaveBeenCalled();
  });

  it("rejects category moves with insufficient available money or tracking mode", async () => {
    const fake = createFake();

    await expect(
      fake.api.moveMoney("budget-1", "2026-11", "expense", "expense-2", 40_000),
    ).rejects.toThrow(
      "The source category does not have enough available money",
    );
    fake.client.getPreferences.mockResolvedValueOnce({
      budgetType: "tracking",
    });
    await expect(
      fake.api.moveMoney("budget-1", "2026-11", "expense", "expense-2", 10_000),
    ).rejects.toThrow("Money can only be moved in an envelope budget");
    expect(fake.client.batchBudgetUpdates).not.toHaveBeenCalled();
  });

  it("validates move pairs and reads Actual's alternate amount fields", async () => {
    const fake = createFake();
    const move = (from: string, to: string, amount = 1_000) =>
      fake.api.moveMoney("budget-1", "2026-11", from, to, amount);

    await expect(move("expense", "expense-2", 0)).rejects.toThrow(
      "Enter a positive move amount up to 1,000,000,000 major units",
    );
    await expect(move("expense", "expense")).rejects.toThrow(
      "Choose two different categories",
    );
    await expect(move("expense", "missing")).rejects.toThrow(
      "Choose categories in this budget month",
    );
    fake.client.getBudgetMonth.mockResolvedValueOnce({
      categoryGroups: [
        {
          categories: [
            {
              id: "expense",
              is_income: false,
              budgeted: 0,
              balance: 2_000,
            },
            {
              id: "income",
              is_income: true,
              budgeted: 0,
              balance: 2_000,
            },
          ],
        },
      ],
    } as unknown as Awaited<ReturnType<typeof fake.client.getBudgetMonth>>);
    await expect(move("expense", "income")).rejects.toThrow(
      "Move money between categories of the same type",
    );

    fake.client.getBudgetMonth.mockResolvedValueOnce({
      categoryGroups: [
        {},
        { categories: [null, "bad-row"] },
        {
          categories: [
            {
              id: "expense",
              is_income: false,
              assigned: 30_000,
              available: 20_000,
            },
            {
              id: "expense-2",
              is_income: false,
              assigned: 10_000,
              available: 5_000,
            },
          ],
        },
      ],
    } as unknown as Awaited<ReturnType<typeof fake.client.getBudgetMonth>>);
    await move("expense", "expense-2");
    expect(fake.client.setBudgetAmount).toHaveBeenNthCalledWith(
      1,
      "2026-11",
      "expense",
      29_000,
    );
    expect(fake.client.setBudgetAmount).toHaveBeenNthCalledWith(
      2,
      "2026-11",
      "expense-2",
      11_000,
    );

    fake.client.getBudgetMonth.mockResolvedValueOnce({
      categoryGroups: [
        {
          categories: [
            {
              id: "expense",
              is_income: false,
              budgeted: 10_000,
              balance: "invalid",
            },
            {
              id: "expense-2",
              is_income: false,
              budgeted: 0,
              balance: 0,
            },
          ],
        },
      ],
    } as unknown as Awaited<ReturnType<typeof fake.client.getBudgetMonth>>);
    await expect(move("expense", "expense-2")).rejects.toThrow(
      "Actual returned an invalid source available amount",
    );
    expect(fake.client.batchBudgetUpdates).toHaveBeenCalledOnce();
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
