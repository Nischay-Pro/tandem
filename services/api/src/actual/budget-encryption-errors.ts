export type BudgetEncryptionErrorCode =
  "budget_password_required" | "budget_password_incorrect";

const MESSAGES: Record<BudgetEncryptionErrorCode, string> = {
  budget_password_required:
    "This budget is encrypted. Enter its encryption password to sync.",
  budget_password_incorrect: "The budget encryption password is incorrect.",
};

export class BudgetEncryptionPasswordError extends Error {
  readonly code: BudgetEncryptionErrorCode;

  constructor(code: BudgetEncryptionErrorCode) {
    super(MESSAGES[code]);
    this.name = "BudgetEncryptionPasswordError";
    this.code = code;
  }
}

export function toBudgetEncryptionError(
  error: unknown,
): BudgetEncryptionPasswordError | undefined {
  if (typeof error !== "object" || error === null) return undefined;
  const code = (error as { code?: unknown }).code;
  if (code === "missing-key") {
    return new BudgetEncryptionPasswordError("budget_password_required");
  }
  if (code === "decrypt-failure") {
    return new BudgetEncryptionPasswordError("budget_password_incorrect");
  }
  return undefined;
}
