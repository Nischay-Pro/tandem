import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import { DeviceAuthorization, type DeviceRecord } from "../auth/device-auth.js";
import {
  BudgetProjectionInputError,
  BUDGET_ENTITY_TYPES,
} from "./contracts.js";
import type { BudgetReadApiPort } from "./budget-read-api.js";
import { API_ERROR_SCHEMA_REF } from "../openapi/error-responses.js";

const errorSchema = API_ERROR_SCHEMA_REF;

interface BudgetRouteParams {
  budgetId: string;
}
interface BudgetChangeQuery {
  generation: string;
  cursor: string;
  throughCursor?: string;
  limit?: number;
}
interface BudgetSnapshotPageQuery {
  generation?: string;
  cursor?: string;
  afterType?: (typeof BUDGET_ENTITY_TYPES)[number];
  afterId?: string;
  limit?: number;
}
interface BudgetReadHeaders {
  "x-budget-encryption-password"?: string;
  "idempotency-key"?: string;
}

export interface BudgetReadRouteDependencies {
  deviceAuth: DeviceAuthorization;
  budgetReadApi: BudgetReadApiPort | undefined;
  requireDevice: (
    deviceAuth: DeviceAuthorization,
    request: FastifyRequest,
    reply: FastifyReply,
  ) => Promise<DeviceRecord | undefined>;
  invalidBudgetRequest: (reply: FastifyReply) => FastifyReply;
  transactionNotFound: (reply: FastifyReply) => FastifyReply;
  actualNotConfigured: (reply: FastifyReply) => FastifyReply;
  budgetReadFailed: (reply: FastifyReply, error?: unknown) => FastifyReply;
  reportTransactionFailure: (context: string, error?: unknown) => void;
}

export function registerBudgetReadRoutes(
  app: FastifyInstance,
  dependencies: BudgetReadRouteDependencies,
): void {
  const {
    deviceAuth,
    budgetReadApi,
    requireDevice,
    invalidBudgetRequest,
    transactionNotFound,
    actualNotConfigured,
    budgetReadFailed,
    reportTransactionFailure,
  } = dependencies;
  const budgetParamsSchema = {
    type: "object",
    properties: {
      budgetId: {
        type: "string",
        minLength: 1,
        maxLength: 500,
        pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
      },
    },
    required: ["budgetId"],
    additionalProperties: false,
  };
  const encryptionHeadersSchema = {
    type: "object",
    properties: {
      "x-budget-encryption-password": {
        type: "string",
        minLength: 1,
        maxLength: 1_024,
      },
    },
    additionalProperties: true,
  };
  const recordSchema = {
    type: "object",
    properties: {
      type: {
        type: "string",
        enum: [
          "account",
          "account-group",
          "category",
          "category-group",
          "payee",
          "transaction",
          "budget-month",
        ],
      },
      id: { type: "string" },
      revision: { type: "string", pattern: "^[a-f0-9]{64}$" },
      body: { type: "object", additionalProperties: true },
    },
    required: ["type", "id", "revision", "body"],
    additionalProperties: false,
  };
  const displaySettingsSchema = {
    type: "object",
    properties: {
      budgetType: { type: "string", enum: ["envelope", "tracking"] },
      currencyCode: { type: "string", pattern: "^[A-Z]{3}$" },
      currencySymbol: { type: "string" },
      currencyFractionDigits: { type: "integer", minimum: 0 },
      numberFormat: {
        anyOf: [
          {
            type: "string",
            enum: [
              "comma-dot",
              "dot-comma",
              "space-comma",
              "apostrophe-dot",
              "comma-dot-in",
            ],
          },
          { type: "null" },
        ],
      },
      hideFraction: { type: "boolean" },
      currencySymbolPosition: {
        anyOf: [
          { type: "string", enum: ["before", "after"] },
          { type: "null" },
        ],
      },
      currencySpaceBetweenAmountAndSymbol: { type: "boolean" },
    },
    required: [
      "budgetType",
      "currencyCode",
      "currencySymbol",
      "currencyFractionDigits",
      "numberFormat",
      "hideFraction",
      "currencySymbolPosition",
      "currencySpaceBetweenAmountAndSymbol",
    ],
    additionalProperties: false,
  };
  const snapshotSchema = {
    type: "object",
    properties: {
      budgetId: { type: "string" },
      generation: { type: "string", format: "uuid" },
      cursor: { type: "string", pattern: "^(0|[1-9][0-9]*)$" },
      records: { type: "array", items: recordSchema },
      displaySettings: {
        anyOf: [displaySettingsSchema, { type: "null" }],
      },
    },
    required: [
      "budgetId",
      "generation",
      "cursor",
      "records",
      "displaySettings",
    ],
    additionalProperties: false,
  };
  const snapshotPageSchema = {
    type: "object",
    properties: {
      budgetId: { type: "string" },
      generation: { type: "string", format: "uuid" },
      cursor: { type: "string", pattern: "^(0|[1-9][0-9]*)$" },
      records: { type: "array", items: recordSchema },
      hasMore: { type: "boolean" },
      resetRequired: { type: "boolean" },
      nextAfterType: {
        anyOf: [
          { type: "string", enum: BUDGET_ENTITY_TYPES },
          { type: "null" },
        ],
      },
      nextAfterId: { type: ["string", "null"] },
      displaySettings: {
        anyOf: [displaySettingsSchema, { type: "null" }],
      },
    },
    required: [
      "budgetId",
      "generation",
      "cursor",
      "records",
      "hasMore",
      "resetRequired",
      "nextAfterType",
      "nextAfterId",
      "displaySettings",
    ],
    additionalProperties: false,
  };
  const changeSchema = {
    type: "object",
    properties: {
      cursor: { type: "string", pattern: "^[1-9][0-9]*$" },
      type: recordSchema.properties.type,
      id: { type: "string" },
      operation: { type: "string", enum: ["upsert", "delete"] },
      revision: { type: "string", pattern: "^[a-f0-9]{64}$" },
      body: {
        anyOf: [
          { type: "object", additionalProperties: true },
          { type: "null" },
        ],
      },
    },
    required: ["cursor", "type", "id", "operation", "revision", "body"],
    additionalProperties: false,
  };
  const changePageSchema = {
    type: "object",
    properties: {
      budgetId: { type: "string" },
      generation: { type: "string", format: "uuid" },
      currentCursor: { type: "string", pattern: "^(0|[1-9][0-9]*)$" },
      throughCursor: { type: "string", pattern: "^(0|[1-9][0-9]*)$" },
      nextCursor: { type: "string", pattern: "^(0|[1-9][0-9]*)$" },
      hasMore: { type: "boolean" },
      resetRequired: { type: "boolean" },
      changes: { type: "array", items: changeSchema },
      displaySettings: {
        anyOf: [displaySettingsSchema, { type: "null" }],
      },
    },
    required: [
      "budgetId",
      "generation",
      "currentCursor",
      "throughCursor",
      "nextCursor",
      "hasMore",
      "resetRequired",
      "changes",
      "displaySettings",
    ],
    additionalProperties: false,
  };

  app.get(
    "/api/v1/budgets",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "listBudgets",
        summary: "List Actual budgets available to the authenticated device",
        security: [{ bearerAuth: [] }],
        response: {
          200: {
            type: "object",
            properties: {
              budgets: {
                type: "array",
                items: {
                  type: "object",
                  properties: {
                    budgetId: { type: "string" },
                    name: { type: "string" },
                    isEncrypted: { type: "boolean" },
                  },
                  required: ["budgetId", "name", "isEncrypted"],
                  additionalProperties: false,
                },
              },
            },
            required: ["budgets"],
            additionalProperties: false,
          },
          401: errorSchema,
          502: errorSchema,
          503: errorSchema,
        },
      },
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      const listBudgets = budgetReadApi?.listBudgets;
      if (!listBudgets) return actualNotConfigured(reply);

      try {
        const budgets = await listBudgets();
        const seenBudgetIds = new Set<string>();
        const authorizedBudgets = budgets.filter((budget) => {
          if (
            !DeviceAuthorization.canAccessBudget(device, budget.budgetId) ||
            seenBudgetIds.has(budget.budgetId)
          ) {
            return false;
          }
          seenBudgetIds.add(budget.budgetId);
          return true;
        });
        reply.header("cache-control", "no-store");
        return reply.code(200).send({ budgets: authorizedBudgets });
      } catch (error) {
        reportTransactionFailure("Budget catalog read failed", error);
        return budgetReadFailed(reply, error);
      }
    },
  );

  app.get<{
    Params: BudgetRouteParams;
    Headers: BudgetReadHeaders;
  }>(
    "/api/v1/budgets/:budgetId/snapshot",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "getBudgetSnapshot",
        summary: "Refresh and read a complete budget snapshot",
        security: [{ bearerAuth: [] }],
        params: budgetParamsSchema,
        headers: encryptionHeadersSchema,
        response: {
          200: snapshotSchema,
          400: errorSchema,
          401: errorSchema,
          404: errorSchema,
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
      if (
        !DeviceAuthorization.canAccessBudget(device, request.params.budgetId)
      ) {
        return transactionNotFound(reply);
      }
      if (!budgetReadApi) return actualNotConfigured(reply);

      try {
        const snapshot = await budgetReadApi.getSnapshot(
          request.params.budgetId,
          request.headers["x-budget-encryption-password"],
        );
        reply.header("cache-control", "no-store");
        return reply.code(200).send(snapshot);
      } catch (error) {
        if (error instanceof BudgetProjectionInputError) {
          return invalidBudgetRequest(reply);
        }
        reportTransactionFailure("Budget snapshot read failed", error);
        return budgetReadFailed(reply, error);
      }
    },
  );

  app.get<{
    Params: BudgetRouteParams;
    Querystring: BudgetSnapshotPageQuery;
    Headers: BudgetReadHeaders;
  }>(
    "/api/v1/budgets/:budgetId/snapshot-pages",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "getBudgetSnapshotPage",
        summary: "Read a bounded page from the current budget snapshot",
        security: [{ bearerAuth: [] }],
        params: budgetParamsSchema,
        headers: encryptionHeadersSchema,
        querystring: {
          type: "object",
          properties: {
            generation: { type: "string", format: "uuid" },
            cursor: { type: "string", pattern: "^(0|[1-9][0-9]*)$" },
            afterType: { type: "string", enum: BUDGET_ENTITY_TYPES },
            afterId: { type: "string", minLength: 1, maxLength: 500 },
            limit: { type: "integer", minimum: 1, maximum: 2_000 },
          },
          additionalProperties: false,
        },
        response: {
          200: snapshotPageSchema,
          400: errorSchema,
          401: errorSchema,
          404: errorSchema,
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
      if (
        !DeviceAuthorization.canAccessBudget(device, request.params.budgetId)
      ) {
        return transactionNotFound(reply);
      }
      if (!budgetReadApi) return actualNotConfigured(reply);

      const query = request.query;
      if (
        (query.generation === undefined) !== (query.cursor === undefined) ||
        (query.afterType === undefined) !== (query.afterId === undefined) ||
        (query.afterType !== undefined && query.generation === undefined)
      ) {
        return invalidBudgetRequest(reply);
      }
      try {
        const page = await budgetReadApi.getSnapshotPage({
          budgetId: request.params.budgetId,
          ...(query.generation === undefined
            ? {}
            : { generation: query.generation }),
          ...(query.cursor === undefined ? {} : { cursor: query.cursor }),
          ...(query.afterType === undefined
            ? {}
            : { afterType: query.afterType }),
          ...(query.afterId === undefined ? {} : { afterId: query.afterId }),
          limit: query.limit ?? 2_000,
          ...(request.headers["x-budget-encryption-password"] === undefined
            ? {}
            : {
                encryptionPassword:
                  request.headers["x-budget-encryption-password"],
              }),
        });
        reply.header("cache-control", "no-store");
        return reply.code(200).send(page);
      } catch (error) {
        if (error instanceof BudgetProjectionInputError) {
          return invalidBudgetRequest(reply);
        }
        reportTransactionFailure("Budget snapshot page read failed", error);
        return budgetReadFailed(reply, error);
      }
    },
  );

  app.get<{
    Params: BudgetRouteParams;
    Querystring: BudgetChangeQuery;
    Headers: BudgetReadHeaders;
  }>(
    "/api/v1/budgets/:budgetId/changes",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "getBudgetChanges",
        summary: "Read a bounded, generation-aware budget change page",
        security: [{ bearerAuth: [] }],
        params: budgetParamsSchema,
        headers: encryptionHeadersSchema,
        querystring: {
          type: "object",
          properties: {
            generation: { type: "string", minLength: 36, maxLength: 36 },
            cursor: { type: "string", pattern: "^(0|[1-9][0-9]*)$" },
            throughCursor: {
              type: "string",
              pattern: "^(0|[1-9][0-9]*)$",
            },
            limit: { type: "integer", minimum: 1, maximum: 500 },
          },
          required: ["generation", "cursor"],
          additionalProperties: false,
        },
        response: {
          200: changePageSchema,
          400: errorSchema,
          401: errorSchema,
          404: errorSchema,
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
      if (
        !DeviceAuthorization.canAccessBudget(device, request.params.budgetId)
      ) {
        return transactionNotFound(reply);
      }
      if (!budgetReadApi) return actualNotConfigured(reply);

      try {
        const page = await budgetReadApi.getChanges({
          budgetId: request.params.budgetId,
          generation: request.query.generation,
          cursor: request.query.cursor,
          ...(request.query.throughCursor === undefined
            ? {}
            : { throughCursor: request.query.throughCursor }),
          limit: request.query.limit ?? 100,
          ...(request.headers["x-budget-encryption-password"] === undefined
            ? {}
            : {
                encryptionPassword:
                  request.headers["x-budget-encryption-password"],
              }),
        });
        reply.header("cache-control", "no-store");
        return reply.code(200).send(page);
      } catch (error) {
        if (error instanceof BudgetProjectionInputError) {
          return invalidBudgetRequest(reply);
        }
        reportTransactionFailure("Budget change feed read failed", error);
        return budgetReadFailed(reply, error);
      }
    },
  );
}
