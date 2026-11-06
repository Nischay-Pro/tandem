interface RateLimitWindow {
  failures: number;
  inFlight: number;
  expiresAt: number;
}

export interface RateLimitDecision {
  allowed: boolean;
  retryAfterSeconds: number;
}

export interface PairingAttemptReservation extends RateLimitDecision {
  complete(succeeded: boolean): void;
}

export class PairingRateLimiter {
  #clients = new Map<string, RateLimitWindow>();

  constructor(
    private readonly maxFailures = 10,
    private readonly windowMs = 60_000,
    private readonly maxClients = 10_000,
    private readonly now: () => number = Date.now,
  ) {
    if (
      !Number.isSafeInteger(maxFailures) ||
      maxFailures < 1 ||
      !Number.isSafeInteger(windowMs) ||
      windowMs < 1 ||
      !Number.isSafeInteger(maxClients) ||
      maxClients < 1
    ) {
      throw new Error("Pairing rate limit configuration must be positive");
    }
  }

  reserveAttempt(clientKey: string): PairingAttemptReservation {
    const currentTime = this.now();
    let window = this.#clients.get(clientKey);
    if (window && window.expiresAt <= currentTime) {
      this.#clients.delete(clientKey);
      window = undefined;
    }

    if (window && window.failures + window.inFlight >= this.maxFailures) {
      return {
        ...rateLimitDecision(window.expiresAt, currentTime),
        complete: () => undefined,
      };
    }

    if (!window) {
      this.#removeExpiredClients(currentTime);
      if (this.#clients.size >= this.maxClients) {
        const oldestClient = this.#clients.keys().next().value!;
        this.#clients.delete(oldestClient);
      }
      window = {
        failures: 0,
        inFlight: 0,
        expiresAt: currentTime + this.windowMs,
      };
      this.#clients.set(clientKey, window);
    }

    window.inFlight += 1;
    let completed = false;
    return {
      allowed: true,
      retryAfterSeconds: 0,
      complete: (succeeded) => {
        if (completed) return;
        completed = true;
        if (this.#clients.get(clientKey) !== window) return;

        window.inFlight -= 1;
        if (this.now() >= window.expiresAt) return;
        if (succeeded) {
          if (window.failures === 0 && window.inFlight === 0) {
            this.#clients.delete(clientKey);
          }
          return;
        }
        window.failures += 1;
      },
    };
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
