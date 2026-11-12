import type { FastifyInstance } from "fastify";
import type { ActualLoginMethods } from "./actual-login-methods.js";
import { ActualLoginMethodsCache } from "./actual-login-methods-cache.js";
import { RequestRateLimiter } from "./request-rate-limiter.js";
import { API_ERROR_SCHEMA_REF } from "../openapi/error-responses.js";

const errorSchema = API_ERROR_SCHEMA_REF;

export function registerActualLoginMethodsRoute(
  app: FastifyInstance,
  options: {
    readActualLoginMethods?: () => Promise<ActualLoginMethods>;
  },
): void {
  const cache = new ActualLoginMethodsCache(45_000);
  const rateLimiter = new RequestRateLimiter(60, 60_000);

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
      const limit = rateLimiter.reserveRequest(request.ip);
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
        return await cache.read(options.readActualLoginMethods);
      } catch {
        return reply.code(503).send(actualLoginMethodsUnavailable());
      }
    },
  );
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
