import { describe, expect, it, vi } from "vitest";
import {
  ACTUAL_BUDGET_MONTH_READ_CONCURRENCY,
  ACTUAL_TRANSACTION_MAX_ROWS,
  ACTUAL_TRANSACTION_PAGE_SIZE,
  ActualBudgetSnapshotReader,
  createBudgetRecordRevision,
  type ActualBudgetReadApi,
} from "../src/actual/budget-snapshot-reader.js";
import {
  createTransactionRevision,
  snapshotActualTransaction,
} from "../src/actual/transaction-reconciliation.js";
import type { ActualBudgetSessionPort } from "../src/actual/transaction-reconciler.js";

function createQueryBuilder() {
  const builder = {
    select: vi.fn(() => builder),
    options: vi.fn(() => builder),
    orderBy: vi.fn(() => builder),
    limit: vi.fn(() => builder),
    offset: vi.fn(() => builder),
  };
  return builder;
}

function createApi(overrides: Record<string, unknown> = {}) {
  const queries: ReturnType<typeof createQueryBuilder>[] = [];
  const actual = {
    getAccountGroups: vi.fn(async () => [
      { id: "group-account", name: "Cash" },
    ]),
    getAccounts: vi.fn(async () => [{ id: "account-1", name: "Checking" }]),
    getAccountBalance: vi.fn(async () => 125_00),
    getCategories: vi.fn(async () => [
      {
        id: "category-1",
        name: "Groceries",
        is_income: false,
        hidden: false,
        group_id: "category-group-1",
        optional_actual_field: undefined,
      },
    ]),
    getCategoryGroups: vi.fn(async () => [
      {
        id: "category-group-1",
        name: "Daily",
        is_income: false,
        hidden: false,
        categories: [{ id: "category-1", name: "Groceries" }],
      },
    ]),
    getPayees: vi.fn(async () => [
      {
        id: "payee-1",
        name: "Market",
        transfer_acct: null,
        metadata: ["synthetic"],
      },
    ]),
    getBudgetMonths: vi.fn(async () => ["2026-10", "2026-11"]),
    getBudgetMonth: vi.fn(async (month: string) => ({
      month,
      incomeAvailable: 100_00,
      totalBudgeted: 50_00,
      categoryGroups: [],
    })),
    getPreferences: vi.fn(async () => ({})),
    q: vi.fn(() => {
      const query = createQueryBuilder();
      queries.push(query);
      return query;
    }),
    aqlQuery: vi.fn(async () => ({ data: [] as unknown[] })),
    ...overrides,
  } as unknown as ActualBudgetReadApi;
  const runBudget = vi.fn(
    async <T>(_budgetId: string, operation: () => Promise<T>) => operation(),
  );
  const session = { runBudget } as ActualBudgetSessionPort;
  return { actual, queries, runBudget, session };
}

function transactionRow(
  id: string,
  fields: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    id,
    account: "account-1",
    amount: 100,
    date: "2026-11-01",
    ...fields,
  };
}

describe("Actual budget snapshot reader", () => {
  it("reads supported Actual entities and pages grouped transactions", async () => {
    const firstPage = [
      {
        id: "split-parent",
        account: "account-1",
        amount: -1_000,
        date: "2026-11-01",
        is_parent: true,
        raw_synced_data: "private-import-payload",
        subtransactions: [
          {
            id: "split-child",
            account: "account-1",
            amount: -400,
            date: "2026-11-01",
            parent_id: "split-parent",
            category: "category-1",
          },
        ],
      },
      {
        id: "transaction-2",
        account: "account-1",
        amount: -600,
        date: "2026-11-02",
      },
    ];
    const secondPage = [
      {
        id: "transaction-3",
        account: "account-1",
        amount: 200,
        date: "2026-11-03",
      },
    ];
    const fake = createApi({
      aqlQuery: vi
        .fn()
        .mockResolvedValueOnce({ data: firstPage })
        .mockResolvedValueOnce({ data: secondPage }),
    });
    const reader = new ActualBudgetSnapshotReader(fake.session, fake.actual, 2);

    const records = await reader.read("budget-1", "encrypted-budget-password");

    expect(fake.runBudget).toHaveBeenCalledWith(
      "budget-1",
      expect.any(Function),
      { encryptionPassword: "encrypted-budget-password" },
    );
    expect(fake.actual.getCategories).toHaveBeenCalledWith();
    expect(fake.actual.getCategoryGroups).toHaveBeenCalledWith();
    expect(fake.actual.getAccountBalance).toHaveBeenCalledWith("account-1");
    expect(records).toHaveLength(11);
    expect(records).toContainEqual(
      expect.objectContaining({
        type: "account",
        id: "account-1",
        body: expect.objectContaining({ balance_current: 12_500 }),
      }),
    );
    expect(records).toContainEqual(
      expect.objectContaining({
        type: "category-group",
        id: "category-group-1",
        body: expect.not.objectContaining({ categories: expect.anything() }),
      }),
    );
    const splitParent = records.find(
      ({ type, id }) => type === "transaction" && id === "split-parent",
    );
    const splitParentInput = firstPage[0] as Record<string, unknown>;
    const splitChildInput = (
      splitParentInput.subtransactions as Record<string, unknown>[]
    )[0];
    expect(splitParent?.body).not.toHaveProperty("raw_synced_data");
    expect(splitParent?.body).toEqual(
      snapshotActualTransaction(splitParentInput),
    );
    expect(splitParent?.revision).toBe(
      createTransactionRevision(snapshotActualTransaction(splitParentInput)),
    );
    expect(records).toContainEqual(
      expect.objectContaining({
        type: "transaction",
        id: "split-child",
        body: expect.objectContaining({ parentId: "split-parent" }),
      }),
    );
    const splitChild = records.find(
      ({ type, id }) => type === "transaction" && id === "split-child",
    );
    expect(splitChild?.revision).toBe(
      createTransactionRevision(snapshotActualTransaction(splitChildInput)),
    );
    expect(fake.queries).toHaveLength(2);
    for (const [index, query] of fake.queries.entries()) {
      expect(query.select).toHaveBeenCalledWith("*");
      expect(query.options).toHaveBeenCalledWith({ splits: "grouped" });
      expect(query.orderBy).toHaveBeenCalledWith("id");
      expect(query.limit).toHaveBeenCalledWith(2);
      expect(query.offset).toHaveBeenCalledWith(index * 2);
    }
    expect(records).toEqual(
      [...records].sort((left, right) =>
        left.type === right.type
          ? left.id.localeCompare(right.id)
          : left.type.localeCompare(right.type),
      ),
    );
  });

  it("uses the default page and row limits", () => {
    expect(ACTUAL_TRANSACTION_PAGE_SIZE).toBe(2_000);
    expect(ACTUAL_TRANSACTION_MAX_ROWS).toBe(1_000_000);
  });

  it("reads budget months with bounded concurrency while preserving each month", async () => {
    const months = Array.from({ length: 17 }, (_, index) => {
      const month = (index % 12) + 1;
      const year = 2025 + Math.floor(index / 12);
      return `${year}-${String(month).padStart(2, "0")}`;
    });
    let activeReads = 0;
    let peakReads = 0;
    const fake = createApi({
      getBudgetMonths: vi.fn(async () => months),
      getBudgetMonth: vi.fn(async (month: string) => {
        activeReads += 1;
        peakReads = Math.max(peakReads, activeReads);
        await new Promise((resolve) => setTimeout(resolve, 0));
        activeReads -= 1;
        return { month, incomeAvailable: 100, totalBudgeted: 50 };
      }),
    });
    const reader = new ActualBudgetSnapshotReader(fake.session, fake.actual);

    const records = await reader.read("budget-1");
    const monthRecords = records.filter(({ type }) => type === "budget-month");

    expect(ACTUAL_BUDGET_MONTH_READ_CONCURRENCY).toBe(8);
    expect(peakReads).toBeGreaterThan(1);
    expect(peakReads).toBeLessThanOrEqual(ACTUAL_BUDGET_MONTH_READ_CONCURRENCY);
    expect(fake.actual.getBudgetMonth).toHaveBeenCalledTimes(months.length);
    expect(monthRecords.map(({ id }) => id).sort()).toEqual([...months].sort());
  });

  it("publishes Companion-created rows with their stable ID, not Actual's random ID", async () => {
    const stableId = "tandem:8c88495a-65d9-4531-8392-93bff2c65374";
    const fake = createApi({
      aqlQuery: vi.fn(async () => ({
        data: [
          transactionRow("actual-random-id", {
            imported_id: stableId,
            payee: "payee-1",
          }),
        ],
      })),
    });
    const reader = new ActualBudgetSnapshotReader(fake.session, fake.actual);

    const records = await reader.read("budget-1");
    const created = records.find(
      ({ type, id }) => type === "transaction" && id === stableId,
    );

    expect(created?.body).toMatchObject({ id: stableId, importedId: null });
    expect(created?.body).not.toHaveProperty("id", "actual-random-id");
  });

  it("returns Actual currency metadata and supported display preferences", async () => {
    const fake = createApi({
      getPreferences: vi.fn(async () => ({
        "flags.currency": "true",
        defaultCurrencyCode: "CAD",
        numberFormat: "space-comma",
        hideFraction: "true",
        currencySymbolPosition: "after",
        currencySpaceBetweenAmountAndSymbol: "true",
        isPrivacyEnabled: "true",
        privateImportToken: "never-expose-this",
      })),
    });
    const reader = new ActualBudgetSnapshotReader(fake.session, fake.actual);

    await expect(reader.readWithSettings("budget-1")).resolves.toMatchObject({
      displaySettings: {
        currencyCode: "CAD",
        currencySymbol: "CA$",
        currencyFractionDigits: 2,
        numberFormat: "space-comma",
        hideFraction: true,
        currencySymbolPosition: "after",
        currencySpaceBetweenAmountAndSymbol: true,
      },
    });
    expect(fake.actual.getPreferences).toHaveBeenCalledOnce();
  });

  it.each([
    ["CLP", "CLP$", 2, "dot-comma", "before"],
    ["IRR", "﷼", 0, "comma-dot", "after"],
  ])(
    "uses Actual's own %s symbol, scale, number format and position",
    async (
      currencyCode,
      currencySymbol,
      fractionDigits,
      numberFormat,
      position,
    ) => {
      const fake = createApi({
        getPreferences: vi.fn(async () => ({
          "flags.currency": "true",
          defaultCurrencyCode: currencyCode,
        })),
      });
      const reader = new ActualBudgetSnapshotReader(fake.session, fake.actual);

      await expect(reader.readWithSettings("budget-1")).resolves.toMatchObject({
        displaySettings: {
          currencyCode,
          currencySymbol,
          currencyFractionDigits: fractionDigits,
          numberFormat,
          currencySymbolPosition: position,
          currencySpaceBetweenAmountAndSymbol: false,
        },
      });
    },
  );

  it("uses Actual's USD defaults when currency preferences are disabled or unset", async () => {
    const fake = createApi({
      getPreferences: vi.fn(async () => ({
        "flags.currency": "false",
        defaultCurrencyCode: "CAD",
      })),
    });
    const reader = new ActualBudgetSnapshotReader(fake.session, fake.actual);

    await expect(reader.readWithSettings("budget-1")).resolves.toMatchObject({
      displaySettings: {
        currencyCode: "USD",
        currencySymbol: "$",
        currencyFractionDigits: 2,
        numberFormat: null,
        currencySymbolPosition: "before",
        currencySpaceBetweenAmountAndSymbol: false,
      },
    });
  });

  it("falls back to Actual's USD defaults for an unsupported configured currency", async () => {
    const fake = createApi({
      getPreferences: vi.fn(async () => ({
        "flags.currency": true,
        defaultCurrencyCode: "XYZ",
      })),
    });
    const reader = new ActualBudgetSnapshotReader(fake.session, fake.actual);

    await expect(reader.readWithSettings("budget-1")).resolves.toMatchObject({
      displaySettings: {
        currencyCode: "USD",
        currencySymbol: "$",
        currencyFractionDigits: 2,
      },
    });
  });

  it.each([0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1])(
    "rejects invalid query page size %s",
    (pageSize) => {
      const fake = createApi();
      expect(
        () =>
          new ActualBudgetSnapshotReader(fake.session, fake.actual, pageSize),
      ).toThrow("Actual transaction page size must be a positive integer");
    },
  );

  it.each([0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1])(
    "rejects invalid query row limit %s",
    (maxRows) => {
      const fake = createApi();
      expect(
        () =>
          new ActualBudgetSnapshotReader(fake.session, fake.actual, 1, maxRows),
      ).toThrow("Actual transaction row limit must be a positive integer");
    },
  );

  it.each([
    {
      name: "account groups",
      override: { getAccountGroups: async () => null },
      message: "Actual returned invalid account groups",
    },
    {
      name: "accounts",
      override: { getAccounts: async () => null },
      message: "Actual returned invalid accounts",
    },
    {
      name: "categories",
      override: { getCategories: async () => null },
      message: "Actual returned invalid categories",
    },
    {
      name: "category groups",
      override: { getCategoryGroups: async () => null },
      message: "Actual returned invalid category groups",
    },
    {
      name: "payees",
      override: { getPayees: async () => null },
      message: "Actual returned invalid payees",
    },
    {
      name: "months",
      override: { getBudgetMonths: async () => null },
      message: "Actual returned invalid budget months",
    },
  ])("rejects an invalid $name result", async ({ override, message }) => {
    const fake = createApi(override);
    await expect(
      new ActualBudgetSnapshotReader(fake.session, fake.actual).read(
        "budget-1",
      ),
    ).rejects.toThrow(message);
  });

  it.each([
    {
      override: { getAccounts: async () => [null] },
      message: "Actual returned an invalid account",
    },
    {
      override: { getAccounts: async () => [{ id: "  " }] },
      message: "Actual returned an invalid account ID",
    },
    {
      override: { getAccountBalance: async () => Number.MAX_SAFE_INTEGER + 1 },
      message: "Actual returned an invalid account balance",
    },
    {
      override: {
        getAccountGroups: async () => [
          { id: "dup", name: "A" },
          { id: "dup", name: "B" },
        ],
      },
      message: "Actual returned duplicate account-group IDs",
    },
    {
      override: { getBudgetMonths: async () => ["", "2026-11"] },
      message: "Actual returned an invalid budget month",
    },
    {
      override: { getBudgetMonths: async () => ["2026-11", "2026-11"] },
      message: "Actual returned a duplicate budget month",
    },
    {
      override: { getBudgetMonth: async () => ({ month: "2026-10" }) },
      message: "Actual returned data for a different budget month",
    },
    {
      override: {
        getBudgetMonth: async (month: string) => ({
          month,
          id: "unexpected-id",
        }),
      },
      message: "Actual returned a mismatched budget-month ID",
    },
    {
      override: {
        getBudgetMonth: async (month: string) => {
          let reads = 0;
          return Object.defineProperty({}, "month", {
            enumerable: true,
            get: () => (++reads === 1 ? month : "2026-12"),
          });
        },
      },
      message: "Actual returned a mismatched budget month ID",
    },
    {
      override: {
        getAccountGroups: async () => [{ id: "group", name: Number.NaN }],
      },
      message: "Actual returned a non-finite value in account-group",
    },
    {
      override: {
        getAccountGroups: async () => [
          { id: "group", unexpected: () => undefined },
        ],
      },
      message: "Actual returned non-JSON data in account-group",
    },
  ])("rejects malformed Actual data", async ({ override, message }) => {
    const fake = createApi(override);
    await expect(
      new ActualBudgetSnapshotReader(fake.session, fake.actual).read(
        "budget-1",
      ),
    ).rejects.toThrow(message);
  });

  it.each([
    {
      result: null,
      message: "Actual transaction query returned an invalid result",
    },
    {
      result: { data: null },
      message: "Actual transaction query returned an invalid result",
    },
    {
      result: { data: [null] },
      message: "Actual returned an invalid transaction",
    },
    {
      result: {
        data: [transactionRow("parent", { subtransactions: "invalid" })],
      },
      message: "Actual returned invalid transaction splits",
    },
    {
      result: {
        data: [transactionRow("parent", { subtransactions: [null] })],
      },
      message: "Actual returned an invalid split transaction",
    },
  ])("rejects malformed AQL transaction data", async ({ result, message }) => {
    const fake = createApi({ aqlQuery: async () => result });
    await expect(
      new ActualBudgetSnapshotReader(fake.session, fake.actual).read(
        "budget-1",
      ),
    ).rejects.toThrow(message);
  });

  it("rejects query pages larger than requested and transaction row overflow", async () => {
    const tooMany = createApi({
      aqlQuery: async () => ({ data: [{ id: "1" }, { id: "2" }] }),
    });
    await expect(
      new ActualBudgetSnapshotReader(tooMany.session, tooMany.actual, 1).read(
        "budget-1",
      ),
    ).rejects.toThrow(
      "Actual transaction query exceeded the requested page size",
    );

    const overflow = createApi({
      aqlQuery: vi
        .fn()
        .mockResolvedValueOnce({ data: [transactionRow("1")] })
        .mockResolvedValueOnce({ data: [transactionRow("2")] }),
    });
    await expect(
      new ActualBudgetSnapshotReader(
        overflow.session,
        overflow.actual,
        1,
        1,
      ).read("budget-1"),
    ).rejects.toThrow(
      "Actual transaction result exceeded the configured row limit",
    );
  });

  it("rejects duplicate transaction IDs across split rows and query pages", async () => {
    const fake = createApi({
      aqlQuery: vi
        .fn()
        .mockResolvedValueOnce({
          data: [transactionRow("same", { amount: 1 })],
        })
        .mockResolvedValueOnce({
          data: [transactionRow("same", { amount: 2 })],
        })
        .mockResolvedValueOnce({ data: [] }),
    });
    await expect(
      new ActualBudgetSnapshotReader(fake.session, fake.actual, 1).read(
        "budget-1",
      ),
    ).rejects.toThrow("Actual returned duplicate transaction IDs");
  });

  it("hashes JSON objects independent of their key order", () => {
    expect(
      createBudgetRecordRevision({ a: 1, nested: { x: true, y: null } }),
    ).toBe(createBudgetRecordRevision({ nested: { y: null, x: true }, a: 1 }));
  });
});
