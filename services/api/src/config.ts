export interface ApiConfig {
  databaseUrl: string;
  host: string;
  port: number;
}

export type Environment = Record<string, string | undefined>;

export const READINESS_TIMEOUT_MS = 2_000;

export function parseApiConfig(environment: Environment): ApiConfig {
  const databaseUrl = environment.DATABASE_URL?.trim();
  if (!databaseUrl) {
    throw new Error("DATABASE_URL is required");
  }

  let parsedDatabaseUrl: URL;
  try {
    parsedDatabaseUrl = new URL(databaseUrl);
  } catch {
    throw new Error("DATABASE_URL must be a valid PostgreSQL URL");
  }

  if (
    parsedDatabaseUrl.protocol !== "postgres:" &&
    parsedDatabaseUrl.protocol !== "postgresql:"
  ) {
    throw new Error("DATABASE_URL must use postgres:// or postgresql://");
  }

  const host = environment.HOST?.trim() || "0.0.0.0";
  const rawPort = environment.PORT?.trim() || "19753";
  const port = Number(rawPort);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error("PORT must be an integer between 1 and 65535");
  }

  return { databaseUrl, host, port };
}
