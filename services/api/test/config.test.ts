import { ConfigError, parseConfig } from "../src/config.js";
import { describe, expect, it } from "vitest";

const base = {
  DATABASE_URL: "postgresql://db",
  ACTUAL_SERVER_URL: "https://actual.example/",
};

describe("config", () => {
  it("reads defaults and normalizes the Actual address", () => {
    expect(parseConfig(base)).toEqual({
      host: "0.0.0.0",
      port: 19753,
      databaseUrl: "postgresql://db",
      actualServerUrl: "https://actual.example",
      trustProxy: false,
    });
    expect(parseConfig({ ...base, TRUST_PROXY: " true " }).trustProxy).toBe(
      true,
    );
    expect(parseConfig({ ...base, TRUST_PROXY: "false" }).trustProxy).toBe(
      false,
    );
    expect(parseConfig({ ...base, TRUST_PROXY: "172.18.0.1" }).trustProxy).toBe(
      "172.18.0.1",
    );
    expect(
      parseConfig({ ...base, HOST: "127.0.0.1", PORT: "8080" }),
    ).toMatchObject({ host: "127.0.0.1", port: 8080 });
  });

  it("explains what is missing or wrong", () => {
    expect(() => parseConfig({})).toThrow(ConfigError);
    expect(() => parseConfig({ DATABASE_URL: "x" })).toThrow(
      "ACTUAL_SERVER_URL is required.",
    );
    expect(() =>
      parseConfig({ ...base, ACTUAL_SERVER_URL: "not a url" }),
    ).toThrow("must be a URL");
    expect(() =>
      parseConfig({ ...base, ACTUAL_SERVER_URL: "ftp://actual" }),
    ).toThrow("http or https");
    expect(() => parseConfig({ ...base, PORT: "0" })).toThrow("PORT");
    expect(() => parseConfig({ ...base, PORT: "abc" })).toThrow("PORT");
  });
});
