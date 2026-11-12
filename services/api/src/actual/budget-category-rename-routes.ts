import type { FastifyInstance } from "fastify";
import { DeviceAuthorization } from "../auth/device-auth.js";
import { BudgetMaintenanceInputError } from "../actual/budget-maintenance-api.js";
import type {
  BudgetMaintenanceRouteDependencies,
  BudgetCategoryRouteParams,
  BudgetCategoryRenameBody,
  BudgetCategoryDeleteBody,
  BudgetReadHeaders,
} from "./budget-maintenance-support.js";
import {
  MaintenanceOperationConflictError,
  runMaintenanceOperation,
  logBudgetMaintenanceEvent,
} from "./budget-maintenance-support.js";
import {
  categoryParamsSchema,
  headersSchema,
  commonResponseSchema,
} from "./budget-maintenance-schemas.js";

export function registerBudgetCategoryRenameRoutes(
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
    actualRequestFailed,
  } = dependencies;
  app.patch<{
    Params: BudgetCategoryRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetCategoryRenameBody;
  }>(
    "/api/v1/budgets/:budgetId/categories/:categoryId",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "renameBudgetCategory",
        summary: "Rename an Actual Budget category",
        security: [{ bearerAuth: [] }],
        params: categoryParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            name: {
              type: "string",
              minLength: 1,
              maxLength: 100,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
          },
          required: ["name"],
          additionalProperties: false,
        },
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
        const name = request.body.name.trim();
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "category-rename",
          request.params.categoryId,
          { name },
          async () => {
            await maintenanceApi.renameCategory(
              request.params.budgetId,
              request.params.categoryId,
              name,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_rename",
          "success",
          request.params.categoryId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "category_rename",
            "rejected",
            request.params.categoryId,
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_rename",
          "failed",
          request.params.categoryId,
        );
        reportTransactionFailure("Actual category rename failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.delete<{
    Params: BudgetCategoryRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetCategoryDeleteBody;
  }>(
    "/api/v1/budgets/:budgetId/categories/:categoryId",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "deleteBudgetCategory",
        summary:
          "Delete an Actual Budget category and reassign its transactions",
        security: [{ bearerAuth: [] }],
        params: categoryParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            transferCategoryId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
            },
          },
          required: ["transferCategoryId"],
          additionalProperties: false,
        },
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
          "category-delete",
          request.params.categoryId,
          { transferCategoryId: request.body.transferCategoryId },
          async () => {
            await maintenanceApi.deleteCategory(
              request.params.budgetId,
              request.params.categoryId,
              request.body.transferCategoryId,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_delete",
          "success",
          request.params.categoryId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "category_delete",
            "rejected",
            request.params.categoryId,
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ? 409 : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : "invalid_request",
                message: error.message,
              },
            });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_delete",
          "failed",
          request.params.categoryId,
        );
        reportTransactionFailure("Actual category delete failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );
}
