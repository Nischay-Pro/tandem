package app.tandemforactual.ui.app.shell

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.requestBudgetOrderFlush
import app.tandemforactual.ui.core.session.transactions.createTransaction
import app.tandemforactual.ui.core.session.transactions.deleteTransactionsWithUndo
import app.tandemforactual.ui.core.session.transactions.updateTransaction
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.restoreTransactionFormBaseline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Sync, transaction and pending-write actions the budget screens call. */
internal class BudgetActions(
    private val state: AppState,
    private val mobileSession: TandemMobileSession,
    private val navigation: AppNavigation,
    private val coroutineScope: CoroutineScope,
    private val haptics: HapticFeedback,
) {
    private val encryptionPassword get() = state.encryptedPassword.takeIf { it.isNotBlank() }

    fun syncNow() {
        // Send reorders held on the device instead of waiting for the next periodic flush.
        mobileSession.budgetQueue.requestBudgetOrderFlush()
        if (!mobileSession.isSyncing && state.selectedBudgetId.isNotBlank()) {
            coroutineScope.launch {
                mobileSession.synchronize(
                    state.selectedBudgetId,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            }
        }
    }

    fun saveTransaction(
        confirmedFutureDate: String?,
        onComplete: (Boolean) -> Unit,
    ) {
        val transactionId = state.selectedTransactionRecordId
        val originalAmount = state.selectedTransactionAmountMinor
        if (!state.readOnlyMode && transactionId.isNotBlank() && originalAmount != null && !mobileSession.isSavingTransaction) {
            coroutineScope.launch {
                val saved =
                    mobileSession.transactions.updateTransaction(
                        budgetId = state.selectedBudgetId,
                        transactionId = transactionId,
                        amountText = state.transactionAmount,
                        notes = state.transactionNotes,
                        accountId = state.newTransactionAccountId,
                        payeeId = state.newTransactionPayeeId.takeIf(String::isNotBlank),
                        categoryId = state.newTransactionCategoryId.takeIf(String::isNotBlank),
                        date = state.transactionDate,
                        originalAmountMinor = originalAmount,
                        transactionType = state.transactionType,
                        displaySettings = state.budgetDisplaySettings,
                        updateDetails = true,
                        confirmedFutureDate = confirmedFutureDate,
                    )
                if (saved) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    rememberSavedTransaction(originalAmount)
                }
                onComplete(saved)
            }
        } else {
            onComplete(false)
        }
    }

    private fun rememberSavedTransaction(originalAmount: Long) {
        val amount = state.parseAmountMinorUnits(state.transactionAmount) ?: kotlin.math.abs(originalAmount)
        state.selectedTransactionAmountMinor =
            when {
                state.transactionType == "Income" -> amount
                state.transactionType == "Transfer" && originalAmount >= 0L -> amount
                else -> -amount
            }
        state.selectedTransactionIsTransfer = state.transactionType == "Transfer"
        state.selectedTransactionNotes = state.transactionNotes
        state.selectedTransactionAccountId = state.newTransactionAccountId
        state.selectedTransactionPayeeId = state.newTransactionPayeeId
        state.selectedTransactionCategoryId = state.newTransactionCategoryId
        state.selectedTransactionDate = state.transactionDate
        state.selectedTransactionAccountName = state.transactionAccount
        state.selectedTransactionPayeeName = state.transactionPayee
        state.selectedTransactionCategoryName = state.transactionCategory
    }

    fun deleteTransaction(onComplete: (Boolean) -> Unit) {
        val transactionId = state.selectedTransactionRecordId
        if (!state.readOnlyMode && transactionId.isNotBlank() && !mobileSession.isSavingTransaction) {
            coroutineScope.launch {
                onComplete(mobileSession.transactions.deleteTransactionsWithUndo(state.selectedBudgetId, setOf(transactionId)))
            }
        } else {
            onComplete(false)
        }
    }

    fun createTransaction(confirmedFutureDate: String?) {
        if (!state.readOnlyMode && !mobileSession.isSavingTransaction) {
            val enteredAmount = state.parseAmountMinorUnits(state.transactionAmount)
            val signedAmountText =
                enteredAmount?.let { amount ->
                    val unsignedAmount = kotlin.math.abs(amount)
                    state.formatAmount(if (state.transactionType == "Income") unsignedAmount else -unsignedAmount)
                } ?: state.transactionAmount
            coroutineScope.launch {
                val created =
                    mobileSession.transactions.createTransaction(
                        budgetId = state.selectedBudgetId,
                        accountId = state.newTransactionAccountId,
                        payeeId = state.newTransactionPayeeId.takeIf { it.isNotBlank() },
                        categoryId = state.newTransactionCategoryId.takeIf { it.isNotBlank() && state.transactionType != "Transfer" },
                        amountText = signedAmountText,
                        dateText = state.transactionDate,
                        notes = state.transactionNotes,
                        encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
                        displaySettings = state.budgetDisplaySettings,
                        isTransfer = state.transactionType == "Transfer",
                        confirmedFutureDate = confirmedFutureDate,
                    )
                if (created) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    state.lastTransactionAccountId = state.newTransactionAccountId
                    restoreTransactionFormBaseline(state, AppRoute.NEW_TRANSACTION)
                    navigation.back()
                }
            }
        }
    }

    fun resolveConflict(
        operationId: String,
        keepDeviceVersion: Boolean,
        reviewedRevision: String,
    ) {
        if (!mobileSession.isSavingTransaction) {
            coroutineScope.launch {
                mobileSession.conflicts.resolveConflict(
                    budgetId = state.selectedBudgetId,
                    operationId = operationId,
                    keepDeviceVersion = keepDeviceVersion,
                    encryptionPassword = encryptionPassword,
                    reviewedRevision = reviewedRevision,
                )
            }
        }
    }

    fun discardWrite(operationId: String) {
        coroutineScope.launch {
            mobileSession.conflicts.discardWrite(state.selectedBudgetId, operationId, encryptionPassword)
        }
    }

    fun retryUncertainWrite(operationId: String) {
        coroutineScope.launch {
            mobileSession.conflicts.retryUncertainWrite(state.selectedBudgetId, operationId, encryptionPassword)
        }
    }

    fun checkUncertainWriteStatus(operationId: String) {
        if (!mobileSession.isSyncing) {
            coroutineScope.launch {
                mobileSession.conflicts.checkUncertainWriteStatus(state.selectedBudgetId, operationId, encryptionPassword)
            }
        }
    }
}
