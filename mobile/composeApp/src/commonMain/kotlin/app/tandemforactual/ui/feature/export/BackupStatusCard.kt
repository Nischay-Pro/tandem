@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.export

import androidx.compose.runtime.Composable
import app.tandemforactual.ui.core.format.formatDateAtEpochMilliseconds
import app.tandemforactual.ui.core.state.BackupLog
import app.tandemforactual.ui.core.state.PlanningSyncStatus
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.FileCheck
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.NotebookPen

/** What each part of this budget needs to be recovered on a new device, and how current it is. */
@Composable
internal fun BackupStatusCard(
    log: BackupLog,
    lastSyncedAtEpochMs: Long?,
    planningSync: PlanningSyncStatus,
    budgetEncrypted: Boolean,
) {
    SectionCard("Recovery", "What a new device needs to get everything back") {
        ListRow(
            leading = RowLeading.Icon(Lucide.Database),
            title = "Budget",
            subtitle =
                "Kept on your Actual server. " +
                    (lastSyncedAtEpochMs?.let { "Last synced ${formatDateAtEpochMilliseconds(it)}." } ?: "Not synced on this device yet."),
        )
        DividerLine()
        ListRow(
            leading = RowLeading.Icon(Lucide.NotebookPen),
            title = "Planning data",
            subtitle =
                listOfNotNull(
                    if (planningSync == PlanningSyncStatus.SYNCED) "Synced through your Actual server." else null,
                    log.backedUpOn?.let { "Backed up ${formatLiveDate(it)}." } ?: "Not backed up from this device.",
                ).joinToString(" "),
        )
        DividerLine()
        ListRow(
            leading = RowLeading.Icon(Lucide.KeyRound),
            title = "Unlocking",
            subtitle =
                when {
                    budgetEncrypted -> "Your budget password opens the budget and its planning data."
                    planningSync == PlanningSyncStatus.NEEDS_RECOVERY_KEY -> "Enter the recovery key from another device in Household."
                    else -> "Other devices need the recovery key shown in Household."
                },
        )
        DividerLine()
        ListRow(
            leading = RowLeading.Icon(Lucide.FileCheck),
            title = "Last checked backup",
            subtitle =
                log.checkedOn?.let { "Opened correctly on ${formatLiveDate(it)}." }
                    ?: "Not checked yet. Check a backup to be sure it opens.",
        )
    }
}
