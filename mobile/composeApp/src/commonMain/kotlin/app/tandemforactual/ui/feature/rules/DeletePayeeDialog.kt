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

/** Confirmation dialog for deleting the selected payee. */
@Composable
internal fun DeletePayeeDialog(
    payee: ManagePayeeState,
    selectedPayee: Pair<String, String>,
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
) {
    DestructiveConfirmationDialog(
        title = "Delete ${selectedPayee.second}?",
        message = mobileSession.budgetActionMessage ?: "Remove this payee from the budget?",
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
        dismissLabel = "Keep payee",
        enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
        onDismissRequest = {
            if (!mobileSession.isPerformingBudgetAction) payee.mode = "menu"
        },
        onConfirm = {
            scope.launch {
                val deleted =
                    mobileSession.budgetEdits.manageBudgetPayee(
                        state.selectedBudgetId,
                        selectedPayee.first,
                        "delete",
                        null,
                        null,
                        state.encryptedPassword.takeIf { it.isNotBlank() },
                    )
                if (deleted) payee.sheetVisible = false else payee.mode = "menu"
            }
        },
    )
}
