@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions.components

import androidx.compose.runtime.Composable
import app.tandemforactual.ui.design.sheets.AmountConfirmationDialog
import app.tandemforactual.ui.design.sheets.FutureTransactionDateConfirmationDialog
import app.tandemforactual.ui.feature.transactions.displayTransactionDate

/** Asks to confirm a budget-formatted amount or a future date before the transaction is saved. */
@Composable
internal fun TransactionSaveConfirmationDialogs(
    confirmations: TransactionSaveConfirmationState,
    budgetAmountPreview: String?,
    onSave: (String?) -> Unit,
) {
    AmountConfirmationDialog(
        visible = confirmations.confirmAmount,
        formattedAmount = budgetAmountPreview.orEmpty(),
        onDismissRequest = {
            confirmations.confirmAmount = false
            confirmations.confirmedFutureDateForAmount = ""
        },
        onConfirm = {
            confirmations.confirmAmount = false
            val confirmedDate = confirmations.confirmedFutureDateForAmount.takeIf(String::isNotBlank)
            confirmations.confirmedFutureDateForAmount = ""
            onSave(confirmedDate)
        },
    )
    FutureTransactionDateConfirmationDialog(
        visible = confirmations.confirmFutureDate,
        formattedDate = displayTransactionDate(confirmations.futureDateConfirmationTarget),
        onDismissRequest = {
            confirmations.confirmFutureDate = false
            confirmations.futureDateConfirmationTarget = ""
        },
        onConfirm = {
            val confirmedDate = confirmations.futureDateConfirmationTarget
            confirmations.confirmFutureDate = false
            confirmations.futureDateConfirmationTarget = ""
            confirmations.continueSave(confirmedDate, budgetAmountPreview, onSave)
        },
    )
}
