package app.tandemforactual.ui

import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.BudgetSyncSummary
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.Uuid

internal interface ConflictResolutionStore {
    suspend fun readPendingWrites(budgetId: String): List<OutboxOperationEntity>

    suspend fun findRecord(
        budgetId: String,
        recordType: String,
        recordId: String,
    ): BudgetRecordEntity?

    suspend fun discardWrite(operationId: String)

    suspend fun enqueueWrite(request: LocalWriteRequest)

    suspend fun findWrite(operationId: String): OutboxOperationEntity?
}

internal class RepositoryConflictResolutionStore(
    private val repository: LocalBudgetRepository,
) : ConflictResolutionStore {
    override suspend fun readPendingWrites(budgetId: String): List<OutboxOperationEntity> = repository.readPendingWrites(budgetId)

    override suspend fun findRecord(
        budgetId: String,
        recordType: String,
        recordId: String,
    ): BudgetRecordEntity? = repository.findRecord(budgetId, recordType, recordId)

    override suspend fun discardWrite(operationId: String) {
        repository.discardWrite(operationId)
    }

    override suspend fun enqueueWrite(request: LocalWriteRequest) {
        repository.enqueueWrite(request)
    }

    override suspend fun findWrite(operationId: String): OutboxOperationEntity? = repository.findWrite(operationId)
}

internal interface ConflictResolutionSync {
    suspend fun confirmConflictWithServer(
        budgetId: String,
        operationId: String,
        encryptionPassword: String?,
    ): Boolean

    suspend fun synchronize(
        budgetId: String,
        encryptionPassword: String?,
        retryQueuedBudgetMutations: Boolean,
    ): BudgetSyncSummary?
}

internal class TransactionConflictResolution(
    private val store: ConflictResolutionStore,
    private val sync: ConflictResolutionSync,
    private val nowEpochMillis: () -> Long,
    private val newOperationId: () -> String = { Uuid.random().toString() },
) {
    suspend fun resolve(
        budgetId: String,
        operationId: String,
        keepDeviceVersion: Boolean,
        encryptionPassword: String?,
    ): String {
        var replacementOperationId: String? = null
        try {
            val operation = store.readPendingWrites(budgetId).firstOrNull { it.operationId == operationId }
            if (operation == null || operation.status != "conflict") {
                return "This change no longer needs review. Sync the budget to check its latest status."
            }
            if (!sync.confirmConflictWithServer(budgetId, operationId, encryptionPassword)) {
                return "Tandem hasn’t confirmed your choice. Your edit remains on this device. Check the connection and try again."
            }
            val confirmedOperation =
                store
                    .readPendingWrites(budgetId)
                    .firstOrNull { it.operationId == operationId }
                    ?.takeIf { it.status == "conflict" }
                    ?: return "Tandem confirmed the latest budget, but this change no longer needs review. " +
                        "Sync the budget to check its status."

            if (!keepDeviceVersion) {
                store.discardWrite(operationId)
                return "Tandem confirmed Actual’s latest version. Tandem kept that version and removed your edit."
            }

            val current = store.findRecord(budgetId, confirmedOperation.recordType, confirmedOperation.recordId)
            if (current?.remoteDeleted == true && confirmedOperation.action == LocalWriteAction.DELETE.name) {
                store.discardWrite(operationId)
                return "Tandem confirmed the transaction was deleted. Tandem removed the waiting deletion."
            }
            val baseJson = current?.remoteJson
            val revision = current?.remoteRevision
            if (current == null || current.remoteDeleted || baseJson == null || revision.isNullOrBlank()) {
                return "Tandem didn’t return the latest transaction. Your edit remains on this device. Sync the budget and try again."
            }
            val base =
                runCatching { Json.parseToJsonElement(baseJson).jsonObject }.getOrNull()
                    ?: return "Tandem couldn’t read the latest transaction. Your edit remains on this device. " +
                        "Sync the budget and try again."
            val patch = runCatching { Json.parseToJsonElement(confirmedOperation.patchJson).jsonObject }.getOrNull()
            if (confirmedOperation.action == LocalWriteAction.UPDATE.name && patch == null) {
                return "Tandem couldn’t read your saved edit. The edit remains on this device. Sync the budget and try again."
            }

            val replacementId = newOperationId()
            replacementOperationId = replacementId
            val action =
                if (confirmedOperation.action == LocalWriteAction.DELETE.name) {
                    LocalWriteAction.DELETE
                } else {
                    LocalWriteAction.UPDATE
                }
            val rebased = patch?.let { JsonObject(base + it) }
            store.enqueueWrite(
                LocalWriteRequest(
                    operationId = replacementId,
                    idempotencyKey = replacementId,
                    budgetId = budgetId,
                    recordType = confirmedOperation.recordType,
                    recordId = confirmedOperation.recordId,
                    action = action,
                    baseRevision = revision,
                    baseSnapshotJson = baseJson,
                    patchJson = if (action == LocalWriteAction.DELETE) "{}" else checkNotNull(patch).toString(),
                    optimisticJson = rebased?.toString(),
                    createdAtEpochMs = nowEpochMillis(),
                    optimisticProjection = rebased?.toProjection(),
                    supersedesConflictOperationId = operationId,
                ),
            )
            val summary = sync.synchronize(budgetId, encryptionPassword, retryQueuedBudgetMutations = false)
            val resolvedWrite = store.findWrite(replacementId)
            return conflictResolutionMessage(resolvedWrite, action, summary)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return if (replacementOperationId == null) {
                "Tandem hasn’t confirmed your choice. Your edit remains on this device. Check the connection and try again."
            } else {
                "Tandem hasn’t confirmed your choice. Your edit remains on this device. Check the connection and try again."
            }
        }
    }

    private fun conflictResolutionMessage(
        resolvedWrite: OutboxOperationEntity?,
        action: LocalWriteAction,
        summary: BudgetSyncSummary?,
    ): String =
        when (resolvedWrite?.status) {
            "committed" ->
                if (action == LocalWriteAction.DELETE) {
                    "Tandem confirmed the deletion in Actual."
                } else {
                    "Tandem confirmed your edit in Actual."
                }
            "queued" -> "Tandem hasn’t confirmed your choice. Your edit remains on this device. Check the connection and try again."
            "conflict" -> "Tandem found another difference. Your edit remains on this device. Review the versions again."
            "uncertain", "sending" ->
                "Tandem hasn’t confirmed your choice. Your edit remains on this device. Check its status before trying again."
            "rejected" ->
                if (resolvedWrite.lastErrorCode == "split-child-field-not-editable") {
                    "Actual manages this part through the full transaction. Your edit remains on this device. Update the full transaction in Actual."
                } else {
                    "Actual kept the previous value. Change the edit and try again."
                }
            else ->
                if (summary == null) {
                    "Tandem hasn’t confirmed your choice. Your edit remains on this device. Check the connection and try again."
                } else {
                    "Tandem hasn’t confirmed your choice. Your edit remains on this device. Check the connection and try again."
                }
        }
}
