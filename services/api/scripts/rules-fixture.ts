// Seeds a budget with varied rules and records what Actual's own rule engine (`rules-run`) returns for sample transactions.
import fs from "node:fs";
import process from "node:process";
import * as Actual from "@actual-app/api";

const url = process.env.ACTUAL_URL ?? "http://localhost:5006";
const password = process.env.ACTUAL_PASSWORD ?? "demo-password";
const out = process.argv[2] ?? "rules-cases.json";
const name = "Rules engine parity";

const lib = (await Actual.init({
  serverURL: url,
  password,
  dataDir: fs.mkdtempSync("/tmp/rules-fixture-"),
})) as unknown as {
  send: (name: string, args: unknown) => Promise<unknown>;
};
const existing = (await Actual.getBudgets()).find(
  (b) => b.name === name && b.groupId,
);
if (existing) {
  await Actual.downloadBudget(existing.groupId!);
} else {
  await Actual.runImport(name, async () => {
    await Actual.createAccount({ name: "Checking", offbudget: false }, 0);
  });
  await Actual.downloadBudget(
    (await Actual.getBudgets()).find((b) => b.name === name && b.groupId)!
      .groupId!,
  );
  const checking = (await Actual.getAccounts())[0]!.id;
  const brokerage = await Actual.createAccount(
    { name: "Brokerage", offbudget: true },
    0,
  );
  const group = await Actual.createCategoryGroup({ name: "Spending" });
  const food = await Actual.createCategory({ name: "Food", group_id: group });
  const fun = await Actual.createCategory({ name: "Fun", group_id: group });
  const bills = await Actual.createCategory({ name: "Bills", group_id: group });
  const market = await Actual.createPayee({ name: "Market" });
  const cinema = await Actual.createPayee({ name: "Cinema" });
  const rule = (r: object) =>
    Actual.createRule({ stage: null, conditionsOp: "and", ...r } as never);
  await rule({
    conditions: [{ field: "payee", op: "is", value: market }],
    actions: [{ field: "category", op: "set", value: food }],
  });
  await rule({
    stage: "pre",
    conditions: [{ field: "imported_payee", op: "contains", value: "MKT" }],
    actions: [{ field: "payee", op: "set", value: market }],
  });
  await rule({
    conditions: [
      { field: "imported_payee", op: "matches", value: "^cine(ma|plex)" },
    ],
    actions: [
      { field: "payee", op: "set", value: cinema },
      { field: "category", op: "set", value: fun },
    ],
  });
  await rule({
    conditionsOp: "or",
    conditions: [
      { field: "notes", op: "contains", value: "rent" },
      {
        field: "amount",
        op: "isbetween",
        value: { num1: -200000, num2: -150000 },
      },
    ],
    actions: [
      { field: "category", op: "set", value: bills },
      { op: "prepend-notes", value: "[bill] " },
    ],
  });
  await rule({
    stage: "post",
    conditions: [{ field: "amount", op: "isapprox", value: -1000 }],
    actions: [
      { op: "append-notes", value: " ~10" },
      { field: "cleared", op: "set", value: false },
    ],
  });
  await rule({
    conditions: [{ field: "amount", op: "gt", value: 500000 }],
    actions: [{ field: "payee_name", op: "set", value: "Big Income" }],
  });
  await rule({
    conditions: [{ field: "account", op: "offBudget" }],
    actions: [{ field: "notes", op: "set", value: "off budget" }],
  });
  await rule({
    conditions: [
      { field: "date", op: "is", value: "2026-07" },
      { field: "amount", op: "lt", value: 0 },
    ],
    actions: [{ op: "append-notes", value: " july" }],
  });
  await rule({
    conditions: [
      { field: "category", op: "oneOf", value: [fun, bills] },
      { field: "notes", op: "hasTags", value: "#split" },
    ],
    actions: [
      { field: "category", op: "set", value: food, options: { splitIndex: 1 } },
      {
        op: "set-split-amount",
        value: 50,
        options: { splitIndex: 1, method: "fixed-percent" },
      },
      { field: "category", op: "set", value: fun, options: { splitIndex: 2 } },
      {
        op: "set-split-amount",
        options: { splitIndex: 2, method: "remainder" },
      },
    ],
  });
  await rule({
    conditions: [
      { field: "notes", op: "doesNotContain", value: "keep" },
      { field: "imported_payee", op: "is", value: "drop me" },
    ],
    actions: [{ op: "delete-transaction" }],
  });
  await rule({
    conditions: [
      { field: "category_group", op: "is", value: group },
      { field: "date", op: "gte", value: "2026-12-01" },
    ],
    actions: [{ op: "append-notes", value: " december" }],
  });
  await Actual.createSchedule({
    name: "Gym",
    payee: cinema,
    account: checking,
    amount: -4000,
    amountOp: "is",
    date: {
      frequency: "monthly",
      start: "2026-01-05",
      interval: 1,
      patterns: [],
      skipWeekend: false,
      weekendSolveMode: "after",
      endMode: "never",
    },
  } as never);
  void brokerage;
}
const accounts = await Actual.getAccounts();
const checking = accounts.find((a) => a.name === "Checking")!.id;
const brokerage = accounts.find((a) => a.name === "Brokerage")!.id;
const categories = await Actual.getCategories();
const cat = (n: string) => categories.find((c) => c.name === n)!.id;
const payees = await Actual.getPayees();
const payee = (n: string) => payees.find((p) => p.name === n)!.id;
const schedule = (await Actual.getSchedules())[0]!.id;
const inputs = [
  {
    account: checking,
    date: "2026-05-02",
    amount: -2500,
    payee: payee("Market"),
  },
  {
    account: checking,
    date: "2026-05-03",
    amount: -2500,
    imported_payee: "MKT 1234",
  },
  {
    account: checking,
    date: "2026-05-04",
    amount: -1800,
    imported_payee: "Cineplex 9",
  },
  { account: checking, date: "2026-05-05", amount: -170000, notes: "May" },
  { account: checking, date: "2026-05-06", amount: -900, notes: "coffee" },
  { account: checking, date: "2026-05-07", amount: 900000, notes: "salary" },
  { account: brokerage, date: "2026-05-08", amount: 100 },
  { account: checking, date: "2026-07-09", amount: -100, notes: "rent" },
  {
    account: checking,
    date: "2026-05-10",
    amount: -3000,
    category: cat("Fun"),
    notes: "night #split",
  },
  {
    account: checking,
    date: "2026-05-11",
    amount: -1,
    imported_payee: "DROP ME",
  },
  {
    account: checking,
    date: "2026-05-12",
    amount: -1,
    imported_payee: "drop me",
    notes: "keep",
  },
  {
    account: checking,
    date: "2026-12-12",
    amount: -4000,
    category: cat("Food"),
  },
  {
    account: checking,
    date: "2026-06-05",
    amount: -4000,
    payee: payee("Cinema"),
    schedule,
  },
  {
    account: checking,
    date: "2026-06-06",
    amount: -1500,
    payee: null,
    category: null,
    notes: "#split",
  },
];
const cases = [];
for (const input of inputs) {
  const output = await lib.send("rules-run", {
    transaction: { id: "input", cleared: true, ...input },
  });
  cases.push({ input: { id: "input", cleared: true, ...input }, output });
}
await Actual.sync();
fs.writeFileSync(out, JSON.stringify(cases, null, 2) + "\n");
await Actual.shutdown();
console.log(`${cases.length} cases`);
