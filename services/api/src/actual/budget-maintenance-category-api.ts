import {
  BudgetMaintenanceInputError,
  type BudgetMaintenanceApiDependencies,
  type BudgetMaintenanceApiPort,
  getAllCategories,
  getAllCategoryGroups,
  writeOptions,
} from "./budget-maintenance-contracts.js";

export function createCategoryOperations({
  locks,
  session,
  client,
}: BudgetMaintenanceApiDependencies): Pick<
  BudgetMaintenanceApiPort,
  | "createCategory"
  | "renameCategory"
  | "createCategoryGroup"
  | "manageCategoryGroup"
  | "manageCategory"
  | "deleteCategory"
> {
  return {
    createCategory(budgetId, name, categoryGroupId, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const groups = await client.getCategoryGroups();
            const group = groups.find(
              (candidate) => candidate.id === categoryGroupId,
            );
            if (!group || group.hidden) {
              throw new BudgetMaintenanceInputError(
                "Choose an active category group",
              );
            }
            const categories = await getAllCategories(client);
            const duplicate = categories.some(
              (candidate) =>
                candidate.group_id === categoryGroupId &&
                candidate.name.trim().toLocaleLowerCase() ===
                  name.trim().toLocaleLowerCase(),
            );
            if (duplicate) {
              throw new BudgetMaintenanceInputError(
                "A category with this name already exists in that group",
              );
            }
            return client.createCategory({
              name,
              group_id: categoryGroupId,
              is_income: group.is_income ?? false,
              hidden: false,
            });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    renameCategory(budgetId, categoryId, name, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const categories = await client.getCategories();
            const category = categories.find(
              (candidate) => candidate.id === categoryId,
            );
            if (!category)
              throw new BudgetMaintenanceInputError(
                "Category does not exist in this budget",
              );
            if (category.name !== name) {
              await client.updateCategory(categoryId, { name });
            }
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    createCategoryGroup(budgetId, name, isIncome, encryptionPassword) {
      const cleanName = name.trim();
      if (!cleanName || cleanName.length > 100) {
        throw new BudgetMaintenanceInputError(
          "Category group names must be between 1 and 100 characters",
        );
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const groups = await getAllCategoryGroups(client);
            if (
              groups.some(
                (group) =>
                  group.name.trim().toLocaleLowerCase() ===
                  cleanName.toLocaleLowerCase(),
              )
            ) {
              throw new BudgetMaintenanceInputError(
                "A category group with this name already exists",
              );
            }
            return client.createCategoryGroup({
              name: cleanName,
              is_income: isIncome,
              hidden: false,
            });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    manageCategoryGroup(
      budgetId,
      groupId,
      action,
      name,
      targetId,
      encryptionPassword,
    ) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const groups = await getAllCategoryGroups(client);
            const group = groups.find((candidate) => candidate.id === groupId);
            if (!group) {
              throw new BudgetMaintenanceInputError(
                "Category group does not exist in this budget",
              );
            }
            if (action === "rename") {
              const cleanName = name?.trim() ?? "";
              if (!cleanName || cleanName.length > 100) {
                throw new BudgetMaintenanceInputError(
                  "Category group names must be between 1 and 100 characters",
                );
              }
              if (
                groups.some(
                  (candidate) =>
                    candidate.id !== groupId &&
                    candidate.name.trim().toLocaleLowerCase() ===
                      cleanName.toLocaleLowerCase(),
                )
              ) {
                throw new BudgetMaintenanceInputError(
                  "A category group with this name already exists",
                );
              }
              await client.updateCategoryGroup(groupId, { name: cleanName });
              return;
            }
            if (action === "hide" || action === "show") {
              await client.updateCategoryGroup(groupId, {
                hidden: action === "hide",
              });
              return;
            }
            if (action === "delete") {
              const categories = await getAllCategories(client);
              const groupCategories = categories.filter(
                (category) => category.group_id === groupId,
              );
              if (targetId != null) {
                const destination = categories.find(
                  (category) => category.id === targetId && !category.hidden,
                );
                const destinationGroup = groups.find(
                  (candidate) =>
                    candidate.id === destination?.group_id && !candidate.hidden,
                );
                if (
                  !destination ||
                  !destinationGroup ||
                  groupCategories.some(
                    (category) =>
                      category.id === destination.id ||
                      category.is_income !== destination.is_income,
                  )
                ) {
                  throw new BudgetMaintenanceInputError(
                    "Choose a visible category outside this group with the same income type",
                  );
                }
              }
              await client.deleteCategoryGroup(groupId, targetId ?? undefined);
              return;
            }
            if (targetId === groupId) {
              throw new BudgetMaintenanceInputError(
                "Choose a different category group position",
              );
            }
            if (
              targetId !== null &&
              targetId !== undefined &&
              !groups.some((candidate) => candidate.id === targetId)
            ) {
              throw new BudgetMaintenanceInputError(
                "Choose a category group in this budget",
              );
            }
            const internal = client.internal;
            if (!internal) {
              throw new BudgetMaintenanceInputError(
                "This Actual version does not support category group ordering",
              );
            }
            await internal.send("category-group-move", {
              id: groupId,
              targetId: targetId ?? null,
            });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    manageCategory(
      budgetId,
      categoryId,
      action,
      targetCategoryId,
      encryptionPassword,
      targetGroupId,
    ) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const categories = await getAllCategories(client);
            const category = categories.find(
              (candidate) => candidate.id === categoryId,
            );
            if (!category) {
              throw new BudgetMaintenanceInputError(
                "Category does not exist in this budget",
              );
            }
            if (action === "hide" || action === "show") {
              await client.updateCategory(categoryId, {
                hidden: action === "hide",
              });
              return;
            }
            const destinationGroupId = targetGroupId ?? category.group_id;
            if (
              targetGroupId != null &&
              destinationGroupId !== category.group_id
            ) {
              const groups = await getAllCategoryGroups(client);
              const destinationGroup = groups.find(
                (candidate) =>
                  candidate.id === destinationGroupId && !candidate.hidden,
              );
              if (!destinationGroup) {
                throw new BudgetMaintenanceInputError(
                  "Choose a visible category group in this budget",
                );
              }
              if (destinationGroup.is_income !== category.is_income) {
                throw new BudgetMaintenanceInputError(
                  "Income categories must stay in an income group",
                );
              }
              if (targetCategoryId != null) {
                throw new BudgetMaintenanceInputError(
                  "A category moved to another group starts at the end of that group",
                );
              }
              const internal = client.internal;
              if (!internal) {
                throw new BudgetMaintenanceInputError(
                  "This Actual version does not support moving categories between groups",
                );
              }
              await internal.send("category-move", {
                id: categoryId,
                groupId: destinationGroupId,
                targetId: null,
              });
              return;
            }
            if (targetCategoryId === categoryId) {
              throw new BudgetMaintenanceInputError(
                "Choose a different category position",
              );
            }
            const target =
              targetCategoryId == null
                ? undefined
                : categories.find(
                    (candidate) => candidate.id === targetCategoryId,
                  );
            if (
              targetCategoryId != null &&
              (!target || target.group_id !== category.group_id)
            ) {
              throw new BudgetMaintenanceInputError(
                "Choose a category in the same group",
              );
            }
            const internal = client.internal;
            if (!internal) {
              throw new BudgetMaintenanceInputError(
                "This Actual version does not support category ordering",
              );
            }
            await internal.send("category-move", {
              id: categoryId,
              groupId: category.group_id,
              targetId: targetCategoryId ?? null,
            });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    deleteCategory(
      budgetId,
      categoryId,
      transferCategoryId,
      encryptionPassword,
    ) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const categories = await client.getCategories();
            const category = categories.find(
              (candidate) => candidate.id === categoryId,
            );
            if (!category) return;
            if (transferCategoryId !== null) {
              const destination = categories.find(
                (candidate) => candidate.id === transferCategoryId,
              );
              if (!destination || destination.id === category.id) {
                throw new BudgetMaintenanceInputError(
                  "Choose a different category in this budget",
                );
              }
              if (destination.is_income !== category.is_income) {
                throw new BudgetMaintenanceInputError(
                  "Transactions can only move to a category of the same type",
                );
              }
            }
            await client.deleteCategory(
              categoryId,
              transferCategoryId ?? undefined,
            );
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
  };
}
