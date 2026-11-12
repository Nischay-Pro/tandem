import { execFileSync, spawnSync } from "node:child_process";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";

const repoRoot = execFileSync("git", ["rev-parse", "--show-toplevel"], {
  encoding: "utf8",
}).trim();
const apiRoot = path.join(repoRoot, "services/api");
const stagedPaths = execFileSync(
  "git",
  ["diff", "--cached", "--name-only", "--diff-filter=ACMR"],
  { encoding: "utf8" },
)
  .split("\n")
  .filter(Boolean);
const stagedSources = stagedPaths.filter(
  (file) => file.startsWith("services/api/src/") && file.endsWith(".ts"),
);

if (stagedSources.length === 0) process.exit(0);

const testFiles = listFiles(path.join(apiRoot, "test"), (file) =>
  file.endsWith(".test.ts"),
);
const vitest = path.join(apiRoot, "node_modules/.bin/vitest");

for (const source of stagedSources) {
  assertNoUnstagedChange(source);
  const modulePath = `src/${source.slice("services/api/src/".length)}`;
  const importPath = `../${modulePath.replace(/\.ts$/u, ".js")}`;
  const matchingTests = testFiles.filter((file) =>
    readFileSync(file, "utf8").includes(importPath),
  );
  const unitTests = matchingTests.filter(
    (file) => !file.endsWith(".integration.test.ts"),
  );

  if (
    ["src/main.ts", "src/server.ts"].includes(modulePath) &&
    matchingTests.some((file) => file.endsWith(".integration.test.ts"))
  ) {
    process.stdout.write(
      "API coverage: production startup wiring is checked by the database-backed integration suite in CI.\n",
    );
    continue;
  }

  if (modulePath === "src/write-journal/postgres-write-journal.ts") {
    const integrationTests = matchingTests.filter((file) =>
      file.endsWith(".integration.test.ts"),
    );
    if (integrationTests.length === 0) {
      throw new Error(
        "PostgreSQL write-journal changes require a database integration test",
      );
    }
    for (const test of integrationTests)
      assertNoUnstagedChange(`services/api/${test}`);

    if (!process.env.DATABASE_URL) {
      process.stdout.write(
        "API coverage: PostgreSQL write-journal integration tests are present; set DATABASE_URL for local coverage (CI runs the full database coverage gate).\n",
      );
      continue;
    }

    process.stdout.write(
      `API coverage: checking ${modulePath} with the full database-backed suite\n`,
    );
    const result = spawnSync(
      vitest,
      ["run", "--coverage.enabled", `--coverage.include=${modulePath}`],
      { cwd: apiRoot, stdio: "inherit" },
    );
    if (result.error) throw result.error;
    if (result.status !== 0) process.exit(result.status ?? 1);
    continue;
  }

  if (unitTests.length === 0) {
    process.stdout.write(
      `API coverage: no focused unit test imports ${modulePath}; CI will run the integration coverage gate.\n`,
    );
    continue;
  }

  const relativeTests = unitTests.map((file) => path.relative(apiRoot, file));
  for (const test of relativeTests)
    assertNoUnstagedChange(`services/api/${test}`);

  process.stdout.write(
    `API coverage: checking ${modulePath} with ${relativeTests.join(", ")}\n`,
  );
  const result = spawnSync(
    vitest,
    [
      "run",
      "--coverage.enabled",
      `--coverage.include=${modulePath}`,
      ...relativeTests,
    ],
    { cwd: apiRoot, stdio: "inherit" },
  );
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}

function assertNoUnstagedChange(file) {
  const unstaged = execFileSync("git", ["diff", "--name-only", "--", file], {
    encoding: "utf8",
  }).trim();
  const untracked = execFileSync(
    "git",
    ["ls-files", "--others", "--exclude-standard", "--", file],
    { encoding: "utf8" },
  ).trim();
  if (unstaged || untracked) {
    process.stderr.write(
      `Refusing API coverage for ${file} while it differs from the staged version. Stage or discard the remaining change first.\n`,
    );
    process.exit(1);
  }
}

function listFiles(directory, predicate) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory()
      ? listFiles(file, predicate)
      : predicate(file)
        ? [file]
        : [];
  });
}
