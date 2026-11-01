import { describe, expect, it, vi } from "vitest";
import { buildApiApp } from "../src/app.js";

describe("health routes and OpenAPI", () => {
  it("reports process liveness without querying PostgreSQL", async () => {
    const ping = vi.fn<() => Promise<void>>();
    const app = await buildApiApp({
      ping,
      close: vi.fn<() => Promise<void>>(),
    });

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/health/live",
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ status: "ok" });
    expect(ping).not.toHaveBeenCalled();
    await app.close();
  });

  it("reports readiness when PostgreSQL responds", async () => {
    const ping = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
    const app = await buildApiApp({
      ping,
      close: vi.fn<() => Promise<void>>(),
    });

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/health/ready",
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ status: "ok" });
    expect(ping).toHaveBeenCalledOnce();
    await app.close();
  });

  it("returns an information-safe unavailable response when PostgreSQL fails", async () => {
    const ping = vi
      .fn<() => Promise<void>>()
      .mockRejectedValue(new Error("secret connection details"));
    const app = await buildApiApp({
      ping,
      close: vi.fn<() => Promise<void>>(),
    });

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/health/ready",
    });

    expect(response.statusCode).toBe(503);
    expect(response.json()).toEqual({ status: "not_ready" });
    expect(response.body).not.toContain("secret connection details");
    await app.close();
  });

  it("returns unavailable when the PostgreSQL readiness probe stalls", async () => {
    let resolvePing: () => void = () => {};
    const ping = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          resolvePing = resolve;
        }),
    );
    const app = await buildApiApp(
      {
        ping,
        close: vi.fn<() => Promise<void>>(),
      },
      20,
    );

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/health/ready",
    });

    expect(response.statusCode).toBe(503);
    expect(response.json()).toEqual({ status: "not_ready" });
    expect(ping).toHaveBeenCalledOnce();
    resolvePing();
    await app.close();
  });

  it("serves the generated OpenAPI 3.1 document as JSON", async () => {
    const app = await buildApiApp({
      ping: vi.fn<() => Promise<void>>(),
      close: vi.fn<() => Promise<void>>(),
    });
    const document = await app.inject({
      method: "GET",
      url: "/api/v1/openapi.json",
    });
    const openApiDocument = document.json() as {
      openapi?: string;
      paths?: Record<string, unknown>;
    };
    expect(document.statusCode).toBe(200);
    expect(document.headers["content-type"]).toContain("application/json");
    expect(openApiDocument.openapi).toBe("3.1.0");
    expect(openApiDocument.paths?.["/api/v1/health/live"]).toBeDefined();
    expect(openApiDocument.paths?.["/api/v1/health/ready"]).toBeDefined();
    await app.close();
  });
});
