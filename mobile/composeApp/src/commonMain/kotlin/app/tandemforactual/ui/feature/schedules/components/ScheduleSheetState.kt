package app.tandemforactual.ui.feature.schedules.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Which schedule's action sheet is open. */
internal class ScheduleSelectionState {
    var selectedScheduleId by mutableStateOf<String?>(null)
    var showScheduleEditor by mutableStateOf(false)
}

/** The open sheet's mode ("menu", "edit" or "delete") and whether the delete alert shows. */
internal class ScheduleDialogState {
    var showDeleteScheduleConfirmation by mutableStateOf(false)
    var dialogMode by mutableStateOf("menu")
}
