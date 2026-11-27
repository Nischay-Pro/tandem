@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.export

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.data.actual.ActualDates
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.platform.rememberFileSharer
import app.tandemforactual.ui.core.session.LocalBackupSecrets
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.ArchiveRestore
import com.composables.icons.lucide.FileSpreadsheet
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch

internal enum class ExportRange(
    val label: String,
) {
    MONTH("This month"),
    YEAR("This year"),
    LAST_YEAR("Last year"),
    ALL("Everything"),
}

/** Backs up the budget with its Tandem extras, now or on a schedule, restores from a backup, and exports CSV. */
@Composable
internal fun ExportScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    openBudget: (BudgetCatalogItem) -> Unit,
) {
    val share = rememberFileSharer()
    var message by remember { mutableStateOf<Pair<String, InlineMessageKind>?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val encryption = rememberBackupEncryption(LocalBackupSecrets.current, state.selectedBudgetEncrypted)
    val backup = rememberBackupFlow(state, mobileSession, encryption, share, { message = it }, openBudget)
    PageColumn(verticalSpacing = TandemSpacing.large) {
        BodyText("Backups contain your Actual budget and Tandem extras, such as goals, debt details, reimbursements and saved reports.")
        message?.let { (text, kind) -> InlineMessage(text, kind, onDismiss = { message = null }) }
        BackupNowCard(state, encryption, backup.backingUp, backup::backUpNow)
        AutomaticBackupsSection(state, mobileSession.preferencesStore, encryption)
        BackupEncryptionSection(encryption)
        SectionCard("Restore") {
            ListRow(
                "Restore from a backup file",
                "See what’s in it before anything changes",
                leading = RowLeading.Icon(Lucide.ArchiveRestore),
                onClick = backup::startRestore,
            )
        }
        SectionCard("Export") {
            ListRow(
                "Export transactions (CSV)",
                "For spreadsheets, not a backup",
                leading = RowLeading.Icon(Lucide.FileSpreadsheet),
                onClick = { exporting = true },
            )
        }
    }
    ExportCsvDialog(exporting, state, mobileSession, share, onDone = { exporting = false }) { message = it }
    BackupPassphraseDialog(backup.askingPassphrase, backup.error, backup::dismiss, backup::submit)
    RestoreBackupSheet(backup, state.selectedBudget)
}

/** Picks a period and shares its transactions as a CSV file. */
@Composable
private fun ExportCsvDialog(
    visible: Boolean,
    state: AppState,
    mobileSession: TandemMobileSession,
    share: (String, String, ByteArray) -> Unit,
    onDone: () -> Unit,
    report: (Pair<String, InlineMessageKind>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var range by rememberSaveable { mutableStateOf(ExportRange.YEAR) }
    var working by remember { mutableStateOf(false) }
    val today = platformTodayIsoDate()
    TandemChoiceDialog(visible = visible, onDismissRequest = onDone) {
        SheetCard(
            header = { TandemModalHeader("Export transactions", onClose = onDone, leadingIcon = Lucide.FileSpreadsheet) },
            actions = {
                TandemActionButton("Export CSV", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), loading = working) {
                    val (from, to) = exportRangeDates(range, today)
                    working = true
                    scope.launch {
                        val password = state.encryptedPassword.takeIf { it.isNotBlank() }
                        val csv = mobileSession.insights.exportTransactions(state.selectedBudgetId, from, to, password)
                        working = false
                        onDone()
                        if (csv == null) {
                            report("Couldn’t export. Open the budget and try again." to InlineMessageKind.ERROR)
                        } else {
                            share(exportFileName(state.selectedBudget, range, today), "text/csv", csv.encodeToByteArray())
                        }
                    }
                }
            },
        ) {
            BodyText("A CSV file for spreadsheets. It isn’t a backup and can’t be restored.")
            FilterPills(ExportRange.entries.map { it to it.label }, range, { range = it })
        }
    }
}

/** The first and last day [range] covers, counted from [today] rather than the month open in Budget. */
internal fun exportRangeDates(
    range: ExportRange,
    today: String,
): Pair<String, String> {
    val month = today.take(7)
    val year = today.take(4).toInt()
    return when (range) {
        ExportRange.MONTH -> "$month-01" to ActualDates.addDays("$month-01", 40).take(7).let { ActualDates.addDays("$it-01", -1) }
        ExportRange.YEAR -> "$year-01-01" to "$year-12-31"
        ExportRange.LAST_YEAR -> "${year - 1}-01-01" to "${year - 1}-12-31"
        ExportRange.ALL -> "1900-01-01" to "2999-12-31"
    }
}

internal fun exportFileName(
    budgetName: String,
    range: ExportRange,
    today: String,
): String {
    val period =
        when (range) {
            ExportRange.MONTH -> today.take(7)
            ExportRange.YEAR -> today.take(4)
            ExportRange.LAST_YEAR -> (today.take(4).toInt() - 1).toString()
            ExportRange.ALL -> "all"
        }
    return "${exportFileStem(budgetName)}-$period.csv"
}

private fun exportFileStem(budgetName: String): String =
    budgetName
        .ifBlank { "budget" }
        .replace(Regex("[^A-Za-z0-9]+"), "-")
        .trim('-')
        .lowercase()
