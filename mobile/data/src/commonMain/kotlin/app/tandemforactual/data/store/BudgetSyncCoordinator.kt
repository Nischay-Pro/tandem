package app.tandemforactual.data.store

import app.tandemforactual.data.database.OutboxOperationEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// WorkManager creates its own coordinator and Room instance in the app process.
// Serialize recovery and write finalization across those coordinator instances.
private val processSyncMutex = Mutex()

data class BudgetSyncSummary(
    val fullSnapshotApplied: Boolean,
    val changePagesApplied: Int,
    val changesApplied: Int,
    val writesCommitted: Int,
    val writesConflicted: Int,
    val writesRejected: Int,
    val writesUncertain: Int,
    val operationStatusErrorCode: String? = null,
)

/** Coordinates one local budget with the Tandem snapshot, change feed, and durable write journal. */
class BudgetSyncCoordinator(
    private val repository: LocalBudgetRepository,
    private val remote: BudgetSyncRemote,
) {
    /** Refreshes budget access before syncing writes, or checks one uncertain operation without sending writes. */
    suspend fun synchronize(
        budgetId: String,
        encryptionPassword: String? = null,
        sendQueuedWrites: Boolean = true,
        reconcileUncertainOperationId: String? = null,
    ): BudgetSyncSummary =
        processSyncMutex.withLock {
            try {
                require(reconcileUncertainOperationId == null || reconcileUncertainOperationId.isNotBlank()) {
                    "Operation ID must not be blank"
                }
                require(reconcileUncertainOperationId == null || !sendQueuedWrites) {
                    "A status-only check cannot send queued writes"
                }
                repository.recoverInterruptedWrites()
                val isOperationStatusCheck = reconcileUncertainOperationId != null
                if (!isOperationStatusCheck) repository.markSyncStarted(budgetId)
                // Verify current budget access before treating an operation 404 as safe to retry.
                val sync = refreshBudget(budgetId, encryptionPassword)
                val writes =
                    if (sendQueuedWrites || reconcileUncertainOperationId != null) {
                        synchronizeWrites(
                            budgetId,
                            encryptionPassword,
                            sync.generation,
                            reconcileUncertainOperationId = reconcileUncertainOperationId,
                            sendQueuedWrites = sendQueuedWrites,
                        )
                    } else {
                        WriteCounts()
                    }
                BudgetSyncSummary(
                    fullSnapshotApplied = sync.fullSnapshotApplied,
                    changePagesApplied = sync.changePagesApplied,
                    changesApplied = sync.changesApplied,
                    writesCommitted = writes.committed,
                    writesConflicted = writes.conflicted,
                    writesRejected = writes.rejected,
                    writesUncertain = writes.uncertain,
                    operationStatusErrorCode = writes.operationStatusErrorCode,
                )
            } catch (error: Exception) {
                if (error is CancellationException) {
                    if (reconcileUncertainOperationId == null) {
                        withContext(NonCancellable) {
                            runCatching { repository.markSyncFailed(budgetId, "sync-cancelled") }
                        }
                    }
                    throw error
                }
                if (reconcileUncertainOperationId == null) {
                    try {
                        repository.markSyncFailed(budgetId, error.syncErrorCode())
                    } catch (_: Exception) {
                        // Keep the original sync failure as the caller-visible error.
                    }
                }
                throw error
            }
        }

    private suspend fun refreshBudget(
        budgetId: String,
        encryptionPassword: String?,
    ): SyncPass {
        val state = repository.readSyncState(budgetId)
        val generation = state?.generation?.takeIf { it.isNotBlank() }
        val checkpointCursor = state?.cursor
        if (generation == null || checkpointCursor == null || state.status == BudgetSyncStatus.NEEDS_FULL_SYNC.name) {
            val snapshotGeneration = applySnapshot(budgetId, encryptionPassword)
            return SyncPass(fullSnapshotApplied = true, generation = snapshotGeneration)
        }

        var cursor: String = checkpointCursor
        var throughCursor: String? = null
        var pageCount = 0
        var changeCount = 0
        while (true) {
            val page = remote.readChanges(budgetId, generation, cursor, throughCursor, encryptionPassword)
            if (page.resetRequired || page.generation != generation) {
                val snapshotGeneration = applySnapshot(budgetId, encryptionPassword)
                return SyncPass(
                    fullSnapshotApplied = true,
                    changePagesApplied = pageCount,
                    changesApplied = changeCount,
                    generation = snapshotGeneration,
                )
            }
            validatePageProgress(page, budgetId, cursor, throughCursor)
            if (throughCursor == null) throughCursor = page.throughCursor
            val result =
                repository.applyChangePage(
                    BudgetChangePage(
                        budgetId = budgetId,
                        generation = page.generation,
                        fromCursor = cursor,
                        nextCursor = page.nextCursor,
                        changes = page.changes,
                        displaySettings = page.displaySettings,
                    ),
                )
            if (result == ChangePageResult.NEEDS_FULL_SYNC) {
                val snapshotGeneration = applySnapshot(budgetId, encryptionPassword)
                return SyncPass(
                    fullSnapshotApplied = true,
                    changePagesApplied = pageCount,
                    changesApplied = changeCount,
                    generation = snapshotGeneration,
                )
            }
            pageCount += 1
            changeCount += page.changes.size
            cursor = page.nextCursor
            if (!page.hasMore) break
        }
        return SyncPass(
            fullSnapshotApplied = false,
            changePagesApplied = pageCount,
            changesApplied = changeCount,
            generation = generation,
        )
    }

    private suspend fun applySnapshot(
        budgetId: String,
        encryptionPassword: String?,
    ): String {
        repeat(MAX_SNAPSHOT_ATTEMPTS) {
            val expectedCheckpoint = repository.readSyncCheckpoint(budgetId)
            var page =
                remote.readSnapshotPage(
                    budgetId = budgetId,
                    generation = null,
                    cursor = null,
                    afterType = null,
                    afterId = null,
                    limit = SNAPSHOT_PAGE_SIZE,
                    encryptionPassword = encryptionPassword,
                )
            if (page.budgetId != budgetId) {
                throw BudgetSyncProtocolException("Snapshot belongs to a different budget")
            }
            if (page.resetRequired) return@repeat
            validateSnapshotCheckpoint(page.generation, page.cursor)
            val generation = page.generation
            val cursor = page.cursor
            repository.beginPagedSnapshot(budgetId, generation, cursor, expectedCheckpoint)
            var displaySettings = page.displaySettings
            var afterType: String? = null
            var afterId: String? = null
            var previousType: String? = null
            var previousId: String? = null
            var resetRequired = false
            while (true) {
                if (page.budgetId != budgetId) {
                    throw BudgetSyncProtocolException("Snapshot page belongs to a different budget")
                }
                if (page.resetRequired || page.generation != generation || page.cursor != cursor) {
                    resetRequired = true
                    break
                }
                if (displaySettings == null) displaySettings = page.displaySettings
                var pagePreviousType = previousType
                var pagePreviousId = previousId
                for (record in page.records) {
                    if (record.generation != generation ||
                        pagePreviousType != null &&
                        (
                            record.recordType < pagePreviousType ||
                                (record.recordType == pagePreviousType && record.recordId <= pagePreviousId.orEmpty())
                        )
                    ) {
                        throw BudgetSyncProtocolException("Snapshot page records are out of order")
                    }
                    pagePreviousType = record.recordType
                    pagePreviousId = record.recordId
                }
                repository.stageSnapshotPage(budgetId, generation, cursor, page.records)
                previousType = pagePreviousType
                previousId = pagePreviousId
                if (!page.hasMore) {
                    if (page.nextAfterType != null || page.nextAfterId != null) {
                        throw BudgetSyncProtocolException("Final snapshot page contains a continuation key")
                    }
                    when (repository.finishPagedSnapshot(budgetId, generation, cursor, displaySettings)) {
                        FullSnapshotResult.APPLIED -> return generation
                        FullSnapshotResult.STALE_CHECKPOINT -> break
                    }
                }
                val nextType = page.nextAfterType
                val nextId = page.nextAfterId
                if (nextType == null ||
                    nextId.isNullOrBlank() ||
                    page.records.isEmpty() ||
                    previousType != nextType ||
                    previousId != nextId
                ) {
                    throw BudgetSyncProtocolException("Snapshot page continuation is invalid")
                }
                afterType = nextType
                afterId = nextId
                page =
                    remote.readSnapshotPage(
                        budgetId = budgetId,
                        generation = generation,
                        cursor = cursor,
                        afterType = afterType,
                        afterId = afterId,
                        limit = SNAPSHOT_PAGE_SIZE,
                        encryptionPassword = encryptionPassword,
                    )
            }
            if (resetRequired) return@repeat
        }
        throw BudgetSyncProtocolException("Snapshot could not be applied because the local checkpoint kept changing")
    }

    private fun validateSnapshotCheckpoint(
        generation: String,
        cursor: String,
    ) {
        if (!generation.matches(Regex("^[A-Za-z0-9._:-]{1,100}$")) || !cursor.isDecimalCursor()) {
            throw BudgetSyncProtocolException("Snapshot contains an invalid checkpoint")
        }
    }

    private fun validatePageProgress(
        page: RemoteBudgetChangePage,
        budgetId: String,
        cursor: String,
        expectedThroughCursor: String?,
    ) {
        if (page.budgetId != budgetId) {
            throw BudgetSyncProtocolException("Change page belongs to a different budget")
        }
        if (!page.currentCursor.isDecimalCursor() ||
            !page.throughCursor.isDecimalCursor() ||
            !page.nextCursor.isDecimalCursor() ||
            !cursor.isDecimalCursor()
        ) {
            throw BudgetSyncProtocolException("Change page contains an invalid cursor")
        }
        if (page.nextCursor.compareDecimalCursors(cursor) < 0 ||
            page.nextCursor.compareDecimalCursors(page.throughCursor) > 0 ||
            page.currentCursor.compareDecimalCursors(page.throughCursor) < 0
        ) {
            throw BudgetSyncProtocolException("Change page cursors are inconsistent")
        }
        if (expectedThroughCursor != null && page.throughCursor != expectedThroughCursor) {
            throw BudgetSyncProtocolException("A paginated change feed changed its high-water cursor")
        }
        if (!page.hasMore && page.nextCursor != page.throughCursor) {
            throw BudgetSyncProtocolException("Final change page did not reach its high-water cursor")
        }
        if (page.hasMore && page.nextCursor == cursor) {
            throw BudgetSyncProtocolException("Change page made no cursor progress")
        }
    }

    private suspend fun synchronizeWrites(
        budgetId: String,
        encryptionPassword: String?,
        generation: String,
        reconcileUncertainOperationId: String? = null,
        sendQueuedWrites: Boolean = true,
    ): WriteCounts {
        val counts = WriteCounts()
        val pendingOperations = repository.readPendingWrites(budgetId)
        val uncertainOperations =
            pendingOperations.filter {
                it.status == "uncertain" &&
                    (reconcileUncertainOperationId == null || it.operationId == reconcileUncertainOperationId)
            }
        uncertainOperations.forEach { operation ->
            val resolution = reconcileUncertain(operation, generation, encryptionPassword)
            counts.record(resolution.outcome)
            if (operation.operationId == reconcileUncertainOperationId) {
                counts.operationStatusErrorCode = resolution.errorCode
            }
        }

        if (!sendQueuedWrites) return counts

        while (true) {
            val operation = repository.claimNextWrite(budgetId) ?: break
            counts.record(sendClaimed(operation, generation, encryptionPassword))
        }
        return counts
    }

    private suspend fun sendClaimed(
        operation: OutboxOperationEntity,
        generation: String,
        encryptionPassword: String?,
    ): WriteOutcome {
        val submitted =
            try {
                remote.submitOperation(operation, encryptionPassword)
            } catch (error: Exception) {
                if (error is CancellationException) {
                    persistUncertainAfterCancellation(operation.operationId, "sync-cancelled-during-send")
                    throw error
                }
                val remoteError = error as? BudgetSyncRemoteException
                if (remoteError?.isRetryableBeforeSubmission == true) {
                    repository.returnWriteToQueue(operation.operationId, remoteError.errorCode.safeCode())
                    throw error
                }
                if (remoteError?.isDefinitiveClientError == true) {
                    repository.markWriteRejected(operation.operationId, error.syncErrorCode())
                    return WriteOutcome.REJECTED
                }
                return pollAfterUncertainSubmit(operation, generation, encryptionPassword, error)
            }

        val firstOutcome =
            try {
                applyOperationResult(operation, submitted, generation, encryptionPassword)
            } catch (error: Exception) {
                if (error is CancellationException) {
                    persistUncertainAfterCancellation(operation.operationId, "sync-cancelled-during-resolution")
                    throw error
                }
                return pollAfterUncertainSubmit(operation, generation, encryptionPassword, error)
            }
        if (firstOutcome != WriteOutcome.PENDING) return firstOutcome
        return pollAfterUncertainSubmit(operation, generation, encryptionPassword, null)
    }

    private suspend fun pollAfterUncertainSubmit(
        operation: OutboxOperationEntity,
        generation: String,
        encryptionPassword: String?,
        originalError: Exception?,
    ): WriteOutcome {
        val polled =
            try {
                remote.readOperation(operation.operationId)
            } catch (error: Exception) {
                if (error is CancellationException) {
                    persistUncertainAfterCancellation(operation.operationId, "sync-cancelled-during-poll")
                    throw error
                }
                repository.markWriteUncertain(operation.operationId, error.syncErrorCode())
                return WriteOutcome.UNCERTAIN
            }
        val outcome =
            try {
                applyOperationResult(operation, polled, generation, encryptionPassword)
            } catch (error: Exception) {
                if (error is CancellationException) {
                    persistUncertainAfterCancellation(operation.operationId, "sync-cancelled-during-resolution")
                    throw error
                }
                repository.markWriteUncertain(operation.operationId, error.syncErrorCode())
                return WriteOutcome.UNCERTAIN
            }
        if (outcome == WriteOutcome.PENDING) {
            repository.markWriteUncertain(
                operation.operationId,
                originalError?.syncErrorCode() ?: polled.errorCode?.safeCode() ?: "remote-operation-pending",
            )
            return WriteOutcome.UNCERTAIN
        }
        return outcome
    }

    private suspend fun reconcileUncertain(
        operation: OutboxOperationEntity,
        generation: String,
        encryptionPassword: String?,
    ): UncertainWriteResolution =
        try {
            val result = remote.readOperation(operation.operationId)
            val outcome = applyOperationResult(operation, result, generation, encryptionPassword)
            if (outcome == WriteOutcome.PENDING) {
                repository.markWriteUncertain(
                    operation.operationId,
                    result.errorCode?.safeCode() ?: "remote-operation-pending",
                )
                UncertainWriteResolution(WriteOutcome.UNCERTAIN)
            } else {
                UncertainWriteResolution(outcome)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            repository.markWriteUncertain(operation.operationId, error.syncErrorCode())
            UncertainWriteResolution(WriteOutcome.UNCERTAIN, error.syncErrorCode())
        }

    private suspend fun persistUncertainAfterCancellation(
        operationId: String,
        errorCode: String,
    ) {
        withContext(NonCancellable) {
            runCatching { repository.markWriteUncertain(operationId, errorCode) }
        }
    }

    private suspend fun applyOperationResult(
        local: OutboxOperationEntity,
        remoteResult: RemoteOperationResult,
        generation: String,
        encryptionPassword: String?,
    ): WriteOutcome {
        if (remoteResult.operationId != local.operationId ||
            remoteResult.budgetId != local.budgetId ||
            remoteResult.transactionId != local.recordId
        ) {
            throw BudgetSyncProtocolException("Operation response does not match its local outbox entry")
        }
        return when (remoteResult.status) {
            RemoteOperationStatus.QUEUED,
            RemoteOperationStatus.APPLYING,
            RemoteOperationStatus.UNCERTAIN,
            -> WriteOutcome.PENDING
            RemoteOperationStatus.COMMITTED -> {
                if (local.action == "DELETE") {
                    repository.markWriteCommitted(local.operationId, null)
                } else {
                    val transaction =
                        remote.readTransaction(local.budgetId, local.recordId, encryptionPassword)
                            ?: throw BudgetSyncProtocolException("Committed update is missing its remote transaction")
                    repository.markWriteCommitted(
                        local.operationId,
                        transaction.toRemoteRecord(local.budgetId, generation),
                    )
                }
                WriteOutcome.COMMITTED
            }
            RemoteOperationStatus.CONFLICT -> {
                val transaction = remote.readTransaction(local.budgetId, local.recordId, encryptionPassword)
                repository.markWriteConflict(
                    operationId = local.operationId,
                    errorCode = remoteResult.errorCode?.safeCode() ?: "remote-conflict",
                    remoteRecord = transaction?.toRemoteRecord(local.budgetId, generation),
                )
                WriteOutcome.CONFLICTED
            }
            RemoteOperationStatus.REJECTED -> {
                repository.markWriteRejected(
                    local.operationId,
                    remoteResult.errorCode?.safeCode() ?: "remote-rejected",
                )
                WriteOutcome.REJECTED
            }
        }
    }

    private fun RemoteTransactionRecord.toRemoteRecord(
        budgetId: String,
        generation: String,
    ): RemoteBudgetRecord =
        RemoteBudgetRecord(
            budgetId = budgetId,
            recordType = "transaction",
            recordId = transactionId,
            revision = revision,
            bodyJson = bodyJson,
            generation = generation,
            recordDate = projection.recordDate,
            accountId = projection.accountId,
            categoryId = projection.categoryId,
            payeeId = projection.payeeId,
            amountMinor = projection.amountMinor,
            budgetMonth = projection.budgetMonth,
            displayMetadata = displayMetadata,
        )

    private fun Exception.syncErrorCode(): String =
        when (this) {
            is BudgetSyncRemoteException -> errorCode.safeCode()
            is BudgetSyncProtocolException -> "invalid-sync-response"
            else -> "sync-request-failed"
        }

    private fun String.safeCode(): String =
        takeIf { length in 1..100 && all { character -> character.isLetterOrDigit() || character == '-' || character == '_' } }
            ?: "remote-error"

    private data class SyncPass(
        val fullSnapshotApplied: Boolean = false,
        val changePagesApplied: Int = 0,
        val changesApplied: Int = 0,
        val generation: String,
    )

    private data class UncertainWriteResolution(
        val outcome: WriteOutcome,
        val errorCode: String? = null,
    )

    private class WriteCounts {
        var committed = 0
        var conflicted = 0
        var rejected = 0
        var uncertain = 0
        var operationStatusErrorCode: String? = null

        fun record(outcome: WriteOutcome) {
            when (outcome) {
                WriteOutcome.COMMITTED -> committed += 1
                WriteOutcome.CONFLICTED -> conflicted += 1
                WriteOutcome.REJECTED -> rejected += 1
                WriteOutcome.UNCERTAIN -> uncertain += 1
                WriteOutcome.PENDING -> Unit
            }
        }
    }

    private enum class WriteOutcome {
        COMMITTED,
        CONFLICTED,
        REJECTED,
        UNCERTAIN,
        PENDING,
    }

    private companion object {
        const val MAX_SNAPSHOT_ATTEMPTS = 3
        const val SNAPSHOT_PAGE_SIZE = 2_000
    }
}

private fun String.compareDecimalCursors(other: String): Int {
    if (length != other.length) return length.compareTo(other.length)
    return compareTo(other)
}

private fun String.isDecimalCursor() =
    isNotEmpty() &&
        all { it in '0'..'9' } &&
        (this == "0" || first() != '0')
