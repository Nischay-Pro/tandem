package app.tandemforactual.ui.feature.activity.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tandemforactual.data.store.BulkTransactionEdit

/** Multi-select on the activity list: which transactions are picked and whether a bulk action is running. */
internal class ActivitySelectionState {
    var selectionMode by mutableStateOf(false)
    var selectedTransactionIds by mutableStateOf<Set<String>>(emptySet())
    var askingToDelete by mutableStateOf(false)
    var confirmBulkDelete by mutableStateOf(false)
    var isDeletingSelection by mutableStateOf(false)
    var isSelectingAll by mutableStateOf(false)
    var bulkMenuOpen by mutableStateOf(false)
    var bulkPicker by mutableStateOf<ActivityBulkPicker?>(null)
    var confirmingBulkAction by mutableStateOf<ActivityBulkAction?>(null)
    var runningBulkAction by mutableStateOf<ActivityBulkAction?>(null)

    fun toggle(transactionId: String) {
        selectedTransactionIds =
            if (transactionId in selectedTransactionIds) {
                selectedTransactionIds - transactionId
            } else {
                selectedTransactionIds + transactionId
            }
        if (selectedTransactionIds.isEmpty()) selectionMode = false
    }

    fun clear() {
        selectedTransactionIds = emptySet()
        selectionMode = false
        bulkMenuOpen = false
        bulkPicker = null
    }
}

/** A choice that needs a value picked before it runs. */
internal enum class ActivityBulkPicker { CATEGORY, ADD_TAG, REMOVE_TAG, ACCOUNT, PAYEE, SCHEDULE }

/** What a bulk action does to the selected transactions. */
internal sealed interface ActivityBulkAction {
    data class Edit(
        val edit: BulkTransactionEdit,
    ) : ActivityBulkAction

    data object Duplicate : ActivityBulkAction

    data object Merge : ActivityBulkAction
}

/** What the bulk menu needs to know about one selected transaction. */
internal data class ActivityBulkItem(
    val id: String,
    val amountMinor: Long,
    val cleared: Boolean,
    val reconciled: Boolean,
    val notes: String,
)
