import { describe, expect, it, vi } from "vitest";
import {
  ActualPasswordLogin,
  ActualPasswordRejectedError,
  ActualPasswordUnavailableError,
} from "../src/auth/actual-password-login.js";

function jsonResponse(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "content-type": "application/json" },
  });
}

describe("Actual password sign-in", () => {
  it("validates the issued token and creates an opaque Actual user identity", async () => {
    const fetchRequest = vi.fn<typeof fetch>(async (input, init) => {
      const endpoint = new URL(input.toString());
      if (endpoint.pathname === "/account/login") {
        expect(JSON.parse(String(init?.body))).toEqual({
          loginMethod: "password",
          password: "synthetic-password",
        });
        return jsonResponse({
          status: "ok",
          data: { token: "synthetic-session" },
        });
      }
      expect(endpoint.pathname).toBe("/account/validate");
      expect(new Headers(init?.headers).get("X-ACTUAL-TOKEN")).toBe(
        "synthetic-session",
      );
      return jsonResponse({
        status: "ok",
        data: { userId: "owner@example.test" },
      });
    });
    const login = new ActualPasswordLogin(
      "https://actual.example",
      fetchRequest,
    );

    const grant = await login.signIn("synthetic-password");

    expect(grant).toMatchObject({
      principalId: expect.stringMatching(/^actual-user:[a-f0-9]{64}$/),
      sessionToken: "synthetic-session",
      budgetIds: [],
    });
    expect(grant.principalId).not.toContain("owner@example.test");
    expect(fetchRequest).toHaveBeenCalledTimes(2);
  });

  it("rejects an invalid password without returning Actual details", async () => {
    const login = new ActualPasswordLogin(
      "https://actual.example",
      vi.fn<typeof fetch>(async () => jsonResponse({ status: "error" }, 401)),
    );

    await expect(login.signIn("wrong-password")).rejects.toBeInstanceOf(
      ActualPasswordRejectedError,
    );
  });

  it("maps Actual's invalid-password HTTP 400 response to rejected credentials", async () => {
    const login = new ActualPasswordLogin(
      "https://actual.example",
      vi.fn<typeof fetch>(async () =>
        jsonResponse({ status: "error", reason: "invalid-password" }, 400),
      ),
    );

    await expect(login.signIn("wrong-password")).rejects.toBeInstanceOf(
      ActualPasswordRejectedError,
    );
  });

  it("does not treat unrelated Actual HTTP 400 responses as bad credentials", async () => {
    const login = new ActualPasswordLogin(
      "https://actual.example",
      vi.fn<typeof fetch>(async () =>
        jsonResponse({ status: "error", reason: "invalid-token" }, 400),
      ),
    );

    await expect(login.signIn("synthetic-password")).rejects.toBeInstanceOf(
      ActualPasswordUnavailableError,
    );
  });

  it("fails closed when an Actual HTTP 400 response is not valid JSON", async () => {
    const login = new ActualPasswordLogin(
      "https://actual.example",
      vi.fn<typeof fetch>(
        async () => new Response("not-json", { status: 400 }),
      ),
    );

    await expect(login.signIn("synthetic-password")).rejects.toBeInstanceOf(
      ActualPasswordUnavailableError,
    );
  });

  it.each(["", "x".repeat(1_025)])(
    "rejects a password outside the supported length: %s",
    async (password) => {
      const fetchRequest = vi.fn<typeof fetch>();
      const login = new ActualPasswordLogin(
        "https://actual.example",
        fetchRequest,
      );

      await expect(login.signIn(password)).rejects.toBeInstanceOf(
        ActualPasswordRejectedError,
      );
      expect(fetchRequest).not.toHaveBeenCalled();
    },
  );

  it.each([
    { status: 403, error: ActualPasswordRejectedError },
    { status: 502, error: ActualPasswordUnavailableError },
  ])("maps login HTTP $status to a safe error", async ({ status, error }) => {
    const login = new ActualPasswordLogin(
      "https://actual.example",
      vi.fn<typeof fetch>(async () => jsonResponse({}, status)),
    );

    await expect(login.signIn("synthetic-password")).rejects.toBeInstanceOf(
      error,
    );
  });

  it("fails closed when the login response is not valid JSON", async () => {
    const login = new ActualPasswordLogin(
      "https://actual.example",
      vi.fn<typeof fetch>(async () => new Response("not-json")),
    );

    await expect(login.signIn("synthetic-password")).rejects.toBeInstanceOf(
      ActualPasswordUnavailableError,
    );
  });

  it("accepts the top-level token response shape", async () => {
    const fetchRequest = vi.fn<typeof fetch>(async (input) => {
      const path = new URL(input.toString()).pathname;
      return path === "/account/login"
        ? jsonResponse({ token: "synthetic-session" })
        : jsonResponse({ status: "ok", data: { userId: "owner" } });
    });
    const login = new ActualPasswordLogin(
      "https://actual.example",
      fetchRequest,
    );

    await expect(login.signIn("synthetic-password")).resolves.toMatchObject({
      sessionToken: "synthetic-session",
      principalId: expect.stringMatching(/^actual-user:[a-f0-9]{64}$/),
    });
  });

  it.each([
    "not-an-object",
    { data: [] },
    { token: 42 },
    { token: "" },
    { token: "x".repeat(4_097) },
  ])("rejects an invalid session-token payload", async (payload) => {
    const login = new ActualPasswordLogin(
      "https://actual.example",
      vi.fn<typeof fetch>(async () => jsonResponse(payload)),
    );

    await expect(login.signIn("synthetic-password")).rejects.toBeInstanceOf(
      ActualPasswordRejectedError,
    );
  });

  it("fails closed when the Actual account endpoint does not return an identity", async () => {
    const login = new ActualPasswordLogin(
      "https://actual.example",
      vi.fn<typeof fetch>(async () => jsonResponse({ status: "ok", data: {} })),
    );

    await expect(login.signIn("synthetic-password")).rejects.toBeInstanceOf(
      ActualPasswordRejectedError,
    );
  });

  it("maps upstream network failures to a safe unavailable error", async () => {
    const login = new ActualPasswordLogin(
      "https://actual.example",
      vi.fn<typeof fetch>(async () => {
        throw new Error("private upstream address");
      }),
    );

    await expect(login.signIn("synthetic-password")).rejects.toBeInstanceOf(
      ActualPasswordUnavailableError,
    );
  });

  it("rejects empty or oversized session tokens before contacting Actual", async () => {
    const fetchRequest = vi.fn<typeof fetch>();
    const login = new ActualPasswordLogin(
      "https://actual.example",
      fetchRequest,
    );

    await expect(login.identifySessionToken("")).rejects.toBeInstanceOf(
      ActualPasswordRejectedError,
    );
    await expect(
      login.identifySessionToken("x".repeat(4_097)),
    ).rejects.toBeInstanceOf(ActualPasswordRejectedError);
    expect(fetchRequest).not.toHaveBeenCalled();
  });

  it.each([
    { status: 401, error: ActualPasswordRejectedError },
    { status: 403, error: ActualPasswordRejectedError },
    { status: 503, error: ActualPasswordUnavailableError },
  ])(
    "maps validation HTTP $status to a safe error",
    async ({ status, error }) => {
      const fetchRequest = vi.fn<typeof fetch>(async (input) => {
        const path = new URL(input.toString()).pathname;
        return path === "/account/login"
          ? jsonResponse({ token: "synthetic-session" })
          : jsonResponse({}, status);
      });
      const login = new ActualPasswordLogin(
        "https://actual.example",
        fetchRequest,
      );

      await expect(login.signIn("synthetic-password")).rejects.toBeInstanceOf(
        error,
      );
    },
  );

  it("maps validation network and malformed JSON failures to unavailable", async () => {
    const networkFailure = new ActualPasswordLogin(
      "https://actual.example",
      vi.fn<typeof fetch>(async (input) => {
        if (new URL(input.toString()).pathname === "/account/login") {
          return jsonResponse({ token: "synthetic-session" });
        }
        throw new Error("private validation endpoint");
      }),
    );
    const malformedJson = new ActualPasswordLogin(
      "https://actual.example",
      vi.fn<typeof fetch>(async (input) =>
        new URL(input.toString()).pathname === "/account/login"
          ? jsonResponse({ token: "synthetic-session" })
          : new Response("not-json"),
      ),
    );

    await expect(
      networkFailure.signIn("synthetic-password"),
    ).rejects.toBeInstanceOf(ActualPasswordUnavailableError);
    await expect(
      malformedJson.signIn("synthetic-password"),
    ).rejects.toBeInstanceOf(ActualPasswordUnavailableError);
  });

  it.each([
    "not-an-object",
    { status: "error", data: { userId: "owner" } },
    { status: "ok", data: [] },
    { status: "ok", data: { userId: " " } },
    { status: "ok", data: { userId: 42 } },
    { status: "ok", data: { userId: "x".repeat(201) } },
  ])("rejects an invalid validated-user response", async (payload) => {
    const fetchRequest = vi.fn<typeof fetch>(async (input) =>
      new URL(input.toString()).pathname === "/account/login"
        ? jsonResponse({ token: "synthetic-session" })
        : jsonResponse(payload),
    );
    const login = new ActualPasswordLogin(
      "https://actual.example",
      fetchRequest,
    );

    await expect(login.signIn("synthetic-password")).rejects.toBeInstanceOf(
      ActualPasswordRejectedError,
    );
  });

  it("starts OpenID sign-in with nested Actual response data", async () => {
    const fetchRequest = vi.fn<typeof fetch>(async () =>
      jsonResponse({ data: { returnUrl: "https://idp.example/authorize" } }),
    );
    const login = new ActualPasswordLogin(
      "https://actual.example",
      fetchRequest,
    );

    await expect(
      login.startOpenIdSignIn("https://tandem.example/return"),
    ).resolves.toBe("https://idp.example/authorize");
  });

  it("accepts an HTTP OpenID redirect for a private server", async () => {
    const login = new ActualPasswordLogin(
      "https://actual.example",
      vi.fn<typeof fetch>(async () =>
        jsonResponse({ returnUrl: "http://idp.internal/authorize" }),
      ),
    );

    await expect(
      login.startOpenIdSignIn("https://tandem.example/return"),
    ).resolves.toBe("http://idp.internal/authorize");
  });

  it.each([
    { label: "non-object payload", payload: "not-an-object" },
    { label: "non-object data", payload: { data: [] } },
    { label: "non-string URL", payload: { returnUrl: 42 } },
    { label: "oversized URL", payload: { returnUrl: "x".repeat(4_097) } },
    {
      label: "unsupported protocol",
      payload: { returnUrl: "javascript:run()" },
    },
    {
      label: "username in URL",
      payload: { returnUrl: "https://user@idp.example" },
    },
    {
      label: "password in URL",
      payload: { returnUrl: "https://:secret@idp.example" },
    },
    { label: "malformed URL", payload: { returnUrl: "https://[" } },
  ])("rejects an invalid OpenID redirect: $label", async ({ payload }) => {
    const login = new ActualPasswordLogin(
      "https://actual.example",
      vi.fn<typeof fetch>(async () => jsonResponse(payload)),
    );

    await expect(
      login.startOpenIdSignIn("https://tandem.example/return"),
    ).rejects.toBeInstanceOf(ActualPasswordRejectedError);
  });

  it("maps OpenID upstream status, network, and JSON failures safely", async () => {
    const forbidden = new ActualPasswordLogin(
      "https://actual.example",
      vi.fn<typeof fetch>(async () => jsonResponse({}, 403)),
    );
    const unreachable = new ActualPasswordLogin(
      "https://actual.example",
      vi.fn<typeof fetch>(async () => {
        throw new Error("private login endpoint");
      }),
    );
    const malformedJson = new ActualPasswordLogin(
      "https://actual.example",
      vi.fn<typeof fetch>(async () => new Response("not-json")),
    );

    await expect(
      forbidden.startOpenIdSignIn("https://tandem.example/return"),
    ).rejects.toBeInstanceOf(ActualPasswordUnavailableError);
    await expect(
      unreachable.startOpenIdSignIn("https://tandem.example/return"),
    ).rejects.toBeInstanceOf(ActualPasswordUnavailableError);
    await expect(
      malformedJson.startOpenIdSignIn("https://tandem.example/return"),
    ).rejects.toBeInstanceOf(ActualPasswordUnavailableError);
  });
});
