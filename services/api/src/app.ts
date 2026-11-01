import swagger from "@fastify/swagger";
import Fastify, { type FastifyInstance, type FastifySchema } from "fastify";
import type { HealthDatabase } from "./database.js";

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

export async function buildApiApp(
  database: HealthDatabase,
): Promise<FastifyInstance> {
  const app = Fastify({ logger: false });

  await app.register(swagger, {
    openapi: {
      openapi: "3.1.0",
      info: {
        title: "Actual Budget Companion API",
        version: "0.1.0",
        description:
          "Unofficial API for the Actual Budget Companion ecosystem.",
      },
      tags: [{ name: "Health" }],
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
        await database.ping();
        return reply.code(200).send({ status: "ok" });
      } catch {
        return reply.code(503).send({ status: "not_ready" });
      }
    },
  );

  app.get("/api/v1/openapi.json", async () => app.swagger());

  app.addHook("onClose", async () => database.close());
  await app.ready();
  return app;
}
