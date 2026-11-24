// Writes the extension service's OpenAPI description, or checks that the committed copy is current.
import { buildApp } from "../src/app.js";
import { InMemoryExtensionStore } from "../src/store.js";
import { readFile, writeFile } from "node:fs/promises";
import process from "node:process";
import { format } from "prettier";

const target = "../../docs/api/openapi-v1.json";
const app = await buildApp({
  store: new InMemoryExtensionStore(),
  access: { check: async () => "allowed" },
  actualServerUrl: "https://actual.example",
});
await app.ready();
const document = await format(JSON.stringify(app.swagger()), {
  parser: "json",
});
await app.close();
if (process.argv.includes("--check")) {
  if ((await readFile(target, "utf8").catch(() => "")) !== document) {
    console.error(
      "docs/api/openapi-v1.json is out of date. Run pnpm api:openapi:generate.",
    );
    process.exitCode = 1;
  }
} else {
  await writeFile(target, document);
}
