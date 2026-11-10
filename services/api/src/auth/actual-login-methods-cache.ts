import type { ActualLoginMethods } from "./actual-login-methods.js";

export class ActualLoginMethodsCache {
  #value: ActualLoginMethods | undefined;
  #expiresAt = 0;
  #pending: Promise<ActualLoginMethods> | undefined;

  constructor(
    private readonly ttlMs: number,
    private readonly now: () => number = Date.now,
  ) {}

  read(load: () => Promise<ActualLoginMethods>): Promise<ActualLoginMethods> {
    if (this.#value && this.now() < this.#expiresAt) {
      return Promise.resolve(this.#value);
    }
    if (this.#pending) return this.#pending;

    const pending = load()
      .then((value) => {
        this.#value = value;
        this.#expiresAt = this.now() + this.ttlMs;
        return value;
      })
      .catch((error: unknown) => {
        if (this.#value) {
          this.#expiresAt = this.now() + this.ttlMs;
          return this.#value;
        }
        throw error;
      })
      .finally(() => {
        this.#pending = undefined;
      });
    this.#pending = pending;
    return pending;
  }
}
