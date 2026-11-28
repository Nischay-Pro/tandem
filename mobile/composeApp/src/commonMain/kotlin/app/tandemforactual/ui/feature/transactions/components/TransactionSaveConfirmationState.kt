package app.tandemforactual.ui.feature.transactions.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.TransactionDateValidationStatus
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.format.validateTransactionDate

/** Which save confirmations (budget amount format, future date) are showing, kept across recreation. */
internal class TransactionSaveConfirmationState(
    confirmAmount: Boolean = false,
    confirmFutureDate: Boolean = false,
    futureDateConfirmationTarget: String = "",
    confirmedFutureDateForAmount: String = "",
) {
    var confirmAmount by mutableStateOf(confirmAmount)
    var confirmFutureDate by mutableStateOf(confirmFutureDate)
    var futureDateConfirmationTarget by mutableStateOf(futureDateConfirmationTarget)
    var confirmedFutureDateForAmount by mutableStateOf(confirmedFutureDateForAmount)

    fun continueSave(
        confirmedFutureDate: String?,
        budgetAmountPreview: String?,
        onSave: (String?) -> Unit,
    ) {
        confirmedFutureDateForAmount = confirmedFutureDate.orEmpty()
        if (budgetAmountPreview != null) {
            confirmAmount = true
        } else {
            onSave(confirmedFutureDate)
        }
    }

    fun requestSave(
        transactionDate: String,
        budgetAmountPreview: String?,
        onSave: (String?) -> Unit,
    ) {
        val dateValidation = validateTransactionDate(transactionDate, platformTodayIsoDate())
        when {
            dateValidation.status == TransactionDateValidationStatus.CONFIRMATION_REQUIRED -> {
                futureDateConfirmationTarget = dateValidation.date.orEmpty()
                confirmFutureDate = true
            }

            dateValidation.status != TransactionDateValidationStatus.VALID -> {
                onSave(null)
            }

            else -> {
                continueSave(null, budgetAmountPreview, onSave)
            }
        }
    }

    companion object {
        val Saver: Saver<TransactionSaveConfirmationState, Any> =
            listSaver(
                save = { listOf(it.confirmAmount, it.confirmFutureDate, it.futureDateConfirmationTarget, it.confirmedFutureDateForAmount) },
                restore = {
                    TransactionSaveConfirmationState(it[0] as Boolean, it[1] as Boolean, it[2] as String, it[3] as String)
                },
            )
    }
}

@Composable
internal fun rememberTransactionSaveConfirmationState(vararg keys: Any?): TransactionSaveConfirmationState =
    rememberSaveable(*keys, saver = TransactionSaveConfirmationState.Saver) { TransactionSaveConfirmationState() }
