import {
  BudgetMaintenanceInputError,
  type BudgetMaintenanceApiDependencies,
  type BudgetMaintenanceApiPort,
  writeOptions,
} from "./budget-maintenance-contracts.js";

export function createPayeeOperations({
  locks,
  session,
  client,
}: BudgetMaintenanceApiDependencies): Pick<
  BudgetMaintenanceApiPort,
  | "createPayee"
  | "renamePayee"
  | "mergePayee"
  | "deletePayee"
  | "setPayeeDefaultCategory"
> {
  return {
    createPayee(budgetId, name, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const payees = await client.getPayees();
            const duplicate = payees.some(
              (candidate) =>
                candidate.name.trim().toLocaleLowerCase() ===
                name.trim().toLocaleLowerCase(),
            );
            if (duplicate) {
              throw new BudgetMaintenanceInputError(
                "A payee with this name already exists",
              );
            }
            return client.createPayee({ name, transfer_acct: "" });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    async renamePayee(budgetId, payeeId, name, encryptionPassword) {
      const normalizedName = name.trim();
      if (!normalizedName || normalizedName.length > 100) {
        throw new BudgetMaintenanceInputError(
          "Payee names must be between 1 and 100 characters",
        );
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const payee = (await client.getPayees()).find(
              (candidate) => candidate.id === payeeId,
            );
            if (!payee || payee.transfer_acct) {
              throw new BudgetMaintenanceInputError(
                "Choose a payee in this budget",
              );
            }
            await client.updatePayee(payeeId, { name: normalizedName });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    async mergePayee(budgetId, payeeId, targetPayeeId, encryptionPassword) {
      if (payeeId === targetPayeeId) {
        throw new BudgetMaintenanceInputError(
          "Choose a different payee to merge into",
        );
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const payees = await client.getPayees();
            const source = payees.find((candidate) => candidate.id === payeeId);
            const target = payees.find(
              (candidate) => candidate.id === targetPayeeId,
            );
            if (
              !source ||
              !target ||
              source.transfer_acct ||
              target.transfer_acct
            ) {
              throw new BudgetMaintenanceInputError(
                "Choose two payees in this budget",
              );
            }
            await client.mergePayees(targetPayeeId, [payeeId]);
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    deletePayee(budgetId, payeeId, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const payee = (await client.getPayees()).find(
              (candidate) => candidate.id === payeeId,
            );
            if (!payee || payee.transfer_acct) {
              throw new BudgetMaintenanceInputError(
                "Choose a payee in this budget",
              );
            }
            await client.deletePayee(payeeId);
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    setPayeeDefaultCategory(budgetId, payeeId, categoryId, encryptionPassword) {
      if (categoryId !== null && !categoryId.trim()) {
        return Promise.reject(
          new BudgetMaintenanceInputError(
            "Choose a category or clear the default",
          ),
        );
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const payee = (await client.getPayees()).find(
              (candidate) => candidate.id === payeeId,
            );
            if (!payee || payee.transfer_acct) {
              throw new BudgetMaintenanceInputError(
                "Choose a regular payee in this budget",
              );
            }
            if (categoryId !== null) {
              const category = (await client.getCategories()).find(
                (candidate) => candidate.id === categoryId,
              );
              if (!category) {
                throw new BudgetMaintenanceInputError(
                  "Choose an active category in this budget",
                );
              }
            }

            const rules = await client.getPayeeRules(payeeId);
            const matchingRules = rules.filter((rule) => {
              const conditions = rule.conditions;
              const stage = (rule as { stage: string | null }).stage;
              return (
                (stage === null || stage === "default") &&
                rule.conditionsOp === "and" &&
                conditions.length === 1 &&
                conditions[0]?.field === "payee" &&
                conditions[0]?.op === "is" &&
                conditions[0]?.value === payeeId &&
                rule.actions.some(
                  (action) =>
                    "field" in action &&
                    action.field === "category" &&
                    action.op === "set",
                )
              );
            });
            if (matchingRules.length > 1) {
              throw new BudgetMaintenanceInputError(
                "This payee has multiple category rules. Edit them in Actual first.",
              );
            }

            const rule = matchingRules[0];
            if (!rule) {
              if (categoryId === null) return;
              await client.createRule({
                stage: "default",
                conditionsOp: "and",
                conditions: [{ field: "payee", op: "is", value: payeeId }],
                actions: [{ field: "category", op: "set", value: categoryId }],
              });
              return;
            }

            const categoryActionIndexes = rule.actions.flatMap(
              (action, index) =>
                "field" in action &&
                action.field === "category" &&
                action.op === "set"
                  ? [index]
                  : [],
            );
            if (categoryActionIndexes.length > 1) {
              throw new BudgetMaintenanceInputError(
                "This payee has multiple category actions. Edit the rule in Actual first.",
              );
            }
            // matchingRules only retains rules with at least one category-set action.
            const categoryActionIndex = categoryActionIndexes[0]!;

            if (categoryId === null) {
              const actions = rule.actions.filter(
                (_action, index) => index !== categoryActionIndex,
              );
              if (actions.length === 0) {
                await client.deleteRule(rule.id);
              } else {
                await client.updateRule({ ...rule, actions });
              }
              return;
            }

            const actions = rule.actions.map((action, index) =>
              index === categoryActionIndex &&
              "field" in action &&
              action.field === "category" &&
              action.op === "set"
                ? { ...action, value: categoryId }
                : action,
            );
            await client.updateRule({ ...rule, actions });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
  };
}
