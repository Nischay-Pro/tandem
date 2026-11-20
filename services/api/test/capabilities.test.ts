import { describe, expect, it, vi } from "vitest";
import { buildApiApp } from "../src/app.js";
import {
  MINIMUM_CLIENT_API_VERSION,
  SERVER_FEATURES,
  TANDEM_API_VERSION,
} from "../src/capabilities.js";

describe("server capabilities", () => {
  it("lists the API version and supported features without sign-in", async () => {
    const app = await buildApiApp({
      ping: vi.fn(async () => undefined),
      close: vi.fn(async () => undefined),
    });
    const response = await app.inject({
      method: "GET",
      url: "/api/v1/capabilities",
    });

    expect(response.statusCode).toBe(200);
    expect(response.headers["cache-control"]).toBe("no-store");
    expect(response.json()).toEqual({
      apiVersion: TANDEM_API_VERSION,
      minimumClientApiVersion: MINIMUM_CLIENT_API_VERSION,
      features: [...SERVER_FEATURES],
      actualVersion: "26.11.0",
    });
    expect(SERVER_FEATURES).toContain("hold-total");
    await app.close();
  });
});
