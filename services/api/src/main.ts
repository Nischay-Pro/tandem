import type { FastifyInstance } from "fastify";
import { createPostgresDatabase, type Database } from "./database.js";
import { parseApiConfig, type Environment } from "./config.js";
import { startApiServer } from "./server.js";
import type { SignalRegistrar } from "./server.js";

export async function runApi(
  environment: Environment,
  createDatabase: (connectionString: string) => Database,
  registerSignalHandler: SignalRegistrar,
) {
  const config = parseApiConfig(environment);
  const database = createDatabase(config.databaseUrl);
  return startApiServer(config, database, registerSignalHandler);
}

export function runProductionApi(
  environment: Environment,
  signals: NodeJS.Process,
): Promise<FastifyInstance> {
  return runApi(environment, createPostgresDatabase, (signal, handler) => {
    signals.once(signal, handler);
  });
}
