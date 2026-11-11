import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { parseApiConfig as parseRawApiConfig } from "../src/config.js";

const databaseUrl = "postgresql://tandem:local-test@localhost:5432/tandem";
const testAuthTokenEncryptionKey =
  "5323b1ab617c73bf862318e07e388d79d4f902170d607b5be38e6a6f66e2eb3c";

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

  it("treats a blank Actual public URL as unset when password login is configured", () => {
    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        ACTUAL_SERVER_URL: "http://actual:5006",
        ACTUAL_PUBLIC_URL: "  ",
      }).actual,
    ).toEqual({
      serverURL: "http://actual:5006",
      dataDir: "/data/actual-api",
    });
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

  it("treats a blank mounted Actual session token file as unset", () => {
    withSecretFiles({ ACTUAL_SESSION_TOKEN: "" }, (paths) => {
      expect(
        parseApiConfig({
          DATABASE_URL: databaseUrl,
          ACTUAL_SERVER_URL: "https://budget.example.com",
          ACTUAL_SESSION_TOKEN_FILE: paths.ACTUAL_SESSION_TOKEN,
        }).actual,
      ).toEqual({
        serverURL: "https://budget.example.com",
        dataDir: "/data/actual-api",
      });
    });
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

  it("requires an independent 32-byte auth-token encryption key encoded as hex", () => {
    expect(() => parseRawApiConfig({ DATABASE_URL: databaseUrl })).toThrow(
      "AUTH_TOKEN_ENCRYPTION_KEY is required",
    );
    for (const invalidKey of [
      "x".repeat(31),
      "x".repeat(32),
      "g".repeat(64),
      "a".repeat(63),
    ]) {
      expect(() =>
        parseApiConfig({
          DATABASE_URL: databaseUrl,
          AUTH_TOKEN_ENCRYPTION_KEY: invalidKey,
        }),
      ).toThrow(
        "AUTH_TOKEN_ENCRYPTION_KEY must be 64 hexadecimal characters encoding 32 bytes",
      );
    }
    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        AUTH_TOKEN_ENCRYPTION_KEY: testAuthTokenEncryptionKey.toUpperCase(),
      }).authTokenEncryptionKey,
    ).toBe(testAuthTokenEncryptionKey.toUpperCase());
  });

  it("rejects obvious low-entropy repeated hex patterns", () => {
    for (const lowEntropyKey of [
      "a".repeat(64),
      "ab".repeat(32),
      "0123".repeat(16),
      "01234567".repeat(8),
      "0123456789abcdef".repeat(4),
      "0123456".repeat(10).slice(0, 64),
    ]) {
      expect(() =>
        parseApiConfig({
          DATABASE_URL: databaseUrl,
          AUTH_TOKEN_ENCRYPTION_KEY: lowEntropyKey,
        }),
      ).toThrow(
        "AUTH_TOKEN_ENCRYPTION_KEY must not contain obvious low-entropy patterns; generate a random 32-byte value",
      );
    }
  });

  it("accepts an optional previous session encryption key", () => {
    const previousKey =
      "4b20f1d87e39a6c0542fb718a6d3e90c1375b2e94f06ca85d1273e8b6c491a20";

    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS: previousKey,
      }).authTokenEncryptionKeyPrevious,
    ).toBe(previousKey);
    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS: testAuthTokenEncryptionKey,
      }),
    ).not.toHaveProperty("authTokenEncryptionKeyPrevious");
    expect(() =>
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS: "not-a-key",
      }),
    ).toThrow(
      "AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS must be 64 hexadecimal characters encoding 32 bytes",
    );
    expect(() =>
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS: "ab".repeat(32),
      }),
    ).toThrow(
      "AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS must not contain obvious low-entropy patterns; generate a random 32-byte value",
    );
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

  it("reads the previous auth encryption key from a secret file", () => {
    const previousKey =
      "4b20f1d87e39a6c0542fb718a6d3e90c1375b2e94f06ca85d1273e8b6c491a20";
    withSecretFiles(
      { AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS: `${previousKey}\n` },
      (paths) => {
        expect(
          parseApiConfig({
            DATABASE_URL: databaseUrl,
            AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS: undefined,
            AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS_FILE:
              paths.AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS,
          }).authTokenEncryptionKeyPrevious,
        ).toBe(previousKey);
      },
    );
  });

  it("treats an empty previous-key secret file as rotation disabled", () => {
    withSecretFiles({ AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS: "" }, (paths) => {
      expect(
        parseApiConfig({
          DATABASE_URL: databaseUrl,
          AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS: undefined,
          AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS_FILE:
            paths.AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS,
        }),
      ).not.toHaveProperty("authTokenEncryptionKeyPrevious");
    });
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
