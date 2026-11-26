@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.data.actual.ActualDates
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.platform.rememberFileSharer
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.backupLog
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ArchiveRestore
import com.composables.icons.lucide.FileCheck
import com.composables.icons.lucide.FileDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Share2
import kotlinx.coroutines.launch

internal enum class ExportRange(
    val label: String,
) {
    MONTH("This month"),
    YEAR("This year"),
    LAST_YEAR("Last year"),
    ALL("Everything"),
}

private const val BACKUP_FORMAT = "tandem-planning-backup"

/** Exports transactions as CSV, and backs up or restores the planning data Actual doesn't hold. */
@Composable
internal fun ExportScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    val share = rememberFileSharer()
    var range by rememberSaveable { mutableStateOf(ExportRange.YEAR) }
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, InlineMessageKind>?>(null) }
    val today = platformTodayIsoDate()
    val backup = rememberBackupFlow(state, mobileSession, share) { message = it }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        message?.let { (text, kind) -> InlineMessage(text, kind, onDismiss = { message = null }) }
        BackupStatusCard(
            state.backupLog,
            mobileSession.localSyncState?.lastSyncedAtEpochMs,
            mobileSession.planning.syncStatus,
            state.selectedBudgetEncrypted,
        )
        SectionCard("Transactions", "A CSV file that opens in any spreadsheet") {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                FilterPills(ExportRange.entries.map { it to it.label }, range, { range = it })
                TandemActionButton(
                    "Export CSV",
                    TandemActionButtonTone.PRIMARY,
                    Modifier.fillMaxWidth(),
                    icon = Lucide.FileDown,
                    loading = exporting,
                ) {
                    val (from, to) = exportRangeDates(range, today)
                    exporting = true
                    scope.launch {
                        val csv =
                            mobileSession.insights.exportTransactions(
                                state.selectedBudgetId,
                                from,
                                to,
                                state.encryptedPassword.takeIf { it.isNotBlank() },
                            )
                        exporting = false
                        if (csv == null) {
                            message = "Couldn’t export. Open the budget and try again." to InlineMessageKind.ERROR
                        } else {
                            share(exportFileName(state.selectedBudget, range, today), "text/csv", csv)
                        }
                    }
                }
            }
        }
        AutomaticBackupCard(state)
        PlanningBackupSection(onBackup = backup::startBackup, onRestore = backup::startRestore, onCheck = backup::startCheck)
    }
    BackupPassphraseDialog(backup.purpose, backup.error, backup::dismiss, backup::submit)
    backup.pendingRestore?.let { pending ->
        DestructiveConfirmationDialog(
            title = "Restore planning data?",
            message = backup.restoreMessage(pending),
            confirmLabel = "Restore",
            dismissLabel = "Cancel",
            onDismissRequest = backup::cancelRestore,
            onConfirm = backup::confirmRestore,
        )
    }
}

/** Back up or restore the planning data Actual doesn't hold. */
@Composable
private fun PlanningBackupSection(
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onCheck: () -> Unit,
) {
    SectionCard("Planning data", "Goals, debt details, reimbursements, reports and saved searches live outside Actual") {
        Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
            Text(
                "Backups are encrypted with a passphrase you choose. Actual’s own export doesn’t include this data.",
                color = tandemPalette.muted,
                fontSize = TandemTypography.label,
            )
            TandemActionButton("Back up", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), icon = Lucide.Share2, onClick = onBackup)
            TandemActionButton(
                "Restore a backup",
                TandemActionButtonTone.NEUTRAL,
                Modifier.fillMaxWidth(),
                icon = Lucide.ArchiveRestore,
                onClick = onRestore,
            )
            TandemActionButton(
                "Check a backup",
                TandemActionButtonTone.NEUTRAL,
                Modifier.fillMaxWidth(),
                icon = Lucide.FileCheck,
                onClick = onCheck,
            )
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
