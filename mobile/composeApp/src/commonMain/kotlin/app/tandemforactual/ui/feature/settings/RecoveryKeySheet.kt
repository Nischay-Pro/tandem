@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.parseRecoveryKey
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette

/** Shows this device's planning recovery key, or takes the one from a device that already shares planning records. */
@Composable
internal fun RecoveryKeySheet(
    visible: Boolean,
    recoveryKey: String,
    onUseKey: (ByteArray) -> Unit,
    onReplaceKey: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmingReplace by remember(visible) { mutableStateOf(false) }
    val palette = tandemPalette
    var entered by remember(visible) { mutableStateOf("") }
    var error by remember(visible) { mutableStateOf<String?>(null) }
    TandemTaskSheet(visible = visible, onDismissRequest = onDismiss, onDismissed = {}, secure = true) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.fillMaxWidth().padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                TandemModalHeader("Planning recovery key", onClose = onDismiss)
                Text(
                    "Planning details like savings goals are encrypted before they sync. To add a device, enter this key there. Keep it somewhere safe.",
                    color = palette.muted,
                    fontSize = TandemTypography.compactBody,
                )
                Text(recoveryKey, color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.Medium)
                TextFieldCard(
                    "Key from another device",
                    entered,
                    { entered = it.take(80) },
                    TextFieldOptions(placeholder = "8 groups of 8 characters"),
                )
                error?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                TandemActionButton(
                    "Use this key",
                    TandemActionButtonTone.PRIMARY,
                    Modifier.fillMaxWidth(),
                    enabled = entered.isNotBlank(),
                ) {
                    val key = parseRecoveryKey(entered)
                    if (key == null) error = "Enter all 64 characters of the key." else onUseKey(key)
                }
                ReplaceKeyAction(confirmingReplace, { confirmingReplace = true }, onReplaceKey)
            }
        }
    }
}

/** Lost a device: replace the key after a confirmation that every other device will need the new one. */
@Composable
private fun ReplaceKeyAction(
    confirming: Boolean,
    onAsk: () -> Unit,
    onReplace: () -> Unit,
) {
    if (!confirming) {
        SecondaryButton("Lost a device? Replace this key", Modifier.fillMaxWidth(), onClick = onAsk)
        return
    }
    InlineMessage(
        "A new key locks out the lost device from anything synced from now on. Enter the new key on each device you still use.",
        InlineMessageKind.WARNING,
    )
    TandemActionButton("Replace the key", TandemActionButtonTone.DESTRUCTIVE, Modifier.fillMaxWidth(), onClick = onReplace)
}
