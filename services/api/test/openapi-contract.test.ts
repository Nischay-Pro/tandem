import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import {
  createOpenApiDocument,
  serializeOpenApiDocument,
  syncOpenApiContract,
} from "../src/openapi/contract.js";

let temporaryDirectory: string | undefined;

afterEach(async () => {
  if (temporaryDirectory) {
    await rm(temporaryDirectory, { recursive: true, force: true });
    temporaryDirectory = undefined;
  }
});

async function createTemporaryContractPath(): Promise<string> {
  temporaryDirectory = await mkdtemp(join(tmpdir(), "tandem-openapi-"));
  return join(temporaryDirectory, "openapi-v1.json");
}

describe("OpenAPI contract generation", () => {
  it("builds the versioned contract from the production Fastify routes", async () => {
    const document = await createOpenApiDocument();

    expect(document.openapi).toBe("3.1.0");
    expect(document.info.version).toBe("0.1.0");
    expect(document.paths["/api/v1/auth/pair"]).toBeDefined();
    expect(
      document.paths[
        "/api/v1/budgets/{budgetId}/transactions/{transactionId}/operations"
      ],
    ).toBeDefined();
  });

  it("writes a stable contract and verifies it without changing the file", async () => {
    const outputPath = await createTemporaryContractPath();

    await syncOpenApiContract(outputPath);
    const generated = await readFile(outputPath, "utf8");
    await syncOpenApiContract(outputPath, true);

    expect(generated.endsWith("\n")).toBe(true);
    expect(JSON.parse(generated).openapi).toBe("3.1.0");
    await expect(serializeOpenApiDocument(JSON.parse(generated))).resolves.toBe(
      generated,
    );
  });

  it("reports a stale checked-in contract", async () => {
    const outputPath = await createTemporaryContractPath();
    await writeFile(outputPath, "{}\n");

    await expect(syncOpenApiContract(outputPath, true)).rejects.toThrow(
      "OpenAPI contract is stale",
    );
  });

  it("reports a missing checked-in contract with a regeneration command", async () => {
    const outputPath = await createTemporaryContractPath();

    await expect(syncOpenApiContract(outputPath, true)).rejects.toThrow(
      "OpenAPI contract is missing or unreadable",
    );
  });

  it("propagates contract write failures", async () => {
    const directoryPath = await createTemporaryContractPath();
    const outputPath = join(directoryPath, "missing", "openapi-v1.json");

    await expect(syncOpenApiContract(outputPath)).rejects.toMatchObject({
      code: "ENOENT",
    });
  });
});
