package app.tandemforactual.api.client

import app.tandemforactual.data.store.BudgetSyncSummary
import app.tandemforactual.data.store.FullSnapshotResult
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.RemoteBudgetRecord
import app.tandemforactual.data.store.RemoteOperationResult
import app.tandemforactual.data.store.RemoteOperationStatus
import app.tandemforactual.data.store.encodeLocalWriteConflictDetails
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val directSyncMutex = Mutex()

/** Syncs a cache and outbox created for a direct budget by [ActualDirectBudget.stageMobileCache]. */
class ActualDirectSyncCoordinator(
    private val budget: ActualDirectBudget,
    private val repository: LocalBudgetRepository,
) {
    suspend fun synchronize(
        today: String,
        sendQueuedWrites: Boolean,
    ): BudgetSyncSummary =
        directSyncMutex.withLock {
            val budgetId = budget.groupId
            val checkpoint = repository.readSyncCheckpoint(budgetId)
            check(checkpoint?.generation == BudgetSyncJson.directGeneration(budgetId)) {
                "The cache has not been staged for direct Actual sync."
            }
            repository.markSyncStarted(budgetId)
            try {
                repository.recoverInterruptedSyncWrites()
                var projection = budget.refreshProjection(today)
                val results = mutableListOf<RemoteOperationResult>()
                if (sendQueuedWrites) {
                    for (operation in repository.readPendingWrites(budgetId)) {
                        if (operation.recordType != "transaction" || operation.status != "uncertain") continue
                        val result = budget.findOperation(operation.operationId)
                        if (result == null) {
                            repository.retryUncertainWrite(operation.operationId)
                        } else {
                            check(result.transactionId == operation.recordId)
                            results += result
                        }
                    }
                    var submitted = false
                    while (true) {
                        val operation = repository.claimNextWrite(budgetId) ?: break
                        results += budget.submit(operation)
                        submitted = true
                    }
                    if (submitted) projection = budget.refreshProjection(today)
                }
                val transactions = projection.records.filter { it.recordType == "transaction" }.associateBy { it.recordId }
                val outcomes = results.map { finalize(it, transactions[it.transactionId]) }
                val applied =
                    repository.applyFullSnapshot(
                        budgetId,
                        projection.generation,
                        projection.cursor,
                        checkpoint,
                        projection.records,
                        projection.displaySettings,
                    )
                check(applied == FullSnapshotResult.APPLIED) { "The direct cache changed while sync was running." }
                BudgetSyncSummary(
                    fullSnapshotApplied = true,
                    changePagesApplied = 0,
                    changesApplied = 0,
                    writesCommitted = outcomes.count { it == RemoteOperationStatus.COMMITTED },
                    writesConflicted = outcomes.count { it == RemoteOperationStatus.CONFLICT },
                    writesRejected = outcomes.count { it == RemoteOperationStatus.REJECTED },
                    writesUncertain = 0,
                )
            } catch (error: Exception) {
                withContext(NonCancellable) {
                    runCatching { repository.markSyncFailed(budgetId, "direct-sync-failed") }
                }
                throw error
            }
        }

    private suspend fun finalize(
        result: RemoteOperationResult,
        record: RemoteBudgetRecord?,
    ): RemoteOperationStatus {
        val operationId = result.operationId
        val operation = checkNotNull(repository.findWrite(operationId))
        return when (result.status) {
            RemoteOperationStatus.COMMITTED -> {
                if ((operation.action == "DELETE") != (record == null)) {
                    repository.markWriteConflict(operationId, "transaction-changed-during-sync", record)
                    RemoteOperationStatus.CONFLICT
                } else {
                    repository.markWriteCommitted(operationId, record)
                    RemoteOperationStatus.COMMITTED
                }
            }

            RemoteOperationStatus.CONFLICT -> {
                val details =
                    encodeLocalWriteConflictDetails(
                        result.errorCode ?: "transaction-conflict",
                        result.conflictType,
                        result.conflictingFields,
                    )
                repository.markWriteConflict(operationId, details, record)
                RemoteOperationStatus.CONFLICT
            }

            RemoteOperationStatus.REJECTED -> {
                repository.markWriteRejected(operationId, result.errorCode ?: "transaction-rejected")
                RemoteOperationStatus.REJECTED
            }

            else -> error("Unexpected direct operation status ${result.status}")
        }
    }
}
