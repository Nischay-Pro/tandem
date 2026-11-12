import type { FastifyInstance, FastifyRequest, FastifySchema } from "fastify";
import {
  DeviceAuthorization,
  DeviceAuthInputError,
  type DeviceRecord,
} from "./device-auth.js";
import {
  ActualPasswordRejectedError,
  ActualPasswordUnavailableError,
  type ActualPasswordGrant,
} from "./actual-password-login.js";
import {
  openIdBrowserCookieName,
  type ActualOpenIdLogin,
} from "./actual-openid-login.js";
import { RequestRateLimiter } from "./request-rate-limiter.js";
import { logSecurityAuditEvent } from "../security-audit.js";
import {
  isStorageFullError,
  SERVER_STORAGE_FULL_ERROR,
} from "../storage-failure.js";
import { API_ERROR_SCHEMA_REF } from "../openapi/error-responses.js";

const errorSchema = API_ERROR_SCHEMA_REF;

export interface DeviceAuthorizationRouteDependencies {
  deviceAuth: DeviceAuthorization;
  trustProxy: false | string[];
  signInWithActualPassword?: (password: string) => Promise<ActualPasswordGrant>;
  persistActualSessionToken?: (
    principalId: string,
    sessionToken: string,
  ) => Promise<void>;
  actualOpenIdLogin?: ActualOpenIdLogin;
  requireDevice: (
    deviceAuth: DeviceAuthorization,
    request: FastifyRequest,
    reply: import("fastify").FastifyReply,
  ) => Promise<DeviceRecord | undefined>;
}

export function registerDeviceAuthorizationRoutes(
  app: FastifyInstance,
  dependencies: DeviceAuthorizationRouteDependencies,
): void {
  const {
    deviceAuth,
    trustProxy,
    signInWithActualPassword,
    persistActualSessionToken,
    actualOpenIdLogin,
    requireDevice,
  } = dependencies;
  const rateLimiter = new RequestRateLimiter(10, 60_000);
  let warnedAboutIgnoredForwardedFor = false;
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
    "x-tandem-rate-limited": true,
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
      500: errorSchema,
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
        if (isStorageFullError(error)) {
          return reply.code(503).send({ error: SERVER_STORAGE_FULL_ERROR });
        }
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
        logSecurityAuditEvent({
          event: "device.sign_in",
          outcome: "failed",
          reason: "internal_error",
          clientAddress: request.ip,
        });
        return reply.code(500).send({
          error: {
            code: "internal_error",
            message: "Could not complete sign-in. Try again shortly.",
          },
        });
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
    app.post<{
      Body: {
        deviceName: string;
        codeChallenge: string;
        callbackState: string;
      };
    }>(
      "/api/v1/auth/openid/start",
      {
        schema: {
          tags: ["Authentication"],
          operationId: "startActualOpenIdSignIn",
          summary: "Start Actual OpenID sign-in",
          "x-tandem-rate-limited": true,
          body: {
            type: "object",
            properties: {
              deviceName: {
                type: "string",
                minLength: 1,
                maxLength: 100,
                pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
              },
              codeChallenge: {
                type: "string",
                minLength: 43,
                maxLength: 43,
                pattern: "^[A-Za-z0-9_-]{43}$",
                description:
                  "Unpadded base64url SHA-256 challenge for the app-generated PKCE verifier.",
              },
              callbackState: {
                type: "string",
                minLength: 43,
                maxLength: 43,
                pattern: "^[A-Za-z0-9_-]{43}$",
                description:
                  "High-entropy state used to match the browser callback to this pending app login.",
              },
            },
            required: ["deviceName", "codeChallenge", "callbackState"],
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
          return await actualOpenIdLogin.start(
            request.body.deviceName,
            request.body.codeChallenge,
            request.body.callbackState,
          );
        } catch (error) {
          if (isStorageFullError(error)) {
            return reply.code(503).send({ error: SERVER_STORAGE_FULL_ERROR });
          }
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
    }>(
      "/api/v1/auth/openid/browser-start/:state",
      {
        schema: {
          tags: ["Authentication"],
          operationId: "startActualOpenIdBrowserHandoff",
          summary: "Start the browser-bound Actual OpenID handoff",
          params: {
            type: "object",
            properties: {
              state: {
                type: "string",
                minLength: 43,
                maxLength: 43,
                pattern: "^[A-Za-z0-9_-]{43}$",
              },
            },
            required: ["state"],
            additionalProperties: false,
          },
          response: {
            302: {
              type: "null",
              description:
                "Sets a short-lived browser cookie and redirects to Actual sign-in.",
            },
            401: errorSchema,
          },
        },
      },
      async (request, reply) => {
        reply.header("cache-control", "no-store");
        reply.header("referrer-policy", "no-referrer");
        const cookieName = openIdBrowserCookieName(request.params.state);
        const existingCookieValue = readCookieValue(
          request.headers.cookie,
          cookieName,
        );
        const handoff = actualOpenIdLogin.startBrowserHandoff(
          request.params.state,
          existingCookieValue,
        );
        if (!handoff) {
          return reply.code(401).send({
            error: {
              code: "sign_in_expired",
              message: "Sign-in expired. Start again.",
            },
          });
        }
        return reply
          .code(302)
          .header("set-cookie", handoff.setCookie)
          .header("location", handoff.redirectUrl)
          .send();
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
            302: {
              type: "null",
              description:
                "Redirects to the mobile app with a one-use code and the state for the pending sign-in.",
            },
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
          const browserCookie = readCookieValue(
            request.headers.cookie,
            openIdBrowserCookieName(request.params.state),
          );
          const returnUrl = await actualOpenIdLogin.completeCallback(
            request.params.state,
            request.query.token,
            browserCookie,
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

    app.post<{ Body: { code: string; codeVerifier: string } }>(
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
              codeVerifier: {
                type: "string",
                minLength: 43,
                maxLength: 128,
                pattern: "^[A-Za-z0-9._~-]{43,128}$",
                description:
                  "The PKCE verifier whose S256 challenge was sent to the start endpoint.",
              },
            },
            required: ["code", "codeVerifier"],
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
        const completion = actualOpenIdLogin.consume(
          request.body.code,
          request.body.codeVerifier,
        );
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
        } catch (error) {
          if (isStorageFullError(error)) {
            return reply.code(503).send({ error: SERVER_STORAGE_FULL_ERROR });
          }
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

function readCookieValue(
  cookieHeader: string | string[] | undefined,
  cookieName: string,
): string | undefined {
  if (typeof cookieHeader !== "string") return undefined;
  const matches = cookieHeader
    .split(";")
    .map((part) => part.trim())
    .filter((part) => {
      const separatorIndex = part.indexOf("=");
      return (
        separatorIndex >= 0 &&
        part.slice(0, separatorIndex).trim() === cookieName
      );
    });
  if (matches.length !== 1) return undefined;
  const matchingCookie = matches[0]!;
  const separatorIndex = matchingCookie.indexOf("=");
  return matchingCookie.slice(separatorIndex + 1).trim();
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
