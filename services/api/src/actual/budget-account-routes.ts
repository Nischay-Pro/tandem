import type { FastifyInstance } from "fastify";
import { DeviceAuthorization } from "../auth/device-auth.js";
import { BudgetMaintenanceInputError } from "../actual/budget-maintenance-api.js";
import type {
  BudgetMaintenanceRouteDependencies,
  BudgetRouteParams,
  BudgetAccountRouteParams,
  BudgetPayeeRouteParams,
  BudgetAccountCreateBody,
  BudgetAccountCloseBody,
  BudgetAccountRenameBody,
  BudgetAccountMoveBody,
  BudgetPayeeCreateBody,
  BudgetPayeeActionBody,
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
  accountParamsSchema,
  headersSchema,
  commonResponseSchema,
  accountCreatedResponse,
  errorSchema,
} from "./budget-maintenance-schemas.js";

export function registerBudgetAccountRoutes(
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
    Body: BudgetAccountCreateBody;
  }>(
    "/api/v1/budgets/:budgetId/accounts",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "createBudgetAccount",
        summary: "Create an Actual Budget account",
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
            offbudget: { type: "boolean" },
            accountGroupId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
            },
          },
          required: ["name", "offbudget"],
          additionalProperties: false,
        },
        response: {
          ...commonResponseSchema,
          200: accountCreatedResponse,
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
          "account-create",
          null,
          {
            name,
            offbudget: request.body.offbudget,
            accountGroupId: request.body.accountGroupId ?? null,
          },
          async () => {
            const accountId = await maintenanceApi.createAccount(
              request.params.budgetId,
              name,
              request.body.offbudget,
              request.body.accountGroupId ?? null,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok", accountId };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "account_create",
          "success",
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "account_create",
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
          "account_create",
          "failed",
        );
        reportTransactionFailure("Actual account creation failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.post<{
    Params: BudgetPayeeRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetPayeeActionBody;
  }>(
    "/api/v1/budgets/:budgetId/payees/:payeeId/actions",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "manageBudgetPayee",
        summary: "Rename, merge, or delete an Actual Budget payee",
        security: [{ bearerAuth: [] }],
        params: {
          type: "object",
          properties: {
            budgetId: paramsSchema.properties.budgetId,
            payeeId: {
              type: "string",
              minLength: 1,
              maxLength: 500,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
          },
          required: ["budgetId", "payeeId"],
          additionalProperties: false,
        },
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            action: {
              type: "string",
              enum: ["rename", "merge", "delete", "default-category"],
            },
            name: {
              type: "string",
              minLength: 1,
              maxLength: 100,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
            targetPayeeId: {
              type: "string",
              minLength: 1,
              maxLength: 500,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
            categoryId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
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
      const { action } = request.body;
      const name = (request.body.name ?? "").trim();
      const targetPayeeId = request.body.targetPayeeId;
      const categoryId = request.body.categoryId;
      const hasCategoryId = Object.prototype.hasOwnProperty.call(
        request.body,
        "categoryId",
      );
      if (
        (action === "rename" && !name) ||
        (action !== "rename" && request.body.name !== undefined) ||
        (action === "merge" && targetPayeeId === undefined) ||
        (action !== "merge" && targetPayeeId !== undefined) ||
        (action === "default-category" && !hasCategoryId) ||
        (action !== "default-category" && hasCategoryId)
      ) {
        return invalidBudgetRequest(reply);
      }
      try {
        const patch: { [key: string]: JsonValue } = { action };
        if (action === "rename") patch.name = name;
        if (action === "merge") patch.targetPayeeId = targetPayeeId!;
        if (action === "default-category")
          patch.categoryId = categoryId ?? null;
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          `payee-${action}`,
          request.params.payeeId,
          patch,
          async () => {
            const password = request.headers["x-budget-encryption-password"];
            switch (action) {
              case "rename":
                await maintenanceApi.renamePayee(
                  request.params.budgetId,
                  request.params.payeeId,
                  name,
                  password,
                );
                break;
              case "merge":
                await maintenanceApi.mergePayee(
                  request.params.budgetId,
                  request.params.payeeId,
                  targetPayeeId!,
                  password,
                );
                break;
              case "delete":
                await maintenanceApi.deletePayee(
                  request.params.budgetId,
                  request.params.payeeId,
                  password,
                );
                break;
              case "default-category":
                await maintenanceApi.setPayeeDefaultCategory(
                  request.params.budgetId,
                  request.params.payeeId,
                  categoryId ?? null,
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
          `payee_${action}`,
          "success",
          request.params.payeeId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            `payee_${action}`,
            "rejected",
            request.params.payeeId,
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
          `payee_${action}`,
          "failed",
          request.params.payeeId,
        );
        reportTransactionFailure("Actual payee action failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.post<{
    Params: BudgetAccountRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetAccountCloseBody;
  }>(
    "/api/v1/budgets/:budgetId/accounts/:accountId/close",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "closeBudgetAccount",
        summary: "Close an Actual Budget account",
        security: [{ bearerAuth: [] }],
        params: accountParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            transferAccountId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
            },
            transferCategoryId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
            },
          },
          required: ["transferAccountId", "transferCategoryId"],
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
          "account-close",
          request.params.accountId,
          {
            transferAccountId: request.body.transferAccountId,
            transferCategoryId: request.body.transferCategoryId,
          },
          async () => {
            await maintenanceApi.closeAccount(
              request.params.budgetId,
              request.params.accountId,
              request.body.transferAccountId,
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
          "account_close",
          "success",
          request.params.accountId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "account_close",
            "rejected",
            request.params.accountId,
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
          "account_close",
          "failed",
          request.params.accountId,
        );
        reportTransactionFailure("Actual account close failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.patch<{
    Params: BudgetAccountRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetAccountRenameBody;
  }>(
    "/api/v1/budgets/:budgetId/accounts/:accountId",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "renameBudgetAccount",
        summary: "Rename an Actual Budget account",
        security: [{ bearerAuth: [] }],
        params: accountParamsSchema,
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
          "account-rename",
          request.params.accountId,
          { name },
          async () => {
            await maintenanceApi.renameAccount(
              request.params.budgetId,
              request.params.accountId,
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
          "account_rename",
          "success",
          request.params.accountId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "account_rename",
            "rejected",
            request.params.accountId,
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
          "account_rename",
          "failed",
          request.params.accountId,
        );
        reportTransactionFailure("Actual account rename failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.post<{
    Params: BudgetAccountRouteParams;
    Headers: BudgetReadHeaders;
  }>(
    "/api/v1/budgets/:budgetId/accounts/:accountId/reopen",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "reopenBudgetAccount",
        summary: "Reopen an Actual Budget account",
        security: [{ bearerAuth: [] }],
        params: accountParamsSchema,
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
          "account-reopen",
          request.params.accountId,
          {},
          async () => {
            await maintenanceApi.reopenAccount(
              request.params.budgetId,
              request.params.accountId,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "account_reopen",
          "success",
          request.params.accountId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "account_reopen",
            "rejected",
            request.params.accountId,
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
          "account_reopen",
          "failed",
          request.params.accountId,
        );
        reportTransactionFailure("Actual account reopen failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.post<{
    Params: BudgetAccountRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetAccountMoveBody;
  }>(
    "/api/v1/budgets/:budgetId/accounts/:accountId/move",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "moveBudgetAccount",
        summary: "Reorder an Actual Budget account",
        security: [{ bearerAuth: [] }],
        params: accountParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            targetAccountId: {
              type: ["string", "null"],
              minLength: 1,
              maxLength: 500,
            },
          },
          required: ["targetAccountId"],
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
          "account-move",
          request.params.accountId,
          { targetAccountId: request.body.targetAccountId },
          async () => {
            await maintenanceApi.moveAccount(
              request.params.budgetId,
              request.params.accountId,
              request.body.targetAccountId,
              request.headers["x-budget-encryption-password"],
            );
            return { status: "ok" };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "account_move",
          "success",
          request.params.accountId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "account_move",
            "rejected",
            request.params.accountId,
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
          "account_move",
          "failed",
          request.params.accountId,
        );
        reportTransactionFailure("Actual account reorder failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.post<{
    Params: BudgetRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetPayeeCreateBody;
  }>(
    "/api/v1/budgets/:budgetId/payees",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "createBudgetPayee",
        summary: "Create an Actual Budget payee",
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
          },
          required: ["name"],
          additionalProperties: false,
        },
        response: {
          200: {
            type: "object",
            properties: {
              status: { type: "string", enum: ["ok"] },
              payeeId: { type: "string", minLength: 1, maxLength: 500 },
            },
            required: ["status", "payeeId"],
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
          "payee-create",
          null,
          { name },
          async () => ({
            payeeId: await maintenanceApi.createPayee(
              request.params.budgetId,
              name,
              request.headers["x-budget-encryption-password"],
            ),
          }),
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "payee_create",
          "success",
          result.payeeId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send({ status: "ok", payeeId: result.payeeId });
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "payee_create",
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
          "payee_create",
          "failed",
        );
        reportTransactionFailure("Actual payee creation failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );
}
