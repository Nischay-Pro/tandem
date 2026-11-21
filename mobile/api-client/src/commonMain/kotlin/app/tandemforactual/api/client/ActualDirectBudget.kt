package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ActualBudgetEdits
import app.tandemforactual.data.actual.ActualBudgetReader
import app.tandemforactual.data.actual.ActualDatabase
import app.tandemforactual.data.actual.ActualMessageStore
import app.tandemforactual.data.actual.ActualSnapshot
import app.tandemforactual.data.actual.ActualSyncEngine
import app.tandemforactual.data.actual.ActualSyncResult
import app.tandemforactual.data.actual.ActualTransactionOperations
import app.tandemforactual.data.actual.ActualWriter
import app.tandemforactual.data.actual.OperationOutcome
import app.tandemforactual.data.actual.ZipArchive
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.BudgetSyncProtocolException
import app.tandemforactual.data.store.FullSnapshotResult
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.RemoteBudgetSnapshot
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

    suspend fun sync() = mutex.withLock { synchronize() }

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
        mutex.withLock {
            synchronize()
            runSchedules(today)
            val sync = synchronize()
            BudgetSyncJson.projectDirectSnapshot(reader.read(today), groupId, cacheGeneration, sync.lastSyncedTimestamp)
        }

    suspend fun snapshot(today: String) = mutex.withLock { reader.read(today) }

    /** Refreshes a staging database for comparison without uploading its history or advancing its write checkpoint. */
    suspend fun preview(today: String) = mutex.withLock { receiveProjection(today).first }

    /** Initializes a cache for a new direct budget with no previous outbox or gateway data. */
    internal suspend fun initializeMobileCache(
        today: String,
        staging: LocalBudgetRepository,
    ) = mutex.withLock {
        staging.requireFreshDirectCache(groupId)
        val (snapshot, cursor) = receiveProjection(today)
        saveInitialProjection(snapshot, cursor, staging)
    }

    /** Seeds an isolated mobile cache only after the staged Actual records match the gateway. */
    suspend fun stageMobileCache(
        today: String,
        gateway: RemoteBudgetSnapshot,
        staging: LocalBudgetRepository,
    ): ActualBudgetParity =
        mutex.withLock {
            require(gateway.budgetId == groupId) { "Choose the same budget on the Actual server and the gateway." }
            staging.requireFreshDirectCache(groupId)
            val (snapshot, cursor) = receiveProjection(today)
            val parity = snapshot.compareWithGateway(gateway)
            if (parity.matches) {
                saveInitialProjection(snapshot, cursor, staging)
            }
            parity
        }

    private suspend fun saveInitialProjection(
        snapshot: ActualSnapshot,
        cursor: String,
        staging: LocalBudgetRepository,
    ) {
        val projection = BudgetSyncJson.projectDirectSnapshot(snapshot, groupId, cacheGeneration, cursor)
        val result =
            staging.applyFullSnapshot(
                groupId,
                projection.generation,
                projection.cursor,
                expectedCheckpoint = null,
                projection.records,
                projection.displaySettings,
            )
        check(result == FullSnapshotResult.APPLIED) { "The staged cache changed during direct setup." }
    }

    private suspend fun receiveProjection(today: String): Pair<ActualSnapshot, String> {
        val sync = syncEngine.fullSync(clock, null, receiveOnly = true)
        return reader.read(today) to sync.lastSyncedTimestamp
    }

    suspend fun transaction(id: String) = mutex.withLock { reader.transaction(id) }

    /** Applies a budget edit against freshly synced state, then uploads it, as the gateway's maintenance routes did. */
    suspend fun <T> edit(
        today: String,
        block: (ActualBudgetEdits) -> T,
    ): T =
        mutex.withLock {
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
    ) = mutex.withLock { operations.submit(operationId, transactionId, action, patch, baseSnapshot) }

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

    internal suspend fun findOperation(operationId: String): OperationOutcome? = mutex.withLock { operations.find(operationId) }

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
            expectedGroupId: String? = null,
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

internal suspend fun LocalBudgetRepository.requireFreshDirectCache(budgetId: String) {
    check(readSyncCheckpoint(budgetId) == null && budgetWrites(budgetId).isEmpty()) {
        "Direct setup requires a fresh cache with an empty outbox."
    }
}
