@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.schedules.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.session.schedules.manageBudgetSchedule
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The selected schedule's sheet: its actions, the edit form, or a pointer to the delete alert. */
@Composable
internal fun ScheduleActionsSheet(
    state: AppState,
    mobileSession: TandemMobileSession,
    selection: ScheduleSelectionState,
    dialog: ScheduleDialogState,
    selectedSchedule: LiveScheduleSummary?,
    scope: CoroutineScope,
    editForm: @Composable () -> Unit,
) {
    TandemTaskSheet(
        visible = selection.showScheduleEditor && selectedSchedule != null,
        onDismissRequest = {
            if (!mobileSession.isPerformingBudgetAction) {
                selection.showScheduleEditor = false
                dialog.showDeleteScheduleConfirmation = false
                selection.selectedScheduleId = null
                dialog.dialogMode = "menu"
                state.pendingScheduleToOpenId = null
            }
        },
        onDismissed = {
            dialog.showDeleteScheduleConfirmation = false
            selection.selectedScheduleId = null
            dialog.dialogMode = "menu"
            state.pendingScheduleToOpenId = null
        },
    ) {
        if (selectedSchedule != null) {
            SheetCard(
                maxWidth = 480.dp,
                header = {
                    ScheduleSheetHeader(state, mobileSession, selection, dialog, selectedSchedule)
                },
            ) {
                ScheduleActionsBody(state, mobileSession, selection, dialog, selectedSchedule, scope, editForm)
            }
        }
    }
}

/** The selected schedule's actions in the pane beside the list of upcoming bills. */
@Composable
internal fun ScheduleActionsPane(
    state: AppState,
    mobileSession: TandemMobileSession,
    selection: ScheduleSelectionState,
    dialog: ScheduleDialogState,
    selectedSchedule: LiveScheduleSummary,
    scope: CoroutineScope,
    editForm: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TandemSpacing.screen, vertical = TandemSpacing.large),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        ScheduleSheetHeader(state, mobileSession, selection, dialog, selectedSchedule)
        ScheduleActionsBody(state, mobileSession, selection, dialog, selectedSchedule, scope, editForm)
    }
}

@Composable
private fun ScheduleActionsBody(
    state: AppState,
    mobileSession: TandemMobileSession,
    selection: ScheduleSelectionState,
    dialog: ScheduleDialogState,
    selectedSchedule: LiveScheduleSummary,
    scope: CoroutineScope,
    editForm: @Composable () -> Unit,
) {
    val pendingScheduleActions = mobileSession.schedules.pendingScheduleActionsFor(state.selectedBudgetId, selectedSchedule.id)
    val pendingActionBlocksChoice =
        pendingScheduleActions.size > 1 ||
            pendingScheduleActions.any { pendingAction ->
                !mobileSession.schedules.scheduleActionCanBeReplaced(pendingAction, selectedSchedule.nextDate)
            }
    when {
        dialog.dialogMode == "delete" -> {
            Text(
                "Confirm deleting this schedule in the alert.",
                color = tandemPalette.muted,
                fontSize = TandemTypography.body,
            )
        }

        dialog.dialogMode == "edit" -> {
            editForm()
        }

        else -> {
            ScheduleActionMenu(
                state,
                mobileSession,
                selection,
                dialog,
                selectedSchedule,
                pendingScheduleActions,
                pendingActionBlocksChoice,
                scope,
            )
        }
    }
}

@Composable
private fun ScheduleSheetHeader(
    state: AppState,
    mobileSession: TandemMobileSession,
    selection: ScheduleSelectionState,
    dialog: ScheduleDialogState,
    selectedSchedule: LiveScheduleSummary,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Icon(
            if (dialog.dialogMode == "edit") Lucide.Pencil else Lucide.CalendarDays,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tandemPalette.accent,
        )
        Text(
            when {
                dialog.dialogMode == "edit" -> "Edit schedule"
                else -> selectedSchedule.name
            },
            modifier = Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.sectionTitle,
            fontFamily = tandemAlloFamily(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TandemIconButton(
            Lucide.X,
            "Close",
            sizeDp = 40,
            iconSizeDp = 18,
            containerColor = tandemPalette.raised,
            enabled = !mobileSession.isPerformingBudgetAction,
        ) {
            selection.showScheduleEditor = false
            state.pendingScheduleToOpenId = null
            dialog.dialogMode = "menu"
        }
    }
}

@Composable
private fun ScheduleActionMenu(
    state: AppState,
    mobileSession: TandemMobileSession,
    selection: ScheduleSelectionState,
    dialog: ScheduleDialogState,
    selectedSchedule: LiveScheduleSummary,
    pendingScheduleActions: List<OutboxOperationEntity>,
    pendingActionBlocksChoice: Boolean,
    scope: CoroutineScope,
) {
    budgetActionFeedbackMessage(mobileSession)
    pendingScheduleActions.forEach { pending ->
        InlineMessage(
            text = mobileSession.schedules.scheduleActionStatusMessage(pending),
            kind = InlineMessageKind.WARNING,
            actionLabel = mobileSession.schedules.scheduleActionStatusActionLabel(pending),
            onAction =
                if (mobileSession.schedules.scheduleActionStatusActionLabel(pending) != null) {
                    { mobileSession.schedules.performScheduleActionStatusAction(pending) }
                } else {
                    null
                },
        )
    }
    val runScheduleAction: (String) -> Unit = { action ->
        scope.launch {
            val done =
                mobileSession.schedules.manageBudgetSchedule(
                    state.selectedBudgetId,
                    selectedSchedule.id,
                    action,
                    null,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                    expectedNextDate = selectedSchedule.nextDate,
                )
            if (done) selection.showScheduleEditor = false
        }
    }
    TandemActionButton(
        "Post now",
        TandemActionButtonTone.PRIMARY,
        modifier = Modifier.fillMaxWidth(),
        icon = Lucide.CalendarDays,
        enabled = !mobileSession.isPerformingBudgetAction && !pendingActionBlocksChoice,
        loading = mobileSession.isPerformingBudgetAction,
    ) { runScheduleAction("post") }
    if (selectedSchedule.canSkip) {
        TandemActionButton(
            "Skip next date",
            TandemActionButtonTone.NEUTRAL,
            modifier = Modifier.fillMaxWidth(),
            icon = Lucide.CalendarDays,
            enabled = !mobileSession.isPerformingBudgetAction && !pendingActionBlocksChoice,
            loading = mobileSession.isPerformingBudgetAction,
        ) { runScheduleAction("skip") }
    }
    TandemActionButton(
        "Edit schedule",
        TandemActionButtonTone.NEUTRAL,
        modifier = Modifier.fillMaxWidth(),
        icon = Lucide.Pencil,
        enabled = !mobileSession.isPerformingBudgetAction,
    ) {
        mobileSession.feedback.clearBudgetActionMessage()
        dialog.dialogMode = "edit"
    }
    TandemActionButton(
        "Delete",
        TandemActionButtonTone.DESTRUCTIVE,
        modifier = Modifier.fillMaxWidth(),
        icon = Lucide.Trash2,
        enabled = !mobileSession.isPerformingBudgetAction,
    ) {
        mobileSession.feedback.clearBudgetActionMessage()
        dialog.dialogMode = "delete"
        dialog.showDeleteScheduleConfirmation = true
    }
}
