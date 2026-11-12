import { readFile } from "node:fs/promises";
import { Writable } from "node:stream";
import { describe, expect, it, vi } from "vitest";
import { buildApiApp } from "../src/app.js";
import type { DeviceAuthorization } from "../src/auth/device-auth.js";
import type { ActualOpenIdLogin } from "../src/auth/actual-openid-login.js";
import { createOpenApiDocument } from "../src/openapi/contract.js";
import {
  buildOpenApiLocalReference,
  withSharedApiErrorResponses,
} from "../src/openapi/error-responses.js";

const errorStatuses = [400, 401, 404, 409, 413, 415, 429, 500, 502, 503];
const errorStatusNames: Record<number, string> = {
  400: "BadRequest",
  401: "Unauthorized",
  404: "NotFound",
  409: "Conflict",
  413: "PayloadTooLarge",
  415: "UnsupportedMediaType",
  429: "TooManyRequests",
  500: "InternalServerError",
  502: "BadGateway",
  503: "ServiceUnavailable",
};

interface ApiErrorDocument {
  paths: Record<string, Record<string, ApiOperation>>;
  components: {
    responses: Record<string, ApiResponseComponent>;
    schemas: Record<string, unknown>;
  };
}

interface ApiOperation {
  operationId?: string;
  requestBody?: unknown;
  parameters?: unknown[];
  security?: Array<Record<string, unknown>>;
  responses: Record<string, ApiResponseReference>;
  "x-tandem-rate-limited"?: boolean;
}

interface ApiResponseReference {
  $ref?: string;
  [key: string]: unknown;
}

interface ApiResponseComponent {
  content?: Record<
    string,
    {
      schema?: { $ref?: string };
      example?: { error?: { code?: string; message?: string } };
    }
  >;
}

function apiOperations(document: ApiErrorDocument) {
  const methods = new Set([
    "get",
    "put",
    "post",
    "delete",
    "options",
    "head",
    "patch",
    "trace",
  ]);
  return Object.entries(document.paths).flatMap(([path, pathItem]) =>
    Object.entries(pathItem)
      .filter(([method]) => methods.has(method))
      .map(([method, operation]) => ({ path, method, operation })),
  );
}

function fillPathParameters(path: string): string {
  return path.replace(/\{([^}]+)\}/gu, (_match, name: string) => {
    if (name === "state") return "A".repeat(43);
    if (name === "deviceId") return "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    return "test-resource";
  });
}

function expectApiErrorBody(body: unknown) {
  expect(body).toMatchObject({
    error: {
      code: expect.any(String),
      message: expect.any(String),
    },
  });
  const error = (body as { error: { code: string; message: string } }).error;
  expect(error.code.length).toBeGreaterThan(0);
  expect(error.message.length).toBeGreaterThan(0);
}

describe("API error contract", () => {
  it("uses named shared responses and documents required statuses on every operation", async () => {
    const document =
      (await createOpenApiDocument()) as unknown as ApiErrorDocument;
    const operations = apiOperations(document);
    expect(operations.length).toBeGreaterThan(0);
    expect(document.components.schemas.TandemApiError).toBeDefined();

    for (const { path, method, operation } of operations) {
      const responses = operation.responses;
      expect(
        responses["500"],
        `${method.toUpperCase()} ${path} needs 500`,
      ).toBeDefined();

      if (operation.requestBody !== undefined || operation.parameters?.length) {
        expect(
          responses["400"],
          `${method.toUpperCase()} ${path} needs 400`,
        ).toBeDefined();
      }
      if (operation.requestBody !== undefined) {
        expect(
          responses["413"],
          `${method.toUpperCase()} ${path} needs 413`,
        ).toBeDefined();
        expect(
          responses["415"],
          `${method.toUpperCase()} ${path} needs 415`,
        ).toBeDefined();
      }
      if (operation.security?.some((scheme) => "bearerAuth" in scheme)) {
        expect(
          responses["401"],
          `${method.toUpperCase()} ${path} needs 401`,
        ).toBeDefined();
      }
      if (operation["x-tandem-rate-limited"]) {
        expect(
          responses["429"],
          `${method.toUpperCase()} ${path} needs 429`,
        ).toBeDefined();
      }

      for (const [status, response] of Object.entries(responses)) {
        if (Number(status) < 400) continue;
        const name = errorStatusNames[Number(status)];
        expect(
          name,
          `${method.toUpperCase()} ${path} has undocumented HTTP ${status}`,
        ).toBeDefined();
        expect(response).toEqual({ $ref: `#/components/responses/${name}` });
        const component = document.components.responses[name!];
        expect(component!.content?.["application/json"]?.schema).toEqual({
          $ref: "#/components/schemas/TandemApiError",
        });
      }
    }

    for (const status of errorStatuses) {
      const component =
        document.components.responses[errorStatusNames[status]!];
      const example = component!.content?.["application/json"]?.example?.error;
      expect(
        example,
        `HTTP ${status} needs a documented error example`,
      ).toBeDefined();
      expect(example!.code).toMatch(/^[a-z][a-z0-9_]*$/u);
      expect(example!.message).toMatch(/[.!?]$/u);
    }

    const apiReadme = await readFile(
      new URL("../../../docs/api/README.md", import.meta.url),
      "utf8",
    );
    for (const status of errorStatuses) {
      expect(
        apiReadme,
        `docs/api/README.md is missing HTTP ${status}`,
      ).toContain(`| \`${status}\` |`);
    }
  });

  it("rejects an error status without a named shared response", async () => {
    const document =
      (await createOpenApiDocument()) as unknown as ApiErrorDocument;
    const operation = document.paths["/api/v1/health/live"]!.get!;
    operation.responses["418"] = { description: "Undocumented error" };

    expect(() => withSharedApiErrorResponses(document as never)).toThrow(
      "No shared API error response is defined for HTTP 418 at GET /api/v1/health/live",
    );
  });

  it("requires the shared schema and skips OpenAPI path-item metadata", () => {
    expect(buildOpenApiLocalReference({ $id: "TandemApiError" }, 2)).toBe(
      "TandemApiError",
    );
    expect(buildOpenApiLocalReference({}, 4)).toBe("def-4");

    expect(() => withSharedApiErrorResponses({ paths: {} })).toThrow(
      "OpenAPI document is missing the TandemApiError schema",
    );

    const document = withSharedApiErrorResponses({
      paths: {
        "/not-a-path-item": null,
        "/path-item-metadata": {
          parameters: [],
          get: null,
          post: {
            requestBody: { content: {} },
            parameters: "not-an-array",
            security: [null, {}],
            responses: { default: { description: "Default response" } },
          },
        },
      },
      components: { schemas: { TandemApiError: {} } },
    });

    const operation = (
      document.paths["/path-item-metadata"] as {
        post: { responses: Record<string, unknown> };
      }
    ).post;
    expect(operation.responses).toMatchObject({
      "400": { $ref: "#/components/responses/BadRequest" },
      "413": { $ref: "#/components/responses/PayloadTooLarge" },
      "415": { $ref: "#/components/responses/UnsupportedMediaType" },
      "500": { $ref: "#/components/responses/InternalServerError" },
    });
    expect(operation.responses["401"]).toBeUndefined();
    expect(operation.responses["429"]).toBeUndefined();
  });

  it("documents the actual error response from every operation and includes request IDs", async () => {
    const document =
      (await createOpenApiDocument()) as unknown as ApiErrorDocument;
    const operations = apiOperations(document);
    const lines: string[] = [];
    const stream = new Writable({
      write(chunk, _encoding, callback) {
        lines.push(String(chunk));
        callback();
      },
    });
    const secret = "private failure detail that must not escape";
    const app = await buildApiApp(
      { ping: vi.fn(async () => {}), close: vi.fn(async () => {}) },
      {
        logger: { level: "info", stream },
        deviceAuth: {
          authenticate: vi.fn(async () => {
            throw new Error(secret);
          }),
        } as unknown as DeviceAuthorization,
        actualOpenIdLogin: {} as ActualOpenIdLogin,
      },
    );

    try {
      for (const { path, method, operation } of operations) {
        const response = await app.inject({
          method: method.toUpperCase() as "GET",
          url: fillPathParameters(path),
        });
        expect(response.statusCode, `${method.toUpperCase()} ${path}`).toBe(
          500,
        );
        expect(operation.responses["500"]).toBeDefined();
        expect(response.headers["x-request-id"]).toMatch(/^[0-9a-f-]{36}$/iu);
        expectApiErrorBody(response.json());
        expect(response.body).not.toContain(secret);
      }
    } finally {
      await app.close();
    }

    expect(lines).toHaveLength(operations.length);
    expect(lines.join("\n")).not.toContain(secret);
    expect(lines.join("\n")).not.toContain("stack");
  });

  it("returns only documented shared errors for body, authorization, and readiness failures", async () => {
    const document =
      (await createOpenApiDocument()) as unknown as ApiErrorDocument;
    const deviceAuth = {
      authenticate: vi.fn(async () => undefined),
    } as unknown as DeviceAuthorization;
    const app = await buildApiApp(
      {
        ping: vi.fn(async () => {
          throw new Error("private database detail");
        }),
        close: vi.fn(async () => {}),
      },
      { deviceAuth, actualOpenIdLogin: {} as ActualOpenIdLogin },
    );

    try {
      for (const { path, method, operation } of apiOperations(document)) {
        let response;
        if (operation.requestBody !== undefined) {
          response = await app.inject({
            method: method.toUpperCase() as "POST",
            url: fillPathParameters(path),
            headers: { "content-type": "text/plain" },
            payload: "not JSON",
          });
          expect(response.statusCode, `${method.toUpperCase()} ${path}`).toBe(
            415,
          );
        } else if (
          operation.security?.some((scheme) => "bearerAuth" in scheme)
        ) {
          response = await app.inject({
            method: method.toUpperCase() as "GET",
            url: fillPathParameters(path),
          });
          expect(response.statusCode, `${method.toUpperCase()} ${path}`).toBe(
            401,
          );
        } else if (operation.operationId === "getReadiness") {
          response = await app.inject({ method: "GET", url: path });
          expect(response.statusCode).toBe(503);
        } else if (operation.operationId === "getActualLoginMethods") {
          response = await app.inject({ method: "GET", url: path });
          expect(response.statusCode).toBe(503);
        } else if (path.includes("{")) {
          response = await app.inject({
            method: method.toUpperCase() as "GET",
            url: fillPathParameters(path),
          });
        } else {
          response = await app.inject({ method: "GET", url: path });
          expect(response.statusCode).toBe(200);
        }

        if (response.statusCode >= 400) {
          expect(
            operation.responses[String(response.statusCode)],
          ).toBeDefined();
          expectApiErrorBody(response.json());
          expect(response.headers["x-request-id"]).toMatch(/^[0-9a-f-]{36}$/iu);
        }
      }
    } finally {
      await app.close();
    }
  });
});
