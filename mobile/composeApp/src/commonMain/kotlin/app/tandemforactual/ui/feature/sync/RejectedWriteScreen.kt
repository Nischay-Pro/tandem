@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.formatSignedCurrency
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.formatSignedCurrency
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.templates.Heading
import app.tandemforactual.ui.design.templates.PageColumn

@Composable
internal fun RejectedWriteScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onDiscard: (String) -> Unit,
    liveTransactions: List<TransactionItem>,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val operation =
            mobileSession.pendingWrites.firstOrNull {
                it.operationId == state.selectedConflictOperationId && it.status == "rejected"
            } ?: mobileSession.pendingWrites.firstOrNull { it.status == "rejected" }
        val transaction = operation?.let { write -> liveTransactions.firstOrNull { it.recordId == write.recordId } }
        val operationPayee = transaction?.payee ?: "this transaction"
        val operationAmount = operation?.optimisticAmountMinor ?: transaction?.amountMinorUnits
        val operationAmountText = operationAmount?.let(state::formatSignedCurrency) ?: "Unavailable"
        var confirmDismiss by rememberSaveable(operation?.operationId) { mutableStateOf(false) }
        PageColumn {
            if (operation == null) {
                Heading("Rejected")
                BodyText("No rejected edits need review.")
            } else {
                ReviewOutcomeCard(
                    title = "Rejected",
                    explanation = "Actual kept the previous value.",
                    isRejected = true,
                )
                SectionCard(transaction?.payee ?: "Deleted transaction", transaction?.dateLabel ?: operationAmountText) {
                    ListRow(
                        "Your edit",
                        trailing = operationAmountText,
                    )
                }
                BudgetMutationReasonCard(
                    title = "WHY IT WAS REJECTED",
                    explanation = rejectedTransactionReferenceMessage(operation.lastErrorCode),
                )
                PrimaryButton("Remove edit", enabled = !state.readOnlyMode) { confirmDismiss = true }
            }
        }
        if (confirmDismiss && operation != null) {
            DestructiveConfirmationDialog(
                title = "Remove this edit?",
                message =
                    "Remove the rejected edit for $operationPayee ($operationAmountText) from this device? Actual kept the previous value.",
                confirmLabel = "Remove edit",
                dismissLabel = "Keep edit",
                enabled = !state.readOnlyMode,
                onDismissRequest = { confirmDismiss = false },
                onConfirm = {
                    confirmDismiss = false
                    onDiscard(operation.operationId)
                },
            )
        }
        return
    }
    PageColumn {
        EmptyStateCard(
            "Choose a budget",
            "Open an Actual budget to review rejected changes.",
            actionLabel = "Choose budget",
            onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) },
        )
    }
}
