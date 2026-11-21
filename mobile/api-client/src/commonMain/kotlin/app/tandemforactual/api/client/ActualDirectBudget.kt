package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ActualBudgetEdits
import app.tandemforactual.data.actual.ActualBudgetReader
import app.tandemforactual.data.actual.ActualDatabase
import app.tandemforactual.data.actual.ActualMessageStore
import app.tandemforactual.data.actual.ActualSyncEngine
import app.tandemforactual.data.actual.ActualSyncResult
import app.tandemforactual.data.actual.ActualTransactionOperations
import app.tandemforactual.data.actual.ActualWriter
import app.tandemforactual.data.actual.OperationOutcome
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
    server: ActualServerClient,
    fileId: String,
    val groupId: String,
    now: () -> Long,
    newId: () -> String,
    downloaded: Boolean,
) {
    private val mutex = Mutex()

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
    private val syncEngine = ActualSyncEngine(store, server::sync, groupId, fileId, now = now)
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
            install: (ByteArray) -> ActualDatabase,
        ): ActualDirectBudget {
            val info = server.fileInfo(fileId)
            val groupId = info.groupId ?: throw ActualServerException("missing-group-id")
            if (expectedGroupId != null && groupId != expectedGroupId) throw ActualServerException("file-has-reset")
            if (info.encryptMeta != null) throw ActualServerException("encrypted-budget")
            val entries = ZipArchive.read(server.download(fileId))
            val data = entries["db.sqlite"] ?: throw ActualServerException("invalid-budget-archive")
            return open(install(data), server, fileId, groupId, now, newId, downloaded = true)
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
        ): ActualDirectBudget = open(db, server, fileId, groupId, now, newId, downloaded = false)

        private fun open(
            db: ActualDatabase,
            server: ActualServerClient,
            fileId: String,
            groupId: String,
            now: () -> Long,
            newId: () -> String,
            downloaded: Boolean,
        ): ActualDirectBudget =
            try {
                ActualDirectBudget(db, server, fileId, groupId, now, newId, downloaded)
            } catch (error: Throwable) {
                runCatching { db.close() }
                throw error
            }
    }

    private suspend fun <T> locked(block: suspend () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }
}
