import type { FastifyInstance } from "fastify";
import { DeviceAuthorization } from "../auth/device-auth.js";
import {
  BudgetMaintenanceInputError,
  MAX_BUDGET_AMOUNT_MINOR,
} from "../actual/budget-maintenance-api.js";
import type {
  BudgetMaintenanceRouteDependencies,
  BudgetRouteParams,
  BudgetCategoryRouteParams,
  BudgetMonthRouteParams,
  BudgetCategoryBudgetAmountBody,
  BudgetCategoryCarryoverBody,
  BudgetMonthActionBody,
  BudgetMoveMoneyBody,
  BudgetReadHeaders,
  JsonValue,
} from "./budget-maintenance-support.js";
import {
  MaintenanceOperationConflictError,
  runMaintenanceOperation,
  logBudgetMaintenanceEvent,
} from "./budget-maintenance-support.js";
import {
  paramsSchema,
  categoryParamsSchema,
  headersSchema,
  commonResponseSchema,
} from "./budget-maintenance-schemas.js";

export function registerBudgetPlanningRoutes(
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
  app.put<{
    Params: BudgetCategoryRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetCategoryBudgetAmountBody;
  }>(
    "/api/v1/budgets/:budgetId/categories/:categoryId/budget-amount",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "setBudgetCategoryAmount",
        summary: "Set a category's assigned amount for an Actual Budget month",
        security: [{ bearerAuth: [] }],
        params: categoryParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            month: { type: "string", pattern: "^[0-9]{4}-(0[1-9]|1[0-2])$" },
            amountMinor: {
              type: "integer",
              minimum: 0,
              maximum: MAX_BUDGET_AMOUNT_MINOR,
            },
          },
          required: ["month", "amountMinor"],
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
          "category-budget-amount",
          request.params.categoryId,
          { month: request.body.month, amountMinor: request.body.amountMinor },
          async () => {
            await maintenanceApi.setBudgetAmount(
              request.params.budgetId,
              request.params.categoryId,
              request.body.month,
              request.body.amountMinor,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_budget",
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
            "category_budget",
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
          "category_budget",
          "failed",
          request.params.categoryId,
        );
        reportTransactionFailure("Actual category budget update failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.put<{
    Params: BudgetCategoryRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetCategoryCarryoverBody;
  }>(
    "/api/v1/budgets/:budgetId/categories/:categoryId/carryover",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "setBudgetCategoryCarryover",
        summary:
          "Set whether unused category money carries into the next month",
        security: [{ bearerAuth: [] }],
        params: categoryParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            month: { type: "string", pattern: "^[0-9]{4}-(0[1-9]|1[0-2])$" },
            enabled: { type: "boolean" },
          },
          required: ["month", "enabled"],
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
          "category-carryover",
          request.params.categoryId,
          { month: request.body.month, enabled: request.body.enabled },
          async () => {
            await maintenanceApi.setBudgetCarryover(
              request.params.budgetId,
              request.params.categoryId,
              request.body.month,
              request.body.enabled,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_carryover",
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
            "category_carryover",
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
          "category_carryover",
          "failed",
          request.params.categoryId,
        );
        reportTransactionFailure(
          "Actual category carryover update failed",
          error,
        );
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.post<{
    Params: BudgetMonthRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetMonthActionBody;
  }>(
    "/api/v1/budgets/:budgetId/months/:month/actions",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "manageBudgetMonth",
        summary: "Manage a month's carryover and category assignments",
        security: [{ bearerAuth: [] }],
        params: {
          type: "object",
          properties: {
            budgetId: paramsSchema.properties.budgetId,
            month: { type: "string", pattern: "^[0-9]{4}-(0[1-9]|1[0-2])$" },
          },
          required: ["budgetId", "month"],
          additionalProperties: false,
        },
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            action: {
              type: "string",
              enum: [
                "copy-last-month",
                "hold-next-month",
                "reset-hold",
                "apply-template",
              ],
            },
            amountMinor: {
              type: "integer",
              minimum: 1,
              maximum: MAX_BUDGET_AMOUNT_MINOR,
            },
            assignments: {
              type: "array",
              minItems: 1,
              maxItems: 500,
              items: {
                type: "object",
                properties: {
                  categoryId: { type: "string", minLength: 1, maxLength: 200 },
                  amountMinor: {
                    type: "integer",
                    minimum: 0,
                    maximum: MAX_BUDGET_AMOUNT_MINOR,
                  },
                },
                required: ["categoryId", "amountMinor"],
                additionalProperties: false,
              },
            },
          },
          required: ["action"],
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
      const action = request.body.action;
      if (
        (action === "hold-next-month" &&
          request.body.amountMinor === undefined) ||
        (action !== "hold-next-month" && request.body.amountMinor !== undefined)
      ) {
        return invalidBudgetRequest(reply);
      }
      if (
        (action === "apply-template" &&
          request.body.assignments === undefined) ||
        (action !== "apply-template" && request.body.assignments !== undefined)
      ) {
        return invalidBudgetRequest(reply);
      }
      const auditAction = `budget_month_${action.replaceAll("-", "_")}`;
      const patch: { [key: string]: JsonValue } = { action };
      if (action === "hold-next-month") {
        patch.amountMinor = request.body.amountMinor!;
      }
      if (action === "apply-template") {
        patch.assignments = request.body.assignments!.map((assignment) => ({
          categoryId: assignment.categoryId,
          amountMinor: assignment.amountMinor,
        }));
      }
      try {
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          `budget-month-${action}`,
          request.params.month,
          patch,
          async () => {
            const password = request.headers["x-budget-encryption-password"];
            switch (action) {
              case "copy-last-month":
                await maintenanceApi.copyBudgetMonth(
                  request.params.budgetId,
                  request.params.month,
                  password,
                );
                break;
              case "hold-next-month":
                await maintenanceApi.holdBudgetForNextMonth(
                  request.params.budgetId,
                  request.params.month,
                  request.body.amountMinor!,
                  password,
                );
                break;
              case "reset-hold":
                await maintenanceApi.resetBudgetHold(
                  request.params.budgetId,
                  request.params.month,
                  password,
                );
                break;
              case "apply-template":
                await maintenanceApi.applyBudgetTemplate(
                  request.params.budgetId,
                  request.params.month,
                  request.body.assignments!,
                  password,
                );
                break;
            }
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          auditAction,
          "success",
          request.params.month,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            auditAction,
            "rejected",
            request.params.month,
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
          auditAction,
          "failed",
          request.params.month,
        );
        reportTransactionFailure("Actual budget month action failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.post<{
    Params: BudgetRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetMoveMoneyBody;
  }>(
    "/api/v1/budgets/:budgetId/categories/move-money",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "moveMoneyBetweenCategories",
        summary: "Move an assigned amount between Actual Budget categories",
        security: [{ bearerAuth: [] }],
        params: paramsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            month: { type: "string", pattern: "^[0-9]{4}-(0[1-9]|1[0-2])$" },
            fromCategoryId: {
              type: "string",
              minLength: 1,
              maxLength: 500,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
            toCategoryId: {
              type: "string",
              minLength: 1,
              maxLength: 500,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
            amountMinor: {
              type: "integer",
              minimum: 1,
              maximum: MAX_BUDGET_AMOUNT_MINOR,
            },
          },
          required: ["month", "fromCategoryId", "toCategoryId", "amountMinor"],
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
        const { month, fromCategoryId, toCategoryId, amountMinor } =
          request.body;
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "category-move-money",
          fromCategoryId,
          { month, fromCategoryId, toCategoryId, amountMinor },
          async () => {
            await maintenanceApi.moveMoney(
              request.params.budgetId,
              month,
              fromCategoryId,
              toCategoryId,
              amountMinor,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_move_money",
          "success",
          fromCategoryId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "category_move_money",
            "rejected",
            request.body.fromCategoryId,
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
          "category_move_money",
          "failed",
          request.body.fromCategoryId,
        );
        reportTransactionFailure("Actual category money move failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );
}
