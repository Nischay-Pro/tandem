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

  it("shares an IPv6 rate-limit window across each canonical /64 prefix", () => {
    const limiter = new RequestRateLimiter(1, 1_000, 10, () => 10);

    expect(limiter.reserveRequest("2001:db8:0:1::1").allowed).toBe(true);
    expect(limiter.reserveRequest("2001:0DB8:0000:0001:aaaa::2").allowed).toBe(
      false,
    );
    expect(limiter.reserveRequest("2001:db8:0:1:0:0:0:3").allowed).toBe(false);
    expect(limiter.reserveRequest("2001:db8:0:2::1").allowed).toBe(true);
  });

  it("ignores IPv6 scope IDs and keeps mapped IPv4 addresses distinct", () => {
    const limiter = new RequestRateLimiter(1, 1_000, 10, () => 10);

    expect(limiter.reserveRequest("fe80::1%eth0").allowed).toBe(true);
    expect(limiter.reserveRequest("fe80:0:0:0:abcd::2%eth1").allowed).toBe(
      false,
    );
    expect(limiter.reserveRequest("::ffff:192.0.2.1").allowed).toBe(true);
    expect(limiter.reserveRequest("192.0.2.1").allowed).toBe(false);
    expect(limiter.reserveRequest("::ffff:c000:201").allowed).toBe(false);
    expect(limiter.reserveRequest("::ffff:192.0.2.2").allowed).toBe(true);
  });

  it("does not combine separate IPv4 clients", () => {
    const limiter = new RequestRateLimiter(1, 1_000, 10, () => 10);

    expect(limiter.reserveRequest("192.0.2.1").allowed).toBe(true);
    expect(limiter.reserveRequest("192.0.2.2").allowed).toBe(true);
  });
});
