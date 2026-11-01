import { describe, expect, it } from "vitest";
import { PairingRateLimiter } from "../src/auth/pairing-rate-limiter.js";

describe("pairing attempt rate limiter", () => {
  it("allows a bounded number of attempts and returns the retry delay", () => {
    const limiter = new PairingRateLimiter(2, 5_000, 10, () => 1_000);

    expect(limiter.consume("client-a")).toEqual({
      allowed: true,
      retryAfterSeconds: 0,
    });
    expect(limiter.consume("client-a")).toEqual({
      allowed: true,
      retryAfterSeconds: 0,
    });
    expect(limiter.consume("client-a")).toEqual({
      allowed: false,
      retryAfterSeconds: 5,
    });
  });

  it("starts a fresh window after the previous one expires", () => {
    let now = 1_000;
    const limiter = new PairingRateLimiter(1, 2_000, 10, () => now);

    expect(limiter.consume("client-a").allowed).toBe(true);
    expect(limiter.consume("client-a").allowed).toBe(false);
    now = 3_000;
    expect(limiter.consume("client-a").allowed).toBe(true);
  });

  it("evicts expired clients and the oldest active client at capacity", () => {
    let now = 1_000;
    const limiter = new PairingRateLimiter(2, 1_000, 1, () => now);

    expect(limiter.consume("client-a").allowed).toBe(true);
    expect(limiter.consume("client-b").allowed).toBe(true);
    expect(limiter.consume("client-a").allowed).toBe(true);
    expect(limiter.consume("client-b").allowed).toBe(true);
    now = 2_000;
    expect(limiter.consume("client-b").allowed).toBe(true);
  });

  it("evicts the oldest active client when the tracker reaches its bound", () => {
    const limiter = new PairingRateLimiter(1, 10_000, 2, () => 1_000);

    expect(limiter.consume("client-a").allowed).toBe(true);
    expect(limiter.consume("client-b").allowed).toBe(true);
    expect(limiter.consume("client-c").allowed).toBe(true);
    expect(limiter.consume("client-b").allowed).toBe(false);
    expect(limiter.consume("client-a").allowed).toBe(true);
  });

  it("removes expired entries before evicting an active client's window", () => {
    let now = 0;
    const limiter = new PairingRateLimiter(2, 100, 4, () => now);

    expect(limiter.consume("client-a").allowed).toBe(true);
    now = 50;
    expect(limiter.consume("client-b").allowed).toBe(true);
    now = 60;
    expect(limiter.consume("client-c").allowed).toBe(true);
    now = 100;
    expect(limiter.consume("client-a").allowed).toBe(true);
    now = 150;
    expect(limiter.consume("client-d").allowed).toBe(true);
    expect(limiter.consume("client-e").allowed).toBe(true);
    expect(limiter.consume("client-a").allowed).toBe(true);
    expect(limiter.consume("client-a").allowed).toBe(false);
  });

  it.each([
    [0, 1, 1],
    [1, 0, 1],
    [1, 1, 0],
    [1.5, 1, 1],
  ])("rejects invalid bounds (%s, %s, %s)", (max, window, clients) => {
    expect(() => new PairingRateLimiter(max, window, clients)).toThrow(
      "Pairing rate limit configuration must be positive",
    );
  });

  it("accepts a one millisecond window as a valid bound", () => {
    const limiter = new PairingRateLimiter(1, 1, 1, () => 1_000);

    expect(limiter.consume("client-a")).toEqual({
      allowed: true,
      retryAfterSeconds: 0,
    });
  });
});
