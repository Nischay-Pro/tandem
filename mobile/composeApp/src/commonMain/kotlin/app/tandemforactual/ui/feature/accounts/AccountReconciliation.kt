package app.tandemforactual.ui.feature.accounts

import app.tandemforactual.ui.core.state.TransactionItem

internal fun reconcileTickedIds(
    transactions: List<TransactionItem>,
    clearedChoices: Map<String, Boolean>,
): Set<String> =
    transactions
        .mapNotNull { transaction ->
            transaction.recordId?.takeIf { clearedChoices[it] ?: (transaction.cleared == true) }
        }.toSet()

internal fun reconciliationClearedBalanceMinor(
    clearedBalanceMinor: Long,
    editableTransactions: List<TransactionItem>,
    selectedTransactionIds: Set<String>,
): Long {
    val editableClearedTotal = editableTransactions.filter { it.cleared == true }.sumOf(TransactionItem::amountMinorUnits)
    val selectedTotal = editableTransactions.filter { it.recordId in selectedTransactionIds }.sumOf(TransactionItem::amountMinorUnits)
    return clearedBalanceMinor - editableClearedTotal + selectedTotal
}

internal fun canFinishReconciliation(
    statementBalanceMinor: Long?,
    differenceMinor: Long?,
    busy: Boolean,
): Boolean = !busy && statementBalanceMinor != null && differenceMinor == 0L
