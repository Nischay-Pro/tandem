import { ActualBudgetAccess } from "./actual-access.js";
import { buildApp } from "./app.js";
import { type Environment, parseConfig } from "./config.js";
import { PostgresExtensionStore } from "./store.js";
import pg from "pg";

export type SignalRegistrar = (
  signal: "SIGINT" | "SIGTERM",
  handler: () => void,
) => void;

/** Starts the extension service and closes it cleanly on SIGINT or SIGTERM. */
export const createPool = (url: string) =>
  new pg.Pool({ connectionString: url });

export async function run(
  environment: Environment,
  onSignal: SignalRegistrar,
  poolFor = createPool,
) {
  const config = parseConfig(environment);
  const store = new PostgresExtensionStore(poolFor(config.databaseUrl));
  await store.ensureSchema();
  const app = await buildApp({
    store,
    access: new ActualBudgetAccess(config.actualServerUrl),
    actualServerUrl: config.actualServerUrl,
    trustProxy: config.trustProxy,
  });
  await app.listen({ host: config.host, port: config.port });
  const stop = () => {
    void app.close().then(() => store.close());
  };
  onSignal("SIGINT", stop);
  onSignal("SIGTERM", stop);
  return app;
}
