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
  #processWindow: RateLimitWindow | undefined;

  constructor(
    private readonly maxRequests = 10,
    private readonly windowMs = 60_000,
    private readonly maxClients = 10_000,
    private readonly now: () => number = Date.now,
    private readonly maxProcessRequests = 100,
  ) {
    if (
      !Number.isSafeInteger(maxRequests) ||
      maxRequests < 1 ||
      !Number.isSafeInteger(windowMs) ||
      windowMs < 1 ||
      !Number.isSafeInteger(maxClients) ||
      maxClients < 1 ||
      !Number.isSafeInteger(maxProcessRequests) ||
      maxProcessRequests < 1
    ) {
      throw new Error("Pairing rate limit configuration must be positive");
    }
  }

  consume(clientKey: string): RateLimitDecision {
    const currentTime = this.now();
    let processWindow = this.#processWindow;
    if (processWindow && processWindow.expiresAt <= currentTime) {
      processWindow = undefined;
    }
    if (!processWindow) {
      processWindow = { count: 0, expiresAt: currentTime + this.windowMs };
      this.#processWindow = processWindow;
    }

    if (processWindow.count >= this.maxProcessRequests) {
      return rateLimitDecision(processWindow.expiresAt, currentTime);
    }

    let window = this.#clients.get(clientKey);
    if (window && window.expiresAt <= currentTime) {
      window = undefined;
    }

    if (!window) {
      this.#removeExpiredClients(currentTime);
      if (this.#clients.size >= this.maxClients) {
        const oldestClient = this.#clients.keys().next().value!;
        this.#clients.delete(oldestClient);
      }
      window = { count: 0, expiresAt: currentTime + this.windowMs };
      this.#clients.set(clientKey, window);
    }

    if (window.count >= this.maxRequests) {
      return rateLimitDecision(window.expiresAt, currentTime);
    }

    window.count += 1;
    processWindow.count += 1;
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

function rateLimitDecision(
  expiresAt: number,
  currentTime: number,
): RateLimitDecision {
  return {
    allowed: false,
    retryAfterSeconds: Math.max(1, Math.ceil((expiresAt - currentTime) / 1000)),
  };
}
