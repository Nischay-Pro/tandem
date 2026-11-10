import swagger from "@fastify/swagger";
import { createHash, randomUUID } from "node:crypto";
import Fastify, {
  type FastifyInstance,
  type FastifyRequest,
  type FastifySchema,
} from "fastify";
import { READINESS_TIMEOUT_MS } from "./config.js";
import type { HealthDatabase } from "./database.js";
import {
  DeviceAuthorization,
  DeviceAuthInputError,
} from "./auth/device-auth.js";
import {
  ActualPasswordRejectedError,
  ActualPasswordUnavailableError,
  type ActualPasswordGrant,
} from "./auth/actual-password-login.js";
import type { ActualLoginMethods } from "./auth/actual-login-methods.js";
import type { ActualOpenIdLogin } from "./auth/actual-openid-login.js";
import { ActualLoginMethodsCache } from "./auth/actual-login-methods-cache.js";
import { RequestRateLimiter } from "./auth/request-rate-limiter.js";
import { logSecurityAuditEvent } from "./security-audit.js";
import { enterActualPrincipal } from "./auth/actual-principal-context.js";
import type { TransactionApiPort } from "./actual/transaction-api.js";
import {
  BudgetMaintenanceBusyError,
  BudgetMaintenanceInputError,
  MAX_BUDGET_AMOUNT_MINOR,
  type BudgetTemplateAssignment,
  type BudgetMaintenanceApiPort,
  type BudgetScheduleEdit,
  type StatementTransactionImport,
} from "./actual/budget-maintenance-api.js";
import type { BudgetReadApiPort } from "./budget-projection/budget-read-api.js";
import {
  BUDGET_ENTITY_TYPES,
  BudgetProjectionInputError,
} from "./budget-projection/contracts.js";
import {
  IDEMPOTENCY_KEY_MAX_LENGTH,
  IdempotencyKeyReuseError,
  OperationIdReuseError,
  type DurableWriteJournal,
  type JsonValue,
  type OperationConflict,
  type WriteOperation,
} from "./write-journal/contracts.js";
import { TransactionRequestValidationError } from "./actual/transaction-reconciler.js";
import { MAX_TRANSACTION_AMOUNT } from "./actual/transaction-reconciliation.js";

const statusOk = {
  type: "object",
  properties: { status: { type: "string", const: "ok" } },
  required: ["status"],
  additionalProperties: false,
};

const statusNotReady = {
  type: "object",
  properties: {
    status: { type: "string", const: "not_ready" },
    message: { type: "string" },
  },
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
  summary: "Check whether the API can reach its configured services",
  response: { 200: statusOk, 503: statusNotReady },
};

export interface ApiAppOptions {
  readinessTimeoutMs?: number;
  actualReadiness?: () => { ready: boolean; message?: string };
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
  maintenanceJournal?: Pick<
    DurableWriteJournal,
    "enqueue" | "claim" | "markCommitted" | "markRejected" | "markUncertain"
  >;
  budgetReadApi?: BudgetReadApiPort;
  trustProxy?: false | string[];
}

export async function buildApiApp(
  database: HealthDatabase,
  options: ApiAppOptions = {},
): Promise<FastifyInstance> {
  const readinessTimeoutMs = options.readinessTimeoutMs ?? READINESS_TIMEOUT_MS;
  const actualLoginMethodsCache = new ActualLoginMethodsCache(45_000);
  const actualLoginMethodsRateLimiter = new RequestRateLimiter(60, 60_000);
  const app = Fastify({
    logger: false,
    trustProxy: options.trustProxy ?? false,
  });
  if (options.deviceAuth) {
    app.addHook("onRequest", async (request) => {
      const match = request.headers.authorization?.match(
        /^Bearer ([A-Za-z0-9_-]{43})$/i,
      );
      const device = await options.deviceAuth?.authenticate(match?.[1]);
      if (device) enterActualPrincipal(device.principalId);
    });
  }

  await app.register(swagger, {
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

  app.get<{ Reply: { status: "ok" | "not_ready" } }>(
    "/api/v1/health/ready",
    { schema: readySchema },
    async (_request, reply) => {
      try {
        await pingWithTimeout(() => database.ping(), readinessTimeoutMs);
      } catch {
        return reply.code(503).send({ status: "not_ready" });
      }
      const actualReadiness = options.actualReadiness?.();
      if (actualReadiness && !actualReadiness.ready) {
        return reply.code(503).send({
          status: "not_ready",
          ...(actualReadiness.message === undefined
            ? {}
            : { message: actualReadiness.message }),
        });
      }
      return reply.code(200).send({ status: "ok" });
    },
  );

  app.get("/api/v1/openapi.json", async () => app.swagger());

  app.get(
    "/api/v1/auth/methods",
    {
      schema: {
        tags: ["Authentication"],
        operationId: "getActualLoginMethods",
        summary: "Check the configured Actual server sign-in method",
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
          503: {
            type: "object",
            properties: {
              error: {
                type: "object",
                properties: {
                  code: { type: "string", const: "actual_unavailable" },
                  message: { type: "string" },
                },
                required: ["code", "message"],
                additionalProperties: false,
              },
            },
            required: ["error"],
            additionalProperties: false,
          },
          429: {
            type: "object",
            properties: {
              error: {
                type: "object",
                properties: {
                  code: { type: "string", const: "rate_limited" },
                  message: { type: "string" },
                },
                required: ["code", "message"],
                additionalProperties: false,
              },
            },
            required: ["error"],
            additionalProperties: false,
          },
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
    registerDeviceAuthorizationRoutes(
      app,
      options.deviceAuth,
      options.trustProxy ?? false,
      options.signInWithActualPassword,
      options.persistActualSessionToken,
      options.actualOpenIdLogin,
    );
    registerTransactionRoutes(app, options.deviceAuth, options.transactionApi);
    registerBudgetMaintenanceRoutes(
      app,
      options.deviceAuth,
      options.budgetMaintenanceApi,
      options.maintenanceJournal,
    );
    registerBudgetReadRoutes(app, options.deviceAuth, options.budgetReadApi);
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
  trustProxy: false | string[],
  signInWithActualPassword?: (password: string) => Promise<ActualPasswordGrant>,
  persistActualSessionToken?: (
    principalId: string,
    sessionToken: string,
  ) => Promise<void>,
  actualOpenIdLogin?: ActualOpenIdLogin,
): void {
  const rateLimiter = new RequestRateLimiter(10, 60_000);
  let warnedAboutIgnoredForwardedFor = false;
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
  const signInSchema: FastifySchema = {
    tags: ["Authentication"],
    operationId: "signInWithActualPassword",
    summary: "Sign in with the Actual account password",
    body: {
      type: "object",
      properties: {
        password: {
          type: "string",
          minLength: 1,
          maxLength: 1024,
        },
        deviceName: {
          type: "string",
          minLength: 1,
          maxLength: 100,
          pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
        },
      },
      required: ["password", "deviceName"],
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
      503: errorSchema,
    },
  };
  app.post<{
    Body: { password: string; deviceName: string };
  }>(
    "/api/v1/auth/login",
    { schema: signInSchema, attachValidation: true },
    async (request, reply) => {
      reply.header("cache-control", "no-store");
      if (request.validationError) {
        logSecurityAuditEvent({
          event: "device.sign_in",
          outcome: "rejected",
          reason: "invalid_request",
          clientAddress: request.ip,
        });
        return reply.code(400).send({
          error: {
            code: "invalid_request",
            message: "Sign-in details are invalid",
          },
        });
      }

      if (
        trustProxy === false &&
        request.headers["x-forwarded-for"] &&
        !warnedAboutIgnoredForwardedFor
      ) {
        warnedAboutIgnoredForwardedFor = true;
        console.warn(
          "X-Forwarded-For is present while TRUST_PROXY is unset; sign-in requests share the proxy's rate limit.",
        );
      }

      const limit = rateLimiter.reserveRequest(request.ip);
      if (!limit.allowed) {
        logSecurityAuditEvent({
          event: "device.sign_in_rate_limited",
          outcome: "rejected",
          reason: "rate_limited",
          clientAddress: request.ip,
        });
        return reply
          .header("retry-after", String(limit.retryAfterSeconds))
          .code(429)
          .send({
            error: {
              code: "rate_limited",
              message: "Too many sign-in attempts. Try again shortly.",
            },
          });
      }

      if (!signInWithActualPassword) {
        return reply.code(503).send({
          error: {
            code: "actual_unavailable",
            message:
              "Actual sign-in is unavailable. Check the server connection.",
          },
        });
      }

      let grant: ActualPasswordGrant;
      let signedIn;
      try {
        grant = await signInWithActualPassword(request.body.password);
        await persistActualSessionToken?.(
          grant.principalId,
          grant.sessionToken,
        );
        signedIn = await deviceAuth.createDevice({
          principalId: grant.principalId,
          budgetIds: grant.budgetIds,
          deviceName: request.body.deviceName,
        });
      } catch (error) {
        if (error instanceof ActualPasswordRejectedError) {
          logSecurityAuditEvent({
            event: "device.sign_in",
            outcome: "rejected",
            reason: "invalid_credentials",
            clientAddress: request.ip,
          });
          return reply.code(401).send({
            error: {
              code: "invalid_credentials",
              message: "The password didn’t match this Actual server.",
            },
          });
        }
        if (error instanceof ActualPasswordUnavailableError) {
          return reply.code(503).send({
            error: {
              code: "actual_unavailable",
              message: "Couldn’t reach the Actual server. Try again shortly.",
            },
          });
        }
        if (error instanceof DeviceAuthInputError) {
          return reply.code(400).send({
            error: { code: "invalid_request", message: error.message },
          });
        }
        throw error;
      }
      if (!signedIn) {
        return reply.code(503).send({
          error: {
            code: "actual_unavailable",
            message: "Actual sign-in is unavailable. Try again shortly.",
          },
        });
      }
      logSecurityAuditEvent({
        event: "device.sign_in",
        outcome: "success",
        clientAddress: request.ip,
        deviceId: signedIn.deviceId,
      });

      return reply.code(201).send(signInResponse(signedIn));
    },
  );

  if (actualOpenIdLogin) {
    app.post<{ Body: { deviceName: string } }>(
      "/api/v1/auth/openid/start",
      {
        schema: {
          tags: ["Authentication"],
          operationId: "startActualOpenIdSignIn",
          summary: "Start Actual OpenID sign-in",
          body: {
            type: "object",
            properties: {
              deviceName: {
                type: "string",
                minLength: 1,
                maxLength: 100,
                pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
              },
            },
            required: ["deviceName"],
            additionalProperties: false,
          },
          response: {
            200: {
              type: "object",
              properties: { redirectUrl: { type: "string", format: "uri" } },
              required: ["redirectUrl"],
              additionalProperties: false,
            },
            400: errorSchema,
            429: errorSchema,
            503: errorSchema,
          },
        },
        attachValidation: true,
      },
      async (request, reply) => {
        reply.header("cache-control", "no-store");
        if (request.validationError) {
          return reply.code(400).send({
            error: {
              code: "invalid_request",
              message: "Sign-in details are invalid",
            },
          });
        }
        const limit = rateLimiter.reserveRequest(request.ip);
        if (!limit.allowed) {
          return reply
            .header("retry-after", String(limit.retryAfterSeconds))
            .code(429)
            .send({
              error: {
                code: "rate_limited",
                message: "Too many sign-in attempts. Try again shortly.",
              },
            });
        }
        try {
          return await actualOpenIdLogin.start(request.body.deviceName);
        } catch {
          return reply.code(503).send({
            error: {
              code: "actual_unavailable",
              message: "Couldn’t start Actual sign-in. Try again shortly.",
            },
          });
        }
      },
    );

    app.get<{
      Params: { state: string };
      Querystring: { token?: string };
    }>(
      "/api/v1/auth/actual-return/:state/openid-cb",
      {
        schema: {
          tags: ["Authentication"],
          operationId: "completeActualOpenIdReturn",
          summary: "Receive Actual’s OpenID callback",
          params: {
            type: "object",
            properties: {
              state: { type: "string", minLength: 43, maxLength: 43 },
            },
            required: ["state"],
            additionalProperties: false,
          },
          querystring: {
            type: "object",
            properties: {
              token: { type: "string", minLength: 1, maxLength: 4096 },
            },
            required: ["token"],
            additionalProperties: false,
          },
          response: {
            302: { type: "null" },
            400: errorSchema,
            401: errorSchema,
            503: errorSchema,
          },
        },
        attachValidation: true,
      },
      async (request, reply) => {
        reply.header("cache-control", "no-store");
        reply.header("referrer-policy", "no-referrer");
        if (request.validationError || !request.query.token) {
          return reply.code(400).send({
            error: {
              code: "invalid_request",
              message: "Sign-in callback is invalid",
            },
          });
        }
        try {
          const returnUrl = await actualOpenIdLogin.completeCallback(
            request.params.state,
            request.query.token,
          );
          if (!returnUrl) {
            return reply.code(401).send({
              error: {
                code: "sign_in_expired",
                message: "Sign-in expired. Start again.",
              },
            });
          }
          return reply.code(302).header("location", returnUrl).send();
        } catch {
          return reply.code(401).send({
            error: {
              code: "invalid_credentials",
              message: "Actual sign-in could not be verified.",
            },
          });
        }
      },
    );

    app.post<{ Body: { code: string } }>(
      "/api/v1/auth/openid/complete",
      {
        schema: {
          tags: ["Authentication"],
          operationId: "completeActualOpenIdSignIn",
          summary: "Exchange a one-use OpenID return code for a device token",
          body: {
            type: "object",
            properties: {
              code: { type: "string", minLength: 43, maxLength: 43 },
            },
            required: ["code"],
            additionalProperties: false,
          },
          response: {
            201: signInResponseSchema(),
            400: errorSchema,
            401: errorSchema,
            503: errorSchema,
          },
        },
        attachValidation: true,
      },
      async (request, reply) => {
        reply.header("cache-control", "no-store");
        if (request.validationError) {
          return reply.code(400).send({
            error: {
              code: "invalid_request",
              message: "Sign-in code is invalid",
            },
          });
        }
        const completion = actualOpenIdLogin.consume(request.body.code);
        if (!completion) {
          return reply.code(401).send({
            error: {
              code: "sign_in_expired",
              message: "Sign-in expired. Start again.",
            },
          });
        }
        try {
          await persistActualSessionToken?.(
            completion.grant.principalId,
            completion.grant.sessionToken,
          );
          const signedIn = await deviceAuth.createDevice({
            principalId: completion.grant.principalId,
            budgetIds: completion.grant.budgetIds,
            deviceName: completion.deviceName,
          });
          logSecurityAuditEvent({
            event: "device.sign_in",
            method: "openid",
            outcome: "success",
            clientAddress: request.ip,
            deviceId: signedIn.deviceId,
          });
          return reply.code(201).send(signInResponse(signedIn));
        } catch {
          return reply.code(503).send({
            error: {
              code: "actual_unavailable",
              message: "Couldn’t finish Actual sign-in. Try again.",
            },
          });
        }
      },
    );
  }

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
      const device = await requireDevice(deviceAuth, request, reply);
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
      const device = await requireDevice(deviceAuth, request, reply);
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
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) {
        return reply;
      }
      const revoked = await deviceAuth.revokeDeviceForDevice(
        device,
        request.params.deviceId,
      );
      if (!revoked) {
        logSecurityAuditEvent({
          event: "device.revoke",
          outcome: "rejected",
          reason: "not_found_or_out_of_scope",
          clientAddress: request.ip,
          deviceId: device.deviceId,
          targetDeviceId: request.params.deviceId,
        });
        return reply.code(404).send({
          error: { code: "device_not_found", message: "Device was not found" },
        });
      }
      logSecurityAuditEvent({
        event: "device.revoke",
        outcome: "success",
        clientAddress: request.ip,
        deviceId: device.deviceId,
        targetDeviceId: request.params.deviceId,
      });
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
      const device = await requireDevice(deviceAuth, request, reply);
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

interface BudgetRouteParams {
  budgetId: string;
}

interface BudgetAccountRouteParams extends BudgetRouteParams {
  accountId: string;
}

interface BudgetCategoryRouteParams extends BudgetRouteParams {
  categoryId: string;
}

interface BudgetCategoryGroupRouteParams extends BudgetRouteParams {
  groupId: string;
}

interface BudgetScheduleRouteParams extends BudgetRouteParams {
  scheduleId: string;
}

interface BudgetPayeeRouteParams extends BudgetRouteParams {
  payeeId: string;
}

interface BudgetMonthRouteParams extends BudgetRouteParams {
  month: string;
}

interface BudgetAccountCreateBody {
  name: string;
  offbudget: boolean;
  accountGroupId?: string | null;
}

interface BudgetAccountCloseBody {
  transferAccountId: string | null;
  transferCategoryId: string | null;
}

interface BudgetAccountRenameBody {
  name: string;
}

interface BudgetAccountMoveBody {
  targetAccountId: string | null;
}

interface BudgetCategoryRenameBody {
  name: string;
}

interface BudgetCategoryGroupCreateBody {
  name: string;
  isIncome: boolean;
}

interface BudgetCategoryGroupActionBody {
  action: "rename" | "hide" | "show" | "move";
  name?: string;
  targetId?: string | null;
}

interface BudgetCategoryActionBody {
  action: "hide" | "show" | "move";
  targetId?: string | null;
  targetGroupId?: string | null;
}

interface BudgetCategoryCreateBody {
  name: string;
  categoryGroupId: string;
}

interface BudgetPayeeCreateBody {
  name: string;
}

interface BudgetPayeeActionBody {
  action: "rename" | "merge" | "delete" | "default-category";
  name?: string;
  targetPayeeId?: string;
  categoryId?: string | null;
}

interface BudgetCategoryBudgetAmountBody {
  month: string;
  amountMinor: number;
}

interface BudgetCategoryCarryoverBody {
  month: string;
  enabled: boolean;
}

interface BudgetMonthActionBody {
  action:
    "copy-last-month" | "hold-next-month" | "reset-hold" | "apply-template";
  amountMinor?: number;
  assignments?: BudgetTemplateAssignment[];
}

interface BudgetStatementImportBody {
  transactions: StatementTransactionImport[];
}

interface BudgetMoveMoneyBody {
  month: string;
  fromCategoryId: string;
  toCategoryId: string;
  amountMinor: number;
}

interface BudgetCategoryDeleteBody {
  transferCategoryId: string | null;
}

interface BudgetScheduleActionBody extends BudgetScheduleEdit {
  action: "rename" | "edit" | "post" | "skip" | "delete";
}

interface BudgetChangeQuery {
  generation: string;
  cursor: string;
  throughCursor?: string;
  limit?: number;
}

interface BudgetSnapshotPageQuery {
  generation?: string;
  cursor?: string;
  afterType?: (typeof BUDGET_ENTITY_TYPES)[number];
  afterId?: string;
  limit?: number;
}

interface BudgetReadHeaders {
  "x-budget-encryption-password"?: string;
  "idempotency-key"?: string;
}

function registerBudgetMaintenanceRoutes(
  app: FastifyInstance,
  deviceAuth: DeviceAuthorization,
  maintenanceApi: BudgetMaintenanceApiPort | undefined,
  journal: ApiAppOptions["maintenanceJournal"],
): void {
  const paramsSchema = {
    type: "object",
    properties: {
      budgetId: {
        type: "string",
        minLength: 1,
        maxLength: 500,
        pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
      },
    },
    required: ["budgetId"],
    additionalProperties: false,
  };
  const accountParamsSchema = {
    type: "object",
    properties: {
      budgetId: {
        type: "string",
        minLength: 1,
        maxLength: 500,
        pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
      },
      accountId: {
        type: "string",
        minLength: 1,
        maxLength: 500,
        pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
      },
    },
    required: ["budgetId", "accountId"],
    additionalProperties: false,
  };
  const categoryParamsSchema = {
    type: "object",
    properties: {
      budgetId: {
        type: "string",
        minLength: 1,
        maxLength: 500,
        pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
      },
      categoryId: {
        type: "string",
        minLength: 1,
        maxLength: 500,
        pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
      },
    },
    required: ["budgetId", "categoryId"],
    additionalProperties: false,
  };
  const categoryGroupParamsSchema = {
    type: "object",
    properties: {
      budgetId: {
        type: "string",
        minLength: 1,
        maxLength: 500,
        pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
      },
      groupId: {
        type: "string",
        minLength: 1,
        maxLength: 500,
        pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
      },
    },
    required: ["budgetId", "groupId"],
    additionalProperties: false,
  };
  const headersSchema = {
    type: "object",
    properties: {
      "x-budget-encryption-password": {
        type: "string",
        minLength: 1,
        maxLength: 1_024,
      },
      "idempotency-key": {
        type: "string",
        minLength: 1,
        maxLength: IDEMPOTENCY_KEY_MAX_LENGTH,
        pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
      },
    },
    required: ["idempotency-key"],
    additionalProperties: true,
  };
  const errorSchema = {
    type: "object",
    properties: {
      error: {
        type: "object",
        properties: { code: { type: "string" }, message: { type: "string" } },
        required: ["code", "message"],
        additionalProperties: false,
      },
    },
    required: ["error"],
    additionalProperties: false,
  };
  const commonResponseSchema = {
    200: statusOk,
    400: errorSchema,
    401: errorSchema,
    404: errorSchema,
    409: errorSchema,
    429: errorSchema,
    502: errorSchema,
    503: errorSchema,
  };

  app.post<{ Params: BudgetRouteParams; Headers: BudgetReadHeaders }>(
    "/api/v1/budgets/:budgetId/bank-sync",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "runBudgetBankSync",
        summary:
          "Run the configured Actual Budget bank sync and refresh its budget data",
        security: [{ bearerAuth: [] }],
        params: paramsSchema,
        headers: headersSchema,
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      try {
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "bank-sync",
          null,
          {},
          async () => {
            await maintenanceApi.runBankSync(
              request.params.budgetId,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "bank_sync",
          "success",
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceBusyError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "bank_sync",
            "rejected",
          );
          reply.header("retry-after", error.retryAfterSeconds.toString());
          return reply.code(429).send({
            error: { code: "bank_sync_busy", message: error.message },
          });
        }
        if (error instanceof MaintenanceOperationConflictError) {
          return reply.code(409).send({
            error: { code: error.code, message: error.message },
          });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "bank_sync",
          "failed",
        );
        reportTransactionFailure("Actual bank sync failed", error);
        return reply.code(502).send({
          error: {
            code: "bank_sync_failed",
            message:
              "Actual could not complete bank sync. Check its provider setup and try again.",
          },
        });
      }
    },
  );

  app.post<{
    Params: BudgetRouteParams & { accountId: string };
    Headers: BudgetReadHeaders;
    Body: BudgetStatementImportBody;
  }>(
    "/api/v1/budgets/:budgetId/accounts/:accountId/transactions/import",
    {
      bodyLimit: 2 * 1024 * 1024,
      schema: {
        tags: ["Transactions"],
        operationId: "importBudgetAccountTransactions",
        summary: "Import a parsed bank statement into an Actual Budget account",
        security: [{ bearerAuth: [] }],
        params: accountParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            transactions: {
              type: "array",
              minItems: 1,
              maxItems: 1_000,
              items: {
                type: "object",
                properties: {
                  date: { type: "string", format: "date" },
                  amountMinor: {
                    type: "integer",
                    minimum: -MAX_TRANSACTION_AMOUNT,
                    maximum: MAX_TRANSACTION_AMOUNT,
                    not: { const: 0 },
                  },
                  payeeName: { type: "string", minLength: 1, maxLength: 200 },
                  notes: { type: "string", maxLength: 500 },
                  importedId: { type: "string", minLength: 1, maxLength: 200 },
                },
                required: ["date", "amountMinor", "payeeName", "importedId"],
                additionalProperties: false,
              },
            },
          },
          required: ["transactions"],
          additionalProperties: false,
        },
        response: {
          200: {
            type: "object",
            properties: {
              status: { type: "string", const: "ok" },
              added: { type: "integer", minimum: 0 },
              updated: { type: "integer", minimum: 0 },
              errorCount: { type: "integer", minimum: 0 },
            },
            required: ["status", "added", "updated", "errorCount"],
            additionalProperties: false,
          },
          400: errorSchema,
          401: errorSchema,
          404: errorSchema,
          409: errorSchema,
          502: errorSchema,
        },
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      const transactions = request.body.transactions;
      const uniqueImportedIds = new Set(
        transactions.map((transaction) => transaction.importedId),
      );
      if (uniqueImportedIds.size !== transactions.length)
        return reply.code(400).send({
          error: {
            code: "duplicate_imported_id",
            message: "Statement rows must have unique references",
          },
        });
      try {
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "statement-import",
          request.params.accountId,
          statementImportJournalPatch(transactions),
          async () => {
            const imported = await maintenanceApi.importStatementTransactions(
              request.params.budgetId,
              request.params.accountId,
              transactions,
              request.headers["x-budget-encryption-password"],
            );
            return {
              status: "ok",
              added: imported.added,
              updated: imported.updated,
              errorCount: imported.errors.length,
            };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "statement_import",
          "success",
          request.params.accountId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof MaintenanceOperationConflictError) {
          return reply.code(409).send({
            error: { code: error.code, message: error.message },
          });
        }
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "statement_import",
            "rejected",
            request.params.accountId,
          );
          return reply.code(400).send({
            error: { code: "invalid_request", message: error.message },
          });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "statement_import",
          "failed",
          request.params.accountId,
        );
        reportTransactionFailure("Actual statement import failed", error);
        return reply.code(502).send({
          error: {
            code: "actual_operation_failed",
            message:
              "Actual could not import these transactions. Review the statement and try again.",
          },
        });
      }
    },
  );

  app.post<{
    Params: BudgetRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetAccountCreateBody;
  }>(
    "/api/v1/budgets/:budgetId/accounts",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "createBudgetAccount",
        summary: "Create an Actual Budget account",
        security: [{ bearerAuth: [] }],
        params: paramsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            name: {
              type: "string",
              minLength: 1,
              maxLength: 100,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
            offbudget: { type: "boolean" },
            accountGroupId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
            },
          },
          required: ["name", "offbudget"],
          additionalProperties: false,
        },
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      try {
        const name = request.body.name.trim();
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "account-create",
          null,
          {
            name,
            offbudget: request.body.offbudget,
            accountGroupId: request.body.accountGroupId ?? null,
          },
          async () => {
            await maintenanceApi.createAccount(
              request.params.budgetId,
              name,
              request.body.offbudget,
              request.body.accountGroupId ?? null,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "account_create",
          "success",
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "account_create",
            "rejected",
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "account_create",
          "failed",
        );
        reportTransactionFailure("Actual account creation failed", error);
        return actualRequestFailed(reply);
      }
    },
  );

  app.post<{
    Params: BudgetPayeeRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetPayeeActionBody;
  }>(
    "/api/v1/budgets/:budgetId/payees/:payeeId/actions",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "manageBudgetPayee",
        summary: "Rename, merge, or delete an Actual Budget payee",
        security: [{ bearerAuth: [] }],
        params: {
          type: "object",
          properties: {
            budgetId: paramsSchema.properties.budgetId,
            payeeId: {
              type: "string",
              minLength: 1,
              maxLength: 500,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
          },
          required: ["budgetId", "payeeId"],
          additionalProperties: false,
        },
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            action: {
              type: "string",
              enum: ["rename", "merge", "delete", "default-category"],
            },
            name: {
              type: "string",
              minLength: 1,
              maxLength: 100,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
            targetPayeeId: {
              type: "string",
              minLength: 1,
              maxLength: 500,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
            categoryId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
          },
          required: ["action"],
          additionalProperties: false,
        },
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      const { action } = request.body;
      const name = (request.body.name ?? "").trim();
      const targetPayeeId = request.body.targetPayeeId;
      const categoryId = request.body.categoryId;
      const hasCategoryId = Object.prototype.hasOwnProperty.call(
        request.body,
        "categoryId",
      );
      if (
        (action === "rename" && !name) ||
        (action !== "rename" && request.body.name !== undefined) ||
        (action === "merge" && targetPayeeId === undefined) ||
        (action !== "merge" && targetPayeeId !== undefined) ||
        (action === "default-category" && !hasCategoryId) ||
        (action !== "default-category" && hasCategoryId)
      ) {
        return invalidBudgetRequest(reply);
      }
      try {
        const patch: { [key: string]: JsonValue } = { action };
        if (action === "rename") patch.name = name;
        if (action === "merge") patch.targetPayeeId = targetPayeeId!;
        if (action === "default-category")
          patch.categoryId = categoryId ?? null;
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          `payee-${action}`,
          request.params.payeeId,
          patch,
          async () => {
            const password = request.headers["x-budget-encryption-password"];
            switch (action) {
              case "rename":
                await maintenanceApi.renamePayee(
                  request.params.budgetId,
                  request.params.payeeId,
                  name,
                  password,
                );
                break;
              case "merge":
                await maintenanceApi.mergePayee(
                  request.params.budgetId,
                  request.params.payeeId,
                  targetPayeeId!,
                  password,
                );
                break;
              case "delete":
                await maintenanceApi.deletePayee(
                  request.params.budgetId,
                  request.params.payeeId,
                  password,
                );
                break;
              case "default-category":
                await maintenanceApi.setPayeeDefaultCategory(
                  request.params.budgetId,
                  request.params.payeeId,
                  categoryId ?? null,
                  password,
                );
                break;
            }
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          `payee_${action}`,
          "success",
          request.params.payeeId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            `payee_${action}`,
            "rejected",
            request.params.payeeId,
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          `payee_${action}`,
          "failed",
          request.params.payeeId,
        );
        reportTransactionFailure("Actual payee action failed", error);
        return actualRequestFailed(reply);
      }
    },
  );

  app.post<{
    Params: BudgetAccountRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetAccountCloseBody;
  }>(
    "/api/v1/budgets/:budgetId/accounts/:accountId/close",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "closeBudgetAccount",
        summary: "Close an Actual Budget account",
        security: [{ bearerAuth: [] }],
        params: accountParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            transferAccountId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
            },
            transferCategoryId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
            },
          },
          required: ["transferAccountId", "transferCategoryId"],
          additionalProperties: false,
        },
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      try {
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "account-close",
          request.params.accountId,
          {
            transferAccountId: request.body.transferAccountId,
            transferCategoryId: request.body.transferCategoryId,
          },
          async () => {
            await maintenanceApi.closeAccount(
              request.params.budgetId,
              request.params.accountId,
              request.body.transferAccountId,
              request.body.transferCategoryId,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "account_close",
          "success",
          request.params.accountId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "account_close",
            "rejected",
            request.params.accountId,
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "account_close",
          "failed",
          request.params.accountId,
        );
        reportTransactionFailure("Actual account close failed", error);
        return actualRequestFailed(reply);
      }
    },
  );

  app.patch<{
    Params: BudgetAccountRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetAccountRenameBody;
  }>(
    "/api/v1/budgets/:budgetId/accounts/:accountId",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "renameBudgetAccount",
        summary: "Rename an Actual Budget account",
        security: [{ bearerAuth: [] }],
        params: accountParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            name: {
              type: "string",
              minLength: 1,
              maxLength: 100,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
          },
          required: ["name"],
          additionalProperties: false,
        },
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      try {
        const name = request.body.name.trim();
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "account-rename",
          request.params.accountId,
          { name },
          async () => {
            await maintenanceApi.renameAccount(
              request.params.budgetId,
              request.params.accountId,
              name,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "account_rename",
          "success",
          request.params.accountId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "account_rename",
            "rejected",
            request.params.accountId,
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "account_rename",
          "failed",
          request.params.accountId,
        );
        reportTransactionFailure("Actual account rename failed", error);
        return actualRequestFailed(reply);
      }
    },
  );

  app.post<{
    Params: BudgetAccountRouteParams;
    Headers: BudgetReadHeaders;
  }>(
    "/api/v1/budgets/:budgetId/accounts/:accountId/reopen",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "reopenBudgetAccount",
        summary: "Reopen an Actual Budget account",
        security: [{ bearerAuth: [] }],
        params: accountParamsSchema,
        headers: headersSchema,
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      try {
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "account-reopen",
          request.params.accountId,
          {},
          async () => {
            await maintenanceApi.reopenAccount(
              request.params.budgetId,
              request.params.accountId,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "account_reopen",
          "success",
          request.params.accountId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "account_reopen",
            "rejected",
            request.params.accountId,
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "account_reopen",
          "failed",
          request.params.accountId,
        );
        reportTransactionFailure("Actual account reopen failed", error);
        return actualRequestFailed(reply);
      }
    },
  );

  app.post<{
    Params: BudgetAccountRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetAccountMoveBody;
  }>(
    "/api/v1/budgets/:budgetId/accounts/:accountId/move",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "moveBudgetAccount",
        summary: "Reorder an Actual Budget account",
        security: [{ bearerAuth: [] }],
        params: accountParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            targetAccountId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
            },
          },
          required: ["targetAccountId"],
          additionalProperties: false,
        },
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      try {
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "account-move",
          request.params.accountId,
          { targetAccountId: request.body.targetAccountId },
          async () => {
            await maintenanceApi.moveAccount(
              request.params.budgetId,
              request.params.accountId,
              request.body.targetAccountId,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "account_move",
          "success",
          request.params.accountId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "account_move",
            "rejected",
            request.params.accountId,
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "account_move",
          "failed",
          request.params.accountId,
        );
        reportTransactionFailure("Actual account reorder failed", error);
        return actualRequestFailed(reply);
      }
    },
  );

  app.post<{
    Params: BudgetRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetPayeeCreateBody;
  }>(
    "/api/v1/budgets/:budgetId/payees",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "createBudgetPayee",
        summary: "Create an Actual Budget payee",
        security: [{ bearerAuth: [] }],
        params: paramsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            name: {
              type: "string",
              minLength: 1,
              maxLength: 100,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
          },
          required: ["name"],
          additionalProperties: false,
        },
        response: {
          200: {
            type: "object",
            properties: {
              status: { type: "string", enum: ["ok"] },
              payeeId: { type: "string", minLength: 1, maxLength: 500 },
            },
            required: ["status", "payeeId"],
            additionalProperties: false,
          },
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
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      try {
        const name = request.body.name.trim();
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "payee-create",
          null,
          { name },
          async () => ({
            payeeId: await maintenanceApi.createPayee(
              request.params.budgetId,
              name,
              request.headers["x-budget-encryption-password"],
            ),
          }),
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "payee_create",
          "success",
          result.payeeId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send({ status: "ok", payeeId: result.payeeId });
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "payee_create",
            "rejected",
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "payee_create",
          "failed",
        );
        reportTransactionFailure("Actual payee creation failed", error);
        return actualRequestFailed(reply);
      }
    },
  );

  app.post<{
    Params: BudgetRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetCategoryCreateBody;
  }>(
    "/api/v1/budgets/:budgetId/categories",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "createBudgetCategory",
        summary: "Create an Actual Budget category",
        security: [{ bearerAuth: [] }],
        params: paramsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            name: {
              type: "string",
              minLength: 1,
              maxLength: 100,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
            categoryGroupId: {
              type: "string",
              minLength: 1,
              maxLength: 500,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
          },
          required: ["name", "categoryGroupId"],
          additionalProperties: false,
        },
        response: {
          200: {
            type: "object",
            properties: {
              status: { type: "string", enum: ["ok"] },
              categoryId: { type: "string", minLength: 1, maxLength: 500 },
            },
            required: ["status", "categoryId"],
            additionalProperties: false,
          },
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
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      try {
        const name = request.body.name.trim();
        const categoryResult = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "category-create",
          null,
          { name, categoryGroupId: request.body.categoryGroupId },
          async () => ({
            categoryId: await maintenanceApi.createCategory(
              request.params.budgetId,
              name,
              request.body.categoryGroupId,
              request.headers["x-budget-encryption-password"],
            ),
          }),
        );
        const categoryId = categoryResult.categoryId;
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_create",
          "success",
          categoryId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send({ status: "ok", categoryId });
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "category_create",
            "rejected",
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_create",
          "failed",
        );
        reportTransactionFailure("Actual category creation failed", error);
        return actualRequestFailed(reply);
      }
    },
  );

  app.post<{
    Params: BudgetRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetCategoryGroupCreateBody;
  }>(
    "/api/v1/budgets/:budgetId/category-groups",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "createBudgetCategoryGroup",
        summary: "Create an Actual Budget category group",
        security: [{ bearerAuth: [] }],
        params: paramsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            name: {
              type: "string",
              minLength: 1,
              maxLength: 100,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
            isIncome: { type: "boolean" },
          },
          required: ["name", "isIncome"],
          additionalProperties: false,
        },
        response: {
          200: {
            type: "object",
            properties: {
              status: { type: "string", enum: ["ok"] },
              categoryGroupId: { type: "string", minLength: 1, maxLength: 500 },
            },
            required: ["status", "categoryGroupId"],
            additionalProperties: false,
          },
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
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      try {
        const name = request.body.name.trim();
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "category-group-create",
          null,
          { name, isIncome: request.body.isIncome },
          async () => ({
            categoryGroupId: await maintenanceApi.createCategoryGroup(
              request.params.budgetId,
              name,
              request.body.isIncome,
              request.headers["x-budget-encryption-password"],
            ),
          }),
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_group_create",
          "success",
          result.categoryGroupId,
        );
        reply.header("cache-control", "no-store");
        return reply
          .code(200)
          .send({ status: "ok", categoryGroupId: result.categoryGroupId });
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "category_group_create",
            "rejected",
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_group_create",
          "failed",
        );
        reportTransactionFailure(
          "Actual category group creation failed",
          error,
        );
        return actualRequestFailed(reply);
      }
    },
  );

  app.patch<{
    Params: BudgetCategoryGroupRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetCategoryGroupActionBody;
  }>(
    "/api/v1/budgets/:budgetId/category-groups/:groupId",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "manageBudgetCategoryGroup",
        summary:
          "Rename, hide, show, or reorder an Actual Budget category group",
        security: [{ bearerAuth: [] }],
        params: categoryGroupParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            action: {
              type: "string",
              enum: ["rename", "hide", "show", "move"],
            },
            name: {
              type: "string",
              minLength: 1,
              maxLength: 100,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
            targetId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
            },
          },
          required: ["action"],
          additionalProperties: false,
        },
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      try {
        if (request.body.action === "rename" && !request.body.name)
          return invalidBudgetRequest(reply);
        if (
          request.body.action === "move" &&
          request.body.targetId === undefined
        )
          return invalidBudgetRequest(reply);
        const action = request.body.action;
        const name = request.body.name?.trim();
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          `category-group-${action}`,
          request.params.groupId,
          {
            action,
            targetId: request.body.targetId ?? null,
            ...(name === undefined ? {} : { name }),
          },
          async () => {
            await maintenanceApi.manageCategoryGroup(
              request.params.budgetId,
              request.params.groupId,
              action,
              name,
              request.body.targetId,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          `category_group_${action}`,
          "success",
          request.params.groupId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            `category_group_${request.body.action}`,
            "rejected",
            request.params.groupId,
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          `category_group_${request.body.action}`,
          "failed",
          request.params.groupId,
        );
        reportTransactionFailure("Actual category group change failed", error);
        return actualRequestFailed(reply);
      }
    },
  );

  app.post<{
    Params: BudgetCategoryRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetCategoryActionBody;
  }>(
    "/api/v1/budgets/:budgetId/categories/:categoryId/actions",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "manageBudgetCategory",
        summary: "Hide, show, reorder, or move an Actual Budget category",
        security: [{ bearerAuth: [] }],
        params: categoryParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            action: { type: "string", enum: ["hide", "show", "move"] },
            targetId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
            },
            targetGroupId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
            },
          },
          required: ["action"],
          additionalProperties: false,
        },
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      try {
        if (
          request.body.action === "move" &&
          request.body.targetId === undefined &&
          request.body.targetGroupId === undefined
        )
          return invalidBudgetRequest(reply);
        const action = request.body.action;
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          `category-${action}`,
          request.params.categoryId,
          {
            action,
            targetId: request.body.targetId ?? null,
            targetGroupId: request.body.targetGroupId ?? null,
          },
          async () => {
            await maintenanceApi.manageCategory(
              request.params.budgetId,
              request.params.categoryId,
              action,
              request.body.targetId,
              request.headers["x-budget-encryption-password"],
              request.body.targetGroupId,
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          `category_${action}`,
          "success",
          request.params.categoryId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            `category_${request.body.action}`,
            "rejected",
            request.params.categoryId,
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          `category_${request.body.action}`,
          "failed",
          request.params.categoryId,
        );
        reportTransactionFailure("Actual category change failed", error);
        return actualRequestFailed(reply);
      }
    },
  );

  app.put<{
    Params: BudgetCategoryRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetCategoryBudgetAmountBody;
  }>(
    "/api/v1/budgets/:budgetId/categories/:categoryId/budget-amount",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "setBudgetCategoryAmount",
        summary: "Set a category's assigned amount for an Actual Budget month",
        security: [{ bearerAuth: [] }],
        params: categoryParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            month: { type: "string", pattern: "^[0-9]{4}-(0[1-9]|1[0-2])$" },
            amountMinor: {
              type: "integer",
              minimum: 0,
              maximum: MAX_BUDGET_AMOUNT_MINOR,
            },
          },
          required: ["month", "amountMinor"],
          additionalProperties: false,
        },
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      try {
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "category-budget-amount",
          request.params.categoryId,
          { month: request.body.month, amountMinor: request.body.amountMinor },
          async () => {
            await maintenanceApi.setBudgetAmount(
              request.params.budgetId,
              request.params.categoryId,
              request.body.month,
              request.body.amountMinor,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_budget",
          "success",
          request.params.categoryId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "category_budget",
            "rejected",
            request.params.categoryId,
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_budget",
          "failed",
          request.params.categoryId,
        );
        reportTransactionFailure("Actual category budget update failed", error);
        return actualRequestFailed(reply);
      }
    },
  );

  app.put<{
    Params: BudgetCategoryRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetCategoryCarryoverBody;
  }>(
    "/api/v1/budgets/:budgetId/categories/:categoryId/carryover",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "setBudgetCategoryCarryover",
        summary:
          "Set whether unused category money carries into the next month",
        security: [{ bearerAuth: [] }],
        params: categoryParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            month: { type: "string", pattern: "^[0-9]{4}-(0[1-9]|1[0-2])$" },
            enabled: { type: "boolean" },
          },
          required: ["month", "enabled"],
          additionalProperties: false,
        },
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      try {
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "category-carryover",
          request.params.categoryId,
          { month: request.body.month, enabled: request.body.enabled },
          async () => {
            await maintenanceApi.setBudgetCarryover(
              request.params.budgetId,
              request.params.categoryId,
              request.body.month,
              request.body.enabled,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_carryover",
          "success",
          request.params.categoryId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "category_carryover",
            "rejected",
            request.params.categoryId,
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_carryover",
          "failed",
          request.params.categoryId,
        );
        reportTransactionFailure(
          "Actual category carryover update failed",
          error,
        );
        return actualRequestFailed(reply);
      }
    },
  );

  app.post<{
    Params: BudgetMonthRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetMonthActionBody;
  }>(
    "/api/v1/budgets/:budgetId/months/:month/actions",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "manageBudgetMonth",
        summary: "Manage a month's carryover and category assignments",
        security: [{ bearerAuth: [] }],
        params: {
          type: "object",
          properties: {
            budgetId: paramsSchema.properties.budgetId,
            month: { type: "string", pattern: "^[0-9]{4}-(0[1-9]|1[0-2])$" },
          },
          required: ["budgetId", "month"],
          additionalProperties: false,
        },
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            action: {
              type: "string",
              enum: [
                "copy-last-month",
                "hold-next-month",
                "reset-hold",
                "apply-template",
              ],
            },
            amountMinor: {
              type: "integer",
              minimum: 1,
              maximum: MAX_BUDGET_AMOUNT_MINOR,
            },
            assignments: {
              type: "array",
              minItems: 1,
              maxItems: 500,
              items: {
                type: "object",
                properties: {
                  categoryId: { type: "string", minLength: 1, maxLength: 200 },
                  amountMinor: {
                    type: "integer",
                    minimum: 0,
                    maximum: MAX_BUDGET_AMOUNT_MINOR,
                  },
                },
                required: ["categoryId", "amountMinor"],
                additionalProperties: false,
              },
            },
          },
          required: ["action"],
          additionalProperties: false,
        },
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      const action = request.body.action;
      if (
        (action === "hold-next-month" &&
          request.body.amountMinor === undefined) ||
        (action !== "hold-next-month" && request.body.amountMinor !== undefined)
      ) {
        return invalidBudgetRequest(reply);
      }
      if (
        (action === "apply-template" &&
          request.body.assignments === undefined) ||
        (action !== "apply-template" && request.body.assignments !== undefined)
      ) {
        return invalidBudgetRequest(reply);
      }
      const auditAction = `budget_month_${action.replaceAll("-", "_")}`;
      const patch: { [key: string]: JsonValue } = { action };
      if (action === "hold-next-month") {
        patch.amountMinor = request.body.amountMinor!;
      }
      if (action === "apply-template") {
        patch.assignments = request.body.assignments!.map((assignment) => ({
          categoryId: assignment.categoryId,
          amountMinor: assignment.amountMinor,
        }));
      }
      try {
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          `budget-month-${action}`,
          request.params.month,
          patch,
          async () => {
            const password = request.headers["x-budget-encryption-password"];
            switch (action) {
              case "copy-last-month":
                await maintenanceApi.copyBudgetMonth(
                  request.params.budgetId,
                  request.params.month,
                  password,
                );
                break;
              case "hold-next-month":
                await maintenanceApi.holdBudgetForNextMonth(
                  request.params.budgetId,
                  request.params.month,
                  request.body.amountMinor!,
                  password,
                );
                break;
              case "reset-hold":
                await maintenanceApi.resetBudgetHold(
                  request.params.budgetId,
                  request.params.month,
                  password,
                );
                break;
              case "apply-template":
                await maintenanceApi.applyBudgetTemplate(
                  request.params.budgetId,
                  request.params.month,
                  request.body.assignments!,
                  password,
                );
                break;
            }
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          auditAction,
          "success",
          request.params.month,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            auditAction,
            "rejected",
            request.params.month,
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          auditAction,
          "failed",
          request.params.month,
        );
        reportTransactionFailure("Actual budget month action failed", error);
        return actualRequestFailed(reply);
      }
    },
  );

  app.post<{
    Params: BudgetRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetMoveMoneyBody;
  }>(
    "/api/v1/budgets/:budgetId/categories/move-money",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "moveMoneyBetweenCategories",
        summary: "Move an assigned amount between Actual Budget categories",
        security: [{ bearerAuth: [] }],
        params: paramsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            month: { type: "string", pattern: "^[0-9]{4}-(0[1-9]|1[0-2])$" },
            fromCategoryId: {
              type: "string",
              minLength: 1,
              maxLength: 500,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
            toCategoryId: {
              type: "string",
              minLength: 1,
              maxLength: 500,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
            amountMinor: {
              type: "integer",
              minimum: 1,
              maximum: MAX_BUDGET_AMOUNT_MINOR,
            },
          },
          required: ["month", "fromCategoryId", "toCategoryId", "amountMinor"],
          additionalProperties: false,
        },
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      try {
        const { month, fromCategoryId, toCategoryId, amountMinor } =
          request.body;
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "category-move-money",
          fromCategoryId,
          { month, fromCategoryId, toCategoryId, amountMinor },
          async () => {
            await maintenanceApi.moveMoney(
              request.params.budgetId,
              month,
              fromCategoryId,
              toCategoryId,
              amountMinor,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_move_money",
          "success",
          fromCategoryId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "category_move_money",
            "rejected",
            request.body.fromCategoryId,
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_move_money",
          "failed",
          request.body.fromCategoryId,
        );
        reportTransactionFailure("Actual category money move failed", error);
        return actualRequestFailed(reply);
      }
    },
  );

  app.patch<{
    Params: BudgetCategoryRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetCategoryRenameBody;
  }>(
    "/api/v1/budgets/:budgetId/categories/:categoryId",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "renameBudgetCategory",
        summary: "Rename an Actual Budget category",
        security: [{ bearerAuth: [] }],
        params: categoryParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            name: {
              type: "string",
              minLength: 1,
              maxLength: 100,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
          },
          required: ["name"],
          additionalProperties: false,
        },
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      try {
        const name = request.body.name.trim();
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "category-rename",
          request.params.categoryId,
          { name },
          async () => {
            await maintenanceApi.renameCategory(
              request.params.budgetId,
              request.params.categoryId,
              name,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_rename",
          "success",
          request.params.categoryId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "category_rename",
            "rejected",
            request.params.categoryId,
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_rename",
          "failed",
          request.params.categoryId,
        );
        reportTransactionFailure("Actual category rename failed", error);
        return actualRequestFailed(reply);
      }
    },
  );

  app.delete<{
    Params: BudgetCategoryRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetCategoryDeleteBody;
  }>(
    "/api/v1/budgets/:budgetId/categories/:categoryId",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "deleteBudgetCategory",
        summary:
          "Delete an Actual Budget category and reassign its transactions",
        security: [{ bearerAuth: [] }],
        params: categoryParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            transferCategoryId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
            },
          },
          required: ["transferCategoryId"],
          additionalProperties: false,
        },
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      try {
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "category-delete",
          request.params.categoryId,
          { transferCategoryId: request.body.transferCategoryId },
          async () => {
            await maintenanceApi.deleteCategory(
              request.params.budgetId,
              request.params.categoryId,
              request.body.transferCategoryId,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_delete",
          "success",
          request.params.categoryId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "category_delete",
            "rejected",
            request.params.categoryId,
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_delete",
          "failed",
          request.params.categoryId,
        );
        reportTransactionFailure("Actual category delete failed", error);
        return actualRequestFailed(reply);
      }
    },
  );

  app.post<{
    Params: BudgetScheduleRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetScheduleActionBody;
  }>(
    "/api/v1/budgets/:budgetId/schedules/:scheduleId/actions",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "manageBudgetSchedule",
        summary: "Edit or manage an Actual Budget schedule",
        security: [{ bearerAuth: [] }],
        params: {
          type: "object",
          properties: {
            budgetId: paramsSchema.properties.budgetId,
            scheduleId: {
              type: "string",
              minLength: 1,
              maxLength: 500,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
          },
          required: ["budgetId", "scheduleId"],
          additionalProperties: false,
        },
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            action: {
              type: "string",
              enum: ["rename", "edit", "post", "skip", "delete"],
            },
            name: {
              type: "string",
              minLength: 1,
              maxLength: 100,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
            accountId: { type: "string", minLength: 1, maxLength: 500 },
            payeeId: { type: "string", minLength: 1, maxLength: 500 },
            amountMinor: {
              type: "integer",
              minimum: -1_000_000_000,
              maximum: 1_000_000_000,
            },
            amountRangeMinor: {
              type: "object",
              properties: {
                num1: {
                  type: "integer",
                  minimum: -1_000_000_000,
                  maximum: 1_000_000_000,
                },
                num2: {
                  type: "integer",
                  minimum: -1_000_000_000,
                  maximum: 1_000_000_000,
                },
              },
              required: ["num1", "num2"],
              additionalProperties: false,
            },
            amountOp: { type: "string", enum: ["is", "isapprox", "isbetween"] },
            date: {
              oneOf: [
                {
                  type: "string",
                  pattern: "^\\d{4}-\\d{2}-\\d{2}$",
                },
                {
                  type: "object",
                  properties: {
                    frequency: {
                      type: "string",
                      enum: ["daily", "weekly", "monthly", "yearly"],
                    },
                    interval: { type: "integer", minimum: 1, maximum: 999 },
                    patterns: {
                      type: "array",
                      maxItems: 31,
                      items: {
                        type: "object",
                        properties: {
                          value: { type: "integer", minimum: -31, maximum: 31 },
                          type: {
                            type: "string",
                            enum: [
                              "SU",
                              "MO",
                              "TU",
                              "WE",
                              "TH",
                              "FR",
                              "SA",
                              "day",
                            ],
                          },
                        },
                        required: ["value", "type"],
                        additionalProperties: false,
                      },
                    },
                    skipWeekend: { type: "boolean" },
                    start: {
                      type: "string",
                      pattern: "^\\d{4}-\\d{2}-\\d{2}$",
                    },
                    endMode: {
                      type: "string",
                      enum: ["never", "after_n_occurrences", "on_date"],
                    },
                    endOccurrences: {
                      type: "integer",
                      minimum: 1,
                      maximum: 100_000,
                    },
                    endDate: {
                      type: "string",
                      pattern: "^\\d{4}-\\d{2}-\\d{2}$",
                    },
                    weekendSolveMode: {
                      type: "string",
                      enum: ["before", "after"],
                    },
                  },
                  required: ["frequency", "start"],
                  additionalProperties: false,
                },
              ],
            },
          },
          required: ["action"],
          additionalProperties: false,
        },
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      const body = request.body as BudgetScheduleActionBody;
      const { action } = body;
      const name = body.name?.trim();
      const edit: BudgetScheduleEdit = {
        ...(name === undefined ? {} : { name }),
        ...(body.accountId === undefined ? {} : { accountId: body.accountId }),
        ...(body.payeeId === undefined ? {} : { payeeId: body.payeeId }),
        ...(body.amountMinor === undefined
          ? {}
          : { amountMinor: body.amountMinor }),
        ...(body.amountRangeMinor === undefined
          ? {}
          : { amountRangeMinor: body.amountRangeMinor }),
        ...(body.amountOp === undefined ? {} : { amountOp: body.amountOp }),
        ...(body.date === undefined ? {} : { date: body.date }),
      };
      const hasEditFields = Object.keys(edit).length > 0;
      if (
        (action === "rename" && !name) ||
        (action === "rename" &&
          Object.keys(edit).some((field) => field !== "name")) ||
        (action === "edit" && !hasEditFields) ||
        (!(["rename", "edit"] as string[]).includes(action) && hasEditFields)
      ) {
        return invalidBudgetRequest(reply);
      }
      try {
        const patch: { [key: string]: JsonValue } = { action };
        if (action === "rename") patch.name = name!;
        if (action === "edit") {
          for (const [field, value] of Object.entries(edit)) {
            patch[field] = value as JsonValue;
          }
        }
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          `schedule-${action}`,
          request.params.scheduleId,
          patch,
          async () => {
            const password = request.headers["x-budget-encryption-password"];
            switch (action) {
              case "rename":
                await maintenanceApi.renameSchedule(
                  request.params.budgetId,
                  request.params.scheduleId,
                  name!,
                  password,
                );
                break;
              case "edit":
                await maintenanceApi.editSchedule(
                  request.params.budgetId,
                  request.params.scheduleId,
                  edit,
                  password,
                );
                break;
              case "post":
                await maintenanceApi.postSchedule(
                  request.params.budgetId,
                  request.params.scheduleId,
                  password,
                );
                break;
              case "skip":
                await maintenanceApi.skipSchedule(
                  request.params.budgetId,
                  request.params.scheduleId,
                  password,
                );
                break;
              case "delete":
                await maintenanceApi.deleteSchedule(
                  request.params.budgetId,
                  request.params.scheduleId,
                  password,
                );
                break;
            }
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          `schedule_${action}`,
          "success",
          request.params.scheduleId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            `schedule_${action}`,
            "rejected",
            request.params.scheduleId,
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          `schedule_${action}`,
          "failed",
          request.params.scheduleId,
        );
        reportTransactionFailure("Actual schedule action failed", error);
        return actualRequestFailed(reply);
      }
    },
  );
}

class MaintenanceOperationConflictError extends BudgetMaintenanceInputError {
  constructor(
    readonly code:
      | "idempotency_key_reused"
      | "operation_in_progress"
      | "operation_uncertain"
      | "operation_rejected"
      | "operation_result_unavailable",
    message: string,
  ) {
    super(message);
    this.name = "MaintenanceOperationConflictError";
  }
}

async function runMaintenanceOperation<T extends JsonValue>(
  journal: NonNullable<ApiAppOptions["maintenanceJournal"]>,
  request: FastifyRequest,
  device: { deviceId: string; principalId: string },
  budgetId: string,
  action: string,
  entityId: string | null,
  patch: { [key: string]: JsonValue },
  work: () => Promise<T>,
): Promise<T> {
  const idempotencyKey = request.headers["idempotency-key"];
  // Fastify rejects missing and whitespace-only keys before this private handler.
  /* v8 ignore next -- retain a defensive check for direct/internal callers. */
  if (
    typeof idempotencyKey !== "string" ||
    idempotencyKey.trim().length === 0
  ) {
    throw new BudgetMaintenanceInputError("An idempotency key is required");
  }

  let queued;
  try {
    queued = await journal.enqueue({
      operationId: randomUUID(),
      principalId: device.principalId,
      budgetId,
      idempotencyKey,
      entityType: "budget-maintenance",
      action,
      entityId,
      baseRevision: null,
      baseSnapshot: {},
      patch,
    });
  } catch (error) {
    if (error instanceof IdempotencyKeyReuseError) {
      throw new MaintenanceOperationConflictError(
        "idempotency_key_reused",
        "This idempotency key was already used for a different change. Submit the change again.",
      );
    }
    throw error;
  }

  let operation = queued.operation;
  if (operation.status === "committed") {
    if (operation.actualResult !== null) return operation.actualResult as T;
    throw new MaintenanceOperationConflictError(
      "operation_result_unavailable",
      "This earlier change was applied, but its result has expired. Refresh the budget before continuing.",
    );
  }
  if (operation.status === "queued") {
    const claim = await journal.claim(operation.operationId);
    operation = claim.operation;
    if (claim.claimed) {
      try {
        const result = await work();
        await journal.markCommitted(operation.operationId, result);
        return result;
      } catch (error) {
        if (error instanceof BudgetMaintenanceBusyError) {
          await journal.markRejected(operation.operationId, "bank-sync-busy");
          throw error;
        }
        if (error instanceof BudgetMaintenanceInputError) {
          await journal.markRejected(operation.operationId, "invalid-request");
          throw error;
        }
        await journal.markUncertain(
          operation.operationId,
          "actual-result-unknown",
        );
        throw error;
      }
    }
  }

  if (operation.status === "applying" || operation.status === "queued") {
    throw new MaintenanceOperationConflictError(
      "operation_in_progress",
      "This change is still being confirmed. Check the budget before trying again.",
    );
  }
  if (operation.status === "uncertain") {
    throw new MaintenanceOperationConflictError(
      "operation_uncertain",
      "Tandem could not confirm whether this change reached Actual. Check the budget before submitting it again.",
    );
  }
  if (operation.status === "rejected") {
    throw new MaintenanceOperationConflictError(
      "operation_rejected",
      "This change was rejected earlier. Correct it and submit it again with a new request.",
    );
  }
  throw new MaintenanceOperationConflictError(
    "operation_uncertain",
    "This change was not applied. Submit it again with a new request.",
  );
}

function statementImportJournalPatch(
  transactions: readonly StatementTransactionImport[],
): { rowCount: number; rowsHash: string } {
  const canonicalRows = transactions
    .map((transaction) => ({
      date: transaction.date,
      amountMinor: transaction.amountMinor,
      payeeName: transaction.payeeName,
      ...(transaction.notes === undefined ? {} : { notes: transaction.notes }),
      importedId: transaction.importedId,
    }))
    .sort((left, right) => left.importedId.localeCompare(right.importedId));
  const rowsHash = createHash("sha256")
    .update(JSON.stringify(canonicalRows))
    .digest("hex");
  return { rowCount: transactions.length, rowsHash };
}

function logBudgetMaintenanceEvent(
  request: FastifyRequest,
  device: { deviceId: string },
  budgetId: string,
  action: string,
  outcome: "success" | "failed" | "rejected",
  targetId?: string,
): void {
  logSecurityAuditEvent({
    event: `budget.${action}`,
    outcome,
    clientAddress: request.ip,
    deviceId: device.deviceId,
    budgetId,
    ...(targetId === undefined ? {} : { targetId }),
  });
}

function registerBudgetReadRoutes(
  app: FastifyInstance,
  deviceAuth: DeviceAuthorization,
  budgetReadApi: BudgetReadApiPort | undefined,
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
  const budgetParamsSchema = {
    type: "object",
    properties: {
      budgetId: {
        type: "string",
        minLength: 1,
        maxLength: 500,
        pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
      },
    },
    required: ["budgetId"],
    additionalProperties: false,
  };
  const encryptionHeadersSchema = {
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
  const recordSchema = {
    type: "object",
    properties: {
      type: {
        type: "string",
        enum: [
          "account",
          "account-group",
          "category",
          "category-group",
          "payee",
          "transaction",
          "budget-month",
        ],
      },
      id: { type: "string" },
      revision: { type: "string", pattern: "^[a-f0-9]{64}$" },
      body: { type: "object", additionalProperties: true },
    },
    required: ["type", "id", "revision", "body"],
    additionalProperties: false,
  };
  const displaySettingsSchema = {
    type: "object",
    properties: {
      budgetType: { type: "string", enum: ["envelope", "tracking"] },
      currencyCode: { type: "string", pattern: "^[A-Z]{3}$" },
      currencySymbol: { type: "string" },
      currencyFractionDigits: { type: "integer", minimum: 0 },
      numberFormat: {
        anyOf: [
          {
            type: "string",
            enum: [
              "comma-dot",
              "dot-comma",
              "space-comma",
              "apostrophe-dot",
              "comma-dot-in",
            ],
          },
          { type: "null" },
        ],
      },
      hideFraction: { type: "boolean" },
      currencySymbolPosition: {
        anyOf: [
          { type: "string", enum: ["before", "after"] },
          { type: "null" },
        ],
      },
      currencySpaceBetweenAmountAndSymbol: { type: "boolean" },
    },
    required: [
      "budgetType",
      "currencyCode",
      "currencySymbol",
      "currencyFractionDigits",
      "numberFormat",
      "hideFraction",
      "currencySymbolPosition",
      "currencySpaceBetweenAmountAndSymbol",
    ],
    additionalProperties: false,
  };
  const snapshotSchema = {
    type: "object",
    properties: {
      budgetId: { type: "string" },
      generation: { type: "string", format: "uuid" },
      cursor: { type: "string", pattern: "^(0|[1-9][0-9]*)$" },
      records: { type: "array", items: recordSchema },
      displaySettings: {
        anyOf: [displaySettingsSchema, { type: "null" }],
      },
    },
    required: [
      "budgetId",
      "generation",
      "cursor",
      "records",
      "displaySettings",
    ],
    additionalProperties: false,
  };
  const snapshotPageSchema = {
    type: "object",
    properties: {
      budgetId: { type: "string" },
      generation: { type: "string", format: "uuid" },
      cursor: { type: "string", pattern: "^(0|[1-9][0-9]*)$" },
      records: { type: "array", items: recordSchema },
      hasMore: { type: "boolean" },
      resetRequired: { type: "boolean" },
      nextAfterType: {
        anyOf: [
          { type: "string", enum: BUDGET_ENTITY_TYPES },
          { type: "null" },
        ],
      },
      nextAfterId: { type: ["string", "null"] },
      displaySettings: {
        anyOf: [displaySettingsSchema, { type: "null" }],
      },
    },
    required: [
      "budgetId",
      "generation",
      "cursor",
      "records",
      "hasMore",
      "resetRequired",
      "nextAfterType",
      "nextAfterId",
      "displaySettings",
    ],
    additionalProperties: false,
  };
  const changeSchema = {
    type: "object",
    properties: {
      cursor: { type: "string", pattern: "^[1-9][0-9]*$" },
      type: recordSchema.properties.type,
      id: { type: "string" },
      operation: { type: "string", enum: ["upsert", "delete"] },
      revision: { type: "string", pattern: "^[a-f0-9]{64}$" },
      body: {
        anyOf: [
          { type: "object", additionalProperties: true },
          { type: "null" },
        ],
      },
    },
    required: ["cursor", "type", "id", "operation", "revision", "body"],
    additionalProperties: false,
  };
  const changePageSchema = {
    type: "object",
    properties: {
      budgetId: { type: "string" },
      generation: { type: "string", format: "uuid" },
      currentCursor: { type: "string", pattern: "^(0|[1-9][0-9]*)$" },
      throughCursor: { type: "string", pattern: "^(0|[1-9][0-9]*)$" },
      nextCursor: { type: "string", pattern: "^(0|[1-9][0-9]*)$" },
      hasMore: { type: "boolean" },
      resetRequired: { type: "boolean" },
      changes: { type: "array", items: changeSchema },
      displaySettings: {
        anyOf: [displaySettingsSchema, { type: "null" }],
      },
    },
    required: [
      "budgetId",
      "generation",
      "currentCursor",
      "throughCursor",
      "nextCursor",
      "hasMore",
      "resetRequired",
      "changes",
      "displaySettings",
    ],
    additionalProperties: false,
  };

  app.get(
    "/api/v1/budgets",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "listBudgets",
        summary: "List Actual budgets available to the authenticated device",
        security: [{ bearerAuth: [] }],
        response: {
          200: {
            type: "object",
            properties: {
              budgets: {
                type: "array",
                items: {
                  type: "object",
                  properties: {
                    budgetId: { type: "string" },
                    name: { type: "string" },
                    isEncrypted: { type: "boolean" },
                  },
                  required: ["budgetId", "name", "isEncrypted"],
                  additionalProperties: false,
                },
              },
            },
            required: ["budgets"],
            additionalProperties: false,
          },
          401: errorSchema,
          502: errorSchema,
          503: errorSchema,
        },
      },
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      const listBudgets = budgetReadApi?.listBudgets;
      if (!listBudgets) return actualNotConfigured(reply);

      try {
        const budgets = await listBudgets();
        const seenBudgetIds = new Set<string>();
        const authorizedBudgets = budgets.filter((budget) => {
          if (
            !DeviceAuthorization.canAccessBudget(device, budget.budgetId) ||
            seenBudgetIds.has(budget.budgetId)
          ) {
            return false;
          }
          seenBudgetIds.add(budget.budgetId);
          return true;
        });
        reply.header("cache-control", "no-store");
        return reply.code(200).send({ budgets: authorizedBudgets });
      } catch (error) {
        reportTransactionFailure("Budget catalog read failed", error);
        return budgetReadFailed(reply);
      }
    },
  );

  app.get<{
    Params: BudgetRouteParams;
    Headers: BudgetReadHeaders;
  }>(
    "/api/v1/budgets/:budgetId/snapshot",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "getBudgetSnapshot",
        summary: "Refresh and read the complete budget projection",
        security: [{ bearerAuth: [] }],
        params: budgetParamsSchema,
        headers: encryptionHeadersSchema,
        response: {
          200: snapshotSchema,
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
      if (request.validationError) return invalidBudgetRequest(reply);
      if (
        !DeviceAuthorization.canAccessBudget(device, request.params.budgetId)
      ) {
        return transactionNotFound(reply);
      }
      if (!budgetReadApi) return actualNotConfigured(reply);

      try {
        const snapshot = await budgetReadApi.getSnapshot(
          request.params.budgetId,
          request.headers["x-budget-encryption-password"],
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(snapshot);
      } catch (error) {
        if (error instanceof BudgetProjectionInputError) {
          return invalidBudgetRequest(reply);
        }
        reportTransactionFailure("Budget snapshot read failed", error);
        return budgetReadFailed(reply);
      }
    },
  );

  app.get<{
    Params: BudgetRouteParams;
    Querystring: BudgetSnapshotPageQuery;
    Headers: BudgetReadHeaders;
  }>(
    "/api/v1/budgets/:budgetId/snapshot-pages",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "getBudgetSnapshotPage",
        summary: "Refresh and read a bounded page of the budget projection",
        security: [{ bearerAuth: [] }],
        params: budgetParamsSchema,
        headers: encryptionHeadersSchema,
        querystring: {
          type: "object",
          properties: {
            generation: { type: "string", format: "uuid" },
            cursor: { type: "string", pattern: "^(0|[1-9][0-9]*)$" },
            afterType: { type: "string", enum: BUDGET_ENTITY_TYPES },
            afterId: { type: "string", minLength: 1, maxLength: 500 },
            limit: { type: "integer", minimum: 1, maximum: 2_000 },
          },
          additionalProperties: false,
        },
        response: {
          200: snapshotPageSchema,
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
      if (request.validationError) return invalidBudgetRequest(reply);
      if (
        !DeviceAuthorization.canAccessBudget(device, request.params.budgetId)
      ) {
        return transactionNotFound(reply);
      }
      if (!budgetReadApi) return actualNotConfigured(reply);

      const query = request.query;
      if (
        (query.generation === undefined) !== (query.cursor === undefined) ||
        (query.afterType === undefined) !== (query.afterId === undefined) ||
        (query.afterType !== undefined && query.generation === undefined)
      ) {
        return invalidBudgetRequest(reply);
      }
      try {
        const page = await budgetReadApi.getSnapshotPage({
          budgetId: request.params.budgetId,
          ...(query.generation === undefined
            ? {}
            : { generation: query.generation }),
          ...(query.cursor === undefined ? {} : { cursor: query.cursor }),
          ...(query.afterType === undefined
            ? {}
            : { afterType: query.afterType }),
          ...(query.afterId === undefined ? {} : { afterId: query.afterId }),
          limit: query.limit ?? 2_000,
          ...(request.headers["x-budget-encryption-password"] === undefined
            ? {}
            : {
                encryptionPassword:
                  request.headers["x-budget-encryption-password"],
              }),
        });
        reply.header("cache-control", "no-store");
        return reply.code(200).send(page);
      } catch (error) {
        if (error instanceof BudgetProjectionInputError) {
          return invalidBudgetRequest(reply);
        }
        reportTransactionFailure("Budget snapshot page read failed", error);
        return budgetReadFailed(reply);
      }
    },
  );

  app.get<{
    Params: BudgetRouteParams;
    Querystring: BudgetChangeQuery;
    Headers: BudgetReadHeaders;
  }>(
    "/api/v1/budgets/:budgetId/changes",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "getBudgetChanges",
        summary: "Read a bounded, generation-aware budget change page",
        security: [{ bearerAuth: [] }],
        params: budgetParamsSchema,
        headers: encryptionHeadersSchema,
        querystring: {
          type: "object",
          properties: {
            generation: { type: "string", minLength: 36, maxLength: 36 },
            cursor: { type: "string", pattern: "^(0|[1-9][0-9]*)$" },
            throughCursor: {
              type: "string",
              pattern: "^(0|[1-9][0-9]*)$",
            },
            limit: { type: "integer", minimum: 1, maximum: 500 },
          },
          required: ["generation", "cursor"],
          additionalProperties: false,
        },
        response: {
          200: changePageSchema,
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
      if (request.validationError) return invalidBudgetRequest(reply);
      if (
        !DeviceAuthorization.canAccessBudget(device, request.params.budgetId)
      ) {
        return transactionNotFound(reply);
      }
      if (!budgetReadApi) return actualNotConfigured(reply);

      try {
        const page = await budgetReadApi.getChanges({
          budgetId: request.params.budgetId,
          generation: request.query.generation,
          cursor: request.query.cursor,
          ...(request.query.throughCursor === undefined
            ? {}
            : { throughCursor: request.query.throughCursor }),
          limit: request.query.limit ?? 100,
          ...(request.headers["x-budget-encryption-password"] === undefined
            ? {}
            : {
                encryptionPassword:
                  request.headers["x-budget-encryption-password"],
              }),
        });
        reply.header("cache-control", "no-store");
        return reply.code(200).send(page);
      } catch (error) {
        if (error instanceof BudgetProjectionInputError) {
          return invalidBudgetRequest(reply);
        }
        reportTransactionFailure("Budget change feed read failed", error);
        return budgetReadFailed(reply);
      }
    },
  );
}

function invalidBudgetRequest(reply: import("fastify").FastifyReply) {
  return reply.code(400).send({
    error: {
      code: "invalid_request",
      message: "Budget read request is invalid",
    },
  });
}

function budgetReadFailed(reply: import("fastify").FastifyReply) {
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
    case "category-not-found":
      return "Category does not exist in this budget";
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

function signInResponse(device: {
  deviceId: string;
  principalId: string;
  name: string;
  budgetIds: string[];
  accessToken: string;
  createdAt: Date;
}) {
  return {
    deviceId: device.deviceId,
    principalId: device.principalId,
    name: device.name,
    budgetIds: device.budgetIds,
    accessToken: device.accessToken,
    tokenType: "Bearer",
    createdAt: device.createdAt.toISOString(),
  };
}

function signInResponseSchema() {
  return {
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
