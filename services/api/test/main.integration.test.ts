import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { createServer } from "node:net";
import { createHash, randomUUID } from "node:crypto";
import { Pool } from "pg";
import { reportProcessFailure, runApi, runProductionApi } from "../src/main.js";
import type { Database } from "../src/database.js";
import { createPostgresDatabase } from "../src/database.js";
import type { ShutdownSignal } from "../src/server.js";
import { OperatorMessageError } from "../src/operator-message-error.js";

const actualApi = vi.hoisted(() => ({
  init: vi.fn(async () => undefined),
  getServerVersion: vi.fn(async () => ({ version: "26.11.0" })),
  downloadBudget: vi.fn(async () => undefined),
  getBudgets: vi.fn(async () => [
    {
      groupId: "synthetic-catalog-budget",
      name: "Synthetic catalog budget",
      encryptKeyId: "synthetic-encryption-key-id",
    },
  ]),
  sync: vi.fn(async () => undefined),
  shutdown: vi.fn(async () => undefined),
}));
const testAuthTokenEncryptionKey =
  "5323b1ab617c73bf862318e07e388d79d4f902170d607b5be38e6a6f66e2eb3c";

vi.mock("@actual-app/api", () => actualApi);

let adminPool: Pool;
let testSchema: string;
let isolatedDatabaseUrl: string;

beforeAll(async () => {
  const connectionString = process.env.DATABASE_URL;
  if (!connectionString) {
    throw new Error(
      "DATABASE_URL is required; run this test with docker compose",
    );
  }
  testSchema = `main_integration_${randomUUID().replaceAll("-", "")}`;
  adminPool = new Pool({ connectionString, max: 2 });
  await adminPool.query(`CREATE SCHEMA ${testSchema}`);
  const connection = new URL(connectionString);
  connection.searchParams.set("options", `-c search_path=${testSchema}`);
  isolatedDatabaseUrl = connection.toString();
});

afterAll(async () => {
  await adminPool?.query(`DROP SCHEMA ${testSchema} CASCADE`);
  await adminPool?.end();
});

async function findAvailablePort(): Promise<number> {
  const probe = createServer();
  await new Promise<void>((resolve, reject) => {
    probe.once("error", reject);
    probe.listen(0, "127.0.0.1", resolve);
  });
  const address = probe.address();
  if (!address || typeof address === "string") {
    throw new Error("Could not determine an available TCP port");
  }
  await new Promise<void>((resolve, reject) => {
    probe.close((error) => (error ? reject(error) : resolve()));
  });
  return address.port;
}

describe("API bootstrap integration", () => {
  it.each([
    { error: new Error("synthetic startup failure"), errorType: "Error" },
    { error: "synthetic startup failure", errorType: "string" },
  ])("reports runtime errors without exposing messages", (scenario) => {
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});

    reportProcessFailure("API startup failed", scenario.error);

    expect(consoleError).toHaveBeenCalledWith("API startup failed", {
      errorType: scenario.errorType,
    });
    vi.restoreAllMocks();
  });

  it("reports operator-safe diagnostics without exposing arbitrary error messages", () => {
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    const error = new OperatorMessageError(
      "PORT must be an integer between 1 and 65535",
    );

    reportProcessFailure("API startup failed", error);

    expect(consoleError).toHaveBeenCalledWith("API startup failed", {
      message: "PORT must be an integer between 1 and 65535",
    });
    vi.restoreAllMocks();
  });

  it("does not throw when runtime failure logging fails", () => {
    vi.spyOn(console, "error").mockImplementation(() => {
      throw new Error("synthetic logger failure");
    });

    expect(() =>
      reportProcessFailure("API startup failed", new Error("startup failure")),
    ).not.toThrow();
    vi.restoreAllMocks();
  });

  it("validates configuration and starts against the disposable PostgreSQL service", async () => {
    const port = await findAvailablePort();
    const environment = {
      DATABASE_URL: isolatedDatabaseUrl,
      AUTH_TOKEN_ENCRYPTION_KEY: testAuthTokenEncryptionKey,
      HOST: "127.0.0.1",
      PORT: String(port),
    };
    if (!environment.DATABASE_URL) {
      throw new Error(
        "DATABASE_URL is required; run this test with docker compose",
      );
    }

    const handlers = new Map<ShutdownSignal, () => Promise<void>>();
    const signals = {
      once: (signal: ShutdownSignal, handler: () => Promise<void>) => {
        handlers.set(signal, handler);
      },
    } as unknown as NodeJS.Process;
    const app = await runProductionApi(environment, signals);

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/health/ready",
    });
    expect(response.statusCode).toBe(200);
    expect(handlers.has("SIGTERM")).toBe(true);
    expect(handlers.has("SIGINT")).toBe(true);
    await handlers.get("SIGTERM")?.();
  });

  it("wires the budget catalog and preserves generic vault read failures", async () => {
    const port = await findAvailablePort();
    const environment = {
      DATABASE_URL: isolatedDatabaseUrl,
      AUTH_TOKEN_ENCRYPTION_KEY: testAuthTokenEncryptionKey,
      HOST: "127.0.0.1",
      PORT: String(port),
      ACTUAL_SERVER_URL: "http://actual.example",
      ACTUAL_API_DATA_DIR: `/tmp/tandem-main-${randomUUID()}`,
    };
    let vaultReadError: Error | undefined;
    const createDatabase = (
      connectionString: string,
      encryptionKey: string,
      previousEncryptionKey?: string,
    ): Database => {
      const database = createPostgresDatabase(
        connectionString,
        encryptionKey,
        previousEncryptionKey,
      );
      const vault = database.actualSessionVault;
      if (!vault) throw new Error("Actual session vault is unavailable");
      const originalRead = vault.read.bind(vault);
      vi.spyOn(vault, "read").mockImplementation(async (principalId) => {
        if (vaultReadError) throw vaultReadError;
        return originalRead(principalId);
      });
      return database;
    };
    actualApi.getBudgets.mockClear();
    let actualUserId = "synthetic-actual-user";
    const actualFetch = vi.fn(async (input: RequestInfo | URL) => {
      const endpoint = new URL(input.toString());
      const body =
        endpoint.pathname === "/account/validate"
          ? { status: "ok", data: { userId: actualUserId } }
          : { status: "ok", data: { token: "synthetic-actual-session-token" } };
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { "content-type": "application/json" },
      });
    });
    vi.stubGlobal("fetch", actualFetch);
    const app = await runApi(environment, createDatabase, () => {});

    try {
      const signInResponse = await app.inject({
        method: "POST",
        url: "/api/v1/auth/login",
        payload: {
          password: "synthetic-actual-server-password",
          deviceName: "Synthetic test phone",
        },
      });
      expect(signInResponse.statusCode).toBe(201);
      const signedIn = signInResponse.json() as { accessToken: string };

      const response = await app.inject({
        method: "GET",
        url: "/api/v1/budgets",
        headers: { authorization: `Bearer ${signedIn.accessToken}` },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toEqual({
        budgets: [
          {
            budgetId: "synthetic-catalog-budget",
            name: "Synthetic catalog budget",
            isEncrypted: true,
          },
        ],
      });
      expect(actualApi.getBudgets).toHaveBeenCalledTimes(2);

      actualUserId = "synthetic-second-actual-user";
      const secondSignInResponse = await app.inject({
        method: "POST",
        url: "/api/v1/auth/login",
        payload: {
          password: "synthetic-actual-server-password",
          deviceName: "Synthetic second phone",
        },
      });
      expect(secondSignInResponse.statusCode).toBe(201);
      expect(actualApi.getBudgets).toHaveBeenCalledTimes(3);

      vaultReadError = new Error("synthetic database query failure");
      const failedVaultRead = await app.inject({
        method: "GET",
        url: "/api/v1/budgets",
        headers: { authorization: `Bearer ${signedIn.accessToken}` },
      });
      expect(failedVaultRead.statusCode).toBe(502);
      expect(failedVaultRead.json().error).toEqual({
        code: "budget_sync_failed",
        message: "The budget projection could not be refreshed",
      });
      expect(JSON.stringify(failedVaultRead.json())).not.toContain(
        "synthetic database",
      );
      expect(actualApi.getBudgets).toHaveBeenCalledTimes(3);
    } finally {
      await app.close();
      vi.unstubAllGlobals();
    }
  });

  it("loads each principal's Actual session without using the last sign-in for startup readiness", async () => {
    const port = await findAvailablePort();
    const accounts = [
      {
        password: "synthetic-account-a-password",
        sessionToken: "synthetic-account-a-session-token",
        userId: "synthetic-account-a",
        budget: {
          groupId: "account-a-budget",
          name: "Account A budget",
          encryptKeyId: "account-a-encryption-key",
        },
      },
      {
        password: "synthetic-account-b-password",
        sessionToken: "synthetic-account-b-session-token",
        userId: "synthetic-account-b",
        budget: {
          groupId: "account-b-budget",
          name: "Account B budget",
          encryptKeyId: "account-b-encryption-key",
        },
      },
    ];
    const readLatest = vi.fn(async () => {
      throw new Error(
        "Startup must not select the most recently signed-in user",
      );
    });
    const createDatabase = (
      connectionString: string,
      encryptionKey: string,
      previousEncryptionKey?: string,
    ): Database => {
      const database = createPostgresDatabase(
        connectionString,
        encryptionKey,
        previousEncryptionKey,
      );
      vi.spyOn(database.actualSessionVault!, "readLatest").mockImplementation(
        readLatest,
      );
      return database;
    };
    const actualFetch = vi.fn<typeof fetch>(async (input, init) => {
      const endpoint = new URL(input.toString());
      if (endpoint.pathname === "/account/login") {
        const body = JSON.parse(String(init?.body)) as { password: string };
        const account = accounts.find(
          ({ password }) => password === body.password,
        );
        return new Response(
          JSON.stringify({
            status: "ok",
            data: { token: account?.sessionToken },
          }),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }
      const token = new Headers(init?.headers).get("X-ACTUAL-TOKEN");
      const account = accounts.find(
        ({ sessionToken }) => sessionToken === token,
      );
      return new Response(
        JSON.stringify({ status: "ok", data: { userId: account?.userId } }),
        { status: 200, headers: { "content-type": "application/json" } },
      );
    });
    actualApi.init.mockReset().mockResolvedValue(undefined);
    actualApi.getServerVersion
      .mockReset()
      .mockResolvedValue({ version: "26.11.0" });
    actualApi.shutdown.mockReset().mockResolvedValue(undefined);
    actualApi.getBudgets
      .mockReset()
      .mockResolvedValueOnce([accounts[0]!.budget])
      .mockResolvedValueOnce([accounts[1]!.budget])
      .mockResolvedValueOnce([accounts[0]!.budget])
      .mockResolvedValueOnce([accounts[0]!.budget]);
    vi.stubGlobal("fetch", actualFetch);
    const app = await runApi(
      {
        DATABASE_URL: isolatedDatabaseUrl,
        AUTH_TOKEN_ENCRYPTION_KEY: testAuthTokenEncryptionKey,
        HOST: "127.0.0.1",
        PORT: String(port),
        ACTUAL_SERVER_URL: "http://actual.example",
        ACTUAL_SESSION_TOKEN: "synthetic-service-session-token",
        ACTUAL_API_DATA_DIR: `/tmp/tandem-principals-${randomUUID()}`,
      },
      createDatabase,
      () => {},
    );

    try {
      const accountAResponse = await app.inject({
        method: "POST",
        url: "/api/v1/auth/login",
        payload: {
          password: accounts[0]!.password,
          deviceName: "Synthetic account A phone",
        },
      });
      expect(accountAResponse.statusCode).toBe(201);
      const accountA = accountAResponse.json() as {
        accessToken: string;
        principalId: string;
        budgetIds: string[];
      };
      const runningApp = app;
      const accountBResponse = await runningApp.inject({
        method: "POST",
        url: "/api/v1/auth/login",
        payload: {
          password: accounts[1]!.password,
          deviceName: "Synthetic account B phone",
        },
      });
      expect(accountBResponse.statusCode).toBe(201);
      const accountB = accountBResponse.json() as {
        accessToken: string;
        principalId: string;
        budgetIds: string[];
      };
      expect(accountA.budgetIds).toEqual([accounts[0]!.budget.groupId]);
      expect(accountB.budgetIds).toEqual([accounts[1]!.budget.groupId]);
      expect(readLatest).not.toHaveBeenCalled();

      const budgetsForA = await runningApp.inject({
        method: "GET",
        url: "/api/v1/budgets",
        headers: { authorization: `Bearer ${accountA.accessToken}` },
      });
      expect(budgetsForA.statusCode).toBe(200);
      expect(budgetsForA.json().budgets).toMatchObject([
        { budgetId: accounts[0]!.budget.groupId },
      ]);
      const initCallsBeforeExpiredAccountB = actualApi.init.mock
        .calls as unknown as [{ sessionToken?: string }][];
      expect(
        initCallsBeforeExpiredAccountB.map(
          ([connection]) => connection.sessionToken,
        ),
      ).toEqual([
        "synthetic-service-session-token",
        accounts[0]!.sessionToken,
        accounts[1]!.sessionToken,
        accounts[0]!.sessionToken,
      ]);

      actualApi.init.mockRejectedValueOnce(
        Object.assign(new Error(`unauthorized ${accounts[1]!.sessionToken}`), {
          status: 401,
        }),
      );
      const expiredAccountB = await runningApp.inject({
        method: "GET",
        url: "/api/v1/budgets",
        headers: { authorization: `Bearer ${accountB.accessToken}` },
      });
      expect(expiredAccountB.statusCode).toBe(401);
      expect(expiredAccountB.json().error).toEqual({
        code: "actual_sign_in_expired",
        message: "Actual sign-in expired. Sign in again to continue.",
      });

      const readiness = await runningApp.inject({
        method: "GET",
        url: "/api/v1/health/ready",
      });
      expect(readiness.statusCode).toBe(200);
      expect(readLatest).not.toHaveBeenCalled();

      const budgetsForAAfterFailure = await runningApp.inject({
        method: "GET",
        url: "/api/v1/budgets",
        headers: { authorization: `Bearer ${accountA.accessToken}` },
      });
      expect(budgetsForAAfterFailure.statusCode).toBe(200);
      expect(budgetsForAAfterFailure.json().budgets).toMatchObject([
        { budgetId: accounts[0]!.budget.groupId },
      ]);

      const sessionPool = new Pool({
        connectionString: isolatedDatabaseUrl,
        max: 1,
      });
      try {
        await sessionPool.query(
          "UPDATE tandem_actual_sessions SET token_tag = $2 WHERE principal_id = $1",
          [accountB.principalId, Buffer.alloc(16, 0)],
        );
        const corruptedAccountB = await runningApp.inject({
          method: "GET",
          url: "/api/v1/budgets",
          headers: { authorization: `Bearer ${accountB.accessToken}` },
        });
        expect(corruptedAccountB.statusCode).toBe(401);
        expect(corruptedAccountB.json().error).toEqual({
          code: "actual_sign_in_expired",
          message: "Actual sign-in expired. Sign in again to continue.",
        });
        const readinessAfterCorruptedToken = await runningApp.inject({
          method: "GET",
          url: "/api/v1/health/ready",
        });
        expect(readinessAfterCorruptedToken.statusCode).toBe(200);

        await sessionPool.query(
          "DELETE FROM tandem_actual_sessions WHERE principal_id = $1",
          [accountB.principalId],
        );
        const missingAccountB = await runningApp.inject({
          method: "GET",
          url: "/api/v1/budgets",
          headers: { authorization: `Bearer ${accountB.accessToken}` },
        });
        expect(missingAccountB.statusCode).toBe(401);
        expect(missingAccountB.json().error).toEqual({
          code: "actual_sign_in_expired",
          message: "Actual sign-in expired. Sign in again to continue.",
        });
        const readinessAfterMissingToken = await runningApp.inject({
          method: "GET",
          url: "/api/v1/health/ready",
        });
        expect(readinessAfterMissingToken.statusCode).toBe(200);
      } finally {
        await sessionPool.end();
      }

      const initCalls = actualApi.init.mock.calls as unknown as [
        { sessionToken?: string },
      ][];
      expect(initCalls.map(([connection]) => connection.sessionToken)).toEqual([
        "synthetic-service-session-token",
        accounts[0]!.sessionToken,
        accounts[1]!.sessionToken,
        accounts[0]!.sessionToken,
        accounts[1]!.sessionToken,
        accounts[0]!.sessionToken,
      ]);
    } finally {
      vi.unstubAllGlobals();
      await (await app).close();
      actualApi.init.mockReset().mockResolvedValue(undefined);
      actualApi.getServerVersion
        .mockReset()
        .mockResolvedValue({ version: "26.11.0" });
      actualApi.shutdown.mockReset().mockResolvedValue(undefined);
      actualApi.getBudgets.mockReset().mockResolvedValue([
        {
          groupId: "synthetic-catalog-budget",
          name: "Synthetic catalog budget",
          encryptKeyId: "synthetic-encryption-key-id",
        },
      ]);
    }
  });

  it("completes hosted OpenID sign-in and loads the account's Actual budgets", async () => {
    const port = await findAvailablePort();
    const actualSessionToken = "synthetic-oidc-session-token";
    const codeVerifier = "v".repeat(64);
    const callbackState = "S".repeat(43);
    const codeChallenge = createHash("sha256")
      .update(codeVerifier, "ascii")
      .digest("base64url");
    const fetchRequest = vi.fn<typeof fetch>(async (input) => {
      const endpoint = new URL(input.toString());
      if (endpoint.pathname === "/account/login") {
        return new Response(
          JSON.stringify({ returnUrl: "https://idp.example/authorize" }),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }
      expect(endpoint.pathname).toBe("/account/validate");
      expect(fetchRequest).toHaveBeenCalledWith(
        new URL("http://actual.example/account/validate"),
        expect.objectContaining({
          headers: expect.objectContaining({
            "X-ACTUAL-TOKEN": actualSessionToken,
          }),
        }),
      );
      return new Response(
        JSON.stringify({ status: "ok", data: { userId: "oidc-owner" } }),
        { status: 200, headers: { "content-type": "application/json" } },
      );
    });
    vi.stubGlobal("fetch", fetchRequest);
    actualApi.init.mockClear();
    actualApi.getBudgets.mockClear();
    actualApi.shutdown.mockClear();
    const app = await runApi(
      {
        DATABASE_URL: isolatedDatabaseUrl,
        AUTH_TOKEN_ENCRYPTION_KEY: testAuthTokenEncryptionKey,
        HOST: "127.0.0.1",
        PORT: String(port),
        ACTUAL_SERVER_URL: "http://actual.example",
        ACTUAL_PUBLIC_URL: "https://tandem.example",
        ACTUAL_API_DATA_DIR: `/tmp/tandem-openid-${randomUUID()}`,
      },
      createPostgresDatabase,
      () => {},
    );

    try {
      const started = await app.inject({
        method: "POST",
        url: "/api/v1/auth/openid/start",
        payload: {
          deviceName: "Synthetic OpenID phone",
          codeChallenge,
          callbackState,
        },
      });
      expect(started.statusCode).toBe(200);
      const tandemBrowserStart = new URL(started.json().redirectUrl);
      expect(tandemBrowserStart.toString()).toMatch(
        /^https:\/\/tandem\.example\/api\/v1\/auth\/openid\/browser-start\/[A-Za-z0-9_-]{43}$/u,
      );

      const loginRequest = JSON.parse(
        String(fetchRequest.mock.calls[0]?.[1]?.body),
      ) as { returnUrl: string };
      const state = new URL(loginRequest.returnUrl).pathname.split("/").at(-2);
      expect(state).toMatch(/^[A-Za-z0-9_-]{43}$/);

      const browserStart = await app.inject({
        method: "GET",
        url: tandemBrowserStart.pathname,
      });
      const setCookieHeader = browserStart.headers["set-cookie"];
      const browserCookie = Array.isArray(setCookieHeader)
        ? setCookieHeader[0]?.split(";", 1)[0]
        : setCookieHeader?.split(";", 1)[0];
      expect(browserStart.statusCode).toBe(302);
      expect(browserStart.headers.location).toBe(
        "https://idp.example/authorize",
      );
      expect(browserCookie).toBeDefined();

      const callback = await app.inject({
        method: "GET",
        url: `/api/v1/auth/actual-return/${state}/openid-cb?token=${actualSessionToken}`,
        headers: { cookie: browserCookie ?? "" },
      });
      expect(callback.statusCode).toBe(302);
      const completionCode = new URL(
        callback.headers.location ?? "",
      ).searchParams.get("code");
      expect(
        new URL(callback.headers.location ?? "").searchParams.get("state"),
      ).toBe(callbackState);
      expect(completionCode).toMatch(/^[A-Za-z0-9_-]{43}$/);

      const stolenCodeAttempt = await app.inject({
        method: "POST",
        url: "/api/v1/auth/openid/complete",
        payload: { code: completionCode, codeVerifier: "x".repeat(64) },
      });
      expect(stolenCodeAttempt.statusCode).toBe(401);

      const completed = await app.inject({
        method: "POST",
        url: "/api/v1/auth/openid/complete",
        payload: { code: completionCode, codeVerifier },
      });
      expect(completed.statusCode).toBe(201);
      expect(completed.json()).toMatchObject({
        principalId: expect.stringMatching(/^actual-user:[a-f0-9]{64}$/),
        budgetIds: ["synthetic-catalog-budget"],
      });
      expect(actualApi.getBudgets).toHaveBeenCalledOnce();
      expect(actualApi.init).toHaveBeenCalledWith(
        expect.objectContaining({ sessionToken: actualSessionToken }),
      );
    } finally {
      await app.close();
      vi.unstubAllGlobals();
    }
    expect(actualApi.shutdown).toHaveBeenCalledOnce();
  });

  it("filters OpenID from login methods when no public callback URL is configured", async () => {
    const scenarios = [
      {
        methods: {
          bootstrapped: true,
          loginMethod: "openid",
          availableLoginMethods: [
            { method: "openid", displayName: "Household SSO", active: true },
            { method: "password", displayName: "Password", active: true },
          ],
        },
        expectedLoginMethod: "password",
        expectedMethods: [
          { method: "password", displayName: "Password", active: true },
        ],
      },
      {
        methods: {
          bootstrapped: true,
          loginMethod: "openid",
          availableLoginMethods: [
            { method: "openid", displayName: "Household SSO", active: true },
            { method: "password", displayName: "Password", active: false },
          ],
        },
        expectedLoginMethod: "unsupported",
        expectedMethods: [
          { method: "password", displayName: "Password", active: false },
        ],
      },
      {
        methods: {
          bootstrapped: true,
          loginMethod: "password",
          availableLoginMethods: [
            { method: "password", displayName: "Password", active: true },
          ],
        },
        expectedLoginMethod: "password",
        expectedMethods: [
          { method: "password", displayName: "Password", active: true },
        ],
      },
    ];

    for (const [index, scenario] of scenarios.entries()) {
      const port = await findAvailablePort();
      const database = {
        ping: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
        close: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
        migrate: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
        journal: {
          expireLeases: vi.fn<() => Promise<number>>().mockResolvedValue(0),
        },
        deviceAuth: { authenticate: async () => undefined },
      } as unknown as Database;
      const fetchRequest = vi.fn<typeof fetch>(
        async () =>
          new Response(
            JSON.stringify({ status: "ok", data: scenario.methods }),
            { status: 200, headers: { "content-type": "application/json" } },
          ),
      );
      vi.stubGlobal("fetch", fetchRequest);
      const app = await runApi(
        {
          DATABASE_URL: "postgres://local/test",
          AUTH_TOKEN_ENCRYPTION_KEY: testAuthTokenEncryptionKey,
          HOST: "127.0.0.1",
          PORT: String(port),
          ACTUAL_SERVER_URL: "http://actual.example",
          ACTUAL_API_DATA_DIR: `/tmp/tandem-login-methods-${index}`,
        },
        () => database,
        () => {},
      );

      try {
        const response = await app.inject({
          method: "GET",
          url: "/api/v1/auth/methods",
        });
        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
          bootstrapped: true,
          loginMethod: scenario.expectedLoginMethod,
          availableLoginMethods: scenario.expectedMethods,
        });
      } finally {
        await app.close();
        vi.unstubAllGlobals();
      }
    }
  });

  it("logs the safe migration-history diagnostic from a production startup failure", async () => {
    const connectionString = process.env.DATABASE_URL;
    if (!connectionString) {
      throw new Error(
        "DATABASE_URL is required; run this test with docker compose",
      );
    }
    const schemaName = `main_migration_failure_${randomUUID().replaceAll("-", "")}`;
    await adminPool.query(`CREATE SCHEMA ${schemaName}`);
    const isolatedUrl = new URL(connectionString);
    isolatedUrl.searchParams.set("options", `-c search_path=${schemaName}`);
    const isolatedPool = new Pool({
      connectionString: isolatedUrl.toString(),
      max: 1,
    });
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});

    try {
      await isolatedPool.query(`CREATE TABLE tandem_migrations (
        position integer PRIMARY KEY CHECK (position > 0),
        name text NOT NULL UNIQUE,
        applied_at timestamptz NOT NULL DEFAULT clock_timestamp()
      )`);
      await isolatedPool.query(
        "INSERT INTO tandem_migrations (position, name) VALUES (1, $1)",
        ["20261101000000-older-schema"],
      );

      const port = await findAvailablePort();
      const startup = runProductionApi(
        {
          DATABASE_URL: isolatedUrl.toString(),
          AUTH_TOKEN_ENCRYPTION_KEY: testAuthTokenEncryptionKey,
          HOST: "127.0.0.1",
          PORT: String(port),
        },
        { once: vi.fn() } as unknown as NodeJS.Process,
      );
      const error = await startup.catch((startupError: unknown) => {
        reportProcessFailure("API startup failed", startupError);
        return startupError;
      });

      expect(error).toBeInstanceOf(OperatorMessageError);
      expect(consoleError).toHaveBeenCalledWith("API startup failed", {
        message: expect.stringContaining(
          "Tandem migration history mismatch at position 1: expected 20261101000000-initial-schema, found 20261101000000-older-schema",
        ),
      });
    } finally {
      vi.restoreAllMocks();
      await isolatedPool.end();
      await adminPool.query(`DROP SCHEMA ${schemaName} CASCADE`);
    }
  });

  it("starts and closes the configured Actual runtime and exposes transaction routes", async () => {
    actualApi.init.mockClear();
    actualApi.getServerVersion.mockClear();
    actualApi.shutdown.mockClear();
    const fetchRequest = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          status: "ok",
          data: {
            bootstrapped: true,
            loginMethod: "openid",
            availableLoginMethods: [
              { method: "openid", displayName: "Household SSO", active: 1 },
            ],
          },
        }),
        { status: 200, headers: { "content-type": "application/json" } },
      ),
    );
    vi.stubGlobal("fetch", fetchRequest);

    const database = {
      ping: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      close: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      migrate: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      journal: {
        expireLeases: vi.fn<() => Promise<number>>().mockResolvedValue(0),
      },
      deviceAuth: { authenticate: async () => undefined },
    } as unknown as Database;
    const port = await findAvailablePort();
    const app = await runApi(
      {
        DATABASE_URL: "postgres://local/test",
        AUTH_TOKEN_ENCRYPTION_KEY: testAuthTokenEncryptionKey,
        HOST: "127.0.0.1",
        PORT: String(port),
        ACTUAL_SERVER_URL: "http://127.0.0.1:5006",
        ACTUAL_PUBLIC_URL: "https://tandem.example",
        ACTUAL_SESSION_TOKEN: "synthetic-server-session-token",
        ACTUAL_API_DATA_DIR: "/tmp/tandem-runtime-test",
      },
      () => database,
      vi.fn(),
    );

    try {
      expect(actualApi.init).toHaveBeenCalledWith({
        serverURL: "http://127.0.0.1:5006",
        sessionToken: "synthetic-server-session-token",
        dataDir: "/tmp/tandem-runtime-test",
      });
      expect(actualApi.getServerVersion).toHaveBeenCalledOnce();
      const response = await app.inject({
        method: "GET",
        url: "/api/v1/openapi.json",
      });
      const readiness = await app.inject({
        method: "GET",
        url: "/api/v1/health/ready",
      });
      const loginMethods = await app.inject({
        method: "GET",
        url: "/api/v1/auth/methods",
      });
      expect(readiness.statusCode).toBe(200);
      expect(loginMethods.statusCode).toBe(200);
      expect(loginMethods.json()).toEqual({
        bootstrapped: true,
        loginMethod: "openid",
        availableLoginMethods: [
          { method: "openid", displayName: "Household SSO", active: true },
        ],
      });
      expect(fetchRequest).toHaveBeenCalledWith(
        new URL("http://127.0.0.1:5006/account/needs-bootstrap"),
        expect.objectContaining({ cache: "no-store" }),
      );
      expect(
        response.json().paths[
          "/api/v1/budgets/{budgetId}/transactions/{transactionId}"
        ].get,
      ).toBeDefined();
    } finally {
      try {
        await app.close();
      } finally {
        vi.unstubAllGlobals();
      }
    }
    expect(actualApi.shutdown).toHaveBeenCalledOnce();
    expect(database.close).toHaveBeenCalledOnce();
  });

  it("exposes a safe readiness message when Actual rejects the session token", async () => {
    const port = await findAvailablePort();
    const sessionToken = "synthetic-private-session-token";
    actualApi.init.mockClear();
    actualApi.init.mockRejectedValueOnce(
      Object.assign(new Error(`unauthorized ${sessionToken}`), { status: 401 }),
    );
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    const app = await runApi(
      {
        DATABASE_URL: isolatedDatabaseUrl,
        AUTH_TOKEN_ENCRYPTION_KEY: testAuthTokenEncryptionKey,
        HOST: "127.0.0.1",
        PORT: String(port),
        ACTUAL_SERVER_URL: "http://actual.example",
        ACTUAL_SESSION_TOKEN: sessionToken,
        ACTUAL_API_DATA_DIR: `/tmp/tandem-main-${randomUUID()}`,
      },
      createPostgresDatabase,
      () => {},
    );

    try {
      const readiness = await app.inject({
        method: "GET",
        url: "/api/v1/health/ready",
      });

      expect(actualApi.init).toHaveBeenCalledWith({
        serverURL: "http://actual.example",
        sessionToken,
        dataDir: expect.stringMatching(/^\/tmp\/tandem-main-/),
      });
      expect(readiness.statusCode).toBe(503);
      expect(readiness.json()).toEqual({
        error: {
          code: "actual_unavailable",
          message:
            "Actual rejected ACTUAL_SESSION_TOKEN. Replace the token and restart Tandem.",
        },
      });
      expect(JSON.stringify(consoleError.mock.calls)).not.toContain(
        sessionToken,
      );
    } finally {
      vi.restoreAllMocks();
      await app.close();
    }
  });

  it("handles shutdown failures from process signal callbacks", async () => {
    const port = await findAvailablePort();
    const environment = {
      DATABASE_URL: isolatedDatabaseUrl,
      AUTH_TOKEN_ENCRYPTION_KEY: testAuthTokenEncryptionKey,
      HOST: "127.0.0.1",
      PORT: String(port),
    };
    if (!environment.DATABASE_URL) {
      throw new Error(
        "DATABASE_URL is required; run this test with docker compose",
      );
    }

    const handlers = new Map<ShutdownSignal, () => Promise<void>>();
    const signals = {
      exitCode: undefined as number | undefined,
      once: (signal: ShutdownSignal, handler: () => Promise<void>) => {
        handlers.set(signal, handler);
      },
    } as unknown as NodeJS.Process;
    const app = await runProductionApi(environment, signals);
    const originalClose = app.close.bind(app);
    const closeError = new Error("synthetic close failure");
    vi.spyOn(app, "close").mockRejectedValue(closeError);
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});

    await expect(handlers.get("SIGTERM")?.()).resolves.toBeUndefined();
    expect(signals.exitCode).toBe(1);
    expect(consoleError).toHaveBeenCalledWith("API shutdown failed", {
      errorType: "Error",
    });

    vi.restoreAllMocks();
    await originalClose();
  });
});
