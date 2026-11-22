package app.tandemforactual.data.store

import androidx.room3.withWriteTransaction
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.BudgetSnapshotProgressEntity
import app.tandemforactual.data.database.BudgetSnapshotRecordEntity
import app.tandemforactual.data.database.BudgetSyncStateEntity
import app.tandemforactual.data.database.RemoteBudgetRecordKey
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

suspend fun LocalBudgetRepository.markSyncStarted(budgetId: String) {
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

suspend fun LocalBudgetRepository.markSyncFailed(
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
suspend fun LocalBudgetRepository.applyFullSnapshot(
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

suspend fun LocalBudgetRepository.beginPagedSnapshot(
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

suspend fun LocalBudgetRepository.stageSnapshotPage(
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

suspend fun LocalBudgetRepository.finishPagedSnapshot(
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

internal suspend fun LocalBudgetRepository.storeBudgetDisplaySettings(
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
