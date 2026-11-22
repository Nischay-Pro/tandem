@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.runtime.Composable
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog

/** Confirmation dialog for discarding this device's edit in favour of Actual's version. */
@Composable
internal fun DiscardConflictEditDialog(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DestructiveConfirmationDialog(
        title = "Discard your edit?",
        message = "Tandem will check Actual’s latest version, then remove your edit from this device. Actual won’t be changed.",
        confirmLabel = "Discard edit",
        dismissLabel = "Keep editing",
        enabled = enabled,
        onDismissRequest = onDismiss,
        onConfirm = onConfirm,
    )
}
