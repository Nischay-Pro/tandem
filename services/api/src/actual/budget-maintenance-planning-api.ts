import {
  BudgetMaintenanceInputError,
  MAX_BUDGET_AMOUNT_MINOR,
  type BudgetMaintenanceApiDependencies,
  type BudgetMaintenanceApiPort,
  isRecord,
  readSafeAmount,
  writeOptions,
} from "./budget-maintenance-contracts.js";

export function createPlanningOperations({
  locks,
  session,
  client,
}: BudgetMaintenanceApiDependencies): Pick<
  BudgetMaintenanceApiPort,
  | "setBudgetAmount"
  | "setBudgetCarryover"
  | "copyBudgetMonth"
  | "applyBudgetTemplate"
  | "holdBudgetForNextMonth"
  | "resetBudgetHold"
  | "moveMoney"
> {
  return {
    setBudgetAmount(
      budgetId,
      categoryId,
      month,
      amountMinor,
      encryptionPassword,
    ) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            if (
              !Number.isSafeInteger(amountMinor) ||
              amountMinor < 0 ||
              amountMinor > MAX_BUDGET_AMOUNT_MINOR
            ) {
              throw new BudgetMaintenanceInputError(
                "Enter a budget amount up to 1,000,000,000 major units",
              );
            }
            const categories = await client.getCategories({ hidden: true });
            if (!categories.some((candidate) => candidate.id === categoryId)) {
              throw new BudgetMaintenanceInputError(
                "Category does not exist in this budget",
              );
            }
            await client.setBudgetAmount(month, categoryId, amountMinor);
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    setBudgetCarryover(
      budgetId,
      categoryId,
      month,
      enabled,
      encryptionPassword,
    ) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const monthData = await client.getBudgetMonth(month);
            const categories = (
              monthData.categoryGroups as Array<{ categories?: unknown[] }>
            )
              .flatMap((group) => group.categories ?? [])
              .filter(isRecord);
            if (!categories.some((category) => category.id === categoryId)) {
              throw new BudgetMaintenanceInputError(
                "Choose a category in this budget month",
              );
            }
            await client.setBudgetCarryover(month, categoryId, enabled);
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    copyBudgetMonth(budgetId, month, encryptionPassword) {
      const year = Number(month.slice(0, 4));
      const monthNumber = Number(month.slice(5, 7));
      if (
        !/^\d{4}-(0[1-9]|1[0-2])$/.test(month) ||
        year < 1 ||
        (year === 1 && monthNumber === 1)
      ) {
        return Promise.reject(
          new BudgetMaintenanceInputError("Choose a valid budget month"),
        );
      }
      const previousMonth =
        monthNumber === 1
          ? `${String(year - 1).padStart(4, "0")}-12`
          : `${String(year).padStart(4, "0")}-${String(monthNumber - 1).padStart(2, "0")}`;
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const sourceMonth = await client.getBudgetMonth(previousMonth);
            const targetMonth = await client.getBudgetMonth(month);
            const activeCategories = await client.getCategories({
              hidden: false,
            });
            const sourceAmounts = new Map<string, number>();
            for (const group of sourceMonth.categoryGroups) {
              for (const category of group.categories ?? []) {
                const id = typeof category.id === "string" ? category.id : null;
                if (id === null) continue;
                sourceAmounts.set(
                  id,
                  readSafeAmount(
                    category.budgeted ?? category.assigned ?? 0,
                    "previous month's category assignment",
                  ),
                );
              }
            }
            const targetCategoryIds = new Set(
              targetMonth.categoryGroups.flatMap((group) =>
                (group.categories ?? [])
                  .map((category) => category.id)
                  .filter((id): id is string => typeof id === "string"),
              ),
            );
            const categories = activeCategories.filter((category) =>
              targetCategoryIds.has(category.id),
            );
            await client.batchBudgetUpdates(async () => {
              for (const category of categories) {
                await client.setBudgetAmount(
                  month,
                  category.id,
                  sourceAmounts.get(category.id) ?? 0,
                );
              }
            });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    applyBudgetTemplate(budgetId, month, assignments, encryptionPassword) {
      if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) {
        return Promise.reject(
          new BudgetMaintenanceInputError("Choose a valid budget month"),
        );
      }
      if (assignments.length === 0 || assignments.length > 500) {
        return Promise.reject(
          new BudgetMaintenanceInputError(
            "Choose a template with 1 to 500 categories",
          ),
        );
      }
      const seen = new Set<string>();
      for (const assignment of assignments) {
        if (
          !assignment.categoryId.trim() ||
          seen.has(assignment.categoryId) ||
          !Number.isSafeInteger(assignment.amountMinor) ||
          assignment.amountMinor < 0 ||
          assignment.amountMinor > MAX_BUDGET_AMOUNT_MINOR
        ) {
          return Promise.reject(
            new BudgetMaintenanceInputError(
              "The template contains an invalid category amount",
            ),
          );
        }
        seen.add(assignment.categoryId);
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const [monthData, categories] = await Promise.all([
              client.getBudgetMonth(month),
              client.getCategories({ hidden: false }),
            ]);
            const monthCategoryIds = new Set(
              monthData.categoryGroups.flatMap((group) =>
                (group.categories ?? [])
                  .map((category) => category.id)
                  .filter((id): id is string => typeof id === "string"),
              ),
            );
            const activeCategoryIds = new Set(
              categories.map((category) => category.id),
            );
            if (
              assignments.some(
                ({ categoryId }) =>
                  !activeCategoryIds.has(categoryId) ||
                  !monthCategoryIds.has(categoryId),
              )
            ) {
              throw new BudgetMaintenanceInputError(
                "A template category is no longer active in this month",
              );
            }
            await client.batchBudgetUpdates(async () => {
              for (const assignment of assignments) {
                await client.setBudgetAmount(
                  month,
                  assignment.categoryId,
                  assignment.amountMinor,
                );
              }
            });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    holdBudgetForNextMonth(budgetId, month, amountMinor, encryptionPassword) {
      if (
        !/^\d{4}-(0[1-9]|1[0-2])$/.test(month) ||
        !Number.isSafeInteger(amountMinor) ||
        amountMinor <= 0 ||
        amountMinor > MAX_BUDGET_AMOUNT_MINOR
      ) {
        return Promise.reject(
          new BudgetMaintenanceInputError(
            "Enter a positive amount to hold for next month",
          ),
        );
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const preferences = await client.getPreferences();
            if (preferences.budgetType === "tracking") {
              throw new BudgetMaintenanceInputError(
                "Hold for next month is only available in an envelope budget",
              );
            }
            const monthData = await client.getBudgetMonth(month);
            const available = readSafeAmount(
              monthData.toBudget ?? 0,
              "ready-to-assign amount",
            );
            if (amountMinor > available) {
              throw new BudgetMaintenanceInputError(
                "The hold cannot exceed the amount ready to assign",
              );
            }
            const held = await client.holdBudgetForNextMonth(
              month,
              amountMinor,
            );
            if (!held) {
              throw new BudgetMaintenanceInputError(
                "There is no money ready to assign in this month",
              );
            }
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    resetBudgetHold(budgetId, month, encryptionPassword) {
      if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) {
        return Promise.reject(
          new BudgetMaintenanceInputError("Choose a valid budget month"),
        );
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          () => client.resetBudgetHold(month),
          writeOptions(encryptionPassword),
        ),
      );
    },
    moveMoney(
      budgetId,
      month,
      fromCategoryId,
      toCategoryId,
      amountMinor,
      encryptionPassword,
    ) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            if (
              !Number.isSafeInteger(amountMinor) ||
              amountMinor <= 0 ||
              amountMinor > MAX_BUDGET_AMOUNT_MINOR
            ) {
              throw new BudgetMaintenanceInputError(
                "Enter a positive move amount up to 1,000,000,000 major units",
              );
            }
            if (fromCategoryId === toCategoryId) {
              throw new BudgetMaintenanceInputError(
                "Choose two different categories",
              );
            }
            const preferences = await client.getPreferences();
            if (preferences.budgetType === "tracking") {
              throw new BudgetMaintenanceInputError(
                "Money can only be moved in an envelope budget",
              );
            }
            const monthData = await client.getBudgetMonth(month);
            const categories = (
              monthData.categoryGroups as Array<{ categories?: unknown[] }>
            )
              .flatMap((group) => group.categories ?? [])
              .filter(isRecord);
            const source = categories.find(
              (category) => category.id === fromCategoryId,
            );
            const destination = categories.find(
              (category) => category.id === toCategoryId,
            );
            if (!source || !destination) {
              throw new BudgetMaintenanceInputError(
                "Choose categories in this budget month",
              );
            }
            if (source.is_income !== destination.is_income) {
              throw new BudgetMaintenanceInputError(
                "Move money between categories of the same type",
              );
            }
            const sourceBudgeted = readSafeAmount(
              source.budgeted ?? source.assigned,
              "source assignment",
            );
            const destinationBudgeted = readSafeAmount(
              destination.budgeted ?? destination.assigned,
              "destination assignment",
            );
            const sourceAvailable = readSafeAmount(
              source.balance ?? source.available,
              "source available amount",
            );
            if (sourceAvailable < amountMinor) {
              throw new BudgetMaintenanceInputError(
                "The source category does not have enough available money",
              );
            }
            await client.batchBudgetUpdates(async () => {
              await client.setBudgetAmount(
                month,
                fromCategoryId,
                sourceBudgeted - amountMinor,
              );
              await client.setBudgetAmount(
                month,
                toCategoryId,
                destinationBudgeted + amountMinor,
              );
            });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
  };
}
