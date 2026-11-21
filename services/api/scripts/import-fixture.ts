// Seeds a budget for statement-import parity, or imports rows into it with Actual's own client and records the outcome.
import fs from "node:fs";
import process from "node:process";
import * as Actual from "@actual-app/api";

const url = process.env.ACTUAL_URL ?? "http://localhost:5006";
const password = process.env.ACTUAL_PASSWORD ?? "demo-password";
const name = process.env.IMPORT_BUDGET ?? "Import parity";
const mode = process.argv[2];
const out = process.argv[3] ?? "import-cases.json";

await Actual.init({
  serverURL: url,
  password,
  dataDir: fs.mkdtempSync("/tmp/import-fixture-"),
});
const find = async () =>
  (await Actual.getBudgets()).find((b) => b.name === name && b.groupId);

export const rows = [
  {
    date: "2026-11-03",
    amount: -1000,
    payee_name: "corner market",
    imported_id: "bank-1",
    notes: "card",
  },
  { date: "2026-11-06", amount: -2500, payee_name: "cafe" },
  { date: "2026-11-08", amount: -2500, payee_name: "new place" },
  {
    date: "2026-11-04",
    amount: -700,
    payee_name: "bakery",
    imported_id: "bank-r",
  },
  {
    date: "2026-11-12",
    amount: -999,
    payee_name: "the lord of the rings shop",
  },
  { date: "2026-11-30", amount: -2500, payee_name: "cafe", cleared: false },
  {
    date: "2026-11-14",
    amount: 150000,
    payee_name: "ACME PAYROLL (API)",
    imported_id: "bank-2",
  },
];

if (mode === "seed") {
  if (!(await find())) {
    await Actual.runImport(name, async () => {
      const checking = await Actual.createAccount(
        { name: "Checking", offbudget: false },
        500000,
      );
      const market = await Actual.createPayee({ name: "Corner Market" });
      const cafe = await Actual.createPayee({ name: "Cafe" });
      const other = await Actual.createPayee({ name: "Other" });
      await Actual.addTransactions(checking, [
        {
          date: "2026-11-02",
          amount: -1000,
          payee: market,
          imported_id: "bank-1",
        },
        { date: "2026-11-05", amount: -2500, payee: cafe, notes: "latte" },
        { date: "2026-11-09", amount: -2500, payee: other },
        {
          date: "2026-11-04",
          amount: -700,
          payee: other,
          imported_id: "bank-r",
          reconciled: true,
          cleared: true,
        },
      ]);
    });
  }
  console.log(`seeded ${(await find())!.groupId}`);
} else {
  await Actual.downloadBudget((await find())!.groupId!);
  const checking = (await Actual.getAccounts())[0]!.id;
  const first = await Actual.importTransactions(checking, rows);
  const again = await Actual.importTransactions(checking, rows);
  const after = (await Actual.getTransactions(
    checking,
    "2026-01-01",
    "2026-12-31",
  )) as Array<Record<string, unknown>>;
  const payees = new Map((await Actual.getPayees()).map((p) => [p.id, p.name]));
  fs.writeFileSync(
    out,
    JSON.stringify(
      {
        rows,
        first: { added: first.added.length, updated: first.updated.length },
        again: { added: again.added.length, updated: again.updated.length },
        transactions: after
          .map((t) => ({
            date: t.date,
            amount: t.amount,
            payee: payees.get(t.payee as string) ?? null,
            notes: t.notes ?? null,
            imported_id: t.imported_id ?? null,
            imported_payee: t.imported_payee ?? null,
            cleared: t.cleared,
            reconciled: t.reconciled,
          }))
          .sort((a, b) =>
            `${a.date}${a.amount}${a.payee}`.localeCompare(
              `${b.date}${b.amount}${b.payee}`,
            ),
          ),
      },
      null,
      2,
    ),
  );
  console.log(
    `import +${first.added.length} ~${first.updated.length}, again +${again.added.length} ~${again.updated.length}`,
  );
}
await Actual.shutdown();
