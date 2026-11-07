package app.tandemforactual.ui

internal fun reconciliationClearedBalanceMinor(
    clearedBalanceMinor: Long,
    editableTransactions: List<TransactionItem>,
    selectedTransactionIds: Set<String>,
): Long {
    val editableClearedTotal = editableTransactions.filter { it.cleared == true }.sumOf(TransactionItem::amountMinorUnits)
    val selectedTotal = editableTransactions.filter { it.recordId in selectedTransactionIds }.sumOf(TransactionItem::amountMinorUnits)
    return clearedBalanceMinor - editableClearedTotal + selectedTotal
}
