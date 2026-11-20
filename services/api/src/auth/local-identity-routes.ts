import type { FastifyInstance, FastifySchema } from "fastify";
import { isIP } from "node:net";
import type { DeviceAuthorization } from "./device-auth.js";
import { RequestRateLimiter } from "./request-rate-limiter.js";
import { API_ERROR_SCHEMA_REF } from "../openapi/error-responses.js";

const schema: FastifySchema = {
  tags: ["Authentication"],
  operationId: "proveLocalIdentity",
  summary: "Prove this server's identity to a device on a private network",
  "x-tandem-rate-limited": true,
  body: {
    type: "object",
    properties: {
      deviceId: { type: "string", minLength: 1, maxLength: 64 },
      nonce: { type: "string", pattern: "^[A-Za-z0-9_-]{43}$" },
    },
    required: ["deviceId", "nonce"],
    additionalProperties: false,
  },
  response: {
    200: {
      type: "object",
      properties: { proof: { type: "string" } },
      required: ["proof"],
      additionalProperties: false,
    },
    400: API_ERROR_SCHEMA_REF,
    404: API_ERROR_SCHEMA_REF,
    429: API_ERROR_SCHEMA_REF,
  },
};

export function registerLocalIdentityRoutes(
  app: FastifyInstance,
  deviceAuth: DeviceAuthorization,
): void {
  const rateLimiter = new RequestRateLimiter(30, 60_000);
  app.post<{ Body: { deviceId: string; nonce: string } }>(
    "/api/v1/local-identity",
    { schema, attachValidation: true },
    async (request, reply) => {
      reply.header("cache-control", "no-store");
      if (!isPrivateNetworkAddress(request.ip)) {
        return reply.code(404).send({
          error: {
            code: "route_not_found",
            message: "Tandem API route was not found.",
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
              message: "Wait a moment, then try again.",
            },
          });
      }
      if (request.validationError) {
        return reply.code(400).send({
          error: {
            code: "invalid_request",
            message: "Check the request details and try again.",
          },
        });
      }
      return {
        proof: await deviceAuth.localIdentityProof(
          request.body.deviceId,
          request.body.nonce,
        ),
      };
    },
  );
}

export function isPrivateNetworkAddress(address: string): boolean {
  const value = address.toLowerCase().replace(/^::ffff:/u, "");
  if (isIP(value) === 4) {
    const [a, b] = value.split(".").map(Number) as [number, number];
    return (
      a === 10 ||
      a === 127 ||
      (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 168) ||
      (a === 100 && b >= 64 && b <= 127)
    );
  }
  if (isIP(value) === 6) {
    return (
      value === "::1" ||
      /^f[cd][0-9a-f]{2}:/u.test(value) ||
      /^fe[89ab][0-9a-f]:/u.test(value)
    );
  }
  return false;
}
