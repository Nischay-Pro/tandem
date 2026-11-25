package app.tandemforactual.data.store

import androidx.room3.withWriteTransaction
import app.tandemforactual.data.database.OutboxOperationEntity
import kotlinx.serialization.json.put

suspend fun LocalBudgetRepository.enqueueWrite(request: LocalWriteRequest): EnqueuedLocalWrite {
    validateWriteRequest(request)
    return database.withWriteTransaction { enqueueWriteInTransaction(request) }
}

suspend fun LocalBudgetRepository.enqueueWrites(requests: List<LocalWriteRequest>): List<EnqueuedLocalWrite> {
    requests.forEach(::validateWriteRequest)
    require(requests.map { it.operationId }.distinct().size == requests.size) { "operation IDs must be unique" }
    require(requests.map { Triple(it.budgetId, it.recordType, it.recordId) }.distinct().size == requests.size) {
        "a batch can contain only one write per record"
    }
    return database.withWriteTransaction {
        requests.map { request -> enqueueWriteInTransaction(request) }
    }
}

private suspend fun LocalBudgetRepository.enqueueWriteInTransaction(request: LocalWriteRequest): EnqueuedLocalWrite {
    val outbox = database.outboxDao()
    val existingKey = outbox.findByIdempotencyKey(request.idempotencyKey)
    if (existingKey != null) {
        if (matchesRequest(existingKey, request)) {
            return EnqueuedLocalWrite(existingKey, replayed = true, replacedUnsent = false)
        }
        throw LocalIdempotencyConflictException()
    }
    if (outbox.find(request.operationId) != null) {
        throw LocalIdempotencyConflictException()
    }

    val records = database.budgetRecordDao()
    val current = records.find(request.budgetId, request.recordType, request.recordId)
    validateWriteBase(request, current)
    val mergeableQueued = outbox.findMergeableQueuedForRecord(request.budgetId, request.recordType, request.recordId)
    val blocker = outbox.findBlockingForRecord(request.budgetId, request.recordType, request.recordId)
    val resolvedConflict =
        blocker?.takeIf {
            it.status == LocalWriteStatus.CONFLICT.name.lowercase() &&
                it.operationId == request.supersedesConflictOperationId &&
                request.action != LocalWriteAction.CREATE
        }
    if (request.supersedesConflictOperationId != null && resolvedConflict == null) {
        throw LocalOperationConflictException()
    }
    val blockerOnlyUploading = blocker?.status == LocalWriteStatus.QUEUED.name.lowercase() && blocker.serverOperationId != null
    if (blocker != null && blocker.operationId != mergeableQueued?.operationId && resolvedConflict == null && !blockerOnlyUploading) {
        throw LocalOperationConflictException()
    }

    val requestedOperation = request.toEntity()
    if (mergeableQueued != null) {
        val actionCanBeConsolidated =
            when (mergeableQueued.action) {
                LocalWriteAction.CREATE.name -> request.action == LocalWriteAction.CREATE
                LocalWriteAction.UPDATE.name -> request.action in setOf(LocalWriteAction.UPDATE, LocalWriteAction.DELETE)
                LocalWriteAction.DELETE.name -> request.action == LocalWriteAction.DELETE
                else -> false
            }
        if (!actionCanBeConsolidated) throw LocalOperationConflictException()
        val operation =
            requestedOperation.copy(
                operationId = mergeableQueued.operationId,
                idempotencyKey = mergeableQueued.idempotencyKey,
                attemptCount = mergeableQueued.attemptCount,
                createdAtEpochMs = mergeableQueued.createdAtEpochMs,
            )
        outbox.put(operation)
        applyLocalOverlay(records, current, request)
        return EnqueuedLocalWrite(operation, replayed = false, replacedUnsent = true)
    }

    val operation = requestedOperation
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
    return EnqueuedLocalWrite(operation, replayed = false, replacedUnsent = false)
}

suspend fun LocalBudgetRepository.claimNextWrite(budgetId: String): OutboxOperationEntity? {
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

/** Marks every in-flight write as uncertain. Call only when no sender in this process can still own one. */
suspend fun LocalBudgetRepository.recoverInterruptedWrites(): Int =
    database.withWriteTransaction {
        database.outboxDao().markInterruptedSendsUncertain(now())
    }

/**
 * Recovers writes that the sync coordinator sends while holding its process-wide lock.
 *
 * Budget mutations and schedule actions are sent by their own queues outside that lock, so a
 * `sending` row of those types can belong to a request that is still in flight. Their queues
 * recover them once when they load, before sending anything.
 */
suspend fun LocalBudgetRepository.recoverInterruptedSyncWrites(): Int =
    database.withWriteTransaction {
        database.outboxDao().markInterruptedSendsUncertainExceptRecordTypes(QUEUE_OWNED_RECORD_TYPES, now())
    }

suspend fun LocalBudgetRepository.recoverInterruptedBudgetMutations(): Int =
    database.withWriteTransaction {
        database.outboxDao().markInterruptedSendsUncertainForRecordTypes(listOf(BUDGET_MUTATION_RECORD_TYPE), now())
    }

suspend fun LocalBudgetRepository.recoverInterruptedScheduleActions(): Int =
    database.withWriteTransaction {
        database.outboxDao().markInterruptedSendsUncertainForRecordTypes(listOf(SCHEDULE_ACTION_RECORD_TYPE), now())
    }

/**
 * Puts a sending write back in the queue. [appliedLocally] means the budget already holds it and only the upload is
 * left, so later edits must not merge into it and it can no longer be discarded.
 */
suspend fun LocalBudgetRepository.returnWriteToQueue(
    operationId: String,
    errorCode: String,
    appliedLocally: Boolean = false,
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
                serverOperationId = operation.serverOperationId ?: operation.operationId.takeIf { appliedLocally },
                lastErrorCode = errorCode,
                updatedAtEpochMs = now(),
            )
        outbox.put(queued)
        queued
    }

suspend fun LocalBudgetRepository.retryUncertainWrite(operationId: String): OutboxOperationEntity =
    database.withWriteTransaction {
        val outbox = database.outboxDao()
        val operation = outbox.find(operationId) ?: throw NoSuchElementException("Local write not found")
        if (operation.status != LocalWriteStatus.UNCERTAIN.name.lowercase()) {
            throw InvalidLocalWriteException("Only an uncertain write can be explicitly retried")
        }
        val queued =
            operation.copy(
                status = LocalWriteStatus.QUEUED.name.lowercase(),
                serverOperationId = operation.serverOperationId ?: operation.operationId,
                lastErrorCode = null,
                updatedAtEpochMs = now(),
            )
        outbox.put(queued)
        queued
    }

suspend fun LocalBudgetRepository.discardWrite(operationId: String): OutboxOperationEntity =
    database.withWriteTransaction {
        val outbox = database.outboxDao()
        val operation = outbox.find(operationId) ?: throw NoSuchElementException("Local write not found")
        if (operation.status !in setOf("queued", "uncertain", "conflict", "rejected")) {
            throw InvalidLocalWriteException("This write is not in a discardable state")
        }
        if (operation.status == "queued" && operation.serverOperationId != null) {
            throw InvalidLocalWriteException("This write is already in the budget and only waiting to upload")
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
