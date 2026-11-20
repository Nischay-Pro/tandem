import { describe, expect, it } from "vitest";
import {
  BudgetEncryptionPasswordError,
  toBudgetEncryptionError,
} from "../src/actual/budget-encryption-errors.js";

describe("budget encryption errors", () => {
  it("maps Actual key errors to password codes", () => {
    expect(
      toBudgetEncryptionError(
        Object.assign(new Error("encrypted"), { code: "missing-key" }),
      )?.code,
    ).toBe("budget_password_required");
    expect(
      toBudgetEncryptionError(
        Object.assign(new Error("bad"), { code: "decrypt-failure" }),
      )?.code,
    ).toBe("budget_password_incorrect");
  });

  it("ignores everything else", () => {
    expect(toBudgetEncryptionError(new Error("other"))).toBeUndefined();
    expect(toBudgetEncryptionError({ code: "network" })).toBeUndefined();
    expect(toBudgetEncryptionError(null)).toBeUndefined();
    expect(toBudgetEncryptionError("missing-key")).toBeUndefined();
  });

  it("explains each code", () => {
    const error = new BudgetEncryptionPasswordError("budget_password_required");
    expect(error.name).toBe("BudgetEncryptionPasswordError");
    expect(error.message).toBe(
      "This budget is encrypted. Enter its encryption password to sync.",
    );
  });
});
