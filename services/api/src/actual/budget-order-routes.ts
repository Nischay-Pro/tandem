import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import { DeviceAuthorization } from "../auth/device-auth.js";
import { BudgetMaintenanceInputError } from "./budget-maintenance-api.js";
import type {
  BudgetMaintenanceRouteDependencies,
  BudgetReadHeaders,
  BudgetRouteParams,
} from "./budget-maintenance-support.js";
import {
  logBudgetMaintenanceEvent,
  MaintenanceOperationConflictError,
  runMaintenanceOperation,
} from "./budget-maintenance-support.js";
import type { JsonValue } from "../write-journal/contracts.js";
import {
  commonResponseSchema,
  headersSchema,
  paramsSchema,
} from "./budget-maintenance-schemas.js";

/** Largest list a single order request may carry. */
export const MAX_ORDER_ITEMS = 1_000;
/** Most category groups one order request may reorder. */
export const MAX_ORDER_LISTS = 200;

interface BudgetOrderChangeBody {
  baseOrder: string[];
  order: string[];
}

interface BudgetOrderBody {
  categoryGroups?: BudgetOrderChangeBody;
  categories?: Array<BudgetOrderChangeBody & { groupId: string }>;
  accounts?: BudgetOrderChangeBody;
}

const idSchema = {
  type: "string",
  minLength: 1,
  maxLength: 500,
  pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
};

const orderListSchema = {
  type: "array",
  maxItems: MAX_ORDER_ITEMS,
  uniqueItems: true,
  items: idSchema,
};

const orderChangeProperties = {
  baseOrder: {
    ...orderListSchema,
    description:
      "The order this device showed before the user started reordering.",
  },
  order: {
    ...orderListSchema,
    description:
      "The order the user chose. It must contain the same items as baseOrder.",
  },
};

const orderChangeSchema = {
  type: "object",
  properties: orderChangeProperties,
  required: ["baseOrder", "order"],
  additionalProperties: false,
};

const orderBodySchema = {
  type: "object",
  properties: {
    categoryGroups: {
      ...orderChangeSchema,
      description:
        "Category group order. Income groups always stay after expense groups.",
    },
    categories: {
      type: "array",
      maxItems: MAX_ORDER_LISTS,
      description: "Category order within each listed group.",
      items: {
        type: "object",
        properties: { groupId: idSchema, ...orderChangeProperties },
        required: ["groupId", "baseOrder", "order"],
        additionalProperties: false,
      },
    },
    accounts: {
      ...orderChangeSchema,
      description:
        "Account order. Accounts are reordered within their section: open on-budget, open off-budget, or closed.",
    },
  },
  minProperties: 1,
  additionalProperties: false,
};

const savedOrderSchema = {
  type: "array",
  items: { type: "string", minLength: 1 },
};

const orderResponseSchema = {
  ...commonResponseSchema,
  200: {
    type: "object",
    properties: {
      status: { type: "string", const: "ok" },
      categoryGroups: savedOrderSchema,
      categories: {
        type: "array",
        items: {
          type: "object",
          properties: { groupId: { type: "string" }, order: savedOrderSchema },
          required: ["groupId", "order"],
          additionalProperties: false,
        },
      },
      accounts: savedOrderSchema,
    },
    required: ["status"],
    additionalProperties: false,
  },
};

export function registerBudgetOrderRoutes(
  app: FastifyInstance,
  dependencies: BudgetMaintenanceRouteDependencies,
): void {
  app.put<{
    Params: BudgetRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetOrderBody;
  }>(
    "/api/v1/budgets/:budgetId/order",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "setBudgetOrder",
        summary:
          "Save reordered category groups, categories, and accounts in one request",
        description:
          "Moves only the items this device reordered between each baseOrder and order, on top of " +
          "Actual's current order, so reorders made meanwhile on other devices are kept. Items " +
          "deleted since baseOrder are skipped, and new items keep their place. All lists are saved " +
          "together under one idempotency key.",
        security: [{ bearerAuth: [] }],
        params: paramsSchema,
        headers: headersSchema,
        body: orderBodySchema,
        response: orderResponseSchema,
      },
      attachValidation: true,
    },
    (request, reply) => handleOrderRequest(dependencies, request, reply),
  );
}

async function handleOrderRequest(
  dependencies: BudgetMaintenanceRouteDependencies,
  request: FastifyRequest<{
    Params: BudgetRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetOrderBody;
  }>,
  reply: FastifyReply,
): Promise<FastifyReply> {
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
  const device = await requireDevice(deviceAuth, request, reply);
  if (!device) return reply;
  if (request.validationError) return invalidBudgetRequest(reply);
  const { budgetId } = request.params;
  if (!DeviceAuthorization.canAccessBudget(device, budgetId))
    return transactionNotFound(reply);
  if (!maintenanceApi) return actualNotConfigured(reply);
  if (!journal) return actualNotConfigured(reply);
  const log = (outcome: "success" | "rejected" | "failed") =>
    logBudgetMaintenanceEvent(
      request,
      device,
      budgetId,
      "order",
      outcome,
      budgetId,
    );
  const body = request.body;
  try {
    const result = await runMaintenanceOperation(
      journal,
      request,
      device,
      budgetId,
      "order",
      null,
      body as unknown as { [key: string]: JsonValue },
      async () => ({
        status: "ok",
        ...((await maintenanceApi.setOrders(
          budgetId,
          body,
          request.headers["x-budget-encryption-password"],
        )) as { [key: string]: JsonValue }),
      }),
    );
    log("success");
    reply.header("cache-control", "no-store");
    return reply.code(200).send(result);
  } catch (error) {
    if (error instanceof BudgetMaintenanceInputError) {
      log("rejected");
      const conflict = error instanceof MaintenanceOperationConflictError;
      return reply.code(conflict ? 409 : 400).send({
        error: {
          code: conflict ? error.code : "invalid_request",
          message: error.message,
        },
      });
    }
    log("failed");
    reportTransactionFailure("Actual reorder failed", error);
    return actualRequestFailed(reply, error);
  }
}
