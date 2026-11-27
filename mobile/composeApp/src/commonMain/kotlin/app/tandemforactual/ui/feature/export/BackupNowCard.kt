@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.export

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.core.format.platformIsoDateAtEpochMilliseconds
import app.tandemforactual.ui.core.session.AutomaticBackups
import app.tandemforactual.ui.core.session.automaticBackupSummary
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.backupLog
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Share2

/** When the last backup was made, whether backups are encrypted, any automatic backup problem, and Back up now. */
@Composable
internal fun BackupNowCard(
    state: AppState,
    encryption: BackupEncryption,
    backingUp: Boolean,
    onBackUp: () -> Unit,
) {
    SurfaceCard {
        ListRow(
            lastBackupDate(state)?.let { "Last backup: ${formatLiveDate(it)}" } ?: "No backup yet",
            listOfNotNull(if (encryption.on) "Encrypted" else "Not encrypted", automaticBackupProblem(state, encryption))
                .joinToString(" · "),
            leading = RowLeading.Icon(Lucide.History),
            options = ListRowOptions(showChevron = false, subtitleMaxLines = 3),
        )
        TandemActionButton(
            "Back up now",
            TandemActionButtonTone.PRIMARY,
            Modifier
                .fillMaxWidth()
                .padding(start = TandemSpacing.large, end = TandemSpacing.large, bottom = TandemSpacing.large),
            icon = Lucide.Share2,
            loading = backingUp,
            onClick = onBackUp,
        )
    }
}

/** The newer of the last Back up now and the last automatic backup, as an ISO date. */
internal fun lastBackupDate(state: AppState): String? =
    listOfNotNull(state.backupLog.sharedOn, state.automaticBackupLastRun?.let(::platformIsoDateAtEpochMilliseconds)).maxOrNull()

/** Why automatic backups aren't running, in a few words, or null while they're fine or off. */
internal fun automaticBackupProblem(
    state: AppState,
    encryption: BackupEncryption,
): String? {
    val settings = state.automaticBackup
    val result = state.automaticBackupResult
    return when {
        !settings.enabled -> null
        AutomaticBackups.needsPassphrase(encryption.budgetEncrypted, encryption.hasPassphrase) ->
            "Automatic backups paused until a passphrase is set"
        settings.folder == null && settings.webDavUrl == null -> "Choose a folder for automatic backups"
        result?.problem != null -> automaticBackupSummary(settings, null, result)
        else -> null
    }
}
