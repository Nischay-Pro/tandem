import { Buffer } from "node:buffer";
import { readFileSync } from "node:fs";
import { isIP } from "node:net";
import { isAbsolute } from "node:path";
import type { ActualConnectionConfig } from "./actual/actual-session.js";
import { OperatorMessageError } from "./operator-message-error.js";

export interface ApiConfig {
  databaseUrl: string;
  pairingCodeHashKey: string;
  host: string;
  port: number;
  trustProxy: false | string[];
  changeFeedEpoch: string;
  actual?: ActualConnectionConfig;
}

export type Environment = Record<string, string | undefined>;

export const READINESS_TIMEOUT_MS = 2_000;

export function parseApiConfig(environment: Environment): ApiConfig {
  const databaseUrl = readSecret(environment, "DATABASE_URL")?.trim();
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

  const pairingCodeHashKey = readSecret(
    environment,
    "PAIRING_CODE_HASH_KEY",
  )?.trim();
  if (!pairingCodeHashKey) {
    throw new OperatorMessageError("PAIRING_CODE_HASH_KEY is required");
  }
  if (Buffer.byteLength(pairingCodeHashKey, "utf8") < 32) {
    throw new OperatorMessageError(
      "PAIRING_CODE_HASH_KEY must contain at least 32 bytes",
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
    pairingCodeHashKey,
    host,
    port,
    trustProxy: trustedProxyAddresses,
    changeFeedEpoch,
    ...(actual === undefined ? {} : { actual }),
  };
}

function parseActualConnection(
  environment: Environment,
): ActualConnectionConfig | undefined {
  const serverUrl = environment.ACTUAL_SERVER_URL?.trim() || undefined;
  const password = readSecret(environment, "ACTUAL_SERVER_PASSWORD");
  const sessionToken = readSecret(environment, "ACTUAL_SESSION_TOKEN");
  const configuredDataDirectory = environment.ACTUAL_API_DATA_DIR?.trim();

  if (password && sessionToken) {
    throw new OperatorMessageError(
      "Configure either ACTUAL_SERVER_PASSWORD or ACTUAL_SESSION_TOKEN, not both",
    );
  }
  if (!serverUrl && !password && !sessionToken) {
    return undefined;
  }
  if (!serverUrl || (!password && !sessionToken)) {
    throw new OperatorMessageError(
      "ACTUAL_SERVER_URL and exactly one non-empty Actual credential must be configured together",
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
    ...(password === undefined
      ? { sessionToken: sessionToken! }
      : { password }),
    dataDir,
  };
}

function readSecret(
  environment: Environment,
  name:
    | "DATABASE_URL"
    | "PAIRING_CODE_HASH_KEY"
    | "ACTUAL_SERVER_PASSWORD"
    | "ACTUAL_SESSION_TOKEN",
): string | undefined {
  const directValue = environment[name];
  const fileVariable = `${name}_FILE`;
  const fileValue = environment[fileVariable];

  if (fileValue === undefined) {
    return directValue;
  }
  if (directValue !== undefined) {
    throw new OperatorMessageError(
      `${name} and ${fileVariable} cannot both be set`,
    );
  }

  const filePath = fileValue.trim();
  if (!filePath) {
    throw new OperatorMessageError(`${fileVariable} must not be empty`);
  }
  if (!isAbsolute(filePath)) {
    throw new OperatorMessageError(`${fileVariable} must be an absolute path`);
  }

  try {
    return readFileSync(filePath, "utf8").replace(/\r?\n$/, "");
  } catch {
    throw new OperatorMessageError(`${fileVariable} could not be read`);
  }
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
