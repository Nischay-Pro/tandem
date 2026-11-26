@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.markTransactionsReconciled
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.TransactionItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Visibility and the cleared ticks the user changed in the reconcile dialog. */
internal class AccountReconcileState(
    showDialog: MutableState<Boolean>,
    clearedChoices: MutableState<Map<String, Boolean>>,
    statementBalance: MutableState<String>,
) {
    var showDialog by showDialog
    var clearedChoices by clearedChoices
    var statementBalance by statementBalance

    fun open(mobileSession: TandemMobileSession) {
        clearedChoices = emptyMap()
        statementBalance = ""
        mobileSession.clearTransactionWriteMessage()
        showDialog = true
    }
}

@Composable
internal fun rememberAccountReconcileState(
    state: AppState,
    liveAccount: LiveAccountSummary?,
): AccountReconcileState {
    val showDialog = rememberSaveable(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(false) }
    val clearedChoices = remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(emptyMap<String, Boolean>()) }
    val statementBalance = rememberSaveable(state.selectedBudgetId, liveAccount?.id) { mutableStateOf("") }
    return AccountReconcileState(showDialog, clearedChoices, statementBalance)
}

/** Reconcile dialog for the account, marking the selected cleared transactions as reconciled. */
@Composable
internal fun AccountReconcileFlow(
    reconcile: AccountReconcileState,
    activity: AccountActivityState,
    state: AppState,
    mobileSession: TandemMobileSession,
    liveAccount: LiveAccountSummary,
    reconciliationCandidates: List<TransactionItem>,
    scope: CoroutineScope,
) {
    val didReconcile: (() -> Unit) -> Unit = { closeAfterSuccess ->
        scope.launch {
            if (
                mobileSession.budgetEdits.markTransactionsReconciled(
                    state.selectedBudgetId,
                    liveAccount.id,
                    reconciliationCandidates.mapNotNull { it.recordId }.toSet(),
                    reconcile.clearedChoices,
                )
            ) {
                closeAfterSuccess()
            }
        }
    }
    AccountReconcileDialog(
        state = state,
        accountName = liveAccount.name,
        clearedBalanceMinor = activity.clearedBalanceMinor,
        transactions = reconciliationCandidates,
        hasMoreTransactions = activity.hasMore,
        loadingMoreTransactions = activity.loadingOlder,
        onLoadMoreTransactions = {
            activity.loadingOlder = true
            activity.offset += ACCOUNT_RECONCILIATION_PAGE_SIZE
        },
        selectedIds = reconcileTickedIds(reconciliationCandidates, reconcile.clearedChoices),
        statementBalance = reconcile.statementBalance,
        busy = mobileSession.isSavingTransaction,
        message = mobileSession.transactionWriteMessage,
        onStatementBalanceChange = { reconcile.statementBalance = it },
        onSelectedChange = { id, selected ->
            reconcile.clearedChoices += id to selected
        },
        onDismiss = { if (!mobileSession.isSavingTransaction) reconcile.showDialog = false },
        onConfirm = didReconcile,
    )
}
