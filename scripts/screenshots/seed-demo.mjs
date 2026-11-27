// Loads the generated demo household budgets into an Actual server for screenshots and local runs.
import * as api from "../../services/api/node_modules/@actual-app/api/dist/index.js";
import { BUDGETS, demoHash, generateDemo } from "../demo-budget/generate.mjs";
import console from "node:console";
import fs from "node:fs";
import process from "node:process";

const url = process.env.ACTUAL_URL ?? "http://localhost:5006";
const password = process.env.ACTUAL_PASSWORD ?? "demo-password";
const dataDir = process.env.SEED_DATA_DIR ?? "/tmp/tandem-seed";
const today = process.env.DEMO_TODAY || localToday();
const variants = (process.env.DEMO_VARIANTS ?? Object.keys(BUDGETS).join(","))
  .split(",")
  .map((v) => v.trim())
  .filter(Boolean);
fs.mkdirSync(dataDir, { recursive: true });

function localToday() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

const status = await (
  await globalThis.fetch(`${url}/account/needs-bootstrap`)
).json();
if (!status.data?.bootstrapped) {
  await globalThis.fetch(`${url}/account/bootstrap`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ password }),
  });
}
await api.init({ serverURL: url, password, dataDir });

async function sync() {
  for (let attempt = 1; ; attempt++) {
    try {
      return await api.sync();
    } catch (error) {
      if (attempt === 5) throw error;
    }
  }
}

async function load(demo) {
  const accounts = {};
  const categories = {};
  await api.runImport(demo.name, async () => {
    for (const account of demo.accounts) {
      accounts[account.key] = await api.createAccount(
        { name: account.name, offbudget: account.offbudget },
        account.opening,
      );
      for (const t of await api.getTransactions(
        accounts[account.key],
        "2000-01-01",
        "2100-01-01",
      )) {
        await api.updateTransaction(t.id, { date: demo.openingDate });
      }
    }
    const income = (await api.getCategoryGroups()).find((g) => g.is_income);
    for (const group of demo.categoryGroups) {
      const groupId = group.isIncome
        ? income.id
        : await api.createCategoryGroup({ name: group.name });
      for (const name of group.categories)
        categories[name] = await api.createCategory({
          name,
          group_id: groupId,
        });
    }
    for (const tag of demo.tags) await api.createTag(tag);

    const transferPayees = Object.fromEntries(
      (await api.getPayees())
        .filter((p) => p.transfer_acct)
        .map((p) => [p.transfer_acct, p.id]),
    );
    const byAccount = {};
    for (const t of demo.transactions) {
      (byAccount[t.account] ??= []).push({
        date: t.date,
        amount: t.amount,
        ...(t.transfer
          ? { payee: transferPayees[accounts[t.transfer]] }
          : { payee_name: t.payee }),
        ...(t.imported_payee ? { imported_payee: t.imported_payee } : {}),
        category: t.category ? categories[t.category] : null,
        notes: t.notes,
        cleared: t.cleared,
        reconciled: t.reconciled ?? false,
        ...(t.subtransactions
          ? {
              subtransactions: t.subtransactions.map((s) => ({
                amount: s.amount,
                category: categories[s.category],
                notes: s.notes,
              })),
            }
          : {}),
      });
    }
    for (const [key, list] of Object.entries(byAccount))
      await api.addTransactions(accounts[key], list, { runTransfers: true });
  });

  await api.downloadBudget(
    (await api.getBudgets()).find((b) => b.name === demo.name && b.groupId)
      .groupId,
  );
  if (demo.budgetType === "tracking")
    await api.setPreference("budgetType", "tracking");
  for (const b of demo.budget)
    await api.setBudgetAmount(b.month, categories[b.category], b.amount);
  for (const c of demo.carryover)
    await api.setBudgetCarryover(c.month, categories[c.category], true);
  for (const h of demo.holds)
    await api.holdBudgetForNextMonth(h.month, h.amount);

  const payees = Object.fromEntries(
    (await api.getPayees()).map((p) => [p.name, p.id]),
  );
  const resolve = (value) =>
    value?.payee
      ? payees[value.payee]
      : value?.category
        ? categories[value.category]
        : value;
  for (const s of demo.schedules)
    await api.createSchedule({
      ...s,
      account: accounts[s.account],
      payee: payees[s.payee],
    });
  for (const rule of demo.rules)
    await api.createRule({
      ...rule,
      conditions: rule.conditions.map((c) => ({
        ...c,
        value: resolve(c.value),
      })),
      actions: rule.actions.map((a) => ({ ...a, value: resolve(a.value) })),
    });
  await sync();
}

const existing = new Set((await api.getBudgets()).map((b) => b.name));
for (const variant of variants) {
  const demo = generateDemo({ variant, today });
  if (existing.has(demo.name)) {
    console.log(`${demo.name} already exists; skipping`);
    continue;
  }
  await load(demo);
  console.log(
    `${demo.name}: ${demo.transactions.length} transactions through ${today} (sha256 ${demoHash(demo)})`,
  );
}
await api.shutdown();
