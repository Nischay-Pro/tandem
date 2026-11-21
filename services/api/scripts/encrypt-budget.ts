// Creates an end-to-end encrypted budget on an Actual server with Actual's own client, for direct sync tests.
import fs from "node:fs";
import process from "node:process";
import * as Actual from "@actual-app/api";

const url = process.env.ACTUAL_URL ?? "http://localhost:5006";
const password = process.env.ACTUAL_PASSWORD ?? "demo-password";
const budgetPassword = process.env.BUDGET_PASSWORD ?? "budget-secret";
const name = process.argv[2] ?? "Encrypted household";

const lib = (await Actual.init({
  serverURL: url,
  password,
  dataDir: fs.mkdtempSync("/tmp/encrypt-budget-"),
})) as unknown as { send: (name: string, args: unknown) => Promise<unknown> };

const existing = (await Actual.getBudgets()).find(
  (b) => b.name === name && b.groupId,
);
if (existing) {
  console.log(`exists ${existing.groupId}`);
} else {
  await Actual.runImport(name, async () => {
    const checking = await Actual.createAccount(
      { name: "Checking", offbudget: false },
      250_000,
    );
    const group = await Actual.createCategoryGroup({ name: "Everyday" });
    const food = await Actual.createCategory({
      name: "Groceries",
      group_id: group,
    });
    const market = await Actual.createPayee({ name: "Market" });
    await Actual.addTransactions(checking, [
      {
        date: "2026-11-03",
        amount: -4_250,
        payee: market,
        category: food,
        notes: "Weekly shop",
      },
      { date: "2026-11-10", amount: -3_100, payee: market, category: food },
    ]);
  });
  const created = (await Actual.getBudgets()).find(
    (b) => b.name === name && b.groupId,
  )!;
  await Actual.downloadBudget(created.groupId!);
  const result = (await lib.send("key-make", { password: budgetPassword })) as
    { error?: unknown } | undefined;
  if (result?.error) throw new Error(JSON.stringify(result.error));
  await Actual.sync();
  console.log(`encrypted ${created.groupId}`);
}
await Actual.shutdown();
