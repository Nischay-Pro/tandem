package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ActualBudgetReader
import app.tandemforactual.data.actual.ActualDatabase
import app.tandemforactual.data.actual.ActualMessageStore
import app.tandemforactual.data.actual.ActualSyncEngine
import app.tandemforactual.data.actual.ActualTransactionOperations
import app.tandemforactual.data.actual.ActualWriter
import app.tandemforactual.data.actual.ZipArchive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
                    "CREATE TABLE tandem_direct_sync (id INTEGER PRIMARY KEY, file_id TEXT NOT NULL, group_id TEXT NOT NULL, timestamp TEXT)",
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
    private val syncEngine = ActualSyncEngine(store, server::sync, groupId, fileId, now = now)
    private val reader = ActualBudgetReader(db)
    private val operations = ActualTransactionOperations(ActualWriter(store, clock, now, newId))


    suspend fun sync() =
        mutex.withLock {
            val lastSyncedTimestamp = db.first("SELECT timestamp FROM tandem_direct_sync WHERE id = 1")?.get("timestamp") as String?
            syncEngine.fullSync(clock, lastSyncedTimestamp).also {
                db.execute("UPDATE tandem_direct_sync SET timestamp = ? WHERE id = 1", it.lastSyncedTimestamp)
            }
        }

    suspend fun snapshot(today: String) = mutex.withLock { reader.read(today) }

    /** Refreshes a staging database for comparison without uploading its history or advancing its write checkpoint. */
    suspend fun preview(today: String) =
        mutex.withLock {
            syncEngine.fullSync(clock, null, receiveOnly = true)
            reader.read(today)
        }

    suspend fun transaction(id: String) = mutex.withLock { reader.transaction(id) }

    suspend fun submit(
        operationId: String,
        transactionId: String,
        action: String,
        patch: JsonObject,
        baseSnapshot: JsonObject?,
    ) = mutex.withLock { operations.submit(operationId, transactionId, action, patch, baseSnapshot) }

    suspend fun close() = mutex.withLock { db.close() }

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
            install: (ByteArray) -> ActualDatabase,
        ): ActualDirectBudget {
            val info = server.fileInfo(fileId)
            val groupId = info.groupId ?: throw ActualServerException("missing-group-id")
            if (info.encryptMeta != null) throw ActualServerException("encrypted-budget")
            val entries = ZipArchive.read(server.download(fileId))
            val data = entries["db.sqlite"] ?: throw ActualServerException("invalid-budget-archive")
            return open(install(data), server, fileId, groupId, now, newId, downloaded = true)
        }

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
}
