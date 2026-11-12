@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import app.tandemforactual.ui.TandemSpacing
import app.tandemforactual.ui.TandemTypography
import app.tandemforactual.ui.tandemPalette

@Composable
internal fun DestructiveConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    enabled: Boolean = true,
    dismissEnabled: Boolean = enabled,
    messageKind: InlineMessageKind? = null,
    messageActionLabel: String? = null,
    onMessageAction: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            TandemModalHeader(
                title = title,
                titleSize = TandemTypography.cardTitle,
                enabled = dismissEnabled,
                onClose = onDismissRequest,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                if (messageKind == null) {
                    Text(message, color = tandemPalette.muted, fontSize = TandemTypography.body)
                } else {
                    InlineMessage(message, messageKind, actionLabel = messageActionLabel, onAction = onMessageAction)
                }
                content?.invoke()
            }
        },
        confirmButton = {
            TextButton(enabled = enabled, onClick = onConfirm) {
                Text(confirmLabel, color = if (enabled) tandemPalette.negative else tandemPalette.muted)
            }
        },
        dismissButton = {
            TextButton(enabled = dismissEnabled, onClick = onDismissRequest) {
                Text(dismissLabel, color = tandemPalette.accent)
            }
        },
        backgroundColor = tandemPalette.surface,
    )
}
