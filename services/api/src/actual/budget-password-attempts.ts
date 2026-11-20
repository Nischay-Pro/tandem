import { createHash } from "node:crypto";

export const BUDGET_PASSWORD_MAX_FAILURES = 5;
export const BUDGET_PASSWORD_WINDOW_MS = 15 * 60_000;

interface AttemptWindow {
  failures: number;
  expiresAt: number;
}

export class BudgetPasswordAttemptLimiter {
  #windows = new Map<string, AttemptWindow>();

  constructor(
    private readonly maxFailures = BUDGET_PASSWORD_MAX_FAILURES,
    private readonly windowMs = BUDGET_PASSWORD_WINDOW_MS,
    private readonly maxKeys = 10_000,
    private readonly now: () => number = Date.now,
  ) {}

  retryAfterSeconds(key: string): number {
    const window = this.#current(key);
    if (!window || window.failures < this.maxFailures) return 0;
    return Math.max(1, Math.ceil((window.expiresAt - this.now()) / 1000));
  }

  recordFailure(key: string): void {
    const window = this.#current(key);
    if (window) {
      window.failures += 1;
      return;
    }
    if (this.#windows.size >= this.maxKeys) {
      this.#windows.delete(this.#windows.keys().next().value as string);
    }
    this.#windows.set(key, {
      failures: 1,
      expiresAt: this.now() + this.windowMs,
    });
  }

  recordSuccess(key: string): void {
    this.#windows.delete(key);
  }

  #current(key: string): AttemptWindow | undefined {
    const window = this.#windows.get(key);
    if (window && window.expiresAt <= this.now()) {
      this.#windows.delete(key);
      return undefined;
    }
    return window;
  }
}

export function budgetPasswordAttemptKey(request: {
  headers: Record<string, unknown>;
  params: unknown;
  ip: string;
}): string | undefined {
  const password = request.headers["x-budget-encryption-password"];
  if (typeof password !== "string" || password.length === 0) return undefined;
  const budgetId = (request.params as { budgetId?: unknown } | undefined)
    ?.budgetId;
  if (typeof budgetId !== "string" || budgetId.length === 0) return undefined;
  const credential = request.headers.authorization;
  const client = typeof credential === "string" ? credential : request.ip;
  return `${createHash("sha256").update(client).digest("hex")}:${budgetId}`;
}
