import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { parseApiConfig as parseRawApiConfig } from "../src/config.js";

const databaseUrl =
  "postgresql://companion:local-test@localhost:5432/companion";
const testPairingCodeHashKey = "synthetic-pairing-code-hash-key-32-bytes";

function parseApiConfig(environment: Record<string, string | undefined>) {
  return parseRawApiConfig({
    PAIRING_CODE_HASH_KEY: testPairingCodeHashKey,
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
        ACTUAL_SERVER_PASSWORD: "",
      }).actual,
    ).toBeUndefined();
  });

  it("uses safe development defaults", () => {
    expect(parseApiConfig({ DATABASE_URL: databaseUrl })).toEqual({
      databaseUrl,
      pairingCodeHashKey: testPairingCodeHashKey,
      host: "0.0.0.0",
      port: 19753,
      trustProxy: false,
      changeFeedEpoch: "default",
    });
  });

  it("keeps Actual integration disabled when credentials are omitted", () => {
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

  it("parses a pinned Actual connection and persistent API workspace", () => {
    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        ACTUAL_SERVER_URL: " https://budget.example.com/ ",
        ACTUAL_SERVER_PASSWORD: " server secret ",
        ACTUAL_API_DATA_DIR: " /srv/actual-api ",
      }).actual,
    ).toEqual({
      serverURL: "https://budget.example.com",
      password: " server secret ",
      dataDir: "/srv/actual-api",
    });
    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        ACTUAL_SERVER_URL: "http://actual:5006",
        ACTUAL_SERVER_PASSWORD: "server-secret",
      }).actual?.dataDir,
    ).toBe("/data/actual-api");
  });

  it.each([
    { ACTUAL_SERVER_URL: "https://actual.example.com" },
    { ACTUAL_SERVER_PASSWORD: "server-secret" },
    {
      ACTUAL_SERVER_URL: "https://actual.example.com",
      ACTUAL_SERVER_PASSWORD: "",
    },
  ])("rejects incomplete Actual connection settings", (actual) => {
    expect(() =>
      parseApiConfig({ DATABASE_URL: databaseUrl, ...actual }),
    ).toThrow(/ACTUAL_SERVER_URL and/);
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
        ACTUAL_SERVER_PASSWORD: "server-secret",
      }),
    ).toThrow("ACTUAL_SERVER_URL must be a valid HTTP(S) URL");
  });

  it("rejects a relative Actual API workspace path", () => {
    expect(() =>
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        ACTUAL_SERVER_URL: "http://actual:5006",
        ACTUAL_SERVER_PASSWORD: "server-secret",
        ACTUAL_API_DATA_DIR: "actual-api",
      }),
    ).toThrow("ACTUAL_API_DATA_DIR must be an absolute path");
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
      pairingCodeHashKey: testPairingCodeHashKey,
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

  it("requires an independent pairing-code hash key with at least 32 bytes", () => {
    expect(() => parseRawApiConfig({ DATABASE_URL: databaseUrl })).toThrow(
      "PAIRING_CODE_HASH_KEY is required",
    );
    expect(() =>
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        PAIRING_CODE_HASH_KEY: "x".repeat(31),
      }),
    ).toThrow("PAIRING_CODE_HASH_KEY must contain at least 32 bytes");
    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        PAIRING_CODE_HASH_KEY: "x".repeat(32),
      }).pairingCodeHashKey,
    ).toBe("x".repeat(32));
  });

  it("reads the database URL and pairing key from secret files", () => {
    withSecretFiles(
      {
        DATABASE_URL: `${databaseUrl}\n`,
        PAIRING_CODE_HASH_KEY: `${testPairingCodeHashKey}\n`,
      },
      (paths) => {
        expect(
          parseApiConfig({
            DATABASE_URL: undefined,
            DATABASE_URL_FILE: paths.DATABASE_URL,
            PAIRING_CODE_HASH_KEY: undefined,
            PAIRING_CODE_HASH_KEY_FILE: paths.PAIRING_CODE_HASH_KEY,
          }),
        ).toMatchObject({
          databaseUrl,
          pairingCodeHashKey: testPairingCodeHashKey,
        });
      },
    );
  });

  it("reads the optional Actual password from a secret file", () => {
    withSecretFiles(
      { ACTUAL_SERVER_PASSWORD: " server secret \n" },
      (paths) => {
        expect(
          parseApiConfig({
            DATABASE_URL: databaseUrl,
            ACTUAL_SERVER_URL: "https://budget.example.com",
            ACTUAL_SERVER_PASSWORD_FILE: paths.ACTUAL_SERVER_PASSWORD,
          }).actual?.password,
        ).toBe(" server secret ");
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
