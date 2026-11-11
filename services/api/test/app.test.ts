import { describe, expect, it, vi } from "vitest";
import { createHash } from "node:crypto";
import { Writable } from "node:stream";
import { buildApiApp, safeErrorType } from "../src/app.js";
import {
  ActualPasswordRejectedError,
  ActualPasswordUnavailableError,
} from "../src/auth/actual-password-login.js";
import { ActualOpenIdLogin } from "../src/auth/actual-openid-login.js";
import type { ActualPasswordLogin } from "../src/auth/actual-password-login.js";
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
    createDevice: vi.fn(),
    authenticate: vi.fn(async () => undefined),
    listDevicesForDevice: vi.fn(async () => []),
    revokeDeviceForDevice: vi.fn(async () => false),
    ...overrides,
  } as unknown as DeviceAuthorization;
}

describe("health routes and OpenAPI", () => {
  it("returns safe structured framework errors and a request id", async () => {
    const app = await buildApiApp(
      {
        ping: vi.fn<() => Promise<void>>(),
        close: vi.fn<() => Promise<void>>(),
      },
      {
        deviceAuth: createDeviceAuth(),
      },
    );

    const missingRoute = await app.inject({
      method: "GET",
      url: "/api/v1/not-a-route",
    });
    const unsupportedMedia = await app.inject({
      method: "POST",
      url: "/api/v1/auth/login",
      headers: { "content-type": "text/plain" },
      payload: "not json",
    });
    const malformedJson = await app.inject({
      method: "POST",
      url: "/api/v1/auth/login",
      headers: { "content-type": "application/json" },
      payload: '{"private":"malformed',
    });
    const oversizedBody = await app.inject({
      method: "POST",
      url: "/api/v1/auth/login",
      headers: { "content-type": "application/json" },
      payload: `{"private":"${"x".repeat(1024 * 1024)}"}`,
    });

    expect(missingRoute.statusCode).toBe(404);
    expect(missingRoute.headers["x-request-id"]).toMatch(/^[0-9a-f-]{36}$/i);
    expect(missingRoute.json()).toEqual({
      error: {
        code: "route_not_found",
        message:
          "Tandem API route was not found. Check the address and /api/v1/ proxy route.",
      },
    });
    expect(unsupportedMedia.statusCode).toBe(415);
    expect(unsupportedMedia.json()).toEqual({
      error: {
        code: "unsupported_media_type",
        message: "Send this request as application/json.",
      },
    });
    expect(malformedJson.statusCode).toBe(400);
    expect(malformedJson.json()).toEqual({
      error: {
        code: "invalid_request",
        message: "Check the request details and try again.",
      },
    });
    expect(malformedJson.body).not.toContain("private");
    expect(oversizedBody.statusCode).toBe(413);
    expect(oversizedBody.json()).toEqual({
      error: {
        code: "request_too_large",
        message: "This request is too large. Reduce it and try again.",
      },
    });
    expect(oversizedBody.body).not.toContain("private");
    await app.close();
  });

  it("logs an unexpected error once with a request id and never sends its details", async () => {
    const lines: string[] = [];
    const stream = new Writable({
      write(chunk, _encoding, callback) {
        lines.push(String(chunk));
        callback();
      },
    });
    const app = await buildApiApp(
      {
        ping: vi.fn<() => Promise<void>>(),
        close: vi.fn<() => Promise<void>>(),
      },
      {
        logger: { level: "info", stream },
        deviceAuth: createDeviceAuth({
          authenticate: vi.fn(async () => {
            throw new Error(
              "synthetic db host 10.1.2.3 password=private-value",
            );
          }),
        }),
      },
    );

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/budgets?access_token=must-not-be-logged",
      headers: { authorization: `Bearer ${testAccessToken}` },
    });
    const entries = lines.map(
      (line) => JSON.parse(line) as Record<string, unknown>,
    );

    expect(response.statusCode).toBe(500);
    expect(response.headers["x-request-id"]).toMatch(/^[0-9a-f-]{36}$/i);
    expect(response.json()).toEqual({
      error: {
        code: "internal_error",
        message: "Tandem couldn’t complete the request. Try again shortly.",
      },
    });
    expect(response.body).not.toContain("10.1.2.3");
    expect(response.body).not.toContain("private-value");
    expect(lines).toHaveLength(1);
    expect(entries[0]).toMatchObject({
      requestId: response.headers["x-request-id"],
      method: "GET",
      route: "/api/v1/budgets",
      statusCode: 500,
      errorType: "Error",
      msg: "API request failed",
    });
    expect(entries[0]?.durationMs).toEqual(expect.any(Number));
    expect(lines[0]).not.toContain("synthetic db host");
    expect(lines[0]).not.toContain("private-value");
    expect(lines[0]).not.toContain("stack");
    expect(lines[0]).not.toContain("must-not-be-logged");
    expect(lines[0]).not.toContain(testAccessToken);
    await app.close();
  });

  it("logs safe types for thrown non-errors and malformed Error constructor names", async () => {
    expect(safeErrorType(null)).toBe("null");
    const lines: string[] = [];
    const stream = new Writable({
      write(chunk, _encoding, callback) {
        lines.push(String(chunk));
        callback();
      },
    });
    const malformedNameError = new Error("private exception");
    Object.defineProperty(malformedNameError, "constructor", {
      value: { name: "Unsafe Error Name" },
    });
    const thrownValues: unknown[] = [
      "private thrown value",
      malformedNameError,
    ];
    let thrownValueIndex = 0;
    const app = await buildApiApp(
      {
        ping: vi.fn<() => Promise<void>>(),
        close: vi.fn<() => Promise<void>>(),
      },
      {
        logger: { level: "info", stream },
        deviceAuth: createDeviceAuth({
          authenticate: vi.fn(async () => {
            throw thrownValues[thrownValueIndex++];
          }),
        }),
      },
    );

    const responses = [];
    for (let index = 0; index < thrownValues.length; index += 1) {
      const response = await app.inject({
        method: "GET",
        url: "/api/v1/budgets",
        headers: { authorization: `Bearer ${testAccessToken}` },
      });
      responses.push(response);
    }
    const entries = lines.map(
      (line) => JSON.parse(line) as Record<string, unknown>,
    );

    expect(responses.map((response) => response.statusCode)).toEqual([
      500, 500,
    ]);
    expect(entries.map((entry) => entry.errorType)).toEqual([
      "string",
      "Error",
    ]);
    expect(lines.join("\n")).not.toContain("private thrown value");
    expect(lines.join("\n")).not.toContain("private exception");
    await app.close();
  });

  it("caches Actual sign-in methods briefly without caching the HTTP response", async () => {
    const readActualLoginMethods = vi.fn(async () => ({
      bootstrapped: true,
      loginMethod: "password" as const,
      availableLoginMethods: [
        { method: "password" as const, displayName: "Password", active: true },
      ],
    }));
    const app = await buildApiApp(
      {
        ping: vi.fn<() => Promise<void>>(),
        close: vi.fn<() => Promise<void>>(),
      },
      { readActualLoginMethods },
    );

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/auth/methods",
    });

    expect(response.statusCode).toBe(200);
    expect(response.headers["cache-control"]).toBe("no-store");
    expect(response.json()).toEqual({
      bootstrapped: true,
      loginMethod: "password",
      availableLoginMethods: [
        { method: "password", displayName: "Password", active: true },
      ],
    });
    const cachedResponse = await app.inject({
      method: "GET",
      url: "/api/v1/auth/methods",
    });
    expect(cachedResponse.statusCode).toBe(200);
    expect(cachedResponse.headers["cache-control"]).toBe("no-store");
    expect(readActualLoginMethods).toHaveBeenCalledOnce();
    await app.close();
  });

  it("rate limits repeated sign-in method checks by client address", async () => {
    const readActualLoginMethods = vi.fn(async () => ({
      bootstrapped: true,
      loginMethod: "password" as const,
      availableLoginMethods: [],
    }));
    const app = await buildApiApp(
      {
        ping: vi.fn<() => Promise<void>>(),
        close: vi.fn<() => Promise<void>>(),
      },
      { readActualLoginMethods },
    );

    let lastResponse;
    for (let attempt = 0; attempt < 61; attempt += 1) {
      lastResponse = await app.inject({
        method: "GET",
        url: "/api/v1/auth/methods",
        remoteAddress: "192.0.2.10",
      });
    }

    expect(lastResponse?.statusCode).toBe(429);
    expect(lastResponse?.headers["retry-after"]).toBe("60");
    expect(lastResponse?.headers["cache-control"]).toBe("no-store");
    expect(lastResponse?.json()).toEqual({
      error: {
        code: "rate_limited",
        message: "Too many sign-in checks. Try again shortly.",
      },
    });
    expect(readActualLoginMethods).toHaveBeenCalledOnce();
    await app.close();
  });

  it("returns one safe response when Actual sign-in discovery is unavailable", async () => {
    const app = await buildApiApp(
      {
        ping: vi.fn<() => Promise<void>>(),
        close: vi.fn<() => Promise<void>>(),
      },
      {
        readActualLoginMethods: vi.fn(async () => {
          throw new Error("private Actual host details");
        }),
      },
    );

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/auth/methods",
    });

    expect(response.statusCode).toBe(503);
    expect(response.headers["cache-control"]).toBe("no-store");
    expect(response.json()).toEqual({
      error: {
        code: "actual_unavailable",
        message:
          "Could not check the Actual sign-in method. Check the server connection.",
      },
    });
    expect(response.body).not.toContain("private Actual host details");
    await app.close();
  });

  it("reports discovery unavailable when the Actual integration is not configured", async () => {
    const app = await buildApiApp({
      ping: vi.fn<() => Promise<void>>(),
      close: vi.fn<() => Promise<void>>(),
    });

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/auth/methods",
    });

    expect(response.statusCode).toBe(503);
    expect(response.headers["cache-control"]).toBe("no-store");
    await app.close();
  });

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

  it("reports a safe Actual credential problem from the readiness check", async () => {
    const app = await buildApiApp(
      {
        ping: vi.fn<() => Promise<void>>(),
        close: vi.fn<() => Promise<void>>(),
      },
      {
        actualReadiness: () => ({
          ready: false,
          message:
            "Actual rejected ACTUAL_SESSION_TOKEN. Replace the token and restart Tandem.",
        }),
      },
    );

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/health/ready",
    });

    expect(response.statusCode).toBe(503);
    expect(response.json()).toEqual({
      status: "not_ready",
      message:
        "Actual rejected ACTUAL_SESSION_TOKEN. Replace the token and restart Tandem.",
    });
    await app.close();
  });

  it("returns not ready without a message when Actual is unavailable", async () => {
    const app = await buildApiApp(
      {
        ping: vi.fn<() => Promise<void>>(),
        close: vi.fn<() => Promise<void>>(),
      },
      { actualReadiness: () => ({ ready: false }) },
    );

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/health/ready",
    });

    expect(response.statusCode).toBe(503);
    expect(response.json()).toEqual({ status: "not_ready" });
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
    expect(openApiDocument.paths?.["/api/v1/auth/login"]).toBeDefined();
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

  it("signs in through Actual and returns a device credential without cache", async () => {
    const auditLog = vi.spyOn(console, "info").mockImplementation(() => {});
    const device = createDevice();
    const createSignedInDevice = vi.fn(
      async ({
        principalId,
        budgetIds,
        deviceName,
      }: {
        principalId: string;
        budgetIds: string[];
        deviceName: string;
      }) => ({
        ...device,
        principalId,
        budgetIds,
        name: deviceName,
        accessToken: testAccessToken,
      }),
    );
    const auth = createDeviceAuth({ createDevice: createSignedInDevice });
    const signInWithActualPassword = vi.fn(async (password: string) => {
      if (password !== "synthetic-password")
        throw new ActualPasswordRejectedError();
      return {
        principalId: "actual-user:synthetic",
        sessionToken: "server-side-actual-session-token",
        budgetIds: ["synthetic-budget"],
      };
    });
    const persistActualSessionToken = vi.fn(async () => {});
    const app = await buildApiApp(
      { ping: vi.fn(), close: vi.fn() },
      { deviceAuth: auth, signInWithActualPassword, persistActualSessionToken },
    );

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/auth/login",
      payload: {
        password: "synthetic-password",
        deviceName: "Synthetic phone",
      },
    });

    expect(response.statusCode).toBe(201);
    expect(response.headers["cache-control"]).toBe("no-store");
    expect(response.json()).toMatchObject({
      deviceId: testDeviceId,
      principalId: "actual-user:synthetic",
      name: "Synthetic phone",
      budgetIds: ["synthetic-budget"],
      accessToken: testAccessToken,
      tokenType: "Bearer",
    });
    expect(signInWithActualPassword).toHaveBeenCalledWith("synthetic-password");
    expect(persistActualSessionToken).toHaveBeenCalledWith(
      "actual-user:synthetic",
      "server-side-actual-session-token",
    );
    expect(createSignedInDevice).toHaveBeenCalledWith({
      principalId: "actual-user:synthetic",
      budgetIds: ["synthetic-budget"],
      deviceName: "Synthetic phone",
    });

    const invalid = await app.inject({
      method: "POST",
      url: "/api/v1/auth/login",
      payload: { password: "wrong-password", deviceName: "Synthetic phone" },
    });
    expect(invalid.statusCode).toBe(401);
    expect(invalid.json()).toMatchObject({
      error: { code: "invalid_credentials" },
    });

    const invalidBody = await app.inject({
      method: "POST",
      url: "/api/v1/auth/login",
      payload: { password: "", deviceName: "Synthetic phone" },
    });
    expect(invalidBody.statusCode).toBe(400);
    const auditText = auditLog.mock.calls.flat().join(" ");
    expect(auditText).not.toContain("synthetic-password");
    expect(auditText).not.toContain("server-side-actual-session-token");
    expect(auditText).not.toContain(testAccessToken);
    auditLog.mockRestore();
    await app.close();
  });

  it("returns safe errors when Actual or device creation cannot complete sign-in", async () => {
    const login = () =>
      ({
        method: "POST" as const,
        url: "/api/v1/auth/login",
        payload: {
          password: "synthetic-password",
          deviceName: "Synthetic phone",
        },
      }) as const;
    const database = () => ({ ping: vi.fn(), close: vi.fn() });

    const unconfigured = await buildApiApp(database(), {
      deviceAuth: createDeviceAuth(),
    });
    const noActual = await unconfigured.inject(login());
    expect(noActual.statusCode).toBe(503);
    expect(noActual.json().error.code).toBe("actual_unavailable");
    await unconfigured.close();

    const unavailable = await buildApiApp(database(), {
      deviceAuth: createDeviceAuth(),
      signInWithActualPassword: async () => {
        throw new ActualPasswordUnavailableError();
      },
    });
    const noConnection = await unavailable.inject(login());
    expect(noConnection.statusCode).toBe(503);
    expect(noConnection.json().error.code).toBe("actual_unavailable");
    expect(noConnection.body).not.toContain("Actual sign-in is unavailable");
    await unavailable.close();

    const failedProvider = await buildApiApp(database(), {
      deviceAuth: createDeviceAuth(),
      signInWithActualPassword: async () => {
        throw new Error("private credential store failure");
      },
    });
    const providerFailure = await failedProvider.inject(login());
    expect(providerFailure.statusCode).toBe(500);
    expect(providerFailure.json().error).toEqual({
      code: "internal_error",
      message: "Could not complete sign-in. Try again shortly.",
    });
    expect(providerFailure.body).not.toContain(
      "private credential store failure",
    );
    await failedProvider.close();

    const rejectedDevice = await buildApiApp(database(), {
      deviceAuth: createDeviceAuth({
        createDevice: vi.fn(async () => {
          throw new DeviceAuthInputError("invalid local device input");
        }),
      }),
      signInWithActualPassword: async () => ({
        principalId: "actual-user:synthetic",
        sessionToken: "actual-session-token",
        budgetIds: ["synthetic-budget"],
      }),
    });
    const invalidDevice = await rejectedDevice.inject(login());
    expect(invalidDevice.statusCode).toBe(400);
    expect(invalidDevice.json().error).toMatchObject({
      code: "invalid_request",
      message: "invalid local device input",
    });
    await rejectedDevice.close();

    const noDevice = await buildApiApp(database(), {
      deviceAuth: createDeviceAuth({
        createDevice: vi.fn(async () => undefined),
      }),
      signInWithActualPassword: async () => ({
        principalId: "actual-user:synthetic",
        sessionToken: "actual-session-token",
        budgetIds: ["synthetic-budget"],
      }),
    });
    const notCreated = await noDevice.inject(login());
    expect(notCreated.statusCode).toBe(503);
    expect(notCreated.json().error.code).toBe("actual_unavailable");
    await noDevice.close();
  });

  it("rate-limits Actual sign-in attempts by client address", async () => {
    const signInWithActualPassword = vi.fn(async () => {
      throw new ActualPasswordRejectedError();
    });
    const app = await buildApiApp(
      { ping: vi.fn(), close: vi.fn() },
      { deviceAuth: createDeviceAuth(), signInWithActualPassword },
    );
    const request = () =>
      app.inject({
        method: "POST",
        url: "/api/v1/auth/login",
        payload: {
          password: "synthetic-password",
          deviceName: "Synthetic phone",
        },
      });

    for (let attempt = 0; attempt < 10; attempt += 1) {
      expect((await request()).statusCode).toBe(401);
    }
    const limited = await request();
    expect(limited.statusCode).toBe(429);
    expect(limited.headers["retry-after"]).toBe("60");
    expect(limited.json()).toEqual({
      error: {
        code: "rate_limited",
        message: "Too many sign-in attempts. Try again shortly.",
      },
    });
    expect(signInWithActualPassword).toHaveBeenCalledTimes(10);
    await app.close();
  });

  it("binds OpenID code exchange to the starting app's PKCE verifier", async () => {
    const codeVerifier = "v".repeat(64);
    const wrongVerifier = "w".repeat(64);
    const codeChallenge = createHash("sha256")
      .update(codeVerifier, "ascii")
      .digest("base64url");
    const startOpenIdSignIn = vi.fn<(returnUrl: string) => Promise<string>>(
      async () => "https://idp.example/login",
    );
    const actualLogin = {
      startOpenIdSignIn,
      identifySessionToken: vi.fn(async () => "actual-user:synthetic"),
    } as unknown as ActualPasswordLogin;
    const openIdLogin = new ActualOpenIdLogin(
      actualLogin,
      "https://tandem.example",
      async (grant) => ({ ...grant, budgetIds: ["synthetic-budget"] }),
    );
    const device = createDevice({
      principalId: "actual-user:synthetic",
      budgetIds: ["synthetic-budget"],
    });
    const createSignedInDevice = vi.fn(
      async (input: {
        principalId: string;
        budgetIds: string[];
        deviceName: string;
      }) => ({
        ...device,
        principalId: input.principalId,
        budgetIds: input.budgetIds,
        name: input.deviceName,
        accessToken: testAccessToken,
      }),
    );
    const auth = createDeviceAuth({ createDevice: createSignedInDevice });
    const persistActualSessionToken = vi.fn(async () => {});
    const app = await buildApiApp(
      { ping: vi.fn(), close: vi.fn() },
      {
        deviceAuth: auth,
        actualOpenIdLogin: openIdLogin,
        persistActualSessionToken,
      },
    );

    const started = await app.inject({
      method: "POST",
      url: "/api/v1/auth/openid/start",
      payload: { deviceName: "Synthetic phone", codeChallenge },
    });
    const actualReturnUrl = startOpenIdSignIn.mock.calls[0]?.[0] ?? "";
    const state = new URL(actualReturnUrl).pathname.split("/").at(-2);
    const callback = await app.inject({
      method: "GET",
      url: `/api/v1/auth/actual-return/${state}/openid-cb?token=actual-secret`,
    });
    const tandemReturn = new URL(callback.headers.location ?? "");
    const completionCode = tandemReturn.searchParams.get("code") ?? "";
    const wrongVerifierResponse = await app.inject({
      method: "POST",
      url: "/api/v1/auth/openid/complete",
      payload: { code: completionCode, codeVerifier: wrongVerifier },
    });
    const completed = await app.inject({
      method: "POST",
      url: "/api/v1/auth/openid/complete",
      payload: { code: completionCode, codeVerifier },
    });
    const replayed = await app.inject({
      method: "POST",
      url: "/api/v1/auth/openid/complete",
      payload: { code: completionCode, codeVerifier },
    });

    expect(started.statusCode).toBe(200);
    expect(started.json()).toEqual({
      redirectUrl: "https://idp.example/login",
    });
    expect(actualReturnUrl).toMatch(
      /\/api\/v1\/auth\/actual-return\/[A-Za-z0-9_-]{43}\/openid-cb$/u,
    );
    expect(callback.statusCode).toBe(302);
    expect(callback.headers.location).toMatch(
      /^tandem:\/\/signin\/complete\?code=[A-Za-z0-9_-]{43}$/u,
    );
    expect(callback.headers["cache-control"]).toBe("no-store");
    expect(callback.body).not.toContain("actual-secret");
    expect(tandemReturn.protocol).toBe("tandem:");
    expect(completionCode).toMatch(/^[A-Za-z0-9_-]{43}$/u);
    expect(callback.headers.location).not.toContain(codeVerifier);
    expect(wrongVerifierResponse.statusCode).toBe(401);
    expect(completed.statusCode).toBe(201);
    expect(completed.json()).toMatchObject({
      principalId: "actual-user:synthetic",
      budgetIds: ["synthetic-budget"],
      accessToken: testAccessToken,
    });
    expect(replayed.statusCode).toBe(401);
    expect(persistActualSessionToken).toHaveBeenCalledWith(
      "actual-user:synthetic",
      "actual-secret",
    );
    expect(persistActualSessionToken).toHaveBeenCalledOnce();
    expect(createSignedInDevice).toHaveBeenCalledOnce();
    await app.close();
  });

  it("handles invalid, expired, rate-limited, and failed OpenID sign-in steps", async () => {
    const completion = {
      deviceName: "Synthetic phone",
      grant: {
        principalId: "actual-user:synthetic",
        sessionToken: "server-side-session-token",
        budgetIds: ["synthetic-budget"],
      },
    };
    const codeChallenge = "H".repeat(43);
    const codeVerifier = "V".repeat(64);
    const openIdLogin = {
      start: vi.fn(async () => {
        throw new Error("private identity provider error");
      }),
      completeCallback: vi.fn(async (_state: string, token: string) => {
        if (token === "expired") return undefined;
        throw new Error("private Actual callback error");
      }),
      consume: vi.fn((code: string) =>
        code === "E".repeat(43) ? undefined : completion,
      ),
    } as unknown as ActualOpenIdLogin;
    const app = await buildApiApp(
      { ping: vi.fn(), close: vi.fn() },
      {
        deviceAuth: createDeviceAuth({
          createDevice: vi.fn(async () => {
            throw new Error("private device store error");
          }),
        }),
        actualOpenIdLogin: openIdLogin,
      },
    );

    const invalidStart = await app.inject({
      method: "POST",
      url: "/api/v1/auth/openid/start",
      payload: { deviceName: " " },
    });
    expect(invalidStart.statusCode).toBe(400);
    const missingChallengeStart = await app.inject({
      method: "POST",
      url: "/api/v1/auth/openid/start",
      payload: { deviceName: "Synthetic phone" },
    });
    expect(missingChallengeStart.statusCode).toBe(400);
    const invalidChallengeStart = await app.inject({
      method: "POST",
      url: "/api/v1/auth/openid/start",
      payload: { deviceName: "Synthetic phone", codeChallenge: "short" },
    });
    expect(invalidChallengeStart.statusCode).toBe(400);

    const firstStart = await app.inject({
      method: "POST",
      url: "/api/v1/auth/openid/start",
      payload: { deviceName: "Synthetic phone", codeChallenge },
    });
    expect(firstStart.statusCode).toBe(503);
    expect(firstStart.body).not.toContain("private identity provider error");
    for (let attempt = 1; attempt < 10; attempt += 1) {
      expect(
        (
          await app.inject({
            method: "POST",
            url: "/api/v1/auth/openid/start",
            payload: { deviceName: "Synthetic phone", codeChallenge },
          })
        ).statusCode,
      ).toBe(503);
    }
    const limitedStart = await app.inject({
      method: "POST",
      url: "/api/v1/auth/openid/start",
      payload: { deviceName: "Synthetic phone", codeChallenge },
    });
    expect(limitedStart.statusCode).toBe(429);
    expect(limitedStart.headers["retry-after"]).toBe("60");

    const state = "S".repeat(43);
    const invalidCallback = await app.inject({
      method: "GET",
      url: `/api/v1/auth/actual-return/${state}/openid-cb`,
    });
    expect(invalidCallback.statusCode).toBe(400);
    const expiredCallback = await app.inject({
      method: "GET",
      url: `/api/v1/auth/actual-return/${state}/openid-cb?token=expired`,
    });
    expect(expiredCallback.statusCode).toBe(401);
    expect(expiredCallback.json().error.code).toBe("sign_in_expired");
    const failedCallback = await app.inject({
      method: "GET",
      url: `/api/v1/auth/actual-return/${state}/openid-cb?token=invalid`,
    });
    expect(failedCallback.statusCode).toBe(401);
    expect(failedCallback.body).not.toContain("private Actual callback error");

    const invalidCompletion = await app.inject({
      method: "POST",
      url: "/api/v1/auth/openid/complete",
      payload: { code: "", codeVerifier },
    });
    expect(invalidCompletion.statusCode).toBe(400);
    const missingVerifierCompletion = await app.inject({
      method: "POST",
      url: "/api/v1/auth/openid/complete",
      payload: { code: "C".repeat(43) },
    });
    expect(missingVerifierCompletion.statusCode).toBe(400);
    const invalidVerifierCompletion = await app.inject({
      method: "POST",
      url: "/api/v1/auth/openid/complete",
      payload: { code: "C".repeat(43), codeVerifier: "short" },
    });
    expect(invalidVerifierCompletion.statusCode).toBe(400);
    const expiredCompletion = await app.inject({
      method: "POST",
      url: "/api/v1/auth/openid/complete",
      payload: { code: "E".repeat(43), codeVerifier },
    });
    expect(expiredCompletion.statusCode).toBe(401);
    const failedCompletion = await app.inject({
      method: "POST",
      url: "/api/v1/auth/openid/complete",
      payload: { code: "C".repeat(43), codeVerifier },
    });
    expect(failedCompletion.statusCode).toBe(503);
    expect(failedCompletion.body).not.toContain("private device store error");
    await app.close();
  });

  it("warns once when sign-in arrives with forwarded addresses but no trusted proxy", async () => {
    const warning = vi
      .spyOn(console, "warn")
      .mockImplementation(() => undefined);
    const app = await buildApiApp(
      { ping: vi.fn(), close: vi.fn() },
      {
        deviceAuth: createDeviceAuth(),
        signInWithActualPassword: async () => {
          throw new ActualPasswordRejectedError();
        },
      },
    );

    try {
      for (let attempt = 0; attempt < 2; attempt += 1) {
        const response = await app.inject({
          method: "POST",
          url: "/api/v1/auth/login",
          headers: { "x-forwarded-for": `198.51.100.${attempt + 1}` },
          payload: {
            password: "synthetic-password",
            deviceName: "Synthetic phone",
          },
        });
        expect(response.statusCode).toBe(401);
      }
      expect(warning).toHaveBeenCalledOnce();
      expect(warning).toHaveBeenCalledWith(
        expect.stringContaining("TRUST_PROXY is unset"),
      );
    } finally {
      await app.close();
      warning.mockRestore();
    }
  });

  it("authenticates, scopes device listing, and returns 404 for invisible revocations", async () => {
    const auditLog = vi.spyOn(console, "info").mockImplementation(() => {});
    const device = createDevice({
      lastSeenAt: new Date("2026-11-01T01:00:00.000Z"),
    });
    const neverSeenDevice = createDevice({
      deviceId: "d2d791c1-d2bc-4e94-9f39-2d28d45e7c10",
    });
    const auth = createDeviceAuth({
      authenticate: vi.fn(async (token?: string) => {
        if (token === testAccessToken) return device;
        if (token === "B".repeat(43)) return neverSeenDevice;
        return undefined;
      }),
      listDevicesForDevice: vi.fn(async () => [device]),
      revokeDeviceForDevice: vi.fn(
        async (_device: DeviceRecord, id: string) => id === testDeviceId,
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
    expect(invalidToken.json().error.code).toBe("invalid_device_credential");

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

    const neverSeen = await app.inject({
      method: "GET",
      url: "/api/v1/auth/me",
      headers: { authorization: `Bearer ${"B".repeat(43)}` },
    });
    expect(neverSeen.statusCode).toBe(200);
    expect(neverSeen.json().lastSeenAt).toBeNull();

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
      ],
    });

    const invisibleRevoke = await app.inject({
      method: "DELETE",
      url: "/api/v1/devices/c2d791c1-d2bc-4e94-9f39-2d28d45e7c11",
      headers: { authorization: `Bearer ${testAccessToken}` },
    });
    expect(invisibleRevoke.statusCode).toBe(404);

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
    const events = auditLog.mock.calls.map(([line]) =>
      JSON.parse(line as string),
    );
    expect(events).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          event: "device.auth",
          outcome: "rejected",
          reason: "invalid_device_credential",
        }),
        expect.objectContaining({ event: "device.revoke", outcome: "success" }),
        expect.objectContaining({
          event: "device.revoke",
          outcome: "rejected",
          reason: "not_found_or_out_of_scope",
        }),
      ]),
    );
    expect(auditLog.mock.calls.flat().join(" ")).not.toContain(testAccessToken);
    expect(auditLog.mock.calls.flat().join(" ")).not.toContain("B".repeat(43));
    auditLog.mockRestore();
    await app.close();
  });

  it("rejects the same device credential after online sign-out revokes it", async () => {
    const device = createDevice();
    let revoked = false;
    const auth = createDeviceAuth({
      authenticate: vi.fn(async (token?: string) =>
        token === testAccessToken && !revoked ? device : undefined,
      ),
      revokeDeviceForDevice: vi.fn(
        async (_device: DeviceRecord, id: string) => {
          if (id !== testDeviceId || revoked) return false;
          revoked = true;
          return true;
        },
      ),
    });
    const app = await buildApiApp(
      { ping: vi.fn(), close: vi.fn() },
      { deviceAuth: auth },
    );

    try {
      const authorization = { authorization: `Bearer ${testAccessToken}` };
      const beforeSignOut = await app.inject({
        method: "GET",
        url: "/api/v1/auth/me",
        headers: authorization,
      });
      expect(beforeSignOut.statusCode).toBe(200);

      const signOut = await app.inject({
        method: "DELETE",
        url: `/api/v1/devices/${testDeviceId}`,
        headers: authorization,
      });
      expect(signOut.statusCode).toBe(204);

      const staleCredential = await app.inject({
        method: "GET",
        url: "/api/v1/auth/me",
        headers: authorization,
      });
      expect(staleCredential.statusCode).toBe(401);
      expect(staleCredential.json().error.code).toBe(
        "invalid_device_credential",
      );
      expect(auth.revokeDeviceForDevice).toHaveBeenCalledWith(
        device,
        testDeviceId,
      );
    } finally {
      await app.close();
    }
  });
});
