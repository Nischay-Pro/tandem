package app.tandemforactual.ui.core.state

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** When this device last made a backup of a budget with Back up now, as an ISO date. */
internal data class BackupLog(
    val sharedOn: String? = null,
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
            BackupLog(
                ((entry["sharedOn"] ?: entry["backedUpOn"]) as? JsonPrimitive)?.contentOrNull,
            )
        }
    }.getOrDefault(emptyMap())

internal fun encodeBackupLogs(logs: Map<String, BackupLog>): String =
    JsonObject(
        logs.mapValues { (_, log) ->
            JsonObject(
                listOfNotNull(
                    log.sharedOn?.let { "sharedOn" to JsonPrimitive(it) },
                ).toMap(),
            )
        },
    ).toString()
