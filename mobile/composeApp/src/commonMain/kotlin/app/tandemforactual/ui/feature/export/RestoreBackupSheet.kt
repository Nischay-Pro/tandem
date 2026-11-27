@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.data.planning.OpenedBackup
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.format.platformFormatDateAtEpochMilliseconds
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.FieldErrorMessage
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.ChoiceRow
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ArchiveRestore
import com.composables.icons.lucide.Lucide

/** Shows what an opened backup holds and where it can go, and restores it only when its button is pressed. */
@Composable
internal fun RestoreBackupSheet(
    flow: BackupFlow,
    currentBudget: String,
) {
    val pending = flow.pendingRestore
    val shown = remember { arrayOfNulls<OpenedBackup.Contents>(1) }
    if (pending != null) shown[0] = pending
    var password by remember(pending) { mutableStateOf("") }
    TandemChoiceDialog(visible = pending != null, onDismissRequest = flow::cancelRestore) {
        val backup = shown[0] ?: return@TandemChoiceDialog
        val newBudget = flow.restoreTarget == RestoreTarget.NEW_BUDGET && backup.database != null
        SheetCard(
            header = {
                TandemModalHeader(
                    "Restore backup",
                    onClose = flow::cancelRestore,
                    enabled = !flow.restoring,
                    leadingIcon = Lucide.ArchiveRestore,
                )
            },
            actions = {
                BodyText(flow.restoreMessage(backup))
                TandemActionButton(
                    if (newBudget) "Restore as new budget" else "Restore extras",
                    if (newBudget) TandemActionButtonTone.PRIMARY else TandemActionButtonTone.DESTRUCTIVE,
                    Modifier.fillMaxWidth(),
                    enabled = !(newBudget && flow.needsBudgetPassword && password.isEmpty()),
                    loading = flow.restoring,
                ) { flow.confirmRestore(password) }
            },
        ) {
            BackupFacts(backup, flow.openedWithPassphrase)
            if (backup.database != null) {
                ChoiceRow(
                    "Restore as a new budget",
                    "Uploads a copy to your Actual server and opens it here",
                    selected = flow.restoreTarget == RestoreTarget.NEW_BUDGET,
                ) { if (!flow.restoring) flow.restoreTarget = RestoreTarget.NEW_BUDGET }
            }
            if (backup.extras.isNotEmpty()) {
                ChoiceRow(
                    "Restore only the Tandem extras",
                    "Into “$currentBudget”, leaving the budget as it is",
                    selected = flow.restoreTarget == RestoreTarget.EXTRAS_ONLY,
                ) { if (!flow.restoring) flow.restoreTarget = RestoreTarget.EXTRAS_ONLY }
            }
            if (backup.database != null) {
                BodyText("Tandem doesn’t replace a budget on this device only, because the next sync with Actual would undo it.")
            }
            if (newBudget && flow.needsBudgetPassword) {
                TextFieldCard("Actual encryption password", password, { password = it }, TextFieldOptions(isSecret = true))
            }
            FieldErrorMessage(flow.restoreError)
        }
    }
}

@Composable
private fun BackupFacts(
    backup: OpenedBackup.Contents,
    encryptedFile: Boolean,
) {
    val made = backup.createdOn?.let(::formatLiveDate) ?: backup.exportedAt?.let(::platformFormatDateAtEpochMilliseconds)
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        backup.database?.let {
            Fact("Budget", backup.budgetName ?: "Not named")
            Fact("Budget size", fileSize(it.size))
        }
        made?.let { Fact("Made on", it) }
        Fact("Tandem extras", formatInteger(backup.extras.size))
        Fact("Backup file", if (encryptedFile) "Encrypted" else "Not encrypted")
    }
}

@Composable
private fun Fact(
    label: String,
    value: String,
) {
    val palette = tandemPalette
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
        Text(label, Modifier.weight(1f), color = palette.muted, fontSize = TandemTypography.body)
        Text(value, color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
    }
}

/** A file size in KB below a megabyte, otherwise in MB to one decimal place. */
internal fun fileSize(bytes: Int): String {
    if (bytes < MEGABYTE) return "${formatInteger((bytes + KILOBYTE - 1) / KILOBYTE)} KB"
    val tenths = bytes.toLong() * 10 / MEGABYTE
    return "${tenths / 10}.${tenths % 10} MB"
}

private const val KILOBYTE = 1024
private const val MEGABYTE = 1024 * 1024
