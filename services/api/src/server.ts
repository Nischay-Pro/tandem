import type { FastifyInstance } from "fastify";
import { buildApiApp } from "./app.js";
import type { ApiConfig } from "./config.js";
import type { ServerDatabase } from "./database.js";
import type { TransactionApiPort } from "./actual/transaction-api.js";
import { WriteLeaseReaper } from "./write-journal/lease-reaper.js";

export type ShutdownSignal = "SIGINT" | "SIGTERM";
export type SignalRegistrar = (
  signal: ShutdownSignal,
  handler: () => Promise<void>,
) => void;

export interface ApiRuntime {
  transactionApi?: TransactionApiPort;
  start?(): Promise<void>;
  close?(): Promise<void>;
}

export async function startApiServer(
  config: ApiConfig,
  database: ServerDatabase,
  registerSignalHandler: SignalRegistrar,
  runtime: ApiRuntime = {},
): Promise<FastifyInstance> {
  let app: FastifyInstance | undefined;
  const leaseReaper = new WriteLeaseReaper(database.journal);

  try {
    await database.migrate();
    await leaseReaper.start();
    await runtime.start?.();
    app = await buildApiApp(database, {
      onClose: async () => {
        try {
          await runtime.close?.();
        } finally {
          await leaseReaper.stop();
        }
      },
      deviceAuth: database.deviceAuth,
      ...(runtime.transactionApi === undefined
        ? {}
        : { transactionApi: runtime.transactionApi }),
      trustProxy: config.trustProxy,
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
        await runtime.close?.();
      } catch (closeError) {
        cleanupErrors.push(closeError);
      }
      try {
        await database.close();
      } catch (closeError) {
        cleanupErrors.push(closeError);
      }
    }
    for (const cleanupError of cleanupErrors) {
      reportCleanupFailure(cleanupError);
    }
    throw error;
  }

  const runningApp = app as FastifyInstance;
  const shutdown = () => runningApp.close();
  registerSignalHandler("SIGTERM", shutdown);
  registerSignalHandler("SIGINT", shutdown);

  return runningApp;
}

function reportCleanupFailure(error: unknown): void {
  try {
    console.error("API startup cleanup failed", {
      errorType: error instanceof Error ? error.name : typeof error,
    });
  } catch {
    // Cleanup diagnostics must not replace the startup failure.
  }
}
