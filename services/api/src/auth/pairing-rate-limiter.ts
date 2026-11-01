interface RateLimitWindow {
  count: number;
  expiresAt: number;
}

export interface RateLimitDecision {
  allowed: boolean;
  retryAfterSeconds: number;
}

export class PairingRateLimiter {
  #clients = new Map<string, RateLimitWindow>();

  constructor(
    private readonly maxRequests = 10,
    private readonly windowMs = 60_000,
    private readonly maxClients = 10_000,
    private readonly now: () => number = Date.now,
  ) {
    if (
      !Number.isSafeInteger(maxRequests) ||
      maxRequests < 1 ||
      !Number.isSafeInteger(windowMs) ||
      windowMs < 1 ||
      !Number.isSafeInteger(maxClients) ||
      maxClients < 1
    ) {
      throw new Error("Pairing rate limit configuration must be positive");
    }
  }

  consume(clientKey: string): RateLimitDecision {
    const currentTime = this.now();
    let window = this.#clients.get(clientKey);
    if (window && window.expiresAt <= currentTime) {
      window = undefined;
    }

    if (!window) {
      this.#removeExpiredClients(currentTime);
      if (this.#clients.size >= this.maxClients) {
        return { allowed: false, retryAfterSeconds: 1 };
      }
      window = { count: 0, expiresAt: currentTime + this.windowMs };
      this.#clients.set(clientKey, window);
    }

    if (window.count >= this.maxRequests) {
      return {
        allowed: false,
        retryAfterSeconds: Math.max(
          1,
          Math.ceil((window.expiresAt - currentTime) / 1000),
        ),
      };
    }

    window.count += 1;
    return { allowed: true, retryAfterSeconds: 0 };
  }

  #removeExpiredClients(currentTime: number): void {
    for (const [clientKey, window] of this.#clients) {
      if (window.expiresAt <= currentTime) {
        this.#clients.delete(clientKey);
      }
    }
  }
}
