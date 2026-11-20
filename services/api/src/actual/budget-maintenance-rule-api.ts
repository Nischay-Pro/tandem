import {
  BudgetMaintenanceInputError,
  getAllCategories,
  type BudgetMaintenanceApiDependencies,
  type BudgetMaintenanceApiPort,
  writeOptions,
} from "./budget-maintenance-contracts.js";

export type BudgetRuleStage = "pre" | "default" | "post";

export interface BudgetRuleCondition {
  field: BudgetRuleConditionField;
  op: string;
  value: string | number | string[] | { num1: number; num2: number };
  options?: { inflow?: boolean; outflow?: boolean };
}

export interface BudgetRuleAction {
  field?: BudgetRuleActionField;
  op: "set" | "prepend-notes" | "append-notes";
  value: string | boolean;
}

export interface BudgetRuleDraft {
  stage: BudgetRuleStage;
  conditionsOp: "and" | "or";
  conditions: BudgetRuleCondition[];
  actions: BudgetRuleAction[];
}

export const RULE_CONDITION_OPS = {
  payee: ["is", "isNot", "oneOf", "notOneOf"],
  account: ["is", "isNot", "oneOf", "notOneOf"],
  category: ["is", "isNot", "oneOf", "notOneOf"],
  imported_payee: ["is", "isNot", "contains", "doesNotContain", "matches"],
  notes: ["is", "isNot", "contains", "doesNotContain", "matches"],
  amount: ["is", "isapprox", "isbetween", "gt", "gte", "lt", "lte"],
} as const;

export type BudgetRuleConditionField = keyof typeof RULE_CONDITION_OPS;

export const RULE_SET_FIELDS = [
  "category",
  "payee",
  "account",
  "notes",
  "cleared",
] as const;

export type BudgetRuleActionField = (typeof RULE_SET_FIELDS)[number];

const ID_FIELDS = new Set(["payee", "account", "category"]);
const MAX_TEXT_LENGTH = 500;

type IdSets = Record<"payee" | "account" | "category", Set<string>>;

export function validateBudgetRuleDraft(draft: BudgetRuleDraft): void {
  if (draft.conditions.length === 0) {
    throw new BudgetMaintenanceInputError("Add at least one condition");
  }
  if (draft.actions.length === 0) {
    throw new BudgetMaintenanceInputError("Add at least one action");
  }
  for (const condition of draft.conditions) {
    const ops: readonly string[] = RULE_CONDITION_OPS[condition.field] ?? [];
    if (!ops.includes(condition.op)) {
      throw new BudgetMaintenanceInputError(
        "This condition isn’t supported. Edit the rule in Actual.",
      );
    }
    validateConditionValue(condition);
  }
  const setFields = new Set<string>();
  for (const action of draft.actions) {
    if (action.op === "set") {
      if (
        action.field === undefined ||
        !(RULE_SET_FIELDS as readonly string[]).includes(action.field)
      ) {
        throw new BudgetMaintenanceInputError(
          "This action isn’t supported. Edit the rule in Actual.",
        );
      }
      if (setFields.has(action.field)) {
        throw new BudgetMaintenanceInputError(
          "Each field can only be set once in a rule",
        );
      }
      setFields.add(action.field);
      if (action.field === "cleared") {
        if (typeof action.value !== "boolean") {
          throw new BudgetMaintenanceInputError("Choose cleared or uncleared");
        }
      } else if (action.field === "notes") {
        requireText(action.value, false);
      } else {
        requireText(action.value, true);
      }
    } else {
      if (action.field !== undefined) {
        throw new BudgetMaintenanceInputError(
          "This action isn’t supported. Edit the rule in Actual.",
        );
      }
      requireText(action.value, true);
    }
  }
}

function validateConditionValue(condition: BudgetRuleCondition): void {
  const { field, op, value } = condition;
  if (condition.options !== undefined && field !== "amount") {
    throw new BudgetMaintenanceInputError(
      "Only amount conditions can be limited to money in or out",
    );
  }
  if (condition.options?.inflow && condition.options.outflow) {
    throw new BudgetMaintenanceInputError(
      "Choose money in or money out, not both",
    );
  }
  if (field === "amount") {
    if (op === "isbetween") {
      if (
        typeof value !== "object" ||
        Array.isArray(value) ||
        !Number.isSafeInteger(value.num1) ||
        !Number.isSafeInteger(value.num2) ||
        value.num1 > value.num2
      ) {
        throw new BudgetMaintenanceInputError(
          "Enter a range from the lower amount to the higher amount",
        );
      }
    } else if (typeof value !== "number" || !Number.isSafeInteger(value)) {
      throw new BudgetMaintenanceInputError("Enter an amount");
    }
    return;
  }
  if (op === "oneOf" || op === "notOneOf") {
    if (
      !Array.isArray(value) ||
      value.length === 0 ||
      value.some((item) => typeof item !== "string" || !item.trim())
    ) {
      throw new BudgetMaintenanceInputError("Choose at least one option");
    }
    return;
  }
  requireText(value, true);
  if (op === "matches") {
    try {
      new RegExp(value as string);
    } catch {
      throw new BudgetMaintenanceInputError("Enter a valid regular expression");
    }
  }
}

function requireText(value: unknown, required: boolean): void {
  if (
    typeof value !== "string" ||
    value.length > MAX_TEXT_LENGTH ||
    (required && !value.trim())
  ) {
    throw new BudgetMaintenanceInputError(
      required ? "Fill in every condition and action" : "Notes are too long",
    );
  }
}

function referencedIds(draft: BudgetRuleDraft): Array<[keyof IdSets, string]> {
  const ids: Array<[keyof IdSets, string]> = [];
  for (const condition of draft.conditions) {
    if (!ID_FIELDS.has(condition.field)) continue;
    const field = condition.field as keyof IdSets;
    const values = Array.isArray(condition.value)
      ? condition.value
      : [condition.value as string];
    for (const value of values) ids.push([field, value]);
  }
  for (const action of draft.actions) {
    if (action.op === "set" && action.field && ID_FIELDS.has(action.field)) {
      ids.push([action.field as keyof IdSets, action.value as string]);
    }
  }
  return ids;
}

const MISSING_REFERENCE_MESSAGES: Record<keyof IdSets, string> = {
  payee: "Choose a payee in this budget",
  account: "Choose an account in this budget",
  category: "Choose a category in this budget",
};

function toActualRule(draft: BudgetRuleDraft) {
  return {
    stage: draft.stage,
    conditionsOp: draft.conditionsOp,
    conditions: draft.conditions.map((condition) => ({
      field: condition.field,
      op: condition.op,
      value: condition.value,
      ...(condition.options ? { options: condition.options } : {}),
    })),
    actions: draft.actions.map((action) =>
      action.op === "set"
        ? { field: action.field!, op: "set" as const, value: action.value }
        : { op: action.op, value: action.value as string },
    ),
  };
}

export function createRuleOperations({
  locks,
  session,
  client,
}: BudgetMaintenanceApiDependencies): Pick<
  BudgetMaintenanceApiPort,
  "createRule" | "editRule" | "deleteRule"
> {
  async function requireReferences(draft: BudgetRuleDraft): Promise<void> {
    const references = referencedIds(draft);
    if (references.length === 0) return;
    const [payees, accounts, categories] = await Promise.all([
      client.getPayees(),
      client.getAccounts(),
      getAllCategories(client),
    ]);
    const known: IdSets = {
      payee: new Set(payees.map((payee) => payee.id)),
      account: new Set(accounts.map((account) => account.id)),
      category: new Set(categories.map((category) => category.id)),
    };
    for (const [field, id] of references) {
      if (!known[field].has(id)) {
        throw new BudgetMaintenanceInputError(
          MISSING_REFERENCE_MESSAGES[field],
        );
      }
    }
  }

  async function requireRule(ruleId: string) {
    const rule = (await client.getRules()).find(
      (candidate) => candidate.id === ruleId,
    );
    if (!rule) {
      throw new BudgetMaintenanceInputError("Choose a rule in this budget");
    }
    return rule;
  }

  return {
    async createRule(budgetId, draft, encryptionPassword) {
      validateBudgetRuleDraft(draft);
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            await requireReferences(draft);
            const created = await client.createRule(
              toActualRule(draft) as Parameters<typeof client.createRule>[0],
            );
            return created.id;
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    async editRule(budgetId, ruleId, draft, encryptionPassword) {
      validateBudgetRuleDraft(draft);
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            await requireRule(ruleId);
            await requireReferences(draft);
            await client.updateRule({
              id: ruleId,
              ...toActualRule(draft),
            } as Parameters<typeof client.updateRule>[0]);
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    async deleteRule(budgetId, ruleId, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            await requireRule(ruleId);
            await client.deleteRule(ruleId);
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
  };
}
