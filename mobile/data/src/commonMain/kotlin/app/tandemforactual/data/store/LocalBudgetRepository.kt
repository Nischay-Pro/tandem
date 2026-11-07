package app.tandemforactual.data.store

import androidx.room3.withWriteTransaction
import app.tandemforactual.data.database.AccountDayAggregate
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.BudgetSnapshotProgressEntity
import app.tandemforactual.data.database.BudgetSnapshotRecordEntity
import app.tandemforactual.data.database.BudgetSyncStateEntity
import app.tandemforactual.data.database.CategoryMonthAggregate
import app.tandemforactual.data.database.MonthlyIncomeSpendingAggregate
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.database.RemoteBudgetRecordKey
import app.tandemforactual.data.database.SearchableBudgetTransaction
import app.tandemforactual.data.database.TandemDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

enum class LocalWriteAction {
    CREATE,
    UPDATE,
    DELETE,
}

enum class LocalWriteStatus {
    QUEUED,
    SENDING,
    UNCERTAIN,
    CONFLICT,
    COMMITTED,
    REJECTED,
    SUPERSEDED,
    DISCARDED,
}

enum class BudgetSyncStatus {
    IDLE,
    SYNCING,
    READY,
    NEEDS_FULL_SYNC,
    FAILED,
}

data class RemoteBudgetRecord(
    val budgetId: String,
    val recordType: String,
    val recordId: String,
    val revision: String,
    val bodyJson: String,
    val generation: String,
    val recordDate: String? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    val payeeId: String? = null,
    val amountMinor: Long? = null,
    val budgetMonth: String? = null,
    val displayMetadata: BudgetRecordDisplayMetadata? = null,
)

data class LocalRecordProjection(
    val recordDate: String?,
    val accountId: String?,
    val categoryId: String?,
    val payeeId: String?,
    val amountMinor: Long?,
    val budgetMonth: String?,
)

private fun parseDisplayMetadata(bodyJson: String?): BudgetRecordDisplayMetadata? =
    bodyJson?.let { json ->
        runCatching {
            val body = Json.parseToJsonElement(json).jsonObject
            BudgetRecordDisplayMetadata(
                displayName = body.displayString("name")?.takeIf { it.isNotBlank() },
                notes = body.displayString("notes")?.takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }

private data class TransactionSearchMetadata(
    val cleared: Boolean?,
    val reconciled: Boolean?,
    val approved: Boolean?,
    val matched: Boolean?,
    val transfer: Boolean?,
    val isParent: Boolean,
)

private fun parseTransactionSearchMetadata(bodyJson: String?): TransactionSearchMetadata? =
    bodyJson?.let { json ->
        runCatching {
            val body = Json.parseToJsonElement(json).jsonObject
            val legacyClearedState =
                body
                    .displayString("cleared")
                    ?.lowercase()
                    ?.takeUnless { it == "true" || it == "false" }
            TransactionSearchMetadata(
                cleared =
                    body.displayBooleanOrNull("cleared")
                        ?: when (legacyClearedState) {
                            "cleared", "reconciled" -> true
                            "uncleared" -> false
                            else -> null
                        },
                reconciled =
                    body.displayBooleanOrNull("reconciled") ?: (legacyClearedState == "reconciled").takeIf { legacyClearedState != null },
                approved = body.displayBooleanOrNull("approved"),
                matched = body.displayBooleanOrNull("matched"),
                transfer =
                    body.displayString("transfer_id") != null ||
                        body.displayString("transferId") != null ||
                        body.displayBooleanOrNull("is_transfer") == true ||
                        body.displayBooleanOrNull("isTransfer") == true,
                isParent = body.displayBooleanOrNull("is_parent") ?: body.displayBooleanOrNull("isParent") ?: false,
            )
        }.getOrNull()
    }

sealed interface BudgetChange {
    val budgetId: String
    val recordType: String
    val recordId: String

    data class Upsert(
        val record: RemoteBudgetRecord,
    ) : BudgetChange {
        override val budgetId: String get() = record.budgetId
        override val recordType: String get() = record.recordType
        override val recordId: String get() = record.recordId
    }

    data class Delete(
        override val budgetId: String,
        override val recordType: String,
        override val recordId: String,
        val revision: String?,
    ) : BudgetChange
}

data class BudgetChangePage(
    val budgetId: String,
    val generation: String,
    val fromCursor: String?,
    val nextCursor: String,
    val changes: List<BudgetChange>,
    val displaySettings: BudgetDisplaySettings? = null,
)

data class BudgetSyncCheckpoint(
    val generation: String,
    val cursor: String?,
)

enum class FullSnapshotResult {
    APPLIED,
    STALE_CHECKPOINT,
}

data class LocalWriteRequest(
    val operationId: String,
    val idempotencyKey: String,
    val budgetId: String,
    val recordType: String,
    val recordId: String,
    val action: LocalWriteAction,
    val baseRevision: String?,
    val baseSnapshotJson: String?,
    val patchJson: String,
    val optimisticJson: String?,
    val createdAtEpochMs: Long,
    val optimisticProjection: LocalRecordProjection? = null,
)

data class EnqueuedLocalWrite(
    val operation: OutboxOperationEntity,
    val replayed: Boolean,
    val replacedUnsent: Boolean,
)

enum class ChangePageResult {
    APPLIED,
    NEEDS_FULL_SYNC,
}

class LocalIdempotencyConflictException :
    IllegalStateException(
        "An idempotency key was reused with a different local write",
    )

class LocalOperationConflictException :
    IllegalStateException(
        "A write for this record is already in flight or needs resolution",
    )

class InvalidLocalWriteException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

class LocalBudgetRepository(
    private val database: TandemDatabase,
    private val now: () -> Long,
) {
    fun observeRecords(
        budgetId: String,
        recordType: String,
    ): Flow<List<BudgetRecordEntity>> = database.budgetRecordDao().observeVisible(budgetId, recordType).stabilized()

    fun observeSearchableTransactions(budgetId: String): Flow<List<SearchableBudgetTransaction>> =
        database.budgetRecordDao().observeSearchableTransactions(budgetId).stabilized()

    fun observeCategoryMonthAggregates(
        budgetId: String,
        fromDate: String?,
        throughDate: String?,
    ): Flow<List<CategoryMonthAggregate>> =
        database.budgetRecordDao().observeCategoryMonthAggregates(budgetId, fromDate, throughDate).stabilized()

    fun observeMonthlyIncomeAndSpending(
        budgetId: String,
        fromDate: String?,
        throughDate: String?,
    ): Flow<List<MonthlyIncomeSpendingAggregate>> =
        database.budgetRecordDao().observeMonthlyIncomeAndSpending(budgetId, fromDate, throughDate).stabilized()

    fun observeAccountDailyActivity(
        budgetId: String,
        throughDate: String,
    ): Flow<List<AccountDayAggregate>> = database.budgetRecordDao().observeAccountDailyActivity(budgetId, throughDate).stabilized()

    fun observeRecordIds(
        budgetId: String,
        recordType: String,
    ): Flow<List<String>> = database.budgetRecordDao().observeVisibleRecordIds(budgetId, recordType).stabilized()

    fun observeRecord(
        budgetId: String,
        recordType: String,
        recordId: String,
    ): Flow<BudgetRecordEntity?> = database.budgetRecordDao().observeVisibleRecord(budgetId, recordType, recordId).stabilized()

    fun observeRecentRecords(
        budgetId: String,
        recordType: String,
        limit: Int,
    ): Flow<List<BudgetRecordEntity>> = database.budgetRecordDao().observeRecentVisible(budgetId, recordType, limit).stabilized()

    fun observeRecentTransactionsForAccount(
        budgetId: String,
        accountId: String,
        limit: Int,
    ): Flow<List<BudgetRecordEntity>> {
        requireIdentifier(budgetId, "budgetId")
        requireIdentifier(accountId, "accountId")
        require(limit in 1..500) { "limit must be between 1 and 500" }
        return database.budgetRecordDao().observeRecentTransactionsForAccount(budgetId, accountId, limit).stabilized()
    }

    fun observeVisibleRecordCount(
        budgetId: String,
        recordType: String,
    ): Flow<Long> = database.budgetRecordDao().observeVisibleCount(budgetId, recordType).stabilized()

    fun observePendingWrites(budgetId: String): Flow<List<OutboxOperationEntity>> = database.outboxDao().observePending(budgetId)

    fun observeSyncState(budgetId: String): Flow<BudgetSyncStateEntity?> = database.budgetSyncStateDao().observe(budgetId)

    suspend fun findRecords(
        budgetId: String,
        recordType: String,
        accountId: String? = null,
        categoryId: String? = null,
        payeeId: String? = null,
        fromDate: String? = null,
        throughDate: String? = null,
    ): List<BudgetRecordEntity> =
        database.budgetRecordDao().findVisible(
            budgetId,
            recordType,
            accountId,
            categoryId,
            payeeId,
            fromDate,
            throughDate,
        )

    suspend fun findRecordIds(
        budgetId: String,
        recordType: String,
    ): List<String> {
        requireIdentifier(budgetId, "budgetId")
        requireIdentifier(recordType, "recordType")
        return database.budgetRecordDao().findVisibleRecordIds(budgetId, recordType)
    }

    suspend fun findRecentRecords(
        budgetId: String,
        recordType: String,
        limit: Int,
    ): List<BudgetRecordEntity> {
        requireIdentifier(budgetId, "budgetId")
        requireIdentifier(recordType, "recordType")
        require(limit in 1..500) { "limit must be between 1 and 500" }
        return database.budgetRecordDao().findRecentVisible(budgetId, recordType, limit)
    }

    suspend fun countVisibleRecords(
        budgetId: String,
        recordType: String,
    ): Long {
        requireIdentifier(budgetId, "budgetId")
        requireIdentifier(recordType, "recordType")
        return database.budgetRecordDao().countVisible(budgetId, recordType)
    }

    suspend fun findRecord(
        budgetId: String,
        recordType: String,
        recordId: String,
    ): BudgetRecordEntity? = database.budgetRecordDao().find(budgetId, recordType, recordId)

    suspend fun readSyncState(budgetId: String): BudgetSyncStateEntity? = database.budgetSyncStateDao().find(budgetId)

    /** Lists local snapshots with a completed sync checkpoint, newest successful sync first. */
    suspend fun findCachedBudgetIds(): List<String> = database.budgetSyncStateDao().findCachedBudgets().map { it.budgetId }

    suspend fun readBudgetDisplaySettings(budgetId: String): BudgetDisplaySettings? =
        database.budgetRecordDao().find(budgetId, "budget-settings", "display")?.remoteJson?.let { body ->
            runCatching {
                val settings = Json.parseToJsonElement(body).jsonObject
                BudgetDisplaySettings(
                    currencyCode = settings.displayString("currencyCode") ?: return@runCatching null,
                    currencySymbol = settings.displayString("currencySymbol") ?: return@runCatching null,
                    currencyFractionDigits = settings.displayInt("currencyFractionDigits") ?: return@runCatching null,
                    numberFormat = settings.displayString("numberFormat"),
                    hideFraction = settings.displayBoolean("hideFraction"),
                    currencySymbolPosition = settings.displayString("currencySymbolPosition"),
                    currencySpaceBetweenAmountAndSymbol = settings.displayBoolean("currencySpaceBetweenAmountAndSymbol"),
                    budgetType = settings.displayString("budgetType")?.takeIf { it == "tracking" } ?: "envelope",
                )
            }.getOrNull()
        }

    /** Capture this before requesting a remote snapshot and pass it back to [applyFullSnapshot]. */
    suspend fun readSyncCheckpoint(budgetId: String): BudgetSyncCheckpoint? {
        requireIdentifier(budgetId, "budgetId")
        return database.budgetSyncStateDao().find(budgetId)?.let { BudgetSyncCheckpoint(it.generation, it.cursor) }
    }

    suspend fun readPendingWrites(budgetId: String): List<OutboxOperationEntity> = database.outboxDao().findPending(budgetId)

    suspend fun findWrite(operationId: String): OutboxOperationEntity? {
        requireIdentifier(operationId, "operationId")
        return database.outboxDao().find(operationId)
    }

    suspend fun markSyncStarted(budgetId: String) {
        requireIdentifier(budgetId, "budgetId")
        database.withWriteTransaction {
            val old = database.budgetSyncStateDao().find(budgetId)
            database.budgetSyncStateDao().put(
                BudgetSyncStateEntity(
                    budgetId = budgetId,
                    generation = old?.generation ?: "",
                    cursor = old?.cursor,
                    status = BudgetSyncStatus.SYNCING.name,
                    lastAttemptAtEpochMs = now(),
                    lastSyncedAtEpochMs = old?.lastSyncedAtEpochMs,
                    lastErrorCode = null,
                ),
            )
        }
    }

    suspend fun markSyncFailed(
        budgetId: String,
        errorCode: String,
    ) {
        requireIdentifier(budgetId, "budgetId")
        requireIdentifier(errorCode, "errorCode")
        database.withWriteTransaction {
            val old = database.budgetSyncStateDao().find(budgetId)
            database.budgetSyncStateDao().put(
                BudgetSyncStateEntity(
                    budgetId = budgetId,
                    generation = old?.generation ?: "",
                    cursor = old?.cursor,
                    status = BudgetSyncStatus.FAILED.name,
                    lastAttemptAtEpochMs = now(),
                    lastSyncedAtEpochMs = old?.lastSyncedAtEpochMs,
                    lastErrorCode = errorCode,
                ),
            )
        }
    }

    /**
     * Applies a full snapshot only if the stored checkpoint still matches the value captured before the request.
     * This prevents a delayed snapshot from replacing changes already applied from a newer change page.
     */
    suspend fun applyFullSnapshot(
        budgetId: String,
        generation: String,
        cursor: String,
        expectedCheckpoint: BudgetSyncCheckpoint?,
        records: List<RemoteBudgetRecord>,
        displaySettings: BudgetDisplaySettings? = null,
    ): FullSnapshotResult {
        requireIdentifier(budgetId, "budgetId")
        requireIdentifier(generation, "generation")
        requireIdentifier(cursor, "cursor")
        val recordKeys = mutableSetOf<Pair<String, String>>()
        var generationMismatch = false
        var duplicateRecords = false
        records.forEach { record ->
            validateRemoteRecord(record, budgetId)
            if (record.generation != generation) generationMismatch = true
            if (!recordKeys.add(record.recordType to record.recordId)) duplicateRecords = true
        }
        if (generationMismatch) {
            throw InvalidLocalWriteException("A record generation must match its full snapshot")
        }
        if (duplicateRecords) {
            throw InvalidLocalWriteException("A full snapshot cannot contain duplicate records")
        }
        return database.withWriteTransaction {
            val syncStateDao = database.budgetSyncStateDao()
            val currentState = syncStateDao.find(budgetId)
            val currentCheckpoint = currentState?.let { BudgetSyncCheckpoint(it.generation, it.cursor) }
            if (currentCheckpoint != expectedCheckpoint) {
                return@withWriteTransaction FullSnapshotResult.STALE_CHECKPOINT
            }
            val recordDao = database.budgetRecordDao()
            val localOverlays = recordDao.findLocalOverlays(budgetId).associateBy { it.recordType to it.recordId }
            if (records.isNotEmpty()) {
                recordDao.putAll(
                    records.map { record ->
                        record.toEntity(localOverlays[record.recordType to record.recordId])
                    },
                )
            }
            recordDao
                .findRemoteRecordKeys(budgetId)
                .filterNot { (it.recordType to it.recordId) in recordKeys }
                .groupBy(RemoteBudgetRecordKey::recordType)
                .forEach { (recordType, staleKeys) ->
                    staleKeys.map(RemoteBudgetRecordKey::recordId).chunked(500).forEach { recordIds ->
                        recordDao.tombstoneRemoteRecords(budgetId, recordType, recordIds)
                    }
                }
            displaySettings?.let { storeBudgetDisplaySettings(budgetId, generation, it) }
            markEditsForDeletedRecords(budgetId)
            syncStateDao.put(
                BudgetSyncStateEntity(
                    budgetId = budgetId,
                    generation = generation,
                    cursor = cursor,
                    status = BudgetSyncStatus.READY.name,
                    lastAttemptAtEpochMs = now(),
                    lastSyncedAtEpochMs = now(),
                    lastErrorCode = null,
                ),
            )
            FullSnapshotResult.APPLIED
        }
    }

    suspend fun beginPagedSnapshot(
        budgetId: String,
        generation: String,
        cursor: String,
        expectedCheckpoint: BudgetSyncCheckpoint?,
    ) {
        requireIdentifier(budgetId, "budgetId")
        requireIdentifier(generation, "generation")
        requireIdentifier(cursor, "cursor")
        val snapshotKey = snapshotKey(generation, cursor)
        database.withWriteTransaction {
            val snapshotDao = database.budgetSnapshotDao()
            snapshotDao.deleteRecords(budgetId)
            snapshotDao.putProgress(
                BudgetSnapshotProgressEntity(
                    budgetId = budgetId,
                    snapshotKey = snapshotKey,
                    generation = generation,
                    cursor = cursor,
                    expectedGeneration = expectedCheckpoint?.generation,
                    expectedCursor = expectedCheckpoint?.cursor,
                ),
            )
        }
    }

    suspend fun stageSnapshotPage(
        budgetId: String,
        generation: String,
        cursor: String,
        records: List<RemoteBudgetRecord>,
    ) {
        requireIdentifier(budgetId, "budgetId")
        val snapshotKey = snapshotKey(generation, cursor)
        val identities = mutableSetOf<Pair<String, String>>()
        val staged =
            records.map { record ->
                validateRemoteRecord(record, budgetId)
                if (record.generation != generation) {
                    throw InvalidLocalWriteException("A record generation must match its snapshot page")
                }
                if (!identities.add(record.recordType to record.recordId)) {
                    throw InvalidLocalWriteException("A snapshot page cannot contain duplicate records")
                }
                record.toEntity(localJson = null, localDeleted = false).let { entity ->
                    BudgetSnapshotRecordEntity(
                        budgetId = entity.budgetId,
                        snapshotKey = snapshotKey,
                        recordType = entity.recordType,
                        recordId = entity.recordId,
                        remoteJson = entity.remoteJson ?: "{}",
                        remoteRevision = entity.remoteRevision ?: "",
                        generation = entity.syncGeneration,
                        recordDate = entity.recordDate,
                        accountId = entity.accountId,
                        categoryId = entity.categoryId,
                        payeeId = entity.payeeId,
                        amountMinor = entity.amountMinor,
                        budgetMonth = entity.budgetMonth,
                        displayName = entity.displayName,
                        notes = entity.notes,
                        cleared = entity.cleared,
                        reconciled = entity.reconciled,
                        approved = entity.approved,
                        matched = entity.matched,
                        transfer = entity.transfer,
                        isParent = entity.isParent,
                    )
                }
            }
        database.withWriteTransaction {
            val progress = database.budgetSnapshotDao().findProgress(budgetId)
            if (progress?.snapshotKey != snapshotKey) {
                throw InvalidLocalWriteException("Snapshot page does not match the active snapshot")
            }
            if (staged.isNotEmpty()) database.budgetSnapshotDao().putAll(staged)
        }
    }

    suspend fun finishPagedSnapshot(
        budgetId: String,
        generation: String,
        cursor: String,
        displaySettings: BudgetDisplaySettings?,
    ): FullSnapshotResult {
        requireIdentifier(budgetId, "budgetId")
        val snapshotKey = snapshotKey(generation, cursor)
        return database.withWriteTransaction {
            val syncStateDao = database.budgetSyncStateDao()
            val snapshotDao = database.budgetSnapshotDao()
            val progress = snapshotDao.findProgress(budgetId)
            if (progress?.snapshotKey != snapshotKey) {
                throw InvalidLocalWriteException("Snapshot finalization does not match the active snapshot")
            }
            val currentState = syncStateDao.find(budgetId)
            val currentCheckpoint = currentState?.let { BudgetSyncCheckpoint(it.generation, it.cursor) }
            val expectedCheckpoint =
                progress.expectedGeneration?.let {
                    BudgetSyncCheckpoint(it, progress.expectedCursor)
                }
            if (currentCheckpoint != expectedCheckpoint) {
                snapshotDao.deleteRecords(budgetId)
                snapshotDao.deleteProgress(budgetId)
                return@withWriteTransaction FullSnapshotResult.STALE_CHECKPOINT
            }

            val recordDao = database.budgetRecordDao()
            val localOverlays = recordDao.findLocalOverlays(budgetId).associateBy { it.recordType to it.recordId }
            var afterType: String? = null
            var afterId: String? = null
            while (true) {
                val page = snapshotDao.findPage(budgetId, snapshotKey, afterType, afterId, SNAPSHOT_LOCAL_BATCH_SIZE)
                if (page.isEmpty()) break
                recordDao.putAll(
                    page.map { staged ->
                        RemoteBudgetRecord(
                            budgetId = staged.budgetId,
                            recordType = staged.recordType,
                            recordId = staged.recordId,
                            revision = staged.remoteRevision,
                            bodyJson = staged.remoteJson,
                            generation = staged.generation,
                            recordDate = staged.recordDate,
                            accountId = staged.accountId,
                            categoryId = staged.categoryId,
                            payeeId = staged.payeeId,
                            amountMinor = staged.amountMinor,
                            budgetMonth = staged.budgetMonth,
                            displayMetadata = BudgetRecordDisplayMetadata(staged.displayName, staged.notes),
                        ).toEntity(localOverlays[staged.recordType to staged.recordId])
                    },
                )
                val last = page.last()
                afterType = last.recordType
                afterId = last.recordId
            }
            recordDao.tombstoneRemoteRecordsMissingFromSnapshot(budgetId, snapshotKey)
            displaySettings?.let { storeBudgetDisplaySettings(budgetId, generation, it) }
            markEditsForDeletedRecords(budgetId)
            syncStateDao.put(
                BudgetSyncStateEntity(
                    budgetId = budgetId,
                    generation = generation,
                    cursor = cursor,
                    status = BudgetSyncStatus.READY.name,
                    lastAttemptAtEpochMs = now(),
                    lastSyncedAtEpochMs = now(),
                    lastErrorCode = null,
                ),
            )
            snapshotDao.deleteRecords(budgetId)
            snapshotDao.deleteProgress(budgetId)
            FullSnapshotResult.APPLIED
        }
    }

    suspend fun applyChangePage(page: BudgetChangePage): ChangePageResult {
        validateChangePage(page)
        return database.withWriteTransaction {
            val dao = database.budgetSyncStateDao()
            val old = dao.find(page.budgetId)
            val generationChanged = old?.generation?.isNotEmpty() == true && old.generation != page.generation
            val cursorChanged = old != null && old.cursor != page.fromCursor
            val firstPageHasCursor = old == null && page.fromCursor != null
            if (generationChanged || cursorChanged || firstPageHasCursor) {
                dao.put(
                    BudgetSyncStateEntity(
                        budgetId = page.budgetId,
                        generation = old?.generation ?: page.generation,
                        cursor = old?.cursor,
                        status = BudgetSyncStatus.NEEDS_FULL_SYNC.name,
                        lastAttemptAtEpochMs = now(),
                        lastSyncedAtEpochMs = old?.lastSyncedAtEpochMs,
                        lastErrorCode = if (generationChanged) "generation-changed" else "cursor-mismatch",
                    ),
                )
                return@withWriteTransaction ChangePageResult.NEEDS_FULL_SYNC
            }

            page.changes.forEach { change ->
                when (change) {
                    is BudgetChange.Upsert -> applyRemoteUpsert(change.record)
                    is BudgetChange.Delete -> applyRemoteDelete(change, page.generation)
                }
            }
            page.displaySettings?.let { storeBudgetDisplaySettings(page.budgetId, page.generation, it) }
            dao.put(
                BudgetSyncStateEntity(
                    budgetId = page.budgetId,
                    generation = page.generation,
                    cursor = page.nextCursor,
                    status = BudgetSyncStatus.READY.name,
                    lastAttemptAtEpochMs = now(),
                    lastSyncedAtEpochMs = now(),
                    lastErrorCode = null,
                ),
            )
            ChangePageResult.APPLIED
        }
    }

    suspend fun enqueueWrite(request: LocalWriteRequest): EnqueuedLocalWrite {
        validateWriteRequest(request)
        return database.withWriteTransaction {
            val outbox = database.outboxDao()
            val existingKey = outbox.findByIdempotencyKey(request.idempotencyKey)
            if (existingKey != null) {
                if (matchesRequest(existingKey, request)) {
                    return@withWriteTransaction EnqueuedLocalWrite(existingKey, replayed = true, replacedUnsent = false)
                }
                throw LocalIdempotencyConflictException()
            }
            if (outbox.find(request.operationId) != null) {
                throw LocalIdempotencyConflictException()
            }

            val records = database.budgetRecordDao()
            val current = records.find(request.budgetId, request.recordType, request.recordId)
            validateWriteBase(request, current)
            val unsent = outbox.findUnsentForRecord(request.budgetId, request.recordType, request.recordId)
            val blocker = outbox.findBlockingForRecord(request.budgetId, request.recordType, request.recordId)
            val resolvedConflict =
                blocker?.takeIf {
                    it.status == LocalWriteStatus.CONFLICT.name.lowercase() &&
                        it.operationId != unsent?.operationId &&
                        request.action != LocalWriteAction.CREATE
                }
            if (blocker != null && blocker.operationId != unsent?.operationId && resolvedConflict == null) {
                throw LocalOperationConflictException()
            }
            if (unsent != null && !sameBase(unsent, request)) {
                throw LocalOperationConflictException()
            }

            val operation = request.toEntity()
            if (unsent != null) {
                outbox.deleteUnsent(unsent.operationId)
            }
            resolvedConflict?.let {
                outbox.put(
                    it.copy(
                        status = LocalWriteStatus.SUPERSEDED.name.lowercase(),
                        lastErrorCode = "superseded-by-resolution",
                        updatedAtEpochMs = now(),
                    ),
                )
            }
            outbox.insert(operation)
            applyLocalOverlay(records, current, request)
            EnqueuedLocalWrite(operation, replayed = false, replacedUnsent = unsent != null)
        }
    }

    suspend fun claimNextWrite(budgetId: String): OutboxOperationEntity? {
        requireIdentifier(budgetId, "budgetId")
        return database.withWriteTransaction {
            val outbox = database.outboxDao()
            val next = outbox.findNextQueued(budgetId) ?: return@withWriteTransaction null
            val claimTime = now()
            if (outbox.markSending(next.operationId, claimTime) != 1) {
                return@withWriteTransaction null
            }
            outbox.find(next.operationId)
        }
    }

    suspend fun recoverInterruptedWrites(): Int =
        database.withWriteTransaction {
            database.outboxDao().markInterruptedSendsUncertain(now())
        }

    suspend fun returnWriteToQueue(
        operationId: String,
        errorCode: String,
    ): OutboxOperationEntity =
        database.withWriteTransaction {
            requireIdentifier(errorCode, "errorCode")
            val outbox = database.outboxDao()
            val operation = outbox.find(operationId) ?: throw NoSuchElementException("Local write not found")
            if (operation.status != LocalWriteStatus.SENDING.name.lowercase()) {
                throw InvalidLocalWriteException("Only a sending write can be returned to the queue")
            }
            val queued =
                operation.copy(
                    status = LocalWriteStatus.QUEUED.name.lowercase(),
                    lastErrorCode = errorCode,
                    updatedAtEpochMs = now(),
                )
            outbox.put(queued)
            queued
        }

    suspend fun retryUncertainWrite(operationId: String): OutboxOperationEntity =
        database.withWriteTransaction {
            val outbox = database.outboxDao()
            val operation = outbox.find(operationId) ?: throw NoSuchElementException("Local write not found")
            if (operation.status != LocalWriteStatus.UNCERTAIN.name.lowercase()) {
                throw InvalidLocalWriteException("Only an uncertain write can be explicitly retried")
            }
            val queued =
                operation.copy(
                    status = LocalWriteStatus.QUEUED.name.lowercase(),
                    lastErrorCode = null,
                    updatedAtEpochMs = now(),
                )
            outbox.put(queued)
            queued
        }

    suspend fun discardWrite(operationId: String): OutboxOperationEntity =
        database.withWriteTransaction {
            val outbox = database.outboxDao()
            val operation = outbox.find(operationId) ?: throw NoSuchElementException("Local write not found")
            if (operation.status !in setOf("queued", "uncertain", "conflict", "rejected")) {
                throw InvalidLocalWriteException("This write is not in a discardable state")
            }
            val discarded =
                operation.copy(
                    status = LocalWriteStatus.DISCARDED.name.lowercase(),
                    lastErrorCode = "discarded-by-user",
                    updatedAtEpochMs = now(),
                )
            outbox.put(discarded)
            if (outbox.findBlockingForRecord(operation.budgetId, operation.recordType, operation.recordId) == null) {
                restoreRemoteOverlay(operation)
            }
            discarded
        }

    suspend fun markWriteUncertain(
        operationId: String,
        errorCode: String,
    ): OutboxOperationEntity = updateOperationStatus(operationId, LocalWriteStatus.UNCERTAIN, errorCode)

    suspend fun markWriteRejected(
        operationId: String,
        errorCode: String,
    ): OutboxOperationEntity =
        database.withWriteTransaction {
            requireIdentifier(errorCode, "errorCode")
            val outbox = database.outboxDao()
            val operation = outbox.find(operationId) ?: throw NoSuchElementException("Local write not found")
            requireInFlight(operation)
            val rejected =
                operation.copy(
                    status = LocalWriteStatus.REJECTED.name.lowercase(),
                    lastErrorCode = errorCode,
                    updatedAtEpochMs = now(),
                )
            outbox.put(rejected)
            restoreRemoteOverlay(operation)
            rejected
        }

    suspend fun markWriteConflict(
        operationId: String,
        errorCode: String,
        remoteRecord: RemoteBudgetRecord?,
    ): OutboxOperationEntity =
        database.withWriteTransaction {
            requireIdentifier(errorCode, "errorCode")
            val outbox = database.outboxDao()
            val operation = outbox.find(operationId) ?: throw NoSuchElementException("Local write not found")
            requireInFlight(operation)
            if (remoteRecord != null) {
                validateRemoteRecord(remoteRecord, operation.budgetId)
                if (remoteRecord.recordType != operation.recordType || remoteRecord.recordId != operation.recordId) {
                    throw InvalidLocalWriteException("A conflict returned a different record")
                }
                applyRemoteUpsert(remoteRecord)
            } else {
                applyRemoteDelete(
                    BudgetChange.Delete(
                        operation.budgetId,
                        operation.recordType,
                        operation.recordId,
                        revision = null,
                    ),
                    database
                        .budgetSyncStateDao()
                        .find(operation.budgetId)
                        ?.generation
                        .orEmpty(),
                )
            }
            val updated =
                operation.copy(
                    status = LocalWriteStatus.CONFLICT.name.lowercase(),
                    lastErrorCode = errorCode,
                    updatedAtEpochMs = now(),
                )
            outbox.put(updated)
            updated
        }

    suspend fun markWriteCommitted(
        operationId: String,
        remoteRecord: RemoteBudgetRecord?,
    ): OutboxOperationEntity =
        database.withWriteTransaction {
            val outbox = database.outboxDao()
            val operation = outbox.find(operationId) ?: throw NoSuchElementException("Local write not found")
            requireInFlight(operation)
            if (operation.action == LocalWriteAction.DELETE.name) {
                if (remoteRecord != null) {
                    throw InvalidLocalWriteException("A committed delete cannot return a live record")
                }
                applyRemoteDelete(
                    BudgetChange.Delete(
                        operation.budgetId,
                        operation.recordType,
                        operation.recordId,
                        revision = null,
                    ),
                    database
                        .budgetSyncStateDao()
                        .find(operation.budgetId)
                        ?.generation
                        .orEmpty(),
                )
            } else {
                if (remoteRecord == null) {
                    throw InvalidLocalWriteException("A committed create or update must return a live record")
                }
                validateRemoteRecord(remoteRecord, operation.budgetId)
                if (remoteRecord.recordType != operation.recordType || remoteRecord.recordId != operation.recordId) {
                    throw InvalidLocalWriteException("A committed write returned a different record")
                }
                applyRemoteUpsert(remoteRecord, preserveLocalProjection = false)
            }
            database.budgetRecordDao().clearLocalOverlay(
                operation.budgetId,
                operation.recordType,
                operation.recordId,
            )
            val updated =
                operation.copy(
                    status = LocalWriteStatus.COMMITTED.name.lowercase(),
                    serverOperationId = operation.serverOperationId ?: operation.operationId,
                    lastErrorCode = null,
                    updatedAtEpochMs = now(),
                )
            outbox.put(updated)
            updated
        }

    private suspend fun updateOperationStatus(
        operationId: String,
        status: LocalWriteStatus,
        errorCode: String,
    ): OutboxOperationEntity =
        database.withWriteTransaction {
            requireIdentifier(errorCode, "errorCode")
            val outbox = database.outboxDao()
            val operation = outbox.find(operationId) ?: throw NoSuchElementException("Local write not found")
            requireInFlight(operation)
            val updated =
                operation.copy(
                    status = status.name.lowercase(),
                    lastErrorCode = errorCode,
                    updatedAtEpochMs = now(),
                )
            outbox.put(updated)
            updated
        }

    private suspend fun restoreRemoteOverlay(operation: OutboxOperationEntity) {
        val records = database.budgetRecordDao()
        val current = records.find(operation.budgetId, operation.recordType, operation.recordId) ?: return
        val remoteMetadata = parseDisplayMetadata(current.remoteJson)
        val searchMetadata = parseTransactionSearchMetadata(current.remoteJson)
        val projection =
            if (current.remoteDeleted) {
                null
            } else {
                current.remoteJson?.let { Json.parseToJsonElement(it).jsonObject.toProjection() }
            }
        records.put(
            current.copy(
                localJson = null,
                localDeleted = false,
                recordDate = projection?.recordDate,
                accountId = projection?.accountId,
                categoryId = projection?.categoryId,
                payeeId = projection?.payeeId,
                amountMinor = projection?.amountMinor,
                budgetMonth = projection?.budgetMonth,
                displayName = remoteMetadata?.displayName,
                notes = remoteMetadata?.notes,
                cleared = searchMetadata?.cleared,
                reconciled = searchMetadata?.reconciled,
                approved = searchMetadata?.approved,
                matched = searchMetadata?.matched,
                transfer = searchMetadata?.transfer,
                isParent = searchMetadata?.isParent ?: false,
            ),
        )
    }

    private fun JsonObject.toProjection(): LocalRecordProjection =
        LocalRecordProjection(
            recordDate = firstString("date", "recordDate"),
            accountId = firstString("account", "accountId"),
            categoryId = firstString("category", "categoryId"),
            payeeId = firstString("payee", "payeeId"),
            amountMinor = firstLong("amount", "amountMinor"),
            budgetMonth = firstString("month", "budgetMonth"),
        )

    private fun JsonObject.firstString(vararg names: String): String? =
        names.firstNotNullOfOrNull { name ->
            (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
        }

    private fun JsonObject.firstLong(vararg names: String): Long? =
        names.firstNotNullOfOrNull { name ->
            (this[name] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
        }

    private fun requireInFlight(operation: OutboxOperationEntity) {
        if (operation.status != "sending" && operation.status != "uncertain") {
            throw InvalidLocalWriteException("Only sending or uncertain writes can be finalized")
        }
    }

    private suspend fun applyRemoteUpsert(
        record: RemoteBudgetRecord,
        preserveLocalProjection: Boolean = true,
    ) {
        val dao = database.budgetRecordDao()
        val current = dao.find(record.budgetId, record.recordType, record.recordId)
        dao.put(record.toEntity(current, preserveLocalProjection))
    }

    private suspend fun applyRemoteDelete(
        change: BudgetChange.Delete,
        generation: String,
    ) {
        val dao = database.budgetRecordDao()
        val current = dao.find(change.budgetId, change.recordType, change.recordId)
        if (current == null) {
            dao.insertTombstone(
                BudgetRecordEntity(
                    budgetId = change.budgetId,
                    recordType = change.recordType,
                    recordId = change.recordId,
                    remoteJson = null,
                    remoteRevision = change.revision,
                    localJson = null,
                    localDeleted = false,
                    remoteDeleted = true,
                    syncGeneration = generation,
                    recordDate = null,
                    accountId = null,
                    categoryId = null,
                    payeeId = null,
                    amountMinor = null,
                    budgetMonth = null,
                ),
            )
        } else {
            dao.applyTombstone(
                change.budgetId,
                change.recordType,
                change.recordId,
                change.revision,
                generation,
            )
        }
        if (change.recordType == "transaction") {
            database.outboxDao().markEditsForDeletedRecord(change.budgetId, change.recordId, now())
        }
    }

    private suspend fun markEditsForDeletedRecords(budgetId: String) {
        database.outboxDao().markEditsForDeletedRecords(budgetId, now())
    }

    private suspend fun validateWriteBase(
        request: LocalWriteRequest,
        current: BudgetRecordEntity?,
    ) {
        if (request.action == LocalWriteAction.CREATE) {
            if (current != null && !current.remoteDeleted && current.remoteJson != null) {
                throw InvalidLocalWriteException("A record with this ID already exists")
            }
            return
        }
        if (current == null || current.remoteDeleted || current.remoteJson == null) {
            throw InvalidLocalWriteException("A remote base record is required for this write")
        }
        if (current.remoteRevision != request.baseRevision) {
            throw InvalidLocalWriteException("The write base revision does not match the cached remote record")
        }
    }

    private suspend fun applyLocalOverlay(
        dao: app.tandemforactual.data.database.BudgetRecordDao,
        current: BudgetRecordEntity?,
        request: LocalWriteRequest,
    ) {
        val localDeleted = request.action == LocalWriteAction.DELETE
        val projection = request.optimisticProjection
        val metadata = request.optimisticJson?.let(::parseDisplayMetadata)
        val searchMetadata = request.optimisticJson?.takeIf { request.recordType == "transaction" }?.let(::parseTransactionSearchMetadata)
        val entity =
            current?.copy(
                localJson = request.optimisticJson,
                localDeleted = localDeleted,
                recordDate = if (projection == null) current.recordDate else projection.recordDate,
                accountId = if (projection == null) current.accountId else projection.accountId,
                categoryId = if (projection == null) current.categoryId else projection.categoryId,
                payeeId = if (projection == null) current.payeeId else projection.payeeId,
                amountMinor = if (projection == null) current.amountMinor else projection.amountMinor,
                budgetMonth = if (projection == null) current.budgetMonth else projection.budgetMonth,
                displayName = if (request.optimisticJson == null) current.displayName else metadata?.displayName,
                notes = if (request.optimisticJson == null) current.notes else metadata?.notes,
                cleared = if (request.optimisticJson == null) current.cleared else searchMetadata?.cleared,
                reconciled = if (request.optimisticJson == null) current.reconciled else searchMetadata?.reconciled,
                approved = if (request.optimisticJson == null) current.approved else searchMetadata?.approved,
                matched = if (request.optimisticJson == null) current.matched else searchMetadata?.matched,
                transfer = if (request.optimisticJson == null) current.transfer else searchMetadata?.transfer,
            ) ?: BudgetRecordEntity(
                budgetId = request.budgetId,
                recordType = request.recordType,
                recordId = request.recordId,
                remoteJson = null,
                remoteRevision = null,
                localJson = request.optimisticJson,
                localDeleted = localDeleted,
                remoteDeleted = false,
                syncGeneration = "",
                recordDate = projection?.recordDate,
                accountId = projection?.accountId,
                categoryId = projection?.categoryId,
                payeeId = projection?.payeeId,
                amountMinor = projection?.amountMinor,
                budgetMonth = projection?.budgetMonth,
                displayName = metadata?.displayName,
                notes = metadata?.notes,
                cleared = searchMetadata?.cleared,
                reconciled = searchMetadata?.reconciled,
                approved = searchMetadata?.approved,
                matched = searchMetadata?.matched,
                transfer = searchMetadata?.transfer,
            )
        dao.put(entity)
    }

    private fun validateWriteRequest(request: LocalWriteRequest) {
        requireIdentifier(request.operationId, "operationId")
        requireIdentifier(request.idempotencyKey, "idempotencyKey")
        requireIdentifier(request.budgetId, "budgetId")
        requireIdentifier(request.recordType, "recordType")
        requireIdentifier(request.recordId, "recordId")
        validateJsonObject(request.patchJson, "patchJson")
        when (request.action) {
            LocalWriteAction.CREATE -> {
                if (request.baseRevision != null || request.baseSnapshotJson != null) {
                    throw InvalidLocalWriteException("Creates cannot include a remote base")
                }
                validateJsonObject(request.optimisticJson, "optimisticJson")
                requireOptimisticProjection(request)
            }
            LocalWriteAction.UPDATE -> {
                requireIdentifier(request.baseRevision, "baseRevision")
                validateJsonObject(request.baseSnapshotJson, "baseSnapshotJson")
                validateJsonObject(request.optimisticJson, "optimisticJson")
                requireOptimisticProjection(request)
            }
            LocalWriteAction.DELETE -> {
                requireIdentifier(request.baseRevision, "baseRevision")
                validateJsonObject(request.baseSnapshotJson, "baseSnapshotJson")
                if (request.optimisticJson != null) {
                    throw InvalidLocalWriteException("Deletes cannot include an optimistic record")
                }
                if (request.optimisticProjection != null) {
                    throw InvalidLocalWriteException("Deletes cannot include an optimistic projection")
                }
            }
        }
    }

    private fun requireOptimisticProjection(request: LocalWriteRequest) {
        if (request.optimisticProjection == null) {
            throw InvalidLocalWriteException("Creates and updates require an optimistic index projection")
        }
    }

    private fun validateChangePage(page: BudgetChangePage) {
        requireIdentifier(page.budgetId, "budgetId")
        requireIdentifier(page.generation, "generation")
        requireIdentifier(page.nextCursor, "nextCursor")
        page.changes.forEach { change ->
            requireIdentifier(change.budgetId, "change.budgetId")
            requireIdentifier(change.recordType, "change.recordType")
            requireIdentifier(change.recordId, "change.recordId")
            if (change.budgetId != page.budgetId) {
                throw InvalidLocalWriteException("A change page cannot contain another budget")
            }
            if (change is BudgetChange.Upsert) {
                validateRemoteRecord(change.record, page.budgetId)
                if (change.record.generation != page.generation) {
                    throw InvalidLocalWriteException("A record generation must match its change page")
                }
            }
        }
    }

    private fun validateRemoteRecord(
        record: RemoteBudgetRecord,
        expectedBudgetId: String,
    ) {
        requireIdentifier(record.budgetId, "record.budgetId")
        requireIdentifier(record.recordType, "record.recordType")
        requireIdentifier(record.recordId, "record.recordId")
        requireIdentifier(record.revision, "record.revision")
        requireIdentifier(record.generation, "record.generation")
        if (record.budgetId != expectedBudgetId) {
            throw InvalidLocalWriteException("A record cannot be written to another budget")
        }
        validateJsonObject(record.bodyJson, "record.bodyJson")
    }

    private fun validateJsonObject(
        value: String?,
        name: String,
    ): JsonObject {
        if (value == null) {
            throw InvalidLocalWriteException("$name is required")
        }
        return try {
            Json.parseToJsonElement(value).jsonObject
        } catch (error: SerializationException) {
            throw InvalidLocalWriteException("$name must contain a JSON object", error)
        } catch (error: IllegalArgumentException) {
            throw InvalidLocalWriteException("$name must contain a JSON object", error)
        }
    }

    private fun requireIdentifier(
        value: String?,
        name: String,
    ) {
        if (value.isNullOrBlank() || value != value.trim()) {
            throw InvalidLocalWriteException("$name must be a non-empty, trimmed value")
        }
    }

    private fun sameBase(
        operation: OutboxOperationEntity,
        request: LocalWriteRequest,
    ): Boolean =
        operation.baseRevision == request.baseRevision &&
            operation.baseSnapshotJson == request.baseSnapshotJson &&
            operation.budgetId == request.budgetId &&
            operation.recordType == request.recordType &&
            operation.recordId == request.recordId

    private fun matchesRequest(
        operation: OutboxOperationEntity,
        request: LocalWriteRequest,
    ): Boolean =
        operation.operationId == request.operationId &&
            operation.budgetId == request.budgetId &&
            operation.recordType == request.recordType &&
            operation.recordId == request.recordId &&
            operation.action == request.action.name &&
            operation.baseRevision == request.baseRevision &&
            operation.baseSnapshotJson == request.baseSnapshotJson &&
            operation.patchJson == request.patchJson &&
            operation.optimisticJson == request.optimisticJson &&
            operation.optimisticRecordDate == request.optimisticProjection?.recordDate &&
            operation.optimisticAccountId == request.optimisticProjection?.accountId &&
            operation.optimisticCategoryId == request.optimisticProjection?.categoryId &&
            operation.optimisticPayeeId == request.optimisticProjection?.payeeId &&
            operation.optimisticAmountMinor == request.optimisticProjection?.amountMinor &&
            operation.optimisticBudgetMonth == request.optimisticProjection?.budgetMonth

    private fun LocalWriteRequest.toEntity(): OutboxOperationEntity =
        OutboxOperationEntity(
            operationId = operationId,
            idempotencyKey = idempotencyKey,
            budgetId = budgetId,
            recordType = recordType,
            recordId = recordId,
            action = action.name,
            baseRevision = baseRevision,
            baseSnapshotJson = baseSnapshotJson,
            patchJson = patchJson,
            optimisticJson = optimisticJson,
            optimisticRecordDate = optimisticProjection?.recordDate,
            optimisticAccountId = optimisticProjection?.accountId,
            optimisticCategoryId = optimisticProjection?.categoryId,
            optimisticPayeeId = optimisticProjection?.payeeId,
            optimisticAmountMinor = optimisticProjection?.amountMinor,
            optimisticBudgetMonth = optimisticProjection?.budgetMonth,
            status = LocalWriteStatus.QUEUED.name.lowercase(),
            attemptCount = 0,
            serverOperationId = null,
            lastErrorCode = null,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = createdAtEpochMs,
        )

    private fun RemoteBudgetRecord.toEntity(
        localJson: String?,
        localDeleted: Boolean,
    ): BudgetRecordEntity {
        val metadata =
            localJson?.let(::parseDisplayMetadata)
                ?: displayMetadata
                ?: parseDisplayMetadata(bodyJson)
        val searchMetadata = parseTransactionSearchMetadata(localJson ?: bodyJson).takeIf { recordType == "transaction" }
        return BudgetRecordEntity(
            budgetId = budgetId,
            recordType = recordType,
            recordId = recordId,
            remoteJson = bodyJson,
            remoteRevision = revision,
            localJson = localJson,
            localDeleted = localDeleted,
            remoteDeleted = false,
            syncGeneration = generation,
            recordDate = recordDate,
            accountId = accountId,
            categoryId = categoryId,
            payeeId = payeeId,
            amountMinor = amountMinor,
            budgetMonth = budgetMonth,
            displayName = metadata?.displayName,
            notes = metadata?.notes,
            cleared = searchMetadata?.cleared,
            reconciled = searchMetadata?.reconciled,
            approved = searchMetadata?.approved,
            matched = searchMetadata?.matched,
            transfer = searchMetadata?.transfer,
            isParent = parseTransactionSearchMetadata(bodyJson)?.isParent ?: false,
        )
    }

    private fun RemoteBudgetRecord.toEntity(
        current: BudgetRecordEntity?,
        preserveLocalProjection: Boolean = true,
    ): BudgetRecordEntity {
        val remoteEntity = toEntity(localJson = current?.localJson, localDeleted = current?.localDeleted ?: false)
        if (!preserveLocalProjection || current?.localJson == null) return remoteEntity
        return remoteEntity.copy(
            recordDate = current.recordDate,
            accountId = current.accountId,
            categoryId = current.categoryId,
            payeeId = current.payeeId,
            amountMinor = current.amountMinor,
            budgetMonth = current.budgetMonth,
        )
    }

    private suspend fun storeBudgetDisplaySettings(
        budgetId: String,
        generation: String,
        settings: BudgetDisplaySettings,
    ) {
        val body =
            buildJsonObject {
                put("currencyCode", settings.currencyCode)
                put("currencySymbol", settings.currencySymbol)
                put("currencyFractionDigits", settings.currencyFractionDigits)
                put("numberFormat", settings.numberFormat?.let { JsonPrimitive(it) } ?: JsonNull)
                put("hideFraction", settings.hideFraction)
                put("currencySymbolPosition", settings.currencySymbolPosition?.let { JsonPrimitive(it) } ?: JsonNull)
                put("currencySpaceBetweenAmountAndSymbol", settings.currencySpaceBetweenAmountAndSymbol)
                put("budgetType", settings.budgetType)
            }.toString()
        database.budgetRecordDao().put(
            BudgetRecordEntity(
                budgetId = budgetId,
                recordType = "budget-settings",
                recordId = "display",
                remoteJson = body,
                remoteRevision = null,
                localJson = null,
                localDeleted = false,
                remoteDeleted = false,
                syncGeneration = generation,
                recordDate = null,
                accountId = null,
                categoryId = null,
                payeeId = null,
                amountMinor = null,
                budgetMonth = null,
            ),
        )
    }
}

private const val SNAPSHOT_LOCAL_BATCH_SIZE = 500

private fun snapshotKey(
    generation: String,
    cursor: String,
): String = "$generation:$cursor"

private fun JsonObject.displayString(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it != JsonNull }?.contentOrNull

private fun JsonObject.displayBooleanOrNull(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.let { value ->
        value.booleanOrNull ?: when (value.contentOrNull?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

private fun JsonObject.displayInt(name: String): Int? = (this[name] as? JsonPrimitive)?.longOrNull?.takeIf { it in 0..6 }?.toInt()

private fun JsonObject.displayBoolean(name: String): Boolean =
    (this[name] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull == "true") } ?: false

@OptIn(FlowPreview::class)
private fun <T> Flow<T>.stabilized(): Flow<T> =
    debounce(75)
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
