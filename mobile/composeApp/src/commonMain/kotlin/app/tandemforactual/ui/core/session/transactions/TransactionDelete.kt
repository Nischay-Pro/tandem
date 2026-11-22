package app.tandemforactual.ui.core.session.transactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import app.tandemforactual.ui.core.session.UndoNotice
import app.tandemforactual.ui.core.session.localStorageFailureMessage
import app.tandemforactual.ui.core.session.transactions.TransactionController
import app.tandemforactual.ui.core.state.UNSYNCED_TRANSACTION_WRITE_STATUSES
import kotlinx.coroutines.CancellationException
import kotlin.uuid.Uuid

/** Deletes right away and offers an undo while the deletions are still waiting to sync. */
internal suspend fun TransactionController.deleteTransactionsWithUndo(
    budgetId: String,
    transactionIds: Set<String>,
): Boolean {
    val deleted = transactionIds.takeWhile { deleteTransaction(budgetId, it) }.toSet()
    if (deleted.isNotEmpty()) {
        val undoable = deleted.filterTo(mutableSetOf()) { queuedTransactionDelete(budgetId, it) != null }
        undoNoticeCount += 1
        undoNotice =
            UndoNotice(
                id = undoNoticeCount,
                message = if (deleted.size == 1) "Transaction deleted" else "${deleted.size} transactions deleted",
                budgetId = budgetId,
                transactionIds = undoable,
            )
    }
    return deleted.size == transactionIds.size
}

internal suspend fun TransactionController.undoTransactionDeletes(notice: UndoNotice) {
    val localRepository = host.repository ?: return
    var restoredAll = true
    for (transactionId in notice.transactionIds) {
        val write = queuedTransactionDelete(notice.budgetId, transactionId)
        if (write == null) restoredAll = false else localRepository.discardWrite(write.operationId)
    }
    host.loadLocalRecords(notice.budgetId)
    undoNoticeCount += 1
    undoNotice =
        if (restoredAll) {
            null
        } else {
            UndoNotice(undoNoticeCount, "Already synced, so it can’t be undone here", notice.budgetId, emptySet())
        }
}

internal suspend fun TransactionController.queuedTransactionDelete(
    budgetId: String,
    transactionId: String,
): OutboxOperationEntity? =
    host.repository
        ?.readPendingWrites(budgetId)
        ?.lastOrNull {
            it.recordType == "transaction" &&
                it.recordId == transactionId &&
                it.action == LocalWriteAction.DELETE.name &&
                it.status == "queued"
        }

internal suspend fun TransactionController.deleteTransaction(
    budgetId: String,
    transactionId: String,
): Boolean {
    host.reportTransactionWriteMessage(null)
    if (host.refuseWriteInReadOnlyMode()) return false
    if (!host.isCurrentIdentityCacheOwner()) {
        host.reportTransactionWriteMessage("Sign in to the server and account that own this budget before deleting a transaction.")
        return false
    }
    val localRepository = host.repository
    if (localRepository == null) {
        host.reportTransactionWriteMessage("Budget data isn’t available on this device. Reopen the budget and try again.")
        return false
    }
    val pendingForRecord =
        localRepository
            .readPendingWrites(budgetId)
            .filter {
                it.recordType == "transaction" && it.recordId == transactionId && it.status in UNSYNCED_TRANSACTION_WRITE_STATUSES
            }.maxByOrNull { it.updatedAtEpochMs }
    if (pendingForRecord?.action == LocalWriteAction.CREATE.name && pendingForRecord.status == "queued") {
        return discardQueuedTransactionCreate(localRepository, budgetId, pendingForRecord)
    }
    if (pendingForRecord != null && pendingForRecord.status != "queued") {
        host.reportTransactionWriteMessage("Open Pending Changes to review this transaction before deleting it.")
        return false
    }
    val current = localRepository.findRecord(budgetId, "transaction", transactionId)
    val baseJson = current?.remoteJson
    val revision = current?.remoteRevision
    if (current == null || current.remoteDeleted || baseJson == null || revision.isNullOrBlank()) {
        host.reportTransactionWriteMessage("This transaction isn’t up to date. Sync the budget before deleting it.")
        return false
    }
    return enqueueTransactionDelete(localRepository, budgetId, transactionId, revision, baseJson)
}

private suspend fun TransactionController.discardQueuedTransactionCreate(
    localRepository: LocalBudgetRepository,
    budgetId: String,
    pendingForRecord: OutboxOperationEntity,
): Boolean {
    host.markSavingTransaction(true)
    try {
        localRepository.discardWrite(pendingForRecord.operationId)
        host.reportTransactionWriteMessage(null)
        host.loadLocalRecords(budgetId)
        return true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        host.reportTransactionWriteMessage(
            localStorageFailureMessage(
                error,
                "Tandem couldn’t remove this transaction. Open Pending Changes to check it.",
            ),
        )
        host.loadLocalRecords(budgetId)
        return false
    } finally {
        host.markSavingTransaction(false)
    }
}

private suspend fun TransactionController.enqueueTransactionDelete(
    localRepository: LocalBudgetRepository,
    budgetId: String,
    transactionId: String,
    revision: String,
    baseJson: String,
): Boolean {
    host.markSavingTransaction(true)
    try {
        val operationId = Uuid.random().toString()
        localRepository.enqueueWrite(
            LocalWriteRequest(
                operationId = operationId,
                idempotencyKey = operationId,
                budgetId = budgetId,
                recordType = "transaction",
                recordId = transactionId,
                action = LocalWriteAction.DELETE,
                baseRevision = revision,
                baseSnapshotJson = baseJson,
                patchJson = "{}",
                optimisticJson = null,
                createdAtEpochMs = host.nowEpochMillis(),
                optimisticProjection = null,
            ),
        )
        host.reportTransactionWriteMessage(null)
        host.loadLocalRecords(budgetId)
        return true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        host.reportTransactionWriteMessage(
            localStorageFailureMessage(
                error,
                "Tandem couldn’t delete this transaction. Open Pending Changes to check it.",
            ),
        )
        host.loadLocalRecords(budgetId)
        return false
    } finally {
        host.markSavingTransaction(false)
    }
}
