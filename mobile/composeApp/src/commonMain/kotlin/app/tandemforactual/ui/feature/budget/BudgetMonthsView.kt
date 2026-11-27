package app.tandemforactual.ui.feature.budget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.store.observeRecords
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.pendingBudgetMutationsFor
import app.tandemforactual.ui.core.session.budget.pendingCategoryBudgetOverrides
import app.tandemforactual.ui.core.session.budget.pendingCategoryCarryoverOverrides
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.liveBudgetMonthSummary
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.currentWindowSizeInfo
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** Several budget months side by side on wide windows, with the selected month highlighted by a sliding band. */
internal class BudgetMonthsView(
    val monthIds: List<String>,
    summaries: Map<String, LiveBudgetMonthSummary>,
    val enabled: Boolean,
    val band: State<Float>,
    val onEnabledChange: (Boolean) -> Unit,
) {
    /** More than one month fits, so the choice between one month and several is offered. */
    val available = monthIds.size >= 2
    val sideBySide = available && enabled
    private val categories = summaries.mapValues { (_, month) -> month.groups.flatMap { it.categories }.associateBy { it.id } }
    private val groups = summaries.mapValues { (_, month) -> month.groups.associateBy { it.id } }
    private val toBudget = summaries.mapValues { (_, month) -> month.toBudgetMinor }

    fun category(
        monthId: String,
        categoryId: String,
    ): LiveCategorySummary? = categories[monthId]?.get(categoryId)

    fun group(
        monthId: String,
        groupId: String,
    ): LiveCategoryGroupSummary? = groups[monthId]?.get(groupId)

    fun toBudget(monthId: String): Long? = toBudget[monthId]
}

@Composable
internal fun rememberBudgetMonthsView(
    state: AppState,
    mobileSession: TandemMobileSession,
    selectedMonth: LiveBudgetMonthSummary?,
    availableWidth: Dp,
): BudgetMonthsView {
    val count = budgetMonthCount(currentWindowSizeInfo().widthClass, availableWidth)
    var enabled by rememberSaveable { mutableStateOf(true) }
    var start by rememberSaveable(state.selectedBudgetId) { mutableStateOf<String?>(null) }
    val monthIds =
        remember(mobileSession.budgetMonthIds, state.selectedMonthId, count, start) {
            if (count < 2) emptyList() else budgetMonthWindow(mobileSession.budgetMonthIds, state.selectedMonthId, count, start)
        }
    LaunchedEffect(monthIds.firstOrNull()) { monthIds.firstOrNull()?.let { start = it } }
    val loaded = if (enabled && monthIds.size >= 2) monthIds else emptyList()
    val summaries = rememberBudgetMonthSummaries(state, mobileSession, loaded, selectedMonth)
    val band =
        animateFloatAsState(
            monthIds.indexOf(state.selectedMonthId).coerceAtLeast(0).toFloat(),
            tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS),
            label = "budget month band",
        )
    return remember(monthIds, summaries, enabled) { BudgetMonthsView(monthIds, summaries, enabled, band) { enabled = it } }
}

/** Each shown month's summary with edits still waiting on this device; the selected month reuses the screen's own. */
@Composable
private fun rememberBudgetMonthSummaries(
    state: AppState,
    mobileSession: TandemMobileSession,
    monthIds: List<String>,
    selectedMonth: LiveBudgetMonthSummary?,
): Map<String, LiveBudgetMonthSummary> {
    val budgetId = state.selectedBudgetId
    val repository = mobileSession.repository
    val watching = monthIds.isNotEmpty()
    val records by remember(repository, budgetId, watching) {
        if (repository == null || !watching) emptyRecords() else repository.observeRecords(budgetId, "budget-month")
    }.collectAsState(emptyList())
    val queue = mobileSession.budgetQueue
    val pending = queue.pendingBudgetMutationsFor(budgetId)
    val budgetOverrides = monthIds.associateWith { queue.pendingCategoryBudgetOverrides(budgetId, it) }
    val carryoverOverrides = monthIds.associateWith { queue.pendingCategoryCarryoverOverrides(budgetId, it) }
    val categories = mobileSession.categoryRecords
    val groups = mobileSession.categoryGroupRecords
    val inputs = listOf(records, monthIds, categories, groups, pending, budgetOverrides, carryoverOverrides)
    val built by produceState<Map<String, LiveBudgetMonthSummary>>(emptyMap(), inputs) {
        value =
            withContext(Dispatchers.Default) {
                monthIds
                    .mapNotNull { monthId ->
                        val record = records.firstOrNull { it.recordId == monthId } ?: return@mapNotNull null
                        liveBudgetMonthSummary(
                            record,
                            categories,
                            groups,
                            categoryBudgetOverrides = budgetOverrides[monthId].orEmpty(),
                            categoryCarryoverOverrides = carryoverOverrides[monthId].orEmpty(),
                            pendingBudgetMutations = pending,
                        )?.let { monthId to it }
                    }.toMap()
            }
    }
    return remember(built, selectedMonth, state.selectedMonthId, monthIds) {
        if (selectedMonth == null || state.selectedMonthId !in monthIds) built else built + (state.selectedMonthId to selectedMonth)
    }
}

private fun emptyRecords(): Flow<List<BudgetRecordEntity>> = flowOf(emptyList())
