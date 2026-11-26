package app.tandemforactual.ui.core.session

import androidx.compose.runtime.staticCompositionLocalOf
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.budgetBackup
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.planning.PlanningCipher
import app.tandemforactual.data.planning.PlanningStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Where the device keeps backup secrets: the passphrase that seals backups, and the WebDAV password. */
class BackupSecrets(
    val passphrase: DeviceCredentialStore,
    val webDavPassword: DeviceCredentialStore,
) {
    /** Whether backups are sealed; a stored passphrase that can't be read still counts, so the card never says Off. */
    fun hasPassphrase(): Boolean = runCatching { passphrase.read() != null }.getOrDefault(true)

    /** Saves the passphrase, or removes it when null; false when the device couldn't. */
    fun setPassphrase(value: String?): Boolean =
        runCatching { if (value == null) passphrase.clear() else passphrase.write(value) }.isSuccess

    /** Saves the WebDAV password; false when the device couldn't. */
    fun setWebDavPassword(value: String): Boolean = runCatching { webDavPassword.write(value) }.isSuccess
}

/** The platform's [BackupSecrets]; without them backups stay plain and WebDAV can't be set up. */
val LocalBackupSecrets = staticCompositionLocalOf<BackupSecrets?> { null }

/** Automatic backup choices, saved with the app's settings so a background task can read them. */
data class AutomaticBackupSettings(
    val enabled: Boolean = false,
    val intervalHours: Int = 24,
    val network: SyncNetwork = SyncNetwork.ANY,
    val keep: Int = 7,
    val folder: String? = null,
    val folderLabel: String? = null,
    val webDavUrl: String? = null,
    val webDavUser: String? = null,
) {
    fun toPreferences(): Map<String, String> =
        mapOf(
            ENABLED to enabled.toString(),
            INTERVAL to intervalHours.toString(),
            NETWORK to network.name,
            KEEP to keep.toString(),
            FOLDER to folder.orEmpty(),
            FOLDER_LABEL to folderLabel.orEmpty(),
            WEBDAV_URL to webDavUrl.orEmpty(),
            WEBDAV_USER to webDavUser.orEmpty(),
        )

    companion object {
        const val ENABLED = "autoBackupEnabled"
        const val INTERVAL = "autoBackupIntervalHours"
        const val NETWORK = "autoBackupNetwork"
        const val KEEP = "autoBackupKeep"
        const val FOLDER = "autoBackupFolder"
        const val FOLDER_LABEL = "autoBackupFolderLabel"
        const val WEBDAV_URL = "autoBackupWebDavUrl"
        const val WEBDAV_USER = "autoBackupWebDavUser"
        const val LAST_RUN = "internal:auto-backup-last-run"

        fun from(preferences: Map<String, String>): AutomaticBackupSettings {
            val defaults = AutomaticBackupSettings()
            return AutomaticBackupSettings(
                enabled = preferences[ENABLED]?.toBooleanStrictOrNull() ?: defaults.enabled,
                intervalHours = preferences[INTERVAL]?.toIntOrNull()?.takeIf { it > 0 } ?: defaults.intervalHours,
                network = SyncNetwork.entries.firstOrNull { it.name == preferences[NETWORK] } ?: defaults.network,
                keep = preferences[KEEP]?.toIntOrNull()?.takeIf { it > 0 } ?: defaults.keep,
                folder = preferences[FOLDER]?.takeIf(String::isNotBlank),
                folderLabel = preferences[FOLDER_LABEL]?.takeIf(String::isNotBlank),
                webDavUrl = preferences[WEBDAV_URL]?.takeIf(String::isNotBlank),
                webDavUser = preferences[WEBDAV_USER]?.takeIf(String::isNotBlank),
            )
        }
    }
}

object AutomaticBackups {
    const val FILE_PREFIX = "tandem-backup-"

    /** Whether a backup is due: switched on, with a folder or WebDAV to go to, and the interval has passed since the last. */
    fun due(
        settings: AutomaticBackupSettings,
        lastRun: Long?,
        now: Long,
    ): Boolean =
        settings.enabled &&
            (settings.folder != null || settings.webDavUrl != null) &&
            (lastRun == null || now - lastRun >= settings.intervalHours * 3_600_000L)

    /** `tandem-backup-household-2026-11-24-0930.zip`; sealed backups aren't zips, so they get their own extension. */
    fun fileName(
        budgetName: String,
        stamp: String,
        sealed: Boolean,
    ): String {
        val slug =
            budgetName
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifEmpty { "budget" }
                .take(40)
        return "$FILE_PREFIX$slug-$stamp.${if (sealed) "tandembackup" else "zip"}"
    }

    /** The oldest of this budget's backups beyond the newest [keep]; names sort by their timestamp. */
    fun toDelete(
        names: List<String>,
        budgetName: String,
        keep: Int,
    ): List<String> {
        val mine = fileName(budgetName, "", sealed = false).substringBeforeLast("-.")
        return names.filter { it.startsWith("$mine-") }.sortedDescending().drop(keep)
    }

    /**
     * A whole-budget backup made outside a running app session: the budget from [client] and the planning records
     * this device can open with its keys in [planningKeys].
     */
    suspend fun build(
        client: TandemApiClient,
        planningDatabase: TandemDatabase,
        planningKeys: DeviceCredentialStore?,
        budgetId: String,
        encryptionPassword: String?,
        budgetName: String,
        passphrase: String?,
        now: () -> Long,
    ): ByteArray {
        val key = PlanningKeyring(planningKeys).existingKey(budgetId)
        val planning =
            key?.let {
                planningBackupJson(
                    PlanningStore(planningDatabase.planningRecordDao(), budgetId, { PlanningCipher(it) }, now).backupRecords(),
                )
            }
        return client.budgetBackup(budgetId, encryptionPassword, budgetName, planning, passphrase)
    }
}

/** The planning backup format, shared by manual planning backups and whole-budget backups. */
internal fun planningBackupJson(records: List<Triple<String, String, kotlinx.serialization.json.JsonObject>>): String =
    buildJsonObject {
        put("format", PLANNING_BACKUP_FORMAT)
        put("version", 1)
        put(
            "records",
            JsonArray(
                records.map { (id, type, body) ->
                    buildJsonObject {
                        put("id", id)
                        put("type", type)
                        put("body", body)
                    }
                },
            ),
        )
    }.toString()

internal const val PLANNING_BACKUP_FORMAT = "tandem-planning-backup"
