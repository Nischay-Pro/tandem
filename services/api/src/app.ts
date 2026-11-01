import swagger from "@fastify/swagger";
import Fastify, { type FastifyInstance, type FastifySchema } from "fastify";
import { READINESS_TIMEOUT_MS } from "./config.js";
import type { HealthDatabase } from "./database.js";
import {
  DeviceAuthInputError,
  type DeviceAuthorization,
} from "./auth/device-auth.js";
import { PairingRateLimiter } from "./auth/pairing-rate-limiter.js";

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
      tags: [{ name: "Health" }, { name: "Authentication" }],
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
        summary: "List devices paired to the authenticated principal",
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
      const devices = await deviceAuth.listDevices(device.principalId);
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
        summary: "Revoke a paired device credential",
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
      const revoked = await deviceAuth.revokeDevice(
        device.principalId,
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
