import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import { DeviceAuthorization } from "../auth/device-auth.js";
import {
  BudgetMaintenanceInputError,
  RULE_CONDITION_OPS,
  RULE_SET_FIELDS,
  type BudgetRuleAction,
  type BudgetRuleCondition,
  type BudgetRuleDraft,
} from "../actual/budget-maintenance-api.js";
import type {
  BudgetMaintenanceRouteDependencies,
  BudgetReadHeaders,
  BudgetRouteParams,
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

interface BudgetRuleRouteParams extends BudgetRouteParams {
  ruleId: string;
}

interface BudgetRuleActionBody {
  action: "edit" | "delete";
  rule?: WireRule;
}

const idSchema = {
  type: "string",
  minLength: 1,
  maxLength: 500,
  pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
};

const amountSchema = {
  type: "integer",
  minimum: -100_000_000_000,
  maximum: 100_000_000_000,
};

const ruleSchema = {
  type: "object",
  properties: {
    stage: { type: "string", enum: ["pre", "default", "post"] },
    conditionsOp: { type: "string", enum: ["and", "or"] },
    conditions: {
      type: "array",
      minItems: 1,
      maxItems: 20,
      items: {
        type: "object",
        properties: {
          field: { type: "string", enum: Object.keys(RULE_CONDITION_OPS) },
          op: {
            type: "string",
            enum: [...new Set(Object.values(RULE_CONDITION_OPS).flat())],
          },
          text: { type: "string", maxLength: 500 },
          ids: { type: "array", minItems: 1, maxItems: 50, items: idSchema },
          amountMinor: amountSchema,
          range: {
            type: "object",
            properties: { num1: amountSchema, num2: amountSchema },
            required: ["num1", "num2"],
            additionalProperties: false,
          },
          inflow: { type: "boolean" },
          outflow: { type: "boolean" },
        },
        required: ["field", "op"],
        additionalProperties: false,
      },
    },
    actions: {
      type: "array",
      minItems: 1,
      maxItems: 20,
      items: {
        type: "object",
        properties: {
          field: { type: "string", enum: [...RULE_SET_FIELDS] },
          op: {
            type: "string",
            enum: ["set", "prepend-notes", "append-notes"],
          },
          text: { type: "string", maxLength: 500 },
          cleared: { type: "boolean" },
        },
        required: ["op"],
        additionalProperties: false,
      },
    },
  },
  required: ["stage", "conditionsOp", "conditions", "actions"],
  additionalProperties: false,
};

interface WireCondition {
  field: BudgetRuleCondition["field"];
  op: string;
  text?: string;
  ids?: string[];
  amountMinor?: number;
  range?: { num1: number; num2: number };
  inflow?: boolean;
  outflow?: boolean;
}

interface WireAction {
  field?: BudgetRuleAction["field"];
  op: BudgetRuleAction["op"];
  text?: string;
  cleared?: boolean;
}

interface WireRule {
  stage: BudgetRuleDraft["stage"];
  conditionsOp: BudgetRuleDraft["conditionsOp"];
  conditions: WireCondition[];
  actions: WireAction[];
}

function exactlyOne(values: unknown[]): boolean {
  return values.filter((value) => value !== undefined).length === 1;
}

export function ruleDraftFromWire(rule: WireRule): BudgetRuleDraft | null {
  const conditions: BudgetRuleCondition[] = [];
  for (const condition of rule.conditions) {
    const { text, ids, amountMinor, range, inflow, outflow } = condition;
    if (!exactlyOne([text, ids, amountMinor, range])) return null;
    const options =
      inflow || outflow
        ? { ...(inflow ? { inflow } : {}), ...(outflow ? { outflow } : {}) }
        : undefined;
    conditions.push({
      field: condition.field,
      op: condition.op,
      value: (ids ?? range ?? amountMinor ?? text)!,
      ...(options ? { options } : {}),
    });
  }
  const actions: BudgetRuleAction[] = [];
  for (const action of rule.actions) {
    if (!exactlyOne([action.text, action.cleared])) return null;
    actions.push({
      ...(action.field === undefined ? {} : { field: action.field }),
      op: action.op,
      value: (action.cleared ?? action.text)!,
    });
  }
  return {
    stage: rule.stage,
    conditionsOp: rule.conditionsOp,
    conditions,
    actions,
  };
}

export function registerBudgetRuleRoutes(
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

  async function handle(
    request: FastifyRequest,
    reply: FastifyReply,
    budgetId: string,
    action: "create" | "edit" | "delete",
    ruleId: string | null,
    wireRule: WireRule | undefined,
  ) {
    const device = await requireDevice(deviceAuth, request, reply);
    if (!device) return reply;
    if (request.validationError) return invalidBudgetRequest(reply);
    if (!DeviceAuthorization.canAccessBudget(device, budgetId))
      return transactionNotFound(reply);
    if (!maintenanceApi || !journal) return actualNotConfigured(reply);
    if ((action === "delete") !== (wireRule === undefined)) {
      return invalidBudgetRequest(reply);
    }
    const rule = wireRule ? ruleDraftFromWire(wireRule) : undefined;
    if (rule === null) return invalidBudgetRequest(reply);
    const password = (request.headers as BudgetReadHeaders)[
      "x-budget-encryption-password"
    ];
    try {
      const patch: { [key: string]: JsonValue } = { action };
      if (rule) patch.rule = rule as unknown as JsonValue;
      const result = await runMaintenanceOperation(
        journal,
        request,
        device,
        budgetId,
        `rule-${action}`,
        ruleId,
        patch,
        async () => {
          switch (action) {
            case "create":
              await maintenanceApi.createRule(budgetId, rule!, password);
              break;
            case "edit":
              await maintenanceApi.editRule(budgetId, ruleId!, rule!, password);
              break;
            case "delete":
              await maintenanceApi.deleteRule(budgetId, ruleId!, password);
              break;
          }
          return { status: "ok" };
        },
      );
      logBudgetMaintenanceEvent(
        request,
        device,
        budgetId,
        `rule_${action}`,
        "success",
        ruleId ?? undefined,
      );
      reply.header("cache-control", "no-store");
      return reply.code(200).send(result);
    } catch (error) {
      if (error instanceof BudgetMaintenanceInputError) {
        logBudgetMaintenanceEvent(
          request,
          device,
          budgetId,
          `rule_${action}`,
          "rejected",
          ruleId ?? undefined,
        );
        const conflict = error instanceof MaintenanceOperationConflictError;
        return reply.code(conflict ? 409 : 400).send({
          error: {
            code: conflict ? error.code : "invalid_request",
            message: error.message,
          },
        });
      }
      logBudgetMaintenanceEvent(
        request,
        device,
        budgetId,
        `rule_${action}`,
        "failed",
        ruleId ?? undefined,
      );
      reportTransactionFailure("Actual rule action failed", error);
      return actualRequestFailed(reply, error);
    }
  }

  app.post<{
    Params: BudgetRouteParams;
    Headers: BudgetReadHeaders;
    Body: WireRule;
  }>(
    "/api/v1/budgets/:budgetId/rules",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "createBudgetRule",
        summary: "Create an Actual Budget rule",
        security: [{ bearerAuth: [] }],
        params: paramsSchema,
        headers: headersSchema,
        body: ruleSchema,
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    (request, reply) =>
      handle(
        request,
        reply,
        request.params.budgetId,
        "create",
        null,
        request.body,
      ),
  );

  app.post<{
    Params: BudgetRuleRouteParams;
    Headers: BudgetReadHeaders;
    Body: BudgetRuleActionBody;
  }>(
    "/api/v1/budgets/:budgetId/rules/:ruleId/actions",
    {
      schema: {
        tags: ["Budgets"],
        operationId: "manageBudgetRule",
        summary: "Edit or delete an Actual Budget rule",
        security: [{ bearerAuth: [] }],
        params: {
          type: "object",
          properties: {
            budgetId: paramsSchema.properties.budgetId,
            ruleId: idSchema,
          },
          required: ["budgetId", "ruleId"],
          additionalProperties: false,
        },
        headers: headersSchema,
        body: {
          type: "object",
          properties: {
            action: { type: "string", enum: ["edit", "delete"] },
            rule: ruleSchema,
          },
          required: ["action"],
          additionalProperties: false,
        },
        response: commonResponseSchema,
      },
      attachValidation: true,
    },
    (request, reply) =>
      handle(
        request,
        reply,
        request.params.budgetId,
        request.body?.action ?? "edit",
        request.params.ruleId,
        request.body?.rule,
      ),
  );
}
