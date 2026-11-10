import { describe, expect, it, vi } from "vitest";
import { ActualOpenIdLogin } from "../src/auth/actual-openid-login.js";
import { ActualPasswordLogin } from "../src/auth/actual-password-login.js";

function jsonResponse(value: unknown): Response {
  return new Response(JSON.stringify(value), {
    status: 200,
    headers: { "content-type": "application/json" },
  });
}

describe("Actual OpenID sign-in handoff", () => {
  it("uses the default clock and code generator for a complete handoff", async () => {
    const startOpenIdSignIn = vi.fn(async (returnUrl: string) =>
      new URL(returnUrl).protocol === "https:"
        ? "https://idp.example/authorize"
        : "https://idp.example/insecure",
    );
    const passwordLogin = {
      startOpenIdSignIn,
      identifySessionToken: vi.fn(async () => "actual-user:synthetic"),
    } as unknown as ActualPasswordLogin;
    const prepareGrant = vi.fn(async (grant) => grant);
    const openId = new ActualOpenIdLogin(
      passwordLogin,
      "https://tandem.example",
      prepareGrant,
    );

    const started = await openId.start("Synthetic phone");
    const callbackUrl = new URL(startOpenIdSignIn.mock.calls[0]?.[0] ?? "");
    const state = callbackUrl.pathname.split("/").at(-2);
    const returnUrl = await openId.completeCallback(
      state ?? "",
      "synthetic-session-token",
    );
    const code = new URL(returnUrl ?? "").searchParams.get("code");

    expect(started.redirectUrl).toBe("https://idp.example/authorize");
    expect(state).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(code).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(prepareGrant).toHaveBeenCalledWith({
      principalId: "actual-user:synthetic",
      sessionToken: "synthetic-session-token",
      budgetIds: [],
    });
    expect(openId.consume(code ?? "")).toMatchObject({
      deviceName: "Synthetic phone",
      grant: { principalId: "actual-user:synthetic" },
    });
  });

  it.each(["", "  ", "x".repeat(101)])(
    "rejects an invalid device name: %s",
    async (deviceName) => {
      const startOpenIdSignIn = vi.fn(async (returnUrl: string) =>
        new URL(returnUrl).protocol === "https:"
          ? "https://idp.example/authorize"
          : "https://idp.example/insecure",
      );
      const passwordLogin = {
        startOpenIdSignIn,
      } as unknown as ActualPasswordLogin;
      const openId = new ActualOpenIdLogin(
        passwordLogin,
        "https://tandem.example",
        async (grant) => grant,
      );

      await expect(openId.start(deviceName)).rejects.toThrow(
        "Device name is invalid",
      );
      expect(startOpenIdSignIn).not.toHaveBeenCalled();
    },
  );

  it("refuses new pending logins when the state store is full", async () => {
    const startOpenIdSignIn = vi.fn(async (returnUrl: string) =>
      new URL(returnUrl).protocol === "https:"
        ? "https://idp.example/authorize"
        : "https://idp.example/insecure",
    );
    const passwordLogin = {
      startOpenIdSignIn,
    } as unknown as ActualPasswordLogin;
    let codeIndex = 0;
    const openId = new ActualOpenIdLogin(
      passwordLogin,
      "https://tandem.example",
      async (grant) => grant,
      () => 0,
      () => String(++codeIndex).padStart(43, "0"),
    );

    for (let index = 0; index < 1_000; index += 1) {
      await openId.start("Synthetic phone");
    }

    await expect(openId.start("Synthetic phone")).rejects.toThrow(
      "OpenID sign-in is temporarily busy",
    );
    expect(startOpenIdSignIn).toHaveBeenCalledTimes(1_000);
  });

  it("keeps the Actual token server-side and exchanges a one-use Tandem code", async () => {
    const requests: Array<{ url: string; body?: string }> = [];
    const fetchRequest = vi.fn<typeof fetch>(async (input, init) => {
      const url = input.toString();
      requests.push({
        url,
        ...(init?.body == null ? {} : { body: init.body.toString() }),
      });
      if (url.endsWith("/account/login")) {
        return jsonResponse({ returnUrl: "https://idp.example/authorize" });
      }
      return jsonResponse({ status: "ok", data: { userId: "oidc-user" } });
    });
    let codeIndex = 0;
    const login = new ActualPasswordLogin(
      "https://actual.example",
      fetchRequest,
    );
    const openId = new ActualOpenIdLogin(
      login,
      "https://actual.example",
      async (grant) => ({ ...grant, budgetIds: ["budget-a"] }),
      () => 100,
      () => {
        codeIndex += 1;
        return String(codeIndex).padStart(43, "0");
      },
    );

    const started = await openId.start("Synthetic phone");
    const requestBody = JSON.parse(requests[0]?.body ?? "{}") as {
      loginMethod: string;
      returnUrl: string;
    };
    const callbackPath = new URL(requestBody.returnUrl).pathname;
    const state = callbackPath.split("/").at(-2);
    expect(started.redirectUrl).toBe("https://idp.example/authorize");
    expect(requestBody.loginMethod).toBe("openid");
    expect(requestBody.returnUrl).toBe(
      `https://actual.example/api/v1/auth/actual-return/${state}/openid-cb`,
    );

    const returnUrl = await openId.completeCallback(
      state ?? "",
      "synthetic-actual-session-token",
    );
    expect(returnUrl).toBe(
      "tandem://signin/complete?code=0000000000000000000000000000000000000000002",
    );
    expect(returnUrl).not.toContain("synthetic-actual-session-token");
    expect(requests[1]?.url).toBe("https://actual.example/account/validate");

    const completion = openId.consume(
      "0000000000000000000000000000000000000000002",
    );
    expect(completion).toMatchObject({
      deviceName: "Synthetic phone",
      grant: {
        principalId: expect.stringMatching(/^actual-user:[a-f0-9]{64}$/),
        sessionToken: "synthetic-actual-session-token",
        budgetIds: ["budget-a"],
      },
    });
    expect(
      openId.consume("0000000000000000000000000000000000000000002"),
    ).toBeUndefined();
  });

  it("expires an OpenID return state and rejects replay", async () => {
    const fetchRequest = vi.fn<typeof fetch>(async () =>
      jsonResponse({ returnUrl: "https://idp.example/authorize" }),
    );
    let now = 0;
    let codeIndex = 0;
    const login = new ActualPasswordLogin(
      "https://actual.example",
      fetchRequest,
    );
    const openId = new ActualOpenIdLogin(
      login,
      "https://actual.example",
      async (grant) => grant,
      () => now,
      () => {
        codeIndex += 1;
        return String(codeIndex).padStart(43, "0");
      },
    );
    await openId.start("Synthetic phone");
    now = 5 * 60_000;

    await expect(
      openId.completeCallback(
        "0000000000000000000000000000000000000000001",
        "token",
      ),
    ).resolves.toBeUndefined();
    expect(fetchRequest).toHaveBeenCalledOnce();
  });

  it("expires a completed handoff and rejects unknown state and code values", async () => {
    let now = 1_000;
    const fetchRequest = vi.fn<typeof fetch>(async (input) =>
      input.toString().endsWith("/account/login")
        ? jsonResponse({ returnUrl: "https://idp.example/authorize" })
        : jsonResponse({ status: "ok", data: { userId: "owner" } }),
    );
    let codeIndex = 0;
    const login = new ActualPasswordLogin(
      "https://actual.example",
      fetchRequest,
    );
    const openId = new ActualOpenIdLogin(
      login,
      "https://actual.example",
      async (grant) => grant,
      () => now,
      () => String(++codeIndex).padStart(43, "0"),
    );

    await expect(
      openId.completeCallback("Z".repeat(43), "unused-token"),
    ).resolves.toBeUndefined();
    expect(openId.consume("Z".repeat(43))).toBeUndefined();

    await openId.start("Synthetic phone");
    const request = JSON.parse(
      String(fetchRequest.mock.calls[0]?.[1]?.body),
    ) as { returnUrl: string };
    const state = new URL(request.returnUrl).pathname.split("/").at(-2);
    const returnUrl = await openId.completeCallback(
      state ?? "",
      "synthetic-session-token",
    );
    const code = new URL(returnUrl ?? "").searchParams.get("code");

    now += 90_000;
    expect(openId.consume(code ?? "")).toBeUndefined();
  });
});
