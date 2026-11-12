import { readFile, writeFile } from "node:fs/promises";
import { format } from "prettier";
import { buildApiApp } from "../app.js";
import type { TransactionApiPort } from "../actual/transaction-api.js";
import type { DeviceAuthorization } from "../auth/device-auth.js";
import type { ActualOpenIdLogin } from "../auth/actual-openid-login.js";
import { withSharedApiErrorResponses } from "./error-responses.js";

export interface OpenApiDocument {
  openapi: string;
  info: { title: string; version: string; description?: string };
  paths: Record<string, unknown>;
  [key: string]: unknown;
}

export async function createOpenApiDocument(): Promise<OpenApiDocument> {
  const app = await buildApiApp(
    {
      async ping() {},
      async close() {},
    },
    {
      deviceAuth: {
        async authenticate() {
          return undefined;
        },
      } as unknown as DeviceAuthorization,
      transactionApi: {} as TransactionApiPort,
      actualOpenIdLogin: {} as ActualOpenIdLogin,
    },
  );

  try {
    await app.inject({
      method: "GET",
      url: "/api/v1/health/ready",
    });
    return withSharedApiErrorResponses(app.swagger() as OpenApiDocument);
  } finally {
    await app.close();
  }
}

export function serializeOpenApiDocument(
  document: OpenApiDocument,
): Promise<string> {
  return format(JSON.stringify(document), { parser: "json" });
}

export async function syncOpenApiContract(
  outputPath: string,
  check = false,
): Promise<void> {
  const serialized = await serializeOpenApiDocument(
    await createOpenApiDocument(),
  );
  if (!check) {
    await writeFile(outputPath, serialized);
    return;
  }

  let current: string;
  try {
    current = await readFile(outputPath, "utf8");
  } catch (error) {
    throw new Error(
      "OpenAPI contract is missing or unreadable; run the OpenAPI generation command",
      { cause: error },
    );
  }
  if (current !== serialized) {
    throw new Error(
      "OpenAPI contract is stale; run the OpenAPI generation command",
    );
  }
}
