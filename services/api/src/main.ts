import type { FastifyInstance } from "fastify";
import * as actualApi from "@actual-app/api";
import { createHash } from "node:crypto";
import { mkdir } from "node:fs/promises";
import { join } from "node:path";
import { createPostgresDatabase, type Database } from "./database.js";
import { parseApiConfig, type ApiConfig, type Environment } from "./config.js";
import {
  startApiServer,
  startupExitCodeFor,
  type ApiRuntime,
} from "./server.js";
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
import { ActualLoginMethodsReader } from "./auth/actual-login-methods.js";
import { ActualPasswordLogin } from "./auth/actual-password-login.js";
import { ActualOpenIdLogin } from "./auth/actual-openid-login.js";
import { runAsActualPrincipal } from "./auth/actual-principal-context.js";

export async function runApi(
  environment: Environment,
  createDatabase: (
    connectionString: string,
    authTokenEncryptionKey: string,
  ) => Database,
  registerSignalHandler: SignalRegistrar,
) {
  const config = parseApiConfig(environment);
  const database = createDatabase(
    config.databaseUrl,
    config.authTokenEncryptionKey,
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
  const session = new ActualSession(
    actualApi,
    actualConfig,
    undefined,
    undefined,
    undefined,
    {
      sessionToken: async (principalId) =>
        (await database.actualSessionVault?.read(principalId))?.sessionToken,
      dataDir: async (principalId) => {
        const accountStorage = join(
          actualConfig.dataDir,
          "accounts",
          createHash("sha256").update(principalId, "utf8").digest("hex"),
        );
        await mkdir(accountStorage, { recursive: true });
        return accountStorage;
      },
    },
  );
  const loginMethods = new ActualLoginMethodsReader(actualConfig.serverURL);
  const passwordLogin = new ActualPasswordLogin(actualConfig.serverURL);
  const transactions = createActualTransactionStore();
  const budgetCatalogReader = new ActualBudgetCatalogReader(session);
  let startupSessionRequired = Boolean(actualConfig.sessionToken);
  const openIdLogin = actualConfig.publicURL
    ? new ActualOpenIdLogin(
        passwordLogin,
        actualConfig.publicURL,
        async (grant) => {
          await database.actualSessionVault?.save({
            principalId: grant.principalId,
            sessionToken: grant.sessionToken,
          });
          return runAsActualPrincipal(grant.principalId, async () => {
            const budgets = await budgetCatalogReader.read();
            return {
              ...grant,
              budgetIds: budgets.map((budget) => budget.budgetId),
            };
          });
        },
      )
    : undefined;
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
    actualReadiness: () => ({
      ready: !startupSessionRequired || session.isReady,
      ...(startupSessionRequired && session.readinessMessage !== undefined
        ? { message: session.readinessMessage }
        : {}),
    }),
    readActualLoginMethods: async () => {
      const methods = await loginMethods.read();
      if (actualConfig.publicURL) return methods;
      const availableLoginMethods = methods.availableLoginMethods.filter(
        (method) => method.method !== "openid",
      );
      return {
        ...methods,
        loginMethod:
          methods.loginMethod === "openid"
            ? availableLoginMethods.some(
                (method) => method.method === "password" && method.active,
              )
              ? "password"
              : "unsupported"
            : methods.loginMethod,
        availableLoginMethods,
      };
    },
    signInWithActualPassword: async (password) => {
      const grant = await passwordLogin.signIn(password);
      await database.actualSessionVault?.save({
        principalId: grant.principalId,
        sessionToken: grant.sessionToken,
      });
      return runAsActualPrincipal(grant.principalId, async () => {
        const budgets = await budgetCatalogReader.read();
        return {
          ...grant,
          budgetIds: budgets.map((budget) => budget.budgetId),
        };
      });
    },
    ...(openIdLogin === undefined ? {} : { actualOpenIdLogin: openIdLogin }),
    persistActualSessionToken: async (principalId, sessionToken) => {
      await database.actualSessionVault?.save({ principalId, sessionToken });
    },
    start: async () => {
      startupSessionRequired = Boolean(actualConfig.sessionToken);
      await session.start();
    },
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
  }).catch((error: unknown) => {
    signals.exitCode = productionStartupExitCode(error);
    throw error;
  });
}

export function productionStartupExitCode(error: unknown): number {
  if (error instanceof OperatorMessageError) return 2;
  return startupExitCodeFor(error) ?? 1;
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
