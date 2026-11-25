package app.tandemforactual.ui.core.navigation

import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem

internal fun openTransaction(
    state: AppState,
    navigation: AppNavigation,
    transaction: TransactionItem,
    isLive: Boolean,
    navigate: Boolean = true,
) {
    state.transactionPayee = transaction.payee
    state.transactionCategory = transaction.category
    state.transactionAccount = transaction.account
    state.transactionDate = transaction.dateIso.ifBlank { transaction.dateLabel }
    state.newTransactionAccountId = transaction.accountId.orEmpty()
    state.newTransactionPayeeId = transaction.payeeId.orEmpty()
    state.newTransactionCategoryId = transaction.categoryId.orEmpty()
    state.selectedTransactionAccountId = transaction.accountId.orEmpty()
    state.selectedTransactionPayeeId = transaction.payeeId.orEmpty()
    state.selectedTransactionCategoryId = transaction.categoryId.orEmpty()
    state.selectedTransactionDate = state.transactionDate
    state.selectedTransactionAccountName = transaction.account
    state.selectedTransactionPayeeName = transaction.payee
    state.selectedTransactionCategoryName = transaction.category
    state.transactionNotes = transaction.notes
    state.transactionAmount = formatAmount(kotlin.math.abs(transaction.amountMinorUnits), state.budgetDisplaySettings)
    state.transactionType =
        when {
            transaction.isTransfer -> "Transfer"
            transaction.amountMinorUnits < 0 -> "Spending"
            else -> "Income"
        }
    state.selectedTransactionAmountMinor = transaction.amountMinorUnits
    state.selectedTransactionNotes = transaction.notes
    state.selectedTransactionRecordId = transaction.recordId.orEmpty()
    state.selectedTransactionIsLive = isLive
    state.selectedTransactionIsTransfer = transaction.isTransfer
    state.transactionSplit = null
    state.selectedTransactionSplit = null
    if (navigate) navigation.open(AppRoute.TRANSACTION_DETAIL)
}
