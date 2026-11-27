@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.schedules.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.actual.DiscoveredSchedule
import app.tandemforactual.data.actual.upcomingScheduleDates
import app.tandemforactual.ui.core.format.rememberTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.session.schedules.manageBudgetSchedule
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SkeletonRows
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The recurring payments found in the budget's history and which of them to add as schedules. */
internal class ScheduleDiscoveryState {
    var visible by mutableStateOf(false)
    var results by mutableStateOf<List<DiscoveredSchedule>?>(null)
    var failed by mutableStateOf(false)
    var selected by mutableStateOf(emptySet<Int>())
    var attempt by mutableStateOf(0)

    fun open() {
        results = null
        failed = false
        selected = emptySet()
        attempt += 1
        visible = true
    }
}

/** The new schedule Actual's "Find schedules" creates from a found payment: no name, linked to every payment it matches. */
internal fun DiscoveredSchedule.createFields(): JsonObject =
    buildJsonObject {
        put("discovered", true)
        put("accountId", accountId)
        put("payeeId", payeeId)
        put("amountOp", if (exactAmount) "is" else "isapprox")
        put("amountMinor", amountMinor)
        put("date", Json.parseToJsonElement(dateJson))
        put("dateOp", if (exactDate) "is" else "isapprox")
    }

/** Finds recurring payments, previews each one's pattern and next date, and adds the chosen ones as schedules. */
@Composable
internal fun ScheduleDiscoverySheet(
    state: AppState,
    mobileSession: TandemMobileSession,
    discovery: ScheduleDiscoveryState,
    names: (DiscoveredSchedule) -> Pair<String, String>,
) {
    LaunchedEffect(discovery.attempt) {
        if (!discovery.visible) return@LaunchedEffect
        val found = mobileSession.insights.discoverSchedules(state.selectedBudgetId, state.encryptedPassword.takeIf { it.isNotBlank() })
        discovery.failed = found == null
        discovery.results = found
    }
    val close = { if (!mobileSession.isPerformingBudgetAction) discovery.visible = false }
    TandemTaskSheet(visible = discovery.visible, onDismissRequest = close, onDismissed = {}) {
        SheetCard(maxWidth = 560.dp, header = { TandemModalHeader(title = "Recurring payments", onClose = { close() }) }) {
            val results = discovery.results
            when {
                discovery.failed ->
                    InlineMessage(
                        "Couldn’t look through this budget’s transactions.",
                        InlineMessageKind.ERROR,
                        actionLabel = "Try again",
                        onAction = { discovery.open() },
                    )
                results == null -> SkeletonRows(count = 4)
                results.isEmpty() ->
                    EmptyState(
                        "No recurring payments found",
                        "Payments to the same payee, for a similar amount, at a regular interval show here.",
                        modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    )
                else -> DiscoveredScheduleList(state, mobileSession, discovery, results, names)
            }
        }
    }
}

@Composable
private fun DiscoveredScheduleList(
    state: AppState,
    mobileSession: TandemMobileSession,
    discovery: ScheduleDiscoveryState,
    results: List<DiscoveredSchedule>,
    names: (DiscoveredSchedule) -> Pair<String, String>,
) {
    val today = rememberTodayIsoDate()
    val scope = rememberCoroutineScope()
    results.forEachIndexed { index, found ->
        if (index > 0) DividerLine()
        val selected = index in discovery.selected
        val (payee, account) = names(found)
        val next = upcomingScheduleDates(found.dateJson, today, 1).firstOrNull()
        ListRow(
            payee,
            listOfNotNull(scheduleRepeatLabel(found.dateJson), account, next?.let { "Next ${formatLiveDate(it)}" }).joinToString(" · "),
            (if (found.exactAmount) "" else "About ") + state.formatCurrency(found.amountMinor),
            leading = RowLeading.Icon(if (selected) Lucide.CircleCheck else Lucide.Circle),
            options = ListRowOptions(selected = selected, showChevron = false),
            onClick = { discovery.selected = if (selected) discovery.selected - index else discovery.selected + index },
        )
    }
    budgetActionFeedbackMessage(mobileSession)
    val count = discovery.selected.size
    TandemActionButton(
        when (count) {
            0 -> "Choose payments to add"
            1 -> "Add 1 schedule"
            else -> "Add $count schedules"
        },
        TandemActionButtonTone.PRIMARY,
        modifier = Modifier.fillMaxWidth(),
        enabled = count > 0 && !mobileSession.isPerformingBudgetAction,
        loading = mobileSession.isPerformingBudgetAction,
    ) {
        scope.launch {
            val chosen = discovery.selected.sorted().mapNotNull(results::getOrNull)
            val added =
                chosen.map { found ->
                    mobileSession.schedules.manageBudgetSchedule(
                        state.selectedBudgetId,
                        Uuid.random().toString(),
                        "create",
                        null,
                        state.encryptedPassword.takeIf { it.isNotBlank() },
                        found.createFields(),
                    )
                }
            if (added.all { it }) discovery.visible = false
        }
    }
}
