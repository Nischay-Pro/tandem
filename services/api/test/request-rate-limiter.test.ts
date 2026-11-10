import { describe, expect, it } from "vitest";
import { RequestRateLimiter } from "../src/auth/request-rate-limiter.js";

describe("RequestRateLimiter", () => {
  it("allows requests up to the per-client limit and returns a retry delay", () => {
    const limiter = new RequestRateLimiter(2, 1_000, 10, () => 10);

    expect(limiter.reserveRequest("phone-a")).toEqual({
      allowed: true,
      retryAfterSeconds: 0,
    });
    expect(limiter.reserveRequest("phone-a")).toEqual({
      allowed: true,
      retryAfterSeconds: 0,
    });
    expect(limiter.reserveRequest("phone-a")).toEqual({
      allowed: false,
      retryAfterSeconds: 1,
    });
    expect(limiter.reserveRequest("phone-b").allowed).toBe(true);
  });

  it("starts a new window and removes expired clients", () => {
    let now = 0;
    const limiter = new RequestRateLimiter(1, 20, 10, () => now);

    expect(limiter.reserveRequest("early").allowed).toBe(true);
    now = 15;
    expect(limiter.reserveRequest("recent").allowed).toBe(true);
    now = 21;
    expect(limiter.reserveRequest("new").allowed).toBe(true);
    expect(limiter.reserveRequest("early").allowed).toBe(true);
    expect(limiter.reserveRequest("recent").allowed).toBe(false);
  });

  it("expires a client's window when that client makes another request", () => {
    let now = 0;
    const limiter = new RequestRateLimiter(1, 20, 10, () => now);

    expect(limiter.reserveRequest("phone").allowed).toBe(true);
    now = 21;
    expect(limiter.reserveRequest("phone").allowed).toBe(true);
  });

  it("evicts the oldest entry when the client table reaches its bound", () => {
    const limiter = new RequestRateLimiter(1, 1_000, 1, () => 10);

    expect(limiter.reserveRequest("first").allowed).toBe(true);
    expect(limiter.reserveRequest("second").allowed).toBe(true);
    expect(limiter.reserveRequest("first").allowed).toBe(true);
  });
});
