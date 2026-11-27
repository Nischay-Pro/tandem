@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.session.BudgetReorderStatus
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.retryQueuedBudgetReorders
import app.tandemforactual.ui.core.session.dragReorderItemModifier
import app.tandemforactual.ui.core.session.reorderAccessibilityAction
import app.tandemforactual.ui.core.session.reorderModeConsumesItemTap
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.categoryIconFor
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.Badge
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.templates.AmountHero
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.budget.BudgetItemReorderActions
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil

/** What the category group list sections need from the screen. */
internal class CategoryGroupListContext(
    val state: AppState,
    val navigation: AppNavigation,
    val mobileSession: TandemMobileSession,
    val screen: CategoryGroupScreenState,
    val reorder: CategoryGroupReorder,
    val isLiveBudget: Boolean,
    val isTrackingBudget: Boolean,
)

/** Month label with edit button, reorder message and the group's amount summary. */
internal fun LazyListScope.categoryGroupHeaderItems(
    group: LiveCategoryGroupSummary,
    categoryMoveStatus: BudgetReorderStatus,
    context: CategoryGroupListContext,
) {
    val state = context.state
    val mobileSession = context.mobileSession
    item(key = "category-group-title:${group.id}") {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            Box(Modifier.weight(1f)) {
                Badge(state.selectedMonth, tandemPalette.muted)
            }
            if (context.isLiveBudget) {
                TandemIconButton(
                    Lucide.Pencil,
                    "Edit category group",
                    enabled = !state.readOnlyMode,
                ) {
                    context.screen.groupNameDraft = group.name
                    mobileSession.feedback.clearBudgetActionMessage()
                    context.screen.showGroupDialog = true
                }
            }
        }
    }
    categoryMoveStatus.message?.let { message ->
        item(key = "category-group-reorder-message:${group.id}") {
            CategoryReorderMessage(state, mobileSession, message, categoryMoveStatus)
        }
    }
    item(key = "category-group-summary:${group.id}") {
        val hero = categoryGroupHero(group, context.isTrackingBudget) { state.formatCurrency(it) }
        AmountHero(hero.first, state.formatCurrency(hero.second), hero.third)
    }
}

/** Reorder failure or warning with a retry action while moves are still queued. */
@Composable
internal fun CategoryReorderMessage(
    state: AppState,
    mobileSession: TandemMobileSession,
    message: String,
    status: BudgetReorderStatus,
) {
    InlineMessage(
        message,
        if (status.warning) InlineMessageKind.WARNING else InlineMessageKind.ERROR,
        Modifier.padding(horizontal = TandemSpacing.large),
        actionLabel = "Try again".takeIf { status.pendingCount > 0 },
        onAction =
            {
                mobileSession.budgetQueue.retryQueuedBudgetReorders(
                    state.selectedBudgetId,
                    state.encryptedPassword.takeIf(String::isNotBlank),
                )
            }.takeIf { status.pendingCount > 0 },
    )
}

/** Draggable rows for the group's visible categories. */
internal fun LazyListScope.categoryGroupCategoryItems(
    group: LiveCategoryGroupSummary,
    orderedCategories: List<LiveCategorySummary>,
    context: CategoryGroupListContext,
) {
    items(orderedCategories, key = { "category-group:${group.id}:${it.id}" }) { category ->
        CategoryGroupCategoryCard(group, category, orderedCategories, context)
    }
}

@Composable
private fun LazyItemScope.CategoryGroupCategoryCard(
    group: LiveCategoryGroupSummary,
    category: LiveCategorySummary,
    orderedCategories: List<LiveCategorySummary>,
    context: CategoryGroupListContext,
) {
    val state = context.state
    val screen = context.screen
    val reorder = context.reorder
    val isLiveBudget = context.isLiveBudget
    val isReordering = screen.selectedCategoryOrderId == category.id
    val categoryCardModifier =
        dragReorderItemModifier(
            itemId = category.id,
            dragState = reorder.dragReorder,
            reduceMotion = tandemReduceMotion,
            dragEnabled = isLiveBudget && !state.readOnlyMode && orderedCategories.size > 1,
            onDragStarted = { reorder.startDrag(category.id) },
            onDragBy = { reorder.dragBy(category.id, it) },
            onDragStopped = { reorder.dropDrag(category.id) },
        )
    SurfaceCard(
        modifier =
            categoryCardModifier
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = category.name,
                    onClick = {
                        if (reorderModeConsumesItemTap(screen.selectedCategoryOrderId)) {
                            screen.selectedCategoryOrderId = null
                        } else {
                            state.selectedCategoryGroupId = group.id
                            state.selectedCategoryId = category.id
                            state.categoryEditRequested = false
                            context.navigation.open(AppRoute.CATEGORY_DETAIL)
                        }
                    },
                ).then(
                    if (isLiveBudget && !state.readOnlyMode && orderedCategories.size > 1) {
                        Modifier.reorderAccessibilityAction(category.name) {
                            screen.selectedCategoryOrderId = category.id
                        }
                    } else {
                        Modifier
                    },
                ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryGroupCategoryRow(state, category, isReordering, budgetCategoryWords(group.isIncome, context.isTrackingBudget))
            if (isReordering && isLiveBudget) {
                CategoryGroupCategoryReorderActions(category, orderedCategories, context)
            }
        }
    }
}

@Composable
private fun RowScope.CategoryGroupCategoryRow(
    state: AppState,
    category: LiveCategorySummary,
    isReordering: Boolean,
    words: BudgetCategoryWords,
) {
    val palette = tandemPalette
    ListRow(
        category.name,
        if (isReordering) null else categoryRowSummary(category, words) { state.formatCurrency(it) },
        if (isReordering || words.plan == null) null else state.formatCurrency(category.assignedMinor),
        leading = RowLeading.Icon(state.categoryIconFor(category.id, category.name)),
        options =
            ListRowOptions(
                trailingColor = if (category.balanceMinor >= 0) palette.positive else palette.warning,
                showChevron = !isReordering,
            ),
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun CategoryGroupCategoryReorderActions(
    category: LiveCategorySummary,
    orderedCategories: List<LiveCategorySummary>,
    context: CategoryGroupListContext,
) {
    val state = context.state
    val reorder = context.reorder
    val categoryIndex = orderedCategories.indexOfFirst { it.id == category.id }
    val canMoveUp = categoryIndex > 0
    val canMoveDown = categoryIndex >= 0 && categoryIndex < orderedCategories.lastIndex
    val moveCategory: (Int) -> Unit = { direction ->
        reorder.enqueueCategoryMove(category.id, direction)
    }
    BudgetItemReorderActions(
        itemName = category.name,
        isHidden = false,
        canMoveUp = canMoveUp,
        canMoveDown = canMoveDown,
        enabled = !state.readOnlyMode,
        toggleEnabled = !state.readOnlyMode,
        dragEnabled = !state.readOnlyMode,
        onToggleHidden = {
            reorder.setCategoryHidden(category.id, reorder.categoryScopeId, hidden = true) {
                context.screen.selectedCategoryOrderId = null
                context.screen.showHidden = true
            }
        },
        onMoveUp = { moveCategory(-1) },
        onMoveDown = { moveCategory(1) },
        onDragStarted = { reorder.startDrag(category.id) },
        onDragBy = { reorder.dragBy(category.id, it) },
        onDragStopped = { reorder.dropDrag(category.id) },
    )
}

/** The group page's headline: what was planned (or received, for income), and what is left of it. */
internal fun categoryGroupHero(
    group: LiveCategoryGroupSummary,
    isTrackingBudget: Boolean,
    format: (Long) -> String,
): Triple<String, Long, String?> =
    when {
        group.isIncome ->
            Triple("Received this month", group.activityMinor, "${format(group.assignedMinor)} expected".takeIf { isTrackingBudget })
        isTrackingBudget -> Triple("Budgeted this month", group.assignedMinor, "${format(group.balanceMinor)} remaining")
        else -> Triple("Assigned this month", group.assignedMinor, "${format(group.balanceMinor)} available")
    }

/** One category's month in a line: spending and what is left, or income received against what was expected. */
internal fun categoryRowSummary(
    category: LiveCategorySummary,
    words: BudgetCategoryWords,
    format: (Long) -> String,
): String =
    when {
        words.isIncome && words.plan != null -> "Received ${format(category.activityMinor)} of ${format(category.assignedMinor)} expected"
        words.isIncome -> "Received ${format(category.activityMinor)}"
        else -> "Spent ${format(kotlin.math.abs(category.activityMinor))} · ${format(category.balanceMinor)} ${words.left}"
    }
