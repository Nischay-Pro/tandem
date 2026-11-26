package app.tandemforactual.ui.feature.export

import app.tandemforactual.data.planning.PlanningBackupSeal
import app.tandemforactual.data.planning.PlanningStore
import app.tandemforactual.ui.core.session.PLANNING_BACKUP_FORMAT
import app.tandemforactual.ui.core.session.PlanningBackupSource
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.planningBackupJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The planning records in a backup file, after opening it with [passphrase] when it is sealed. */
internal sealed interface BackupContents {
    /** [budgetId], [budgetName] and [createdOn] are null for backups made before files named their budget. */
    data class Records(
        val records: List<Triple<String, String, JsonObject>>,
        val budgetId: String? = null,
        val budgetName: String? = null,
        val createdOn: String? = null,
    ) : BackupContents

    data object NeedsPassphrase : BackupContents

    data object WrongPassphrase : BackupContents

    data object NotABackup : BackupContents
}

internal fun readBackupFile(
    contents: String,
    passphrase: String?,
): BackupContents {
    if (!PlanningBackupSeal.isSealed(contents)) {
        return readPlanningBackup(contents) ?: BackupContents.NotABackup
    }
    if (passphrase == null) return BackupContents.NeedsPassphrase
    val opened = PlanningBackupSeal.open(contents, passphrase) ?: return BackupContents.WrongPassphrase
    return readPlanningBackup(opened) ?: BackupContents.NotABackup
}

/** A sealed backup of every planning record in [source]'s budget, or null when its planning data isn't open. */
internal suspend fun sealedPlanningBackup(
    session: TandemMobileSession,
    source: PlanningBackupSource,
    passphrase: String,
): String? {
    val records = session.planning.backupRecords(source.budgetId) ?: return null
    return PlanningBackupSeal.seal(planningBackupJson(records, source), passphrase)
}

internal fun readPlanningBackup(contents: String): BackupContents.Records? =
    runCatching {
        val root = Json.parseToJsonElement(contents).jsonObject
        if ((root["format"] as? JsonPrimitive)?.content != PLANNING_BACKUP_FORMAT) return null
        val records =
            root
                .getValue("records")
                .jsonArray
                .map { element ->
                    val record = element.jsonObject
                    Triple(
                        record.getValue("id").jsonPrimitive.content,
                        record.getValue("type").jsonPrimitive.content,
                        record.getValue("body").jsonObject,
                    )
                }.filter { (_, type, _) -> PlanningStore.isBackedUp(type) }
        BackupContents.Records(records, root.text("budgetId"), root.text("budgetName"), root.text("createdOn"))
    }.getOrNull()

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
