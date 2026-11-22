package app.tandemforactual.ui.core.session

import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.PendingCategorySummary

internal fun BudgetReorderQueueState.movesFor(
    budgetId: String,
    scopeId: String,
): List<QueuedBudgetReorder> = moves.filter { it.budgetId == budgetId && it.scopeId == scopeId }

internal fun BudgetReorderQueueState.statusFor(
    budgetId: String,
    scopeId: String,
): BudgetReorderStatus = statuses[budgetReorderStatusKey(budgetId, scopeId)] ?: BudgetReorderStatus()

internal fun BudgetReorderQueueState.categoryVisibilityOverridesFor(budgetId: String): Map<String, Boolean> =
    categoryVisibilityOverrides
        .filterKeys { it.startsWith("$budgetId::") }
        .mapKeys { (key, _) -> resolveCategoryId(budgetId, key.removePrefix("$budgetId::")) }

internal fun BudgetReorderQueueState.categoryGroupNameOverridesFor(budgetId: String): Map<String, String> =
    categoryGroupNameOverrides
        .filterKeys { it.startsWith("$budgetId::") }
        .mapKeys { (key, _) -> resolveCategoryGroupId(budgetId, key.removePrefix("$budgetId::")) }

internal fun BudgetReorderQueueState.categoryNameOverridesFor(budgetId: String): Map<String, String> =
    categoryNameOverrides
        .filterKeys { it.startsWith("$budgetId::") }
        .mapKeys { (key, _) -> resolveCategoryId(budgetId, key.removePrefix("$budgetId::")) }

internal fun BudgetReorderQueueState.categoryGroupIdOverridesFor(budgetId: String): Map<String, String> =
    categoryGroupIdOverrides
        .filterKeys { it.startsWith("$budgetId::") }
        .mapKeys { (key, _) -> resolveCategoryId(budgetId, key.removePrefix("$budgetId::")) }
        .mapValues { (_, groupId) -> resolveCategoryGroupId(budgetId, groupId) }

internal fun BudgetReorderQueueState.categoryGroupVisibilityOverridesFor(budgetId: String): Map<String, Boolean> =
    categoryGroupVisibilityOverrides
        .filterKeys { it.startsWith("$budgetId::") }
        .mapKeys { (key, _) -> resolveCategoryGroupId(budgetId, key.removePrefix("$budgetId::")) }

internal fun BudgetReorderQueueState.deletedCategoryIdsFor(budgetId: String): Set<String> =
    deletedCategoryIds
        .filter { it.startsWith("$budgetId::") }
        .map { resolveCategoryId(budgetId, it.removePrefix("$budgetId::")) }
        .toSet()

internal fun BudgetReorderQueueState.pendingCategoryGroupCreatesFor(budgetId: String): List<LiveCategoryGroupSummary> =
    moves
        .filter {
            it.budgetId == budgetId &&
                it.kind in setOf(BudgetReorderKind.CREATE_CATEGORY_GROUP, BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE)
        }.mapNotNull { move ->
            move.name?.let { name ->
                LiveCategoryGroupSummary(
                    id = move.itemId,
                    name = name,
                    categories = emptyList(),
                    isIncome = move.isIncome,
                )
            }
        }

internal fun BudgetReorderQueueState.pendingCategoryCreatesFor(budgetId: String): List<PendingCategorySummary> =
    moves
        .filter {
            it.budgetId == budgetId &&
                it.kind in setOf(BudgetReorderKind.CREATE_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_CREATE)
        }.mapNotNull { move ->
            val name = move.name ?: return@mapNotNull null
            val groupId = move.parentGroupId ?: return@mapNotNull null
            PendingCategorySummary(
                groupId = resolveCategoryGroupId(budgetId, groupId),
                category = LiveCategorySummary(move.itemId, name, 0, 0, 0),
            )
        }

internal fun BudgetReorderQueueState.resolveCategoryGroupId(
    budgetId: String,
    groupId: String,
): String {
    var resolved = groupId
    val visited = mutableSetOf<String>()
    while (visited.add(resolved)) {
        resolved = categoryGroupIdAliases[categoryVisibilityKey(budgetId, resolved)] ?: return resolved
    }
    return resolved
}

internal fun BudgetReorderQueueState.resolveCategoryId(
    budgetId: String,
    categoryId: String,
): String {
    var resolved = categoryId
    val visited = mutableSetOf<String>()
    while (visited.add(resolved)) {
        resolved = categoryIdAliases[categoryVisibilityKey(budgetId, resolved)] ?: return resolved
    }
    return resolved
}
