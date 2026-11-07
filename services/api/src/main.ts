import type { FastifyInstance } from "fastify";
import * as actualApi from "@actual-app/api";
import { createPostgresDatabase, type Database } from "./database.js";
import { parseApiConfig, type ApiConfig, type Environment } from "./config.js";
import { startApiServer, type ApiRuntime } from "./server.js";
import type { SignalRegistrar } from "./server.js";
import { ActualSession } from "./actual/actual-session.js";
import { createTransactionApi } from "./actual/transaction-api.js";
import { createBudgetMaintenanceApi } from "./actual/budget-maintenance-api.js";
import { TransactionReconciler } from "./actual/transaction-reconciler.js";
import { createActualTransactionStore } from "./actual/transaction-store.js";
import { ActualBudgetSnapshotReader } from "./actual/budget-snapshot-reader.js";
import { ActualBudgetCatalogReader } from "./actual/budget-catalog-reader.js";
import { BudgetReadApi } from "./budget-projection/budget-read-api.js";
import { OperatorMessageError } from "./operator-message-error.js";

export async function runApi(
  environment: Environment,
  createDatabase: (
    connectionString: string,
    pairingCodeHashKey: string,
  ) => Database,
  registerSignalHandler: SignalRegistrar,
) {
  const config = parseApiConfig(environment);
  const database = createDatabase(
    config.databaseUrl,
    config.pairingCodeHashKey,
  );
  const runtime = createActualRuntime(
    config.actual,
    database,
    config.changeFeedEpoch,
  );
  return startApiServer(config, database, registerSignalHandler, runtime);
}

function createActualRuntime(
  actualConfig: ApiConfig["actual"],
  database: Database,
  changeFeedEpoch: string,
): ApiRuntime {
  if (!actualConfig) {
    return {};
  }
  const session = new ActualSession(actualApi, actualConfig);
  const transactions = createActualTransactionStore();
  const budgetCatalogReader = new ActualBudgetCatalogReader(session);
  const budgetReadApi = new BudgetReadApi(
    database,
    new ActualBudgetSnapshotReader(session),
    database.budgetProjection,
    changeFeedEpoch,
    () => budgetCatalogReader.read(),
  );
  const reconciler = new TransactionReconciler(
    database,
    database.journal,
    session,
    transactions,
  );
  return {
    transactionApi: createTransactionApi(
      session,
      transactions,
      reconciler,
      database.journal,
    ),
    budgetMaintenanceApi: createBudgetMaintenanceApi(database, session),
    budgetReadApi,
    start: () => session.start(),
    close: () => session.close(),
  };
}

export function runProductionApi(
  environment: Environment,
  signals: NodeJS.Process,
): Promise<FastifyInstance> {
  return runApi(environment, createPostgresDatabase, (signal, handler) => {
    signals.once(signal, async () => {
      try {
        await handler();
        signals.exitCode ??= 0;
      } catch (error) {
        reportProcessFailure("API shutdown failed", error);
        signals.exitCode = 1;
      }
    });
  });
}

export function reportProcessFailure(
  context: "API startup failed" | "API shutdown failed",
  error: unknown,
): void {
  try {
    if (error instanceof OperatorMessageError) {
      console.error(context, { message: error.message });
    } else {
      console.error(context, {
        errorType: error instanceof Error ? error.name : typeof error,
      });
    }
  } catch {
    // Process failure reporting must not create another unhandled rejection.
  }
}
