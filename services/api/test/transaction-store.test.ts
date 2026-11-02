import * as actualApi from "@actual-app/api";
import { describe, expect, it, vi } from "vitest";
import { createActualTransactionStore } from "../src/actual/transaction-store.js";
import type {
  TransactionCreateInput,
  TransactionPatch,
} from "../src/actual/transaction-reconciliation.js";

function createClient(result: unknown) {
  const queries: unknown[] = [];
  const client = {
    q: actualApi.q,
    aqlQuery: vi.fn(async (query: ReturnType<typeof actualApi.q>) => {
      queries.push(query.serialize());
      return result;
    }),
    addTransactions: vi.fn(async () => "ok" as const),
    updateTransaction: vi.fn(async () => []),
    deleteTransaction: vi.fn(async () => []),
  };
  return { client, queries };
}

describe("Actual transaction store", () => {
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
      updateTransaction: vi.fn(async () => []),
      deleteTransaction: vi.fn(async () => []),
    };
    const store = createActualTransactionStore(client);

    await expect(store.create(id, input)).resolves.toEqual(transaction);
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
      "New transactions require a Companion transaction ID",
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

  it("resolves stable Companion IDs to Actual IDs before update or delete", async () => {
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
