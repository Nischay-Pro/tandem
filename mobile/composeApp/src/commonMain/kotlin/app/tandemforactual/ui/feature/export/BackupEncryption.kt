@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.export

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.core.session.BackupSecrets
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.FieldErrorMessage
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.templates.ToggleRow
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide

internal const val MIN_PASSPHRASE = 8
internal const val ENCRYPTED_BUDGET_REASON = "This budget is end-to-end encrypted, so its backups are always encrypted."

/**
 * The one passphrase every backup, made now or automatically, is encrypted with. It lives in the device's secure store;
 * an end-to-end encrypted budget can't turn encryption off.
 */
internal class BackupEncryption(
    private val secrets: BackupSecrets?,
    val budgetEncrypted: Boolean,
) {
    var hasPassphrase by mutableStateOf(secrets?.hasPassphrase() == true)
        private set
    var settingPassphrase by mutableStateOf(false)
        private set
    var confirmingOff by mutableStateOf(false)
        private set
    private var afterSet: (() -> Unit)? = null

    val available: Boolean get() = secrets != null
    val on: Boolean get() = hasPassphrase || budgetEncrypted

    /** Whether a backup can be made right now, or needs a passphrase set first. */
    val ready: Boolean get() = !budgetEncrypted || hasPassphrase

    fun passphrase(): String? = if (hasPassphrase) runCatching { secrets?.passphrase?.read() }.getOrNull() else null

    fun askForPassphrase(then: (() -> Unit)? = null) {
        afterSet = then
        settingPassphrase = true
    }

    fun closePassphrase(passphrase: String?) {
        settingPassphrase = false
        val next = afterSet
        afterSet = null
        if (passphrase != null && secrets?.setPassphrase(passphrase) == true) {
            hasPassphrase = true
            next?.invoke()
        }
    }

    fun turn(on: Boolean) {
        if (on) {
            askForPassphrase()
        } else if (!budgetEncrypted) {
            confirmingOff = true
        }
    }

    fun confirmOff(stop: Boolean) {
        confirmingOff = false
        if (stop && !budgetEncrypted && secrets?.setPassphrase(null) == true) hasPassphrase = false
    }
}

@Composable
internal fun rememberBackupEncryption(
    secrets: BackupSecrets?,
    budgetEncrypted: Boolean,
): BackupEncryption = remember(secrets, budgetEncrypted) { BackupEncryption(secrets, budgetEncrypted) }

/** The encrypt switch, and the passphrase row while it's on. */
@Composable
internal fun BackupEncryptionSection(encryption: BackupEncryption) {
    SectionCard("Encryption") {
        ToggleRow(
            "Encrypt backups",
            when {
                encryption.budgetEncrypted -> "Always on for an end-to-end encrypted budget"
                !encryption.available -> "Not available on this device"
                encryption.on -> "Only your passphrase opens them"
                else -> "Anyone with a backup file can open it"
            },
            checked = encryption.on,
            enabled = encryption.available && !encryption.budgetEncrypted,
            leadingIcon = Lucide.Lock,
        ) { encryption.turn(it) }
        if (encryption.on && encryption.available) {
            DividerLine()
            ListRow(
                if (encryption.hasPassphrase) "Change passphrase" else "Set a passphrase",
                if (encryption.hasPassphrase) null else "Needed before this budget can be backed up",
                leading = RowLeading.Icon(Lucide.KeyRound),
                onClick = { encryption.askForPassphrase() },
            )
        }
    }
    PassphraseSetupDialog(
        encryption.settingPassphrase,
        changing = encryption.hasPassphrase,
        reason = ENCRYPTED_BUDGET_REASON.takeIf { encryption.budgetEncrypted && !encryption.hasPassphrase },
        onClose = encryption::closePassphrase,
    )
    if (encryption.confirmingOff) {
        DestructiveConfirmationDialog(
            title = "Stop encrypting backups?",
            message = "New backups can be opened by anyone who has the file. Backups you’ve already made stay encrypted.",
            confirmLabel = "Stop encrypting",
            dismissLabel = "Keep encrypting",
            onDismissRequest = { encryption.confirmOff(false) },
            onConfirm = { encryption.confirmOff(true) },
        )
    }
}

/** Sets or changes the backup passphrase; it isn't shown again and Tandem can't recover it. */
@Composable
internal fun PassphraseSetupDialog(
    visible: Boolean,
    changing: Boolean,
    reason: String?,
    onClose: (String?) -> Unit,
) {
    var passphrase by remember(visible) { mutableStateOf("") }
    var confirmation by remember(visible) { mutableStateOf("") }
    val ready = passphrase.length >= MIN_PASSPHRASE && confirmation == passphrase
    TandemChoiceDialog(visible = visible, onDismissRequest = { onClose(null) }) {
        SheetCard(
            header = {
                TandemModalHeader(
                    if (changing) "Change passphrase" else "Encrypt backups",
                    onClose = { onClose(null) },
                    leadingIcon = Lucide.KeyRound,
                )
            },
            actions = {
                TandemActionButton("Save", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), enabled = ready) { onClose(passphrase) }
            },
        ) {
            reason?.let { BodyText(it) }
            BodyText("Backups made from now on are encrypted with this passphrase. Tandem can’t recover it, so keep it somewhere safe.")
            TextFieldCard("Passphrase", passphrase, { passphrase = it }, TextFieldOptions(isSecret = true))
            TextFieldCard("Confirm passphrase", confirmation, { confirmation = it }, TextFieldOptions(isSecret = true))
            FieldErrorMessage(
                when {
                    confirmation.isNotEmpty() && confirmation != passphrase -> "The passphrases don’t match."
                    passphrase.isNotEmpty() && passphrase.length < MIN_PASSPHRASE -> "Use at least $MIN_PASSPHRASE characters."
                    else -> null
                },
            )
        }
    }
}
