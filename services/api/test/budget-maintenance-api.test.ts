import { describe, expect, it, vi } from "vitest";
import {
  BANK_SYNC_TIMEOUT_MS,
  createBudgetMaintenanceApi,
} from "../src/actual/budget-maintenance-api.js";
import type { BudgetOperationOptions } from "../src/actual/actual-session.js";
import type { ActualBudgetMaintenanceSession } from "../src/actual/budget-maintenance-api.js";
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
    stage: "pre" | "post" | null;
    conditionsOp: "and" | "or";
    conditions: Array<{ field: string; op: string; value: string }>;
    actions: Array<{ field: string; op: string; value: unknown }>;
  }> = [];
  const schedules: Array<{
    id: string;
    name?: string;
    completed?: boolean;
    account?: string;
    date: string | { frequency: string; start: string };
  }> = [
    {
      id: "monthly-rent",
      name: "Rent",
      completed: false,
      account: "checking",
      date: { frequency: "monthly", start: "2026-01-01" },
    },
    {
      id: "one-time",
      name: "One time",
      completed: false,
      account: "checking",
      date: "2026-12-01",
    },
    {
      id: "completed",
      name: "Completed",
      completed: true,
      account: "checking",
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
  const client = {
    runBankSync: vi.fn(async () => undefined),
    createAccount: vi.fn(async () => "new-account"),
    updateAccount: vi.fn(async () => undefined),
    reopenAccount: vi.fn(async () => undefined),
    getAccountGroups: vi.fn(async () => [{ id: "credit", name: "Credit" }]),
    closeAccount: vi.fn(async () => undefined),
    getAccounts: vi.fn(async () => [
      { id: "source", closed: false },
      { id: "destination", closed: false },
      { id: "closed", closed: true },
    ]),
    getAccountBalance: vi.fn(async () => 0),
    getCategories: vi.fn(async () => [...categories]),
    getCategoryGroups: vi.fn(async () => [
      { id: "group-1", name: "Food", is_income: false, hidden: false },
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
    updateCategory: vi.fn(async (id: string, fields: { name?: string; hidden?: boolean }) => {
      const category = categories.find((candidate) => candidate.id === id);
      if (category && fields.name) category.name = fields.name;
      if (category && fields.hidden !== undefined) category.hidden = fields.hidden;
    }),
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
    await expect(fake.api.moveAccount("budget-1", "source", "closed")).rejects.toThrow(
      "Accounts can only be reordered within the same section",
    );
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

    await expect(fake.api.createCategoryGroup("budget-1", "Bills", false)).resolves.toBe("new-group");
    expect(fake.client.createCategoryGroup).toHaveBeenCalledWith({
      name: "Bills",
      is_income: false,
      hidden: false,
    });
    await fake.api.manageCategoryGroup("budget-1", "group-1", "rename", "Needs");
    expect(fake.client.updateCategoryGroup).toHaveBeenCalledWith("group-1", { name: "Needs" });
    await fake.api.manageCategoryGroup("budget-1", "group-1", "hide");
    expect(fake.client.updateCategoryGroup).toHaveBeenCalledWith("group-1", { hidden: true });
    await fake.api.manageCategoryGroup("budget-1", "group-1", "move", undefined, null);
    expect(fake.client.internal?.send).toHaveBeenCalledWith("category-group-move", {
      id: "group-1",
      targetId: null,
    });

    await fake.api.manageCategory("budget-1", "expense", "hide");
    expect(fake.client.updateCategory).toHaveBeenCalledWith("expense", { hidden: true });
    await fake.api.manageCategory("budget-1", "expense", "move", "expense-2");
    expect(fake.client.internal?.send).toHaveBeenCalledWith("category-move", {
      id: "expense",
      groupId: "group-1",
      targetId: "expense-2",
    });
    await expect(fake.api.manageCategory("budget-1", "expense", "move", "income")).rejects.toThrow(
      "Choose a category in the same group",
    );
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
  ])("rejects $label before calling Actual", async ({ edit, message }) => {
    const fake = createFake();

    await expect(
      fake.api.editSchedule("budget-1", "monthly-rent", edit),
    ).rejects.toThrow(message);
    expect(fake.client.updateSchedule).not.toHaveBeenCalled();
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

  it("rejects transfer payees, hidden categories, and ambiguous category rules", async () => {
    const fake = createFake();
    fake.payees.push({ id: "transfer", name: "Transfer: Checking", transfer_acct: "checking" });
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

    await fake.api.skipSchedule("budget-1", "monthly-rent");
    await fake.api.postSchedule("budget-1", "monthly-rent");

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

    await expect(fake.api.skipSchedule("budget-1", "one-time")).rejects.toThrow(
      "A one-time schedule has no next date to skip",
    );
    await expect(fake.api.postSchedule("budget-1", "missing")).rejects.toThrow(
      "Choose an active schedule in this budget",
    );
    await expect(
      fake.api.skipSchedule("budget-1", "completed"),
    ).rejects.toThrow("Choose an active schedule in this budget");
    expect(fake.client.internal.send).toHaveBeenCalledTimes(2);
  });

  it("reports when this Actual client does not expose schedule handlers", async () => {
    const fake = createFake();
    Object.assign(fake.client, { internal: undefined });

    await expect(
      fake.api.skipSchedule("budget-1", "monthly-rent"),
    ).rejects.toThrow("Actual schedule actions are unavailable");
    await expect(
      fake.api.postSchedule("budget-1", "monthly-rent"),
    ).rejects.toThrow("Actual schedule actions are unavailable");
  });

  it("rejects posting a schedule without an account and deletes only existing schedules", async () => {
    const fake = createFake();
    fake.client.getSchedules.mockResolvedValueOnce([
      {
        id: "no-account",
        date: "2026-12-01",
        name: "No account",
      },
    ] as never);

    await expect(
      fake.api.postSchedule("budget-1", "no-account"),
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
  });

  it("applies validated template assignments in one Actual batch", async () => {
    const fake = createFake();
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
      fake.api.applyBudgetTemplate("budget-1", "2026-11", [{ categoryId: "missing", amountMinor: 1 }]),
    ).rejects.toThrow("no longer active");
    await expect(
      fake.api.applyBudgetTemplate("budget-1", "2026-11", [{ categoryId: "expense", amountMinor: -1 }]),
    ).rejects.toThrow("invalid category amount");
  });

  it("holds only available envelope money and resets the held amount", async () => {
    const fake = createFake();

    await fake.api.holdBudgetForNextMonth("budget-1", "2026-11", 12_500, "secret");
    expect(fake.client.holdBudgetForNextMonth).toHaveBeenCalledWith("2026-11", 12_500);
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

    fake.client.getPreferences.mockResolvedValueOnce({ budgetType: "tracking" } as never);
    await expect(
      fake.api.holdBudgetForNextMonth("budget-1", "2026-11", 100),
    ).rejects.toThrow("only available in an envelope budget");
    await expect(
      fake.api.holdBudgetForNextMonth("budget-1", "2026-11", 0),
    ).rejects.toThrow("Enter a positive amount");

    await fake.api.resetBudgetHold("budget-1", "2026-11");
    expect(fake.client.resetBudgetHold).toHaveBeenCalledWith("2026-11");
    await expect(fake.api.resetBudgetHold("budget-1", "2026-13")).rejects.toThrow(
      "Choose a valid budget month",
    );
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
