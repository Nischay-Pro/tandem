@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.schedules.components

import androidx.compose.runtime.Composable
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog

/** Confirms deleting the selected schedule, showing any failure from the last attempt. */
@Composable
internal fun ScheduleDeleteConfirmationDialog(
    mobileSession: TandemMobileSession,
    selectedSchedule: LiveScheduleSummary,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    DestructiveConfirmationDialog(
        title = "Delete schedule?",
        message = mobileSession.budgetActionMessage ?: "Remove ${selectedSchedule.name} from upcoming bills?",
        messageKind = mobileSession.feedback.budgetActionMessageKind.takeIf { mobileSession.budgetActionMessage != null },
        messageActionLabel = mobileSession.feedback.budgetActionMessageActionLabel,
        onMessageAction =
            if (mobileSession.feedback.budgetActionMessageActionLabel ==
                null
            ) {
                null
            } else {
                mobileSession.feedback::retryBudgetAction
            },
        confirmLabel = if (mobileSession.isPerformingBudgetAction) "Deleting…" else "Delete",
        dismissLabel = "Keep schedule",
        enabled = !mobileSession.isPerformingBudgetAction,
        onDismissRequest = onDismissRequest,
        onConfirm = onConfirm,
    )
}
