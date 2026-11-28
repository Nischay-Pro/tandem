package app.tandemforactual.ui.app.shell

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import app.tandemforactual.ui.app.recordPayeeLocation
import app.tandemforactual.ui.core.platform.CoarseLocator
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.requestBudgetOrderFlush
import app.tandemforactual.ui.core.session.transactions.SplitEdit
import app.tandemforactual.ui.core.session.transactions.createTransaction
import app.tandemforactual.ui.core.session.transactions.deleteTransactionsWithUndo
import app.tandemforactual.ui.core.session.transactions.updateSplit
import app.tandemforactual.ui.core.session.transactions.updateTransaction
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.recordName
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
    private val locator: CoarseLocator,
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

    /**
     * The form's payee. One added from the form has a device id until Actual creates it, and the budget's records then
     * replace it, so a payee that's no longer listed is found again by the name the form shows.
     */
    private fun formPayeeId(): String? {
        val id = state.newTransactionPayeeId.takeIf(String::isNotBlank) ?: return null
        if (mobileSession.payeeRecords.any { it.recordId == id }) return id
        return mobileSession.payeeRecords
            .firstOrNull { it.recordName()?.equals(state.transactionPayee.trim(), ignoreCase = true) == true }
            ?.recordId ?: id
    }

    fun saveTransaction(
        confirmedFutureDate: String?,
        onComplete: (Boolean) -> Unit,
    ) {
        val transactionId = state.selectedTransactionRecordId
        val originalAmount = state.selectedTransactionAmountMinor
        if (state.transactionSplit != null || state.selectedTransactionSplit != null) return saveSplit(onComplete)
        if (!state.readOnlyMode && transactionId.isNotBlank() && originalAmount != null && !mobileSession.isSavingTransaction) {
            val before = state.savedCategoryEntries(mobileSession)
            val after = state.formCategoryEntries(mobileSession)
            coroutineScope.launch {
                val notice = CategorySaveNotice.prepare(state.selectedBudgetId, mobileSession, before, after)
                val saved =
                    mobileSession.transactions.updateTransaction(
                        budgetId = state.selectedBudgetId,
                        transactionId = transactionId,
                        amountText = state.transactionAmount,
                        notes = state.transactionNotes,
                        accountId = state.newTransactionAccountId,
                        payeeId = formPayeeId(),
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
                    notice?.show(state, mobileSession)
                }
                onComplete(saved)
            }
        } else {
            onComplete(false)
        }
    }

    /**
     * Saves a split's lines with its amount, payee and notes in one edit, or turns it back into one transaction when the
     * split was removed. A split's account and date are changed in Actual.
     */
    private fun saveSplit(onComplete: (Boolean) -> Unit) {
        val originalAmount = state.selectedTransactionAmountMinor
        val amount = state.parseAmountMinorUnits(state.transactionAmount)
        if (state.readOnlyMode || originalAmount == null || amount == null || mobileSession.isSavingTransaction) return onComplete(false)
        if (state.transactionType == "Transfer") {
            mobileSession.reportTransactionWriteMessage("A split can’t be a transfer. Remove the split first.")
            return onComplete(false)
        }
        if (state.newTransactionAccountId != state.selectedTransactionAccountId || state.transactionDate != state.selectedTransactionDate) {
            mobileSession.reportTransactionWriteMessage("Change a split transaction’s account or date in Actual.")
            return onComplete(false)
        }
        val income = state.transactionType == "Income"
        val edit =
            SplitEdit(
                parentId = state.selectedTransactionRecordId,
                lines = state.transactionSplit?.editedLines(state::parseAmountMinorUnits, income).orEmpty(),
                amountMinor = if (income) amount else -amount,
                notes = state.transactionNotes,
                payeeId = formPayeeId(),
                unsplitCategoryId = state.newTransactionCategoryId.takeIf(String::isNotBlank),
            )
        val before = state.savedCategoryEntries(mobileSession)
        val after = state.formCategoryEntries(mobileSession)
        coroutineScope.launch {
            val notice = CategorySaveNotice.prepare(state.selectedBudgetId, mobileSession, before, after)
            val saved = mobileSession.transactions.updateSplit(state.selectedBudgetId, edit)
            if (saved) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                rememberSavedTransaction(originalAmount)
                state.selectedTransactionSplit = state.transactionSplit
                notice?.show(state, mobileSession)
            }
            onComplete(saved)
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
            val after = state.formCategoryEntries(mobileSession)
            coroutineScope.launch {
                val notice = CategorySaveNotice.prepare(state.selectedBudgetId, mobileSession, emptyList(), after)
                val created =
                    mobileSession.transactions.createTransaction(
                        budgetId = state.selectedBudgetId,
                        accountId = state.newTransactionAccountId,
                        payeeId = formPayeeId(),
                        categoryId = state.newTransactionCategoryId.takeIf { it.isNotBlank() && state.transactionType != "Transfer" },
                        amountText = signedAmountText,
                        dateText = state.transactionDate,
                        notes = state.transactionNotes,
                        encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
                        displaySettings = state.budgetDisplaySettings,
                        isTransfer = state.transactionType == "Transfer",
                        confirmedFutureDate = confirmedFutureDate,
                        splitLines =
                            state.transactionSplit
                                ?.takeIf { state.transactionType != "Transfer" }
                                ?.signedLines(state::parseAmountMinorUnits, income = state.transactionType == "Income"),
                    )
                if (created) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    val paidPayee = formPayeeId().orEmpty().takeIf { state.transactionType != "Transfer" }
                    if (paidPayee != null) launch { recordPayeeLocation(state, mobileSession, locator, paidPayee) }
                    state.lastTransactionAccountId = state.newTransactionAccountId
                    restoreTransactionFormBaseline(state, AppRoute.NEW_TRANSACTION)
                    navigation.back()
                    notice?.show(state, mobileSession)
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
