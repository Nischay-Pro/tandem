@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.rules
import androidx.compose.runtime.Composable
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.manageBudgetPayee
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Confirmation dialog for merging the selected payee into the chosen target. */
@Composable
internal fun MergePayeesDialog(
    payee: ManagePayeeState,
    selectedPayee: Pair<String, String>,
    mergeTarget: Pair<String, String>?,
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
) {
    DestructiveConfirmationDialog(
        title = "Merge payees?",
        message =
            mobileSession.budgetActionMessage
                ?: "Move transactions from ${selectedPayee.second} to ${mergeTarget?.second ?: "the selected payee"}?",
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
        confirmLabel = if (mobileSession.isPerformingBudgetAction) "Merging…" else "Merge",
        dismissLabel = "Choose another",
        enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && mergeTarget != null,
        onDismissRequest = {
            if (!mobileSession.isPerformingBudgetAction) payee.mode = "merge"
        },
        onConfirm = {
            scope.launch {
                val merged =
                    mobileSession.budgetEdits.manageBudgetPayee(
                        state.selectedBudgetId,
                        selectedPayee.first,
                        "merge",
                        null,
                        mergeTarget?.first,
                        state.encryptedPassword.takeIf { it.isNotBlank() },
                    )
                if (merged) payee.sheetVisible = false else payee.mode = "merge"
            }
        },
    )
}
