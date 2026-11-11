import { readFileSync } from "node:fs";
import { isIP } from "node:net";
import { isAbsolute } from "node:path";
import type { ActualSessionConfig } from "./actual/actual-session.js";
import { OperatorMessageError } from "./operator-message-error.js";

export interface ApiConfig {
  databaseUrl: string;
  authTokenEncryptionKey: string;
  authTokenEncryptionKeyPrevious?: string;
  host: string;
  port: number;
  trustProxy: false | string[];
  changeFeedEpoch: string;
  actual?: ActualSessionConfig;
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

  const authTokenEncryptionKey = readSecret(
    environment,
    "AUTH_TOKEN_ENCRYPTION_KEY",
  )?.trim();
  if (!authTokenEncryptionKey) {
    throw new OperatorMessageError("AUTH_TOKEN_ENCRYPTION_KEY is required");
  }
  if (!/^[a-fA-F0-9]{64}$/.test(authTokenEncryptionKey)) {
    throw new OperatorMessageError(
      "AUTH_TOKEN_ENCRYPTION_KEY must be 64 hexadecimal characters encoding 32 bytes",
    );
  }
  if (hasObviousLowEntropyHexPattern(authTokenEncryptionKey)) {
    throw new OperatorMessageError(
      "AUTH_TOKEN_ENCRYPTION_KEY must not contain obvious low-entropy patterns; generate a random 32-byte value",
    );
  }
  const previousEncryptionKey =
    readSecret(environment, "AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS")?.trim() ||
    undefined;
  if (previousEncryptionKey !== undefined) {
    validateEncryptionKey(
      previousEncryptionKey,
      "AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS",
    );
  }
  const authTokenEncryptionKeyPrevious =
    previousEncryptionKey === authTokenEncryptionKey
      ? undefined
      : previousEncryptionKey;

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
    authTokenEncryptionKey,
    ...(authTokenEncryptionKeyPrevious === undefined
      ? {}
      : { authTokenEncryptionKeyPrevious }),
    host,
    port,
    trustProxy: trustedProxyAddresses,
    changeFeedEpoch,
    ...(actual === undefined ? {} : { actual }),
  };
}

function hasObviousLowEntropyHexPattern(value: string): boolean {
  const normalized = value.toLowerCase();
  if (new Set(normalized).size < 8) return true;
  for (let patternLength = 1; patternLength <= 16; patternLength *= 2) {
    const pattern = normalized.slice(0, patternLength);
    if (pattern.repeat(64 / patternLength) === normalized) return true;
  }
  return false;
}

function validateEncryptionKey(
  value: string,
  name: "AUTH_TOKEN_ENCRYPTION_KEY" | "AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS",
): void {
  if (!/^[a-fA-F0-9]{64}$/.test(value)) {
    throw new OperatorMessageError(
      `${name} must be 64 hexadecimal characters encoding 32 bytes`,
    );
  }
  if (hasObviousLowEntropyHexPattern(value)) {
    throw new OperatorMessageError(
      `${name} must not contain obvious low-entropy patterns; generate a random 32-byte value`,
    );
  }
}

function parseActualConnection(
  environment: Environment,
): ActualSessionConfig | undefined {
  const serverUrl = environment.ACTUAL_SERVER_URL?.trim() || undefined;
  const password = readSecret(environment, "ACTUAL_SERVER_PASSWORD");
  if (password !== undefined) {
    throw new OperatorMessageError(
      "ACTUAL_SERVER_PASSWORD is not supported; sign in from Tandem or configure an operator-managed ACTUAL_SESSION_TOKEN",
    );
  }
  const sessionToken = readSecret(environment, "ACTUAL_SESSION_TOKEN");
  const configuredDataDirectory = environment.ACTUAL_API_DATA_DIR?.trim();

  if (!serverUrl && !sessionToken) {
    return undefined;
  }
  if (!serverUrl) {
    throw new OperatorMessageError(
      "ACTUAL_SERVER_URL is required when configuring an Actual session token",
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

  const publicURL = environment.ACTUAL_PUBLIC_URL?.trim();
  if (publicURL) {
    let parsedPublicURL: URL;
    try {
      parsedPublicURL = new URL(publicURL);
    } catch {
      throw new OperatorMessageError(
        "ACTUAL_PUBLIC_URL must be a valid HTTP(S) URL",
      );
    }
    if (
      (parsedPublicURL.protocol !== "http:" &&
        parsedPublicURL.protocol !== "https:") ||
      parsedPublicURL.username.length > 0 ||
      parsedPublicURL.password.length > 0
    ) {
      throw new OperatorMessageError(
        "ACTUAL_PUBLIC_URL must be a valid HTTP(S) URL",
      );
    }
  }

  return {
    serverURL: parsedServerUrl.toString().replace(/\/$/, ""),
    ...(sessionToken === undefined ? {} : { sessionToken }),
    dataDir,
    ...(publicURL === undefined
      ? {}
      : { publicURL: new URL(publicURL).toString().replace(/\/$/, "") }),
  };
}

function readSecret(
  environment: Environment,
  name:
    | "DATABASE_URL"
    | "AUTH_TOKEN_ENCRYPTION_KEY"
    | "AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS"
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
