import { describe, expect, it, vi } from "vitest";
import { buildApiApp } from "../src/app.js";
import { DeviceAuthInputError } from "../src/auth/device-auth.js";
import type {
  DeviceAuthorization,
  DeviceRecord,
} from "../src/auth/device-auth.js";

const testDeviceId = "c2d791c1-d2bc-4e94-9f39-2d28d45e7c10";
const testAccessToken = "A".repeat(43);

function createDevice(overrides: Partial<DeviceRecord> = {}): DeviceRecord {
  return {
    deviceId: testDeviceId,
    principalId: "owner",
    name: "Synthetic phone",
    budgetIds: ["synthetic-budget"],
    createdAt: new Date("2026-11-01T00:00:00.000Z"),
    lastSeenAt: null,
    revokedAt: null,
    ...overrides,
  };
}

function createDeviceAuth(overrides: Record<string, unknown> = {}) {
  return {
    createPairingCode: vi.fn(),
    pair: vi.fn(async () => undefined),
    authenticate: vi.fn(async () => undefined),
    listDevices: vi.fn(async () => []),
    revokeDevice: vi.fn(async () => false),
    ...overrides,
  } as unknown as DeviceAuthorization;
}

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
      { readinessTimeoutMs: 20 },
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
    const app = await buildApiApp(
      {
        ping: vi.fn<() => Promise<void>>(),
        close: vi.fn<() => Promise<void>>(),
      },
      { deviceAuth: createDeviceAuth() },
    );
    const document = await app.inject({
      method: "GET",
      url: "/api/v1/openapi.json",
    });
    const openApiDocument = document.json() as {
      openapi?: string;
      paths?: Record<string, unknown>;
      components?: { securitySchemes?: Record<string, unknown> };
    };
    expect(document.statusCode).toBe(200);
    expect(document.headers["content-type"]).toContain("application/json");
    expect(openApiDocument.openapi).toBe("3.1.0");
    expect(openApiDocument.paths?.["/api/v1/health/live"]).toBeDefined();
    expect(openApiDocument.paths?.["/api/v1/health/ready"]).toBeDefined();
    expect(openApiDocument.paths?.["/api/v1/auth/pair"]).toBeDefined();
    expect(openApiDocument.paths?.["/api/v1/auth/me"]).toBeDefined();
    expect(openApiDocument.paths?.["/api/v1/devices/{deviceId}"]).toBeDefined();
    expect(
      openApiDocument.components?.securitySchemes?.bearerAuth,
    ).toMatchObject({
      type: "http",
      scheme: "bearer",
    });
    await app.close();
  });

  it("pairs a device once and returns its bearer token without cache", async () => {
    const device = createDevice();
    const auth = createDeviceAuth({
      pair: vi.fn(async ({ code }: { code: string }) =>
        code === "B".repeat(43)
          ? { ...device, accessToken: testAccessToken }
          : undefined,
      ),
    });
    const app = await buildApiApp(
      { ping: vi.fn(), close: vi.fn() },
      { deviceAuth: auth },
    );

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/auth/pair",
      payload: { code: "B".repeat(43), deviceName: "Synthetic phone" },
    });

    expect(response.statusCode).toBe(201);
    expect(response.headers["cache-control"]).toBe("no-store");
    expect(response.json()).toMatchObject({
      deviceId: testDeviceId,
      principalId: "owner",
      name: "Synthetic phone",
      accessToken: testAccessToken,
      tokenType: "Bearer",
    });
    expect(auth.pair).toHaveBeenCalledWith({
      code: "B".repeat(43),
      deviceName: "Synthetic phone",
    });

    const invalid = await app.inject({
      method: "POST",
      url: "/api/v1/auth/pair",
      payload: { code: "C".repeat(43), deviceName: "Synthetic phone" },
    });
    expect(invalid.statusCode).toBe(401);
    expect(invalid.json()).toEqual({
      error: {
        code: "invalid_pairing_code",
        message: "Pairing code is invalid, expired, or already used",
      },
    });

    const invalidBody = await app.inject({
      method: "POST",
      url: "/api/v1/auth/pair",
      payload: { code: "short", deviceName: "Synthetic phone" },
    });
    expect(invalidBody.statusCode).toBe(400);
    await app.close();
  });

  it("maps device authorization input errors to structured HTTP 400 responses", async () => {
    const auth = createDeviceAuth({
      pair: vi
        .fn()
        .mockRejectedValueOnce(
          new DeviceAuthInputError(
            "deviceName must contain between 1 and 100 characters",
          ),
        )
        .mockRejectedValueOnce(new Error("synthetic store failure")),
    });
    const app = await buildApiApp(
      { ping: vi.fn(), close: vi.fn() },
      { deviceAuth: auth },
    );

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/auth/pair",
      payload: { code: "F".repeat(43), deviceName: "Synthetic phone" },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toEqual({
      error: {
        code: "invalid_request",
        message: "deviceName must contain between 1 and 100 characters",
      },
    });
    const storeFailure = await app.inject({
      method: "POST",
      url: "/api/v1/auth/pair",
      payload: { code: "F".repeat(43), deviceName: "Synthetic phone" },
    });
    expect(storeFailure.statusCode).toBe(500);
    await app.close();
  });

  it("rate-limits pairing attempts from one client address", async () => {
    const auth = createDeviceAuth();
    const app = await buildApiApp(
      { ping: vi.fn(), close: vi.fn() },
      { deviceAuth: auth },
    );
    const request = () =>
      app.inject({
        method: "POST",
        url: "/api/v1/auth/pair",
        payload: { code: "D".repeat(43), deviceName: "Synthetic phone" },
      });

    for (let attempt = 0; attempt < 10; attempt += 1) {
      expect((await request()).statusCode).toBe(401);
    }
    const limited = await request();
    expect(limited.statusCode).toBe(429);
    expect(limited.headers["retry-after"]).toBe("60");
    expect(limited.json()).toEqual({
      error: { code: "rate_limited", message: "Too many pairing attempts" },
    });
    expect(auth.pair).toHaveBeenCalledTimes(10);
    await app.close();
  });

  it("authenticates, lists, and revokes only devices under the same principal", async () => {
    const device = createDevice({
      lastSeenAt: new Date("2026-11-01T01:00:00.000Z"),
    });
    const auth = createDeviceAuth({
      authenticate: vi.fn(async (token?: string) =>
        token === testAccessToken ? device : undefined,
      ),
      listDevices: vi.fn(async () => [
        device,
        createDevice({
          deviceId: "c2d791c1-d2bc-4e94-9f39-2d28d45e7c11",
          revokedAt: new Date("2026-11-01T02:00:00.000Z"),
        }),
      ]),
      revokeDevice: vi.fn(
        async (_principalId: string, id: string) => id === testDeviceId,
      ),
    });
    const app = await buildApiApp(
      { ping: vi.fn(), close: vi.fn() },
      { deviceAuth: auth },
    );

    const unauthorized = await app.inject({
      method: "GET",
      url: "/api/v1/auth/me",
    });
    expect(unauthorized.statusCode).toBe(401);
    expect(unauthorized.headers["www-authenticate"]).toContain("Bearer");

    const invalidToken = await app.inject({
      method: "GET",
      url: "/api/v1/auth/me",
      headers: { authorization: "Bearer not-valid" },
    });
    expect(invalidToken.statusCode).toBe(401);

    const unauthorizedList = await app.inject({
      method: "GET",
      url: "/api/v1/devices",
    });
    expect(unauthorizedList.statusCode).toBe(401);
    const unauthorizedRevoke = await app.inject({
      method: "DELETE",
      url: `/api/v1/devices/${testDeviceId}`,
    });
    expect(unauthorizedRevoke.statusCode).toBe(401);

    const current = await app.inject({
      method: "GET",
      url: "/api/v1/auth/me",
      headers: { authorization: `bearer ${testAccessToken}` },
    });
    expect(current.statusCode).toBe(200);
    expect(current.json()).toMatchObject({
      deviceId: testDeviceId,
      lastSeenAt: "2026-11-01T01:00:00.000Z",
      budgetIds: ["synthetic-budget"],
    });

    const listed = await app.inject({
      method: "GET",
      url: "/api/v1/devices",
      headers: { authorization: `Bearer ${testAccessToken}` },
    });
    expect(listed.statusCode).toBe(200);
    expect(listed.json()).toMatchObject({
      devices: [
        {
          deviceId: testDeviceId,
          lastSeenAt: "2026-11-01T01:00:00.000Z",
          revokedAt: null,
        },
        {
          deviceId: "c2d791c1-d2bc-4e94-9f39-2d28d45e7c11",
          lastSeenAt: null,
          revokedAt: "2026-11-01T02:00:00.000Z",
        },
      ],
    });

    const revoked = await app.inject({
      method: "DELETE",
      url: `/api/v1/devices/${testDeviceId}`,
      headers: { authorization: `Bearer ${testAccessToken}` },
    });
    expect(revoked.statusCode).toBe(204);
    const missing = await app.inject({
      method: "DELETE",
      url: `/api/v1/devices/${"d2d791c1-d2bc-4e94-9f39-2d28d45e7c10"}`,
      headers: { authorization: `Bearer ${testAccessToken}` },
    });
    expect(missing.statusCode).toBe(404);
    await app.close();
  });
});
