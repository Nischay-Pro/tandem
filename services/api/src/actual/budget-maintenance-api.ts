import * as actualApi from "@actual-app/api";
import type { BudgetLockPort } from "./transaction-reconciler.js";
import { createAccountOperations } from "./budget-maintenance-account-api.js";
import { createBankSyncOperations } from "./budget-maintenance-bank-sync-api.js";
import { createCategoryOperations } from "./budget-maintenance-category-api.js";
import { createPayeeOperations } from "./budget-maintenance-payee-api.js";
import { createPlanningOperations } from "./budget-maintenance-planning-api.js";
import { createScheduleOperations } from "./budget-maintenance-schedule-api.js";
import type {
  ActualBudgetMaintenanceSession,
  BudgetMaintenanceApiPort,
} from "./budget-maintenance-contracts.js";
import type { ActualBudgetMaintenanceClient } from "./budget-maintenance-contracts.js";
export * from "./budget-maintenance-contracts.js";

export function createBudgetMaintenanceApi(
  locks: BudgetLockPort,
  session: ActualBudgetMaintenanceSession,
  client: ActualBudgetMaintenanceClient = actualApi,
): BudgetMaintenanceApiPort {
  const dependencies = { locks, session, client };
  return {
    ...createBankSyncOperations(dependencies),
    ...createAccountOperations(dependencies),
    ...createCategoryOperations(dependencies),
    ...createPayeeOperations(dependencies),
    ...createPlanningOperations(dependencies),
    ...createScheduleOperations(dependencies),
  };
}
