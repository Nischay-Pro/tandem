import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { basename, dirname, extname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import process from "node:process";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");

function markdownFiles() {
  return execFileSync(
    "git",
    ["ls-files", "--cached", "--others", "--exclude-standard", "--", "*.md"],
    { cwd: root, encoding: "utf8" },
  )
    .split("\n")
    .filter(Boolean);
}

function withoutCode(text) {
  return text
    .replace(/^ {0,3}(\x60{3,}|~{3,})[^\n]*\n[\s\S]*?^ {0,3}\1[ \t]*$/gm, "")
    .replace(/(\x60+)[^\x60\n]*\1/g, "")
    .replace(/<!--[\s\S]*?-->/g, "");
}

function headingIds(text) {
  const ids = new Set();
  const counts = new Map();
  for (const match of withoutCode(text).matchAll(
    /^ {0,3}#{1,6}\s+(.+?)\s*#*\s*$/gm,
  )) {
    const slug = match[1]
      .replace(/<[^>]*>/g, "")
      .replace(/!?\[([^\]]*)\]\([^)]*\)/g, "$1")
      .replace(/[\x60*_~]/g, "")
      .toLowerCase()
      .replace(/[^\p{L}\p{N}_ -]/gu, "")
      .trim()
      .replace(/\s+/g, "-")
      .replace(/-+/g, "-");
    const count = counts.get(slug) ?? 0;
    counts.set(slug, count + 1);
    ids.add(count ? slug + "-" + count : slug);
  }
  for (const match of withoutCode(text).matchAll(/\bid=["']([^"']+)["']/g)) {
    ids.add(match[1]);
  }
  return ids;
}

export function findBrokenLinks(repoRoot, files) {
  const documents = new Map(
    files.map((file) => [
      resolve(repoRoot, file),
      readFileSync(resolve(repoRoot, file), "utf8"),
    ]),
  );
  const errors = [];
  const linkPattern =
    /!?\[[^\]]*\]\(\s*(<[^>]+>|[^)\s]+)(?:\s+(?:"[^"]*"|'[^']*'))?\s*\)/g;

  for (const [source, text] of documents) {
    for (const match of withoutCode(text).matchAll(linkPattern)) {
      let href = match[1].replace(/^<|>$/g, "");
      if (/^(?:[a-z][a-z\d+.-]*:|\/\/)/i.test(href)) continue;
      const line = text.slice(0, match.index).split("\n").length;
      const hash = href.indexOf("#");
      const pathAndQuery = hash < 0 ? href : href.slice(0, hash);
      const path = pathAndQuery.split("?")[0];
      let fragment = hash < 0 ? "" : href.slice(hash + 1);
      let target;
      try {
        target = path.startsWith("/")
          ? resolve(repoRoot, decodeURIComponent(path.slice(1)))
          : resolve(dirname(source), decodeURIComponent(path || source));
        fragment = decodeURIComponent(fragment);
      } catch {
        errors.push(source + ": " + line + ": invalid URL escape in " + href);
        continue;
      }
      if (!existsSync(target) && existsSync(join(target, "README.md"))) {
        target = join(target, "README.md");
      }
      if (!existsSync(target)) {
        errors.push(source + ": " + line + ": missing local link " + href);
      } else if (fragment && extname(target).toLowerCase() === ".md") {
        const targetText =
          documents.get(target) ?? readFileSync(target, "utf8");
        if (!headingIds(targetText).has(fragment.toLowerCase())) {
          errors.push(
            source +
              ": " +
              line +
              ": missing heading #" +
              fragment +
              " in " +
              target,
          );
        }
      }
    }
  }
  return errors;
}

export function findStaleTerms(repoRoot, files) {
  const staleTerm = /\b(pairing|change[ -]feed)\b/gi;
  const history = /^(?:changelog|history)(?:[.-]|$)/i;
  const errors = [];
  for (const file of files) {
    if (history.test(basename(file))) continue;
    const text = readFileSync(resolve(repoRoot, file), "utf8");
    for (const match of text.matchAll(staleTerm)) {
      errors.push(
        file +
          ": " +
          text.slice(0, match.index).split("\n").length +
          ": stale term " +
          match[0],
      );
    }
  }
  return errors;
}

export function versionMentionErrors(
  file,
  label,
  text,
  pattern,
  expected,
  pairedExpected,
) {
  const errors = [];
  const matcher = new RegExp(
    pattern.source,
    pattern.flags.replace("g", "") + "g",
  );
  const matches = [...text.matchAll(matcher)];
  if (!matches.length) return [file + " does not document " + label + "."];
  for (const match of matches) {
    if (match[1] !== expected) {
      errors.push(
        file +
          " documents " +
          label +
          " " +
          match[1] +
          "; source specifies " +
          expected +
          ".",
      );
    }
    if (pairedExpected && match[2] !== pairedExpected) {
      errors.push(
        file +
          " documents minSdk " +
          match[2] +
          "; source specifies " +
          pairedExpected +
          ".",
      );
    }
  }
  return errors;
}

function sourceVersion(file, text, pattern, errors) {
  const match = text.match(pattern);
  if (!match)
    errors.push("Could not read a toolchain version from " + file + ".");
  return match?.[1];
}

export function checkVersions(repoRoot = root) {
  const get = (file) => readFileSync(join(repoRoot, file), "utf8");
  const packageJson = JSON.parse(get("package.json"));
  const apiPackage = JSON.parse(get("services/api/package.json"));
  const catalog = get("mobile/gradle/libs.versions.toml");
  const production = get("deploy/compose/production.yml");
  const compatibility = get("docker-compose.dev.yml");
  const action = get(".github/actions/setup-mobile-build/action.yml");
  const appBuild = get("mobile/androidApp/build.gradle.kts");
  const errors = [];
  const getSource = (file, text, pattern) =>
    sourceVersion(file, text, pattern, errors);
  const catalogVersion = (key) =>
    getSource(
      "mobile/gradle/libs.versions.toml",
      catalog,
      new RegExp("^" + key + '\\s*=\\s*"([^"]+)"', "m"),
    );
  const versions = {
    node: getSource("mise.toml", get("mise.toml"), /^node\s*=\s*"([^"]+)"/m),
    pnpm: packageJson.packageManager?.match(/^pnpm@(.+)$/)?.[1],
    postgres: getSource(
      "deploy/compose/production.yml",
      production,
      /^\s+image:\s+postgres:([\d.]+)-/m,
    ),
    actualServer: getSource(
      "docker-compose.dev.yml",
      compatibility,
      /^\s+image:\s+ghcr\.io\/actualbudget\/actual:([\d.]+)@/m,
    ),
    actualApi: apiPackage.dependencies?.["@actual-app/api"],
    kotlin: catalogVersion("kotlin"),
    androidGradlePlugin: catalogVersion("androidGradlePlugin"),
    openapiGenerator: catalogVersion("openapiKmpGen"),
    ktlint: catalogVersion("ktlintGradle"),
    kover: catalogVersion("kover"),
    gradle: getSource(
      "Gradle wrapper",
      get("mobile/gradle/wrapper/gradle-wrapper.properties"),
      /gradle-([\d.]+)-bin\.zip/,
    ),
    java: getSource("CI Java setup", action, /JAVA_HOME_(\d+)_/),
    compileSdk: getSource(
      "mobile/androidApp/build.gradle.kts",
      appBuild,
      /compileSdk\s*=\s*(\d+)/,
    ),
    minSdk: getSource(
      "mobile/androidApp/build.gradle.kts",
      appBuild,
      /minSdk\s*=\s*(\d+)/,
    ),
  };
  if (!versions.pnpm)
    errors.push("Could not read pnpm version from package.json.");
  if (!versions.actualApi)
    errors.push(
      "Could not read @actual-app/api version from its package manifest.",
    );
  if (versions.actualApi !== versions.actualServer) {
    errors.push(
      "@actual-app/api and its compatibility server use different versions.",
    );
  }

  const sourceChecks = [
    [".node-version", /^([\d.]+)/, versions.node],
    ["services/api/Dockerfile", /^FROM node:([\d.]+)-/m, versions.node],
    ["services/api/Dockerfile.dev", /^FROM node:([\d.]+)-/m, versions.node],
    ["services/api/Dockerfile", /pnpm@([\d.]+)/, versions.pnpm],
    ["services/api/Dockerfile.dev", /pnpm@([\d.]+)/, versions.pnpm],
    [
      ".github/actions/setup-mobile-build/action.yml",
      /version:\s*([\d.]+)/,
      versions.pnpm,
    ],
    [".github/workflows/ci.yml", /version:\s*([\d.]+)/, versions.pnpm],
    [
      "docker-compose.dev.yml",
      /^\s+image:\s+postgres:([\d.]+)-/m,
      versions.postgres,
    ],
    [
      "mobile/api-client/build.gradle.kts",
      /compileSdk\s*=\s*(\d+)/,
      versions.compileSdk,
    ],
    [
      "mobile/api-client/build.gradle.kts",
      /minSdk\s*=\s*(\d+)/,
      versions.minSdk,
    ],
  ];
  for (const [file, pattern, expected] of sourceChecks) {
    const actual = sourceVersion(file, get(file), pattern, errors);
    if (actual !== expected)
      errors.push(
        file + " has version " + actual + "; expected " + expected + ".",
      );
  }

  const checks = [
    [
      "docs/develop/build-and-test.md",
      "Node.js",
      /\bNode\.js\s+\x60?(\d+(?:\.\d+){1,2})/gi,
      versions.node,
    ],
    [
      "docs/develop/build-and-test.md",
      "pnpm",
      /\bpnpm\s+\x60?(\d+(?:\.\d+){1,2})/g,
      versions.pnpm,
    ],
    [
      "docs/develop/build-and-test.md",
      "PostgreSQL",
      /\bPostgreSQL\s+\x60?(\d+\.\d+(?:\.\d+)?)/gi,
      versions.postgres,
    ],
    ["docs/develop/build-and-test.md", "Java", /Java\s+(\d+)/i, versions.java],
    [
      "docs/develop/toolchain.md",
      "Kotlin plugin",
      /Kotlin Multiplatform Gradle plugin:\s*([\d.]+)/i,
      versions.kotlin,
    ],
    [
      "docs/develop/toolchain.md",
      "Android Gradle plugin",
      /Android Gradle plugin:\s*([\d.]+)/i,
      versions.androidGradlePlugin,
    ],
    [
      "docs/develop/toolchain.md",
      "Gradle wrapper",
      /Gradle wrapper:\s*([\d.]+)/i,
      versions.gradle,
    ],
    [
      "docs/develop/toolchain.md",
      "OpenAPI KMP Gen",
      /OpenAPI KMP Gen:\s*([\d.]+)/i,
      versions.openapiGenerator,
    ],
    [
      "docs/develop/toolchain.md",
      "ktlint Gradle plugin",
      /ktlint Gradle plugin:\s*([\d.]+)/i,
      versions.ktlint,
    ],
    [
      "docs/develop/toolchain.md",
      "Kover",
      /Kover:\s*([\d.]+)/i,
      versions.kover,
    ],
    [
      "docs/develop/toolchain.md",
      "Java runtime",
      /JVM runtime:\s*Java\s+(\d+)/i,
      versions.java,
    ],
    [
      "docs/develop/toolchain.md",
      "Android SDK",
      /Android compile SDK:\s*(\d+),\s*minSdk:\s*(\d+)/i,
      versions.compileSdk,
      versions.minSdk,
    ],
    [
      "docs/concepts/actual-compatibility.md",
      "Actual server",
      /Actual server\s+([\d.]+)/i,
      versions.actualServer,
    ],
    [
      "docs/concepts/actual-compatibility.md",
      "@actual-app/api",
      /@actual-app\/api\x60?\s+([\d.]+)/i,
      versions.actualApi,
    ],
  ];
  for (const [file, label, pattern, expected, paired] of checks) {
    errors.push(
      ...versionMentionErrors(
        file,
        label,
        get(file),
        pattern,
        expected,
        paired,
      ),
    );
  }
  return errors;
}

function run() {
  const files = markdownFiles();
  const errors = [
    ...findBrokenLinks(root, files),
    ...findStaleTerms(root, files),
    ...checkVersions(),
  ];
  if (errors.length) {
    process.stderr.write("- " + errors.join("\n- ") + "\n");
    process.exitCode = 1;
  } else {
    process.stdout.write(
      "Documentation links and toolchain versions are valid (" +
        files.length +
        " Markdown files checked).\n",
    );
  }
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  run();
}
