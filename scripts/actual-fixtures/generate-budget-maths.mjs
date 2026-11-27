// Builds each budget-maths scenario in a local Actual budget and records what getBudgetMonth reports for every month.
import * as api from "../../services/api/node_modules/@actual-app/api/dist/index.js";
import console from "node:console";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { URL } from "node:url";

const dir = new URL(
  "../../mobile/data/src/jvmTest/resources/actual/budget-maths/",
  import.meta.url,
);
const scenarios = JSON.parse(
  fs.readFileSync(new URL("scenarios.json", dir), "utf8"),
);
const fields = [
  "toBudget",
  "incomeAvailable",
  "lastMonthOverspent",
  "forNextMonth",
  "totalBudgeted",
  "fromLastMonth",
  "totalIncome",
  "totalSpent",
  "totalBalance",
];
const cellFields = ["budgeted", "spent", "received", "balance", "carryover"];

function flatten(month) {
  const out = {};
  for (const field of fields)
    if (month[field] != null) out[field] = month[field];
  for (const group of month.categoryGroups) {
    for (const field of cellFields) {
      if (group[field] != null)
        out[`group:${group.name}:${field}`] = group[field];
    }
    for (const category of group.categories) {
      for (const field of cellFields) {
        if (category[field] != null) {
          out[`category:${category.name}:${field}`] = category[field];
        }
      }
    }
  }
  return Object.fromEntries(
    Object.entries(out).sort(([a], [b]) => a.localeCompare(b)),
  );
}

async function build(scenario) {
  const accounts = {};
  const categories = {};
  for (const account of scenario.accounts) {
    accounts[account.name] = await api.createAccount(
      { name: account.name, offbudget: account.offbudget },
      0,
    );
  }
  const defaults = await api.getCategoryGroups();
  const incomeGroup = defaults.find((g) => g.is_income);
  for (const group of scenario.groups) {
    const id = group.income
      ? incomeGroup.id
      : await api.createCategoryGroup({
          name: group.name,
          hidden: !!group.hidden,
        });
    for (const category of group.categories) {
      categories[category.name] = await api.createCategory({
        name: category.name,
        group_id: id,
        is_income: !!group.income,
        hidden: !!category.hidden,
      });
    }
  }
  for (const category of incomeGroup.categories) {
    await api.deleteCategory(category.id);
  }
  const payees = await api.getPayees();
  const transferPayee = (account) =>
    payees.find((p) => p.transfer_acct === accounts[account]).id;
  for (const [index, t] of scenario.transactions.entries()) {
    const tx = {
      date: t.date,
      amount: t.amount,
      notes: `t${index}`,
      category: t.category ? categories[t.category] : undefined,
      payee: t.transferTo ? transferPayee(t.transferTo) : undefined,
      subtransactions: t.splits?.map((s) => ({
        amount: s.amount,
        category: s.category ? categories[s.category] : undefined,
      })),
    };
    await api.addTransactions(accounts[t.account], [tx], {
      runTransfers: true,
    });
    if (t.deleted) {
      const stored = (
        await api.getTransactions(accounts[t.account], t.date, t.date)
      ).find((s) => s.notes === `t${index}`);
      await api.deleteTransaction(stored.id);
    }
  }
  for (const b of scenario.budgets) {
    await api.setBudgetAmount(b.month, categories[b.category], b.amount);
  }
  for (const c of scenario.carryover) {
    await api.default.internal.send("budget/set-carryover", {
      startMonth: c.from,
      category: categories[c.category],
      flag: c.flag,
    });
  }
}

const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), "budget-maths-"));
await api.init({ dataDir });
const golden = {};
for (const scenario of scenarios) {
  await api.runImport(scenario.name, async () => {
    if (scenario.type === "tracking") {
      await api.setPreference("budgetType", "tracking");
    }
    await build(scenario);
  });
  for (const hold of scenario.holds) {
    await api.holdBudgetForNextMonth(hold.month, hold.amount);
    const held = (await api.getBudgetMonth(hold.month)).forNextMonth;
    if (held !== hold.amount) {
      throw new Error(
        `${scenario.name}: ${hold.month} held ${held}, not ${hold.amount}`,
      );
    }
  }
  golden[scenario.name] = {};
  for (const month of scenario.months) {
    golden[scenario.name][month] = flatten(await api.getBudgetMonth(month));
  }
}
await api.shutdown();
fs.writeFileSync(
  new URL("actual-golden.json", dir),
  JSON.stringify(golden, null, 2) + "\n",
);
console.log(`Recorded ${scenarios.length} scenarios`);
