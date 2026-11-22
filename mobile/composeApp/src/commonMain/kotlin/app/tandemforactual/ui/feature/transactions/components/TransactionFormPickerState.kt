package app.tandemforactual.ui.feature.transactions.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** Whether the transaction form's account or date picker is open, kept across recreation. */
internal class TransactionFormPickerState(
    showAccountPicker: Boolean = false,
    showDatePicker: Boolean = false,
) {
    var showAccountPicker by mutableStateOf(showAccountPicker)
    var showDatePicker by mutableStateOf(showDatePicker)

    companion object {
        val Saver: Saver<TransactionFormPickerState, Any> =
            listSaver(
                save = { listOf(it.showAccountPicker, it.showDatePicker) },
                restore = { TransactionFormPickerState(it[0], it[1]) },
            )
    }
}

@Composable
internal fun rememberTransactionFormPickerState(): TransactionFormPickerState =
    rememberSaveable(saver = TransactionFormPickerState.Saver) { TransactionFormPickerState() }
