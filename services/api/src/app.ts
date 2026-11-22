import Fastify, { type FastifyInstance, type FastifyReply } from "fastify";
import rateLimit from "@fastify/rate-limit";
import swagger from "@fastify/swagger";
import type { BudgetAccess } from "./actual-access.js";
import type { ExtensionStore, RecordWrite, WrappedKey } from "./store.js";

export const API_VERSION = 1;
const MAX_CIPHERTEXT = 65_536;

const error = (code: string, message: string) => ({ error: { code, message } });
const errorSchema = {
  type: "object",
  properties: {
    error: {
      type: "object",
      properties: { code: { type: "string" }, message: { type: "string" } },
      required: ["code", "message"],
    },
  },
  required: ["error"],
} as const;
const base64 = {
  type: "string",
  maxLength: MAX_CIPHERTEXT,
  pattern: "^[A-Za-z0-9+/]*={0,2}$",
} as const;
const sealedRecord = {
  type: "object",
  properties: {
    recordId: { type: "string" },
    recordType: { type: "string" },
    schemaVersion: { type: "integer" },
    revision: { type: "integer" },
    deleted: { type: "boolean" },
    iv: { type: "string" },
    ciphertext: { type: "string" },
    seq: { type: "integer" },
  },
  required: [
    "recordId",
    "recordType",
    "schemaVersion",
    "revision",
    "deleted",
    "iv",
    "ciphertext",
    "seq",
  ],
} as const;
const wrappedKey = {
  type: "object",
  properties: {
    keyId: { type: "string", minLength: 1, maxLength: 64 },
    wrappedKey: base64,
    wrapSalt: base64,
    keyCheck: base64,
  },
  required: ["keyId", "wrappedKey", "wrapSalt", "keyCheck"],
} as const;
const budgetParams = {
  type: "object",
  properties: {
    budgetId: { type: "string", pattern: "^[A-Za-z0-9-]{1,100}$" },
  },
  required: ["budgetId"],
} as const;

export interface AppOptions {
  store: ExtensionStore;
  access: BudgetAccess;
  /** Identifies the Actual server, so budgets on different self-hosted servers never share a scope. */
  actualServerUrl: string;
  /** Requests each signed-in session may make per minute. */
  requestsPerMinute?: number;
}

/**
 * Tandem's optional extension sync: it stores planning records the device has already sealed, so a budget's planning
 * details can follow its members across devices. Budget data itself never passes through here.
 */
export async function buildApp(options: AppOptions): Promise<FastifyInstance> {
  const app = Fastify({ bodyLimit: 256 * 1024, logger: false });
  await app.register(swagger, {
    openapi: {
      info: {
        title: "Tandem extension sync",
        version: `${API_VERSION}.0.0`,
        description:
          "Optional encrypted sync for Tandem planning records. Budget data syncs with Actual directly.",
      },
      components: {
        securitySchemes: {
          actualToken: { type: "apiKey", in: "header", name: "x-actual-token" },
        },
      },
    },
  });
  await app.register(rateLimit, {
    max: options.requestsPerMinute ?? 600,
    timeWindow: 60_000,
    keyGenerator: (request) => {
      const token = request.headers["x-actual-token"];
      return typeof token === "string" && token
        ? `t:${token}`
        : `ip:${request.ip}`;
    },
    errorResponseBuilder: (_request, context) => ({
      statusCode: context.statusCode,
      ...error("rate_limited", "Too many requests. Try again in a minute."),
    }),
  });
  const scope = (budgetId: string) => `${options.actualServerUrl}|${budgetId}`;

  async function authorize(
    token: string | undefined,
    budgetId: string,
    reply: FastifyReply,
  ) {
    if (!token) {
      await reply
        .code(401)
        .send(error("sign_in_required", "Sign in to Actual first."));
      return false;
    }
    const access = await options.access.check(token, budgetId);
    if (access === "allowed") return true;
    await reply
      .code(access === "denied" ? 403 : 503)
      .send(
        access === "denied"
          ? error(
              "budget_forbidden",
              "This Actual session can't open this budget.",
            )
          : error(
              "actual_unavailable",
              "Tandem couldn't reach the Actual server.",
            ),
      );
    return false;
  }

  app.get(
    "/extension/v1/health/live",
    {
      schema: {
        operationId: "getLiveness",
        response: {
          200: { type: "object", properties: { status: { type: "string" } } },
        },
      },
    },
    async () => ({ status: "ok" }),
  );

  app.get(
    "/extension/v1/health/ready",
    {
      schema: {
        operationId: "getReadiness",
        response: {
          200: { type: "object", properties: { status: { type: "string" } } },
          503: errorSchema,
        },
      },
    },
    async (_request, reply) => {
      try {
        await options.store.ping();
        return { status: "ok" };
      } catch {
        return reply
          .code(503)
          .send(
            error("storage_unavailable", "Extension storage isn't reachable."),
          );
      }
    },
  );

  app.get(
    "/extension/v1/capabilities",
    {
      schema: {
        operationId: "getCapabilities",
        response: {
          200: {
            type: "object",
            properties: {
              apiVersion: { type: "integer" },
              features: { type: "array", items: { type: "string" } },
            },
          },
        },
      },
    },
    async () => ({
      apiVersion: API_VERSION,
      features: ["planning-records", "planning-key"],
    }),
  );

  app.get<{ Params: { budgetId: string } }>(
    "/extension/v1/budgets/:budgetId/key",
    {
      schema: {
        operationId: "getPlanningKey",
        security: [{ actualToken: [] }],
        params: budgetParams,
        response: {
          200: wrappedKey,
          401: errorSchema,
          403: errorSchema,
          404: errorSchema,
          503: errorSchema,
        },
      },
    },
    async (request, reply) => {
      if (
        !(await authorize(
          request.headers["x-actual-token"] as string | undefined,
          request.params.budgetId,
          reply,
        ))
      )
        return reply;
      const key = await options.store.key(scope(request.params.budgetId));
      return (
        key ??
        reply
          .code(404)
          .send(
            error(
              "key_not_found",
              "No planning key has been shared for this budget.",
            ),
          )
      );
    },
  );

  app.put<{
    Params: { budgetId: string };
    Body: WrappedKey & { expectedKeyId: string | null };
  }>(
    "/extension/v1/budgets/:budgetId/key",
    {
      schema: {
        operationId: "putPlanningKey",
        security: [{ actualToken: [] }],
        params: budgetParams,
        body: {
          ...wrappedKey,
          properties: {
            ...wrappedKey.properties,
            expectedKeyId: { type: ["string", "null"] },
          },
          required: [...wrappedKey.required, "expectedKeyId"],
        },
        response: {
          200: { type: "object", properties: { status: { type: "string" } } },
          401: errorSchema,
          403: errorSchema,
          409: errorSchema,
          503: errorSchema,
        },
      },
    },
    async (request, reply) => {
      if (
        !(await authorize(
          request.headers["x-actual-token"] as string | undefined,
          request.params.budgetId,
          reply,
        ))
      )
        return reply;
      const { expectedKeyId, ...key } = request.body;
      return (await options.store.putKey(
        scope(request.params.budgetId),
        key,
        expectedKeyId,
      ))
        ? { status: "stored" }
        : reply
            .code(409)
            .send(
              error(
                "key_conflict",
                "The planning key changed on another device.",
              ),
            );
    },
  );

  app.get<{
    Params: { budgetId: string };
    Querystring: { after?: number; limit?: number };
  }>(
    "/extension/v1/budgets/:budgetId/changes",
    {
      schema: {
        operationId: "getPlanningChanges",
        security: [{ actualToken: [] }],
        params: budgetParams,
        querystring: {
          type: "object",
          properties: {
            after: { type: "integer", minimum: 0 },
            limit: { type: "integer", minimum: 1, maximum: 500 },
          },
        },
        response: {
          200: {
            type: "object",
            properties: {
              records: { type: "array", items: sealedRecord },
              cursor: { type: "integer" },
              hasMore: { type: "boolean" },
            },
          },
          401: errorSchema,
          403: errorSchema,
          503: errorSchema,
        },
      },
    },
    async (request, reply) => {
      if (
        !(await authorize(
          request.headers["x-actual-token"] as string | undefined,
          request.params.budgetId,
          reply,
        ))
      )
        return reply;
      return options.store.changes(
        scope(request.params.budgetId),
        request.query.after ?? 0,
        request.query.limit ?? 200,
      );
    },
  );

  app.put<{
    Params: { budgetId: string; recordId: string };
    Body: RecordWrite;
  }>(
    "/extension/v1/budgets/:budgetId/records/:recordId",
    {
      schema: {
        operationId: "putPlanningRecord",
        security: [{ actualToken: [] }],
        params: {
          type: "object",
          properties: {
            ...budgetParams.properties,
            recordId: { type: "string", pattern: "^[A-Za-z0-9-]{1,100}$" },
          },
          required: ["budgetId", "recordId"],
        },
        body: {
          type: "object",
          properties: {
            recordType: { type: "string", pattern: "^[a-z_.]{1,64}$" },
            schemaVersion: { type: "integer", minimum: 1 },
            revision: { type: "integer", minimum: 1 },
            baseRevision: { type: ["integer", "null"], minimum: 1 },
            deleted: { type: "boolean" },
            iv: base64,
            ciphertext: base64,
          },
          required: [
            "recordType",
            "schemaVersion",
            "revision",
            "baseRevision",
            "deleted",
            "iv",
            "ciphertext",
          ],
        },
        response: {
          200: { type: "object", properties: { seq: { type: "integer" } } },
          401: errorSchema,
          403: errorSchema,
          409: {
            type: "object",
            properties: {
              error: errorSchema.properties.error,
              current: { anyOf: [sealedRecord, { type: "null" }] },
            },
          },
          503: errorSchema,
        },
      },
    },
    async (request, reply) => {
      if (
        !(await authorize(
          request.headers["x-actual-token"] as string | undefined,
          request.params.budgetId,
          reply,
        ))
      )
        return reply;
      const result = await options.store.write(
        scope(request.params.budgetId),
        request.params.recordId,
        request.body,
      );
      if (result.status === "stored") return { seq: result.seq };
      return reply.code(409).send({
        ...error("revision_conflict", "This record changed on another device."),
        current: result.current ?? null,
      });
    },
  );

  return app;
}
