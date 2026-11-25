package app.tandemforactual.data.store

import androidx.room3.withWriteTransaction
import app.tandemforactual.data.database.OutboxOperationEntity
import kotlinx.serialization.json.put

suspend fun LocalBudgetRepository.readBudgetMutationQueue(): LocalBudgetMutationQueueSnapshot {
    pruneTerminalWrites()
    val outbox = database.outboxDao()
    val rows = outbox.findByRecordType(BUDGET_MUTATION_RECORD_TYPE)
    val metadata = outbox.find(BUDGET_MUTATION_METADATA_OPERATION_ID)?.patchJson
    return LocalBudgetMutationQueueSnapshot(
        items =
            rows.filter {
                it.status in
                    setOf(
                        LocalWriteStatus.QUEUED.name.lowercase(),
                        LocalWriteStatus.UNCERTAIN.name.lowercase(),
                        LocalWriteStatus.CONFLICT.name.lowercase(),
                    )
            },
        metadataJson = metadata,
    )
}

/**
 * Whether a payee, category or account this budget's transactions may use is still waiting to be created. Their
 * writes must wait for it, or Actual rejects them for pointing at a record it doesn't have yet.
 */
suspend fun LocalBudgetRepository.hasPendingRecordCreate(budgetId: String): Boolean =
    database.outboxDao().findByRecordType(BUDGET_MUTATION_RECORD_TYPE).any {
        it.budgetId == budgetId && it.action in RECORD_CREATE_ACTIONS && it.status in UNSENT_STATUSES
    }

private val RECORD_CREATE_ACTIONS = setOf("CREATE_CATEGORY", "CREATE_PAYEE", "CREATE_ACCOUNT")
private val UNSENT_STATUSES = setOf("queued", "sending", "uncertain", "conflict")

suspend fun LocalBudgetRepository.replaceBudgetMutationQueue(
    items: List<LocalBudgetMutationOutboxItem>,
    metadataJson: String,
    supersededOperationIds: Set<String> = emptySet(),
) {
    require(items.map { it.operationId }.distinct().size == items.size) { "budget mutation operation IDs must be unique" }
    items.forEach { item ->
        requireIdentifier(item.operationId, "operationId")
        requireIdentifier(item.budgetId, "budgetId")
        requireIdentifier(item.action, "action")
        requireIdentifier(item.recordId, "recordId")
    }
    database.withWriteTransaction {
        val outbox = database.outboxDao()
        val existing = outbox.findByRecordType(BUDGET_MUTATION_RECORD_TYPE)
        val requestedIds = items.mapTo(mutableSetOf()) { it.operationId }
        items.forEach { item ->
            val previous = outbox.find(item.operationId)
            val operation = queuedBudgetMutationOperation(item)
            if (previous == null || previous.status == LocalWriteStatus.QUEUED.name.lowercase()) {
                outbox.put(operation)
            }
        }

        existing.filterNot { it.operationId in requestedIds }.forEach { previous ->
            if (previous.operationId in supersededOperationIds) {
                require(previous.status in setOf("conflict", "rejected", "uncertain")) {
                    "Only a reviewed budget mutation can be superseded"
                }
                outbox.put(
                    previous.copy(
                        status = LocalWriteStatus.SUPERSEDED.name.lowercase(),
                        lastErrorCode = null,
                        updatedAtEpochMs = now(),
                    ),
                )
            } else {
                outbox.deleteQueuedOperation(previous.operationId)
            }
        }

        val metadata = budgetMutationMetadataOperation(metadataJson)
        outbox.put(metadata)
    }
}

suspend fun LocalBudgetRepository.beginBudgetMutation(operationId: String): Boolean =
    database.withWriteTransaction {
        requireIdentifier(operationId, "operationId")
        val outbox = database.outboxDao()
        val operation = outbox.find(operationId) ?: return@withWriteTransaction false
        if (operation.recordType != BUDGET_MUTATION_RECORD_TYPE || operation.status != "queued") {
            return@withWriteTransaction false
        }
        outbox.markSending(operationId, now()) == 1
    }

suspend fun LocalBudgetRepository.completeBudgetMutation(operationId: String): OutboxOperationEntity =
    updateBudgetMutationStatus(
        operationId = operationId,
        fromStatuses = setOf("sending"),
        status = LocalWriteStatus.COMMITTED,
    )

suspend fun LocalBudgetRepository.completeBudgetMutationAfterConfirmation(operationId: String): OutboxOperationEntity =
    updateBudgetMutationStatus(
        operationId = operationId,
        fromStatuses = setOf("sending", "uncertain", "conflict", "rejected"),
        status = LocalWriteStatus.COMMITTED,
    )

suspend fun LocalBudgetRepository.markBudgetMutationUncertain(
    operationId: String,
    errorCode: String,
): OutboxOperationEntity =
    updateBudgetMutationStatus(
        operationId = operationId,
        fromStatuses = setOf("sending"),
        status = LocalWriteStatus.UNCERTAIN,
        errorCode = errorCode,
    )

suspend fun LocalBudgetRepository.markBudgetMutationConflict(
    operationId: String,
    errorCode: String,
): OutboxOperationEntity =
    updateBudgetMutationStatus(
        operationId = operationId,
        fromStatuses = setOf("sending"),
        status = LocalWriteStatus.CONFLICT,
        errorCode = errorCode,
    )

suspend fun LocalBudgetRepository.markBudgetMutationRejected(
    operationId: String,
    errorCode: String,
): OutboxOperationEntity =
    updateBudgetMutationStatus(
        operationId = operationId,
        fromStatuses = setOf("sending"),
        status = LocalWriteStatus.REJECTED,
        errorCode = errorCode,
    )

suspend fun LocalBudgetRepository.returnBudgetMutationToQueue(
    operationId: String,
    errorCode: String,
): OutboxOperationEntity =
    updateBudgetMutationStatus(
        operationId = operationId,
        fromStatuses = setOf("sending"),
        status = LocalWriteStatus.QUEUED,
        errorCode = errorCode,
    )

suspend fun LocalBudgetRepository.retryBudgetMutationAfterReview(operationId: String): OutboxOperationEntity =
    updateBudgetMutationStatus(
        operationId = operationId,
        fromStatuses = setOf("uncertain", "conflict"),
        status = LocalWriteStatus.QUEUED,
    )

private suspend fun LocalBudgetRepository.updateBudgetMutationStatus(
    operationId: String,
    fromStatuses: Set<String>,
    status: LocalWriteStatus,
    errorCode: String? = null,
): OutboxOperationEntity =
    database.withWriteTransaction {
        requireIdentifier(operationId, "operationId")
        errorCode?.let { requireIdentifier(it, "errorCode") }
        val outbox = database.outboxDao()
        val operation = outbox.find(operationId) ?: throw NoSuchElementException("Budget mutation not found")
        if (operation.recordType != BUDGET_MUTATION_RECORD_TYPE || operation.status !in fromStatuses) {
            throw InvalidLocalWriteException("This budget mutation cannot move to the requested state")
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

private fun LocalBudgetRepository.queuedBudgetMutationOperation(item: LocalBudgetMutationOutboxItem): OutboxOperationEntity =
    OutboxOperationEntity(
        operationId = item.operationId,
        idempotencyKey = "budget-mutation:${item.operationId}",
        budgetId = item.budgetId,
        recordType = BUDGET_MUTATION_RECORD_TYPE,
        recordId = item.recordId,
        action = item.action,
        baseRevision = null,
        baseSnapshotJson = null,
        patchJson = item.patchJson,
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

private fun LocalBudgetRepository.budgetMutationMetadataOperation(metadataJson: String): OutboxOperationEntity =
    OutboxOperationEntity(
        operationId = BUDGET_MUTATION_METADATA_OPERATION_ID,
        idempotencyKey = BUDGET_MUTATION_METADATA_IDEMPOTENCY_KEY,
        budgetId = BUDGET_MUTATION_METADATA_BUDGET_ID,
        recordType = BUDGET_MUTATION_METADATA_RECORD_TYPE,
        recordId = BUDGET_MUTATION_METADATA_RECORD_ID,
        action = "METADATA",
        baseRevision = null,
        baseSnapshotJson = null,
        patchJson = metadataJson,
        optimisticJson = null,
        optimisticRecordDate = null,
        optimisticAccountId = null,
        optimisticCategoryId = null,
        optimisticPayeeId = null,
        optimisticAmountMinor = null,
        optimisticBudgetMonth = null,
        status = LocalWriteStatus.COMMITTED.name.lowercase(),
        attemptCount = 0,
        serverOperationId = null,
        lastErrorCode = null,
        createdAtEpochMs = now(),
        updatedAtEpochMs = now(),
    )
