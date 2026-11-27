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

internal const val MIN_PASSPHRASE = 8

/** What the passphrase is for: sealing a new backup, or opening one to restore or check. */
internal enum class PassphrasePurpose { BACK_UP, RESTORE, CHECK }

@Composable
internal fun BackupPassphraseDialog(
    purpose: PassphrasePurpose?,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var passphrase by remember(purpose) { mutableStateOf("") }
    var confirmation by remember(purpose) { mutableStateOf("") }
    val creating = purpose == PassphrasePurpose.BACK_UP
    val mismatch = creating && confirmation.isNotEmpty() && confirmation != passphrase
    val ready = if (creating) passphrase.length >= MIN_PASSPHRASE && confirmation == passphrase else passphrase.isNotEmpty()
    TandemChoiceDialog(visible = purpose != null, onDismissRequest = onDismiss) {
        SheetCard(
            header = {
                TandemModalHeader(
                    if (creating) "Protect this backup" else "Open backup",
                    onClose = onDismiss,
                    leadingIcon = Lucide.KeyRound,
                )
            },
            actions = {
                TandemActionButton(
                    when (purpose) {
                        PassphrasePurpose.BACK_UP -> "Back up"
                        PassphrasePurpose.RESTORE -> "Restore"
                        else -> "Check backup"
                    },
                    TandemActionButtonTone.PRIMARY,
                    Modifier.fillMaxWidth(),
                    enabled = ready,
                ) { onSubmit(passphrase) }
            },
        ) {
            BodyText(
                if (creating) {
                    "The backup is encrypted with this passphrase. Tandem can’t recover it, so store it somewhere safe."
                } else {
                    "Enter the passphrase used when this backup was made."
                },
            )
            TextFieldCard("Passphrase", passphrase, { passphrase = it }, TextFieldOptions(isSecret = true))
            if (creating) {
                TextFieldCard("Confirm passphrase", confirmation, { confirmation = it }, TextFieldOptions(isSecret = true))
                FieldErrorMessage(
                    when {
                        mismatch -> "The passphrases don’t match."
                        passphrase.isNotEmpty() && passphrase.length < MIN_PASSPHRASE -> "Use at least $MIN_PASSPHRASE characters."
                        else -> null
                    },
                )
            }
            FieldErrorMessage(error)
        }
    }
}
