package app.tandemforactual.data.planning

import app.tandemforactual.data.actual.ZipArchive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Why a file couldn't be opened as a backup. */
enum class BackupFileProblem {
    NOT_A_BACKUP,
    DAMAGED,
    NOT_A_BUDGET_DATABASE,
}

sealed interface OpenedBackup {
    /**
     * A readable backup. [database] is null for a planning-only backup, and [planningRecords] is null when the backup
     * carries no planning records. [exportedAt] is the epoch millis written into a budget backup's metadata.
     */
    data class Contents(
        val database: ByteArray?,
        val metadataJson: String?,
        val planningRecords: List<Triple<String, String, JsonObject>>?,
        val budgetId: String?,
        val budgetName: String?,
        val createdOn: String?,
        val exportedAt: Long?,
    ) : OpenedBackup {
        override fun equals(other: Any?): Boolean =
            other is Contents &&
                database.contentEquals(other.database) &&
                metadataJson == other.metadataJson &&
                planningRecords == other.planningRecords &&
                budgetId == other.budgetId &&
                budgetName == other.budgetName &&
                createdOn == other.createdOn &&
                exportedAt == other.exportedAt

        override fun hashCode(): Int = database.contentHashCode() * 31 + planningRecords.hashCode()
    }

    data object NeedsPassphrase : OpenedBackup

    data object WrongPassphrase : OpenedBackup

    data class Rejected(
        val problem: BackupFileProblem,
    ) : OpenedBackup
}

/**
 * Opens a file picked for restoring: a whole-budget backup (a plain zip such as Actual's export, or one sealed as a
 * `.tandembackup`) or a planning backup (JSON, plain or sealed). The bytes are untrusted, so the zip is read with its
 * size limits and checksums, and the database must be SQLite.
 */
object BackupArchive {
    const val PLANNING_FORMAT = "tandem-planning-backup"
    private val SQLITE_HEADER = "SQLite format 3\u0000".encodeToByteArray()
    private val ZIP_SIGNATURE = "PK".encodeToByteArray()
    private val RECORD_PREFIXES = listOf("planning.", "profile.")

    fun open(
        bytes: ByteArray,
        passphrase: String?,
    ): OpenedBackup {
        if (BudgetBackupFile.isSealed(bytes)) {
            if (passphrase.isNullOrEmpty()) return OpenedBackup.NeedsPassphrase
            return BudgetBackupFile.unseal(bytes, passphrase)?.let(::openZip) ?: OpenedBackup.WrongPassphrase
        }
        if (startsWith(bytes, ZIP_SIGNATURE)) return openZip(bytes)
        val text = runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull()
        return text?.let { openPlanning(it, passphrase) } ?: rejected(BackupFileProblem.NOT_A_BACKUP)
    }

    private fun openPlanning(
        text: String,
        passphrase: String?,
    ): OpenedBackup? {
        if (!PlanningBackupSeal.isSealed(text)) return planningOnly(text)
        if (passphrase.isNullOrEmpty()) return OpenedBackup.NeedsPassphrase
        val opened = PlanningBackupSeal.open(text, passphrase) ?: return OpenedBackup.WrongPassphrase
        return planningOnly(opened) ?: rejected(BackupFileProblem.DAMAGED)
    }

    private fun planningOnly(text: String): OpenedBackup? {
        val planning = readPlanning(text) ?: return null
        return OpenedBackup.Contents(null, null, planning.records, planning.budgetId, planning.budgetName, planning.createdOn, null)
    }

    private fun openZip(zip: ByteArray): OpenedBackup {
        val entries = runCatching { ZipArchive.read(zip) }.getOrNull() ?: return rejected(BackupFileProblem.DAMAGED)
        val database = entries[BudgetBackupFile.DATABASE] ?: return rejected(BackupFileProblem.NOT_A_BACKUP)
        if (!startsWith(database, SQLITE_HEADER)) return rejected(BackupFileProblem.NOT_A_BUDGET_DATABASE)
        val metadataJson = entries[BudgetBackupFile.METADATA]?.decodeToString()
        val metadata = metadataJson?.let { jsonObject(it) ?: return rejected(BackupFileProblem.DAMAGED) }
        val planning =
            entries[BudgetBackupFile.PLANNING]?.let { readPlanning(it.decodeToString()) ?: return rejected(BackupFileProblem.DAMAGED) }
        return OpenedBackup.Contents(
            database = database,
            metadataJson = metadataJson,
            planningRecords = planning?.records,
            budgetId = planning?.budgetId ?: metadata?.text("id"),
            budgetName = planning?.budgetName ?: metadata?.text("budgetName"),
            createdOn = planning?.createdOn,
            exportedAt = (metadata?.get("exportedAt") as? JsonPrimitive)?.longOrNull,
        )
    }

    private class PlanningBackup(
        val records: List<Triple<String, String, JsonObject>>,
        val budgetId: String?,
        val budgetName: String?,
        val createdOn: String?,
    )

    private fun readPlanning(text: String): PlanningBackup? =
        runCatching {
            val root = jsonObject(text)?.takeIf { it.text("format") == PLANNING_FORMAT } ?: return null
            val records =
                root.getValue("records").jsonArray.map { element ->
                    val record = element.jsonObject
                    Triple(
                        record.getValue("id").jsonPrimitive.content,
                        record.getValue("type").jsonPrimitive.content,
                        record.getValue("body").jsonObject,
                    )
                }
            PlanningBackup(
                records.filter { (_, type, _) -> RECORD_PREFIXES.any(type::startsWith) },
                root.text("budgetId"),
                root.text("budgetName"),
                root.text("createdOn"),
            )
        }.getOrNull()

    private fun jsonObject(text: String): JsonObject? = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()

    private fun JsonObject.text(name: String): String? = (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun startsWith(
        bytes: ByteArray,
        prefix: ByteArray,
    ): Boolean = bytes.size >= prefix.size && bytes.copyOfRange(0, prefix.size).contentEquals(prefix)

    private fun rejected(problem: BackupFileProblem) = OpenedBackup.Rejected(problem)
}
