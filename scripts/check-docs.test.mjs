import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import {
  findBrokenLinks,
  findStaleTerms,
  versionMentionErrors,
} from "./check-docs.mjs";

test("checks local file links and GitHub-style heading anchors", () => {
  const root = mkdtempSync(join(tmpdir(), "tandem-doc-links-"));
  try {
    mkdirSync(join(root, "docs"));
    writeFileSync(
      join(root, "README.md"),
      "[guide](docs/guide.md?raw=1#quick-start)\n",
    );
    writeFileSync(join(root, "docs/guide.md"), "# Quick start\n");

    assert.deepEqual(findBrokenLinks(root, ["README.md", "docs/guide.md"]), []);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("reports missing files and anchors while ignoring code and external links", () => {
  const root = mkdtempSync(join(tmpdir(), "tandem-doc-links-"));
  const tick = String.fromCharCode(96);
  try {
    writeFileSync(
      join(root, "README.md"),
      [
        "[missing file](nope.md)",
        "[missing heading](guide.md#not-there)",
        "[external](https://example.com)",
        tick + "[inline code](also-missing.md)" + tick,
        tick.repeat(3) + "md",
        "[fenced code](still-missing.md)",
        tick.repeat(3),
      ].join("\n"),
    );
    writeFileSync(join(root, "guide.md"), "# Present\n");

    assert.deepEqual(findBrokenLinks(root, ["README.md", "guide.md"]), [
      join(root, "README.md") + ": 1: missing local link nope.md",
      join(root, "README.md") +
        ": 2: missing heading #not-there in " +
        join(root, "guide.md"),
    ]);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("accepts source-aligned versions and reports stale or missing versions", () => {
  const pattern = /Android Gradle plugin:\s*([\d.]+)/i;
  assert.deepEqual(
    versionMentionErrors(
      "mobile.md",
      "Android Gradle plugin",
      "Android Gradle plugin: 9.5.0",
      pattern,
      "9.5.0",
    ),
    [],
  );
  assert.deepEqual(
    versionMentionErrors(
      "mobile.md",
      "Android Gradle plugin",
      "Android Gradle plugin: 9.3.1",
      pattern,
      "9.5.0",
    ),
    [
      "mobile.md documents Android Gradle plugin 9.3.1; source specifies 9.5.0.",
    ],
  );
  assert.deepEqual(
    versionMentionErrors(
      "mobile.md",
      "Android Gradle plugin",
      "No tool version here.",
      pattern,
      "9.5.0",
    ),
    ["mobile.md does not document Android Gradle plugin."],
  );
});

test("flags retired terminology but permits it in a history file", () => {
  const root = mkdtempSync(join(tmpdir(), "tandem-doc-terms-"));
  try {
    writeFileSync(
      join(root, "guide.md"),
      "Old pairing and change-feed wording.\n",
    );
    writeFileSync(
      join(root, "HISTORY.md"),
      "Pairing was used in an old release.\n",
    );

    assert.deepEqual(findStaleTerms(root, ["guide.md", "HISTORY.md"]), [
      "guide.md: 1: stale term pairing",
      "guide.md: 1: stale term change-feed",
    ]);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
