package app.tandemforactual.data.store

import androidx.room3.withWriteTransaction
import app.tandemforactual.data.database.OutboxOperationEntity
import kotlinx.serialization.json.put

suspend fun LocalBudgetRepository.readScheduleActionQueue(): List<OutboxOperationEntity> {
    pruneTerminalWrites()
    return database
        .outboxDao()
        .findByRecordType(SCHEDULE_ACTION_RECORD_TYPE)
        .filter { it.status in setOf("queued", "sending", "uncertain", "conflict", "rejected") }
}

suspend fun LocalBudgetRepository.enqueueScheduleAction(item: LocalScheduleActionOutboxItem): OutboxOperationEntity =
    database.withWriteTransaction {
        requireIdentifier(item.operationId, "operationId")
        requireIdentifier(item.budgetId, "budgetId")
        requireIdentifier(item.scheduleId, "scheduleId")
        requireIdentifier(item.action, "action")
        val outbox = database.outboxDao()
        val activeForSchedule =
            outbox
                .findByRecordType(SCHEDULE_ACTION_RECORD_TYPE)
                .filter {
                    it.budgetId == item.budgetId &&
                        it.recordId == item.scheduleId &&
                        it.status in setOf("queued", "sending", "uncertain", "conflict", "rejected")
                }
        activeForSchedule
            .firstOrNull { it.action == item.action && it.patchJson == item.payloadJson }
            ?.let { existing ->
                if (existing.status in setOf("queued", "sending")) return@withWriteTransaction existing
                if (existing.status != "conflict" || existing.lastErrorCode != "schedule_changed") {
                    throw LocalOperationConflictException()
                }
            }
        val staleDateConflict =
            activeForSchedule.firstOrNull {
                it.status == "conflict" &&
                    it.lastErrorCode == "schedule_changed" &&
                    it.action == item.action &&
                    item.action in setOf("post", "skip")
            }
        if (staleDateConflict != null) {
            val oldDate = scheduleActionExpectedDate(staleDateConflict.patchJson)
            val refreshedDate = scheduleActionExpectedDate(item.payloadJson)
            if (oldDate.isNullOrBlank() || refreshedDate.isNullOrBlank() || oldDate == refreshedDate) {
                throw LocalOperationConflictException()
            }
            outbox.put(staleDateConflict.copy(status = "discarded", updatedAtEpochMs = now()))
        }
        val operation =
            OutboxOperationEntity(
                operationId = item.operationId,
                idempotencyKey = "schedule-action:${item.operationId}",
                budgetId = item.budgetId,
                recordType = SCHEDULE_ACTION_RECORD_TYPE,
                recordId = item.scheduleId,
                action = item.action,
                baseRevision = null,
                baseSnapshotJson = null,
                patchJson = item.payloadJson,
                optimisticJson = null,
                optimisticRecordDate = null,
                optimisticAccountId = null,
                optimisticCategoryId = null,
                optimisticPayeeId = null,
                optimisticAmountMinor = null,
                optimisticBudgetMonth = null,
                status = LocalWriteStatus.QUEUED.name.lowercase(),
                attemptCount = 0,
                serverOperationId = null,
                lastErrorCode = null,
                createdAtEpochMs = item.createdAtEpochMs,
                updatedAtEpochMs = now(),
            )
        outbox.insert(operation)
        operation
    }

suspend fun LocalBudgetRepository.cancelQueuedScheduleAction(operationId: String): Boolean =
    database.withWriteTransaction {
        val outbox = database.outboxDao()
        val operation = outbox.find(operationId) ?: return@withWriteTransaction false
        if (operation.recordType != SCHEDULE_ACTION_RECORD_TYPE) return@withWriteTransaction false
        outbox.deleteQueuedOperation(operationId) == 1
    }

suspend fun LocalBudgetRepository.beginScheduleAction(operationId: String): Boolean =
    database.withWriteTransaction {
        val outbox = database.outboxDao()
        val operation = outbox.find(operationId) ?: return@withWriteTransaction false
        if (operation.recordType != SCHEDULE_ACTION_RECORD_TYPE || operation.status != "queued") {
            return@withWriteTransaction false
        }
        outbox.markSending(operationId, now()) == 1
    }

suspend fun LocalBudgetRepository.completeScheduleAction(operationId: String): OutboxOperationEntity =
    updateScheduleActionStatus(operationId, setOf("sending", "uncertain"), LocalWriteStatus.COMMITTED)

suspend fun LocalBudgetRepository.returnScheduleActionToQueue(
    operationId: String,
    errorCode: String,
): OutboxOperationEntity = updateScheduleActionStatus(operationId, setOf("sending"), LocalWriteStatus.QUEUED, errorCode)

suspend fun LocalBudgetRepository.markScheduleActionUncertain(
    operationId: String,
    errorCode: String,
): OutboxOperationEntity = updateScheduleActionStatus(operationId, setOf("sending"), LocalWriteStatus.UNCERTAIN, errorCode)

suspend fun LocalBudgetRepository.markScheduleActionConflict(
    operationId: String,
    errorCode: String,
): OutboxOperationEntity = updateScheduleActionStatus(operationId, setOf("sending"), LocalWriteStatus.CONFLICT, errorCode)

suspend fun LocalBudgetRepository.markScheduleActionRejected(
    operationId: String,
    errorCode: String,
): OutboxOperationEntity = updateScheduleActionStatus(operationId, setOf("queued", "sending"), LocalWriteStatus.REJECTED, errorCode)

suspend fun LocalBudgetRepository.retryScheduleActionCheck(operationId: String): OutboxOperationEntity =
    updateScheduleActionStatus(operationId, setOf("uncertain"), LocalWriteStatus.QUEUED)

private suspend fun LocalBudgetRepository.updateScheduleActionStatus(
    operationId: String,
    fromStatuses: Set<String>,
    status: LocalWriteStatus,
    errorCode: String? = null,
): OutboxOperationEntity =
    database.withWriteTransaction {
        val outbox = database.outboxDao()
        val operation = outbox.find(operationId) ?: throw NoSuchElementException("Schedule action not found")
        if (operation.recordType != SCHEDULE_ACTION_RECORD_TYPE || operation.status !in fromStatuses) {
            throw InvalidLocalWriteException("This schedule action cannot move to the requested state")
        }
        val updated =
            operation.copy(
                status = status.name.lowercase(),
                serverOperationId = if (status == LocalWriteStatus.COMMITTED) operation.operationId else operation.serverOperationId,
                lastErrorCode = errorCode,
                updatedAtEpochMs = now(),
            )
        outbox.put(updated)
        updated
    }
