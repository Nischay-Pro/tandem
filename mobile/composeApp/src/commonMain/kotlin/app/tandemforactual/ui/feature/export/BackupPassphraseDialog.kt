@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.export

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.FieldErrorMessage
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide

/** Asks for the passphrase an encrypted backup file was made with, to open it. */
@Composable
internal fun BackupPassphraseDialog(
    visible: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var passphrase by remember(visible) { mutableStateOf("") }
    TandemChoiceDialog(visible = visible, onDismissRequest = onDismiss) {
        SheetCard(
            header = { TandemModalHeader("Open backup", onClose = onDismiss, leadingIcon = Lucide.KeyRound) },
            actions = {
                TandemActionButton(
                    "Open",
                    TandemActionButtonTone.PRIMARY,
                    Modifier.fillMaxWidth(),
                    enabled = passphrase.isNotEmpty(),
                ) { onSubmit(passphrase) }
            },
        ) {
            BodyText("This backup is encrypted. Enter the passphrase it was made with.")
            TextFieldCard("Passphrase", passphrase, { passphrase = it }, TextFieldOptions(isSecret = true))
            FieldErrorMessage(error)
        }
    }
}
