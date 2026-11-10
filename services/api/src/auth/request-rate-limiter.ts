export interface RequestRateLimitDecision {
  allowed: boolean;
  retryAfterSeconds: number;
}

interface ClientWindow {
  requests: number;
  expiresAt: number;
}

export class RequestRateLimiter {
  #clients = new Map<string, ClientWindow>();

  constructor(
    private readonly maxRequests: number,
    private readonly windowMs: number,
    private readonly maxClients = 10_000,
    private readonly now: () => number = Date.now,
  ) {}

  reserveRequest(clientKey: string): RequestRateLimitDecision {
    const currentTime = this.now();
    let window = this.#clients.get(clientKey);
    if (window && window.expiresAt <= currentTime) {
      this.#clients.delete(clientKey);
      window = undefined;
    }

    if (!window) {
      this.#removeExpiredClients(currentTime);
      if (this.#clients.size >= this.maxClients) {
        const oldestClient = this.#clients.keys().next().value as string;
        this.#clients.delete(oldestClient);
      }
      window = { requests: 0, expiresAt: currentTime + this.windowMs };
      this.#clients.set(clientKey, window);
    }

    if (window.requests >= this.maxRequests) {
      return {
        allowed: false,
        retryAfterSeconds: Math.max(
          1,
          Math.ceil((window.expiresAt - currentTime) / 1000),
        ),
      };
    }

    window.requests += 1;
    return { allowed: true, retryAfterSeconds: 0 };
  }

  #removeExpiredClients(currentTime: number): void {
    for (const [clientKey, window] of this.#clients) {
      if (window.expiresAt <= currentTime) this.#clients.delete(clientKey);
    }
  }
}
