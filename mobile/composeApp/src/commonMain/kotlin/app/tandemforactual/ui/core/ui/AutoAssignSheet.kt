@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.store.findRecord
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.applyBudgetTemplate
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.AutoAssignRule
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.liveBudgetMonthSummary
import app.tandemforactual.ui.core.state.monthBefore
import app.tandemforactual.ui.core.state.planAutoAssign
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import kotlinx.coroutines.launch

/**
 * Shares out the money left to budget this month by the chosen rule. Every change is previewed first and applied as
 * one edit, never assigning more than is left.
 */
@Composable
internal fun AutoAssignSheet(
    visible: Boolean,
    state: AppState,
    mobileSession: TandemMobileSession,
    month: LiveBudgetMonthSummary?,
    onDismiss: () -> Unit,
) {
    val available = month?.toBudgetMinor ?: 0
    var rule by remember { mutableStateOf(AutoAssignRule.COVER_OVERSPENDING) }
    var history by remember { mutableStateOf<List<LiveBudgetMonthSummary>>(emptyList()) }
    LaunchedEffect(visible, state.selectedBudgetId, state.selectedMonthId) {
        if (!visible) return@LaunchedEffect
        mobileSession.feedback.clearBudgetActionMessage()
        history = loadHistory(state, mobileSession)
    }
    val changes = remember(rule, month, history, available) { month?.let { planAutoAssign(rule, it, history, available) }.orEmpty() }
    val scope = rememberCoroutineScope()
    TandemChoiceDialog(visible = visible, onDismissRequest = onDismiss) {
        SheetCard(
            maxWidth = 460.dp,
            header = {
                TandemModalHeader("Auto-assign", onClose = onDismiss, leadingIcon = Lucide.Sparkles)
            },
            actions = {
                TandemActionButton(
                    if (changes.isEmpty()) "Nothing to assign" else "Assign ${state.formatCurrency(changes.sumOf { it.addedMinor })}",
                    TandemActionButtonTone.PRIMARY,
                    Modifier.fillMaxWidth(),
                    icon = Lucide.Check,
                    enabled = changes.isNotEmpty() && !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                    loading = mobileSession.isPerformingBudgetAction,
                ) {
                    scope.launch {
                        val applied =
                            mobileSession.budgetEdits.applyBudgetTemplate(
                                state.selectedBudgetId,
                                state.selectedMonthId,
                                changes.map { it.categoryId to it.toMinor },
                                state.encryptedPassword.takeIf { it.isNotBlank() },
                            )
                        if (applied) onDismiss()
                    }
                }
            },
        ) {
            Text("${state.formatCurrency(available)} left to budget", color = tandemPalette.muted, fontSize = TandemTypography.label)
            FilterPills(AutoAssignRule.entries.map { it to it.label }, rule, { rule = it })
            Text(rule.detail, color = tandemPalette.muted, fontSize = TandemTypography.label)
            AutoAssignPreview(state, changes.map { it.name to it.addedMinor })
            budgetActionFeedbackMessage(mobileSession)
        }
    }
}

/** Each category the rule would add to, and by how much. */
@Composable
private fun AutoAssignPreview(
    state: AppState,
    lines: List<Pair<String, Long>>,
) {
    if (lines.isEmpty()) {
        Text("This rule has nothing to add this month.", color = tandemPalette.muted, fontSize = TandemTypography.body)
        return
    }
    Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
        lines.forEachIndexed { index, (name, added) ->
            if (index > 0) DividerLine()
            ListRow(name, trailing = "+${state.formatCurrency(added)}", options = ListRowOptions(showChevron = false, dense = true))
        }
    }
}

/** The three months before the selected one, as far as this device has them, most recent first. */
private suspend fun loadHistory(
    state: AppState,
    mobileSession: TandemMobileSession,
): List<LiveBudgetMonthSummary> {
    val repository = mobileSession.repository ?: return emptyList()
    var monthId = state.selectedMonthId.takeIf { it.length >= 7 } ?: return emptyList()
    return buildList {
        repeat(3) {
            monthId = monthBefore(monthId)
            val record = repository.findRecord(state.selectedBudgetId, "budget-month", monthId)
            liveBudgetMonthSummary(record, mobileSession.categoryRecords, mobileSession.categoryGroupRecords)?.let(::add)
        }
    }
}
