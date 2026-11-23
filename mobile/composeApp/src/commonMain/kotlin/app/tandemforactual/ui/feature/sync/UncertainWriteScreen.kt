@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.templates.PageColumn

@Composable
internal fun UncertainWriteScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onCheckStatus: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
    liveTransactions: List<TransactionItem>,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val pendingOperation =
            if (state.selectedConflictOperationId.isNotBlank()) {
                mobileSession.pendingWrites.firstOrNull { it.operationId == state.selectedConflictOperationId }
            } else {
                mobileSession.pendingWrites.firstOrNull { it.status == "uncertain" }
            }
        val checkedOperation =
            mobileSession.conflicts.checkedWriteOperation?.takeIf {
                it.operationId == state.selectedConflictOperationId ||
                    (state.selectedConflictOperationId.isBlank() && it.operationId == mobileSession.conflicts.checkedWriteOperationId)
            }
        val operation = checkedOperation ?: pendingOperation
        val transaction = operation?.let { write -> liveTransactions.firstOrNull { it.recordId == write.recordId } }
        val isChecked = operation != null && mobileSession.conflicts.checkedWriteOperationId == operation.operationId
        val isNotFound =
            operation != null &&
                isChecked &&
                operation.status == "uncertain" &&
                isUncertainWriteOperationNotFound(operation.lastErrorCode)
        val checkError =
            operation?.let { checked ->
                mobileSession.conflicts.uncertainWriteCheckError.takeIf {
                    mobileSession.conflicts.uncertainWriteCheckErrorOperationId == checked.operationId
                }
            }
        UncertainWriteDetailsScreen(
            state = state,
            operation = operation,
            transaction = transaction,
            isChecked = isChecked,
            isNotFound = isNotFound,
            checkError = checkError,
            isSyncing = mobileSession.isSyncing,
            onCheckStatus = { operation?.let { onCheckStatus(it.operationId) } },
            onRetry = { operation?.let { onRetry(it.operationId) } },
            onDiscard = { operation?.let { onDiscard(it.operationId) } },
            onReviewConflict = {
                operation?.let {
                    mobileSession.clearTransactionWriteMessage()
                    state.selectedConflictOperationId = it.operationId
                    navigation.open(AppRoute.CONFLICT_REVIEW)
                }
            },
            onReviewRejected = {
                operation?.let {
                    state.selectedConflictOperationId = it.operationId
                    navigation.open(AppRoute.UNCERTAIN_WRITE)
                }
            },
            onDone = navigation.back,
        )
        return
    }
    PageColumn {
        EmptyStateCard(
            "Choose a budget",
            "Open an Actual budget to check pending changes.",
            actionLabel = "Choose budget",
            onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) },
        )
    }
}
