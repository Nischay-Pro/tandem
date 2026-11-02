import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { createServer } from "node:net";
import { randomUUID } from "node:crypto";
import { Pool } from "pg";
import { reportProcessFailure, runApi, runProductionApi } from "../src/main.js";
import type { Database } from "../src/database.js";
import type { ShutdownSignal } from "../src/server.js";
import { OperatorMessageError } from "../src/operator-message-error.js";

const actualApi = vi.hoisted(() => ({
  init: vi.fn(async () => undefined),
  getServerVersion: vi.fn(async () => ({ version: "26.10.0" })),
  downloadBudget: vi.fn(async () => undefined),
  sync: vi.fn(async () => undefined),
  shutdown: vi.fn(async () => undefined),
}));

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
      await isolatedPool.query(`CREATE TABLE companion_migrations (
        position integer PRIMARY KEY CHECK (position > 0),
        name text NOT NULL UNIQUE,
        applied_at timestamptz NOT NULL DEFAULT clock_timestamp()
      )`);
      await isolatedPool.query(
        "INSERT INTO companion_migrations (position, name) VALUES (1, $1)",
        ["20261101000000-older-schema"],
      );

      const port = await findAvailablePort();
      const startup = runProductionApi(
        {
          DATABASE_URL: isolatedUrl.toString(),
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
          "Companion migration history mismatch at position 1: expected 20261101155148-companion-schema-v2, found 20261101000000-older-schema",
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

    const database = {
      ping: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      close: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      migrate: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
      journal: {
        expireLeases: vi.fn<() => Promise<number>>().mockResolvedValue(0),
      },
      deviceAuth: {},
    } as unknown as Database;
    const port = await findAvailablePort();
    const app = await runApi(
      {
        DATABASE_URL: "postgres://local/test",
        HOST: "127.0.0.1",
        PORT: String(port),
        ACTUAL_SERVER_URL: "http://127.0.0.1:5006",
        ACTUAL_SERVER_PASSWORD: "synthetic-server-password",
        ACTUAL_API_DATA_DIR: "/tmp/actual-companion-runtime-test",
      },
      () => database,
      vi.fn(),
    );

    expect(actualApi.init).toHaveBeenCalledWith({
      serverURL: "http://127.0.0.1:5006",
      password: "synthetic-server-password",
      dataDir: "/tmp/actual-companion-runtime-test",
    });
    expect(actualApi.getServerVersion).toHaveBeenCalledOnce();
    const response = await app.inject({
      method: "GET",
      url: "/api/v1/openapi.json",
    });
    expect(
      response.json().paths[
        "/api/v1/budgets/{budgetId}/transactions/{transactionId}"
      ].get,
    ).toBeDefined();

    await app.close();
    expect(actualApi.shutdown).toHaveBeenCalledOnce();
    expect(database.close).toHaveBeenCalledOnce();
  });

  it("handles shutdown failures from process signal callbacks", async () => {
    const port = await findAvailablePort();
    const environment = {
      DATABASE_URL: isolatedDatabaseUrl,
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
