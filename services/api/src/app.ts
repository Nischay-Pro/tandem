import swagger from "@fastify/swagger";
import Fastify, { type FastifyInstance, type FastifySchema } from "fastify";
import { READINESS_TIMEOUT_MS } from "./config.js";
import type { HealthDatabase } from "./database.js";
import {
  DeviceAuthorization,
  DeviceAuthInputError,
} from "./auth/device-auth.js";
import { PairingRateLimiter } from "./auth/pairing-rate-limiter.js";
import type { TransactionApiPort } from "./actual/transaction-api.js";
import {
  IDEMPOTENCY_KEY_MAX_LENGTH,
  IdempotencyKeyReuseError,
  OperationIdReuseError,
  type JsonValue,
  type OperationConflict,
  type WriteOperation,
} from "./write-journal/contracts.js";
import { TransactionRequestValidationError } from "./actual/transaction-reconciler.js";

const statusOk = {
  type: "object",
  properties: { status: { type: "string", const: "ok" } },
  required: ["status"],
  additionalProperties: false,
};

const statusNotReady = {
  type: "object",
  properties: { status: { type: "string", const: "not_ready" } },
  required: ["status"],
  additionalProperties: false,
};

const liveSchema: FastifySchema = {
  tags: ["Health"],
  operationId: "getLiveness",
  summary: "Check whether the API process is running",
  response: { 200: statusOk },
};

const readySchema: FastifySchema = {
  tags: ["Health"],
  operationId: "getReadiness",
  summary: "Check whether the API can reach PostgreSQL",
  response: { 200: statusOk, 503: statusNotReady },
};

export interface ApiAppOptions {
  readinessTimeoutMs?: number;
  onClose?: () => Promise<void>;
  deviceAuth?: DeviceAuthorization;
  transactionApi?: TransactionApiPort;
  trustProxy?: false | string[];
}

export async function buildApiApp(
  database: HealthDatabase,
  options: ApiAppOptions = {},
): Promise<FastifyInstance> {
  const readinessTimeoutMs = options.readinessTimeoutMs ?? READINESS_TIMEOUT_MS;
  const app = Fastify({
    logger: false,
    trustProxy: options.trustProxy ?? false,
  });

  await app.register(swagger, {
    openapi: {
      openapi: "3.1.0",
      info: {
        title: "Actual Budget Companion API",
        version: "0.1.0",
        description:
          "Unofficial API for the Actual Budget Companion ecosystem.",
      },
      tags: [
        { name: "Health" },
        { name: "Authentication" },
        { name: "Transactions" },
      ],
      components: {
        securitySchemes: {
          bearerAuth: {
            type: "http",
            scheme: "bearer",
            bearerFormat: "opaque",
          },
        },
      },
    },
  });
  app.get<{ Reply: { status: "ok" } }>(
    "/api/v1/health/live",
    { schema: liveSchema },
    async () => ({
      status: "ok",
    }),
  );

  app.get<{ Reply: { status: "ok" | "not_ready" } }>(
    "/api/v1/health/ready",
    { schema: readySchema },
    async (_request, reply) => {
      try {
        await pingWithTimeout(() => database.ping(), readinessTimeoutMs);
        return reply.code(200).send({ status: "ok" });
      } catch {
        return reply.code(503).send({ status: "not_ready" });
      }
    },
  );

  app.get("/api/v1/openapi.json", async () => app.swagger());

  if (options.deviceAuth) {
    registerDeviceAuthorizationRoutes(app, options.deviceAuth);
    registerTransactionRoutes(app, options.deviceAuth, options.transactionApi);
  }

  app.addHook("onClose", async () => {
    try {
      await options.onClose?.();
    } finally {
      await database.close();
    }
  });
  await app.ready();
  return app;
}

function registerDeviceAuthorizationRoutes(
  app: FastifyInstance,
  deviceAuth: DeviceAuthorization,
): void {
  const rateLimiter = new PairingRateLimiter();
  const errorSchema = {
    type: "object",
    properties: {
      error: {
        type: "object",
        properties: {
          code: { type: "string" },
          message: { type: "string" },
        },
        required: ["code", "message"],
        additionalProperties: false,
      },
    },
    required: ["error"],
    additionalProperties: false,
  };
  const deviceSchema = {
    type: "object",
    properties: {
      deviceId: { type: "string", format: "uuid" },
      principalId: { type: "string" },
      name: { type: "string" },
      budgetIds: { type: "array", items: { type: "string" } },
      createdAt: { type: "string", format: "date-time" },
      lastSeenAt: { type: ["string", "null"], format: "date-time" },
      revokedAt: { type: ["string", "null"], format: "date-time" },
    },
    required: [
      "deviceId",
      "principalId",
      "name",
      "budgetIds",
      "createdAt",
      "lastSeenAt",
      "revokedAt",
    ],
    additionalProperties: false,
  };
  const pairSchema: FastifySchema = {
    tags: ["Authentication"],
    operationId: "pairDevice",
    summary: "Exchange a one-time pairing code for a device credential",
    body: {
      type: "object",
      properties: {
        code: { type: "string", minLength: 43, maxLength: 43 },
        deviceName: {
          type: "string",
          minLength: 1,
          maxLength: 100,
          pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
        },
      },
      required: ["code", "deviceName"],
      additionalProperties: false,
    },
    response: {
      201: {
        type: "object",
        properties: {
          deviceId: { type: "string", format: "uuid" },
          principalId: { type: "string" },
          name: { type: "string" },
          budgetIds: { type: "array", items: { type: "string" } },
          accessToken: { type: "string", minLength: 43, maxLength: 43 },
          tokenType: { type: "string", const: "Bearer" },
          createdAt: { type: "string", format: "date-time" },
        },
        required: [
          "deviceId",
          "principalId",
          "name",
          "budgetIds",
          "accessToken",
          "tokenType",
          "createdAt",
        ],
        additionalProperties: false,
      },
      401: errorSchema,
      400: errorSchema,
      429: errorSchema,
    },
  };
  app.post<{
    Body: { code: string; deviceName: string };
  }>(
    "/api/v1/auth/pair",
    { schema: pairSchema, attachValidation: true },
    async (request, reply) => {
      if (request.validationError) {
        return reply.code(400).send({
          error: {
            code: "invalid_request",
            message: "Pairing request is invalid",
          },
        });
      }

      const rateLimit = rateLimiter.consume(request.ip);
      if (!rateLimit.allowed) {
        return reply
          .header("retry-after", String(rateLimit.retryAfterSeconds))
          .code(429)
          .send({
            error: {
              code: "rate_limited",
              message: "Too many pairing attempts",
            },
          });
      }

      let paired;
      try {
        paired = await deviceAuth.pair(request.body);
      } catch (error) {
        if (!(error instanceof DeviceAuthInputError)) {
          throw error;
        }
        return reply.code(400).send({
          error: { code: "invalid_request", message: error.message },
        });
      }
      if (!paired) {
        return reply.code(401).send({
          error: {
            code: "invalid_pairing_code",
            message: "Pairing code is invalid, expired, or already used",
          },
        });
      }

      reply.header("cache-control", "no-store");
      return reply.code(201).send({
        deviceId: paired.deviceId,
        principalId: paired.principalId,
        name: paired.name,
        budgetIds: paired.budgetIds,
        accessToken: paired.accessToken,
        tokenType: "Bearer",
        createdAt: paired.createdAt.toISOString(),
      });
    },
  );

  app.get(
    "/api/v1/auth/me",
    {
      schema: {
        tags: ["Authentication"],
        operationId: "getCurrentDevice",
        summary: "Return the authenticated device and its budget scope",
        security: [{ bearerAuth: [] }],
        response: { 200: deviceSchema, 401: errorSchema },
      },
    },
    async (request, reply) => {
      const device = await requireDevice(
        deviceAuth,
        request.headers.authorization,
        reply,
      );
      if (!device) {
        return reply;
      }
      return toDeviceResponse(device);
    },
  );

  app.get(
    "/api/v1/devices",
    {
      schema: {
        tags: ["Authentication"],
        operationId: "listDevices",
        summary: "List devices visible to the authenticated device",
        security: [{ bearerAuth: [] }],
        response: {
          200: {
            type: "object",
            properties: {
              devices: { type: "array", items: deviceSchema },
            },
            required: ["devices"],
            additionalProperties: false,
          },
          401: errorSchema,
        },
      },
    },
    async (request, reply) => {
      const device = await requireDevice(
        deviceAuth,
        request.headers.authorization,
        reply,
      );
      if (!device) {
        return reply;
      }
      const devices = await deviceAuth.listDevicesForDevice(device);
      return { devices: devices.map(toDeviceResponse) };
    },
  );

  app.delete<{
    Params: { deviceId: string };
  }>(
    "/api/v1/devices/:deviceId",
    {
      schema: {
        tags: ["Authentication"],
        operationId: "revokeDevice",
        summary:
          "Revoke this device or a device visible to a full-access device",
        security: [{ bearerAuth: [] }],
        params: {
          type: "object",
          properties: {
            deviceId: { type: "string", format: "uuid" },
          },
          required: ["deviceId"],
          additionalProperties: false,
        },
        response: {
          204: { type: "null" },
          401: errorSchema,
          404: errorSchema,
        },
      },
    },
    async (request, reply) => {
      const device = await requireDevice(
        deviceAuth,
        request.headers.authorization,
        reply,
      );
      if (!device) {
        return reply;
      }
      const revoked = await deviceAuth.revokeDeviceForDevice(
        device,
        request.params.deviceId,
      );
      if (!revoked) {
        return reply.code(404).send({
          error: { code: "device_not_found", message: "Device was not found" },
        });
      }
      return reply.code(204).send();
    },
  );
}

interface TransactionRouteParams {
  budgetId: string;
  transactionId: string;
}

interface OperationRouteParams {
  operationId: string;
}

interface SubmitTransactionBody {
  operationId: string;
  action: "update" | "delete";
  baseRevision: string;
  baseSnapshot: JsonValue;
  patch: { [key: string]: JsonValue };
}

interface TransactionHeaders {
  "idempotency-key": string;
  "x-budget-encryption-password"?: string;
}

function registerTransactionRoutes(
  app: FastifyInstance,
  deviceAuth: DeviceAuthorization,
  transactionApi: TransactionApiPort | undefined,
): void {
  const errorSchema = {
    type: "object",
    properties: {
      error: {
        type: "object",
        properties: {
          code: { type: "string" },
          message: { type: "string" },
        },
        required: ["code", "message"],
        additionalProperties: false,
      },
    },
    required: ["error"],
    additionalProperties: false,
  };
  const nullableStringSchema = { type: ["string", "null"] };
  const nullableBooleanSchema = { type: ["boolean", "null"] };
  const transactionSnapshotSchema = {
    type: "object",
    properties: {
      id: { type: "string" },
      account: { type: "string" },
      amount: { type: "integer" },
      date: { type: "string", format: "date" },
      payee: nullableStringSchema,
      category: nullableStringSchema,
      notes: nullableStringSchema,
      cleared: nullableBooleanSchema,
      reconciled: nullableBooleanSchema,
      importedId: nullableStringSchema,
      importedPayee: nullableStringSchema,
      startingBalance: nullableBooleanSchema,
      schedule: nullableStringSchema,
      transferId: nullableStringSchema,
      isParent: { type: "boolean" },
      isChild: { type: "boolean" },
      parentId: nullableStringSchema,
      subtransactions: {
        type: "array",
        maxItems: 1_000,
        items: { type: "object", additionalProperties: true },
      },
    },
    required: [
      "id",
      "account",
      "amount",
      "date",
      "payee",
      "category",
      "notes",
      "cleared",
      "reconciled",
      "importedId",
      "importedPayee",
      "startingBalance",
      "schedule",
      "transferId",
      "isParent",
      "isChild",
      "parentId",
      "subtransactions",
    ],
    additionalProperties: false,
  };
  const conflictSchema = {
    type: ["object", "null"],
    properties: {
      conflictId: { type: "string", format: "uuid" },
      conflictType: { type: "string" },
      baseSnapshot: { type: "object", additionalProperties: true },
      localPatch: { type: "object", additionalProperties: true },
      remoteSnapshot: { anyOf: [transactionSnapshotSchema, { type: "null" }] },
      conflictingFields: { type: "array", items: { type: "string" } },
      createdAt: { type: "string", format: "date-time" },
    },
    required: [
      "conflictId",
      "conflictType",
      "baseSnapshot",
      "localPatch",
      "remoteSnapshot",
      "conflictingFields",
      "createdAt",
    ],
    additionalProperties: false,
  };
  const operationResultSchema = {
    type: "object",
    properties: {
      operation: {
        type: "object",
        properties: {
          operationId: { type: "string", format: "uuid" },
          budgetId: { type: "string" },
          action: { type: "string", enum: ["update", "delete"] },
          transactionId: { type: "string" },
          baseRevision: { type: "string" },
          baseSnapshot: { type: "object", additionalProperties: true },
          patch: { type: "object", additionalProperties: true },
          status: {
            type: "string",
            enum: [
              "queued",
              "applying",
              "committed",
              "conflict",
              "uncertain",
              "rejected",
            ],
          },
          attemptCount: { type: "integer" },
          errorCode: nullableStringSchema,
          actualResult: {
            type: ["object", "array", "string", "number", "boolean", "null"],
          },
          createdAt: { type: "string", format: "date-time" },
          updatedAt: { type: "string", format: "date-time" },
          startedAt: { type: ["string", "null"], format: "date-time" },
          completedAt: { type: ["string", "null"], format: "date-time" },
        },
        required: [
          "operationId",
          "budgetId",
          "action",
          "transactionId",
          "baseRevision",
          "baseSnapshot",
          "patch",
          "status",
          "attemptCount",
          "errorCode",
          "actualResult",
          "createdAt",
          "updatedAt",
          "startedAt",
          "completedAt",
        ],
        additionalProperties: false,
      },
      conflict: conflictSchema,
    },
    required: ["operation", "conflict"],
    additionalProperties: false,
  };
  const transactionParamsSchema = {
    type: "object",
    properties: {
      budgetId: {
        type: "string",
        minLength: 1,
        maxLength: 500,
        pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
      },
      transactionId: {
        type: "string",
        minLength: 1,
        maxLength: 500,
        pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
      },
    },
    required: ["budgetId", "transactionId"],
    additionalProperties: false,
  };
  const budgetEncryptionHeaderSchema = {
    type: "object",
    properties: {
      "x-budget-encryption-password": {
        type: "string",
        minLength: 1,
        maxLength: 1_024,
      },
    },
    additionalProperties: true,
  };

  app.get<{
    Params: TransactionRouteParams;
    Headers: TransactionHeaders;
  }>(
    "/api/v1/budgets/:budgetId/transactions/:transactionId",
    {
      schema: {
        tags: ["Transactions"],
        operationId: "getBudgetTransaction",
        summary: "Read one transaction and its optimistic concurrency revision",
        security: [{ bearerAuth: [] }],
        params: transactionParamsSchema,
        headers: budgetEncryptionHeaderSchema,
        response: {
          200: {
            type: "object",
            properties: {
              transaction: transactionSnapshotSchema,
              revision: { type: "string", pattern: "^[a-f0-9]{64}$" },
            },
            required: ["transaction", "revision"],
            additionalProperties: false,
          },
          400: errorSchema,
          401: errorSchema,
          404: errorSchema,
          502: errorSchema,
          503: errorSchema,
        },
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(
        deviceAuth,
        request.headers.authorization,
        reply,
      );
      if (!device) return reply;
      if (request.validationError) return invalidTransactionRequest(reply);
      if (
        !DeviceAuthorization.canAccessBudget(device, request.params.budgetId)
      ) {
        return transactionNotFound(reply);
      }
      if (!transactionApi) return actualNotConfigured(reply);

      try {
        const result = await transactionApi.readTransaction(
          request.params.budgetId,
          request.params.transactionId,
          request.headers["x-budget-encryption-password"],
        );
        if (!result) return transactionNotFound(reply);
        reply.header("cache-control", "no-store");
        return reply.code(200).send({
          transaction: result.snapshot,
          revision: result.revision,
        });
      } catch {
        reportTransactionFailure("Transaction read failed");
        return actualRequestFailed(reply);
      }
    },
  );

  app.post<{
    Params: TransactionRouteParams;
    Headers: TransactionHeaders;
    Body: SubmitTransactionBody;
  }>(
    "/api/v1/budgets/:budgetId/transactions/:transactionId/operations",
    {
      schema: {
        tags: ["Transactions"],
        operationId: "submitTransactionOperation",
        summary:
          "Submit an idempotent, revision-checked transaction update or deletion",
        security: [{ bearerAuth: [] }],
        params: transactionParamsSchema,
        headers: {
          ...budgetEncryptionHeaderSchema,
          properties: {
            ...budgetEncryptionHeaderSchema.properties,
            "idempotency-key": {
              type: "string",
              minLength: 1,
              maxLength: IDEMPOTENCY_KEY_MAX_LENGTH,
              pattern: "\\S",
            },
          },
          required: ["idempotency-key"],
        },
        body: {
          type: "object",
          properties: {
            operationId: { type: "string", format: "uuid" },
            action: { type: "string", enum: ["update", "delete"] },
            baseRevision: { type: "string", minLength: 1, maxLength: 500 },
            baseSnapshot: {
              type: "object",
              additionalProperties: true,
              maxProperties: 20,
            },
            patch: {
              type: "object",
              additionalProperties: true,
              maxProperties: 8,
            },
          },
          required: [
            "operationId",
            "action",
            "baseRevision",
            "baseSnapshot",
            "patch",
          ],
          additionalProperties: false,
        },
        response: {
          200: operationResultSchema,
          400: errorSchema,
          401: errorSchema,
          404: errorSchema,
          409: errorSchema,
          502: errorSchema,
          503: errorSchema,
        },
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(
        deviceAuth,
        request.headers.authorization,
        reply,
      );
      if (!device) return reply;
      if (request.validationError) return invalidTransactionRequest(reply);
      if (
        !DeviceAuthorization.canAccessBudget(device, request.params.budgetId)
      ) {
        return transactionNotFound(reply);
      }
      if (!transactionApi) return actualNotConfigured(reply);

      const idempotencyKey = request.headers["idempotency-key"];
      try {
        const result = await transactionApi.submit({
          operationId: request.body.operationId,
          principalId: device.principalId,
          budgetId: request.params.budgetId,
          idempotencyKey,
          transactionId: request.params.transactionId,
          baseRevision: request.body.baseRevision,
          baseSnapshot: request.body.baseSnapshot,
          action: request.body.action,
          patch: request.body.patch,
          ...(request.headers["x-budget-encryption-password"] === undefined
            ? {}
            : {
                encryptionPassword:
                  request.headers["x-budget-encryption-password"],
              }),
        });
        reply.header("cache-control", "no-store");
        return reply.code(200).send(toOperationResultResponse(result));
      } catch (error) {
        if (error instanceof TransactionRequestValidationError) {
          return invalidTransactionRequest(reply);
        }
        if (error instanceof IdempotencyKeyReuseError) {
          return reply.code(409).send({
            error: {
              code: "idempotency_key_reused",
              message: "Idempotency key was already used for another request",
            },
          });
        }
        if (error instanceof OperationIdReuseError) {
          return reply.code(409).send({
            error: {
              code: "operation_id_reused",
              message: "Operation ID was already used for another request",
            },
          });
        }
        reportTransactionFailure("Transaction operation failed", error);
        return actualRequestFailed(reply);
      }
    },
  );

  app.get<{ Params: OperationRouteParams }>(
    "/api/v1/operations/:operationId",
    {
      schema: {
        tags: ["Transactions"],
        operationId: "getTransactionOperation",
        summary: "Read the durable status and any conflict for an operation",
        security: [{ bearerAuth: [] }],
        params: {
          type: "object",
          properties: { operationId: { type: "string", format: "uuid" } },
          required: ["operationId"],
          additionalProperties: false,
        },
        response: {
          200: operationResultSchema,
          400: errorSchema,
          401: errorSchema,
          404: errorSchema,
          503: errorSchema,
        },
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(
        deviceAuth,
        request.headers.authorization,
        reply,
      );
      if (!device) return reply;
      if (request.validationError) return invalidTransactionRequest(reply);
      if (!transactionApi) return actualNotConfigured(reply);

      try {
        const result = await transactionApi.getOperation(
          device.principalId,
          request.params.operationId,
        );
        if (
          !result ||
          !DeviceAuthorization.canAccessBudget(
            device,
            result.operation.budgetId,
          )
        ) {
          return transactionNotFound(reply);
        }
        reply.header("cache-control", "no-store");
        return reply.code(200).send(toOperationResultResponse(result));
      } catch (error) {
        reportTransactionFailure("Transaction operation lookup failed", error);
        return actualRequestFailed(reply);
      }
    },
  );
}

function invalidTransactionRequest(reply: import("fastify").FastifyReply) {
  return reply.code(400).send({
    error: {
      code: "invalid_request",
      message: "Transaction request is invalid",
    },
  });
}

function transactionNotFound(reply: import("fastify").FastifyReply) {
  return reply.code(404).send({
    error: { code: "not_found", message: "Resource was not found" },
  });
}

function actualNotConfigured(reply: import("fastify").FastifyReply) {
  return reply.code(503).send({
    error: {
      code: "actual_not_configured",
      message: "Actual Budget integration is not configured",
    },
  });
}

function actualRequestFailed(reply: import("fastify").FastifyReply) {
  return reply.code(502).send({
    error: {
      code: "actual_operation_failed",
      message: "The Actual Budget operation could not be completed",
    },
  });
}

function reportTransactionFailure(context: string, error?: unknown): void {
  try {
    console.error(context, {
      errorType: error instanceof Error ? error.name : typeof error,
    });
  } catch {
    // Never let diagnostic logging expose financial data or mask the response.
  }
}

function toOperationResultResponse(result: {
  operation: WriteOperation;
  conflict: OperationConflict | null;
}) {
  return {
    operation: {
      operationId: result.operation.operationId,
      budgetId: result.operation.budgetId,
      action: result.operation.action,
      transactionId: result.operation.entityId ?? "",
      baseRevision: result.operation.baseRevision ?? "",
      baseSnapshot: result.operation.baseSnapshot,
      patch: result.operation.patch,
      status: result.operation.status,
      attemptCount: result.operation.attemptCount,
      errorCode: result.operation.errorCode,
      actualResult: result.operation.actualResult,
      createdAt: result.operation.createdAt.toISOString(),
      updatedAt: result.operation.updatedAt.toISOString(),
      startedAt: result.operation.startedAt?.toISOString() ?? null,
      completedAt: result.operation.completedAt?.toISOString() ?? null,
    },
    conflict: result.conflict
      ? {
          conflictId: result.conflict.conflictId,
          conflictType: result.conflict.conflictType,
          baseSnapshot: result.conflict.baseSnapshot,
          localPatch: result.conflict.localPatch,
          remoteSnapshot: result.conflict.remoteSnapshot,
          conflictingFields: result.conflict.conflictingFields,
          createdAt: result.conflict.createdAt.toISOString(),
        }
      : null,
  };
}

async function requireDevice(
  deviceAuth: DeviceAuthorization,
  authorization: string | undefined,
  reply: import("fastify").FastifyReply,
) {
  const match = authorization?.match(/^Bearer ([A-Za-z0-9_-]{43})$/i);
  const device = await deviceAuth.authenticate(match?.[1]);
  if (device) {
    return device;
  }
  reply.header("www-authenticate", 'Bearer realm="actual-budget-companion"');
  await reply.code(401).send({
    error: {
      code: "unauthorized",
      message: "Valid device credentials required",
    },
  });
  return undefined;
}

function toDeviceResponse(device: {
  deviceId: string;
  principalId: string;
  name: string;
  budgetIds: string[];
  createdAt: Date;
  lastSeenAt: Date | null;
  revokedAt: Date | null;
}) {
  return {
    deviceId: device.deviceId,
    principalId: device.principalId,
    name: device.name,
    budgetIds: device.budgetIds,
    createdAt: device.createdAt.toISOString(),
    lastSeenAt: device.lastSeenAt?.toISOString() ?? null,
    revokedAt: device.revokedAt?.toISOString() ?? null,
  };
}

async function pingWithTimeout(
  ping: () => Promise<void>,
  timeoutMs: number,
): Promise<void> {
  let timer!: ReturnType<typeof setTimeout>;
  const timeout = new Promise<never>((_resolve, reject) => {
    timer = setTimeout(
      () => reject(new Error("Database readiness probe timed out")),
      timeoutMs,
    );
  });

  try {
    await Promise.race([Promise.resolve().then(ping), timeout]);
  } finally {
    clearTimeout(timer);
  }
}
