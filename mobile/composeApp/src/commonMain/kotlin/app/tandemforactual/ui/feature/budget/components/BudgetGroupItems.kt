@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.budgetReorderMovesFor
import app.tandemforactual.ui.core.session.budget.budgetReorderStatus
import app.tandemforactual.ui.core.session.budgetOrderForScreen
import app.tandemforactual.ui.core.session.dragReorderItemModifier
import app.tandemforactual.ui.core.session.reorderModeConsumesItemTap
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.isBudgetSectionCollapsed
import app.tandemforactual.ui.design.theme.tandemReduceMotion

/** What a budget group list section needs from the screen. */
internal class BudgetGroupListContext(
    val state: AppState,
    val navigation: AppNavigation,
    val mobileSession: TandemMobileSession,
    val reorder: BudgetReorderState,
    val actions: BudgetReorderActions,
    val isLiveBudget: Boolean,
    val isTrackingBudget: Boolean,
    val compact: Boolean,
)

/** Header card and category rows for one budget category group. */
internal fun LazyListScope.budgetGroupItems(
    group: LiveCategoryGroupSummary,
    displayGroups: List<LiveCategoryGroupSummary>,
    context: BudgetGroupListContext,
) {
    val state = context.state
    val reorder = context.reorder
    val mobileSession = context.mobileSession
    val groupSectionKey = "live:${state.selectedBudgetId}:group:${group.id}"
    val groupExpanded = reorder.selectedGroupOrderId == null && !state.isBudgetSectionCollapsed(groupSectionKey)
    val isReordering = reorder.selectedGroupOrderId == group.id
    val groupIndex = displayGroups.indexOfFirst { it.id == group.id }
    val categoryOrderStatus = mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, group.id)
    val queuedCategoryMoves = mobileSession.budgetQueue.budgetReorderMovesFor(state.selectedBudgetId, group.id)
    val serverCategoryOrder = group.categories.map { it.id }
    val queuedCategoryOrder = budgetOrderForScreen(serverCategoryOrder, categoryOrderStatus, queuedCategoryMoves)
    val categoryOrderIds =
        if (reorder.selectedCategoryGroupId == group.id) {
            reorder.categoryDrag.currentOrder(queuedCategoryOrder)
        } else {
            queuedCategoryOrder
        }
    val categoriesById = group.categories.associateBy { it.id }
    val orderedCategories =
        categoryOrderIds.mapNotNull(categoriesById::get) + group.categories.filterNot { it.id in categoryOrderIds }
    item(key = "$groupSectionKey:section") {
        BudgetGroupCard(
            group = group,
            groupSectionKey = groupSectionKey,
            groupExpanded = groupExpanded,
            isReordering = isReordering,
            groupIndex = groupIndex,
            displayGroups = displayGroups,
            context = context,
        )
    }
    if (groupExpanded) {
        items(orderedCategories, key = { "budget-category:${group.id}:${it.id}" }) { category ->
            BudgetCategoryItem(group, category, orderedCategories, context)
        }
    }
}

@Composable
private fun LazyItemScope.BudgetCategoryItem(
    group: LiveCategoryGroupSummary,
    category: LiveCategorySummary,
    orderedCategories: List<LiveCategorySummary>,
    context: BudgetGroupListContext,
) {
    val state = context.state
    val reorder = context.reorder
    val actions = context.actions
    val categoryIndex = orderedCategories.indexOfFirst { it.id == category.id }
    val categoryIsReordering = reorder.selectedCategoryOrderId == category.id
    val startCategoryDrag = { actions.startCategoryDrag(group, category.id) }
    val dragCategoryBy = { delta: Float -> actions.dragCategoryBy(category.id, delta) }
    val dropCategoryDrag = { actions.dropCategoryDrag(group, category.id) }
    val categoryCardModifier =
        dragReorderItemModifier(
            itemId = category.id,
            dragState = reorder.categoryDrag,
            reduceMotion = tandemReduceMotion,
            dragEnabled = !state.readOnlyMode && orderedCategories.size > 1,
            onDragStarted = startCategoryDrag,
            onDragBy = dragCategoryBy,
            onDragStopped = dropCategoryDrag,
        )
    BudgetCategoryCard(
        state = state,
        category = category,
        compact = context.compact,
        modifier = categoryCardModifier,
        reorder =
            BudgetCategoryReorder(
                isReordering = categoryIsReordering,
                canMoveUp = categoryIndex > 0,
                canMoveDown = categoryIndex >= 0 && categoryIndex < orderedCategories.lastIndex,
                onMoveUp = { actions.moveCategory(group, category.id, -1) },
                onMoveDown = { actions.moveCategory(group, category.id, 1) },
                onDragStarted = startCategoryDrag,
                onDragBy = dragCategoryBy,
                onDragStopped = dropCategoryDrag,
            ),
        onClick = {
            if (reorderModeConsumesItemTap(reorder.selectedCategoryOrderId)) {
                reorder.selectedCategoryOrderId = null
            } else {
                state.selectedCategoryGroupId = group.id
                state.selectedCategoryId = category.id
                context.navigation.open(AppRoute.CATEGORY_DETAIL)
            }
        },
    )
}
