import swagger from "@fastify/swagger";
import { randomUUID } from "node:crypto";
import { performance } from "node:perf_hooks";
import Fastify, {
  LogController,
  type FastifyInstance,
  type FastifyRequest,
  type FastifyServerOptions,
} from "fastify";
import { READINESS_TIMEOUT_MS } from "./config.js";
import type { HealthDatabase } from "./database.js";
import {
  ActualPrincipalSessionExpiredError,
  ACTUAL_PRINCIPAL_SESSION_EXPIRED_MESSAGE,
} from "./actual/actual-session.js";
import { DeviceAuthorization } from "./auth/device-auth.js";
import { registerDeviceAuthorizationRoutes } from "./auth/device-authorization-routes.js";
import { registerActualLoginMethodsRoute } from "./auth/actual-login-method-routes.js";
import type { ActualPasswordGrant } from "./auth/actual-password-login.js";
import type { ActualOpenIdLogin } from "./auth/actual-openid-login.js";
import { runAsActualPrincipal } from "./auth/actual-principal-context.js";
import type { ActualLoginMethods } from "./auth/actual-login-methods.js";
import { logSecurityAuditEvent } from "./security-audit.js";
import type { TransactionApiPort } from "./actual/transaction-api.js";
import type { BudgetMaintenanceApiPort } from "./actual/budget-maintenance-api.js";
import type { BudgetReadApiPort } from "./budget-projection/budget-read-api.js";
import {
  registerBudgetMaintenanceRoutes,
  type BudgetMaintenanceJournal,
} from "./actual/budget-maintenance-routes.js";
import { registerBudgetReadRoutes } from "./budget-projection/budget-read-routes.js";
import { registerHealthRoutes } from "./health/health-routes.js";
import { registerOpenApiRoute } from "./openapi/openapi-route.js";
import { registerTransactionRoutes } from "./actual/transaction-routes.js";
import {
  API_ERROR_SCHEMA,
  buildOpenApiLocalReference,
} from "./openapi/error-responses.js";
import {
  isStorageFullError,
  SERVER_STORAGE_FULL_ERROR,
} from "./storage-failure.js";

declare module "fastify" {
  interface FastifySchema {
    "x-tandem-rate-limited"?: boolean;
  }
}

export function safeErrorType(error: unknown): string {
  if (!(error instanceof Error)) return error === null ? "null" : typeof error;
  const name = error.constructor.name;
  return /^[A-Za-z][A-Za-z0-9_$]{0,63}$/.test(name) ? name : "Error";
}

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
  registerHealthRoutes(app, {
    database,
    readinessTimeoutMs,
    ...(options.actualReadiness === undefined
      ? {}
      : { actualReadiness: options.actualReadiness }),
  });
  registerOpenApiRoute(app);
  registerActualLoginMethodsRoute(app, {
    ...(options.readActualLoginMethods === undefined
      ? {}
      : { readActualLoginMethods: options.readActualLoginMethods }),
  });

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
    registerTransactionRoutes(app, {
      deviceAuth: options.deviceAuth,
      ...(options.transactionApi === undefined
        ? {}
        : { transactionApi: options.transactionApi }),
      requireDevice,
      invalidTransactionRequest,
      transactionNotFound,
      actualNotConfigured,
      reportTransactionFailure,
      actualRequestFailed,
    });
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
