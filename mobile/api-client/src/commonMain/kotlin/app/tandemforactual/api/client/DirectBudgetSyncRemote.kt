package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ActualTimestamp
import app.tandemforactual.data.actual.OperationStatus
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.BudgetChange
import app.tandemforactual.data.store.BudgetSyncRemote
import app.tandemforactual.data.store.BudgetSyncRemoteException
import app.tandemforactual.data.store.RemoteBudgetChangePage
import app.tandemforactual.data.store.RemoteBudgetRecord
import app.tandemforactual.data.store.RemoteBudgetSnapshot
import app.tandemforactual.data.store.RemoteBudgetSnapshotPage
import app.tandemforactual.data.store.RemoteOperationResult
import app.tandemforactual.data.store.RemoteOperationStatus
import app.tandemforactual.data.store.RemoteTransactionRecord
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Feeds the mobile cache from the device's own copy of each Actual budget: every refresh syncs with Actual and serves a
 * fresh snapshot, and queued transaction edits are applied locally with Actual's rules, then uploaded.
 */
class DirectBudgetSyncRemote(
    private val client: TandemApiClient,
) : BudgetSyncRemote {
    private val pages = Mutex()
    private var staged: RemoteBudgetSnapshot? = null

    private suspend fun snapshot(
        budgetId: String,
        password: String?,
    ): RemoteBudgetSnapshot {
        val budget = client.budget(budgetId, password)
        val projection = client.call { budget.refreshProjection(client.today()) }
        val synced = ActualTimestamp.parse(projection.cursor)
        val cursor = synced?.let { (it.millis * 65_536 + it.counter).toString() } ?: "0"
        return projection.copy(
            cursor = cursor,
            records =
                projection.records.sortedWith(
                    compareBy<RemoteBudgetRecord> {
                        it.recordType
                    }.thenBy { it.recordId },
                ),
        )
    }

    override suspend fun readSnapshot(
        budgetId: String,
        encryptionPassword: String?,
    ): RemoteBudgetSnapshot = snapshot(budgetId, encryptionPassword)

    override suspend fun readSnapshotPage(
        budgetId: String,
        generation: String?,
        cursor: String?,
        afterType: String?,
        afterId: String?,
        limit: Int,
        encryptionPassword: String?,
    ): RemoteBudgetSnapshotPage =
        pages.withLock {
            val current =
                staged?.takeIf { afterType != null && it.budgetId == budgetId && it.generation == generation && it.cursor == cursor }
                    ?: snapshot(budgetId, encryptionPassword).also {
                        staged = it
                        client.budget(budgetId, encryptionPassword).saveServedProjection(
                            it.cursor,
                            it.records.associate { record ->
                                (
                                    record.recordType to
                                        record.recordId
                                ) to
                                    record.revision
                            },
                        )
                    }
            val start =
                if (afterType ==
                    null
                ) {
                    0
                } else {
                    current.records.indexOfFirst { it.recordType == afterType && it.recordId == afterId } + 1
                }
            val page = current.records.drop(start).take(limit)
            val hasMore = start + page.size < current.records.size
            RemoteBudgetSnapshotPage(
                budgetId = budgetId,
                generation = current.generation,
                cursor = current.cursor,
                records = page,
                hasMore = hasMore,
                resetRequired = false,
                nextAfterType = page.lastOrNull()?.recordType.takeIf { hasMore },
                nextAfterId = page.lastOrNull()?.recordId.takeIf { hasMore },
                displaySettings = current.displaySettings,
            )
        }

    /** Sends only the records whose revision changed since the cache's cursor, or asks for a full snapshot. */
    override suspend fun readChanges(
        budgetId: String,
        generation: String,
        cursor: String,
        throughCursor: String?,
        encryptionPassword: String?,
    ): RemoteBudgetChangePage {
        val budget = client.budget(budgetId, encryptionPassword)
        val (servedCursor, served) = budget.servedProjection()
        val fresh = snapshot(budgetId, encryptionPassword)
        if (fresh.generation != generation || servedCursor != cursor || fresh.cursor.toLong() < (cursor.toLongOrNull() ?: Long.MAX_VALUE)) {
            return RemoteBudgetChangePage(
                budgetId,
                fresh.generation,
                fresh.cursor,
                fresh.cursor,
                fresh.cursor,
                hasMore = false,
                resetRequired = true,
                changes = emptyList(),
            )
        }
        val current = fresh.records.associateBy { it.recordType to it.recordId }
        val changes =
            current.values.filter { served[it.recordType to it.recordId] != it.revision }.map { BudgetChange.Upsert(it) } +
                served.keys.filter { it !in current }.map { (type, id) ->
                    BudgetChange.Delete(budgetId, type, id, served.getValue(type to id))
                }
        budget.saveServedProjection(fresh.cursor, current.mapValues { it.value.revision })
        return RemoteBudgetChangePage(
            budgetId,
            generation,
            fresh.cursor,
            fresh.cursor,
            fresh.cursor,
            hasMore = false,
            resetRequired = false,
            changes = changes,
            displaySettings = fresh.displaySettings,
        )
    }

    override suspend fun submitOperation(
        operation: OutboxOperationEntity,
        encryptionPassword: String?,
    ): RemoteOperationResult {
        val budget = client.budget(operation.budgetId, encryptionPassword)
        val outcome = client.call { budget.submit(operation) }
        runCatching { budget.sync() }
        return result(operation.budgetId, outcome)
    }

    override suspend fun readOperation(operationId: String): RemoteOperationResult =
        throw BudgetSyncRemoteException("operation_not_found", 404)

    override suspend fun readTransaction(
        budgetId: String,
        transactionId: String,
        encryptionPassword: String?,
    ): RemoteTransactionRecord? =
        client.budget(budgetId, encryptionPassword).transactionRecord(transactionId)?.let(BudgetSyncJson::directTransaction)

    private fun result(
        budgetId: String,
        outcome: app.tandemforactual.data.actual.OperationOutcome,
    ) = RemoteOperationResult(
        operationId = outcome.operationId,
        budgetId = budgetId,
        transactionId = outcome.transactionId,
        status =
            when (outcome.status) {
                OperationStatus.COMMITTED -> RemoteOperationStatus.COMMITTED
                OperationStatus.CONFLICT -> RemoteOperationStatus.CONFLICT
                OperationStatus.REJECTED -> RemoteOperationStatus.REJECTED
            },
        errorCode = outcome.errorCode ?: if (outcome.status == OperationStatus.CONFLICT) "transaction-conflict" else null,
        conflictType = outcome.conflictType,
        conflictingFields = outcome.conflictingFields,
    )
}
