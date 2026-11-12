import swagger from "@fastify/swagger";
import { randomUUID } from "node:crypto";
import { performance } from "node:perf_hooks";
import Fastify, {
  LogController,
  type FastifyInstance,
  type FastifyRequest,
  type FastifyServerOptions,
  type FastifySchema,
} from "fastify";
import { READINESS_TIMEOUT_MS } from "./config.js";
import type { HealthDatabase } from "./database.js";
import {
  ActualPrincipalSessionExpiredError,
  ACTUAL_PRINCIPAL_SESSION_EXPIRED_MESSAGE,
} from "./actual/actual-session.js";
import { DeviceAuthorization } from "./auth/device-auth.js";
import { registerDeviceAuthorizationRoutes } from "./auth/device-authorization-routes.js";
import type { ActualPasswordGrant } from "./auth/actual-password-login.js";
import type { ActualOpenIdLogin } from "./auth/actual-openid-login.js";
import { RequestRateLimiter } from "./auth/request-rate-limiter.js";
import { runAsActualPrincipal } from "./auth/actual-principal-context.js";
import type { ActualLoginMethods } from "./auth/actual-login-methods.js";
import { ActualLoginMethodsCache } from "./auth/actual-login-methods-cache.js";
import { logSecurityAuditEvent } from "./security-audit.js";
import type { TransactionApiPort } from "./actual/transaction-api.js";
import type { BudgetMaintenanceApiPort } from "./actual/budget-maintenance-api.js";
import type { BudgetReadApiPort } from "./budget-projection/budget-read-api.js";
import {
  registerBudgetMaintenanceRoutes,
  type BudgetMaintenanceJournal,
} from "./actual/budget-maintenance-routes.js";
import { registerBudgetReadRoutes } from "./budget-projection/budget-read-routes.js";
import {
  API_ERROR_SCHEMA,
  API_ERROR_SCHEMA_REF,
  buildOpenApiLocalReference,
  withSharedApiErrorResponses,
} from "./openapi/error-responses.js";
import {
  isStorageFullError,
  SERVER_STORAGE_FULL_ERROR,
} from "./storage-failure.js";
import {
  IDEMPOTENCY_KEY_MAX_LENGTH,
  IdempotencyKeyReuseError,
  OperationIdReuseError,
  type JsonValue,
  type OperationConflict,
  type WriteOperation,
} from "./write-journal/contracts.js";
import { TransactionRequestValidationError } from "./actual/transaction-reconciler.js";

declare module "fastify" {
  interface FastifySchema {
    "x-tandem-rate-limited"?: boolean;
  }
}

const statusOk = {
  type: "object",
  properties: { status: { type: "string", const: "ok" } },
  required: ["status"],
  additionalProperties: false,
};
const errorSchema = API_ERROR_SCHEMA_REF;

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

export function safeErrorType(error: unknown): string {
  if (!(error instanceof Error)) return error === null ? "null" : typeof error;
  const name = error.constructor.name;
  return /^[A-Za-z][A-Za-z0-9_$]{0,63}$/.test(name) ? name : "Error";
}

const liveSchema: FastifySchema = {
  tags: ["Health"],
  operationId: "getLiveness",
  summary: "Check whether the API process is running",
  response: { 200: statusOk },
};

const readySchema: FastifySchema = {
  tags: ["Health"],
  operationId: "getReadiness",
  summary: "Check whether the API can reach its configured services",
  response: { 200: statusOk, 503: errorSchema },
};

export interface ApiAppOptions {
  readinessTimeoutMs?: number;
  actualReadiness?: () => {
    ready: boolean;
    code?: string;
    message?: string;
  };
  readActualLoginMethods?: () => Promise<ActualLoginMethods>;
  signInWithActualPassword?: (password: string) => Promise<ActualPasswordGrant>;
  actualOpenIdLogin?: ActualOpenIdLogin;
  persistActualSessionToken?: (
    principalId: string,
    sessionToken: string,
  ) => Promise<void>;
  onClose?: () => Promise<void>;
  deviceAuth?: DeviceAuthorization;
  transactionApi?: TransactionApiPort;
  budgetMaintenanceApi?: BudgetMaintenanceApiPort;
  maintenanceJournal?: BudgetMaintenanceJournal;
  budgetReadApi?: BudgetReadApiPort;
  trustProxy?: false | string[];
  logger?: FastifyServerOptions["logger"];
}

export async function buildApiApp(
  database: HealthDatabase,
  options: ApiAppOptions = {},
): Promise<FastifyInstance> {
  const readinessTimeoutMs = options.readinessTimeoutMs ?? READINESS_TIMEOUT_MS;
  const actualLoginMethodsCache = new ActualLoginMethodsCache(45_000);
  const actualLoginMethodsRateLimiter = new RequestRateLimiter(60, 60_000);
  const requestErrors = new WeakMap<FastifyRequest, unknown>();
  const requestPrincipalIds = new WeakMap<FastifyRequest, string>();
  const responseErrorCodes = new WeakMap<FastifyRequest, string>();
  const requestStartedAt = new WeakMap<FastifyRequest, number>();
  const app = Fastify({
    logger: options.logger ?? false,
    logController: new LogController({ disableRequestLogging: true }),
    requestIdHeader: false,
    genReqId: () => randomUUID(),
    trustProxy: options.trustProxy ?? false,
  });
  app.addSchema(API_ERROR_SCHEMA);
  app.removeContentTypeParser("text/plain");
  app.addHook("onRequest", async (request, reply) => {
    requestStartedAt.set(request, performance.now());
    reply.header("x-request-id", request.id);
  });
  app.setErrorHandler((error, request, reply) => {
    if (isStorageFullError(error)) {
      requestErrors.set(request, error);
      reply.code(503).send({ error: SERVER_STORAGE_FULL_ERROR });
      return;
    }
    const statusCode = safeHttpStatus(frameworkErrorStatus(error));
    requestErrors.set(request, error);
    const response = safeFrameworkErrorResponse(
      statusCode,
      frameworkErrorCode(error),
    );
    reply.code(statusCode).send({ error: response });
  });
  app.setNotFoundHandler((_request, reply) => {
    reply.code(404).send({
      error: {
        code: "route_not_found",
        message:
          "Tandem API route was not found. Check the address and /api/v1/ proxy route.",
      },
    });
  });
  app.addHook("onSend", async (request, reply, payload) => {
    const statusCode = reply.statusCode;
    if (statusCode === 409 || statusCode >= 500) {
      const errorCode = responseErrorCode(payload, statusCode);
      responseErrorCodes.set(request, errorCode);
    }
    return payload;
  });
  app.addHook("onResponse", async (request, reply) => {
    const statusCode = reply.statusCode;
    const route = request.routeOptions.url ?? "unmatched";
    const logFields = {
      requestId: request.id,
      method: request.method,
      route,
      statusCode,
      durationMs: Math.round(
        performance.now() - requestStartedAt.get(request)!,
      ),
      ...(responseErrorCodes.has(request)
        ? { errorCode: responseErrorCodes.get(request) }
        : {}),
    };
    const hasThrownError = requestErrors.has(request);
    const thrownError = requestErrors.get(request);
    if (hasThrownError || statusCode === 409 || statusCode >= 500) {
      request.log.error(
        {
          ...logFields,
          ...(hasThrownError ? { errorType: safeErrorType(thrownError) } : {}),
        },
        "API request failed",
      );
    } else {
      request.log.info(logFields, "API request completed");
    }
  });
  if (options.deviceAuth) {
    app.addHook("onRequest", async (request) => {
      const match = request.headers.authorization?.match(
        /^Bearer ([A-Za-z0-9_-]{43})$/i,
      );
      const device = await options.deviceAuth?.authenticate(match?.[1]);
      if (device) requestPrincipalIds.set(request, device.principalId);
    });
    app.addHook("preHandler", (request, _reply, done) => {
      const principalId = requestPrincipalIds.get(request);
      if (principalId === undefined) {
        done();
        return;
      }
      runAsActualPrincipal(principalId, () => done());
    });
  }

  await app.register(swagger, {
    refResolver: {
      buildLocalReference(schema, _baseUri, _fragment, index) {
        return buildOpenApiLocalReference(schema, index);
      },
    },
    openapi: {
      openapi: "3.1.0",
      info: {
        title: "Tandem for Actual Budget API",
        version: "0.1.0",
        description: "Unofficial API for the Tandem for Actual Budget app.",
      },
      tags: [
        { name: "Health" },
        { name: "Authentication" },
        { name: "Budgets" },
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

  app.get<{
    Reply: { status: "ok" } | { error: { code: string; message: string } };
  }>(
    "/api/v1/health/ready",
    { schema: readySchema },
    async (_request, reply) => {
      try {
        await pingWithTimeout(() => database.ping(), readinessTimeoutMs);
      } catch (error) {
        if (isStorageFullError(error)) {
          return reply.code(503).send({ error: SERVER_STORAGE_FULL_ERROR });
        }
        return reply.code(503).send({
          error: {
            code: "database_unavailable",
            message: "Tandem could not reach its database. Try again shortly.",
          },
        });
      }
      const actualReadiness = options.actualReadiness?.();
      if (actualReadiness && !actualReadiness.ready) {
        return reply.code(503).send({
          error: {
            code: actualReadiness.code ?? "actual_unavailable",
            message:
              actualReadiness.message ??
              "Actual Budget is unavailable. Check the server connection.",
          },
        });
      }
      return reply.code(200).send({ status: "ok" });
    },
  );

  app.get("/api/v1/openapi.json", async () =>
    withSharedApiErrorResponses(app.swagger() as never),
  );

  app.get(
    "/api/v1/auth/methods",
    {
      schema: {
        tags: ["Authentication"],
        operationId: "getActualLoginMethods",
        summary: "Check the configured Actual server sign-in method",
        "x-tandem-rate-limited": true,
        response: {
          200: {
            type: "object",
            properties: {
              bootstrapped: { type: "boolean" },
              loginMethod: {
                type: ["string", "null"],
                enum: ["password", "openid", "unsupported", null],
              },
              availableLoginMethods: {
                type: "array",
                items: {
                  type: "object",
                  properties: {
                    method: { type: "string", enum: ["password", "openid"] },
                    displayName: { type: "string", maxLength: 80 },
                    active: { type: "boolean" },
                  },
                  required: ["method", "displayName", "active"],
                  additionalProperties: false,
                },
              },
            },
            required: ["bootstrapped", "loginMethod", "availableLoginMethods"],
            additionalProperties: false,
          },
          503: errorSchema,
          429: errorSchema,
        },
      },
    },
    async (request, reply) => {
      reply.header("cache-control", "no-store");
      const limit = actualLoginMethodsRateLimiter.reserveRequest(request.ip);
      if (!limit.allowed) {
        return reply
          .header("retry-after", String(limit.retryAfterSeconds))
          .code(429)
          .send({
            error: {
              code: "rate_limited",
              message: "Too many sign-in checks. Try again shortly.",
            },
          });
      }
      if (!options.readActualLoginMethods) {
        return reply.code(503).send(actualLoginMethodsUnavailable());
      }

      try {
        return await actualLoginMethodsCache.read(
          options.readActualLoginMethods,
        );
      } catch {
        return reply.code(503).send(actualLoginMethodsUnavailable());
      }
    },
  );

  if (options.deviceAuth) {
    registerDeviceAuthorizationRoutes(app, {
      deviceAuth: options.deviceAuth,
      trustProxy: options.trustProxy ?? false,
      ...(options.signInWithActualPassword === undefined
        ? {}
        : { signInWithActualPassword: options.signInWithActualPassword }),
      ...(options.persistActualSessionToken === undefined
        ? {}
        : { persistActualSessionToken: options.persistActualSessionToken }),
      ...(options.actualOpenIdLogin === undefined
        ? {}
        : { actualOpenIdLogin: options.actualOpenIdLogin }),
      requireDevice,
    });
    registerTransactionRoutes(app, options.deviceAuth, options.transactionApi);
    registerBudgetMaintenanceRoutes(app, {
      deviceAuth: options.deviceAuth,
      maintenanceApi: options.budgetMaintenanceApi,
      journal: options.maintenanceJournal,
      requireDevice,
      invalidBudgetRequest,
      transactionNotFound,
      actualNotConfigured,
      reportTransactionFailure,
      actualRequestFailed,
    });
    registerBudgetReadRoutes(app, {
      deviceAuth: options.deviceAuth,
      budgetReadApi: options.budgetReadApi,
      requireDevice,
      invalidBudgetRequest,
      transactionNotFound,
      actualNotConfigured,
      budgetReadFailed,
      reportTransactionFailure,
    });
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

interface TransactionRouteParams {
  budgetId: string;
  transactionId: string;
}

interface OperationRouteParams {
  operationId: string;
}

interface OperationHeaders {
  "x-budget-encryption-password"?: string;
}

interface SubmitTransactionBody {
  operationId: string;
  action: "create" | "update" | "delete";
  baseRevision?: string;
  baseSnapshot?: JsonValue;
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
          action: { type: "string", enum: ["create", "update", "delete"] },
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
            additionalProperties: true,
            items: {},
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
      const device = await requireDevice(deviceAuth, request, reply);
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
      } catch (error) {
        reportTransactionFailure("Transaction read failed", error);
        return actualRequestFailed(reply, error);
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
          "Submit an idempotent stable-ID transaction create, update, or deletion",
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
            action: { type: "string", enum: ["create", "update", "delete"] },
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
          required: ["operationId", "action", "patch"],
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
      const device = await requireDevice(deviceAuth, request, reply);
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
        if (
          request.body.action === "create" &&
          (request.body.baseRevision !== undefined ||
            request.body.baseSnapshot !== undefined)
        ) {
          return invalidTransactionRequest(reply);
        }
        if (
          request.body.action !== "create" &&
          (request.body.baseRevision === undefined ||
            request.body.baseSnapshot === undefined)
        ) {
          return invalidTransactionRequest(reply);
        }
        const transactionRequest =
          request.body.action === "create"
            ? {
                operationId: request.body.operationId,
                principalId: device.principalId,
                budgetId: request.params.budgetId,
                idempotencyKey,
                transactionId: request.params.transactionId,
                action: "create" as const,
                patch: request.body.patch,
                ...(request.headers["x-budget-encryption-password"] ===
                undefined
                  ? {}
                  : {
                      encryptionPassword:
                        request.headers["x-budget-encryption-password"],
                    }),
              }
            : {
                operationId: request.body.operationId,
                principalId: device.principalId,
                budgetId: request.params.budgetId,
                idempotencyKey,
                transactionId: request.params.transactionId,
                baseRevision: request.body.baseRevision!,
                baseSnapshot: request.body.baseSnapshot!,
                action: request.body.action,
                patch: request.body.patch,
                ...(request.headers["x-budget-encryption-password"] ===
                undefined
                  ? {}
                  : {
                      encryptionPassword:
                        request.headers["x-budget-encryption-password"],
                    }),
              };
        const result = await transactionApi.submit({
          ...transactionRequest,
        });
        if (result.operation.status === "rejected") {
          const errorCode = result.operation.errorCode ?? "invalid_request";
          return invalidTransactionRequest(
            reply,
            rejectedTransactionMessage(errorCode),
            errorCode,
          );
        }
        reply.header("cache-control", "no-store");
        return reply.code(200).send(toOperationResultResponse(result));
      } catch (error) {
        if (error instanceof TransactionRequestValidationError) {
          return invalidTransactionRequest(
            reply,
            error.message,
            error.errorCode,
          );
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
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.get<{ Params: OperationRouteParams; Headers: OperationHeaders }>(
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
        headers: budgetEncryptionHeaderSchema,
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
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidTransactionRequest(reply);
      if (!transactionApi) return actualNotConfigured(reply);

      try {
        const encryptionPassword =
          request.headers["x-budget-encryption-password"];
        const result =
          encryptionPassword === undefined
            ? await transactionApi.getOperation(
                device.principalId,
                request.params.operationId,
              )
            : await transactionApi.getOperation(
                device.principalId,
                request.params.operationId,
                encryptionPassword,
              );
        if (!result) {
          return transactionNotFound(reply);
        } else if (
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
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.post<{ Params: OperationRouteParams }>(
    "/api/v1/operations/:operationId/discard",
    {
      schema: {
        tags: ["Transactions"],
        operationId: "discardUncertainTransactionOperation",
        summary: "Discard an uncertain transaction operation on the server",
        description:
          "Marks an uncertain operation as discarded only if it is still uncertain. If its status changed, returns the current status without discarding it.",
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
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidTransactionRequest(reply);
      if (!transactionApi) return actualNotConfigured(reply);

      try {
        const result = await transactionApi.discardUncertainOperation(
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
        reportTransactionFailure("Uncertain operation discard failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );
}

function safeHttpStatus(statusCode: number | undefined): number {
  switch (statusCode) {
    case 400:
    case 401:
    case 404:
    case 409:
    case 413:
    case 415:
    case 429:
    case 500:
    case 502:
    case 503:
      return statusCode;
    case 422:
      // Validation failures use the API's documented BadRequest status.
      return 400;
    default:
      // Never emit a framework status without a corresponding API response.
      return 500;
  }
}

export function frameworkErrorStatus(error: unknown): number | undefined {
  if (typeof error !== "object" || error === null || !("statusCode" in error)) {
    return undefined;
  }
  const statusCode = (error as { statusCode?: unknown }).statusCode;
  return typeof statusCode === "number" ? statusCode : undefined;
}

export function frameworkErrorCode(error: unknown): string | undefined {
  if (typeof error !== "object" || error === null || !("code" in error)) {
    return undefined;
  }
  const code = (error as { code?: unknown }).code;
  return typeof code === "string" ? code : undefined;
}

export function safeFrameworkErrorResponse(
  statusCode: number,
  frameworkCode?: string,
) {
  if (
    frameworkCode === "FST_ERR_CTP_INVALID_MEDIA_TYPE" ||
    statusCode === 415
  ) {
    return {
      code: "unsupported_media_type",
      message: "Send this request as application/json.",
    };
  }
  if (frameworkCode === "FST_ERR_CTP_BODY_TOO_LARGE" || statusCode === 413) {
    return {
      code: "request_too_large",
      message: "This request is too large. Reduce it and try again.",
    };
  }
  switch (statusCode) {
    case 400:
      return {
        code: "invalid_request",
        message: "Check the request details and try again.",
      };
    case 401:
      return {
        code: "invalid_device_credential",
        message: "Valid device credentials are required.",
      };
    case 404:
      return {
        code: "route_not_found",
        message:
          "Tandem API route was not found. Check the address and /api/v1/ proxy route.",
      };
    case 409:
      return {
        code: "request_conflict",
        message:
          "This request conflicts with the latest budget. Refresh, then try again.",
      };
    case 429:
      return {
        code: "rate_limited",
        message: "Too many requests. Wait a moment, then try again.",
      };
    case 502:
      return {
        code: "actual_operation_failed",
        message: "The Actual Budget operation could not be completed.",
      };
    case 503:
      return {
        code: "actual_unavailable",
        message: "Check the server connection and try again.",
      };
    default:
      return {
        code: "internal_error",
        message: "Tandem couldn’t complete the request. Try again shortly.",
      };
  }
}

export function errorCodeFromPayload(payload: unknown): string | undefined {
  if (typeof payload !== "string") return undefined;
  try {
    const value: unknown = JSON.parse(payload);
    if (typeof value !== "object" || value === null || !("error" in value))
      return undefined;
    const error = (value as { error?: unknown }).error;
    if (typeof error !== "object" || error === null || !("code" in error))
      return undefined;
    const code = (error as { code?: unknown }).code;
    return typeof code === "string" && /^[a-z0-9_-]{1,80}$/i.test(code)
      ? code
      : undefined;
  } catch {
    return undefined;
  }
}

export function responseErrorCode(
  payload: unknown,
  statusCode: number,
): string {
  return errorCodeFromPayload(payload) ?? `http_${statusCode}`;
}

function invalidBudgetRequest(reply: import("fastify").FastifyReply) {
  return reply.code(400).send({
    error: {
      code: "invalid_request",
      message: "Budget read request is invalid",
    },
  });
}

function budgetReadFailed(
  reply: import("fastify").FastifyReply,
  error?: unknown,
) {
  if (error instanceof ActualPrincipalSessionExpiredError) {
    return reply.code(401).send({
      error: {
        code: "actual_sign_in_expired",
        message: ACTUAL_PRINCIPAL_SESSION_EXPIRED_MESSAGE,
      },
    });
  }
  return reply.code(502).send({
    error: {
      code: "budget_sync_failed",
      message: "The budget projection could not be refreshed",
    },
  });
}

function invalidTransactionRequest(
  reply: import("fastify").FastifyReply,
  message = "Transaction request is invalid",
  code = "invalid_request",
) {
  return reply.code(400).send({
    error: { code, message },
  });
}

function rejectedTransactionMessage(errorCode: string): string {
  switch (errorCode) {
    case "account-not-found":
      return "Account does not exist in this budget";
    case "account-closed":
      return "Choose an open account for this transaction";
    case "payee-not-found":
      return "Payee does not exist in this budget";
    case "transfer-account-closed":
      return "Choose an open destination account for this transfer";
    case "transfer-account-same-as-source":
      return "Choose a different destination account for this transfer";
    case "category-not-found":
      return "Category does not exist in this budget";
    case "category-hidden":
      return "Choose a visible category for this transaction";
    default:
      return "Transaction request is invalid";
  }
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

function actualLoginMethodsUnavailable() {
  return {
    error: {
      code: "actual_unavailable",
      message:
        "Could not check the Actual sign-in method. Check the server connection.",
    },
  };
}

function actualRequestFailed(
  reply: import("fastify").FastifyReply,
  error?: unknown,
) {
  if (isStorageFullError(error)) {
    return reply.code(503).send({ error: SERVER_STORAGE_FULL_ERROR });
  }
  if (error instanceof ActualPrincipalSessionExpiredError) {
    return reply.code(401).send({
      error: {
        code: "actual_sign_in_expired",
        message: ACTUAL_PRINCIPAL_SESSION_EXPIRED_MESSAGE,
      },
    });
  }
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
  request: FastifyRequest,
  reply: import("fastify").FastifyReply,
) {
  const authorization = request.headers.authorization;
  const match = authorization?.match(/^Bearer ([A-Za-z0-9_-]{43})$/i);
  const device = await deviceAuth.authenticate(match?.[1]);
  if (device) {
    return device;
  }
  logSecurityAuditEvent({
    event: "device.auth",
    outcome: "rejected",
    reason: "invalid_device_credential",
    clientAddress: request.ip,
    route: request.routeOptions.url,
  });
  reply.header("www-authenticate", 'Bearer realm="tandem-for-actual"');
  await reply.code(401).send({
    error: {
      code: "invalid_device_credential",
      message: "Valid device credentials required",
    },
  });
  return undefined;
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
