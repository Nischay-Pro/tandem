@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.FieldErrorMessage
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide

/** Sets, changes or removes the passphrase automatic backups are sealed with; it stays in the device's secure store. */
@Composable
internal fun AutomaticBackupPassphraseDialog(
    visible: Boolean,
    hasPassphrase: Boolean,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var passphrase by remember(visible) { mutableStateOf("") }
    var confirmation by remember(visible) { mutableStateOf("") }
    val ready = passphrase.length >= MIN_PASSPHRASE && confirmation == passphrase
    TandemChoiceDialog(visible = visible, onDismissRequest = onDismiss) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                TandemModalHeader("Encrypt automatic backups", onClose = onDismiss, leadingIcon = Lucide.KeyRound)
                BodyText("Backups made from now on are sealed with this passphrase. Tandem can’t recover it, so store it somewhere safe.")
                TextFieldCard("Passphrase", passphrase, { passphrase = it }, TextFieldOptions(isSecret = true))
                TextFieldCard("Confirm passphrase", confirmation, { confirmation = it }, TextFieldOptions(isSecret = true))
                FieldErrorMessage(
                    when {
                        confirmation.isNotEmpty() && confirmation != passphrase -> "The passphrases don’t match."
                        passphrase.isNotEmpty() && passphrase.length < MIN_PASSPHRASE -> "Use at least $MIN_PASSPHRASE characters."
                        else -> null
                    },
                )
                TandemActionButton("Save", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), enabled = ready) { onSave(passphrase) }
                if (hasPassphrase) {
                    TandemActionButton("Stop encrypting", TandemActionButtonTone.NEUTRAL, Modifier.fillMaxWidth()) { onSave(null) }
                }
            }
        }
    }
}
