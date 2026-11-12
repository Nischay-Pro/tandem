import type { FastifyInstance } from "fastify";
import { DeviceAuthorization } from "../auth/device-auth.js";
import { BudgetMaintenanceInputError } from "../actual/budget-maintenance-api.js";
import type {
  BudgetMaintenanceRouteDependencies,
  BudgetRouteParams,
  BudgetCategoryRouteParams,
  BudgetCategoryGroupRouteParams,
  BudgetCategoryGroupCreateBody,
  BudgetCategoryGroupActionBody,
  BudgetCategoryActionBody,
  BudgetCategoryCreateBody,
  BudgetReadHeaders,
} from "./budget-maintenance-support.js";
import {
  MaintenanceOperationConflictError,
  runMaintenanceOperation,
  logBudgetMaintenanceEvent,
} from "./budget-maintenance-support.js";
import {
  paramsSchema,
  categoryParamsSchema,
  categoryGroupParamsSchema,
  headersSchema,
  commonResponseSchema,
  errorSchema,
} from "./budget-maintenance-schemas.js";

export function registerBudgetCategoryRoutes(
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
  app.post<{
    Params: BudgetRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetCategoryCreateBody;
  }>(
    "/api/v1/budgets/:budgetId/categories",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "createBudgetCategory",
        summary: "Create an Actual Budget category",
        security: [{ bearerAuth: [] }],
        params: paramsSchema,
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
            categoryGroupId: {
              type: "string",
              minLength: 1,
              maxLength: 500,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
          },
          required: ["name", "categoryGroupId"],
          additionalProperties: false,
        },
        response: {
          200: {
            type: "object",
            properties: {
              status: { type: "string", enum: ["ok"] },
              categoryId: { type: "string", minLength: 1, maxLength: 500 },
            },
            required: ["status", "categoryId"],
            additionalProperties: false,
          },
          400: errorSchema,
          401: errorSchema,
          404: errorSchema,
          409: errorSchema,
          502: errorSchema,
          503: errorSchema,
        },
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
        const categoryResult = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "category-create",
          null,
          { name, categoryGroupId: request.body.categoryGroupId },
          async () => ({
            categoryId: await maintenanceApi.createCategory(
              request.params.budgetId,
              name,
              request.body.categoryGroupId,
              request.headers["x-budget-encryption-password"],
            ),
          }),
        );
        const categoryId = categoryResult.categoryId;
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_create",
          "success",
          categoryId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send({ status: "ok", categoryId });
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "category_create",
            "rejected",
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
          "category_create",
          "failed",
        );
        reportTransactionFailure("Actual category creation failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.post<{
    Params: BudgetRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetCategoryGroupCreateBody;
  }>(
    "/api/v1/budgets/:budgetId/category-groups",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "createBudgetCategoryGroup",
        summary: "Create an Actual Budget category group",
        security: [{ bearerAuth: [] }],
        params: paramsSchema,
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
            isIncome: { type: "boolean" },
          },
          required: ["name", "isIncome"],
          additionalProperties: false,
        },
        response: {
          200: {
            type: "object",
            properties: {
              status: { type: "string", enum: ["ok"] },
              categoryGroupId: { type: "string", minLength: 1, maxLength: 500 },
            },
            required: ["status", "categoryGroupId"],
            additionalProperties: false,
          },
          400: errorSchema,
          401: errorSchema,
          404: errorSchema,
          409: errorSchema,
          502: errorSchema,
          503: errorSchema,
        },
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
          "category-group-create",
          null,
          { name, isIncome: request.body.isIncome },
          async () => ({
            categoryGroupId: await maintenanceApi.createCategoryGroup(
              request.params.budgetId,
              name,
              request.body.isIncome,
              request.headers["x-budget-encryption-password"],
            ),
          }),
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "category_group_create",
          "success",
          result.categoryGroupId,
        );
        reply.header("cache-control", "no-store");
        return reply
          .code(200)
          .send({ status: "ok", categoryGroupId: result.categoryGroupId });
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "category_group_create",
            "rejected",
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
          "category_group_create",
          "failed",
        );
        reportTransactionFailure(
          "Actual category group creation failed",
          error,
        );
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.patch<{
    Params: BudgetCategoryGroupRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetCategoryGroupActionBody;
  }>(
    "/api/v1/budgets/:budgetId/category-groups/:groupId",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "manageBudgetCategoryGroup",
        summary:
          "Rename, hide, show, reorder, or delete an Actual Budget category group",
        security: [{ bearerAuth: [] }],
        params: categoryGroupParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            action: {
              type: "string",
              enum: ["rename", "hide", "show", "move", "delete"],
            },
            name: {
              type: "string",
              minLength: 1,
              maxLength: 100,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
            targetId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
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
      try {
        if (request.body.action === "rename" && !request.body.name)
          return invalidBudgetRequest(reply);
        if (
          request.body.action === "move" &&
          request.body.targetId === undefined
        )
          return invalidBudgetRequest(reply);
        const action = request.body.action;
        const name = request.body.name?.trim();
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          `category-group-${action}`,
          request.params.groupId,
          {
            action,
            targetId: request.body.targetId ?? null,
            ...(name === undefined ? {} : { name }),
          },
          async () => {
            await maintenanceApi.manageCategoryGroup(
              request.params.budgetId,
              request.params.groupId,
              action,
              name,
              request.body.targetId,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          `category_group_${action}`,
          "success",
          request.params.groupId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            `category_group_${request.body.action}`,
            "rejected",
            request.params.groupId,
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
          `category_group_${request.body.action}`,
          "failed",
          request.params.groupId,
        );
        reportTransactionFailure("Actual category group change failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.post<{
    Params: BudgetCategoryRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetCategoryActionBody;
  }>(
    "/api/v1/budgets/:budgetId/categories/:categoryId/actions",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "manageBudgetCategory",
        summary: "Hide, show, reorder, or move an Actual Budget category",
        security: [{ bearerAuth: [] }],
        params: categoryParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            action: { type: "string", enum: ["hide", "show", "move"] },
            targetId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
            },
            targetGroupId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
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
      try {
        if (
          request.body.action === "move" &&
          request.body.targetId === undefined &&
          request.body.targetGroupId === undefined
        )
          return invalidBudgetRequest(reply);
        const action = request.body.action;
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          `category-${action}`,
          request.params.categoryId,
          {
            action,
            targetId: request.body.targetId ?? null,
            targetGroupId: request.body.targetGroupId ?? null,
          },
          async () => {
            await maintenanceApi.manageCategory(
              request.params.budgetId,
              request.params.categoryId,
              action,
              request.body.targetId,
              request.headers["x-budget-encryption-password"],
              request.body.targetGroupId,
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          `category_${action}`,
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
            `category_${request.body.action}`,
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
          `category_${request.body.action}`,
          "failed",
          request.params.categoryId,
        );
        reportTransactionFailure("Actual category change failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );
}
