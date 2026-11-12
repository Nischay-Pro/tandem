import type { FastifyInstance, FastifySchema } from "fastify";
import type { HealthDatabase } from "../database.js";
import { API_ERROR_SCHEMA_REF } from "../openapi/error-responses.js";
import {
  isStorageFullError,
  SERVER_STORAGE_FULL_ERROR,
} from "../storage-failure.js";

const statusOk = {
  type: "object",
  properties: { status: { type: "string", const: "ok" } },
  required: ["status"],
  additionalProperties: false,
};

const errorSchema = API_ERROR_SCHEMA_REF;

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

export interface HealthRouteOptions {
  database: HealthDatabase;
  readinessTimeoutMs: number;
  actualReadiness?: () => {
    ready: boolean;
    code?: string;
    message?: string;
  };
}

export function registerHealthRoutes(
  app: FastifyInstance,
  options: HealthRouteOptions,
): void {
  app.get<{ Reply: { status: "ok" } }>(
    "/api/v1/health/live",
    { schema: liveSchema },
    async () => ({ status: "ok" }),
  );

  app.get<{
    Reply: { status: "ok" } | { error: { code: string; message: string } };
  }>(
    "/api/v1/health/ready",
    { schema: readySchema },
    async (_request, reply) => {
      try {
        await pingWithTimeout(
          () => options.database.ping(),
          options.readinessTimeoutMs,
        );
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
