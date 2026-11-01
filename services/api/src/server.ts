import type { FastifyInstance } from "fastify";
import { buildApiApp } from "./app.js";
import type { ApiConfig } from "./config.js";
import type { ServerDatabase } from "./database.js";
import { WriteLeaseReaper } from "./write-journal/lease-reaper.js";

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
  const leaseReaper = new WriteLeaseReaper(database.journal);

  try {
    await database.migrate();
    await leaseReaper.start();
    app = await buildApiApp(database, {
      onClose: () => leaseReaper.stop(),
      deviceAuth: database.deviceAuth,
    });
    await app.listen({ host: config.host, port: config.port });
  } catch (error) {
    const cleanupErrors: unknown[] = [];
    if (app) {
      try {
        await app.close();
      } catch (closeError) {
        cleanupErrors.push(closeError);
      }
    } else {
      await leaseReaper.stop();
      try {
        await database.close();
      } catch (closeError) {
        cleanupErrors.push(closeError);
      }
    }
    if (cleanupErrors.length > 0) {
      throw new AggregateError(
        [error, ...cleanupErrors],
        "API startup failed and dependencies did not close cleanly",
        { cause: error },
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
