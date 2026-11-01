import { isIP } from "node:net";

export interface ApiConfig {
  databaseUrl: string;
  host: string;
  port: number;
  trustProxy: false | string[];
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

  const trustedProxyAddresses = parseTrustedProxyAddresses(
    environment.TRUST_PROXY,
  );

  return {
    databaseUrl,
    host,
    port,
    trustProxy: trustedProxyAddresses,
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
    throw new Error(
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
