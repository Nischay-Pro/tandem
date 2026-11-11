import { describe, expect, it, vi } from "vitest";
import {
  createBudgetMaintenanceApi,
  type StatementTransactionImport,
} from "../src/actual/budget-maintenance-api.js";
import type { ActualBudgetMaintenanceSession } from "../src/actual/budget-maintenance-api.js";
import type { BudgetLockPort } from "../src/actual/transaction-reconciler.js";

const validateTransactionDateMock = vi.hoisted(() => vi.fn());

vi.mock(
  "../src/actual/transaction-reconciliation.js",
  async (importOriginal) => {
    const original =
      await importOriginal<
        typeof import("../src/actual/transaction-reconciliation.js")
      >();
    validateTransactionDateMock.mockImplementation(
      original.validateTransactionDate,
    );
    return {
      ...original,
      validateTransactionDate: validateTransactionDateMock,
    };
  },
);

describe("budget maintenance date validation fallback", () => {
  it("uses a safe message when statement date validation throws a non-Error", async () => {
    validateTransactionDateMock.mockImplementationOnce(() => {
      throw {};
    });
    const importTransactions = vi.fn();
    const client = {
      getAccounts: vi.fn(async () => [{ id: "checking", closed: false }]),
      importTransactions,
    } as unknown as NonNullable<
      Parameters<typeof createBudgetMaintenanceApi>[2]
    >;
    const locks: BudgetLockPort = {
      async withBudgetLock<T>(_budgetId: string, work: () => Promise<T>) {
        return work();
      },
    };
    const session: ActualBudgetMaintenanceSession = {
      async runBudget<T>(_budgetId: string, operation: () => Promise<T>) {
        return operation();
      },
    };
    const api = createBudgetMaintenanceApi(locks, session, client);
    const rows: StatementTransactionImport[] = [
      {
        date: "2026-11-01",
        amountMinor: -1200,
        payeeName: "Market",
        importedId: "statement-row-1",
      },
    ];

    await expect(
      api.importStatementTransactions("budget-1", "checking", rows),
    ).rejects.toMatchObject({
      name: "BudgetMaintenanceInputError",
      message: "The statement contains an invalid transaction date",
    });
    expect(importTransactions).not.toHaveBeenCalled();
  });
});
