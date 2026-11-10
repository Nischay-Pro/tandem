import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { parseApiConfig as parseRawApiConfig } from "../src/config.js";

const databaseUrl = "postgresql://tandem:local-test@localhost:5432/tandem";
const testAuthTokenEncryptionKey = "synthetic-session-token-encryption-key-32";

function parseApiConfig(environment: Record<string, string | undefined>) {
  return parseRawApiConfig({
    AUTH_TOKEN_ENCRYPTION_KEY: testAuthTokenEncryptionKey,
    ...environment,
  });
}

function withSecretFiles<T>(
  contents: Record<string, string>,
  run: (paths: Record<string, string>) => T,
): T {
  const directory = mkdtempSync(join(tmpdir(), "tandem-config-secrets-"));
  try {
    const paths = Object.fromEntries(
      Object.entries(contents).map(([name, content]) => {
        const path = join(directory, name.toLowerCase());
        writeFileSync(path, content);
        return [name, path];
      }),
    );
    return run(paths);
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
}

describe("parseApiConfig", () => {
  it("treats empty optional Actual settings as unconfigured", () => {
    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        ACTUAL_SERVER_URL: "  ",
      }).actual,
    ).toBeUndefined();
  });

  it("uses safe development defaults", () => {
    expect(parseApiConfig({ DATABASE_URL: databaseUrl })).toEqual({
      databaseUrl,
      authTokenEncryptionKey: testAuthTokenEncryptionKey,
      host: "0.0.0.0",
      port: 19753,
      trustProxy: false,
      changeFeedEpoch: "default",
    });
  });

  it("keeps Actual integration disabled when its URL is omitted", () => {
    expect(
      parseApiConfig({ DATABASE_URL: databaseUrl }).actual,
    ).toBeUndefined();
    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        ACTUAL_API_DATA_DIR: "/srv/actual-api",
      }).actual,
    ).toBeUndefined();
  });

  it("parses an Actual URL and persistent API workspace without shared credentials", () => {
    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        ACTUAL_SERVER_URL: " https://budget.example.com/ ",
        ACTUAL_API_DATA_DIR: " /srv/actual-api ",
      }).actual,
    ).toEqual({
      serverURL: "https://budget.example.com",
      dataDir: "/srv/actual-api",
    });
    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        ACTUAL_SERVER_URL: "http://actual:5006",
      }).actual?.dataDir,
    ).toBe("/data/actual-api");
  });

  it("accepts an Actual session token instead of a server password", () => {
    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        ACTUAL_SERVER_URL: "https://budget.example.com/",
        ACTUAL_SESSION_TOKEN: "browser-session-token",
      }).actual,
    ).toEqual({
      serverURL: "https://budget.example.com",
      sessionToken: "browser-session-token",
      dataDir: "/data/actual-api",
    });
  });

  it("reads Actual session tokens from a mounted secret file", () => {
    withSecretFiles(
      { ACTUAL_SESSION_TOKEN: "browser-session-token\n" },
      (paths) => {
        expect(
          parseApiConfig({
            DATABASE_URL: databaseUrl,
            ACTUAL_SERVER_URL: "https://budget.example.com",
            ACTUAL_SESSION_TOKEN_FILE: paths.ACTUAL_SESSION_TOKEN,
          }).actual,
        ).toEqual({
          serverURL: "https://budget.example.com",
          sessionToken: "browser-session-token",
          dataDir: "/data/actual-api",
        });
      },
    );
  });

  it("rejects a shared Actual server password", () => {
    expect(() =>
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        ACTUAL_SERVER_URL: "https://budget.example.com",
        ACTUAL_SERVER_PASSWORD: "server-password",
        ACTUAL_SESSION_TOKEN: "browser-session-token",
      }),
    ).toThrow("ACTUAL_SERVER_PASSWORD is not supported");
  });

  it("requires an Actual server URL for an operator-managed session token", () => {
    expect(() =>
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        ACTUAL_SESSION_TOKEN: "browser-session-token",
      }),
    ).toThrow(
      "ACTUAL_SERVER_URL is required when configuring an Actual session token",
    );
  });

  it.each([
    "not-a-url",
    "ftp://actual.example.com",
    "https://user:secret@actual.example.com",
  ])("rejects unsafe Actual server URL %s", (serverUrl) => {
    expect(() =>
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        ACTUAL_SERVER_URL: serverUrl,
      }),
    ).toThrow("ACTUAL_SERVER_URL must be a valid HTTP(S) URL");
  });

  it("rejects a relative Actual API workspace path", () => {
    expect(() =>
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        ACTUAL_SERVER_URL: "http://actual:5006",
        ACTUAL_API_DATA_DIR: "actual-api",
      }),
    ).toThrow("ACTUAL_API_DATA_DIR must be an absolute path");
  });

  it.each([
    "not-a-url",
    "ftp://tandem.example",
    "https://user@tandem.example",
    "https://:secret@tandem.example",
  ])("rejects an unsafe public callback URL %s", (publicURL) => {
    expect(() =>
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        ACTUAL_SERVER_URL: "https://budget.example.com",
        ACTUAL_PUBLIC_URL: publicURL,
      }),
    ).toThrow("ACTUAL_PUBLIC_URL must be a valid HTTP(S) URL");
  });

  it("normalizes an explicit HTTPS public callback URL", () => {
    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        ACTUAL_SERVER_URL: "https://budget.example.com",
        ACTUAL_PUBLIC_URL: "https://tandem.example/",
      }).actual?.publicURL,
    ).toBe("https://tandem.example");
  });

  it("accepts explicit host and port", () => {
    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        HOST: "127.0.0.1",
        PORT: "8090",
      }),
    ).toEqual({
      databaseUrl,
      authTokenEncryptionKey: testAuthTokenEncryptionKey,
      host: "127.0.0.1",
      port: 8090,
      trustProxy: false,
      changeFeedEpoch: "default",
    });
  });

  it("accepts only explicitly trusted proxy addresses and CIDRs", () => {
    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        TRUST_PROXY: " 172.30.0.1, 10.0.0.0/24,2001:db8::/64,172.30.0.1 ",
      }).trustProxy,
    ).toEqual(["172.30.0.1", "10.0.0.0/24", "2001:db8::/64"]);
  });

  it.each([
    ",127.0.0.1",
    "127.0.0.1,",
    "127.0.0.1,,10.0.0.1",
    "true",
    "not-an-ip",
    "192.0.2.1/not-a-prefix",
    "192.0.2.1/33",
    "0.0.0.0/0",
    "192.0.2.1/24/8",
  ])("rejects an empty proxy entry in %s", (proxies) => {
    expect(() =>
      parseApiConfig({ DATABASE_URL: databaseUrl, TRUST_PROXY: proxies }),
    ).toThrow("TRUST_PROXY must be a comma-separated list");
  });

  it("uses the default host when the supplied host is blank", () => {
    expect(parseApiConfig({ DATABASE_URL: databaseUrl, HOST: "  " }).host).toBe(
      "0.0.0.0",
    );
  });

  it.each(["", "  "])(
    "uses the default port when supplied port is blank",
    (port) => {
      expect(
        parseApiConfig({ DATABASE_URL: databaseUrl, PORT: port }).port,
      ).toBe(19753);
    },
  );

  it("disables proxy trust when the environment value is blank", () => {
    expect(
      parseApiConfig({ DATABASE_URL: databaseUrl, TRUST_PROXY: "  " })
        .trustProxy,
    ).toBe(false);
  });

  it("accepts a bounded change-feed epoch and rejects unsafe values", () => {
    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        CHANGE_FEED_EPOCH: " restore-2026-11 ",
      }).changeFeedEpoch,
    ).toBe("restore-2026-11");
    expect(() =>
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        CHANGE_FEED_EPOCH: "bad epoch",
      }),
    ).toThrow("CHANGE_FEED_EPOCH must contain 1 to 100 safe characters");
    expect(() =>
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        CHANGE_FEED_EPOCH: "x".repeat(101),
      }),
    ).toThrow("CHANGE_FEED_EPOCH must contain 1 to 100 safe characters");
  });

  it("rejects a missing database URL", () => {
    expect(() => parseApiConfig({})).toThrow("DATABASE_URL is required");
  });

  it("requires an independent auth-token encryption key with at least 32 bytes", () => {
    expect(() => parseRawApiConfig({ DATABASE_URL: databaseUrl })).toThrow(
      "AUTH_TOKEN_ENCRYPTION_KEY is required",
    );
    expect(() =>
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        AUTH_TOKEN_ENCRYPTION_KEY: "x".repeat(31),
      }),
    ).toThrow("AUTH_TOKEN_ENCRYPTION_KEY must contain at least 32 bytes");
    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        AUTH_TOKEN_ENCRYPTION_KEY: "x".repeat(32),
      }).authTokenEncryptionKey,
    ).toBe("x".repeat(32));
  });

  it("reads the database URL and auth encryption key from secret files", () => {
    withSecretFiles(
      {
        DATABASE_URL: `${databaseUrl}\n`,
        AUTH_TOKEN_ENCRYPTION_KEY: `${testAuthTokenEncryptionKey}\n`,
      },
      (paths) => {
        expect(
          parseApiConfig({
            DATABASE_URL: undefined,
            DATABASE_URL_FILE: paths.DATABASE_URL,
            AUTH_TOKEN_ENCRYPTION_KEY: undefined,
            AUTH_TOKEN_ENCRYPTION_KEY_FILE: paths.AUTH_TOKEN_ENCRYPTION_KEY,
          }),
        ).toMatchObject({
          databaseUrl,
          authTokenEncryptionKey: testAuthTokenEncryptionKey,
        });
      },
    );
  });

  it("rejects the removed shared Actual password secret", () => {
    withSecretFiles(
      { ACTUAL_SERVER_PASSWORD: " server secret \n" },
      (paths) => {
        expect(() =>
          parseApiConfig({
            DATABASE_URL: databaseUrl,
            ACTUAL_SERVER_URL: "https://budget.example.com",
            ACTUAL_SERVER_PASSWORD_FILE: paths.ACTUAL_SERVER_PASSWORD,
          }),
        ).toThrow("ACTUAL_SERVER_PASSWORD is not supported");
      },
    );
  });

  it("rejects supplying a secret both directly and through a file", () => {
    withSecretFiles({ DATABASE_URL: databaseUrl }, (paths) => {
      expect(() =>
        parseApiConfig({
          DATABASE_URL: databaseUrl,
          DATABASE_URL_FILE: paths.DATABASE_URL,
        }),
      ).toThrow("DATABASE_URL and DATABASE_URL_FILE cannot both be set");
    });
  });

  it("requires secret-file paths to be absolute and readable", () => {
    expect(() =>
      parseApiConfig({ DATABASE_URL: undefined, DATABASE_URL_FILE: "secret" }),
    ).toThrow("DATABASE_URL_FILE must be an absolute path");
    expect(() =>
      parseApiConfig({
        DATABASE_URL: undefined,
        DATABASE_URL_FILE: join(tmpdir(), `missing-${process.pid}-secret`),
      }),
    ).toThrow("DATABASE_URL_FILE could not be read");
    expect(() =>
      parseApiConfig({ DATABASE_URL: undefined, DATABASE_URL_FILE: "  " }),
    ).toThrow("DATABASE_URL_FILE must not be empty");
  });

  it("rejects malformed database URLs", () => {
    expect(() => parseApiConfig({ DATABASE_URL: "not-a-url" })).toThrow(
      "DATABASE_URL must be a valid PostgreSQL URL",
    );
  });

  it("rejects non-PostgreSQL URLs", () => {
    expect(() =>
      parseApiConfig({ DATABASE_URL: "https://example.com" }),
    ).toThrow("DATABASE_URL must use postgres:// or postgresql://");
  });

  it.each(["0", "65536", "1.5", "nope"])("rejects invalid port %s", (port) => {
    expect(() =>
      parseApiConfig({ DATABASE_URL: databaseUrl, PORT: port }),
    ).toThrow("PORT must be an integer between 1 and 65535");
  });
});
