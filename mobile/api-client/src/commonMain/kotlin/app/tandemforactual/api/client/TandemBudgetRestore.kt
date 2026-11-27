package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ActualBudgetKey
import app.tandemforactual.data.actual.ActualDatabase
import app.tandemforactual.data.database.ActualBudgetFiles
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** A budget made from a backup: [budgetId] is its sync group, the id Tandem opens it by. */
data class RestoredBudget(
    val budgetId: String,
    val name: String,
    val encrypted: Boolean,
)

/**
 * Uploads a backup's Actual database to the server as a new budget, as Actual's import does: it gets a new file id and
 * sync group, so the budget the backup came from is never touched, here or on the server. The copy is first cleared
 * of its sync history like Actual's `resetSync`. A backup of an end-to-end encrypted budget goes up encrypted under
 * the same key, which [budgetPassword] must open; otherwise nothing is uploaded.
 */
suspend fun TandemApiClient.restoreBudgetAsNew(
    database: ByteArray,
    metadataJson: String?,
    name: String,
    budgetPassword: String?,
): RestoredBudget =
    call {
        val server = requireServer()
        val files = budgetFiles ?: throw TandemApiException("not_supported")
        val (prepared, keyInfo) = prepareForUpload(files, database)
        val key = keyInfo?.let { restoreKey(it, budgetPassword) }
        val uploaded = server.uploadBudget(null, name, prepared, restoredMetadata(metadataJson), key, newId)
        RestoredBudget(uploaded.groupId, name, key != null)
    }

/** The backup's database with Actual's sync history, caches and Tandem's own tables removed, and its key details. */
private fun TandemApiClient.prepareForUpload(
    files: ActualBudgetFiles,
    database: ByteArray,
): Pair<ByteArray, ActualKeyInfo?> {
    val scratch = "restore-${newId()}"
    try {
        val db =
            if (files.canSeal) {
                files.installSealed(scratch, database, Uuid.random().toByteArray() + Uuid.random().toByteArray(), "{}")
            } else {
                files.install(scratch, database)
            }
        try {
            val keyInfo = ActualDirectBudget.storedKeyInfo(db)
            clearSyncHistory(db)
            return files.snapshot(scratch) { files.exportPlain(scratch, db, it) } to keyInfo
        } finally {
            db.close()
        }
    } catch (error: Exception) {
        throw TandemApiException("backup_unreadable", cause = error)
    } finally {
        files.remove(scratch)
    }
}

/** Actual's `resetSync` cleanup and the cache its import empties, then Tandem's tables, which only a device copy has. */
internal fun clearSyncHistory(db: ActualDatabase) {
    val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table'").map { it["name"] as String }.toSet()
    db.transaction {
        CLEARED_TABLES.filter(tables::contains).forEach { db.execute("DELETE FROM $it") }
        TOMBSTONED_TABLES.filter(tables::contains).forEach { db.execute("DELETE FROM $it WHERE tombstone = 1") }
        tables.filter { it.startsWith("tandem_") }.forEach { db.execute("DROP TABLE $it") }
    }
}

private fun restoreKey(
    info: ActualKeyInfo,
    password: String?,
): ActualUploadKey {
    if (password.isNullOrEmpty()) throw TandemApiException("budget_password_required")
    val salt = info.salt.orEmpty()
    val test = info.test.orEmpty()
    val key = ActualBudgetKey.derive(checkNotNull(info.id), password, salt)
    if (!key.opens(test)) throw TandemApiException("budget_password_incorrect")
    return ActualUploadKey(key, salt, test)
}

/** The backup's metadata without the ids that tied it to its old budget and server file. */
internal fun restoredMetadata(metadataJson: String?): String? {
    val metadata = metadataJson?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return null
    return JsonObject(metadata - STALE_METADATA).toString()
}

private val CLEARED_TABLES = listOf("messages_crdt", "messages_clock", "messages_pending", "kvcache", "kvcache_key")
private val TOMBSTONED_TABLES =
    listOf("transactions", "accounts", "payees", "categories", "category_groups", "schedules", "rules")
private val STALE_METADATA = setOf("id", "cloudFileId", "groupId", "encryptKeyId", "lastUploaded", "lastSyncedTimestamp", "resetClock")
