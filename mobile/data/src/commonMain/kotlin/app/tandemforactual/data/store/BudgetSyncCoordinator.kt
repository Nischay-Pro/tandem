package app.tandemforactual.data.store

import app.tandemforactual.data.database.OutboxOperationEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class BudgetSyncSummary(
    val fullSnapshotApplied: Boolean,
    val changePagesApplied: Int,
    val changesApplied: Int,
    val writesCommitted: Int,
    val writesConflicted: Int,
    val writesRejected: Int,
    val writesUncertain: Int,
)

/** Coordinates one local budget with the Tandem snapshot, change feed, and durable write journal. */
class BudgetSyncCoordinator(
    private val repository: LocalBudgetRepository,
    private val remote: BudgetSyncRemote,
) {
    private val mutex = Mutex()

    /** Reconciles pending remote writes once and returns after the change feed is current. */
    suspend fun synchronize(
        budgetId: String,
        encryptionPassword: String? = null,
        sendQueuedWrites: Boolean = true,
    ): BudgetSyncSummary =
        mutex.withLock {
            try {
                repository.recoverInterruptedWrites()
                repository.markSyncStarted(budgetId)
                val sync = refreshBudget(budgetId, encryptionPassword)
                val writes =
                    if (sendQueuedWrites) {
                        synchronizeWrites(budgetId, encryptionPassword, sync.generation)
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
                )
            } catch (error: Exception) {
                if (error is CancellationException) {
                    withContext(NonCancellable) {
                        runCatching { repository.markSyncFailed(budgetId, "sync-cancelled") }
                    }
                    throw error
                }
                try {
                    repository.markSyncFailed(budgetId, error.syncErrorCode())
                } catch (_: Exception) {
                    // Keep the original sync failure as the caller-visible error.
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
            val snapshot = remote.readSnapshot(budgetId, encryptionPassword)
            if (snapshot.budgetId != budgetId) {
                throw BudgetSyncProtocolException("Snapshot belongs to a different budget")
            }
            when (
                repository.applyFullSnapshot(
                    budgetId = budgetId,
                    generation = snapshot.generation,
                    cursor = snapshot.cursor,
                    expectedCheckpoint = expectedCheckpoint,
                    records = snapshot.records,
                    displaySettings = snapshot.displaySettings,
                )
            ) {
                FullSnapshotResult.APPLIED -> return snapshot.generation
                FullSnapshotResult.STALE_CHECKPOINT -> Unit
            }
        }
        throw BudgetSyncProtocolException("Snapshot could not be applied because the local checkpoint kept changing")
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
    ): WriteCounts {
        val counts = WriteCounts()
        repository
            .readPendingWrites(budgetId)
            .filter { it.status == "uncertain" }
            .forEach { operation ->
                counts.record(reconcileUncertain(operation, generation, encryptionPassword))
            }

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
    ): WriteOutcome =
        try {
            val result = remote.readOperation(operation.operationId)
            val outcome = applyOperationResult(operation, result, generation, encryptionPassword)
            if (outcome == WriteOutcome.PENDING) {
                repository.markWriteUncertain(
                    operation.operationId,
                    result.errorCode?.safeCode() ?: "remote-operation-pending",
                )
                WriteOutcome.UNCERTAIN
            } else {
                outcome
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            repository.markWriteUncertain(operation.operationId, error.syncErrorCode())
            WriteOutcome.UNCERTAIN
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

    private class WriteCounts {
        var committed = 0
        var conflicted = 0
        var rejected = 0
        var uncertain = 0

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
