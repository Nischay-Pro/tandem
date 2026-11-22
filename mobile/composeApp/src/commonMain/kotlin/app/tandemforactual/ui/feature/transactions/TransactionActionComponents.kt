@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.state.AppState

/** A past transaction copied into a new one dated today. */
internal data class AddAgainDraft(
    val accountId: String,
    val payeeId: String,
    val payeeName: String,
    val categoryId: String,
    val categoryName: String,
    val amountMinor: Long,
    val notes: String,
)

internal fun AppState.addAgainDraft(): AddAgainDraft? {
    val amount = selectedTransactionAmountMinor ?: return null
    if (selectedTransactionIsTransfer || selectedTransactionRecordId.isBlank()) return null
    return AddAgainDraft(
        accountId = selectedTransactionAccountId,
        payeeId = selectedTransactionPayeeId,
        payeeName = selectedTransactionPayeeName.ifBlank { transactionPayee },
        categoryId = selectedTransactionCategoryId,
        categoryName = selectedTransactionCategoryName.ifBlank { transactionCategory },
        amountMinor = amount,
        notes = selectedTransactionNotes,
    )
}

internal fun AppState.applyAddAgain(draft: AddAgainDraft) {
    transactionAmount = formatAmount(kotlin.math.abs(draft.amountMinor))
    transactionType = if (draft.amountMinor < 0) "Spending" else "Income"
    newTransactionPayeeId = draft.payeeId
    transactionPayee = draft.payeeName
    newTransactionCategoryId = draft.categoryId
    transactionCategory = draft.categoryName
    transactionNotes = draft.notes
}
