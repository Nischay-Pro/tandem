@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.currentMonthId
import app.tandemforactual.ui.core.format.daysInMonth
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.keyboard.ShortcutAction
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LocalRouteLazyListState
import app.tandemforactual.ui.core.state.MoveMoneyRequest
import app.tandemforactual.ui.core.state.isTrackingBudget
import app.tandemforactual.ui.design.rows.RowMenuAction
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.budget.BudgetMonthsView
import app.tandemforactual.ui.feature.budget.grid.BudgetGridData
import app.tandemforactual.ui.feature.budget.grid.BudgetGridState
import app.tandemforactual.ui.feature.budget.grid.GridAmounts
import app.tandemforactual.ui.feature.budget.grid.GridCellContext
import app.tandemforactual.ui.feature.budget.grid.GridKey
import app.tandemforactual.ui.feature.budget.grid.perform
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.WalletCards

internal const val BUDGET_MONTHS_HEADER_KEY = "budget-months-header"
private val TODAY_TRACK = 2.dp

/** The grid over the months on screen: every spending category in screen order, income left out. */
internal fun budgetGridData(
    months: BudgetMonthsView,
    groups: List<LiveCategoryGroupSummary>,
): BudgetGridData =
    BudgetGridData(
        rows = groups.filterNot { it.isIncome }.flatMap { group -> group.categories.map { it.id } },
        columns = months.monthIds,
        previousMonth = months.previousMonth,
        earlierMonth = months.earlierMonth,
        laterMonth = months.laterMonth,
    ) { key ->
        months.category(key.monthId, key.categoryId)?.let { category ->
            GridAmounts(
                budgeted = category.assignedMinor,
                spent = (-category.activityMinor).coerceAtLeast(0),
                balance = category.balanceMinor,
                pending = category.pendingChanges,
            )
        }
    }

/** The same changes the keyboard makes, for right-click and long-press. */
internal fun budgetGridMenu(
    cells: GridCellContext,
    key: GridKey,
    onOpen: () -> Unit,
): List<RowMenuAction> {
    fun run(action: ShortcutAction) = cells.grid.perform(key, action, cells.data, cells.io)
    val edits =
        if (!cells.io.editable) {
            emptyList()
        } else {
            listOf(
                RowMenuAction("Edit amount", Lucide.Pencil) { run(ShortcutAction.GRID_EDIT) },
                RowMenuAction("Copy last month", Lucide.Copy) { run(ShortcutAction.GRID_COPY_PREVIOUS) },
                RowMenuAction("Budget what was spent", Lucide.Receipt) { run(ShortcutAction.GRID_SET_SPENT) },
                RowMenuAction("Set to zero", Lucide.RotateCcw) { run(ShortcutAction.GRID_CLEAR) },
            ) + moveMoneyActions(cells, key, onOpen)
        }
    return edits + RowMenuAction("Open category", Lucide.ExternalLink, onClick = onOpen)
}

private fun moveMoneyActions(
    cells: GridCellContext,
    key: GridKey,
    onOpen: () -> Unit,
): List<RowMenuAction> {
    if (cells.state.isTrackingBudget) return emptyList()

    fun request(coverMinor: Long) {
        cells.state.categoryMoveMoneyRequest = MoveMoneyRequest(coverMinor)
        onOpen()
    }
    val overspent = (cells.data.amounts(key)?.balance ?: 0L).takeIf { it < 0L }
    return listOfNotNull(
        overspent?.let { RowMenuAction("Cover overspending", Lucide.ShieldCheck) { request(-it) } },
        RowMenuAction("Move money", Lucide.WalletCards) { request(0L) },
    )
}

/**
 * Scrolls to the group holding the focused cell once it has left the screen, so the arrows never lose the cell; a cell
 * that is still composed brings itself into view as it takes focus.
 */
@Composable
internal fun BudgetGridFollowFocus(
    grid: BudgetGridState,
    groups: List<LiveCategoryGroupSummary>,
) {
    val listState = LocalRouteLazyListState.current ?: return
    val categoryId = grid.focus?.categoryId
    LaunchedEffect(categoryId, listState) {
        val position = groups.indexOfFirst { group -> group.categories.any { it.id == categoryId } }
        if (categoryId == null || position < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val header = visible.firstOrNull { it.key == BUDGET_MONTHS_HEADER_KEY } ?: return@LaunchedEffect
        val target = header.index + 1 + position
        if (visible.none { it.index == target }) listState.animateScrollToItem(target)
    }
}

/** On this month's header, how far through the month today is. */
@Composable
internal fun BudgetMonthTodayMarker(monthId: String) {
    val progress =
        remember(monthId) {
            if (monthId != currentMonthId()) return@remember null
            val day = platformTodayIsoDate().drop(8).take(2).toIntOrNull() ?: return@remember null
            val days = daysInMonth(monthId) ?: return@remember null
            day.toFloat() / days
        } ?: return
    val palette = tandemPalette
    val track = palette.border
    val fill = palette.accent
    Box(
        Modifier
            .fillMaxWidth()
            .height(TODAY_TRACK)
            .semantics { contentDescription = "Today, ${(progress * 100).toInt()}% through the month" }
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                val width = size.width * progress
                val left = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                drawRoundRect(fill, topLeft = Offset(left, 0f), size = Size(width, size.height), cornerRadius = radius)
            },
    )
}
