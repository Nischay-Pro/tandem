@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.schedules
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.format.parseIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.schedules.manageBudgetSchedule
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.formatTransactionListDate
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.rules.amountLabel
import app.tandemforactual.ui.feature.schedules.components.ScheduleActionsSheet
import app.tandemforactual.ui.feature.schedules.components.ScheduleDeleteConfirmationDialog
import app.tandemforactual.ui.feature.schedules.components.ScheduleDialogState
import app.tandemforactual.ui.feature.schedules.components.ScheduleEditDatePicker
import app.tandemforactual.ui.feature.schedules.components.ScheduleEditDraft
import app.tandemforactual.ui.feature.schedules.components.ScheduleEditForm
import app.tandemforactual.ui.feature.schedules.components.ScheduleSelectionState
import app.tandemforactual.ui.feature.schedules.components.editedScheduleFields
import app.tandemforactual.ui.feature.schedules.components.rememberScheduleDateValue
import app.tandemforactual.ui.feature.schedules.components.rememberScheduleEditDraft
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

@Composable
internal fun UpcomingSchedulesScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    schedules: List<LiveScheduleSummary>,
    accounts: List<LiveAccountSummary>,
    payeeRecords: List<BudgetRecordEntity>,
) {
    // A dismissed action sheet must stay dismissed when this route is restored from the
    // navigation stack. Persisting this transient visibility state can reopen the sheet
    // after Back returns to an earlier Upcoming bills route entry.
    val selection = remember(state.selectedBudgetId) { ScheduleSelectionState() }
    val dialog = remember(state.selectedBudgetId, selection.selectedScheduleId) { ScheduleDialogState() }
    LaunchedEffect(state.pendingScheduleToOpenId, schedules) {
        val requestedId = state.pendingScheduleToOpenId ?: return@LaunchedEffect
        if (schedules.any { it.id == requestedId }) {
            selection.selectedScheduleId = requestedId
            selection.showScheduleEditor = true
            dialog.dialogMode = "menu"
            state.pendingScheduleToOpenId = null
        }
    }
    val selectedSchedule = schedules.firstOrNull { it.id == selection.selectedScheduleId }
    val dateValue = rememberScheduleDateValue(selectedSchedule)
    val draft = rememberScheduleEditDraft(state, selection.selectedScheduleId, selectedSchedule, dateValue)
    val scope = rememberCoroutineScope()
    PageLazyColumn {
        upcomingScheduleItems(state, mobileSession, schedules) { schedule ->
            selection.selectedScheduleId = schedule.id
            selection.showScheduleEditor = true
            dialog.dialogMode = "menu"
            mobileSession.feedback.clearBudgetActionMessage()
        }
    }

    ScheduleActionsSheet(
        state,
        mobileSession,
        selection,
        dialog,
        selectedSchedule,
        scope,
    ) {
        ScheduleEditForm(
            state,
            mobileSession,
            draft,
            rememberScheduleAccountOptions(accounts),
            rememberSchedulePayeeOptions(payeeRecords, selectedSchedule),
            onSave = {
                if (selectedSchedule !=
                    null
                ) {
                    saveScheduleEdit(state, mobileSession, scope, draft, dateValue as? JsonObject, selectedSchedule, selection)
                }
            },
            onCancel = { dialog.dialogMode = "menu" },
        )
    }
    if (dialog.showDeleteScheduleConfirmation && selectedSchedule != null) {
        ScheduleDeleteConfirmationDialog(
            mobileSession,
            selectedSchedule,
            onDismissRequest = {
                if (!mobileSession.isPerformingBudgetAction) {
                    dialog.showDeleteScheduleConfirmation = false
                    dialog.dialogMode = "menu"
                }
            },
            onConfirm = { deleteSchedule(state, mobileSession, scope, selectedSchedule, selection, dialog) },
        )
    }
    ScheduleEditDatePicker(draft)
}

@Composable
private fun rememberSchedulePayeeOptions(
    payeeRecords: List<BudgetRecordEntity>,
    selectedSchedule: LiveScheduleSummary?,
): List<Pair<String, String>> =
    remember(payeeRecords, selectedSchedule?.payeeId) {
        val names =
            payeeRecords
                .mapNotNull { record ->
                    record.recordName()?.let { record.recordId to it }
                }.toMap()
                .toMutableMap()
        selectedSchedule?.payeeId?.let { id -> if (id !in names) names[id] = id }
        names.entries.sortedBy { it.value.lowercase() }.map { it.key to it.value }
    }

@Composable
private fun rememberScheduleAccountOptions(accounts: List<LiveAccountSummary>): List<Pair<String, String>> =
    remember(accounts) {
        accounts.filterNot(LiveAccountSummary::closed).sortedBy { it.name.lowercase() }.map { it.id to it.name }
    }

/** One card per upcoming schedule with its pending-action notices, or an empty state. */
private fun LazyListScope.upcomingScheduleItems(
    state: AppState,
    mobileSession: TandemMobileSession,
    schedules: List<LiveScheduleSummary>,
    onOpen: (LiveScheduleSummary) -> Unit,
) {
    if (schedules.isEmpty()) {
        item(key = "upcoming-schedules-empty") {
            SurfaceCard {
                EmptyState(
                    "No upcoming bills",
                    "Schedules added in Actual will appear here.",
                    modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                )
            }
        }
    } else {
        items(schedules.size, key = { index -> schedules[index].id }) { index ->
            val schedule = schedules[index]
            val pendingActions = mobileSession.schedules.pendingScheduleActionsFor(state.selectedBudgetId, schedule.id)
            SurfaceCard {
                pendingActions.forEach { pendingAction ->
                    InlineMessage(
                        text = mobileSession.schedules.scheduleActionStatusMessage(pendingAction),
                        kind =
                            if (pendingAction.status in setOf("uncertain", "conflict", "rejected")) {
                                InlineMessageKind.WARNING
                            } else {
                                InlineMessageKind.INFO
                            },
                        actionLabel = mobileSession.schedules.scheduleActionStatusActionLabel(pendingAction),
                        onAction =
                            if (mobileSession.schedules.scheduleActionStatusActionLabel(pendingAction) != null) {
                                { mobileSession.schedules.performScheduleActionStatusAction(pendingAction) }
                            } else {
                                null
                            },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ListRow(
                        schedule.name,
                        listOfNotNull(
                            formatTransactionListDate(schedule.nextDate),
                            schedule.accountName,
                        ).joinToString(" · "),
                        schedule.amountLabel(state),
                        leading = RowLeading.Icon(Lucide.CalendarDays),
                        onClick = { onOpen(schedule) },
                    )
                    TandemIconButton(
                        Lucide.ChevronRight,
                        "Schedule actions",
                        modifier = Modifier.padding(end = TandemSpacing.small),
                        sizeDp = 40,
                        iconSizeDp = 18,
                        containerColor = tandemPalette.raised,
                    ) {
                        onOpen(schedule)
                    }
                }
            }
        }
    }
}

/** Validates the draft and, when it is valid, sends the edit and closes the sheet once saved. */
private fun saveScheduleEdit(
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
    draft: ScheduleEditDraft,
    dateConfig: JsonObject?,
    selectedSchedule: LiveScheduleSummary,
    selection: ScheduleSelectionState,
) {
    val amountMinor = state.parseAmountMinorUnits(draft.amountDraft)
    val amountMaxMinor = state.parseAmountMinorUnits(draft.amountMaxDraft)
    val interval = draft.intervalDraft.toIntOrNull()
    val occurrences = draft.endOccurrencesDraft.toIntOrNull()
    val dateIsValid = parseIsoDate(draft.dateDraft) != null
    val endDateIsValid = draft.endModeDraft != "on_date" || parseIsoDate(draft.endDateDraft) != null
    val amountRangeIsInvalid =
        draft.amountRuleDraft == "isbetween" &&
            amountMinor != null &&
            amountMaxMinor != null &&
            amountMinor > amountMaxMinor
    val intervalIsInvalid =
        draft.frequencyDraft != "once" && (interval == null || interval !in 1..999)
    val repeatCountIsInvalid =
        draft.frequencyDraft != "once" &&
            draft.endModeDraft == "after_n_occurrences" &&
            (occurrences == null || occurrences !in 1..100_000)
    val endBeforeStart = draft.endModeDraft == "on_date" && draft.endDateDraft < draft.dateDraft
    val validationError =
        when {
            draft.nameDraft.isBlank() -> "name" to "Enter a schedule name."
            draft.accountIdDraft.isBlank() -> "account" to "Choose an account."
            draft.payeeIdDraft.isBlank() -> "payee" to "Choose a payee."
            amountMinor == null -> "amount" to "Enter a valid amount."
            draft.amountRuleDraft == "isbetween" && amountMaxMinor == null ->
                "maximum" to "Enter a valid maximum amount."
            amountRangeIsInvalid -> "maximum" to "The maximum must be at least the minimum."
            intervalIsInvalid -> "interval" to "Repeat interval must be between 1 and 999."
            repeatCountIsInvalid -> "occurrences" to "Enter a valid number of repeats."
            !dateIsValid -> "startDate" to "Choose a valid start date."
            !endDateIsValid -> "endDate" to "Choose a valid end date."
            endBeforeStart -> "endDate" to "The end date must be on or after the start date."
            else -> null
        }
    draft.editErrorField = validationError?.first
    draft.editError = validationError?.second
    if (draft.editError == null) {
        val fields = draft.editedScheduleFields(dateConfig, amountMinor, amountMaxMinor, interval, occurrences)
        scope.launch {
            val saved =
                mobileSession.schedules.manageBudgetSchedule(
                    state.selectedBudgetId,
                    selectedSchedule.id,
                    "edit",
                    null,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                    fields,
                )
            if (saved) selection.showScheduleEditor = false
        }
    }
}

private fun deleteSchedule(
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
    selectedSchedule: LiveScheduleSummary,
    selection: ScheduleSelectionState,
    dialog: ScheduleDialogState,
) {
    scope.launch {
        val deleted =
            mobileSession.schedules.manageBudgetSchedule(
                state.selectedBudgetId,
                selectedSchedule.id,
                "delete",
                null,
                state.encryptedPassword.takeIf { it.isNotBlank() },
            )
        if (deleted) {
            dialog.showDeleteScheduleConfirmation = false
            selection.showScheduleEditor = false
        } else {
            dialog.showDeleteScheduleConfirmation = false
            dialog.dialogMode = "menu"
        }
    }
}
