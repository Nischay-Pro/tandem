package app.tandemforactual.ui.feature.budget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.WindowWidthClass

internal val BudgetMonthLabelWidth = 220.dp
internal val BudgetMonthMinWidth = 264.dp
internal val BudgetMonthsMaxWidth = 1_440.dp
internal const val MAX_BUDGET_MONTHS = 6

/** Months shown side by side: only on Large windows and wider, and only once at least two fit. */
internal fun budgetMonthCount(
    widthClass: WindowWidthClass,
    availableWidth: Dp,
): Int {
    if (widthClass < WindowWidthClass.LARGE) return 1
    val fit = ((minOf(availableWidth, BudgetMonthsMaxWidth) - BudgetMonthLabelWidth) / BudgetMonthMinWidth).toInt()
    return if (fit < 2) 1 else fit.coerceAtMost(MAX_BUDGET_MONTHS)
}

/**
 * The months on screen, always including the selected one. The window stays put while the selection moves
 * inside it and slides only when the selection steps past an edge, so columns don't jump under the pointer.
 */
internal fun budgetMonthWindow(
    monthIds: List<String>,
    selectedMonthId: String,
    count: Int,
    previousStart: String?,
): List<String> {
    val months = (monthIds + selectedMonthId).filter { it.isNotBlank() }.distinct().sorted()
    val selected = months.indexOf(selectedMonthId)
    if (selected < 0) return emptyList()
    val size = count.coerceIn(1, months.size)
    var start = previousStart?.let(months::indexOf)?.takeIf { it >= 0 } ?: selected
    if (selected < start) start = selected
    if (selected >= start + size) start = selected - size + 1
    start = start.coerceIn(0, months.size - size)
    return months.subList(start, start + size)
}
