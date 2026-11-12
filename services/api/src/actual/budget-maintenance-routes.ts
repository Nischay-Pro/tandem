import type { FastifyInstance } from "fastify";
import type { BudgetMaintenanceRouteDependencies } from "./budget-maintenance-support.js";
import { registerBudgetBankSyncRoutes } from "./budget-bank-sync-routes.js";
import { registerBudgetStatementImportRoutes } from "./budget-statement-import-routes.js";
import { registerBudgetAccountRoutes } from "./budget-account-routes.js";
import { registerBudgetCategoryRoutes } from "./budget-category-routes.js";
import { registerBudgetPlanningRoutes } from "./budget-planning-routes.js";
import { registerBudgetCategoryRenameRoutes } from "./budget-category-rename-routes.js";
import { registerBudgetScheduleRoutes } from "./budget-schedule-routes.js";

export { type BudgetMaintenanceJournal } from "./budget-maintenance-support.js";

export function registerBudgetMaintenanceRoutes(
  app: FastifyInstance,
  dependencies: BudgetMaintenanceRouteDependencies,
): void {
  registerBudgetBankSyncRoutes(app, dependencies);
  registerBudgetStatementImportRoutes(app, dependencies);
  registerBudgetAccountRoutes(app, dependencies);
  registerBudgetCategoryRoutes(app, dependencies);
  registerBudgetPlanningRoutes(app, dependencies);
  registerBudgetCategoryRenameRoutes(app, dependencies);
  registerBudgetScheduleRoutes(app, dependencies);
}
