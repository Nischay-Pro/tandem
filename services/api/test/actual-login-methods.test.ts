import { describe, expect, it, vi } from "vitest";
import { ActualLoginMethodsReader } from "../src/auth/actual-login-methods.js";

describe("ActualLoginMethodsReader", () => {
  it("returns the configured login method and safe provider labels", async () => {
    const fetchRequest = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          status: "ok",
          data: {
            bootstrapped: true,
            loginMethod: "openid",
            availableLoginMethods: [
              {
                method: "openid",
                active: 1,
                displayName: "Household sign-in\n",
                clientSecret: "must not be returned",
              },
              {
                method: "password",
                active: true,
                displayName: "p".repeat(100),
              },
              {
                method: "password",
                active: false,
                displayName: "\u007f",
              },
              { method: "password", active: false, displayName: 42 },
              null,
              { method: "header", active: 1, displayName: "Reverse proxy" },
            ],
            sessionToken: "must not be returned",
          },
        }),
        { status: 200, headers: { "content-type": "application/json" } },
      ),
    );

    const result = await new ActualLoginMethodsReader(
      "https://budget.example.com",
      fetchRequest,
    ).read();

    expect(result).toEqual({
      bootstrapped: true,
      loginMethod: "openid",
      availableLoginMethods: [
        { method: "openid", displayName: "Household sign-in", active: true },
        { method: "password", displayName: "p".repeat(80), active: true },
        { method: "password", displayName: "password", active: false },
        { method: "password", displayName: "password", active: false },
      ],
    });
    expect(fetchRequest).toHaveBeenCalledWith(
      new URL("https://budget.example.com/account/needs-bootstrap"),
      expect.objectContaining({ cache: "no-store" }),
    );
  });

  it("does not expose an Actual response body when the server rejects discovery", async () => {
    const fetchRequest = vi
      .fn<typeof fetch>()
      .mockResolvedValue(
        new Response("private server diagnostics", { status: 502 }),
      );

    await expect(
      new ActualLoginMethodsReader(
        "https://budget.example.com",
        fetchRequest,
      ).read(),
    ).rejects.toThrow("Could not read Actual login methods");
  });

  it("rejects an incomplete bootstrap response", async () => {
    const fetchRequest = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ status: "ok", data: {} }), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );

    await expect(
      new ActualLoginMethodsReader(
        "https://budget.example.com",
        fetchRequest,
      ).read(),
    ).rejects.toThrow("Could not read Actual login methods");
  });

  it("handles missing providers and an unset current method", async () => {
    const fetchRequest = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          status: "ok",
          data: {
            bootstrapped: false,
            loginMethod: null,
            availableLoginMethods: "not a list",
          },
        }),
        {
          status: 200,
          headers: { "content-type": "application/json" },
        },
      ),
    );

    await expect(
      new ActualLoginMethodsReader(
        "https://budget.example.com",
        fetchRequest,
      ).read(),
    ).resolves.toEqual({
      bootstrapped: false,
      loginMethod: null,
      availableLoginMethods: [],
    });
  });

  it("marks an unrecognized configured sign-in method", async () => {
    const fetchRequest = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          status: "ok",
          data: { bootstrapped: true, loginMethod: "saml" },
        }),
        {
          status: 200,
          headers: { "content-type": "application/json" },
        },
      ),
    );

    await expect(
      new ActualLoginMethodsReader(
        "https://budget.example.com",
        fetchRequest,
      ).read(),
    ).resolves.toEqual({
      bootstrapped: true,
      loginMethod: "unsupported",
      availableLoginMethods: [],
    });
  });

  it.each([
    { name: "a non-object response", body: [] },
    {
      name: "an unknown response status",
      body: { status: "unavailable", data: { bootstrapped: true } },
    },
    { name: "a non-object data value", body: { status: "ok", data: null } },
  ])("rejects $name", async ({ body }) => {
    const fetchRequest = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify(body), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );

    await expect(
      new ActualLoginMethodsReader(
        "https://budget.example.com",
        fetchRequest,
      ).read(),
    ).rejects.toThrow("Could not read Actual login methods");
  });
});
