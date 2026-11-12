#!/usr/bin/env node

import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import process from "node:process";
import console from "node:console";
import { URL, fileURLToPath } from "node:url";

const repositoryRoot = resolve(fileURLToPath(new URL("..", import.meta.url)));
const imagePlaceholder =
  "    image: ${TANDEM_API_IMAGE:?Run the setup command to choose a digest-pinned API image}";

export function renderPinnedCompose(template, image) {
  if (!/^ghcr\.io\/nischay-pro\/tandem-api@sha256:[a-f0-9]{64}$/.test(image)) {
    throw new Error(
      "TANDEM_API_IMAGE must be the Tandem GHCR image pinned by sha256 digest.",
    );
  }

  const matches = template.split(imagePlaceholder).length - 1;
  if (matches !== 1) {
    throw new Error(
      `Expected exactly one API image placeholder, found ${matches}.`,
    );
  }

  return template.replace(imagePlaceholder, `    image: ${image}`);
}

async function main() {
  const [image, outputDirectory] = process.argv.slice(2);
  if (!image || !outputDirectory) {
    throw new Error(
      "Usage: render-pinned-compose.mjs <image@sha256:digest> <output-directory>",
    );
  }

  const output = resolve(outputDirectory);
  const template = await readFile(
    resolve(repositoryRoot, "deploy/compose/production.yml"),
    "utf8",
  );
  const environmentTemplate = await readFile(
    resolve(repositoryRoot, "deploy/compose/.env.example"),
    "utf8",
  );
  const pinnedEnvironment = environmentTemplate
    .split("\n")
    .filter((line) => !line.startsWith("TANDEM_API_IMAGE="))
    .join("\n")
    .replaceAll("deploy/compose/secrets/", "secrets/")
    .replace(
      "# Copy to deploy/compose/.env and chmod 600.",
      "# Copy to .env and chmod 600.",
    );

  await mkdir(output, { recursive: true });
  await writeFile(
    resolve(output, "production.yml"),
    renderPinnedCompose(template, image),
  );
  await writeFile(resolve(output, ".env.example"), pinnedEnvironment);
  await writeFile(
    resolve(output, "README.md"),
    [
      "# Tandem nightly Compose snapshot",
      "",
      `This Compose file pins the API image to ${image}. Copy .env.example to .env,`,
      "create the referenced secret files under secrets/, and set the Actual and proxy values",
      "before starting Compose. This artifact contains configuration only; it contains no credentials.",
      "",
    ].join("\n"),
  );
}

if (
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
