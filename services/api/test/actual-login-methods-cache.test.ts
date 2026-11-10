import { describe, expect, it, vi } from "vitest";
import { ActualLoginMethodsCache } from "../src/auth/actual-login-methods-cache.js";
import type { ActualLoginMethods } from "../src/auth/actual-login-methods.js";

const passwordMethods: ActualLoginMethods = {
  bootstrapped: true,
  loginMethod: "password",
  availableLoginMethods: [
    { method: "password", displayName: "Password", active: true },
  ],
};

describe("ActualLoginMethodsCache", () => {
  it("serves a successful result until its cache window expires", async () => {
    let now = 10;
    const load = vi.fn(async () => passwordMethods);
    const cache = new ActualLoginMethodsCache(30, () => now);

    await expect(cache.read(load)).resolves.toEqual(passwordMethods);
    now = 39;
    await expect(cache.read(load)).resolves.toEqual(passwordMethods);
    expect(load).toHaveBeenCalledOnce();

    now = 40;
    await expect(cache.read(load)).resolves.toEqual(passwordMethods);
    expect(load).toHaveBeenCalledTimes(2);
  });

  it("shares an in-flight Actual lookup between simultaneous requests", async () => {
    let resolve!: (methods: ActualLoginMethods) => void;
    const load = vi.fn(
      () =>
        new Promise<ActualLoginMethods>((complete) => {
          resolve = complete;
        }),
    );
    const cache = new ActualLoginMethodsCache(30, () => 10);
    const first = cache.read(load);
    const second = cache.read(load);

    expect(load).toHaveBeenCalledOnce();
    resolve(passwordMethods);
    await expect(Promise.all([first, second])).resolves.toEqual([
      passwordMethods,
      passwordMethods,
    ]);
  });

  it("serves the last good result and slows retries when Actual is unavailable", async () => {
    let now = 10;
    const cache = new ActualLoginMethodsCache(30, () => now);
    const load = vi
      .fn<() => Promise<ActualLoginMethods>>()
      .mockResolvedValueOnce(passwordMethods)
      .mockRejectedValueOnce(new Error("private upstream details"))
      .mockRejectedValueOnce(new Error("private upstream details"));

    await cache.read(load);
    now = 41;
    await expect(cache.read(load)).resolves.toEqual(passwordMethods);
    now = 60;
    await expect(cache.read(load)).resolves.toEqual(passwordMethods);
    expect(load).toHaveBeenCalledTimes(2);
  });

  it("propagates lookup failure when no successful result has been cached", async () => {
    const cache = new ActualLoginMethodsCache(30, () => 10);

    await expect(
      cache.read(async () => {
        throw new Error("Actual unavailable");
      }),
    ).rejects.toThrow("Actual unavailable");
  });
});
