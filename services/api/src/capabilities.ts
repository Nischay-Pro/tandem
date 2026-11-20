import type { FastifyInstance, FastifySchema } from "fastify";
import { SUPPORTED_ACTUAL_SERVER_VERSION } from "./actual/actual-session.js";

export const TANDEM_API_VERSION = 1;
export const MINIMUM_CLIENT_API_VERSION = 1;

export const SERVER_FEATURES = [
  "budget-order-merge",
  "budget-password-codes",
  "budget-password-attempt-limit",
  "hold-total",
  "local-identity",
] as const;

const schema: FastifySchema = {
  tags: ["Health"],
  operationId: "getServerCapabilities",
  summary: "List the API version and features this Tandem server supports",
  response: {
    200: {
      type: "object",
      properties: {
        apiVersion: { type: "integer" },
        minimumClientApiVersion: { type: "integer" },
        features: { type: "array", items: { type: "string" } },
        actualVersion: { type: "string" },
      },
      required: [
        "apiVersion",
        "minimumClientApiVersion",
        "features",
        "actualVersion",
      ],
      additionalProperties: false,
    },
  },
};

export function registerCapabilitiesRoute(app: FastifyInstance): void {
  app.get("/api/v1/capabilities", { schema }, async (_request, reply) => {
    reply.header("cache-control", "no-store");
    return {
      apiVersion: TANDEM_API_VERSION,
      minimumClientApiVersion: MINIMUM_CLIENT_API_VERSION,
      features: [...SERVER_FEATURES],
      actualVersion: SUPPORTED_ACTUAL_SERVER_VERSION,
    };
  });
}
