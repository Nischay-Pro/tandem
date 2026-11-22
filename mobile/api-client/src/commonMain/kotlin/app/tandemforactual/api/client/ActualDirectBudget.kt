package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ActualBankSync
import app.tandemforactual.data.actual.ActualBudgetEdits
import app.tandemforactual.data.actual.ActualBudgetKey
import app.tandemforactual.data.actual.ActualBudgetReader
import app.tandemforactual.data.actual.ActualChangeHistory
import app.tandemforactual.data.actual.ActualDatabase
import app.tandemforactual.data.actual.ActualEditException
import app.tandemforactual.data.actual.ActualMessageStore
import app.tandemforactual.data.actual.ActualSyncEngine
import app.tandemforactual.data.actual.ActualSyncResult
import app.tandemforactual.data.actual.ActualTransactionOperations
import app.tandemforactual.data.actual.ActualWriter
import app.tandemforactual.data.actual.ChangeSet
import app.tandemforactual.data.actual.OperationOutcome
import app.tandemforactual.data.actual.UndoResult
import app.tandemforactual.data.actual.ZipArchive
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.BudgetSyncProtocolException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** Owns one local budget connection and serializes edits with CRDT synchronization. */
class ActualDirectBudget private constructor(
    private val db: ActualDatabase,
    private val server: ActualServerClient,
    private val fileId: String,
    val groupId: String,
    now: () -> Long,
    newId: () -> String,
    downloaded: Boolean,
    key: ActualBudgetKey?,
) {
    private val mutex = Mutex()
    internal val keySecret: ByteArray? = key?.secret

    init {
        if (downloaded) {
            db.transaction {
                db.execute(
                    "CREATE TABLE tandem_direct_sync " +
                        "(id INTEGER PRIMARY KEY, file_id TEXT NOT NULL, group_id TEXT NOT NULL, timestamp TEXT)",
                )
                db.execute("INSERT INTO tandem_direct_sync (id, file_id, group_id) VALUES (1, ?, ?)", fileId, groupId)
            }
        } else {
            val identity = db.first("SELECT file_id, group_id FROM tandem_direct_sync WHERE id = 1")
            require(identity != null && identity["file_id"] == fileId && identity["group_id"] == groupId) {
                "The local Actual database belongs to another budget or sync group."
            }
        }
    }

    private val store = ActualMessageStore(db)
    private val clock = store.loadClock().also { if (downloaded) store.resetNode(it) }
    internal val cacheGeneration = "actual:${clock.clock.timestamp.node}"
    private val syncEngine = ActualSyncEngine(store, server::sync, groupId, fileId, cipher = key, now = now)
    private val reader = ActualBudgetReader(db)
    private val writer = ActualWriter(store, clock, now, newId)
    private val operations = ActualTransactionOperations(writer)

    suspend fun sync() = locked { synchronize() }

    private suspend fun synchronize(): ActualSyncResult {
        val lastSyncedTimestamp = db.first("SELECT timestamp FROM tandem_direct_sync WHERE id = 1")?.get("timestamp") as String?
        return syncEngine.fullSync(clock, lastSyncedTimestamp).also {
            db.execute("UPDATE tandem_direct_sync SET timestamp = ? WHERE id = 1", it.lastSyncedTimestamp)
        }
    }

    /** Runs Actual's schedule service once a day after a successful sync, then uploads anything it changed. */
    private suspend fun runSchedules(today: String) {
        db.execute("CREATE TABLE IF NOT EXISTS tandem_prefs (id TEXT PRIMARY KEY, value TEXT)")
        if (db.first("SELECT value FROM tandem_prefs WHERE id = 'lastScheduleRun'")?.get("value") == today) return
        ActualBudgetEdits(writer, { today }).schedules.runService(syncSuccess = true)
        db.execute("INSERT OR REPLACE INTO tandem_prefs (id, value) VALUES ('lastScheduleRun', ?)", today)
        synchronize()
    }

    /** Builds the mobile cache projection at the same checkpoint as the completed CRDT sync. */
    suspend fun refreshProjection(today: String) =
        locked {
            synchronize()
            runSchedules(today)
            val sync = synchronize()
            BudgetSyncJson.projectDirectSnapshot(reader.read(today), groupId, cacheGeneration, sync.lastSyncedTimestamp)
        }

    suspend fun snapshot(today: String) = locked { reader.read(today) }

    /** The revisions last served to the mobile cache, so a refresh can send only what changed. */
    internal suspend fun servedProjection(): Pair<String?, Map<Pair<String, String>, String>> =
        locked {
            ensureProjectionTables()
            val cursor = db.first("SELECT value FROM tandem_prefs WHERE id = 'projectionCursor'")?.get("value") as String?
            cursor to
                db.query("SELECT type, id, revision FROM tandem_projection").associate {
                    (it["type"] as String to it["id"] as String) to
                        it["revision"] as String
                }
        }

    internal suspend fun saveServedProjection(
        cursor: String,
        records: Map<Pair<String, String>, String>,
    ) = locked {
        ensureProjectionTables()
        db.transaction {
            db.execute("DELETE FROM tandem_projection")
            records.forEach { (key, revision) ->
                db.execute("INSERT INTO tandem_projection (type, id, revision) VALUES (?, ?, ?)", key.first, key.second, revision)
            }
            db.execute("INSERT OR REPLACE INTO tandem_prefs (id, value) VALUES ('projectionCursor', ?)", cursor)
        }
    }

    private fun ensureProjectionTables() {
        db.execute("CREATE TABLE IF NOT EXISTS tandem_prefs (id TEXT PRIMARY KEY, value TEXT)")
        db.execute(
            "CREATE TABLE IF NOT EXISTS tandem_projection (type TEXT NOT NULL, id TEXT NOT NULL, revision TEXT NOT NULL, PRIMARY KEY (type, id))",
        )
    }

    suspend fun transactionRecord(id: String) = locked { reader.transactionRecord(id) }

    /** Downloads each bank-linked account through the Actual server and reconciles it, as Actual's bank sync does. */
    suspend fun bankSync(today: String): Int {
        sync()
        val accounts = locked { ActualBankSync(writer, { today }).linkedAccounts() }
        var changed = 0
        for (account in accounts) {
            val download = server.bankTransactions(account, fileId)
            locked {
                val result = ActualBankSync(writer, { today }).apply(account, download)
                changed += result.added.size + result.updated.size
            }
        }
        sync()
        return changed
    }

    /** Which of these statement ids the account already holds, so an import can say how many rows are new. */
    suspend fun importedIds(
        accountId: String,
        ids: List<String>,
    ): Set<String> =
        locked {
            ids.chunked(500).flatMapTo(HashSet()) { chunk ->
                db
                    .query(
                        "SELECT imported_id FROM v_transactions WHERE account = ? AND imported_id IN (${chunk.joinToString { "?" }})",
                        accountId,
                        *chunk.toTypedArray(),
                    ).map { it["imported_id"] as String }
            }
        }

    val deviceNode: String get() = clock.clock.timestamp.node

    suspend fun changeHistory(limit: Int): List<ChangeSet> =
        locked {
            runCatching {
                synchronize()
            }.exceptionOrNull()?.let { if (it is kotlin.coroutines.cancellation.CancellationException) throw it }
            ActualChangeHistory(writer).recent(limit)
        }

    /** Reverts one edit from the change history against freshly synced state, then uploads the reversal. */
    suspend fun undoChange(id: String): UndoResult =
        locked {
            synchronize()
            val history = ActualChangeHistory(writer)
            val set =
                history.recent(UNDO_SEARCH_LIMIT).firstOrNull { it.id == id }
                    ?: throw ActualEditException("This change is too old to undo", "change_not_found")
            history.undo(set).also { synchronize() }
        }

    /** Applies a budget edit against freshly synced state, then uploads it, as the gateway's maintenance routes did. */
    suspend fun <T> edit(
        today: String,
        block: (ActualBudgetEdits) -> T,
    ): T =
        locked {
            synchronize()
            runSchedules(today)
            val result = block(ActualBudgetEdits(writer, { today }))
            synchronize()
            result
        }

    suspend fun submit(
        operationId: String,
        transactionId: String,
        action: String,
        patch: JsonObject,
        baseSnapshot: JsonObject?,
    ) = locked { operations.submit(operationId, transactionId, action, patch, baseSnapshot) }

    /**
     * Applies a transaction assigned to this budget's direct outbox. The caller must establish ownership of the
     * outbox before switching from the gateway.
     */
    internal suspend fun submit(operation: OutboxOperationEntity): OperationOutcome {
        if (operation.budgetId != groupId || operation.recordType != "transaction") {
            throw BudgetSyncProtocolException("The operation does not belong to this direct transaction outbox")
        }
        val action =
            when (operation.action) {
                "CREATE" -> "create"
                "UPDATE" -> "update"
                "DELETE" -> "delete"
                else -> throw BudgetSyncProtocolException("This action is unavailable in the direct transaction outbox")
            }
        return submit(
            operation.operationId,
            operation.recordId,
            action,
            BudgetSyncJson.parseJsonObject(operation.patchJson),
            operation.baseSnapshotJson?.let(BudgetSyncJson::parseJsonObject),
        )
    }

    suspend fun close() = locked { db.close() }

    internal fun closeNow() = db.close()

    companion object {
        private const val UNDO_SEARCH_LIMIT = 200

        /**
         * [install] opens a new local database from the downloaded SQLite bytes. Keep it separate from any existing
         * budget until the initial sync succeeds. Persist [groupId] with the installed database for [reopen].
         */
        suspend fun download(
            server: ActualServerClient,
            fileId: String,
            now: () -> Long,
            newId: () -> String,
            expectedGroupId: String?,
            unlock: suspend (ActualKeyInfo) -> ActualBudgetKey,
            install: (ByteArray) -> ActualDatabase,
        ): ActualDirectBudget {
            val info = server.fileInfo(fileId)
            val groupId = info.groupId ?: throw ActualServerException("missing-group-id")
            if (expectedGroupId != null && groupId != expectedGroupId) throw ActualServerException("file-has-reset")
            val keyInfo = info.encryptMeta?.let { server.keyInfo(fileId) }
            val key = keyInfo?.let { unlock(it) }
            val archive = server.download(fileId).let { bytes -> if (key != null) key.decrypt(bytes, info.encryptMeta) else bytes }
            val data = ZipArchive.read(archive)["db.sqlite"] ?: throw ActualServerException("invalid-budget-archive")
            val db = install(data)
            if (keyInfo != null) saveKeyInfo(db, keyInfo)
            return open(db, server, fileId, groupId, now, newId, downloaded = true, key)
        }

        /** The key Actual encrypted this budget with, saved without the password or the derived key. */
        fun storedKeyInfo(db: ActualDatabase): ActualKeyInfo? =
            runCatching {
                db.query("SELECT id, value FROM tandem_prefs WHERE id IN ('keyId', 'keySalt', 'keyTest')").associate {
                    it["id"] as String to
                        it["value"] as String?
                }
            }.getOrNull()?.takeIf { it["keyId"] != null }?.let { ActualKeyInfo(it["keyId"], it["keySalt"], it["keyTest"]) }

        private fun saveKeyInfo(
            db: ActualDatabase,
            info: ActualKeyInfo,
        ) {
            db.execute("CREATE TABLE IF NOT EXISTS tandem_prefs (id TEXT PRIMARY KEY, value TEXT)")
            listOf("keyId" to info.id, "keySalt" to info.salt, "keyTest" to info.test).forEach { (id, value) ->
                db.execute("INSERT OR REPLACE INTO tandem_prefs (id, value) VALUES (?, ?)", id, value)
            }
        }

        /** The Actual file id saved with a downloaded budget, or null for a database Tandem didn't download. */
        fun storedFileId(db: ActualDatabase): String? =
            runCatching { db.first("SELECT file_id FROM tandem_direct_sync WHERE id = 1")?.get("file_id") as String? }.getOrNull()

        /** Uses the saved sync group so a reset is detected by Actual on the next sync. */
        fun reopen(
            db: ActualDatabase,
            server: ActualServerClient,
            fileId: String,
            groupId: String,
            now: () -> Long,
            newId: () -> String,
            key: ActualBudgetKey?,
        ): ActualDirectBudget = open(db, server, fileId, groupId, now, newId, downloaded = false, key)

        private fun open(
            db: ActualDatabase,
            server: ActualServerClient,
            fileId: String,
            groupId: String,
            now: () -> Long,
            newId: () -> String,
            downloaded: Boolean,
            key: ActualBudgetKey?,
        ): ActualDirectBudget =
            try {
                ActualDirectBudget(db, server, fileId, groupId, now, newId, downloaded, key)
            } catch (error: Throwable) {
                runCatching { db.close() }
                throw error
            }
    }

    private suspend fun <T> locked(block: suspend () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }
}
