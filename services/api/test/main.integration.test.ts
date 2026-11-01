import { describe, expect, it, vi } from "vitest";
import { createServer } from "node:net";
import { reportProcessFailure, runProductionApi } from "../src/main.js";
import type { ShutdownSignal } from "../src/server.js";

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
      DATABASE_URL: process.env.DATABASE_URL,
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

  it("handles shutdown failures from process signal callbacks", async () => {
    const port = await findAvailablePort();
    const environment = {
      DATABASE_URL: process.env.DATABASE_URL,
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
