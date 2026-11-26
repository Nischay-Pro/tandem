@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.ui.core.format.platformFormatDateAtEpochMilliseconds
import app.tandemforactual.ui.core.platform.rememberBackupFolderPicker
import app.tandemforactual.ui.core.session.AutomaticBackupSettings
import app.tandemforactual.ui.core.session.BackupSecrets
import app.tandemforactual.ui.core.session.LocalBackupSecrets
import app.tandemforactual.ui.core.session.SyncNetwork
import app.tandemforactual.ui.core.session.automaticBackupSummary
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
import com.composables.icons.lucide.Cloud
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
internal fun AutomaticBackupCard(
    state: AppState,
    preferences: AppPreferencesStore?,
) {
    val settings = state.automaticBackup
    val update = { change: (AutomaticBackupSettings) -> AutomaticBackupSettings -> state.automaticBackup = change(state.automaticBackup) }
    val pickFolder = rememberBackupFolderPicker { folder, label -> update { it.copy(folder = folder, folderLabel = label) } }
    val secrets = LocalBackupSecrets.current
    var settingWebDav by remember { mutableStateOf(false) }
    var hasPassphrase by remember(secrets) { mutableStateOf(secrets?.hasPassphrase() == true) }
    var settingPassphrase by remember { mutableStateOf(false) }
    LaunchedEffect(preferences) {
        preferences?.let { store -> runCatching { store.read() }.onSuccess(state::restoreAutomaticBackupStatus) }
    }
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
            if (on && settings.folder == null && settings.webDavUrl == null) pickFolder()
        }
        DividerLine()
        ListRow(
            "Folder",
            settings.folderLabel ?: "Choose where backups go",
            leading = RowLeading.Icon(Lucide.FolderOpen),
            onClick = pickFolder,
        )
        DividerLine()
        BackupSchedule(settings, update)
        DividerLine()
        ListRow(
            "Upload to WebDAV",
            settings.webDavUrl ?: "Off. Copy each backup to Nextcloud, a NAS or another WebDAV server.",
            leading = RowLeading.Icon(Lucide.Cloud),
            onClick = if (secrets != null) ({ settingWebDav = true }) else null,
        )
        DividerLine()
        ListRow(
            "Encrypt backups",
            if (hasPassphrase) "With your passphrase. Tap to change or remove it." else "Off. Anyone with the files can open them.",
            leading = RowLeading.Icon(Lucide.KeyRound),
            onClick = if (secrets != null) ({ settingPassphrase = true }) else null,
        )
    }
    WebDavSetup(settingWebDav, settings, secrets, onClose = { settingWebDav = false }, update = update)
    AutomaticBackupPassphraseDialog(settingPassphrase, hasPassphrase, onDismiss = { settingPassphrase = false }) { passphrase ->
        settingPassphrase = false
        if (secrets?.setPassphrase(passphrase) == true) hasPassphrase = passphrase != null
    }
}

/** How often backups run, on which networks, and how many are kept. */
@Composable
private fun BackupSchedule(
    settings: AutomaticBackupSettings,
    update: ((AutomaticBackupSettings) -> AutomaticBackupSettings) -> Unit,
) {
    Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
        Label("How often")
        FilterPills(BACKUP_INTERVALS, settings.intervalHours, { hours -> update { it.copy(intervalHours = hours) } })
        Label("Back up on")
        FilterPills(SyncNetwork.entries.map { it to it.shortLabel }, settings.network, { network -> update { it.copy(network = network) } })
        Label("Keep")
        FilterPills(BACKUP_KEEP.map { it to "Last $it" }, settings.keep, { keep -> update { it.copy(keep = keep) } })
    }
}

@Composable
private fun WebDavSetup(
    visible: Boolean,
    settings: AutomaticBackupSettings,
    secrets: BackupSecrets?,
    onClose: () -> Unit,
    update: ((AutomaticBackupSettings) -> AutomaticBackupSettings) -> Unit,
) {
    WebDavBackupDialog(
        visible,
        settings.webDavUrl,
        settings.webDavUser,
        onDismiss = onClose,
        onSave = { url, user, password ->
            val saved = secrets?.setWebDavPassword(password) == true
            if (saved) {
                onClose()
                update { it.copy(webDavUrl = url, webDavUser = user) }
            }
            saved
        },
        onRemove =
            settings.webDavUrl?.let {
                {
                    onClose()
                    runCatching { secrets?.webDavPassword?.clear() }
                    update { it.copy(webDavUrl = null, webDavUser = null) }
                }
            },
    )
}

@Composable
private fun Label(text: String) = Text(text, color = tandemPalette.muted, fontSize = TandemTypography.label)

private fun automaticBackupStatus(state: AppState): String =
    automaticBackupSummary(
        state.automaticBackup,
        state.automaticBackupLastRun?.let(::platformFormatDateAtEpochMilliseconds),
        state.automaticBackupResult,
    )
