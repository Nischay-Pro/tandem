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
});
