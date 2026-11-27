package app.tandemforactual.ui.feature.schedules.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Which schedule's action sheet is open, or the new schedule being made and the transaction it came from. */
internal class ScheduleSelectionState {
    var selectedScheduleId by mutableStateOf<String?>(null)
    var showScheduleEditor by mutableStateOf(false)
    var creating by mutableStateOf(false)
    var createKey by mutableIntStateOf(0)
    var createValues by mutableStateOf(ScheduleDraftValues())
    var linkTransactionId by mutableStateOf<String?>(null)

    fun startCreating(
        values: ScheduleDraftValues,
        transactionId: String? = null,
    ) {
        showScheduleEditor = false
        createValues = values
        linkTransactionId = transactionId
        createKey += 1
        creating = true
    }
}

/** The open sheet's mode ("menu", "edit" or "delete") and whether the delete alert shows. */
internal class ScheduleDialogState {
    var showDeleteScheduleConfirmation by mutableStateOf(false)
    var dialogMode by mutableStateOf("menu")
}
