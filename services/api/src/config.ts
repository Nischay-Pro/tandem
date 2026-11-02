import { isAbsolute } from "node:path";
import { isIP } from "node:net";
import type { ActualConnectionConfig } from "./actual/actual-session.js";
import { OperatorMessageError } from "./operator-message-error.js";

export interface ApiConfig {
  databaseUrl: string;
  host: string;
  port: number;
  trustProxy: false | string[];
  changeFeedEpoch: string;
  actual?: Extract<ActualConnectionConfig, { password: string }>;
}

export type Environment = Record<string, string | undefined>;

export const READINESS_TIMEOUT_MS = 2_000;

export function parseApiConfig(environment: Environment): ApiConfig {
  const databaseUrl = environment.DATABASE_URL?.trim();
  if (!databaseUrl) {
    throw new OperatorMessageError("DATABASE_URL is required");
  }

  let parsedDatabaseUrl: URL;
  try {
    parsedDatabaseUrl = new URL(databaseUrl);
  } catch {
    throw new OperatorMessageError(
      "DATABASE_URL must be a valid PostgreSQL URL",
    );
  }

  if (
    parsedDatabaseUrl.protocol !== "postgres:" &&
    parsedDatabaseUrl.protocol !== "postgresql:"
  ) {
    throw new OperatorMessageError(
      "DATABASE_URL must use postgres:// or postgresql://",
    );
  }

  const host = environment.HOST?.trim() || "0.0.0.0";
  const rawPort = environment.PORT?.trim() || "19753";
  const port = Number(rawPort);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new OperatorMessageError(
      "PORT must be an integer between 1 and 65535",
    );
  }

  const trustedProxyAddresses = parseTrustedProxyAddresses(
    environment.TRUST_PROXY,
  );
  const changeFeedEpoch = environment.CHANGE_FEED_EPOCH?.trim() || "default";
  if (!/^[A-Za-z0-9._:-]{1,100}$/.test(changeFeedEpoch)) {
    throw new OperatorMessageError(
      "CHANGE_FEED_EPOCH must contain 1 to 100 safe characters",
    );
  }
  const actual = parseActualConnection(environment);

  return {
    databaseUrl,
    host,
    port,
    trustProxy: trustedProxyAddresses,
    changeFeedEpoch,
    ...(actual === undefined ? {} : { actual }),
  };
}

function parseActualConnection(
  environment: Environment,
): Extract<ActualConnectionConfig, { password: string }> | undefined {
  const serverUrl = environment.ACTUAL_SERVER_URL?.trim();
  const password = environment.ACTUAL_SERVER_PASSWORD;
  const configuredDataDirectory = environment.ACTUAL_API_DATA_DIR?.trim();

  if (
    serverUrl === undefined &&
    (password === undefined || password.length === 0)
  ) {
    return undefined;
  }
  if (
    serverUrl === undefined ||
    password === undefined ||
    password.length === 0
  ) {
    throw new OperatorMessageError(
      "ACTUAL_SERVER_URL and a non-empty ACTUAL_SERVER_PASSWORD must be configured together",
    );
  }

  let parsedServerUrl: URL;
  try {
    parsedServerUrl = new URL(serverUrl);
  } catch {
    throw new OperatorMessageError(
      "ACTUAL_SERVER_URL must be a valid HTTP(S) URL",
    );
  }
  if (
    (parsedServerUrl.protocol !== "http:" &&
      parsedServerUrl.protocol !== "https:") ||
    parsedServerUrl.username.length > 0 ||
    parsedServerUrl.password.length > 0
  ) {
    throw new OperatorMessageError(
      "ACTUAL_SERVER_URL must be a valid HTTP(S) URL",
    );
  }

  const dataDir = configuredDataDirectory || "/data/actual-api";
  if (!isAbsolute(dataDir)) {
    throw new OperatorMessageError(
      "ACTUAL_API_DATA_DIR must be an absolute path",
    );
  }

  return {
    serverURL: parsedServerUrl.toString().replace(/\/$/, ""),
    password,
    dataDir,
  };
}

function parseTrustedProxyAddresses(
  value: string | undefined,
): false | string[] {
  const normalized = value?.trim();
  if (!normalized) {
    return false;
  }

  const addresses = normalized.split(",").map((address) => address.trim());
  if (addresses.some((address) => !isIpOrCidr(address))) {
    throw new OperatorMessageError(
      "TRUST_PROXY must be a comma-separated list of proxy IP addresses or CIDRs",
    );
  }
  return [...new Set(addresses)];
}

function isIpOrCidr(value: string): boolean {
  const [address, prefix, extra] = value.split("/");
  if (!address || extra !== undefined) {
    return false;
  }
  const version = isIP(address);
  if (version === 0) {
    return false;
  }
  if (prefix === undefined) {
    return true;
  }
  if (!/^(0|[1-9]\d*)$/.test(prefix)) {
    return false;
  }
  const prefixLength = Number(prefix);
  return prefixLength > 0 && prefixLength <= (version === 4 ? 32 : 128);
}
