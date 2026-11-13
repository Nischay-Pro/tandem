import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import test from "node:test";

import { nightlyCiDecision } from "./nightly-ci-status.mjs";

test("waits until a push CI run exists", () => {
  assert.deepEqual(nightlyCiDecision([]), {
    decision: "wait",
    status: "missing",
    conclusion: "",
  });
});

test("ignores manual and scheduled CI runs", () => {
  assert.deepEqual(
    nightlyCiDecision([
      {
        event: "workflow_dispatch",
        status: "completed",
        conclusion: "success",
      },
      { event: "schedule", status: "completed", conclusion: "success" },
    ]),
    { decision: "wait", status: "missing", conclusion: "" },
  );
});

test("waits while the push CI run is still active", () => {
  assert.deepEqual(
    nightlyCiDecision([
      { event: "push", status: "in_progress", conclusion: null },
    ]),
    { decision: "wait", status: "in_progress", conclusion: "" },
  );
});

test("allows publishing only after the push CI run succeeds", () => {
  assert.deepEqual(
    nightlyCiDecision([
      { event: "push", status: "completed", conclusion: "success" },
    ]),
    { decision: "publish", status: "completed", conclusion: "success" },
  );
});

test("skips a red, cancelled, or skipped push CI run without publishing", () => {
  for (const conclusion of ["failure", "cancelled", "skipped"]) {
    assert.deepEqual(
      nightlyCiDecision([{ event: "push", status: "completed", conclusion }]),
      { decision: "skip", status: "completed", conclusion },
    );
  }
});

test("rejects malformed API output instead of treating it as green", () => {
  assert.throws(
    () => nightlyCiDecision({ status: "completed", conclusion: "success" }),
    {
      name: "TypeError",
      message: "GitHub Actions runs must be a JSON array",
    },
  );
});

test("CLI reads piped JSON and prints the publishing decision", () => {
  const output = execFileSync("node", ["scripts/nightly-ci-status.mjs"], {
    encoding: "utf8",
    input: JSON.stringify([
      { event: "push", status: "completed", conclusion: "success" },
    ]),
  });

  assert.equal(
    output,
    JSON.stringify({
      decision: "publish",
      status: "completed",
      conclusion: "success",
    }),
  );
});
