import type { FastifyInstance } from "fastify";
import { buildApiApp } from "./app.js";
import type { ApiConfig } from "./config.js";
import type { ServerDatabase } from "./database.js";
import type { TransactionApiPort } from "./actual/transaction-api.js";
import type { BudgetMaintenanceApiPort } from "./actual/budget-maintenance-api.js";
import type { BudgetReadApiPort } from "./budget-projection/budget-read-api.js";
import type { ActualPasswordGrant } from "./auth/actual-password-login.js";
import type { ActualOpenIdLogin } from "./auth/actual-openid-login.js";
import { WriteLeaseReaper } from "./write-journal/lease-reaper.js";

export type ShutdownSignal = "SIGINT" | "SIGTERM";
export type SignalRegistrar = (
  signal: ShutdownSignal,
  handler: () => Promise<void>,
) => void;

const startupExitCodes = new WeakMap<object, number>();

export function startupExitCodeFor(error: unknown): number | undefined {
  return typeof error === "object" && error !== null
    ? startupExitCodes.get(error)
    : undefined;
}

export interface ApiRuntime {
  transactionApi?: TransactionApiPort;
  budgetMaintenanceApi?: BudgetMaintenanceApiPort;
  budgetReadApi?: BudgetReadApiPort;
  start?(): Promise<void>;
  actualReadiness?(): { ready: boolean; message?: string };
  readActualLoginMethods?(): Promise<
    import("./auth/actual-login-methods.js").ActualLoginMethods
  >;
  signInWithActualPassword?(password: string): Promise<ActualPasswordGrant>;
  actualOpenIdLogin?: ActualOpenIdLogin;
  persistActualSessionToken?(
    principalId: string,
    sessionToken: string,
  ): Promise<void>;
  close?(): Promise<void>;
}

export async function startApiServer(
  config: ApiConfig,
  database: ServerDatabase,
  registerSignalHandler: SignalRegistrar,
  runtime: ApiRuntime = {},
): Promise<FastifyInstance> {
  let app: FastifyInstance | undefined;
  let startupPhase: "database" | "actual" | "api" = "database";
  const leaseReaper = new WriteLeaseReaper(database.journal);

  try {
    await database.migrate();
    await leaseReaper.start();
    startupPhase = "actual";
    await runtime.start?.();
    startupPhase = "api";
    app = await buildApiApp(database, {
      onClose: async () => {
        try {
          await runtime.close?.();
        } finally {
          await leaseReaper.stop();
        }
      },
      deviceAuth: database.deviceAuth,
      ...(runtime.actualReadiness === undefined
        ? {}
        : { actualReadiness: runtime.actualReadiness }),
      ...(runtime.readActualLoginMethods === undefined
        ? {}
        : { readActualLoginMethods: runtime.readActualLoginMethods }),
      ...(runtime.signInWithActualPassword === undefined
        ? {}
        : { signInWithActualPassword: runtime.signInWithActualPassword }),
      ...(runtime.actualOpenIdLogin === undefined
        ? {}
        : { actualOpenIdLogin: runtime.actualOpenIdLogin }),
      ...(runtime.persistActualSessionToken === undefined
        ? {}
        : { persistActualSessionToken: runtime.persistActualSessionToken }),
      ...(runtime.transactionApi === undefined
        ? {}
        : { transactionApi: runtime.transactionApi }),
      ...(runtime.budgetMaintenanceApi === undefined
        ? {}
        : { budgetMaintenanceApi: runtime.budgetMaintenanceApi }),
      ...(database.maintenanceJournal === undefined
        ? {}
        : { maintenanceJournal: database.maintenanceJournal }),
      ...(runtime.budgetReadApi === undefined
        ? {}
        : { budgetReadApi: runtime.budgetReadApi }),
      trustProxy: config.trustProxy,
      logger: true,
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
    if (typeof error === "object" && error !== null) {
      if (startupPhase === "database") startupExitCodes.set(error, 3);
      if (startupPhase === "actual") startupExitCodes.set(error, 4);
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
