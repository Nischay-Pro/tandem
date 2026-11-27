@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.schedules
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.app.ListDetailPanes
import app.tandemforactual.ui.core.format.amountLabel
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.formatTransactionListDate
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.theme.TandemBackHandler
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.schedules.components.ScheduleActionsPane
import app.tandemforactual.ui.feature.schedules.components.ScheduleActionsSheet
import app.tandemforactual.ui.feature.schedules.components.ScheduleCreatePane
import app.tandemforactual.ui.feature.schedules.components.ScheduleCreateSheet
import app.tandemforactual.ui.feature.schedules.components.ScheduleDeleteConfirmationDialog
import app.tandemforactual.ui.feature.schedules.components.ScheduleDialogState
import app.tandemforactual.ui.feature.schedules.components.ScheduleDraftValues
import app.tandemforactual.ui.feature.schedules.components.ScheduleEditDatePicker
import app.tandemforactual.ui.feature.schedules.components.ScheduleEditDraft
import app.tandemforactual.ui.feature.schedules.components.ScheduleEditForm
import app.tandemforactual.ui.feature.schedules.components.ScheduleSelectionState
import app.tandemforactual.ui.feature.schedules.components.scheduleDraftValues
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import kotlinx.serialization.json.Json
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
    ScheduleOpenRequests(state, schedules, selection, dialog)
    val selectedSchedule = schedules.firstOrNull { it.id == selection.selectedScheduleId }
    val dateValue =
        remember(selectedSchedule?.id, selectedSchedule?.dateValueJson) {
            selectedSchedule?.dateValueJson?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
        }
    val editDraft =
        rememberSaveable(state.selectedBudgetId, selection.selectedScheduleId, saver = ScheduleEditDraft.Saver) {
            val categoryId = selectedSchedule?.let { scheduleCategoryId(mobileSession.ruleRecords, it.ruleId) }
            ScheduleEditDraft(
                selectedSchedule?.let { scheduleDraftValues(it, dateValue, categoryId, state::formatAmount) } ?: ScheduleDraftValues(),
            )
        }
    val createDraft =
        rememberSaveable(state.selectedBudgetId, selection.createKey, saver = ScheduleEditDraft.Saver) {
            ScheduleEditDraft(selection.createValues)
        }
    val scope = rememberCoroutineScope()
    val options = rememberScheduleFormOptions(accounts, payeeRecords, mobileSession.categoryRecords, selectedSchedule)
    val editForm: @Composable () -> Unit = {
        ScheduleEditForm(
            state,
            mobileSession,
            editDraft,
            options,
            "Save schedule",
            onSave = {
                if (selectedSchedule != null) {
                    saveScheduleEdit(state, mobileSession, scope, editDraft, dateValue as? JsonObject, selectedSchedule, selection)
                }
            },
            onCancel = { dialog.dialogMode = "menu" },
        )
    }
    val createForm: @Composable () -> Unit = {
        ScheduleEditForm(
            state,
            mobileSession,
            createDraft,
            options,
            "Add schedule",
            onSave = { saveNewSchedule(state, mobileSession, scope, createDraft, selection) },
            onCancel = { selection.creating = false },
        )
    }
    ListDetailPanes(
        detailKey = if (selection.creating) "new-schedule" else selectedSchedule?.id?.takeIf { selection.showScheduleEditor },
        emptyTitle = "Choose a bill",
        emptyMessage = "Post it, skip it or change it here.",
        list = { detailInPane ->
            UpcomingScheduleList(state, mobileSession, schedules, selection, dialog, detailInPane)
            if (!detailInPane) {
                ScheduleActionsSheet(state, mobileSession, selection, dialog, selectedSchedule, scope, editForm)
                ScheduleCreateSheet(mobileSession, selection, createForm)
            }
        },
        detail = {
            when {
                selection.creating -> ScheduleCreatePane(mobileSession, selection, createForm)
                selectedSchedule != null -> ScheduleActionsPane(state, mobileSession, selection, dialog, selectedSchedule, scope, editForm)
            }
        },
    )
    if (dialog.showDeleteScheduleConfirmation && selectedSchedule != null) {
        ScheduleDeleteDialog(state, mobileSession, selectedSchedule, selection, dialog)
    }
    ScheduleEditDatePicker(if (selection.creating) createDraft else editDraft)
}

@Composable
private fun ScheduleDeleteDialog(
    state: AppState,
    mobileSession: TandemMobileSession,
    schedule: LiveScheduleSummary,
    selection: ScheduleSelectionState,
    dialog: ScheduleDialogState,
) {
    val scope = rememberCoroutineScope()
    ScheduleDeleteConfirmationDialog(
        mobileSession,
        schedule,
        onDismissRequest = {
            if (!mobileSession.isPerformingBudgetAction) {
                dialog.showDeleteScheduleConfirmation = false
                dialog.dialogMode = "menu"
            }
        },
        onConfirm = { deleteSchedule(state, mobileSession, scope, schedule, selection, dialog) },
    )
}

/** Opens a schedule another screen asked for, or a new schedule made from a transaction. */
@Composable
private fun ScheduleOpenRequests(
    state: AppState,
    schedules: List<LiveScheduleSummary>,
    selection: ScheduleSelectionState,
    dialog: ScheduleDialogState,
) {
    LaunchedEffect(state.pendingScheduleToOpenId, schedules) {
        val requestedId = state.pendingScheduleToOpenId ?: return@LaunchedEffect
        if (schedules.any { it.id == requestedId }) {
            selection.selectedScheduleId = requestedId
            selection.showScheduleEditor = true
            dialog.dialogMode = "menu"
            state.pendingScheduleToOpenId = null
        }
    }
    LaunchedEffect(state.pendingScheduleSeed) {
        val seed = state.pendingScheduleSeed ?: return@LaunchedEffect
        selection.startCreating(scheduleDraftValues(seed, state::formatAmount), seed.transactionId)
        state.pendingScheduleSeed = null
    }
}

/** The upcoming bills; in a pane layout the open one is marked and back closes it. */
@Composable
private fun UpcomingScheduleList(
    state: AppState,
    mobileSession: TandemMobileSession,
    schedules: List<LiveScheduleSummary>,
    selection: ScheduleSelectionState,
    dialog: ScheduleDialogState,
    detailInPane: Boolean,
) {
    val openId = selection.selectedScheduleId.takeIf { detailInPane && selection.showScheduleEditor && !selection.creating }
    TandemBackHandler(
        enabled = detailInPane && (openId != null || selection.creating),
        onBack = {
            if (!mobileSession.isPerformingBudgetAction) {
                selection.showScheduleEditor = false
                selection.creating = false
                dialog.dialogMode = "menu"
            }
        },
        onBackProgress = { _, _ -> },
    )
    PageLazyColumn {
        item(key = "upcoming-schedules-header") {
            ScheduleCountRow(schedules.size, canAdd = !state.readOnlyMode) {
                mobileSession.feedback.clearBudgetActionMessage()
                selection.startCreating(ScheduleDraftValues(date = platformTodayIsoDate(), endDate = platformTodayIsoDate()))
            }
        }
        upcomingScheduleItems(state, mobileSession, schedules, openId) { schedule ->
            selection.creating = false
            selection.selectedScheduleId = schedule.id
            selection.showScheduleEditor = true
            dialog.dialogMode = "menu"
            mobileSession.feedback.clearBudgetActionMessage()
        }
    }
}

/** How many schedules there are, and the button to add one. */
@Composable
private fun ScheduleCountRow(
    count: Int,
    canAdd: Boolean,
    onAdd: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.small), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (count == 1) "1 schedule" else "${formatInteger(count)} schedules",
            modifier = Modifier.weight(1f),
            color = tandemPalette.muted,
            fontSize = TandemTypography.caption,
        )
        TandemIconButton(
            Lucide.Plus,
            "Add schedule",
            enabled = canAdd,
            tint = tandemPalette.accent,
            containerColor = tandemPalette.accentSurface,
            onClick = onAdd,
        )
    }
}

/** One card per upcoming schedule with its pending-action notices, or an empty state. */
private fun LazyListScope.upcomingScheduleItems(
    state: AppState,
    mobileSession: TandemMobileSession,
    schedules: List<LiveScheduleSummary>,
    openId: String?,
    onOpen: (LiveScheduleSummary) -> Unit,
) {
    if (schedules.isEmpty()) {
        item(key = "upcoming-schedules-empty") {
            SurfaceCard {
                EmptyState(
                    "No upcoming bills",
                    "Add a schedule for rent, subscriptions or pay, and Tandem shows when each one is due.",
                    modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                )
            }
        }
    } else {
        items(schedules.size, key = { index -> schedules[index].id }) { index ->
            val schedule = schedules[index]
            SurfaceCard(highlighted = schedule.id == openId) {
                SchedulePendingNotices(state, mobileSession, schedule)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ListRow(
                        schedule.name,
                        listOfNotNull(formatTransactionListDate(schedule.nextDate), schedule.accountName).joinToString(" · "),
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

@Composable
private fun SchedulePendingNotices(
    state: AppState,
    mobileSession: TandemMobileSession,
    schedule: LiveScheduleSummary,
) {
    mobileSession.schedules.pendingScheduleActionsFor(state.selectedBudgetId, schedule.id).forEach { pendingAction ->
        val actionLabel = mobileSession.schedules.scheduleActionStatusActionLabel(pendingAction)
        InlineMessage(
            text = mobileSession.schedules.scheduleActionStatusMessage(pendingAction),
            kind =
                if (pendingAction.status in setOf("uncertain", "conflict", "rejected")) {
                    InlineMessageKind.WARNING
                } else {
                    InlineMessageKind.INFO
                },
            actionLabel = actionLabel,
            onAction = if (actionLabel != null) ({ mobileSession.schedules.performScheduleActionStatusAction(pendingAction) }) else null,
        )
    }
}
