import {
  BUDGETS,
  demoHash,
  demoManifest,
  generateDemo,
  MANIFEST_PATH,
} from "./generate.mjs";
import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

test("the same seed and date give byte-identical budgets", () => {
  for (const variant of Object.keys(BUDGETS)) {
    const first = JSON.stringify(generateDemo({ variant }));
    const second = JSON.stringify(generateDemo({ variant }));
    assert.equal(first, second);
  }
});

test("the committed manifest matches the generator", () => {
  const committed = JSON.parse(fs.readFileSync(MANIFEST_PATH, "utf8"));
  assert.deepEqual(committed, demoManifest());
});

test("a different seed changes the data", () => {
  assert.notEqual(
    demoHash(generateDemo({ seed: "other" })),
    demoHash(generateDemo()),
  );
});

test("history covers thirteen months and stops at the given day", () => {
  const demo = generateDemo({ today: "2027-02-10" });
  assert.equal(demo.months.length, 13);
  assert.equal(demo.months.at(-1), "2027-02");
  assert.ok(demo.transactions.every((t) => t.date <= "2027-02-10"));
  assert.ok(demo.transactions.every((t) => t.date >= `${demo.months[0]}-01`));
});

test("both variants contain every feature the demo promises", () => {
  const { variants } = demoManifest();
  for (const variant of Object.values(variants)) {
    for (const key of [
      "splits",
      "transfers",
      "reconciled",
      "schedules",
      "rules",
      "tags",
      "tagged",
      "uncategorized",
    ])
      assert.ok(variant.counts[key] > 0, `${variant.name} has no ${key}`);
    assert.ok(variant.overspent.length > 0);
  }
  assert.ok(variants.envelope.holds.length > 0);
  assert.ok(variants.tracking.counts.rolloverFlags > 0);
});

test("on-budget accounts never go overdrawn except the credit card", () => {
  const demo = generateDemo();
  const balances = Object.fromEntries(
    demo.accounts.map((a) => [a.key, a.opening]),
  );
  for (const t of demo.transactions) {
    balances[t.account] += t.amount;
    if (t.transfer) balances[t.transfer] -= t.amount;
    for (const key of ["checking", "savings", "cash"])
      assert.ok(balances[key] >= 0, `${key} overdrawn on ${t.date}`);
  }
});
