// Creates the "Demo household" budget used for app screenshots on a fresh Actual server.
// Actual stores no account kind; Tandem suggests one from each name, so names here say what each account is.
import * as api from "../../services/api/node_modules/@actual-app/api/dist/index.js";
import console from "node:console";
import fs from "node:fs";
import process from "node:process";

const url = process.env.ACTUAL_URL ?? "http://localhost:5006";
const password = process.env.ACTUAL_PASSWORD ?? "demo-password";
const dataDir = process.env.SEED_DATA_DIR ?? "/tmp/tandem-seed";
fs.mkdirSync(dataDir, { recursive: true });

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

const day = (offset) => {
  const d = new Date(Date.now() - offset * 86_400_000);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};
const month = (offset) =>
  day(0).slice(0, 7) === day(offset).slice(0, 7)
    ? day(0).slice(0, 7)
    : day(offset).slice(0, 7);

await api.runImport("Demo household", async () => {
  const checking = await api.createAccount(
    { name: "Everyday checking", offbudget: false },
    325000,
  );
  const savings = await api.createAccount(
    { name: "Savings", offbudget: false },
    1250000,
  );
  const card = await api.createAccount(
    { name: "Credit card", offbudget: false },
    -42000,
  );
  const brokerage = await api.createAccount(
    { name: "Brokerage", offbudget: true },
    2840000,
  );
  for (const account of [checking, savings, card, brokerage]) {
    for (const t of await api.getTransactions(
      account,
      "2000-01-01",
      "2100-01-01",
    )) {
      await api.updateTransaction(t.id, { date: day(75) });
    }
  }

  const categories = {};
  for (const [group, names] of Object.entries({
    Living: ["Rent", "Groceries", "Utilities", "Phone"],
    "Getting around": ["Fuel", "Transit"],
    Fun: ["Dining out", "Entertainment", "Hobbies"],
    Saving: ["Emergency fund", "Holiday"],
  })) {
    const groupId = await api.createCategoryGroup({ name: group });
    for (const name of names)
      categories[name] = await api.createCategory({ name, group_id: groupId });
  }

  const pattern = [
    ["Corner Market", "Groceries", -6420],
    ["City Power", "Utilities", -8900],
    ["Metro Card", "Transit", -3000],
    ["Shell", "Fuel", -5200],
    ["Noodle House", "Dining out", -2850],
    ["Cinema 8", "Entertainment", -1800],
    ["Mobile Co", "Phone", -3500],
  ];
  const history = [];
  for (let i = 2; i < 60; i++) {
    const [payee, category, amount] = pattern[i % pattern.length];
    history.push({
      date: day(i),
      amount: amount - (i % 5) * 37,
      payee_name: payee,
      category: categories[category],
    });
  }
  history.push({
    date: day(3),
    amount: -180000,
    payee_name: "Landlord",
    category: categories.Rent,
  });
  history.push({
    date: day(0),
    amount: -4218,
    payee_name: "Corner Market",
    category: categories.Groceries,
    notes: "Weekly shop",
  });
  history.push({
    date: day(0),
    amount: -540,
    payee_name: "Blue Bottle Coffee",
    category: categories["Dining out"],
  });
  history.push({
    date: day(1),
    amount: -2650,
    payee_name: "Shell",
    category: categories.Fuel,
  });
  await api.addTransactions(checking, history);
  await api.addTransactions(card, [
    {
      date: day(1),
      amount: -1599,
      payee_name: "Spotify",
      category: categories.Entertainment,
    },
    {
      date: day(5),
      amount: -12999,
      payee_name: "Bookshop",
      category: categories.Hobbies,
    },
  ]);

  const assigned = {
    Rent: 180000,
    Groceries: 60000,
    Utilities: 22000,
    Phone: 6000,
    Fuel: 25000,
    Transit: 12000,
    "Dining out": 15000,
    Entertainment: 8000,
    Hobbies: 15000,
    "Emergency fund": 50000,
    Holiday: 30000,
  };
  for (const target of new Set([month(0), month(31)])) {
    for (const [name, amount] of Object.entries(assigned))
      await api.setBudgetAmount(target, categories[name], amount);
  }
});

console.log(JSON.stringify((await api.getBudgets()).map((b) => b.name)));
await api.shutdown();
