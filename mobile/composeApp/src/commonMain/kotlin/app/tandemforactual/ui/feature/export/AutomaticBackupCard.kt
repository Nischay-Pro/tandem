@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.export

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.ui.core.platform.rememberBackupFolderPicker
import app.tandemforactual.ui.core.session.AutomaticBackupSettings
import app.tandemforactual.ui.core.session.BackupSecrets
import app.tandemforactual.ui.core.session.LocalBackupSecrets
import app.tandemforactual.ui.core.session.SyncNetwork
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.ChoiceRow
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Wifi

internal val BACKUP_INTERVALS = listOf(24 to "Daily", 72 to "Every 3 days", 168 to "Weekly", 720 to "Monthly")

internal fun backupIntervalLabel(settings: AutomaticBackupSettings): String =
    if (!settings.enabled) "Off" else BACKUP_INTERVALS.firstOrNull { it.first == settings.intervalHours }?.second ?: "Daily"

internal fun backupNetworkLabel(network: SyncNetwork): String = if (network == SyncNetwork.WIFI) "Wi-Fi only" else "Wi-Fi or mobile data"

private enum class BackupChoice { SCHEDULE, NETWORK }

/**
 * How often backups run in the background, where they go, and which networks WebDAV uploads use; folder backups need
 * none. The rows after the schedule show only while it's on. Where the platform can't run them yet the section says so instead.
 */
@Composable
internal fun AutomaticBackupsSection(
    state: AppState,
    preferences: AppPreferencesStore?,
    encryption: BackupEncryption,
) {
    val settings = state.automaticBackup
    val update = { change: (AutomaticBackupSettings) -> AutomaticBackupSettings -> state.automaticBackup = change(state.automaticBackup) }
    val pickFolder = rememberBackupFolderPicker { folder, label -> update { it.copy(folder = folder, folderLabel = label) } }
    val secrets = LocalBackupSecrets.current
    var choosing by remember { mutableStateOf<BackupChoice?>(null) }
    var settingWebDav by remember { mutableStateOf(false) }
    LaunchedEffect(preferences) {
        preferences?.let { store -> runCatching { store.read() }.onSuccess(state::restoreAutomaticBackupStatus) }
    }
    SectionCard("Automatic backups") {
        if (pickFolder == null) {
            ListRow(
                "Automatic backups run on Android for now",
                leading = RowLeading.Icon(Lucide.Info),
                options = ListRowOptions(showChevron = false),
            )
            return@SectionCard
        }
        ListRow(
            "Back up",
            backupIntervalLabel(settings),
            leading = RowLeading.Icon(Lucide.CalendarClock),
            onClick = { choosing = BackupChoice.SCHEDULE },
        )
        AnimatedReveal(visible = settings.enabled) {
            Column {
                DividerLine()
                ListRow(
                    "Save to",
                    settings.folderLabel ?: "Choose a folder",
                    leading = RowLeading.Icon(Lucide.FolderOpen),
                    onClick = pickFolder,
                )
                DividerLine()
                ListRow(
                    "Upload to WebDAV",
                    settings.webDavUrl ?: "Not set up",
                    leading = RowLeading.Icon(Lucide.Cloud),
                    options = ListRowOptions(subtitleMaxLines = 1),
                    onClick = if (secrets != null) ({ settingWebDav = true }) else null,
                )
                DividerLine()
                ListRow(
                    "Back up using",
                    "${backupNetworkLabel(settings.network)} for WebDAV uploads",
                    leading = RowLeading.Icon(Lucide.Wifi),
                    onClick = { choosing = BackupChoice.NETWORK },
                )
            }
        }
    }
    val schedule = { hours: Int? ->
        choosing = null
        val turnOn = {
            update { it.copy(enabled = true, intervalHours = hours ?: it.intervalHours) }
            if (settings.folder == null && settings.webDavUrl == null) pickFolder?.invoke()
        }
        when {
            hours == null -> update { it.copy(enabled = false) }
            encryption.ready -> turnOn()
            else -> encryption.askForPassphrase(turnOn)
        }
    }
    ScheduleChoice(choosing == BackupChoice.SCHEDULE, settings, onDismiss = { choosing = null }, onChoose = schedule)
    NetworkChoice(choosing == BackupChoice.NETWORK, settings.network, onDismiss = { choosing = null }) { network ->
        choosing = null
        update { it.copy(network = network) }
    }
    WebDavSetup(settingWebDav, settings, secrets, onClose = { settingWebDav = false }, update = update)
}

@Composable
private fun ScheduleChoice(
    visible: Boolean,
    settings: AutomaticBackupSettings,
    onDismiss: () -> Unit,
    onChoose: (Int?) -> Unit,
) {
    ChoiceSheet(visible, "Back up", onDismiss) {
        ChoiceRow("Off", selected = !settings.enabled) { onChoose(null) }
        BACKUP_INTERVALS.forEach { (hours, label) ->
            ChoiceRow(label, selected = settings.enabled && settings.intervalHours == hours) { onChoose(hours) }
        }
    }
}

@Composable
private fun NetworkChoice(
    visible: Boolean,
    network: SyncNetwork,
    onDismiss: () -> Unit,
    onChoose: (SyncNetwork) -> Unit,
) {
    ChoiceSheet(visible, "Back up using", onDismiss) {
        ChoiceRow(
            backupNetworkLabel(SyncNetwork.WIFI),
            "Hotspots and metered Wi-Fi count as mobile data.",
            selected = network == SyncNetwork.WIFI,
        ) { onChoose(SyncNetwork.WIFI) }
        ChoiceRow(backupNetworkLabel(SyncNetwork.ANY), selected = network != SyncNetwork.WIFI) { onChoose(SyncNetwork.ANY) }
    }
}

@Composable
private fun ChoiceSheet(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    choices: @Composable () -> Unit,
) {
    TandemChoiceDialog(visible = visible, onDismissRequest = onDismiss) {
        SheetCard(header = { TandemModalHeader(title, onClose = onDismiss) }) { choices() }
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
