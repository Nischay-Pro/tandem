package app.tandemforactual.ui.core.state

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** When this device last backed up a budget's planning data, and last confirmed a backup opens. ISO dates. */
internal data class BackupLog(
    val backedUpOn: String? = null,
    val checkedOn: String? = null,
)

internal val AppState.backupLog: BackupLog get() = decodeBackupLogs(backupLogJson)[selectedBudgetId] ?: BackupLog()

internal fun AppState.updateBackupLog(change: (BackupLog) -> BackupLog) {
    val all = decodeBackupLogs(backupLogJson)
    backupLogJson = encodeBackupLogs(all + (selectedBudgetId to change(all[selectedBudgetId] ?: BackupLog())))
}

internal fun decodeBackupLogs(json: String): Map<String, BackupLog> =
    runCatching {
        Json.parseToJsonElement(json).jsonObject.mapValues { (_, value) ->
            val entry = value.jsonObject
            BackupLog((entry["backedUpOn"] as? JsonPrimitive)?.contentOrNull, (entry["checkedOn"] as? JsonPrimitive)?.contentOrNull)
        }
    }.getOrDefault(emptyMap())

internal fun encodeBackupLogs(logs: Map<String, BackupLog>): String =
    JsonObject(
        logs.mapValues { (_, log) ->
            JsonObject(
                listOfNotNull(
                    log.backedUpOn?.let { "backedUpOn" to JsonPrimitive(it) },
                    log.checkedOn?.let { "checkedOn" to JsonPrimitive(it) },
                ).toMap(),
            )
        },
    ).toString()
