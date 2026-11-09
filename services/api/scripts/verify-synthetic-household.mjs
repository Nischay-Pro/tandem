import * as Actual from "@actual-app/api";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

const serverURL = process.env.ACTUAL_SERVER_URL?.trim();
const password = process.env.ACTUAL_SERVER_PASSWORD?.trim();

if (!serverURL || !password) {
  throw new Error(
    "Set ACTUAL_SERVER_URL and ACTUAL_SERVER_PASSWORD to verify the synthetic household budgets.",
  );
}

const dataDir = await mkdtemp(join(tmpdir(), "tandem-household-verify-"));
const failures = [];
const summaries = [];

function check(condition, message) {
  if (!condition) failures.push(message);
}

try {
  await Actual.init({ dataDir, serverURL, password });
  const remoteBudgets = (await Actual.getBudgets()).filter(
    (budget) => !budget.id,
  );
  const targets = [
    remoteBudgets.find(
      (budget) => budget.name.includes("Canada") && budget.name.includes("CAD"),
    ),
    remoteBudgets.find(
      (budget) =>
        budget.name.includes("United States") && budget.name.includes("USD"),
    ),
  ];

  check(
    targets.every(Boolean),
    "Expected one Canada CAD budget and one United States USD budget.",
  );
  for (const budget of targets.filter(Boolean)) {
    await Actual.downloadBudget(budget.groupId);
  }

  const localBudgets = (await Actual.getBudgets()).filter(
    (budget) => budget.id,
  );
  let purchaseCount = 0;
  let splitCount = 0;
  let uncategorizedCount = 0;
  let reconciledCount = 0;
  let scheduleCount = 0;
  let overspentCategoryMonths = 0;
  let pairedTransferRows = 0;

  for (const target of targets.filter(Boolean)) {
    const budget = localBudgets.find(
      (entry) => entry.groupId === target.groupId,
    );
    check(Boolean(budget), `Could not open ${target.name}.`);
    if (!budget) continue;

    await Actual.loadBudget(budget.id);
    const [accounts, categories, payees, months, schedules] = await Promise.all(
      [
        Actual.getAccounts(),
        Actual.getCategories(),
        Actual.getPayees(),
        Actual.getBudgetMonths(),
        Actual.getSchedules(),
      ],
    );
    const categoryById = new Map(
      categories.map((category) => [category.id, category]),
    );
    const payeeById = new Map(payees.map((payee) => [payee.id, payee.name]));
    const transactions = [];

    for (const account of accounts) {
      transactions.push(
        ...(await Actual.getTransactions(
          account.id,
          "1900-01-01",
          "2099-12-31",
        )),
      );
    }

    const activeTransactions = transactions.filter(
      (transaction) => !transaction.tombstone,
    );
    const transactionById = new Map(
      activeTransactions.map((transaction) => [transaction.id, transaction]),
    );
    const openingBalances = activeTransactions.filter(
      (transaction) => transaction.starting_balance_flag,
    );
    const firstDate = activeTransactions
      .map((transaction) => transaction.date)
      .sort()[0];
    const importIds = activeTransactions
      .map((transaction) => transaction.imported_id)
      .filter(Boolean);
    const annualTotals = new Map();
    const dailyBalanceChanges = new Map();
    const accountIds = new Set(accounts.map((account) => account.id));

    check(
      openingBalances.length === accounts.length,
      `${target.name} needs one opening balance row per account.`,
    );
    for (const opening of openingBalances) {
      check(
        opening.date === firstDate,
        `${target.name} has an opening balance outside its first day.`,
      );
      check(
        opening.date.endsWith("-01"),
        `${target.name} has an opening balance after the first day of its start month.`,
      );
      check(
        Boolean(opening.imported_id),
        `${target.name} has an opening balance without an import id.`,
      );
    }

    check(
      new Set(importIds).size === importIds.length,
      `${target.name} contains duplicate transaction import ids.`,
    );
    check(
      activeTransactions.every((transaction) => transaction.amount !== 0),
      `${target.name} contains a zero-amount transaction.`,
    );
    check(
      activeTransactions.every(
        (transaction) =>
          transaction.date <= new Date().toISOString().slice(0, 10),
      ),
      `${target.name} contains a future-dated transaction.`,
    );

    for (const transaction of activeTransactions) {
      const category = categoryById.get(transaction.category);
      const year = transaction.date.slice(0, 4);
      const totals = annualTotals.get(year) ?? { income: 0, spending: 0 };
      if (category?.is_income && transaction.amount > 0)
        totals.income += transaction.amount;
      if (category && !category.is_income && transaction.amount < 0) {
        totals.spending -= transaction.amount;
      }
      annualTotals.set(year, totals);

      if (
        transaction.amount < 0 &&
        !transaction.transfer_id &&
        !transaction.starting_balance_flag &&
        !category?.is_income
      ) {
        purchaseCount += 1;
      }
      if (transaction.is_parent) splitCount += 1;
      if (
        !transaction.category &&
        !transaction.transfer_id &&
        !transaction.starting_balance_flag &&
        !transaction.is_parent
      ) {
        uncategorizedCount += 1;
      }
      if (transaction.reconciled) reconciledCount += 1;

      if (transaction.transfer_id) {
        const counterpart = transactionById.get(transaction.transfer_id);
        check(
          Boolean(counterpart) &&
            counterpart.transfer_id === transaction.id &&
            counterpart.amount === -transaction.amount,
          `${target.name} contains an unpaired transfer.`,
        );
        if (counterpart) pairedTransferRows += 1;
      }

      const dailyKey = `${transaction.account}::${transaction.date}`;
      dailyBalanceChanges.set(
        dailyKey,
        (dailyBalanceChanges.get(dailyKey) ?? 0) + transaction.amount,
      );
    }

    for (const [year, totals] of annualTotals) {
      check(
        totals.income >= totals.spending,
        `${target.name} spends more than it earns in ${year}.`,
      );
    }

    for (const accountId of accountIds) {
      let balance = 0;
      let minimumBalance = Number.POSITIVE_INFINITY;
      const days = [...dailyBalanceChanges.entries()]
        .filter(([key]) => key.startsWith(`${accountId}::`))
        .map(([key, amount]) => [key.slice(accountId.length + 2), amount])
        .sort(([left], [right]) => left.localeCompare(right));
      for (const [, amount] of days) {
        balance += amount;
        minimumBalance = Math.min(minimumBalance, balance);
      }
      const actualBalance = await Actual.getAccountBalance(accountId);
      check(
        balance === actualBalance,
        `${target.name} has an account balance that does not match its transactions.`,
      );
      check(
        minimumBalance >= 0,
        `${target.name} has a negative daily account balance.`,
      );
    }

    let negativeToBudgetMonths = 0;
    for (const month of months) {
      const monthData = await Actual.getBudgetMonth(month);
      if (monthData.toBudget < 0) negativeToBudgetMonths += 1;
      overspentCategoryMonths += monthData.categoryGroups
        .flatMap((group) => group.categories)
        .some((category) => category.balance < 0)
        ? 1
        : 0;
    }
    scheduleCount += schedules.length;
    check(
      negativeToBudgetMonths <= 3,
      `${target.name} has ${negativeToBudgetMonths} months with a negative To Budget amount.`,
    );

    summaries.push({
      name: target.name,
      purchases: activeTransactions.filter(
        (transaction) =>
          transaction.amount < 0 &&
          !transaction.transfer_id &&
          !transaction.starting_balance_flag &&
          !categoryById.get(transaction.category)?.is_income,
      ).length,
      openingBalances: openingBalances.length,
      accounts: accounts.length,
      negativeToBudgetMonths,
      transferRows: activeTransactions.filter(
        (transaction) => transaction.transfer_id,
      ).length,
    });
  }

  check(
    purchaseCount === 15_000,
    `Expected 15,000 purchases across both budgets; found ${purchaseCount}.`,
  );
  check(splitCount > 0, "Expected at least one split transaction.");
  check(scheduleCount > 0, "Expected at least one scheduled transaction.");
  check(
    uncategorizedCount > 0,
    "Expected at least one uncategorized transaction.",
  );
  check(reconciledCount > 0, "Expected at least one reconciled transaction.");
  check(
    overspentCategoryMonths > 0,
    "Expected at least one overspent category month.",
  );
  check(
    pairedTransferRows > 0,
    "Expected paired transfers between budget accounts.",
  );

  if (failures.length > 0) {
    process.stderr.write(
      `${failures.map((failure) => `- ${failure}`).join("\n")}\n`,
    );
    process.exitCode = 1;
  } else {
    process.stdout.write(
      `${JSON.stringify({ status: "passed", purchaseCount, pairedTransferRows, splitCount, uncategorizedCount, reconciledCount, scheduleCount, overspentCategoryMonths, budgets: summaries }, null, 2)}\n`,
    );
  }
} finally {
  await Actual.shutdown().catch(() => {});
  await rm(dataDir, { recursive: true, force: true });
}
