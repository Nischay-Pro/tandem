// Writes the empty budget Tandem uploads for a new budget: Actual's own new-budget file, with every migration run.
// Run it again whenever @actual-app/api is updated, so new budgets match what Actual itself creates.
import fs from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const require = createRequire(path.join(root, "services/api/package.json"));
const api = require("@actual-app/api");
const output = path.join(
  root,
  "mobile/composeApp/src/commonMain/composeResources/files/empty-budget.sqlite",
);

const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), "tandem-empty-budget-"));
try {
  await api.init({ dataDir });
  await api.internal.send("create-budget", {
    budgetName: "Empty",
    avoidUpload: true,
  });
  await api.internal.send("close-budget");
  const [budget] = fs
    .readdirSync(dataDir)
    .filter((name) => fs.statSync(path.join(dataDir, name)).isDirectory());
  fs.mkdirSync(path.dirname(output), { recursive: true });
  fs.copyFileSync(path.join(dataDir, budget, "db.sqlite"), output);
  process.stdout.write(`Wrote ${path.relative(root, output)}\n`);
} finally {
  await api.shutdown();
  fs.rmSync(dataDir, { recursive: true, force: true });
}
