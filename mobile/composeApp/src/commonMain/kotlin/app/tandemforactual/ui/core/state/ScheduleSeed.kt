package app.tandemforactual.ui.core.state

import app.tandemforactual.ui.core.format.parseIsoDate

/** A transaction to turn into a schedule: the schedules screen opens a new schedule filled from it and links it. */
internal data class ScheduleSeed(
    val transactionId: String,
    val accountId: String,
    val payeeId: String,
    val categoryId: String,
    val amountMinor: Long,
    val date: String,
    val name: String,
)

/** The selected transaction as a [ScheduleSeed], or null when it can't become a schedule. */
internal fun AppState.selectedTransactionScheduleSeed(): ScheduleSeed? {
    val amount = selectedTransactionAmountMinor ?: return null
    if (selectedTransactionRecordId.isBlank() || selectedTransactionAccountId.isBlank()) return null
    parseIsoDate(selectedTransactionDate) ?: return null
    return ScheduleSeed(
        transactionId = selectedTransactionRecordId,
        accountId = selectedTransactionAccountId,
        payeeId = selectedTransactionPayeeId,
        categoryId = if (selectedTransactionIsTransfer) "" else selectedTransactionCategoryId,
        amountMinor = amount,
        date = selectedTransactionDate,
        name = selectedTransactionPayeeName.ifBlank { transactionPayee },
    )
}
