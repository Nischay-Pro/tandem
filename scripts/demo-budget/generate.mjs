// Builds the fictional demo household as plain data. Same seed and date in, same bytes out.
import { createHash } from "node:crypto";
import fs from "node:fs";
import process from "node:process";
import { fileURLToPath, URL } from "node:url";

export const DEMO_SEED = "tandem-demo-household-1";
export const REFERENCE_TODAY = "2026-11-20";
export const HISTORY_MONTHS = 13;
export const BUDGETS = {
  envelope: "Demo household",
  tracking: "Demo household (tracking)",
};

const ACCOUNTS = [
  {
    key: "checking",
    name: "Everyday checking",
    offbudget: false,
    opening: 412000,
  },
  {
    key: "savings",
    name: "High-yield savings",
    offbudget: false,
    opening: 850000,
  },
  {
    key: "card",
    name: "Rewards credit card",
    offbudget: false,
    opening: -63500,
  },
  { key: "cash", name: "Cash wallet", offbudget: false, opening: 8000 },
  { key: "brokerage", name: "Brokerage", offbudget: true, opening: 2150000 },
  { key: "carLoan", name: "Car loan", offbudget: true, opening: -1420000 },
];

const INCOME = ["Salary", "Other income"];

const GROUPS = [
  ["Housing", { Rent: 185000, Utilities: 18000, Internet: 6000, Phone: 4500 }],
  [
    "Everyday",
    { Groceries: 70000, Household: 14000, "Dining out": 20000, Coffee: 4000 },
  ],
  [
    "Transport",
    {
      Fuel: 16000,
      Transit: 8000,
      "Car payment": 32000,
      "Car maintenance": 5000,
    },
  ],
  ["Health", { Pharmacy: 3000, Fitness: 5500 }],
  ["Fun", { Entertainment: 6000, Hobbies: 8000, Subscriptions: 3200 }],
  ["Seasonal", { Gifts: 6000, "Holiday travel": 25000 }],
  [
    "Saving goals",
    { "Emergency fund": 0, "Car insurance": 9000, Investing: 30000 },
  ],
];

const TAGS = [
  { tag: "vacation", color: "#2F80ED", description: "Trips away from home" },
  {
    tag: "reimbursable",
    color: "#27AE60",
    description: "Work costs to claim back",
  },
  { tag: "gift", color: "#EB5757", description: "Presents for others" },
];

const UTILITY_SEASON = [
  1.35, 1.3, 1.1, 0.9, 0.8, 0.95, 1.2, 1.25, 0.95, 0.85, 1.0, 1.3,
];
const COVERED = new Set(["Car insurance", "Holiday travel"]);
const ENVELOPE_ROLLOVER = "Car maintenance";
const TRACKING_ROLLOVER = ["Holiday travel"];

function seedHash(text) {
  let h = 0x811c9dc5;
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return h;
}

function random(...parts) {
  let a = seedHash(parts.join(":"));
  const next = () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  return {
    int: (min, max) => min + Math.floor(next() * (max - min + 1)),
    chance: (p) => next() < p,
    vary: (base, spread) => Math.round(base * (1 + (next() * 2 - 1) * spread)),
  };
}

function monthsEnding(today) {
  const [year, month] = today.split("-").map(Number);
  return Array.from({ length: HISTORY_MONTHS }, (_, i) => {
    const d = new Date(Date.UTC(year, month - 1 - (HISTORY_MONTHS - 1 - i), 1));
    const y = d.getUTCFullYear();
    const m = d.getUTCMonth() + 1;
    return {
      index: i,
      key: `${y}-${String(m).padStart(2, "0")}`,
      calendar: m,
      days: new Date(Date.UTC(y, m, 0)).getUTCDate(),
    };
  });
}

function monthTransactions(month, today, seed, state) {
  const r = random(seed, "month", month.index);
  const isCurrent = month.key === today.slice(0, 7);
  const lastDay = isCurrent ? Number(today.slice(8, 10)) : month.days;
  const out = [];
  const events = [];
  const add = (day, tx) => {
    const d = Math.min(day, month.days);
    if (d > lastDay) return;
    out.push({ date: `${month.key}-${String(d).padStart(2, "0")}`, ...tx });
  };
  const spend = (account, day, payee, category, amount, extra = {}) =>
    add(day, { account, payee, category, amount: -Math.abs(amount), ...extra });
  const transfer = (from, to, day, amount, category = null, notes) =>
    add(day, {
      account: from,
      transfer: to,
      category,
      amount: -amount,
      ...(notes ? { notes } : {}),
    });
  const cal = month.calendar;

  add(1, {
    account: "checking",
    payee: "Employer payroll",
    category: "Salary",
    amount: 165000,
    notes: "Alex pay",
  });
  add(15, {
    account: "checking",
    payee: "Employer payroll",
    category: "Salary",
    amount: 165000,
    notes: "Alex pay",
  });
  add(7, {
    account: "checking",
    payee: "Design studio payroll",
    category: "Salary",
    amount: 128000,
    notes: "Sam pay",
  });
  add(22, {
    account: "checking",
    payee: "Design studio payroll",
    category: "Salary",
    amount: 128000,
    notes: "Sam pay",
  });
  add(28, {
    account: "savings",
    payee: "Bank interest",
    category: "Other income",
    amount: Math.round((state.savings * 0.035) / 12),
  });
  if (cal === 3) {
    add(15, {
      account: "checking",
      payee: "Design studio payroll",
      category: "Salary",
      amount: 250000,
      notes: "Annual bonus",
    });
    events.push("annual bonus");
  }
  if (cal === 4) {
    add(18, {
      account: "checking",
      payee: "Tax office",
      category: "Other income",
      amount: 84000,
      notes: "Tax refund",
    });
    events.push("tax refund");
  }

  spend("checking", 1, "Landlord", "Rent", 185000, { notes: "Monthly rent" });
  spend(
    "checking",
    9,
    "City Power & Water",
    "Utilities",
    r.vary(14500 * UTILITY_SEASON[cal - 1], 0.08),
  );
  spend("checking", 12, "Fibre Internet", "Internet", 6000);
  spend("card", 18, "Mobile Co", "Phone", 4500);
  spend("card", 8, "Streaming Service", "Subscriptions", 1599);
  spend("card", 19, "Music Streaming", "Subscriptions", 1099);
  spend("checking", 3, "Fitness Club", "Fitness", 5500);

  const groceryBoost = cal === 12 ? 1.25 : cal === 11 ? 1.1 : 1;
  for (const day of [2, 9, 16, 23, 30]) {
    if (day === 16) continue;
    spend(
      r.chance(0.7) ? "checking" : "card",
      day + r.int(0, 1),
      "Corner Market",
      "Groceries",
      r.vary(10500 * groceryBoost, 0.25),
    );
  }
  const bigBoxGifts = cal === 12 ? r.vary(18000, 0.2) : 0;
  const bigBox = [
    { amount: -r.vary(17500 * groceryBoost, 0.15), category: "Groceries" },
    { amount: -r.vary(7500, 0.3), category: "Household" },
    ...(bigBoxGifts
      ? [{ amount: -bigBoxGifts, category: "Gifts", notes: "#gift" }]
      : []),
  ];
  add(16, {
    account: "card",
    payee: "Big Box Store",
    imported_payee: "BIGBOX STORE #1142",
    amount: bigBox.reduce((sum, s) => sum + s.amount, 0),
    category: null,
    subtransactions: bigBox,
  });
  if (r.chance(0.5)) {
    const rx = r.vary(1800, 0.3);
    const groceries = r.vary(2600, 0.3);
    add(r.int(4, 26), {
      account: "card",
      payee: "Pharmacy",
      amount: -(rx + groceries),
      category: null,
      subtransactions: [
        { amount: -rx, category: "Pharmacy" },
        { amount: -groceries, category: "Groceries" },
      ],
    });
  } else {
    spend("card", r.int(4, 26), "Pharmacy", "Pharmacy", r.vary(2400, 0.4));
  }

  const diningBoost = cal === 12 ? 1.8 : cal === 7 || cal === 8 ? 1.2 : 1;
  const diningVisits = cal === 12 ? 8 : r.int(4, 6);
  for (let i = 0; i < diningVisits; i++) {
    const payee = [
      "Noodle House",
      "Pizza Place",
      "Taco Stand",
      "Neighbourhood Bistro",
    ][r.int(0, 3)];
    spend(
      "card",
      2 + Math.floor((i * 27) / diningVisits) + r.int(0, 2),
      payee,
      "Dining out",
      r.vary(3600 * diningBoost, 0.35),
    );
  }
  for (let i = 0; i < r.int(5, 8); i++) {
    spend(
      r.chance(0.6) ? "cash" : "card",
      r.int(1, 28),
      "Coffee Corner",
      "Coffee",
      r.vary(520, 0.25),
    );
  }

  const fuelBoost = cal >= 6 && cal <= 8 ? 1.15 : 1;
  for (const day of [6, 20]) {
    spend(
      "card",
      day + r.int(0, 2),
      "Fuel Station",
      "Fuel",
      r.vary(7400 * fuelBoost, 0.12),
      { imported_payee: "FUEL STATION 0815" },
    );
  }
  spend("checking", 4, "Metro Transit", "Transit", 5800);
  if (r.chance(0.35)) {
    spend("card", r.int(5, 25), "Metro Transit", "Transit", r.vary(1900, 0.2), {
      notes: "Client visit #reimbursable",
    });
  }

  spend("card", r.int(5, 26), "Cinema", "Entertainment", r.vary(3400, 0.25));
  spend("card", r.int(5, 26), "Bookshop", "Hobbies", r.vary(4200, 0.4));
  if (r.chance(0.5))
    spend("card", r.int(5, 26), "Craft Supplies", "Hobbies", r.vary(3100, 0.4));
  if (r.chance(0.4))
    spend(
      "checking",
      r.int(5, 26),
      "Hardware Store",
      "Household",
      r.vary(4600, 0.4),
    );

  transfer("checking", "cash", 5, 6000, null, "ATM withdrawal");
  spend("cash", r.int(8, 24), "Farmers Market", "Groceries", r.vary(2400, 0.3));
  transfer(
    "checking",
    "brokerage",
    5,
    30000,
    "Investing",
    "Monthly investment",
  );
  transfer("checking", "carLoan", 10, 32000, "Car payment");
  add(10, {
    account: "carLoan",
    payee: "Auto lender",
    category: null,
    amount: -Math.round((-state.carLoan * 0.059) / 12),
    notes: "Interest",
  });
  add(28, {
    account: "brokerage",
    payee: "Market change",
    category: null,
    amount: r.vary(Math.round(state.brokerage * 0.006), 2.2),
  });
  if (!isCurrent || lastDay >= 16)
    transfer("checking", "savings", 16, cal === 3 ? 250000 : 120000);
  if (state.cardOwed > 0)
    transfer("checking", "card", 25, state.cardOwed, null, "Card payment");

  if (cal === 3) {
    spend("checking", 20, "Insurance Co", "Car insurance", 108000, {
      notes: "Annual car insurance",
    });
    events.push("annual car insurance");
  }
  if (cal === 7) {
    spend("card", 3, "Airline", "Holiday travel", 98000, {
      notes: "Flights #vacation",
    });
    spend("card", 18, "Seaside Hotel", "Holiday travel", 76500, {
      notes: "Five nights #vacation",
    });
    spend("card", 20, "Car Rental Desk", "Holiday travel", 21000, {
      notes: "#vacation",
    });
    events.push("summer holiday");
  }
  if (cal === 12) {
    spend("card", 6, "Toy Store", "Gifts", 14800, { notes: "#gift" });
    spend("card", 13, "Department Store", "Gifts", 26500, { notes: "#gift" });
    spend("checking", 20, "Rail Company", "Holiday travel", 18400, {
      notes: "Visiting family #vacation",
    });
    events.push("holiday season");
  }
  if (month.index === 6) {
    spend("checking", 14, "Auto Repair Shop", "Car maintenance", 128400, {
      notes: "New brakes and timing belt",
    });
    events.push("major car repair");
  }
  if (month.index === 9 || month.index === 2) {
    spend(
      "card",
      r.int(10, 24),
      "Department Store",
      "Household",
      r.vary(15500, 0.2),
      { notes: "Back to school" },
    );
  }
  if (isCurrent) {
    spend("card", 2, "Concert Hall", "Entertainment", 8500, {
      notes: "Concert tickets",
    });
    spend("checking", 3, "Tyre Centre", "Car maintenance", 38600, {
      notes: "Four new tyres",
    });
    events.push("new tyres");
    add(Math.max(1, lastDay - 1), {
      account: "card",
      payee: "Corner Market",
      amount: -4218,
      category: "Groceries",
      notes: "Weekly shop",
    });
    add(lastDay, {
      account: "checking",
      payee: "Online Marketplace",
      amount: -2399,
      category: null,
    });
    add(lastDay, {
      account: "card",
      payee: "Garden Centre",
      amount: -3150,
      category: null,
    });
  }
  return { transactions: out, events };
}

function cardActivity(transactions) {
  return transactions
    .filter((t) => t.account === "card" && !t.transfer)
    .reduce((sum, t) => sum + t.amount, 0);
}

function balanceChange(transactions, key) {
  let total = 0;
  for (const t of transactions) {
    if (t.account === key) total += t.amount;
    if (t.transfer === key) total -= t.amount;
  }
  return total;
}

function categoryActivity(transactions) {
  const activity = {};
  for (const t of transactions) {
    const parts = t.subtransactions ?? [t];
    for (const p of parts) {
      if (!p.category) continue;
      if (t.transfer || !ACCOUNTS.find((a) => a.key === t.account).offbudget)
        activity[p.category] = (activity[p.category] ?? 0) + p.amount;
    }
  }
  return activity;
}

function buildHistory(seed, today) {
  const months = monthsEnding(today);
  const state = {
    savings: 850000,
    brokerage: 2150000,
    carLoan: -1420000,
    cardOwed: 63500,
  };
  const transactions = [];
  const perMonth = [];
  for (const month of months) {
    const { transactions: txs, events } = monthTransactions(
      month,
      today,
      seed,
      state,
    );
    for (const key of ["savings", "brokerage", "carLoan"])
      state[key] += balanceChange(txs, key);
    state.cardOwed = -cardActivity(txs);
    transactions.push(...txs);
    perMonth.push({
      month,
      events,
      activity: categoryActivity(txs),
      transactions: txs,
    });
  }
  return { months, transactions, perMonth };
}

function planned(category, month) {
  for (const [, cats] of GROUPS)
    if (category in cats) {
      if (category === "Gifts" && month.calendar === 12) return 45000;
      return cats[category];
    }
  throw new Error(`Unknown category ${category}`);
}

const EXPENSES = GROUPS.flatMap(([, cats]) => Object.keys(cats));
const OPENING_ON_BUDGET = ACCOUNTS.filter((a) => !a.offbudget).reduce(
  (s, a) => s + a.opening,
  0,
);

function envelopeBudget(seed, history) {
  const budget = [];
  const holds = [];
  const carryover = [];
  const overspent = [];
  const balances = Object.fromEntries(EXPENSES.map((c) => [c, 0]));
  const rollover = new Set();
  let toBudget = 0;
  let heldIn = 0;
  let deduction = 0;
  for (const { month, activity } of history.perMonth) {
    const r = random(seed, "envelope", month.index);
    const income =
      INCOME.reduce((s, c) => s + (activity[c] ?? 0), 0) +
      (month.index === 0 ? OPENING_ON_BUDGET : 0);
    if (month.index === 6) rollover.add(ENVELOPE_ROLLOVER);
    let available = toBudget + income + heldIn - deduction;
    const assigned = {};
    for (const c of EXPENSES) {
      if (c === "Emergency fund") continue;
      let amount = planned(c, month);
      if (COVERED.has(c) || (c === ENVELOPE_ROLLOVER && month.index === 7))
        amount = Math.max(amount, -(balances[c] + (activity[c] ?? 0)));
      assigned[c] = Math.min(amount, Math.max(0, available));
      available -= assigned[c];
    }
    const isLast = month.index === HISTORY_MONTHS - 1;
    const leftover = Math.min(
      Math.max(0, available),
      isLast ? r.int(8000, 16000) : r.int(0, 6000),
    );
    const hold =
      month.index >= HISTORY_MONTHS - 4 && !isLast
        ? Math.max(0, available - leftover)
        : month.calendar === 3
          ? Math.min(100000, Math.max(0, available - leftover))
          : 0;
    assigned["Emergency fund"] = Math.max(0, available - hold - leftover);
    available -= assigned["Emergency fund"];
    toBudget = available - hold;
    heldIn = hold;
    if (hold) holds.push({ month: month.key, amount: hold });
    deduction = 0;
    for (const c of EXPENSES) {
      if (assigned[c])
        budget.push({ month: month.key, category: c, amount: assigned[c] });
      balances[c] += assigned[c] + (activity[c] ?? 0);
      if (rollover.has(c)) carryover.push({ month: month.key, category: c });
      if (balances[c] < 0) {
        overspent.push({ month: month.key, category: c, amount: -balances[c] });
        if (!rollover.has(c)) {
          deduction += -balances[c];
          balances[c] = 0;
        }
      }
    }
  }
  return { budget, holds, carryover, overspent };
}

function trackingBudget(history) {
  const budget = [];
  const carryover = [];
  const overspent = [];
  const balances = Object.fromEntries(EXPENSES.map((c) => [c, 0]));
  for (const { month, activity } of history.perMonth) {
    budget.push({ month: month.key, category: "Salary", amount: 586000 });
    for (const c of EXPENSES) {
      const amount =
        c === "Emergency fund"
          ? 40000
          : c === "Car insurance"
            ? month.calendar === 3
              ? 108000
              : 0
            : planned(c, month);
      budget.push({ month: month.key, category: c, amount });
      const rolls = TRACKING_ROLLOVER.includes(c);
      balances[c] = (rolls ? balances[c] : 0) + amount + (activity[c] ?? 0);
      if (rolls) carryover.push({ month: month.key, category: c });
      if (balances[c] < 0)
        overspent.push({ month: month.key, category: c, amount: -balances[c] });
    }
  }
  return { budget, holds: [], carryover, overspent };
}

function reconcile(transactions, today) {
  const currentMonth = today.slice(0, 7);
  const recent = new Date(Date.parse(today) - 4 * 86_400_000)
    .toISOString()
    .slice(0, 10);
  return transactions.map((t) => {
    const settled = t.date.slice(0, 7) < currentMonth;
    const reconciled = t.account === "checking" && settled;
    const cleared = settled || t.date <= recent;
    return { ...t, cleared, ...(reconciled ? { reconciled: true } : {}) };
  });
}

function sortTransactions(transactions) {
  return transactions
    .map((t, i) => [t, i])
    .sort(([a, i], [b, j]) =>
      a.date < b.date ? -1 : a.date > b.date ? 1 : i - j,
    )
    .map(([t]) => t);
}

function schedules(months) {
  const start = `${months[0].key}-01`;
  const monthly = (day) => ({
    frequency: "monthly",
    start: `${months[0].key}-${String(day).padStart(2, "0")}`,
    interval: 1,
    patterns: [],
    skipWeekend: false,
    weekendSolveMode: "after",
    endMode: "never",
  });
  const march = months.find((m) => m.calendar === 3).key;
  return [
    {
      name: "Rent",
      account: "checking",
      payee: "Landlord",
      amount: -185000,
      amountOp: "is",
      date: monthly(1),
    },
    {
      name: "Internet",
      account: "checking",
      payee: "Fibre Internet",
      amount: -6000,
      amountOp: "is",
      date: monthly(12),
    },
    {
      name: "Phone plan",
      account: "card",
      payee: "Mobile Co",
      amount: -4500,
      amountOp: "is",
      date: monthly(18),
    },
    {
      name: "Streaming",
      account: "card",
      payee: "Streaming Service",
      amount: -1599,
      amountOp: "is",
      date: monthly(8),
    },
    {
      name: "Alex pay",
      account: "checking",
      payee: "Employer payroll",
      amount: 165000,
      amountOp: "isapprox",
      date: {
        ...monthly(1),
        start,
        patterns: [
          { type: "day", value: 1 },
          { type: "day", value: 15 },
        ],
      },
    },
    {
      name: "Car insurance",
      account: "checking",
      payee: "Insurance Co",
      amount: -108000,
      amountOp: "isapprox",
      date: { ...monthly(20), frequency: "yearly", start: `${march}-20` },
    },
  ];
}

const RULES = [
  {
    stage: "pre",
    conditionsOp: "and",
    conditions: [{ field: "imported_payee", op: "contains", value: "BIGBOX" }],
    actions: [{ field: "payee", op: "set", value: { payee: "Big Box Store" } }],
  },
  {
    stage: "pre",
    conditionsOp: "and",
    conditions: [
      { field: "imported_payee", op: "contains", value: "FUEL STATION" },
    ],
    actions: [{ field: "payee", op: "set", value: { payee: "Fuel Station" } }],
  },
  {
    stage: null,
    conditionsOp: "and",
    conditions: [
      { field: "payee", op: "is", value: { payee: "Coffee Corner" } },
    ],
    actions: [{ field: "category", op: "set", value: { category: "Coffee" } }],
  },
  {
    stage: null,
    conditionsOp: "and",
    conditions: [
      { field: "payee", op: "is", value: { payee: "Streaming Service" } },
    ],
    actions: [
      { field: "category", op: "set", value: { category: "Subscriptions" } },
    ],
  },
  {
    stage: null,
    conditionsOp: "or",
    conditions: [
      { field: "payee", op: "is", value: { payee: "Noodle House" } },
      { field: "payee", op: "is", value: { payee: "Pizza Place" } },
      { field: "payee", op: "is", value: { payee: "Taco Stand" } },
    ],
    actions: [
      { field: "category", op: "set", value: { category: "Dining out" } },
    ],
  },
];

export function generateDemo({
  variant = "envelope",
  seed = DEMO_SEED,
  today = REFERENCE_TODAY,
} = {}) {
  if (!(variant in BUDGETS)) throw new Error(`Unknown variant ${variant}`);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(today)) throw new Error(`Bad date ${today}`);
  const history = buildHistory(seed, today);
  const plan =
    variant === "envelope"
      ? envelopeBudget(seed, history)
      : trackingBudget(history);
  return {
    name: BUDGETS[variant],
    variant,
    budgetType: variant,
    seed,
    today,
    months: history.months.map((m) => m.key),
    openingDate: `${history.months[0].key}-01`,
    accounts: ACCOUNTS,
    categoryGroups: [
      { name: "Income", isIncome: true, categories: INCOME },
      ...GROUPS.map(([name, cats]) => ({
        name,
        isIncome: false,
        categories: Object.keys(cats),
      })),
    ],
    tags: TAGS,
    transactions: sortTransactions(reconcile(history.transactions, today)),
    budget: plan.budget,
    holds: plan.holds,
    carryover: plan.carryover,
    schedules: schedules(history.months),
    rules: RULES,
    events: history.perMonth
      .filter((p) => p.events.length)
      .map((p) => ({ month: p.month.key, events: p.events })),
    overspent: plan.overspent,
  };
}

export function demoHash(demo) {
  return createHash("sha256").update(JSON.stringify(demo)).digest("hex");
}

export function demoManifest({
  seed = DEMO_SEED,
  today = REFERENCE_TODAY,
} = {}) {
  const variants = {};
  for (const variant of Object.keys(BUDGETS)) {
    const demo = generateDemo({ variant, seed, today });
    const tx = demo.transactions;
    variants[variant] = {
      name: demo.name,
      budgetType: demo.budgetType,
      sha256: demoHash(demo),
      counts: {
        accounts: demo.accounts.length,
        categories: demo.categoryGroups.reduce(
          (s, g) => s + g.categories.length,
          0,
        ),
        payees: new Set(tx.filter((t) => t.payee).map((t) => t.payee)).size,
        transactions: tx.length,
        splits: tx.filter((t) => t.subtransactions).length,
        transfers: tx.filter((t) => t.transfer).length,
        uncategorized: tx.filter(
          (t) =>
            !t.category &&
            !t.subtransactions &&
            !t.transfer &&
            !ACCOUNTS.find((a) => a.key === t.account).offbudget,
        ).length,
        tagged: tx.filter((t) =>
          [t, ...(t.subtransactions ?? [])].some((p) => p.notes?.includes("#")),
        ).length,
        reconciled: tx.filter((t) => t.reconciled).length,
        budgetAmounts: demo.budget.length,
        holds: demo.holds.length,
        rolloverFlags: demo.carryover.length,
        schedules: demo.schedules.length,
        rules: demo.rules.length,
        tags: demo.tags.length,
      },
      holds: demo.holds,
      overspent: demo.overspent,
    };
  }
  const reference = generateDemo({ seed, today });
  const net = Object.fromEntries(
    reference.months.map((m) => [
      m,
      Object.values(
        categoryActivity(
          reference.transactions.filter((t) => t.date.startsWith(m)),
        ),
      ).reduce((sum, v) => sum + v, 0),
    ]),
  );
  return {
    seed,
    referenceToday: today,
    historyMonths: HISTORY_MONTHS,
    months: reference.months,
    accounts: ACCOUNTS.map(({ name, offbudget, opening }) => ({
      name,
      offbudget,
      opening,
    })),
    events: reference.events,
    netByMonth: net,
    goodMonths: reference.months.slice(0, -1).filter((m) => net[m] >= 150000),
    badMonths: reference.months.slice(0, -1).filter((m) => net[m] < 75000),
    tags: TAGS.map((t) => t.tag),
    variants,
  };
}

export const MANIFEST_PATH = fileURLToPath(
  new URL("./manifest.json", import.meta.url),
);

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  fs.writeFileSync(
    MANIFEST_PATH,
    `${JSON.stringify(demoManifest(), null, 2)}\n`,
  );
}
