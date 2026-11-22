package app.tandemforactual.data.store

import androidx.room3.withWriteTransaction
import app.tandemforactual.data.database.OutboxOperationEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

suspend fun LocalBudgetRepository.markWriteUncertain(
    operationId: String,
    errorCode: String,
): OutboxOperationEntity = updateOperationStatus(operationId, LocalWriteStatus.UNCERTAIN, errorCode)

suspend fun LocalBudgetRepository.markWriteRejected(
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

suspend fun LocalBudgetRepository.markWriteConflict(
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

suspend fun LocalBudgetRepository.markWriteCommitted(
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

private suspend fun LocalBudgetRepository.updateOperationStatus(
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

internal suspend fun LocalBudgetRepository.restoreRemoteOverlay(operation: OutboxOperationEntity) {
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

private fun LocalBudgetRepository.requireInFlight(operation: OutboxOperationEntity) {
    if (operation.status != "sending" && operation.status != "uncertain") {
        throw InvalidLocalWriteException("Only sending or uncertain writes can be finalized")
    }
}
