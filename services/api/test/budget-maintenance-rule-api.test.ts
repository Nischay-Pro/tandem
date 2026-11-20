import { describe, expect, it, vi } from "vitest";
import {
  createBudgetMaintenanceApi,
  validateBudgetRuleDraft,
  type ActualBudgetMaintenanceSession,
  type BudgetRuleDraft,
} from "../src/actual/budget-maintenance-api.js";
import type { BudgetLockPort } from "../src/actual/transaction-reconciler.js";

const draft: BudgetRuleDraft = {
  stage: "default",
  conditionsOp: "and",
  conditions: [
    { field: "payee", op: "oneOf", value: ["payee-1"] },
    { field: "amount", op: "lt", value: -500, options: { outflow: true } },
  ],
  actions: [
    { field: "category", op: "set", value: "category-1" },
    { field: "cleared", op: "set", value: true },
    { op: "prepend-notes", value: "Coffee: " },
  ],
};

function createFake() {
  const client = {
    getPayees: vi.fn(async () => [{ id: "payee-1" }]),
    getAccounts: vi.fn(async () => [{ id: "account-1" }]),
    getCategories: vi.fn(async () => [{ id: "category-1" }]),
    getRules: vi.fn(async () => [{ id: "rule-1" }]),
    createRule: vi.fn(async () => ({ id: "rule-new" })),
    updateRule: vi.fn(async () => ({ id: "rule-1" })),
    deleteRule: vi.fn(async () => true),
  };
  const withBudgetLock = vi.fn(
    async <T>(_budgetId: string, work: () => Promise<T>) => work(),
  );
  const session: ActualBudgetMaintenanceSession = {
    internal: undefined,
    runBudget: async (_budgetId, operation) => operation(),
  };
  const api = createBudgetMaintenanceApi(
    { withBudgetLock } as unknown as BudgetLockPort,
    session,
    client as unknown as Parameters<typeof createBudgetMaintenanceApi>[2],
  );
  return { api, client, withBudgetLock };
}

describe("Actual rule maintenance", () => {
  it("creates, edits, and deletes rules under the budget lock", async () => {
    const fake = createFake();

    await expect(fake.api.createRule("budget-1", draft)).resolves.toBe(
      "rule-new",
    );
    await fake.api.editRule("budget-1", "rule-1", { ...draft, stage: "post" });
    await fake.api.deleteRule("budget-1", "rule-1");

    expect(fake.client.createRule).toHaveBeenCalledWith({
      stage: "default",
      conditionsOp: "and",
      conditions: [
        { field: "payee", op: "oneOf", value: ["payee-1"] },
        {
          field: "amount",
          op: "lt",
          value: -500,
          options: { outflow: true },
        },
      ],
      actions: [
        { field: "category", op: "set", value: "category-1" },
        { field: "cleared", op: "set", value: true },
        { op: "prepend-notes", value: "Coffee: " },
      ],
    });
    expect(fake.client.updateRule).toHaveBeenCalledWith(
      expect.objectContaining({ id: "rule-1", stage: "post" }),
    );
    expect(fake.client.deleteRule).toHaveBeenCalledWith("rule-1");
    expect(fake.withBudgetLock).toHaveBeenCalledTimes(3);
  });

  it("skips the reference lookup for rules that only use text", async () => {
    const fake = createFake();

    await fake.api.createRule("budget-1", {
      stage: "pre",
      conditionsOp: "or",
      conditions: [
        { field: "notes", op: "contains", value: "refund" },
        { field: "amount", op: "isbetween", value: { num1: 100, num2: 900 } },
      ],
      actions: [{ op: "append-notes", value: " (refund)" }],
    });

    expect(fake.client.getPayees).not.toHaveBeenCalled();
    expect(fake.client.createRule).toHaveBeenCalledOnce();
  });

  it("rejects missing rules and references to other budgets", async () => {
    const fake = createFake();

    await expect(
      fake.api.editRule("budget-1", "missing", draft),
    ).rejects.toThrow("Choose a rule in this budget");
    await expect(fake.api.deleteRule("budget-1", "missing")).rejects.toThrow(
      "Choose a rule in this budget",
    );
    await expect(
      fake.api.createRule("budget-1", {
        ...draft,
        actions: [{ field: "category", op: "set", value: "elsewhere" }],
      }),
    ).rejects.toThrow("Choose a category in this budget");
    await expect(
      fake.api.createRule("budget-1", {
        ...draft,
        conditions: [{ field: "account", op: "is", value: "elsewhere" }],
      }),
    ).rejects.toThrow("Choose an account in this budget");
    expect(fake.client.createRule).not.toHaveBeenCalled();
    expect(fake.client.deleteRule).not.toHaveBeenCalled();
  });

  it("validates conditions and actions before touching Actual", () => {
    const invalid: Array<[Record<string, unknown>, string]> = [
      [{ conditions: [] }, "Add at least one condition"],
      [{ actions: [] }, "Add at least one action"],
      [
        { conditions: [{ field: "payee", op: "contains", value: "x" }] },
        "isn’t supported",
      ],
      [
        { conditions: [{ field: "notes", op: "matches", value: "(" }] },
        "valid regular expression",
      ],
      [
        {
          conditions: [
            { field: "amount", op: "isbetween", value: { num1: 5, num2: 1 } },
          ],
        },
        "lower amount",
      ],
      [
        {
          conditions: [
            {
              field: "amount",
              op: "is",
              value: 1,
              options: { inflow: true, outflow: true },
            },
          ],
        },
        "not both",
      ],
      [
        {
          conditions: [
            {
              field: "notes",
              op: "is",
              value: "x",
              options: { inflow: true },
            },
          ],
        },
        "Only amount conditions",
      ],
      [
        { conditions: [{ field: "payee", op: "oneOf", value: [] }] },
        "at least one option",
      ],
      [
        {
          actions: [
            { field: "category", op: "set", value: "a" },
            { field: "category", op: "set", value: "b" },
          ],
        },
        "only be set once",
      ],
      [
        { actions: [{ field: "cleared", op: "set", value: "yes" }] },
        "cleared or uncleared",
      ],
      [
        { actions: [{ field: "notes", op: "append-notes", value: "x" }] },
        "isn’t supported",
      ],
      [
        { actions: [{ op: "append-notes", value: "  " }] },
        "Fill in every condition",
      ],
      [{ actions: [{ op: "set", value: "category-1" }] }, "isn’t supported"],
      [
        { conditions: [{ field: "amount", op: "gt", value: "ten" }] },
        "Enter an amount",
      ],
      [
        { conditions: [{ field: "date", op: "is", value: "2026-11-01" }] },
        "isn’t supported",
      ],
      [
        { conditions: [{ field: "amount", op: "isbetween", value: 5 }] },
        "lower amount",
      ],
      [
        { conditions: [{ field: "amount", op: "isbetween", value: [1, 2] }] },
        "lower amount",
      ],
      [
        {
          conditions: [
            { field: "amount", op: "isbetween", value: { num1: 1.5, num2: 2 } },
          ],
        },
        "lower amount",
      ],
      [
        {
          conditions: [
            { field: "amount", op: "isbetween", value: { num1: 1, num2: 2.5 } },
          ],
        },
        "lower amount",
      ],
      [
        { actions: [{ field: "notes", op: "set", value: "x".repeat(501) }] },
        "Notes are too long",
      ],
    ];
    for (const [change, message] of invalid) {
      expect(() =>
        validateBudgetRuleDraft({ ...draft, ...change } as BudgetRuleDraft),
      ).toThrow(message);
    }
    expect(() =>
      validateBudgetRuleDraft({
        ...draft,
        actions: [{ field: "notes", op: "set", value: "" }],
      }),
    ).not.toThrow();
  });
});
