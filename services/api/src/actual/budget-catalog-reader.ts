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
      const byBudgetId = new Map<string, BudgetCatalogEntry>();
      for (const budget of budgets) {
        const budgetId = budget.groupId?.trim();
        if (!budgetId) continue;
        const name = budget.name?.trim();
        if (!name) {
          throw new Error("Actual returned a budget without a valid name");
        }
        const existing = byBudgetId.get(budgetId);
        byBudgetId.set(budgetId, {
          budgetId,
          // Actual can return both a cached local file and its remote listing.
          // Keep the latest name while retaining encryption metadata from either.
          name,
          isEncrypted:
            Boolean(budget.encryptKeyId) || existing?.isEncrypted === true,
        });
      }
      return [...byBudgetId.values()];
    });
  }
}
