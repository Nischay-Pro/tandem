import type { FastifyInstance } from "fastify";
import { DeviceAuthorization } from "../auth/device-auth.js";
import { BudgetMaintenanceBusyError } from "../actual/budget-maintenance-api.js";
import type {
  BudgetMaintenanceRouteDependencies,
  BudgetRouteParams,
  BudgetReadHeaders,
} from "./budget-maintenance-support.js";
import {
  MaintenanceOperationConflictError,
  runMaintenanceOperation,
  logBudgetMaintenanceEvent,
} from "./budget-maintenance-support.js";
import {
  paramsSchema,
  headersSchema,
  commonResponseSchema,
} from "./budget-maintenance-schemas.js";

export function registerBudgetBankSyncRoutes(
  app: FastifyInstance,
  dependencies: BudgetMaintenanceRouteDependencies,
): void {
  const {
    deviceAuth,
    maintenanceApi,
    journal,
    requireDevice,
    invalidBudgetRequest,
    transactionNotFound,
    actualNotConfigured,
    reportTransactionFailure,
  } = dependencies;
  app.post<{ Params: BudgetRouteParams; Headers: BudgetReadHeaders }>(
    "/api/v1/budgets/:budgetId/bank-sync",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "runBudgetBankSync",
        summary:
          "Run the configured Actual Budget bank sync and refresh its budget data",
        security: [{ bearerAuth: [] }],
        params: paramsSchema,
        headers: headersSchema,
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (!DeviceAuthorization.canAccessBudget(device, request.params.budgetId))
        return transactionNotFound(reply);
      if (!maintenanceApi) return actualNotConfigured(reply);
      if (!journal) return actualNotConfigured(reply);
      try {
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "bank-sync",
          null,
          {},
          async () => {
            await maintenanceApi.runBankSync(
              request.params.budgetId,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "bank_sync",
          "success",
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceBusyError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "bank_sync",
            "rejected",
          );
          reply.header("retry-after", error.retryAfterSeconds.toString());
          return reply.code(429).send({
            error: { code: "bank_sync_busy", message: error.message },
          });
        }
        if (error instanceof MaintenanceOperationConflictError) {
          return reply.code(409).send({
            error: { code: error.code, message: error.message },
          });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "bank_sync",
          "failed",
        );
        reportTransactionFailure("Actual bank sync failed", error);
        return reply.code(502).send({
          error: {
            code: "bank_sync_failed",
            message:
              "Actual could not complete bank sync. Check its provider setup and try again.",
          },
        });
      }
    },
  );
}
