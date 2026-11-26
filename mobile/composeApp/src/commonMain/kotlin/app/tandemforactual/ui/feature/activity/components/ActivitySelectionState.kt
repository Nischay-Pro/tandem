package app.tandemforactual.ui.feature.activity.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Multi-select on the activity list: which transactions are picked and whether a bulk action is running. */
internal class ActivitySelectionState {
    var selectionMode by mutableStateOf(false)
    var selectedTransactionIds by mutableStateOf<Set<String>>(emptySet())
    var askingToDelete by mutableStateOf(false)
    var confirmBulkDelete by mutableStateOf(false)
    var isDeletingSelection by mutableStateOf(false)
    var isSelectingAll by mutableStateOf(false)

    fun toggle(transactionId: String) {
        selectedTransactionIds =
            if (transactionId in selectedTransactionIds) {
                selectedTransactionIds - transactionId
            } else {
                selectedTransactionIds + transactionId
            }
        if (selectedTransactionIds.isEmpty()) selectionMode = false
    }
}
