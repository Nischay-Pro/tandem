import { describe, expect, it } from "vitest";
import { PairingRateLimiter } from "../src/auth/pairing-rate-limiter.js";

describe("pairing failure rate limiter", () => {
  it("counts failed codes but does not charge successful pairings", () => {
    const limiter = new PairingRateLimiter(2, 5_000, 10, () => 1_000);

    for (let attempt = 0; attempt < 4; attempt += 1) {
      const pairing = limiter.reserveAttempt("client-a");
      expect(pairing.allowed).toBe(true);
      pairing.complete(true);
    }

    const firstFailure = limiter.reserveAttempt("client-a");
    expect(firstFailure.allowed).toBe(true);
    firstFailure.complete(false);
    const secondFailure = limiter.reserveAttempt("client-a");
    expect(secondFailure.allowed).toBe(true);
    secondFailure.complete(false);

    const limited = limiter.reserveAttempt("client-a");
    expect(limited).toMatchObject({
      allowed: false,
      retryAfterSeconds: 5,
    });
    limited.complete(false);
  });

  it("preserves failures when another in-flight request succeeds", () => {
    const limiter = new PairingRateLimiter(2, 5_000, 10, () => 1_000);
    const first = limiter.reserveAttempt("client-a");
    const second = limiter.reserveAttempt("client-a");

    first.complete(true);
    second.complete(false);
    const success = limiter.reserveAttempt("client-a");
    expect(success.allowed).toBe(true);
    success.complete(true);
    const failure = limiter.reserveAttempt("client-a");
    failure.complete(false);
    expect(limiter.reserveAttempt("client-a").allowed).toBe(false);
  });

  it("ignores attempts evicted before their request finishes", () => {
    const limiter = new PairingRateLimiter(1, 5_000, 1, () => 1_000);
    const evictedAttempt = limiter.reserveAttempt("client-a");

    limiter.reserveAttempt("client-b").complete(false);
    evictedAttempt.complete(false);
    expect(limiter.reserveAttempt("client-a").allowed).toBe(true);
  });

  it("ignores late and duplicate completions", () => {
    let now = 1_000;
    const limiter = new PairingRateLimiter(1, 1_000, 10, () => now);
    const attempt = limiter.reserveAttempt("client-a");

    now = 2_000;
    attempt.complete(false);
    attempt.complete(false);
    expect(limiter.reserveAttempt("client-a").allowed).toBe(true);
  });

  it("reserves concurrent attempts and releases successful ones", () => {
    const limiter = new PairingRateLimiter(1, 5_000, 10, () => 1_000);
    const first = limiter.reserveAttempt("client-a");
    expect(first.allowed).toBe(true);
    expect(limiter.reserveAttempt("client-a").allowed).toBe(false);

    first.complete(true);
    const retry = limiter.reserveAttempt("client-a");
    expect(retry.allowed).toBe(true);
    retry.complete(false);
    expect(limiter.reserveAttempt("client-a").allowed).toBe(false);
  });

  it("starts a fresh window after the previous one expires", () => {
    let now = 1_000;
    const limiter = new PairingRateLimiter(1, 2_000, 10, () => now);

    limiter.reserveAttempt("client-a").complete(false);
    expect(limiter.reserveAttempt("client-a").allowed).toBe(false);
    now = 3_000;
    expect(limiter.reserveAttempt("client-a").allowed).toBe(true);
  });

  it("evicts expired clients and the oldest active client at capacity", () => {
    let now = 1_000;
    const limiter = new PairingRateLimiter(2, 1_000, 1, () => now);

    limiter.reserveAttempt("client-a").complete(false);
    now = 2_000;
    const currentClient = limiter.reserveAttempt("client-b");
    expect(currentClient.allowed).toBe(true);
    currentClient.complete(false);
    expect(limiter.reserveAttempt("client-b").allowed).toBe(true);
  });

  it("evicts the oldest active client when the tracker reaches its bound", () => {
    const limiter = new PairingRateLimiter(1, 10_000, 2, () => 1_000);

    limiter.reserveAttempt("client-a").complete(false);
    limiter.reserveAttempt("client-b").complete(false);
    limiter.reserveAttempt("client-c").complete(false);
    expect(limiter.reserveAttempt("client-b").allowed).toBe(false);
    expect(limiter.reserveAttempt("client-a").allowed).toBe(true);
  });

  it("removes expired entries before evicting a client's window", () => {
    let now = 0;
    const limiter = new PairingRateLimiter(2, 100, 2, () => now);

    limiter.reserveAttempt("client-a").complete(false);
    now = 50;
    limiter.reserveAttempt("client-b").complete(false);
    now = 100;
    const currentClient = limiter.reserveAttempt("client-c");
    expect(currentClient.allowed).toBe(true);
    currentClient.complete(false);
    expect(limiter.reserveAttempt("client-b").allowed).toBe(true);
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
    const attempt = limiter.reserveAttempt("client-a");

    expect(attempt.allowed).toBe(true);
    attempt.complete(false);
  });
});
