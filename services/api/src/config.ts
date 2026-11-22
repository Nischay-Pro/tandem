export type Environment = Record<string, string | undefined>;

export interface ExtensionConfig {
  host: string;
  port: number;
  databaseUrl: string;
  actualServerUrl: string;
}

export class ConfigError extends Error {}

/** Reads the extension service's settings; it needs only Postgres and the Actual server it vouches for. */
export function parseConfig(environment: Environment): ExtensionConfig {
  const databaseUrl = environment.DATABASE_URL?.trim();
  if (!databaseUrl) throw new ConfigError("DATABASE_URL is required.");
  const actualServerUrl = environment.ACTUAL_SERVER_URL?.trim();
  if (!actualServerUrl) throw new ConfigError("ACTUAL_SERVER_URL is required.");
  let origin: URL;
  try {
    origin = new URL(actualServerUrl);
  } catch {
    throw new ConfigError("ACTUAL_SERVER_URL must be a URL.");
  }
  if (origin.protocol !== "https:" && origin.protocol !== "http:") {
    throw new ConfigError("ACTUAL_SERVER_URL must use http or https.");
  }
  const port = Number(environment.PORT ?? "19753");
  if (!Number.isInteger(port) || port < 1 || port > 65_535) {
    throw new ConfigError("PORT must be a whole number from 1 to 65535.");
  }
  return {
    host: environment.HOST?.trim() || "0.0.0.0",
    port,
    databaseUrl,
    actualServerUrl: origin.toString().replace(/\/$/, ""),
  };
}
