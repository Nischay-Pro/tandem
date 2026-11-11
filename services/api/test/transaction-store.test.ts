import * as actualApi from "@actual-app/api";
import { describe, expect, it, vi } from "vitest";
import { createActualTransactionStore } from "../src/actual/transaction-store.js";
import type {
  TransactionCreateInput,
  TransactionPatch,
} from "../src/actual/transaction-reconciliation.js";

function createClient(
  result: unknown,
  references: {
    accounts?: Array<{ id: string; name: string; closed?: boolean }>;
    categories?: Array<{
      id: string;
      name: string;
      is_income: boolean;
      hidden: boolean;
      group_id: string;
    }>;
    hiddenCategories?: Array<{
      id: string;
      name: string;
      is_income: boolean;
      hidden: boolean;
      group_id: string;
    }>;
    payees?: Array<{ id: string; name: string; transfer_acct: string }>;
  } = {},
) {
  const queries: unknown[] = [];
  const client = {
    q: actualApi.q,
    aqlQuery: vi.fn(async (query: ReturnType<typeof actualApi.q>) => {
      queries.push(query.serialize());
      return result;
    }),
    addTransactions: vi.fn(async () => "ok" as const),
    getAccounts: vi.fn(
      async () =>
        references.accounts ?? [
          { id: "account-1", name: "Checking", closed: false },
        ],
    ),
    getCategories: vi.fn(async (options?: { hidden?: boolean }) =>
      options?.hidden
        ? (references.hiddenCategories ?? [])
        : (references.categories ?? [
            {
              id: "category-1",
              name: "Groceries",
              is_income: false,
              hidden: false,
              group_id: "category-group-1",
            },
          ]),
    ),
    getPayees: vi.fn(
      async () =>
        references.payees ?? [
          { id: "payee-1", name: "Market", transfer_acct: "" },
        ],
    ),
    updateTransaction: vi.fn(async () => []),
    deleteTransaction: vi.fn(async () => []),
  };
  return { client, queries };
}

describe("Actual transaction store", () => {
  it.each([
    [
      "missing account",
      { account: "missing-account", payee: null, category: null },
      {
        code: "account-not-found",
        message: "Account does not exist in this budget",
      },
      {},
    ],
    [
      "closed account",
      { account: "account-1", payee: null, category: null },
      {
        code: "account-closed",
        message: "Choose an open account for this transaction",
      },
      { accounts: [{ id: "account-1", name: "Closed", closed: true }] },
    ],
    [
      "missing payee",
      { account: "account-1", payee: "missing-payee", category: null },
      {
        code: "payee-not-found",
        message: "Payee does not exist in this budget",
      },
      { payees: [] },
    ],
    [
      "missing category",
      { account: "account-1", payee: null, category: "missing-category" },
      {
        code: "category-not-found",
        message: "Category does not exist in this budget",
      },
      { categories: [], hiddenCategories: [] },
    ],
  ])(
    "validates a %s reference without creating a transaction",
    async (_label, refs, issue, references) => {
      const fake = createClient({ data: [] }, references as never);
      const store = createActualTransactionStore(fake.client);
      const input: TransactionCreateInput = {
        account: refs.account,
        amount: -100,
        date: "2026-11-01",
        payee: refs.payee,
        category: refs.category,
        notes: "Synthetic validation case",
        cleared: false,
      };

      await expect(store.validateCreateInput(input)).resolves.toEqual(issue);
      expect(fake.client.addTransactions).not.toHaveBeenCalled();
    },
  );

  it("accepts existing hidden categories and nullable payees", async () => {
    const hiddenCategory = {
      id: "hidden-category",
      name: "Archived",
      is_income: false,
      hidden: true,
      group_id: "category-group-1",
    };
    const fake = createClient(
      { data: [] },
      { categories: [], hiddenCategories: [hiddenCategory] },
    );
    const store = createActualTransactionStore(fake.client);

    await expect(
      store.validateCreateInput({
        account: "account-1",
        amount: -100,
        date: "2026-11-01",
        payee: null,
        category: "hidden-category",
        notes: "Hidden category remains a valid reference",
        cleared: false,
      }),
    ).resolves.toBeUndefined();
    expect(fake.client.getCategories.mock.calls[0]).toEqual([]);
    expect(fake.client.getCategories).toHaveBeenNthCalledWith(2, {
      hidden: true,
    });
  });

  it("accepts existing visible category, account, and payee references", async () => {
    const fake = createClient({ data: [] });
    const store = createActualTransactionStore(fake.client);

    await expect(
      store.validateCreateInput({
        account: "account-1",
        amount: -100,
        date: "2026-11-01",
        payee: "payee-1",
        category: "category-1",
        notes: "Existing references are valid",
        cleared: false,
      }),
    ).resolves.toBeUndefined();
    expect(fake.client.getAccounts).toHaveBeenCalledOnce();
    expect(fake.client.getPayees).toHaveBeenCalledOnce();
    expect(fake.client.getCategories).toHaveBeenCalledOnce();
  });

  it("accepts a valid account when optional payee and category are null", async () => {
    const fake = createClient({ data: [] });
    const store = createActualTransactionStore(fake.client);

    await expect(
      store.validateCreateInput({
        account: "account-1",
        amount: -100,
        date: "2026-11-01",
        payee: null,
        category: null,
        notes: "Optional references can be empty",
        cleared: false,
      }),
    ).resolves.toBeUndefined();
    expect(fake.client.getAccounts).toHaveBeenCalledOnce();
    expect(fake.client.getPayees).not.toHaveBeenCalled();
    expect(fake.client.getCategories).not.toHaveBeenCalled();
  });

  it.each([
    [
      "a removed account",
      { account: "removed-account" },
      {
        code: "account-not-found",
        message: "Account does not exist in this budget",
      },
      { accounts: [] },
    ],
    [
      "a closed account",
      { account: "account-1" },
      {
        code: "account-closed",
        message: "Choose an open account for this transaction",
      },
      { accounts: [{ id: "account-1", name: "Closed", closed: true }] },
    ],
    [
      "a removed payee",
      { payee: "removed-payee" },
      {
        code: "payee-not-found",
        message: "Payee does not exist in this budget",
      },
      { payees: [] },
    ],
    [
      "a removed category",
      { category: "removed-category" },
      {
        code: "category-not-found",
        message: "Category does not exist in this budget",
      },
      { categories: [], hiddenCategories: [] },
    ],
    [
      "a hidden category",
      { category: "hidden-category" },
      {
        code: "category-hidden",
        message: "Choose a visible category for this transaction",
      },
      {
        categories: [],
        hiddenCategories: [
          {
            id: "hidden-category",
            name: "Hidden",
            is_income: false,
            hidden: true,
            group_id: "category-group-1",
          },
        ],
      },
    ],
  ])(
    "validates only changed references and rejects %s",
    async (_label, patch, issue, references) => {
      const fake = createClient({ data: [] }, references as never);
      const store = createActualTransactionStore(fake.client);

      await expect(
        store.validateUpdateInput(patch as TransactionPatch),
      ).resolves.toEqual(issue);
      expect(fake.client.updateTransaction).not.toHaveBeenCalled();
    },
  );

  it("does not revalidate references omitted from an update patch", async () => {
    const fake = createClient(
      { data: [] },
      { accounts: [], payees: [], categories: [] },
    );
    const store = createActualTransactionStore(fake.client);

    await expect(
      store.validateUpdateInput({ amount: -250 }),
    ).resolves.toBeUndefined();
    expect(fake.client.getAccounts).not.toHaveBeenCalled();
    expect(fake.client.getPayees).not.toHaveBeenCalled();
    expect(fake.client.getCategories).not.toHaveBeenCalled();
  });

  it("accepts open accounts and existing payees and visible categories in an update", async () => {
    const fake = createClient({ data: [] });
    const store = createActualTransactionStore(fake.client);

    await expect(
      store.validateUpdateInput({
        account: "account-1",
        payee: "payee-1",
        category: "category-1",
      }),
    ).resolves.toBeUndefined();
    expect(fake.client.getAccounts).toHaveBeenCalledOnce();
    expect(fake.client.getPayees).toHaveBeenCalledOnce();
    expect(fake.client.getCategories).toHaveBeenCalledOnce();
  });

  it("accepts null payee and category references in an update", async () => {
    const fake = createClient({ data: [] });
    const store = createActualTransactionStore(fake.client);

    await expect(
      store.validateUpdateInput({ payee: null, category: null }),
    ).resolves.toBeUndefined();
    expect(fake.client.getPayees).not.toHaveBeenCalled();
    expect(fake.client.getCategories).not.toHaveBeenCalled();
  });

  it("reads by stable ID with grouped split data through public AQL", async () => {
    const transaction = {
      id: "transaction-1",
      account: "account-1",
      amount: -1500,
      date: "2026-10-31",
    };
    const fake = createClient({ data: [transaction] });
    const store = createActualTransactionStore(fake.client);

    await expect(store.read("transaction-1")).resolves.toEqual(transaction);
    expect(fake.queries[0]).toMatchObject({
      table: "transactions",
      filterExpressions: [{ id: "transaction-1" }],
      selectExpressions: ["*"],
      tableOptions: { splits: "grouped" },
    });
  });

  it("finds child IDs nested in Actual's grouped split result", async () => {
    const child = {
      id: "split-child",
      parent_id: "split-parent",
      is_child: true,
      amount: -200,
    };
    const parent = {
      id: "split-parent",
      is_parent: true,
      subtransactions: [child],
    };
    const store = createActualTransactionStore(
      createClient({ data: [parent] }).client,
    );

    await expect(store.read("split-child")).resolves.toEqual(child);
    await expect(store.read("split-parent")).resolves.toEqual(parent);
  });

  it("rejects invalid grouped subtransaction data", async () => {
    const invalidList = createActualTransactionStore(
      createClient({ data: [{ id: "parent", subtransactions: {} }] }).client,
    );
    await expect(invalidList.read("child")).rejects.toThrow(
      "Actual transaction query returned invalid subtransactions",
    );

    const invalidChild = createActualTransactionStore(
      createClient({ data: [{ id: "parent", subtransactions: [null] }] })
        .client,
    );
    await expect(invalidChild.read("child")).rejects.toThrow(
      "Actual transaction query returned an invalid subtransaction",
    );
  });

  it("returns undefined for a missing visible transaction", async () => {
    const fake = createClient({ data: [] });
    const store = createActualTransactionStore(fake.client);

    await expect(store.read("missing-transaction")).resolves.toBeUndefined();
  });

  it("ignores other returned IDs and returns the matching row", async () => {
    const matching = { id: "target", amount: -100 };
    const fake = createClient({ data: [{ id: "other" }, matching] });
    const store = createActualTransactionStore(fake.client);

    await expect(store.read("target")).resolves.toEqual(matching);
  });

  it.each([null, {}, { data: "invalid" }])(
    "rejects malformed AQL results: %j",
    async (result) => {
      const store = createActualTransactionStore(createClient(result).client);
      await expect(store.read("transaction-1")).rejects.toThrow(
        "Actual transaction query returned an invalid result",
      );
    },
  );

  it("rejects malformed rows and duplicate stable IDs", async () => {
    const invalidRow = createActualTransactionStore(
      createClient({ data: [null] }).client,
    );
    await expect(invalidRow.read("transaction-1")).rejects.toThrow(
      "Actual transaction query returned an invalid row",
    );

    const duplicateRows = createActualTransactionStore(
      createClient({
        data: [{ id: "transaction-1" }, { id: "transaction-1" }],
      }).client,
    );
    await expect(duplicateRows.read("transaction-1")).rejects.toThrow(
      "Actual transaction query returned duplicate IDs",
    );
  });

  it("rejects primitive and callable AQL rows", async () => {
    for (const row of [1, "not-a-row", true, () => "not-a-row"]) {
      const store = createActualTransactionStore(
        createClient({ data: [row] }).client,
      );
      await expect(store.read("transaction-1")).rejects.toThrow(
        "Actual transaction query returned an invalid row",
      );
    }
  });

  it("delegates validated updates and deletes to the public API methods", async () => {
    const fake = createClient({ data: [] });
    const store = createActualTransactionStore(fake.client);
    const patch: TransactionPatch = { amount: -1200, notes: "" };

    await expect(store.update("transaction-1", patch)).resolves.toEqual([]);
    await expect(store.delete("transaction-1")).resolves.toEqual([]);
    expect(fake.client.updateTransaction).toHaveBeenCalledWith(
      "transaction-1",
      patch,
    );
    expect(fake.client.deleteTransaction).toHaveBeenCalledWith("transaction-1");
  });

  it("creates with a stable imported ID through Actual's public API", async () => {
    const id = "tandem:8c88495a-65d9-4531-8392-93bff2c65374";
    const input: TransactionCreateInput = {
      account: "account-1",
      amount: -1250,
      date: "2026-11-01",
      payee: "payee-1",
      category: "category-1",
      notes: "Lunch",
      cleared: false,
    };
    const queries: unknown[] = [];
    let transaction: Record<string, unknown> | undefined;
    const client = {
      q: actualApi.q,
      aqlQuery: vi.fn(async (query: ReturnType<typeof actualApi.q>) => {
        queries.push(query.serialize());
        return { data: transaction ? [transaction] : [] };
      }),
      addTransactions: vi.fn(
        async (
          accountId: string,
          rows: Array<{
            date: string;
            amount?: number;
            payee?: string | null;
            category?: string;
            notes?: string;
            cleared?: boolean;
            imported_id?: string;
          }>,
        ) => {
          transaction = {
            id: "actual-generated-id",
            account: accountId,
            ...rows[0],
          };
          return "ok" as const;
        },
      ),
      getAccounts: vi.fn(async () => [
        { id: "account-1", name: "Checking", closed: false },
      ]),
      getCategories: vi.fn(async () => [
        {
          id: "category-1",
          name: "Groceries",
          is_income: false,
          hidden: false,
          group_id: "category-group-1",
        },
      ]),
      getPayees: vi.fn(async () => [
        { id: "payee-1", name: "Market", transfer_acct: "" },
      ]),
      updateTransaction: vi.fn(async () => []),
      deleteTransaction: vi.fn(async () => []),
    };
    const store = createActualTransactionStore(client);

    await expect(store.create(id, input)).resolves.toMatchObject({
      id: "actual-generated-id",
      account: "account-1",
      imported_id: id,
    });
    expect(client.addTransactions).toHaveBeenCalledWith(
      "account-1",
      [
        {
          date: "2026-11-01",
          amount: -1250,
          payee: "payee-1",
          category: "category-1",
          notes: "Lunch",
          cleared: false,
          imported_id: id,
        },
      ],
      { runTransfers: false, learnCategories: false },
    );
    expect(queries[0]).toMatchObject({
      filterExpressions: [{ imported_id: id }],
      tableOptions: { splits: "grouped" },
    });
  });

  it("uses Actual's transfer payee hook to create both linked legs", async () => {
    const id = "tandem:8c88495a-65d9-4531-8392-93bff2c65375";
    const outgoing = {
      id: "actual-transfer-out",
      account: "account-1",
      amount: -1250,
      date: "2026-11-01",
      payee: "transfer-payee",
      imported_id: id,
      transfer_id: "actual-transfer-in",
    };
    const fake = createClient(
      { data: [outgoing] },
      {
        payees: [
          {
            id: "transfer-payee",
            name: "Transfer: Savings",
            transfer_acct: "account-2",
          },
        ],
      },
    );
    const store = createActualTransactionStore(fake.client);
    const input: TransactionCreateInput = {
      account: "account-1",
      amount: -1250,
      date: "2026-11-01",
      payee: "transfer-payee",
      category: null,
      notes: "Move to savings",
      cleared: false,
    };

    await expect(store.validateCreateInput(input)).resolves.toBeUndefined();

    await expect(store.create(id, input)).resolves.toEqual(outgoing);
    expect(fake.client.getPayees).toHaveBeenCalledOnce();
    expect(fake.client.addTransactions).toHaveBeenCalledWith(
      "account-1",
      [
        {
          date: "2026-11-01",
          amount: -1250,
          payee: "transfer-payee",
          notes: "Move to savings",
          cleared: false,
          imported_id: id,
        },
      ],
      { runTransfers: true, learnCategories: false },
    );
  });

  it("rejects transaction creation without a stable ID or Actual read-back", async () => {
    const id = "tandem:8c88495a-65d9-4531-8392-93bff2c65374";
    const input: TransactionCreateInput = {
      account: "account-1",
      amount: -1250,
      date: "2026-11-01",
      payee: null,
      category: null,
      notes: "",
      cleared: false,
    };
    const missingReadBack = createActualTransactionStore(
      createClient({ data: [] }).client,
    );

    await expect(missingReadBack.create(id, input)).rejects.toThrow(
      "Actual did not return the created transaction",
    );
    await expect(missingReadBack.create("unstable-id", input)).rejects.toThrow(
      "New transactions require a Tandem transaction ID",
    );
  });

  it("rejects writes when a stable imported ID no longer resolves", async () => {
    const id = "tandem:8c88495a-65d9-4531-8392-93bff2c65374";
    const store = createActualTransactionStore(
      createClient({ data: [] }).client,
    );

    await expect(store.update(id, { amount: -1200 })).rejects.toThrow(
      "Actual transaction no longer exists",
    );
    await expect(store.delete(id)).rejects.toThrow(
      "Actual transaction no longer exists",
    );
  });

  it("resolves stable Tandem IDs to Actual IDs before update or delete", async () => {
    const id = "tandem:8c88495a-65d9-4531-8392-93bff2c65374";
    const actual = { id: "actual-generated-id", imported_id: id };
    const fake = createClient({ data: [actual] });
    const store = createActualTransactionStore(fake.client);

    await store.update(id, { amount: -1200 });
    await store.delete(id);

    expect(fake.client.updateTransaction).toHaveBeenCalledWith(
      "actual-generated-id",
      { amount: -1200 },
    );
    expect(fake.client.deleteTransaction).toHaveBeenCalledWith(
      "actual-generated-id",
    );
  });
});
