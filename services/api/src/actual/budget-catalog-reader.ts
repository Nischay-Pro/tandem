import * as Actual from "@actual-app/api";
import type { BudgetCatalogEntry } from "../budget-projection/budget-read-api.js";

interface ActualBudgetCatalogSession {
  execute<T>(operation: () => Promise<T>): Promise<T>;
}

export type ActualBudgetCatalogApi = Pick<typeof Actual, "getBudgets">;

export class ActualBudgetCatalogReader {
  constructor(
    private readonly session: ActualBudgetCatalogSession,
    private readonly actual: ActualBudgetCatalogApi = Actual,
  ) {}

  read(): Promise<BudgetCatalogEntry[]> {
    return this.session.execute(async () => {
      const budgets = await this.actual.getBudgets();
      return budgets.flatMap((budget) => {
        const budgetId = budget.groupId?.trim();
        if (!budgetId) return [];
        const name = budget.name?.trim();
        if (!name) {
          throw new Error("Actual returned a budget without a valid name");
        }
        return [
          {
            budgetId,
            name,
            isEncrypted: Boolean(budget.encryptKeyId),
          },
        ];
      });
    });
  }
}
