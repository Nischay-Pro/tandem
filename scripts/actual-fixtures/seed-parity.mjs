// Seeds budgets on a local Actual server that exercise the accounting rules Tandem's on-device engine must match.
import console from "node:console";
import fs from "node:fs";
import process from "node:process";
import * as api from "../../services/api/node_modules/@actual-app/api/dist/index.js";

const url = process.env.ACTUAL_URL ?? "http://localhost:5006";
const password = process.env.ACTUAL_PASSWORD ?? "demo-password";
const dataDir = fs.mkdtempSync("/tmp/parity-seed-");

const now = new Date();
const date = (monthOffset, day) => {
  const d = new Date(
    Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + monthOffset, day),
  );
  return d.toISOString().slice(0, 10);
};
const month = (monthOffset) => date(monthOffset, 1).slice(0, 7);

await api.init({ serverURL: url, password, dataDir });

async function seed(name, tracking) {
  let ids = {};
  await api.runImport(name, async () => {
    const checking = await api.createAccount(
      { name: "Checking", offbudget: false },
      500000,
    );
    const savings = await api.createAccount(
      { name: "Savings", offbudget: false },
      200000,
    );
    const card = await api.createAccount(
      { name: "Card", offbudget: false },
      -30000,
    );
    const brokerage = await api.createAccount(
      { name: "Brokerage", offbudget: true },
      900000,
    );
    const old = await api.createAccount(
      { name: "Old wallet", offbudget: false },
      12345,
    );
    for (const account of [checking, savings, card, brokerage, old]) {
      for (const t of await api.getTransactions(
        account,
        "2000-01-01",
        "2100-01-01",
      )) {
        await api.updateTransaction(t.id, { date: date(-3, 1) });
      }
    }
    const income = (await api.getCategoryGroups()).find((g) => g.is_income);
    const salary = await api.createCategory({
      name: "Salary",
      group_id: income.id,
    });
    const bills = await api.createCategoryGroup({ name: "Bills" });
    const everyday = await api.createCategoryGroup({ name: "Everyday" });
    const archive = await api.createCategoryGroup({
      name: "Archive",
      hidden: true,
    });
    const categories = {
      rent: await api.createCategory({ name: "Rent", group_id: bills }),
      power: await api.createCategory({ name: "Power", group_id: bills }),
      groceries: await api.createCategory({
        name: "Groceries",
        group_id: everyday,
      }),
      dining: await api.createCategory({ name: "Dining", group_id: everyday }),
      misc: await api.createCategory({
        name: "Misc",
        group_id: everyday,
        hidden: true,
      }),
      oldCat: await api.createCategory({
        name: "Old category",
        group_id: archive,
      }),
    };
    ids = {
      checking,
      savings,
      card,
      brokerage,
      old,
      salary,
      bills,
      everyday,
      archive,
      ...categories,
    };
    const tx = [];
    for (let m = -3; m <= 0; m++) {
      tx.push({
        date: date(m, 1),
        amount: 400000,
        payee_name: "Employer",
        category: salary,
      });
      tx.push({
        date: date(m, 2),
        amount: -150000,
        payee_name: "Landlord",
        category: categories.rent,
      });
      tx.push({
        date: date(m, 5),
        amount: -9000 - m * 100,
        payee_name: "Power Co",
        category: categories.power,
      });
      tx.push({
        date: date(m, 9),
        amount: -42000,
        payee_name: "Market",
        category: categories.groceries,
        cleared: true,
      });
      tx.push({
        date: date(m, 12),
        amount: -m * -5000 - 7000,
        payee_name: "Bistro",
        category: categories.dining,
      });
    }
    tx.push({
      date: date(-2, 15),
      amount: -1999,
      payee_name: "Corner shop",
      category: categories.misc,
    });
    tx.push({
      date: date(-1, 20),
      amount: -3000,
      payee_name: "Archive store",
      category: categories.oldCat,
    });
    tx.push({
      date: date(-1, 21),
      amount: -2500,
      payee_name: "Mystery",
      notes: "uncategorized",
    });
    await api.addTransactions(checking, tx);
    await api.addTransactions(card, [
      {
        date: date(-1, 3),
        amount: -88000,
        payee_name: "Bistro",
        category: categories.dining,
      },
      {
        date: date(0, 4),
        amount: -1500,
        payee_name: "Market",
        category: categories.groceries,
      },
    ]);
    await api.addTransactions(brokerage, [
      { date: date(-1, 10), amount: 5000, payee_name: "Dividend" },
    ]);
  });

  await api.downloadBudget(
    (await api.getBudgets()).find((b) => b.name === name && b.groupId).groupId,
  );
  if (tracking) await api.setPreference?.("budgetType", "tracking");

  const transferPayee = (await api.getPayees()).find(
    (p) => p.transfer_acct === ids.savings,
  );
  await api.addTransactions(ids.checking, [
    {
      date: date(-1, 25),
      amount: -25000,
      payee: transferPayee.id,
      notes: "to savings",
    },
    {
      date: date(0, 6),
      amount: -12000,
      payee_name: "Superstore",
      subtransactions: [
        { amount: -8000, category: ids.groceries, notes: "food" },
        { amount: -4000, category: ids.power },
      ],
    },
    {
      date: date(0, 7),
      amount: -777,
      payee_name: "Deleted later",
      category: ids.dining,
    },
  ]);
  const deleted = (
    await api.getTransactions(ids.checking, date(0, 7), date(0, 7))
  ).find((t) => t.amount === -777);
  await api.deleteTransaction(deleted.id);
  const edited = (
    await api.getTransactions(ids.checking, date(0, 9), date(0, 9))
  )[0];
  await api.updateTransaction(edited.id, {
    amount: -43210,
    notes: "edited after import",
    reconciled: true,
  });

  for (let m = -3; m <= 1; m++) {
    await api.setBudgetAmount(month(m), ids.rent, 150000);
    await api.setBudgetAmount(month(m), ids.power, 10000);
    await api.setBudgetAmount(month(m), ids.groceries, 40000 + m * 1000);
    await api.setBudgetAmount(month(m), ids.dining, 10000);
    if (tracking) await api.setBudgetAmount(month(m), ids.salary, 390000);
  }
  await api.setBudgetAmount(month(-2), ids.oldCat, 5000);
  await api.setBudgetCarryover(month(-2), ids.groceries, true);
  await api.setBudgetCarryover(month(-1), ids.groceries, true);
  await api.setBudgetCarryover(month(-1), ids.salary, true);
  if (!tracking) await api.holdBudgetForNextMonth(month(-1), 20000);

  const payees = await api.getPayees();
  const bistro = payees.find((p) => p.name === "Bistro");
  const market = payees.find((p) => p.name === "Corner shop");
  await api.mergePayees(market.id, [
    payees.find((p) => p.name === "Archive store").id,
  ]);
  await api.updatePayee(bistro.id, { name: "Bistro & Bar" });
  await api.deleteCategory(ids.misc, ids.groceries);
  await api.updateCategory(ids.power, { name: "Electricity" });
  await api.createRule({
    stage: null,
    conditionsOp: "and",
    conditions: [{ op: "is", field: "payee", value: bistro.id }],
    actions: [{ op: "set", field: "category", value: ids.dining }],
  });
  await api.createSchedule({
    name: "Rent",
    payee: payees.find((p) => p.name === "Landlord").id,
    account: ids.checking,
    amount: -150000,
    amountOp: "is",
    date: {
      frequency: "monthly",
      start: date(-3, 2),
      interval: 1,
      patterns: [],
      skipWeekend: false,
      weekendSolveMode: "after",
      endMode: "never",
    },
  });
  await api.closeAccount(ids.old, ids.checking);
  await api.updateAccount(ids.card, { name: "Credit card" });
  await api.sync();
}

await seed("Parity envelope", false);
await seed("Parity tracking", true);
console.log(JSON.stringify((await api.getBudgets()).map((b) => b.name)));
await api.shutdown();
