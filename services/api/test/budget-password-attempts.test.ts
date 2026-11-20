import { describe, expect, it } from "vitest";
import {
  BudgetPasswordAttemptLimiter,
  budgetPasswordAttemptKey,
} from "../src/actual/budget-password-attempts.js";

describe("budget password attempts", () => {
  it("locks a key after repeated failures until the window ends", () => {
    let now = 0;
    const limiter = new BudgetPasswordAttemptLimiter(2, 1_000, 10, () => now);
    expect(limiter.retryAfterSeconds("a")).toBe(0);
    limiter.recordFailure("a");
    expect(limiter.retryAfterSeconds("a")).toBe(0);
    limiter.recordFailure("a");
    expect(limiter.retryAfterSeconds("a")).toBe(1);
    now = 1_000;
    expect(limiter.retryAfterSeconds("a")).toBe(0);
  });

  it("clears on success and evicts the oldest key when full", () => {
    const limiter = new BudgetPasswordAttemptLimiter(1, 60_000, 1);
    limiter.recordFailure("a");
    limiter.recordSuccess("a");
    expect(limiter.retryAfterSeconds("a")).toBe(0);
    limiter.recordFailure("a");
    limiter.recordFailure("b");
    expect(limiter.retryAfterSeconds("a")).toBe(0);
    expect(limiter.retryAfterSeconds("b")).toBe(60);
  });

  it("keys attempts by credential and budget only when a password is sent", () => {
    const base = { ip: "203.0.113.4", params: { budgetId: "budget-1" } };
    expect(budgetPasswordAttemptKey({ ...base, headers: {} })).toBeUndefined();
    expect(
      budgetPasswordAttemptKey({
        ...base,
        headers: { "x-budget-encryption-password": "" },
      }),
    ).toBeUndefined();
    expect(
      budgetPasswordAttemptKey({
        ip: base.ip,
        params: undefined,
        headers: { "x-budget-encryption-password": "secret" },
      }),
    ).toBeUndefined();
    const byIp = budgetPasswordAttemptKey({
      ...base,
      headers: { "x-budget-encryption-password": "secret" },
    });
    const byCredential = budgetPasswordAttemptKey({
      ...base,
      headers: {
        "x-budget-encryption-password": "secret",
        authorization: "Bearer device",
      },
    });
    expect(byIp).toMatch(/:budget-1$/);
    expect(byCredential).not.toBe(byIp);
  });
});
