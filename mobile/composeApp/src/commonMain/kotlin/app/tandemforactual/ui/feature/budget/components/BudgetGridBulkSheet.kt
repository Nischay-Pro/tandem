@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.store.observeRecords
import app.tandemforactual.ui.core.format.reportMonthLabel
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.liveBudgetMonthSummary
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.design.buttons.SegmentedSelector
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.MoneyAmountInput
import app.tandemforactual.ui.feature.budget.BudgetMonthsView
import app.tandemforactual.ui.feature.budget.grid.BudgetGridData
import app.tandemforactual.ui.feature.budget.grid.BulkFill
import app.tandemforactual.ui.feature.budget.grid.GridKey
import app.tandemforactual.ui.feature.budget.grid.MonthFigures
import app.tandemforactual.ui.feature.budget.grid.bulkAmount
import app.tandemforactual.ui.feature.budget.grid.bulkHistoryMonths
import app.tandemforactual.ui.feature.budget.grid.gridChanges
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Fills the selected cells from one rule, with every new amount shown and editable before anything is saved. */
@Composable
internal fun BudgetGridBulkSheet(
    state: AppState,
    mobileSession: TandemMobileSession,
    months: BudgetMonthsView,
    data: BudgetGridData,
) {
    val grid = months.grid
    val open = grid.bulkOpen
    val cells =
        remember(open, data) {
            grid
                .range(data)
                ?.cells()
                .orEmpty()
                .filter { data.key(it) != null }
        }
    val keys = cells.mapNotNull(data::key)
    val history by rememberBulkHistory(state, mobileSession, keys.map { it.monthId }.toSet(), open)
    var fill by remember(open) { mutableStateOf(BulkFill.SET) }
    var amountText by remember(open) { mutableStateOf("") }
    var percentText by remember(open) { mutableStateOf("") }
    val edited = remember(open, fill) { mutableStateMapOf<GridKey, String>() }
    val percent = percentText.trim().toLongOrNull() ?: 0L
    val amount = state.parseAmountMinorUnits(amountText)

    fun proposed(key: GridKey): Long? =
        data.amounts(key)?.budgeted?.let { current -> bulkAmount(fill, key, current, amount, percent) { history[it] } }

    fun close() {
        grid.bulkOpen = false
    }
    TandemTaskSheet(visible = open, onDismissRequest = ::close, onDismissed = {}) {
        SheetCard(
            maxWidth = 560.dp,
            header = { TandemModalHeader(title = "Fill ${cellCount(keys.size)}", onClose = ::close) },
            actions = {
                TandemActionButton("Fill", TandemActionButtonTone.PRIMARY, icon = Lucide.Check, enabled = !state.readOnlyMode) {
                    val targets =
                        cells.zip(keys).map { (cell, key) ->
                            cell to (edited[key]?.let { state.parseAmountMinorUnits(it) } ?: proposed(key))
                        }
                    grid.apply(gridChanges(targets, data::budgeted), data, months.gridIo)
                    close()
                }
            },
        ) {
            BulkFillChoice(fill) { fill = it }
            if (fill == BulkFill.SET) {
                MoneyAmountInput("Amount", amountText, state.formatAmount(0), { amountText = it })
            } else {
                MoneyAmountInput("Adjust by %", percentText, "0", { percentText = it }, fractionDigits = 0)
            }
            keys.forEach { key ->
                val name = months.category(key.monthId, key.categoryId)?.name ?: return@forEach
                val now = data.amounts(key)?.budgeted ?: 0L
                MoneyAmountInput(
                    "$name · ${reportMonthLabel(key.monthId)} · now ${state.formatAmount(now)}",
                    edited[key] ?: proposed(key)?.let { state.formatAmount(it) }.orEmpty(),
                    "Unchanged",
                    { edited[key] = it },
                )
            }
            if (keys.isEmpty()) BodyText("Select some cells first.")
        }
    }
}

@Composable
private fun BulkFillChoice(
    fill: BulkFill,
    onChoose: (BulkFill) -> Unit,
) {
    BulkFill.entries.chunked(FILLS_PER_ROW).forEach { row ->
        SegmentedSelector(row.map { it.label to (it == fill) }) { onChoose(row[it]) }
    }
}

/** What each category budgeted and spent in the year before the months being filled, read once the sheet opens. */
@Composable
private fun rememberBulkHistory(
    state: AppState,
    mobileSession: TandemMobileSession,
    targets: Set<String>,
    open: Boolean,
) = produceState<Map<GridKey, MonthFigures>>(emptyMap(), open, targets) {
    val repository = mobileSession.repository
    if (!open || repository == null) return@produceState
    val wanted = bulkHistoryMonths(targets)
    val records = repository.observeRecords(state.selectedBudgetId, "budget-month").first()
    val categories = mobileSession.categoryRecords
    val groups = mobileSession.categoryGroupRecords
    value =
        withContext(Dispatchers.Default) {
            records
                .filter { it.recordId in wanted }
                .flatMap { record ->
                    val month = liveBudgetMonthSummary(record, categories, groups) ?: return@flatMap emptyList()
                    month.groups.flatMap { it.categories }.map { category ->
                        GridKey(category.id, record.recordId) to
                            MonthFigures(category.assignedMinor, (-category.activityMinor).coerceAtLeast(0))
                    }
                }.toMap()
        }
}

private fun cellCount(count: Int): String = if (count == 1) "1 cell" else "$count cells"

private const val FILLS_PER_ROW = 3
