import { describe, expect, it } from "vitest";
import { parseApiConfig } from "../src/config.js";

const databaseUrl =
  "postgresql://companion:local-test@localhost:5432/companion";

describe("parseApiConfig", () => {
  it("uses safe development defaults", () => {
    expect(parseApiConfig({ DATABASE_URL: databaseUrl })).toEqual({
      databaseUrl,
      host: "0.0.0.0",
      port: 19753,
    });
  });

  it("accepts explicit host and port", () => {
    expect(
      parseApiConfig({
        DATABASE_URL: databaseUrl,
        HOST: "127.0.0.1",
        PORT: "8090",
      }),
    ).toEqual({
      databaseUrl,
      host: "127.0.0.1",
      port: 8090,
    });
  });

  it("uses the default host when the supplied host is blank", () => {
    expect(parseApiConfig({ DATABASE_URL: databaseUrl, HOST: "  " }).host).toBe(
      "0.0.0.0",
    );
  });

  it.each(["", "  "])(
    "uses the default port when supplied port is blank",
    (port) => {
      expect(
        parseApiConfig({ DATABASE_URL: databaseUrl, PORT: port }).port,
      ).toBe(19753);
    },
  );

  it("rejects a missing database URL", () => {
    expect(() => parseApiConfig({})).toThrow("DATABASE_URL is required");
  });

  it("rejects malformed database URLs", () => {
    expect(() => parseApiConfig({ DATABASE_URL: "not-a-url" })).toThrow(
      "DATABASE_URL must be a valid PostgreSQL URL",
    );
  });

  it("rejects non-PostgreSQL URLs", () => {
    expect(() =>
      parseApiConfig({ DATABASE_URL: "https://example.com" }),
    ).toThrow("DATABASE_URL must use postgres:// or postgresql://");
  });

  it.each(["0", "65536", "1.5", "nope"])("rejects invalid port %s", (port) => {
    expect(() =>
      parseApiConfig({ DATABASE_URL: databaseUrl, PORT: port }),
    ).toThrow("PORT must be an integer between 1 and 65535");
  });
});
