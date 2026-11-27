@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.design.buttons.MIN_TOUCH_TARGET
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette

@Composable
internal fun DestructiveConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    enabled: Boolean = true,
    dismissEnabled: Boolean = true,
    messageKind: InlineMessageKind? = null,
    messageActionLabel: String? = null,
    onMessageAction: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val dismiss: () -> Unit = { if (dismissEnabled) onDismissRequest() }
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            TandemModalHeader(
                title = title,
                titleSize = TandemTypography.cardTitle,
                enabled = dismissEnabled,
                onClose = dismiss,
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
            TextButton(enabled = enabled, onClick = onConfirm, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                Text(confirmLabel, color = if (enabled) tandemPalette.negative else tandemPalette.muted)
            }
        },
        dismissButton = {
            TextButton(enabled = dismissEnabled, onClick = dismiss, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                Text(dismissLabel, color = if (dismissEnabled) tandemPalette.accent else tandemPalette.muted)
            }
        },
        backgroundColor = tandemPalette.surface,
    )
}
