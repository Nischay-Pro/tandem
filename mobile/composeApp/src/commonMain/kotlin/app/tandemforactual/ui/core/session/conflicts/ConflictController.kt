package app.tandemforactual.ui.core.session.conflicts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.BudgetSyncSummary
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.ui.core.session.ApiFailureContext
import app.tandemforactual.ui.core.session.ConflictResolutionFeedback
import app.tandemforactual.ui.core.session.SessionHost
import app.tandemforactual.ui.core.session.apiFailureHttpStatus
import app.tandemforactual.ui.core.session.apiFailureMessage
import app.tandemforactual.ui.core.session.budget.restoreBudgetReorderQueue
import app.tandemforactual.ui.core.session.budget.retryQueuedBudgetReorders
import app.tandemforactual.ui.core.session.conflicts.ConflictController
import app.tandemforactual.ui.core.session.conflicts.ConflictResolutionSync
import app.tandemforactual.ui.core.session.conflicts.RepositoryConflictResolutionStore
import app.tandemforactual.ui.core.session.conflicts.TransactionConflictResolution
import app.tandemforactual.ui.core.session.discardOperation
import app.tandemforactual.ui.core.session.discardedPendingWriteMessage
import kotlinx.coroutines.CancellationException

/** Edits that conflict with Actual or whose result is uncertain: resolving, discarding, retrying and checking their status. */
internal class ConflictController(
    private val host: SessionHost,
) {
    suspend fun confirmConflictWithServer(
        budgetId: String,
        operationId: String,
        encryptionPassword: String?,
    ): Boolean {
        serverConfirmedConflictOperationId = null
        val summary =
            host.synchronize(
                budgetId = budgetId,
                encryptionPassword = encryptionPassword,
                sendQueuedWrites = false,
                retryQueuedBudgetMutations = false,
            ) ?: return false
        val operation = host.repository?.readPendingWrites(budgetId)?.firstOrNull { it.operationId == operationId }
        if (operation?.status != "conflict") return false
        serverConfirmedConflictOperationId = operationId
        return true
    }

    suspend fun resolveConflict(
        budgetId: String,
        operationId: String,
        keepDeviceVersion: Boolean,
        encryptionPassword: String?,
        reviewedRevision: String,
    ) {
        host.clearTransactionWriteMessage()
        if (host.refuseWriteInReadOnlyMode()) {
            reportConflictResolution(operationId, host.transactionWriteMessage ?: "Read-only mode is on.")
            return
        }
        if (host.isSavingTransaction) {
            reportConflictResolution(
                operationId,
                "A decision is already being saved. Wait for it to finish, then review this change again.",
            )
            return
        }
        val localRepository = host.repository
        if (localRepository == null) {
            reportConflictResolution(operationId, "Budget data isn’t available on this device. Restart the app and try again.")
            return
        }
        val operation = localRepository.readPendingWrites(budgetId).firstOrNull { it.operationId == operationId }
        if (operation == null || operation.status != "conflict") {
            reportConflictResolution(operationId, "This change no longer needs review. Sync the budget to refresh its status.")
            host.loadLocalRecords(budgetId)
            return
        }
        host.markSavingTransaction(true)
        try {
            val workflow =
                TransactionConflictResolution(
                    store = RepositoryConflictResolutionStore(localRepository),
                    sync =
                        object : ConflictResolutionSync {
                            override suspend fun confirmConflictWithServer(
                                budgetId: String,
                                operationId: String,
                                encryptionPassword: String?,
                            ): Boolean = this@ConflictController.confirmConflictWithServer(budgetId, operationId, encryptionPassword)

                            override suspend fun synchronize(
                                budgetId: String,
                                encryptionPassword: String?,
                                retryQueuedBudgetMutations: Boolean,
                            ): BudgetSyncSummary? =
                                host.synchronize(
                                    budgetId = budgetId,
                                    encryptionPassword = encryptionPassword,
                                    retryQueuedBudgetMutations = retryQueuedBudgetMutations,
                                )
                        },
                    nowEpochMillis = host.nowEpochMillis,
                )
            reportConflictResolution(
                operationId,
                workflow.resolve(
                    budgetId,
                    operationId,
                    keepDeviceVersion,
                    encryptionPassword,
                    reviewedRevision,
                ),
            )
            host.loadLocalRecords(budgetId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            reportConflictResolution(
                operationId,
                "Tandem hasn’t confirmed your choice. Your edit remains on this device. Check the connection and try again.",
            )
            host.loadLocalRecords(budgetId)
        } finally {
            host.markSavingTransaction(false)
        }
    }

    suspend fun discardWrite(
        budgetId: String,
        operationId: String,
        encryptionPassword: String? = null,
    ) {
        host.reportTransactionWriteMessage(null)
        clearUncertainWriteCheck()
        if (host.refuseWriteInReadOnlyMode()) return
        val localRepository = host.repository ?: return
        val operation = localRepository.findWrite(operationId)
        if (operation?.status == "uncertain" && operation.recordType != "budget-mutation") {
            if (operation.recordType != "transaction") {
                uncertainWriteCheckError = "Tandem couldn’t confirm that this change was removed. It remains on this device."
                uncertainWriteCheckErrorOperationId = operationId
                return
            }
            discardUncertainTransactionWrite(budgetId, operationId, encryptionPassword, localRepository, operation)
            return
        }
        if (operation?.recordType == "budget-mutation" && !host.budgetQueue.restoreBudgetReorderQueue()) {
            host.reportTransactionWriteMessage("Couldn’t verify this pending change. Try again before removing it.")
            return
        }
        localRepository.discardWrite(operationId)
        if (operation?.recordType == "budget-mutation") {
            host.budgetQueue.budgetReorderQueue = host.budgetQueue.budgetReorderQueue.discardOperation(operationId)
            if (operation.status != "uncertain") host.budgetQueue.retryQueuedBudgetReorders()
        }
        host.loadLocalRecords(budgetId)
        host.reportTransactionWriteMessage(discardedPendingWriteMessage(operation?.status))
    }

    private suspend fun discardUncertainTransactionWrite(
        budgetId: String,
        operationId: String,
        encryptionPassword: String?,
        localRepository: LocalBudgetRepository,
        operation: OutboxOperationEntity,
    ) {
        val coordinator = host.syncCoordinator
        if (coordinator == null || !host.isCurrentIdentityCacheOwner()) {
            uncertainWriteCheckError =
                "Tandem couldn’t confirm that this edit was removed. It remains on this device. Sign in to the server and try again."
            uncertainWriteCheckErrorOperationId = operationId
            checkedWriteOperation = operation
            checkedWriteOperationId = operationId
            return
        }
        host.markSavingTransaction(true)
        try {
            val result =
                host.apiClient.withExclusiveSession {
                    coordinator.discardUncertainWrite(
                        budgetId,
                        operationId,
                    )
                }
            if (result.status == app.tandemforactual.data.store.RemoteOperationStatus.REJECTED &&
                result.errorCode == "discarded-by-user"
            ) {
                host.loadLocalRecords(budgetId)
                checkedWriteOperation = localRepository.findWrite(operationId)
                checkedWriteOperationId = operationId
                host.reportTransactionWriteMessage(discardedPendingWriteMessage("uncertain"))
                return
            }

            val summary =
                host.synchronize(
                    budgetId = budgetId,
                    encryptionPassword = encryptionPassword,
                    sendQueuedWrites = false,
                    reconcileUncertainOperationId = operationId,
                )
            checkedWriteOperation = localRepository.findWrite(operationId)
            checkedWriteOperationId = operationId
            if (checkedWriteOperation?.status == "uncertain") {
                uncertainWriteCheckError =
                    if (summary == null) {
                        "Tandem couldn’t confirm that this edit was removed. " +
                            "It remains on this device. Check the connection and try again."
                    } else {
                        "Tandem still can’t confirm whether Actual saved this edit. " +
                            "It remains on this device. Check status before removing it."
                    }
                uncertainWriteCheckErrorOperationId = operationId
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            checkedWriteOperation = localRepository.findWrite(operationId)
            checkedWriteOperationId = operationId
            uncertainWriteCheckError =
                "Tandem couldn’t confirm that this edit was removed. It remains on this device. Check the connection and try again."
            uncertainWriteCheckErrorOperationId = operationId
            host.loadLocalRecords(budgetId)
        } finally {
            host.markSavingTransaction(false)
        }
    }

    suspend fun retryUncertainWrite(
        budgetId: String,
        operationId: String,
        encryptionPassword: String?,
    ) {
        host.reportTransactionWriteMessage(null)
        clearUncertainWriteCheck()
        if (host.refuseWriteInReadOnlyMode()) return
        val localRepository = host.repository ?: return
        when (localRepository.findWrite(operationId)?.status) {
            "uncertain" -> localRepository.retryUncertainWrite(operationId)
            "queued" -> Unit
            else -> {
                host.reportTransactionWriteMessage("This edit is no longer waiting for confirmation. Refresh Pending Changes.")
                host.loadLocalRecords(budgetId)
                return
            }
        }
        host.synchronize(budgetId, encryptionPassword)
        host.loadLocalRecords(budgetId)
    }

    suspend fun checkUncertainWriteStatus(
        budgetId: String,
        operationId: String,
        encryptionPassword: String?,
    ) {
        if (host.isSyncing) return
        clearUncertainWriteCheck()
        val summary =
            host.synchronize(
                budgetId = budgetId,
                encryptionPassword = encryptionPassword,
                sendQueuedWrites = false,
                reconcileUncertainOperationId = operationId,
            )
        if (summary == null) {
            checkedWriteOperationId = operationId
            uncertainWriteCheckError = host.syncError ?: "Tandem couldn’t check this change. Try again."
            uncertainWriteCheckErrorOperationId = operationId
            return
        }

        checkedWriteOperationId = operationId
        summary.operationStatusErrorCode?.takeIf { it != "operation_not_found" }?.let { statusErrorCode ->
            uncertainWriteCheckError =
                apiFailureMessage(
                    errorCode = statusErrorCode,
                    httpStatus = apiFailureHttpStatus(statusErrorCode, null),
                    context = ApiFailureContext.SYNC,
                    fallback = "Tandem couldn’t confirm this change’s status. Check the connection, then try again.",
                )
            uncertainWriteCheckErrorOperationId = operationId
        }
        try {
            checkedWriteOperation = host.repository?.findWrite(operationId)
            if (checkedWriteOperation == null) {
                uncertainWriteCheckError = "This edit is no longer waiting to sync."
                uncertainWriteCheckErrorOperationId = operationId
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            uncertainWriteCheckError = "Couldn’t check this edit’s status on this device."
            uncertainWriteCheckErrorOperationId = operationId
        }
    }

    private fun clearUncertainWriteCheck() {
        checkedWriteOperation = null
        checkedWriteOperationId = null
        uncertainWriteCheckError = null
        uncertainWriteCheckErrorOperationId = null
    }

    fun clearConflictResolutionFeedback() {
        conflictResolutionFeedback = null
    }

    private fun reportConflictResolution(
        operationId: String,
        message: String,
    ) {
        host.reportTransactionWriteMessage(message)
        conflictResolutionFeedback = ConflictResolutionFeedback(operationId, message)
    }

    var conflictResolutionFeedback by mutableStateOf<ConflictResolutionFeedback?>(null)
        private set

    var serverConfirmedConflictOperationId by mutableStateOf<String?>(null)
        private set

    var checkedWriteOperation by mutableStateOf<OutboxOperationEntity?>(null)
        private set

    var checkedWriteOperationId by mutableStateOf<String?>(null)
        private set

    var uncertainWriteCheckError by mutableStateOf<String?>(null)
        private set

    var uncertainWriteCheckErrorOperationId by mutableStateOf<String?>(null)
        private set

    /** Clears the message about the last conflict resolution. */
    fun clearFeedback() {
        conflictResolutionFeedback = null
        serverConfirmedConflictOperationId = null
    }

    /** Forgets everything about the open budget's conflicts, for sign-out or switching budgets. */
    fun reset() {
        checkedWriteOperation = null
        checkedWriteOperationId = null
        uncertainWriteCheckError = null
        uncertainWriteCheckErrorOperationId = null
        conflictResolutionFeedback = null
    }
}
