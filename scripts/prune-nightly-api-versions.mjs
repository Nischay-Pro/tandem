#!/usr/bin/env node

import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import process from "node:process";
import console from "node:console";
import { fileURLToPath } from "node:url";

export function obsoleteNightlyVersionIds(pages, keepDates = 14) {
  const versions = pages.flat();
  const datedVersions = versions.flatMap((version) => {
    const tags = version.metadata?.container?.tags ?? [];
    const dates = tags
      .map((tag) => /^nightly-(\d{8})$/.exec(tag)?.[1])
      .filter(Boolean);
    return dates.map((date) => ({
      id: String(version.id),
      date,
      hasRollingTag: tags.includes("nightly"),
    }));
  });
  const retainedDates = new Set(
    datedVersions
      .map(({ date }) => date)
      .sort((left, right) => right.localeCompare(left))
      .slice(0, keepDates),
  );

  return [
    ...new Set(
      datedVersions
        .filter(
          ({ date, hasRollingTag }) =>
            !retainedDates.has(date) && !hasRollingTag,
        )
        .map(({ id }) => id),
    ),
  ];
}

async function main() {
  const inputPath = process.argv[2];
  if (!inputPath) {
    throw new Error(
      "Usage: prune-nightly-api-versions.mjs <gh-package-versions.json>",
    );
  }

  const pages = JSON.parse(await readFile(resolve(inputPath), "utf8"));
  if (!Array.isArray(pages) || !pages.every(Array.isArray)) {
    throw new Error(
      "Expected paginated package versions from `gh api --slurp`.",
    );
  }
  for (const id of obsoleteNightlyVersionIds(pages)) {
    process.stdout.write(`${id}\n`);
  }
}

if (
  process.argv[1] &&
  resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))
) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
