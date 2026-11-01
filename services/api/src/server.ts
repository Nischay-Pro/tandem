import type { FastifyInstance } from "fastify";
import { buildApiApp } from "./app.js";
import type { ApiConfig } from "./config.js";
import type { Database } from "./database.js";

export type ShutdownSignal = "SIGINT" | "SIGTERM";
export type SignalRegistrar = (
  signal: ShutdownSignal,
  handler: () => Promise<void>,
) => void;

export async function startApiServer(
  config: ApiConfig,
  database: Database,
  registerSignalHandler: SignalRegistrar,
): Promise<FastifyInstance> {
  const app = await buildApiApp(database);

  try {
    await app.listen({ host: config.host, port: config.port });
  } catch (error) {
    await app.close();
    throw error;
  }

  const shutdown = () => app.close();
  registerSignalHandler("SIGTERM", shutdown);
  registerSignalHandler("SIGINT", shutdown);

  return app;
}
