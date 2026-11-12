import type { FastifyInstance } from "fastify";
import { DeviceAuthorization } from "../auth/device-auth.js";
import {
  BudgetMaintenanceInputError,
  BudgetScheduleChangedError,
} from "../actual/budget-maintenance-api.js";
import type {
  BudgetMaintenanceRouteDependencies,
  BudgetScheduleRouteParams,
  BudgetScheduleActionBody,
  BudgetReadHeaders,
  BudgetScheduleEdit,
  JsonValue,
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

export function registerBudgetScheduleRoutes(
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
    Params: BudgetScheduleRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetScheduleActionBody;
  }>(
    "/api/v1/budgets/:budgetId/schedules/:scheduleId/actions",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "manageBudgetSchedule",
        summary: "Edit or manage an Actual Budget schedule",
        security: [{ bearerAuth: [] }],
        params: {
          type: "object",
          properties: {
            budgetId: paramsSchema.properties.budgetId,
            scheduleId: {
              type: "string",
              minLength: 1,
              maxLength: 500,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
          },
          required: ["budgetId", "scheduleId"],
          additionalProperties: false,
        },
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            action: {
              type: "string",
              enum: ["rename", "edit", "post", "skip", "delete"],
            },
            name: {
              type: "string",
              minLength: 1,
              maxLength: 100,
              pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
            },
            accountId: { type: "string", minLength: 1, maxLength: 500 },
            payeeId: { type: "string", minLength: 1, maxLength: 500 },
            amountMinor: {
              type: "integer",
              minimum: -1_000_000_000,
              maximum: 1_000_000_000,
            },
            amountRangeMinor: {
              type: "object",
              properties: {
                num1: {
                  type: "integer",
                  minimum: -1_000_000_000,
                  maximum: 1_000_000_000,
                },
                num2: {
                  type: "integer",
                  minimum: -1_000_000_000,
                  maximum: 1_000_000_000,
                },
              },
              required: ["num1", "num2"],
              additionalProperties: false,
            },
            amountOp: { type: "string", enum: ["is", "isapprox", "isbetween"] },
            expectedNextDate: {
              type: "string",
              format: "date",
              pattern: "^\\d{4}-\\d{2}-\\d{2}$",
              description:
                "For post and skip, the exact next date shown by the client.",
            },
            date: {
              oneOf: [
                {
                  type: "string",
                  pattern: "^\\d{4}-\\d{2}-\\d{2}$",
                },
                {
                  type: "object",
                  properties: {
                    frequency: {
                      type: "string",
                      enum: ["daily", "weekly", "monthly", "yearly"],
                    },
                    interval: { type: "integer", minimum: 1, maximum: 999 },
                    patterns: {
                      type: "array",
                      maxItems: 31,
                      items: {
                        type: "object",
                        properties: {
                          value: { type: "integer", minimum: -31, maximum: 31 },
                          type: {
                            type: "string",
                            enum: [
                              "SU",
                              "MO",
                              "TU",
                              "WE",
                              "TH",
                              "FR",
                              "SA",
                              "day",
                            ],
                          },
                        },
                        required: ["value", "type"],
                        additionalProperties: false,
                      },
                    },
                    skipWeekend: { type: "boolean" },
                    start: {
                      type: "string",
                      pattern: "^\\d{4}-\\d{2}-\\d{2}$",
                    },
                    endMode: {
                      type: "string",
                      enum: ["never", "after_n_occurrences", "on_date"],
                    },
                    endOccurrences: {
                      type: "integer",
                      minimum: 1,
                      maximum: 100_000,
                    },
                    endDate: {
                      type: "string",
                      pattern: "^\\d{4}-\\d{2}-\\d{2}$",
                    },
                    weekendSolveMode: {
                      type: "string",
                      enum: ["before", "after"],
                    },
                  },
                  required: ["frequency", "start"],
                  additionalProperties: false,
                },
              ],
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
      const body = request.body as BudgetScheduleActionBody;
      const { action } = body;
      const needsExpectedNextDate = action === "post" || action === "skip";
      const name = body.name?.trim();
      const edit: BudgetScheduleEdit = {
        ...(name === undefined ? {} : { name }),
        ...(body.accountId === undefined ? {} : { accountId: body.accountId }),
        ...(body.payeeId === undefined ? {} : { payeeId: body.payeeId }),
        ...(body.amountMinor === undefined
          ? {}
          : { amountMinor: body.amountMinor }),
        ...(body.amountRangeMinor === undefined
          ? {}
          : { amountRangeMinor: body.amountRangeMinor }),
        ...(body.amountOp === undefined ? {} : { amountOp: body.amountOp }),
        ...(body.date === undefined ? {} : { date: body.date }),
      };
      const hasEditFields = Object.keys(edit).length > 0;
      if (
        (action === "rename" && !name) ||
        (action === "rename" &&
          Object.keys(edit).some((field) => field !== "name")) ||
        (action === "edit" && !hasEditFields) ||
        (needsExpectedNextDate && body.expectedNextDate === undefined) ||
        (!needsExpectedNextDate && body.expectedNextDate !== undefined) ||
        (!(["rename", "edit"] as string[]).includes(action) && hasEditFields)
      ) {
        return invalidBudgetRequest(reply);
      }
      try {
        const patch: { [key: string]: JsonValue } = { action };
        if (needsExpectedNextDate) {
          patch.expectedNextDate = body.expectedNextDate!;
        }
        if (action === "rename") patch.name = name!;
        if (action === "edit") {
          for (const [field, value] of Object.entries(edit)) {
            patch[field] = value as JsonValue;
          }
        }
        const result = await runMaintenanceOperation(
          journal,
          request,
          device,
          request.params.budgetId,
          `schedule-${action}`,
          request.params.scheduleId,
          patch,
          async () => {
            const password = request.headers["x-budget-encryption-password"];
            switch (action) {
              case "rename":
                await maintenanceApi.renameSchedule(
                  request.params.budgetId,
                  request.params.scheduleId,
                  name!,
                  password,
                );
                break;
              case "edit":
                await maintenanceApi.editSchedule(
                  request.params.budgetId,
                  request.params.scheduleId,
                  edit,
                  password,
                );
                break;
              case "post":
                await maintenanceApi.postSchedule(
                  request.params.budgetId,
                  request.params.scheduleId,
                  body.expectedNextDate!,
                  password,
                );
                break;
              case "skip":
                await maintenanceApi.skipSchedule(
                  request.params.budgetId,
                  request.params.scheduleId,
                  body.expectedNextDate!,
                  password,
                );
                break;
              case "delete":
                await maintenanceApi.deleteSchedule(
                  request.params.budgetId,
                  request.params.scheduleId,
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
          `schedule_${action}`,
          "success",
          request.params.scheduleId,
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof BudgetMaintenanceInputError) {
          logBudgetMaintenanceEvent(
            request,
            device,
            request.params.budgetId,
            `schedule_${action}`,
            "rejected",
            request.params.scheduleId,
          );
          return reply
            .code(
              error instanceof MaintenanceOperationConflictError ||
                error instanceof BudgetScheduleChangedError
                ? 409
                : 400,
            )
            .send({
              error: {
                code:
                  error instanceof MaintenanceOperationConflictError
                    ? error.code
                    : error instanceof BudgetScheduleChangedError
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
          `schedule_${action}`,
          "failed",
          request.params.scheduleId,
        );
        reportTransactionFailure("Actual schedule action failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );
}
