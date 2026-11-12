import assert from "node:assert/strict";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve } from "node:path";
import { URL, fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";
import test from "node:test";
import { renderPinnedCompose } from "./render-pinned-compose.mjs";

const repositoryRoot = resolve(fileURLToPath(new URL("..", import.meta.url)));
const digest = `sha256:${"a".repeat(64)}`;
const image = `ghcr.io/nischay-pro/tandem-api@${digest}`;
const placeholder =
  "    image: ${TANDEM_API_IMAGE:?Run the setup command to choose a digest-pinned API image}";

test("pins exactly the production API image and rejects mutable or unrelated references", () => {
  const rendered = renderPinnedCompose(
    `services:\n  api:\n${placeholder}\n`,
    image,
  );

  assert.ok(rendered.includes(`    image: ${image}`));
  assert.throws(() =>
    renderPinnedCompose(`services:\n  api:\n${placeholder}\n`, "latest"),
  );
  assert.throws(() =>
    renderPinnedCompose("services:\n  api:\n    image: latest\n", image),
  );
});

test("writes a usable pinned Compose bundle without an API image setting", async () => {
  const output = await mkdtemp(resolve(tmpdir(), "tandem-compose-"));
  try {
    execFileSync("node", ["scripts/render-pinned-compose.mjs", image, output], {
      cwd: repositoryRoot,
      stdio: "pipe",
    });

    const compose = await readFile(resolve(output, "production.yml"), "utf8");
    const environment = await readFile(resolve(output, ".env.example"), "utf8");
    const readme = await readFile(resolve(output, "README.md"), "utf8");

    assert.ok(compose.includes(`    image: ${image}`));
    assert.ok(!compose.includes("${TANDEM_API_IMAGE"));
    assert.ok(!environment.includes("TANDEM_API_IMAGE="));
    assert.ok(
      environment.includes("POSTGRES_PASSWORD_FILE=secrets/postgres_password"),
    );
    assert.ok(readme.includes(image));
    assert.ok(readme.includes("contains no credentials"));
  } finally {
    await rm(output, { recursive: true, force: true });
  }
});
