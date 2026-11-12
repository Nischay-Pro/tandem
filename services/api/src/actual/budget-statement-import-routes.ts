import type { FastifyInstance } from "fastify";
import { DeviceAuthorization } from "../auth/device-auth.js";
import { BudgetMaintenanceInputError } from "../actual/budget-maintenance-api.js";
import { MAX_TRANSACTION_AMOUNT } from "../actual/transaction-reconciliation.js";
import type {
  BudgetMaintenanceRouteDependencies,
  BudgetRouteParams,
  BudgetStatementImportBody,
  BudgetStatementImportPreviewBody,
  BudgetReadHeaders,
} from "./budget-maintenance-support.js";
import {
  MaintenanceOperationConflictError,
  runMaintenanceOperation,
  statementImportJournalPatch,
  logBudgetMaintenanceEvent,
} from "./budget-maintenance-support.js";
import {
  accountParamsSchema,
  headersSchema,
  budgetEncryptionHeaderSchema,
  errorSchema,
} from "./budget-maintenance-schemas.js";

export function registerBudgetStatementImportRoutes(
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
  app.post<{
    Params: BudgetRouteParams & { accountId: string };
    Headers: BudgetReadHeaders;
    Body: BudgetStatementImportPreviewBody;
  }>(
    "/api/v1/budgets/:budgetId/accounts/:accountId/transactions/import/preview",
    {
      bodyLimit: 256 * 1024,
      schema: {
        tags: ["Transactions"],
        operationId: "previewBudgetAccountStatementImport",
        summary:
          "Check which statement references already exist in an Actual Budget account",
        security: [{ bearerAuth: [] }],
        params: accountParamsSchema,
        headers: budgetEncryptionHeaderSchema,
        body: {
          type: "object",
          properties: {
            importedIds: {
              type: "array",
              minItems: 1,
              maxItems: 1_000,
              uniqueItems: true,
              items: { type: "string", minLength: 1, maxLength: 200 },
            },
          },
          required: ["importedIds"],
          additionalProperties: false,
        },
        response: {
          200: {
            type: "object",
            properties: {
              status: { type: "string", const: "ok" },
              existingImportedIds: {
                type: "array",
                uniqueItems: true,
                items: { type: "string", minLength: 1, maxLength: 200 },
              },
            },
            required: ["status", "existingImportedIds"],
            additionalProperties: false,
          },
          400: errorSchema,
          401: errorSchema,
          404: errorSchema,
          502: errorSchema,
        },
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidBudgetRequest(reply);
      if (
        !DeviceAuthorization.canAccessBudget(device, request.params.budgetId)
      ) {
        return transactionNotFound(reply);
      }
      if (!maintenanceApi) return actualNotConfigured(reply);
      try {
        const preview = await maintenanceApi.previewStatementImportTransactions(
          request.params.budgetId,
          request.params.accountId,
          request.body.importedIds,
          request.headers["x-budget-encryption-password"],
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send({
          status: "ok",
          existingImportedIds: preview.existingImportedIds,
        });
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          return reply.code(400).send({
            error: { code: "invalid_request", message: error.message },
          });
        }
        reportTransactionFailure(
          "Actual statement import preview failed",
          error,
        );
        return reply.code(502).send({
          error: {
            code: "statement_preview_failed",
            message:
              "Actual could not check which statement rows are already imported. Try again.",
          },
        });
      }
    },
  );

  app.post<{
    Params: BudgetRouteParams & { accountId: string };
    Headers: BudgetReadHeaders;
    Body: BudgetStatementImportBody;
  }>(
    "/api/v1/budgets/:budgetId/accounts/:accountId/transactions/import",
    {
      bodyLimit: 2 * 1024 * 1024,
      schema: {
        tags: ["Transactions"],
        operationId: "importBudgetAccountTransactions",
        summary: "Import a parsed bank statement into an Actual Budget account",
        security: [{ bearerAuth: [] }],
        params: accountParamsSchema,
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            transactions: {
              type: "array",
              minItems: 1,
              maxItems: 1_000,
              items: {
                type: "object",
                properties: {
                  date: { type: "string", format: "date" },
                  amountMinor: {
                    type: "integer",
                    minimum: -MAX_TRANSACTION_AMOUNT,
                    maximum: MAX_TRANSACTION_AMOUNT,
                    not: { const: 0 },
                  },
                  payeeName: { type: "string", minLength: 1, maxLength: 200 },
                  notes: { type: "string", maxLength: 500 },
                  importedId: { type: "string", minLength: 1, maxLength: 200 },
                },
                required: ["date", "amountMinor", "payeeName", "importedId"],
                additionalProperties: false,
              },
            },
          },
          required: ["transactions"],
          additionalProperties: false,
        },
        response: {
          200: {
            type: "object",
            properties: {
              status: { type: "string", const: "ok" },
              added: { type: "integer", minimum: 0 },
              updated: { type: "integer", minimum: 0 },
              errorCount: { type: "integer", minimum: 0 },
            },
            required: ["status", "added", "updated", "errorCount"],
            additionalProperties: false,
          },
          400: errorSchema,
          401: errorSchema,
          404: errorSchema,
          409: errorSchema,
          502: errorSchema,
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
      const transactions = request.body.transactions;
      const uniqueImportedIds = new Set(
        transactions.map((transaction) => transaction.importedId),
      );
      if (uniqueImportedIds.size !== transactions.length)
        return reply.code(400).send({
          error: {
            code: "duplicate_imported_id",
            message: "Statement rows must have unique references",
          },
        });
      try {
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          "statement-import",
          request.params.accountId,
          statementImportJournalPatch(transactions),
          async () => {
            const imported = await maintenanceApi.importStatementTransactions(
              request.params.budgetId,
              request.params.accountId,
              transactions,
              request.headers["x-budget-encryption-password"],
            );
            return {
              status: "ok",
              added: imported.added,
              updated: imported.updated,
              errorCount: imported.errors.length,
            };
          },
        );
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "statement_import",
          "success",
          request.params.accountId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof MaintenanceOperationConflictError) {
          return reply.code(409).send({
            error: { code: error.code, message: error.message },
          });
        }
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            "statement_import",
            "rejected",
            request.params.accountId,
          );
          return reply.code(400).send({
            error: { code: "invalid_request", message: error.message },
          });
        }
        logBudgetMaintenanceEvent(
          request,
          device,
          request.params.budgetId,
          "statement_import",
          "failed",
          request.params.accountId,
        );
        reportTransactionFailure("Actual statement import failed", error);
        return reply.code(502).send({
          error: {
            code: "actual_operation_failed",
            message:
              "Actual could not import these transactions. Review the statement and try again.",
          },
        });
      }
    },
  );
}
