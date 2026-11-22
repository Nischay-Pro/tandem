package app.tandemforactual.data.store

import androidx.room3.withWriteTransaction
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.BudgetSyncStateEntity
import kotlinx.serialization.json.put

suspend fun LocalBudgetRepository.applyChangePage(page: BudgetChangePage): ChangePageResult {
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

internal suspend fun LocalBudgetRepository.applyRemoteUpsert(
    record: RemoteBudgetRecord,
    preserveLocalProjection: Boolean = true,
) {
    val dao = database.budgetRecordDao()
    val current = dao.find(record.budgetId, record.recordType, record.recordId)
    dao.put(record.toEntity(current, preserveLocalProjection))
}

internal suspend fun LocalBudgetRepository.applyRemoteDelete(
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

internal suspend fun LocalBudgetRepository.markEditsForDeletedRecords(budgetId: String) {
    database.outboxDao().markEditsForDeletedRecords(budgetId, now())
}

private fun LocalBudgetRepository.validateChangePage(page: BudgetChangePage) {
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

internal fun LocalBudgetRepository.validateRemoteRecord(
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

internal fun RemoteBudgetRecord.toEntity(
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

internal fun RemoteBudgetRecord.toEntity(
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
