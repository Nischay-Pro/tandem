#!/usr/bin/env node

import { existsSync, readdirSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { error } from "node:console";
import path from "node:path";
import process from "node:process";

const prettierSuffixes = new Set([
  ".cjs",
  ".css",
  ".html",
  ".js",
  ".json",
  ".md",
  ".mjs",
  ".ts",
  ".tsx",
  ".yaml",
  ".yml",
]);

const generatedDirectories = new Set([
  ".dev-tmp",
  ".git",
  ".gradle",
  ".idea",
  ".kotlin",
  ".pnpm-store",
  ".vscode",
  "DerivedData",
  "Pods",
  "__pycache__",
  "build",
  "coverage",
  "dist",
  "node_modules",
  "xcuserdata",
]);

function usage() {
  error("usage: check-prettier-files.mjs PRETTIER_OR_PNPM [--tracked|--all]");
  process.exit(2);
}

const [executable, mode] = process.argv.slice(2);
if (
  !executable ||
  process.argv.length > 4 ||
  (mode && !["--tracked", "--all"].includes(mode))
) {
  usage();
}

function listAllFiles(directory, files = []) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const filePath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      if (!generatedDirectories.has(entry.name)) listAllFiles(filePath, files);
    } else if (
      entry.isFile() &&
      prettierSuffixes.has(path.extname(entry.name).toLowerCase())
    ) {
      files.push(filePath);
    }
  }
  return files;
}

let files;
if (mode === "--all") {
  files = listAllFiles(".");
} else {
  const gitArgs =
    mode === "--tracked"
      ? ["ls-files", "-z"]
      : ["diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z"];
  const git = spawnSync("git", gitArgs, { encoding: "buffer" });
  if (git.status !== 0) {
    if (git.stderr) process.stderr.write(git.stderr);
    process.exit(git.status ?? 1);
  }
  files = git.stdout
    .toString("utf8")
    .split("\0")
    .filter(
      (file) => file && prettierSuffixes.has(path.extname(file).toLowerCase()),
    );
  if (mode === "--tracked") {
    files = files.filter((file) => existsSync(file));
  }
}

if (files.length === 0) process.exit(0);

const command =
  path.basename(executable) === "pnpm"
    ? [executable, "exec", "prettier", "--check", ...files]
    : [executable, "--check", ...files];
const result = spawnSync(command[0], command.slice(1), { stdio: "inherit" });
if (result.error) {
  error(result.error.message);
  process.exit(1);
}
process.exit(result.status ?? 1);
