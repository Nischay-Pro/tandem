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
});
