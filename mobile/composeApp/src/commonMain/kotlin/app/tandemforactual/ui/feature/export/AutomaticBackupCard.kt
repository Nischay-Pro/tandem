@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.core.format.platformFormatDateAtEpochMilliseconds
import app.tandemforactual.ui.core.platform.rememberBackupFolderPicker
import app.tandemforactual.ui.core.session.AutomaticBackupSettings
import app.tandemforactual.ui.core.session.LocalBackupPassphraseStore
import app.tandemforactual.ui.core.session.SyncNetwork
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide

internal val BACKUP_INTERVALS = listOf(24 to "Daily", 72 to "Every 3 days", 168 to "Weekly", 720 to "Monthly")
internal val BACKUP_KEEP = listOf(3, 7, 14, 30)

/**
 * Backs up the whole budget and its planning data to a folder on a schedule, in the background, on the networks
 * chosen. Where the platform can't run them yet the card says so instead.
 */
@Composable
internal fun AutomaticBackupCard(state: AppState) {
    val settings = state.automaticBackup
    val update = { change: (AutomaticBackupSettings) -> AutomaticBackupSettings -> state.automaticBackup = change(state.automaticBackup) }
    val pickFolder = rememberBackupFolderPicker { folder, label -> update { it.copy(folder = folder, folderLabel = label) } }
    val passphrases = LocalBackupPassphraseStore.current
    var hasPassphrase by remember(passphrases) { mutableStateOf(runCatching { passphrases?.read() }.getOrNull() != null) }
    var settingPassphrase by remember { mutableStateOf(false) }
    SectionCard("Automatic backups", automaticBackupStatus(state)) {
        if (pickFolder == null) {
            Text(
                "Automatic backups run on Android for now.",
                Modifier.padding(TandemSpacing.large),
                color = tandemPalette.muted,
                fontSize = TandemTypography.label,
            )
            return@SectionCard
        }
        ToggleRow("Back up automatically", "The whole budget and its planning data", settings.enabled) { on ->
            update { it.copy(enabled = on) }
            if (on && settings.folder == null) pickFolder()
        }
        DividerLine()
        ListRow(
            "Folder",
            settings.folderLabel ?: "Choose where backups go",
            leading = RowLeading.Icon(Lucide.FolderOpen),
            onClick = pickFolder,
        )
        DividerLine()
        Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
            Label("How often")
            FilterPills(BACKUP_INTERVALS, settings.intervalHours, { hours -> update { it.copy(intervalHours = hours) } })
            Label("Back up on")
            FilterPills(
                SyncNetwork.entries.map { it to it.shortLabel },
                settings.network,
                { network -> update { it.copy(network = network) } },
            )
            Label("Keep")
            FilterPills(BACKUP_KEEP.map { it to "Last $it" }, settings.keep, { keep -> update { it.copy(keep = keep) } })
        }
        DividerLine()
        ListRow(
            "Encrypt backups",
            if (hasPassphrase) "With your passphrase. Tap to change or remove it." else "Off. Anyone with the files can open them.",
            leading = RowLeading.Icon(Lucide.KeyRound),
            onClick = if (passphrases != null) ({ settingPassphrase = true }) else null,
        )
    }
    AutomaticBackupPassphraseDialog(settingPassphrase, hasPassphrase, onDismiss = { settingPassphrase = false }) { passphrase ->
        settingPassphrase = false
        runCatching { if (passphrase == null) passphrases?.clear() else passphrases?.write(passphrase) }
        hasPassphrase = passphrase != null
    }
}

@Composable
private fun Label(text: String) = Text(text, color = tandemPalette.muted, fontSize = TandemTypography.label)

private fun automaticBackupStatus(state: AppState): String {
    val settings = state.automaticBackup
    val last = state.automaticBackupLastRun?.let { "Last backup ${platformFormatDateAtEpochMilliseconds(it)}" }
    return when {
        !settings.enabled -> "Off"
        settings.folder == null -> "Choose a folder to start"
        else -> last ?: "Waiting for the first backup"
    }
}
