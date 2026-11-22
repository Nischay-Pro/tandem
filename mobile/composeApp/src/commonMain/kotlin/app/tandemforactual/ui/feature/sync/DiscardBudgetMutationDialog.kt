@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.runtime.Composable
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog

/** Confirmation dialog for discarding a pending budget change from this device. */
@Composable
internal fun DiscardBudgetMutationDialog(
    operation: OutboxOperationEntity,
    itemName: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DestructiveConfirmationDialog(
        title = "Discard this change?",
        message =
            if (operation.status == "uncertain") {
                "Remove the pending change for $itemName from this device? Actual may already have saved it; this won’t undo a change already stored there."
            } else {
                "Remove this budget change from this device?"
            },
        confirmLabel = "Discard change",
        dismissLabel = "Keep change",
        enabled = enabled,
        onDismissRequest = onDismiss,
        onConfirm = onConfirm,
    )
}
