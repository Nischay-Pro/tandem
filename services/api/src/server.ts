import type { FastifyInstance } from "fastify";
import { buildApiApp } from "./app.js";
import type { ApiConfig } from "./config.js";
import type { ServerDatabase } from "./database.js";

export type ShutdownSignal = "SIGINT" | "SIGTERM";
export type SignalRegistrar = (
  signal: ShutdownSignal,
  handler: () => Promise<void>,
) => void;

export async function startApiServer(
  config: ApiConfig,
  database: ServerDatabase,
  registerSignalHandler: SignalRegistrar,
): Promise<FastifyInstance> {
  let app: FastifyInstance | undefined;

  try {
    await database.migrate();
    app = await buildApiApp(database);
    await app.listen({ host: config.host, port: config.port });
  } catch (error) {
    try {
      if (app) {
        await app.close();
      } else {
        await database.close();
      }
    } catch (closeError) {
      throw new AggregateError(
        [error, closeError],
        "API startup failed and dependencies did not close cleanly",
        { cause: closeError },
      );
    }
    throw error;
  }

  const runningApp = app as FastifyInstance;
  const shutdown = () => runningApp.close();
  registerSignalHandler("SIGTERM", shutdown);
  registerSignalHandler("SIGINT", shutdown);

  return runningApp;
}
