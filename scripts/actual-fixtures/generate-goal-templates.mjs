// Builds budgets whose category notes hold goal templates, exports each one, then records what Actual's own
// "Apply budget templates" writes month by month. Run with TZ=UTC from the repository root.
import console from "node:console";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import process from "node:process";
import { URL } from "node:url";

if (process.env.TZ !== "UTC") throw new Error("Run with TZ=UTC");

const TODAY = "2026-11-15";
const RealDate = Date;
const started = RealDate.now();
const fixed = RealDate.parse(`${TODAY}T12:00:00Z`);
globalThis.Date = class extends RealDate {
  constructor(...args) {
    if (args.length === 0) super(fixed + (RealDate.now() - started));
    else super(...args);
  }

  static now() {
    return fixed + (RealDate.now() - started);
  }
};

const api =
  await import("../../services/api/node_modules/@actual-app/api/dist/index.js");
const send = (name, args) => api.default.internal.send(name, args);
const dir = new URL(
  "../../mobile/data/src/jvmTest/resources/actual/goal-templates/",
  import.meta.url,
);

const monthly = (start, interval = 1) => ({
  frequency: "monthly",
  start,
  interval,
  endMode: "never",
});

const spending = (category, amounts, day = 12) =>
  amounts.map((amount, i) => ({
    date: `2026-${String(5 + i).padStart(2, "0")}-${String(day).padStart(2, "0")}`,
    amount: -amount,
    category,
  }));

const salary = (amount) =>
  [5, 6, 7, 8, 9, 10, 11].map((m) => ({
    date: `2026-${String(m).padStart(2, "0")}-01`,
    amount,
    category: "Salary",
  }));

const scenarios = [
  {
    name: "envelope",
    groups: [
      { name: "Income", income: true, categories: ["Salary", "Bonus"] },
      {
        name: "Bills",
        categories: ["Rent", "Phone", "Insurance", "Streaming", "Water"],
      },
      {
        name: "Everyday",
        categories: ["Groceries", "Dining", "Fuel", "Coffee", "Gym"],
      },
      {
        name: "Goals",
        categories: ["Holiday", "Car", "Gifts", "Emergency", "Annual"],
      },
      {
        name: "Savings",
        categories: [
          "Invest",
          "Rainy",
          "Spare",
          "Biweekly",
          "Kids",
          "Nest egg",
          "Copycat",
          "Messy",
          "Orphan",
          "Extra",
          "Misc",
        ],
      },
      { name: "Archive", categories: [{ name: "Hidden", hidden: true }] },
    ],
    transactions: [
      ...salary(500000),
      { date: "2026-10-15", amount: 20000, category: "Bonus" },
      ...spending("Rent", [120000, 120000, 120000, 120000, 120000, 120000], 2),
      ...spending(
        "Groceries",
        [35210, 41890, 38000, 52125, 30500, 44000, 12000],
      ),
      ...spending("Dining", [8000, 12500, 9900, 11000, 7300, 10450]),
      ...spending("Fuel", [6000, 5400, 6600, 7000, 6100, 2000]),
      ...spending("Coffee", [2500, 3100, 2800, 2600, 2900, 1200]),
      ...spending("Phone", [4500, 4500, 4500, 4500, 4500, 4500], 10),
      ...spending("Streaming", [1599, 1599, 1599, 1599, 1599, 1599], 20),
      ...spending("Gifts", [0, 0, 0, 0, 4000], 18),
    ],
    budgets: [
      ...["2026-05", "2026-06", "2026-07", "2026-08"].flatMap((month) => [
        { month, category: "Rent", amount: 120000 },
        { month, category: "Groceries", amount: 40000 },
        { month, category: "Dining", amount: 10000 },
        { month, category: "Gifts", amount: 5000 },
        { month, category: "Copycat", amount: 7700 },
        { month, category: "Phone", amount: 4500 },
      ]),
      { month: "2026-07", category: "Copycat", amount: 12345 },
    ],
    carryover: [{ category: "Groceries", from: "2026-05" }],
    schedules: [
      { name: "Phone bill", amount: -4500, date: monthly("2026-01-10") },
      {
        name: "Car insurance",
        amount: -60000,
        date: {
          frequency: "yearly",
          start: "2027-02-01",
          interval: 1,
          endMode: "never",
        },
      },
      { name: "Streaming", amount: -1599, date: monthly("2026-01-20") },
      {
        name: "Gym fee",
        amount: -1000,
        date: {
          frequency: "weekly",
          start: "2026-10-02",
          interval: 2,
          endMode: "never",
        },
      },
      {
        name: "Water",
        amount: { num1: -3000, num2: -5000 },
        amountOp: "isbetween",
        date: monthly("2026-12-05", 2),
      },
    ],
    notes: {
      Salary: "#template 5000",
      Rent: "#template 1200",
      Phone: "#template schedule Phone bill",
      Insurance: "#template schedule Car insurance",
      Streaming:
        "Netflix plus tax\n#template schedule full Streaming [increase 10%]",
      Water: "#template schedule Water [decrease 5]",
      Groceries: "#template 400 up to 600",
      Dining: "#template average 3 months [increase 10%]",
      Fuel: "#template up to 5 per day",
      Coffee: "#template up to 20 per week starting 2026-01-05 hold",
      Gym: "#template schedule Gym fee",
      Holiday: "#template 1500 by 2027-06 repeat every year",
      Car: "#template-2 3000 by 2027-03\n#template-2 600 by 2026-12 repeat every 6 months",
      Gifts: "#template 300 by 2026-12 spend from 2026-09 repeat every year",
      Emergency: "#goal 5000\n#template 200",
      Annual: "#template 120 repeat every year starting 2027-01-15",
      Invest: "#template-3 10% of all income",
      Rainy: "#template-3 5% of previous Salary",
      Spare: "#template-4 5% of available funds",
      Biweekly:
        "#template 30 repeat every 2 weeks starting 2026-10-02 up to 100",
      Kids: "Pocket money\n\n#template-1 50.25 up to 100",
      "Nest egg": "#goal 1000",
      Copycat: "#template copy from 2 months ago",
      Messy: "#template garbage words\n#template 10",
      Orphan: "#template 50",
      Extra: "#template remainder 2 up to 300",
      Misc: "#template remainder",
      Hidden: "#template 999",
    },
    steps: [
      { check: true },
      { apply: "2026-09" },
      { apply: "2026-10" },
      { note: "Orphan", text: "no more templates" },
      { apply: "2026-11" },
      { overwrite: "2026-11" },
      { set: "Nest egg", month: "2026-12", amount: 25000 },
      { apply: "2026-12" },
      { single: "Dining", month: "2027-01" },
      { multiple: ["Rent", "Car", "Salary", "Hidden"], month: "2027-01" },
      { overwrite: "2027-02" },
    ],
  },
  {
    name: "tracking",
    tracking: true,
    groups: [
      { name: "Income", income: true, categories: ["Salary"] },
      { name: "Bills", categories: ["Rent", "Phone", "Water"] },
      { name: "Everyday", categories: ["Groceries", "Dining"] },
      { name: "Goals", categories: ["Car", "Invest", "Misc"] },
    ],
    transactions: [
      ...salary(400000),
      ...spending("Rent", [100000, 100000, 100000, 100000, 100000, 100000], 2),
      ...spending("Groceries", [30000, 33000, 35000, 31000, 29000, 34000]),
      ...spending("Dining", [6000, 7000, 8000, 9000, 6500, 7200]),
      ...spending("Phone", [4500, 4500, 4500, 4500, 4500, 4500], 10),
    ],
    budgets: [
      { month: "2026-08", category: "Groceries", amount: 45000 },
      { month: "2026-08", category: "Salary", amount: 400000 },
    ],
    carryover: [{ category: "Groceries", from: "2026-08" }],
    schedules: [
      { name: "Phone bill", amount: -4500, date: monthly("2026-01-10") },
      {
        name: "Water",
        amount: -6000,
        date: monthly("2026-12-05", 3),
      },
    ],
    notes: {
      Salary: "#template 4000",
      Rent: "#template 1000",
      Phone: "#template schedule Phone bill",
      Water: "#template schedule Water",
      Groceries: "#template 400 up to 500",
      Dining: "#template average 6 months",
      Car: "#template 2000 by 2027-04",
      Invest: "#template-2 10% of all income",
      Misc: "#template remainder",
    },
    steps: [
      { apply: "2026-10" },
      { apply: "2026-11" },
      { overwrite: "2026-11" },
      { apply: "2026-12" },
    ],
  },
  {
    name: "scarce-rounded",
    preferences: { hideFraction: "true" },
    groups: [
      { name: "Income", income: true, categories: ["Salary"] },
      {
        name: "Spending",
        categories: ["Rent", "Food", "Fun", "Car", "Gifts", "Save", "Rest"],
      },
    ],
    transactions: [
      ...salary(100055),
      ...spending("Food", [25033, 31000, 28050, 30000, 26010, 29999]),
    ],
    budgets: [],
    carryover: [],
    schedules: [],
    notes: {
      Rent: "#template 700.40",
      Food: "#template-1 300.33",
      Fun: "#template-2 200.77",
      Car: "#template-1 1000 by 2027-05",
      Gifts: "#template 99.99 up to 150.5",
      Save: "#template remainder 3",
      Rest: "#template remainder",
    },
    steps: [
      { apply: "2026-11" },
      { overwrite: "2026-11" },
      { apply: "2026-12" },
    ],
  },
  {
    name: "yen",
    preferences: { defaultCurrencyCode: "JPY" },
    groups: [
      { name: "Income", income: true, categories: ["Salary"] },
      { name: "Spending", categories: ["Rent", "Food", "Rest"] },
    ],
    transactions: salary(300000),
    budgets: [],
    carryover: [],
    schedules: [],
    notes: {
      Rent: "#template 120000",
      Food: "#template 33.5 up to 40000",
      Rest: "#template remainder",
    },
    steps: [{ apply: "2026-11" }],
  },
  {
    name: "editor",
    groups: [
      { name: "Income", income: true, categories: ["Salary"] },
      {
        name: "Spending",
        categories: [
          "Rent",
          "Pct",
          "Odd",
          "Phone",
          "Daily",
          "Once",
          "Gone",
          "Done",
          "Split",
          "Shared",
          "Weekend",
          "Trim",
          "Late",
          "Tenth",
          "Bimonthly",
          "Twice",
          "Rest",
        ],
      },
    ],
    transactions: [
      ...salary(600000),
      ...spending("Rent", [50000, 50000, 50000, 50000, 50000, 50000], 3),
      ...spending("Trim", [9000, 12000, 15000, 8000, 7000, 11000], 9),
      { date: "2026-11-05", amount: -2500, category: "Late" },
    ],
    budgets: [
      { month: "2026-08", category: "Rent", amount: 80000 },
      { month: "2026-11", category: "Late", amount: 3000 },
    ],
    carryover: [],
    schedules: [
      { name: "Phone bill", amount: -4500, date: monthly("2026-01-10") },
      {
        name: "Every 40 days",
        amount: -4000,
        date: {
          frequency: "daily",
          start: "2026-11-20",
          interval: 40,
          endMode: "never",
        },
      },
      { name: "One off", amount: -9000, date: "2027-01-10" },
      {
        name: "Every 6 weeks",
        amount: -6000,
        date: {
          frequency: "weekly",
          start: "2026-12-04",
          interval: 6,
          endMode: "never",
        },
      },
      { name: "Long ago", amount: -1000, date: "2026-03-01" },
      {
        name: "Finished",
        amount: -1500,
        date: monthly("2026-01-15"),
        completed: true,
      },
      {
        name: "Shared",
        amount: -8000,
        date: monthly("2026-01-25"),
        split: { amount: -3000, category: "Split" },
      },
      {
        name: "Weekend",
        amount: -2000,
        date: {
          ...monthly("2026-11-07"),
          skipWeekend: true,
          weekendSolveMode: "after",
        },
      },
    ],
    notes: {
      Daily: "#template schedule Every 40 days",
      Once: "#template schedule One off",
      Gone: "#template schedule Long ago",
      Done: "#template schedule Finished",
      Split: "#template schedule Shared",
      Weekend: "#template schedule Weekend",
      Trim: "#template average 2 months [decrease 15]",
      Late: "#template average 3 months",
      Tenth: "#template 1 repeat every 10 days starting 2026-10-03",
      Bimonthly: "#template 25 repeat every 2 months starting 2026-08-15",
      Twice: "#template schedule Phone bill\n#template schedule Every 6 weeks",
      Rest: "#template remainder",
    },
    automations: {
      Rent: [
        { type: "refill", priority: 0, directive: "template" },
        {
          type: "limit",
          amount: 500,
          hold: false,
          period: "monthly",
          priority: null,
          directive: "template",
        },
      ],
      Pct: [
        {
          type: "percentage",
          percent: 10,
          previous: false,
          category: "cat:Salary",
          priority: 0,
          directive: "template",
        },
      ],
      Odd: [{ type: "mystery", priority: 0, directive: "template" }],
      Phone: [
        {
          type: "schedule",
          scheduleId: "sched:Phone bill",
          name: "Phone bill",
          priority: 0,
          directive: "template",
        },
      ],
    },
    steps: [
      { apply: "2026-10" },
      { apply: "2026-11" },
      { apply: "2026-12" },
      { overwrite: "2027-01" },
    ],
  },
  {
    name: "editor-errors",
    groups: [
      { name: "Income", income: true, categories: ["Salary"] },
      {
        name: "Spending",
        categories: [
          "Weekly",
          "Period",
          "Interval",
          "Lost",
          "Blank",
          "Spends",
          "Goals",
          "Unknown",
        ],
      },
    ],
    transactions: salary(300000),
    budgets: [],
    carryover: [],
    schedules: [],
    notes: {
      Goals: "#goal 10\n#goal 20",
      Unknown: "#template 10% of Nope",
    },
    automations: {
      Weekly: [
        {
          type: "simple",
          monthly: null,
          limit: { amount: 10, hold: false, period: "weekly" },
          priority: 0,
          directive: "template",
        },
      ],
      Period: [
        {
          type: "simple",
          monthly: 5,
          limit: { amount: 10, hold: false, period: "yearly" },
          priority: 0,
          directive: "template",
        },
      ],
      Interval: [
        {
          type: "periodic",
          amount: 5,
          period: { period: "week", amount: 0 },
          starting: "2026-01-01",
          priority: 0,
          directive: "template",
        },
      ],
      Lost: [
        {
          type: "schedule",
          scheduleId: "missing",
          name: "Ghost",
          priority: 0,
          directive: "template",
        },
      ],
      Blank: [{ type: "schedule", priority: 0, directive: "template" }],
      Spends: [
        {
          type: "spend",
          amount: 5,
          month: "2027-01",
          from: "2026-10",
          priority: 0,
          directive: "template",
        },
        {
          type: "spend",
          amount: 6,
          month: "2027-01",
          from: "2026-10",
          priority: 0,
          directive: "template",
        },
      ],
    },
    steps: [{ check: true }, { apply: "2026-11" }],
  },
  {
    name: "errors",
    groups: [
      { name: "Income", income: true, categories: ["Salary"] },
      {
        name: "Spending",
        categories: ["Past", "Ghost", "Double", "Mixed", "Adjust", "Fine"],
      },
    ],
    transactions: salary(300000),
    budgets: [],
    carryover: [],
    schedules: [
      { name: "Phone bill", amount: -4500, date: monthly("2026-01-10") },
    ],
    notes: {
      Past: "#template 100 by 2026-01",
      Ghost: "#template schedule Nobody",
      Double: "#template 10 up to 20\n#template up to 30",
      Mixed: "#template-1 100 by 2027-01\n#template-2 schedule Phone bill",
      Adjust: "#template average 3 months [decrease 150%]\n#template 5",
      Fine: "#template 50",
    },
    steps: [{ check: true }, { apply: "2026-11" }],
  },
];

const lines = [
  "#template 50",
  "#template $50.25",
  "#template € 12.5",
  "#template -10",
  "#template 12.",
  "#template 12.345",
  "#template-3 50",
  "#template-x 50",
  "#template -2",
  "#template 0 up to 600",
  "#template 400 up to 600",
  "#template 400 up to 600 hold",
  "#template 400 up to 600hold",
  "#template up to 5 per day",
  "#template up to 5 per day hold",
  "#template up to 20 per week starting 2026-01-05",
  "#template up to 20 per week starting 2026-01-05 hold",
  "#template up to 20 per week",
  "#template 50 garbage",
  "#templates 50",
  "#template 10% of all income",
  "#template 12.5% of previous Salary",
  "#template 5 % of Available funds",
  "#template 10% of previousSalary",
  "#template 10% of previous",
  "#template 10%",
  "#template-2 10% ofSalary",
  "#template 10 repeat every day starting 2026-01-01",
  "#template 10 repeat every 3 days starting 2026-01-01",
  "#template 10 repeat every week starting 2026-01-01",
  "#template 10 repeat every 2 weeks starting 2026-01-01 up to 30",
  "#template 10 repeat every 2 months starting 2026-01-01",
  "#template 10 repeat every year starting 2026-01-01",
  "#template 10 repeat every 2 years starting 2026-01-01",
  "#template 10 repeat every month starting 2026-01-01",
  "#template 10 repeat every weeks starting 2026-01-01",
  "#template 10 repeat every 2 weeks starting 2026-01",
  "#template 500 by 2026-12",
  "#template 500 by 2026-12 repeat every month",
  "#template 500 by 2026-12 repeat every 3 months",
  "#template 500 by 2026-12 repeat every year",
  "#template 500 by 2026-12 repeat every 2 years",
  "#template 500 by 2026-12 repeat every months",
  "#template 500 by 2026-12 spend from 2026-03",
  "#template 500 by 2026-12 spend from 2026-03 repeat every year",
  "#template 500 by 2026-12 garbage",
  "#template 500 by 26-12",
  "#template schedule Rent",
  "#template schedule  Rent  bill ",
  "#template schedule full Rent",
  "#template schedule fullRent",
  "#template schedule Rent [increase 10%]",
  "#template schedule Rent [decrease 5]",
  "#template schedule Rent[Increase 2.5 %]",
  "#template schedule Rent [increase abc]",
  "#template schedule [increase 10%]",
  "#template schedule Rent [weird] bill",
  "#template-1 schedule Rent",
  "#template remainder",
  "#template remainder 3",
  "#template remainder3 up to 100",
  "#template-2 remainder",
  "#template average 3 months",
  "#template average 6",
  "#template average 3 months [increase 20%]",
  "#template average 3 months [decrease 15]",
  "#template average 3 months [bump 5]",
  "#template average 0 months",
  "#template copy from 2 months ago",
  "#template copy from 2 months ago up to 50",
  "#template copy from 0 months ago",
  "#goal 1000",
  "#Goal 1000",
  "#goal $1000",
  "#goal1000",
  "#goal 10 up to 20",
  "#template",
  "#template\t50",
];

async function recordLines() {
  let ids = [];
  await api.runImport("lines", async () => {
    const group = await api.createCategoryGroup({ name: "Lines" });
    for (const [i, line] of lines.entries()) {
      const id = await api.createCategory({ name: `L${i}`, group_id: group });
      await api.updateNote(id, line);
      ids.push(id);
    }
  });
  await send("budget/store-note-templates");
  const { data } = await api.aqlQuery(
    api.q("categories").select(["id", "goal_def"]),
  );
  return lines.map((line, i) => {
    const templates = JSON.parse(data.find((c) => c.id === ids[i]).goal_def);
    const t = templates?.[0] ?? null;
    return { line, template: t?.type === "error" ? { type: "error" } : t };
  });
}

const splits = [];

async function addSplits(categories) {
  for (const split of splits.splice(0)) {
    const { data } = await api.aqlQuery(
      api.q("schedules").filter({ id: split.id }).select(["rule"]),
    );
    const rule = await send("rule-get", { id: data[0].rule });
    await send("rule-update", {
      ...rule,
      actions: [
        ...rule.actions,
        {
          op: "set-split-amount",
          value: split.amount,
          options: { splitIndex: 1, method: "fixed-amount" },
        },
        {
          op: "set",
          field: "category",
          value: categories[split.category],
          options: { splitIndex: 1 },
        },
        {
          op: "set-split-amount",
          options: { splitIndex: 2, method: "remainder" },
        },
      ],
    });
  }
}

async function build(scenario) {
  const categories = {};
  const account = await api.createAccount(
    { name: "Checking", offbudget: false },
    0,
  );
  const defaults = await api.getCategoryGroups();
  const incomeGroup = defaults.find((g) => g.is_income);
  for (const group of defaults.filter((g) => !g.is_income)) {
    for (const c of group.categories) await api.deleteCategory(c.id);
    await api.deleteCategoryGroup(group.id);
  }
  for (const group of scenario.groups) {
    const id = group.income
      ? incomeGroup.id
      : await api.createCategoryGroup({ name: group.name });
    for (const entry of group.categories) {
      const c = typeof entry === "string" ? { name: entry } : entry;
      categories[c.name] = await api.createCategory({
        name: c.name,
        group_id: id,
        is_income: !!group.income,
        hidden: !!c.hidden,
      });
    }
  }
  for (const c of incomeGroup.categories) await api.deleteCategory(c.id);
  if (scenario.tracking) await api.setPreference("budgetType", "tracking");
  for (const [id, value] of Object.entries(scenario.preferences ?? {})) {
    await send("preferences/save", { id, value });
  }
  await api.addTransactions(
    account,
    scenario.transactions
      .filter((t) => t.amount !== 0)
      .map((t) => ({ ...t, category: categories[t.category] })),
  );
  for (const b of scenario.budgets) {
    await api.setBudgetAmount(b.month, categories[b.category], b.amount);
  }
  for (const c of scenario.carryover) {
    await send("budget/set-carryover", {
      startMonth: c.from,
      category: categories[c.category],
      flag: true,
    });
  }
  const schedules = {};
  for (const s of scenario.schedules) {
    const id = await api.createSchedule({
      name: s.name,
      posts_transaction: false,
      account,
      amount: s.amount,
      amountOp: s.amountOp ?? "is",
      date: s.date,
    });
    schedules[s.name] = id;
    if (s.completed) {
      await send("schedule/update", { schedule: { id, completed: true } });
    }
    if (s.split) splits.push({ id, ...s.split });
  }
  const resolve = (value) => {
    if (typeof value !== "string") return value;
    if (value.startsWith("cat:")) return categories[value.slice(4)];
    if (value.startsWith("sched:")) return schedules[value.slice(6)];
    return value;
  };
  if (scenario.automations) {
    await send("budget/set-category-automations", {
      categoriesWithTemplates: Object.entries(scenario.automations).map(
        ([name, templates]) => ({
          id: categories[name],
          templates: templates.map((t) =>
            Object.fromEntries(
              Object.entries(t).map(([k, v]) => [k, resolve(v)]),
            ),
          ),
        }),
      ),
      source: "ui",
    });
  }
  for (const [name, note] of Object.entries(scenario.notes)) {
    await api.updateNote(categories[name], note);
  }
  return categories;
}

async function cells(scenario, categories, month) {
  const table = scenario.tracking ? "reflect_budgets" : "zero_budgets";
  const { data } = await api.aqlQuery(
    api
      .q(table)
      .filter({ month: Number(month.replace("-", "")) })
      .select(["category", "amount", "goal", "long_goal"]),
  );
  const out = {};
  for (const [name, id] of Object.entries(categories)) {
    const row = data.find((r) => r.category === id);
    out[name] = [row?.amount ?? 0, row?.goal ?? null, row?.long_goal ?? null];
  }
  return out;
}

async function run(scenario, categories, step) {
  if (step.check) return { result: await send("budget/check-templates") };
  if (step.note) {
    await api.updateNote(categories[step.note], step.text);
    return {};
  }
  if (step.set) {
    await api.setBudgetAmount(step.month, categories[step.set], step.amount);
    return {};
  }
  let month;
  let result;
  if (step.apply) {
    month = step.apply;
    result = await send("budget/apply-goal-template", { month });
  } else if (step.overwrite) {
    month = step.overwrite;
    result = await send("budget/overwrite-goal-template", { month });
  } else if (step.single) {
    month = step.month;
    result = await send("budget/apply-single-template", {
      month,
      category: categories[step.single],
    });
  } else {
    month = step.month;
    result = await send("budget/apply-multiple-templates", {
      month,
      categoryIds: step.multiple.map((name) => categories[name]),
    });
  }
  return { result, cells: await cells(scenario, categories, month) };
}

const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), "goal-templates-"));
await api.init({ dataDir });
const golden = { today: TODAY, scenarios: {} };
for (const scenario of scenarios) {
  let categories;
  await api.runImport(scenario.name, async () => {
    categories = await build(scenario);
  });
  await addSplits(categories);
  fs.writeFileSync(
    new URL(`${scenario.name}.zip`, dir),
    await api.exportBudget(),
  );
  const steps = [];
  for (const step of scenario.steps) {
    steps.push({ ...step, ...(await run(scenario, categories, step)) });
  }
  golden.scenarios[scenario.name] = steps;
}
golden.lines = await recordLines();
await api.shutdown();
fs.writeFileSync(
  new URL("actual-golden.json", dir),
  JSON.stringify(golden, null, 2) + "\n",
);
console.log(`Recorded ${scenarios.length} scenarios`);
