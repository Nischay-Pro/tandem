package app.tandemforactual.ui.core.session.budget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.completeBudgetMutationAfterConfirmation
import app.tandemforactual.data.store.findRecord
import app.tandemforactual.data.store.findWrite
import app.tandemforactual.data.store.readPendingWrites
import app.tandemforactual.data.store.retryBudgetMutationAfterReview
import app.tandemforactual.ui.core.session.BUDGET_QUEUE_RESTORE_ERROR
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.BudgetReorderQueueState
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.awaitRefresh
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits
import app.tandemforactual.ui.core.session.budgetMutationNeedsRefresh
import app.tandemforactual.ui.core.session.budgetMutationRequestForRetry
import app.tandemforactual.ui.core.session.budgetMutationRequiresActualCheck
import app.tandemforactual.ui.core.session.budgetMutationRetryNeedsNewKey
import app.tandemforactual.ui.core.session.clearMessage
import app.tandemforactual.ui.core.session.complete
import app.tandemforactual.ui.core.session.completeCategoryCreate
import app.tandemforactual.ui.core.session.completeCategoryGroupCreate
import app.tandemforactual.ui.core.session.decodeBudgetReorderQueue
import app.tandemforactual.ui.core.session.enqueue
import app.tandemforactual.ui.core.session.localStorageFailureMessage
import app.tandemforactual.ui.core.session.parseQueuePayload
import app.tandemforactual.ui.core.session.replaceOperationId
import app.tandemforactual.ui.core.session.replaceRejectedOperation
import app.tandemforactual.ui.core.session.resumeAfterConnection
import app.tandemforactual.ui.core.session.statusFor
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.budgetMonthCategoryCarryover
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.stringValue
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

internal fun BudgetEditQueue.retryQueuedBudgetReorders(
    budgetId: String? = null,
    encryptionPassword: String? = null,
) {
    if (!budgetId.isNullOrBlank() && !encryptionPassword.isNullOrBlank()) {
        budgetReorderQueue =
            budgetReorderQueue.copy(
                moves =
                    budgetReorderQueue.moves.map { move ->
                        if (move.budgetId == budgetId && move.requiresEncryptionPassword) {
                            move.copy(encryptionPassword = encryptionPassword)
                        } else {
                            move
                        }
                    },
            )
    }
    budgetReorderRetrySignal += 1
}

internal fun BudgetEditQueue.retryBudgetMutation(operationId: String) {
    if (isCorrectingBudgetMutation) return
    host.sessionScope.launch { retryBudgetMutationNow(operationId) }
}

private suspend fun BudgetEditQueue.retryBudgetMutationNow(operationId: String) {
    val localRepository = host.repository ?: return
    val operation = host.pendingWrites.firstOrNull { it.operationId == operationId } ?: return
    val request = retryRequestFor(operation) ?: return

    if (budgetMutationNeedsRefresh(operation.lastErrorCode)) {
        completeAfterRefresh(localRepository, operation, request)
        return
    }

    if (budgetMutationRequiresActualCheck(operation.lastErrorCode)) {
        confirmWithActual(localRepository, operation, request)
        return
    }

    val requeued =
        if (budgetMutationRetryNeedsNewKey(operation.lastErrorCode)) {
            requeueWithNewKey(operation, request)
        } else {
            resumeAfterReview(localRepository, operation, request, operationId)
        }
    if (!requeued) return
    host.replacePendingWrites(localRepository.readPendingWrites(operation.budgetId))
    retryQueuedBudgetReorders()
}

private fun BudgetEditQueue.retryRequestFor(operation: OutboxOperationEntity): QueuedBudgetReorder? =
    decodeBudgetReorderQueue(operation.patchJson)
        .moves
        .singleOrNull()
        ?.let { move ->
            if (move.encryptionPassword == null) {
                move.copy(encryptionPassword = host.scheduleActionEncryptionPasswords[operation.budgetId])
            } else {
                move
            }
        }

private suspend fun BudgetEditQueue.completeAfterRefresh(
    localRepository: LocalBudgetRepository,
    operation: OutboxOperationEntity,
    request: QueuedBudgetReorder,
) {
    val refreshed =
        host.synchronize(
            operation.budgetId,
            request.encryptionPassword,
            retryQueuedBudgetMutations = false,
        )
    if (refreshed == null) return
    runCatching { localRepository.completeBudgetMutationAfterConfirmation(operation.operationId) }
        .getOrNull() ?: return
    if (budgetReorderQueue.moves.none { it.operationId == operation.operationId }) {
        budgetReorderQueue = budgetReorderQueue.enqueue(request.copy(operationId = operation.operationId))
    }
    budgetReorderQueue = completeAppliedBudgetMutationAfterRefresh(request.copy(operationId = operation.operationId))
    persistCurrentBudgetReorderQueue()
    host.replacePendingWrites(localRepository.readPendingWrites(operation.budgetId))
    retryQueuedBudgetReorders()
}

private suspend fun BudgetEditQueue.confirmWithActual(
    localRepository: LocalBudgetRepository,
    operation: OutboxOperationEntity,
    request: QueuedBudgetReorder,
) {
    val refreshed =
        host.synchronize(
            operation.budgetId,
            request.encryptionPassword,
            retryQueuedBudgetMutations = false,
        )
    if (refreshed != null) {
        if (request.kind == BudgetReorderKind.SET_CATEGORY_CARRYOVER) {
            val payload = request.payloadJson?.let(::parseQueuePayload)
            val monthId = payload?.stringValue("month")
            val desiredCarryover = payload?.booleanValue("enabled")
            val refreshedMonth =
                monthId?.let { localRepository.findRecord(operation.budgetId, "budget-month", it) }
            val actualCarryover = budgetMonthCategoryCarryover(refreshedMonth?.remoteJson, request.itemId)
            if (desiredCarryover != null && actualCarryover == desiredCarryover) {
                val confirmedRequest = request.copy(operationId = operation.operationId)
                val confirmed =
                    runCatching {
                        localRepository.completeBudgetMutationAfterConfirmation(operation.operationId)
                    }.isSuccess
                if (confirmed) {
                    if (budgetReorderQueue.moves.none { it.operationId == operation.operationId }) {
                        budgetReorderQueue = budgetReorderQueue.enqueue(confirmedRequest)
                    }
                    budgetReorderQueue = completeAppliedBudgetMutationAfterRefresh(confirmedRequest)
                    persistCurrentBudgetReorderQueue()
                    host.replacePendingWrites(localRepository.readPendingWrites(operation.budgetId))
                    retryQueuedBudgetReorders()
                    return
                }
            }
        }
        host.replacePendingWrites(localRepository.readPendingWrites(operation.budgetId))
    }
}

private suspend fun BudgetEditQueue.requeueWithNewKey(
    operation: OutboxOperationEntity,
    request: QueuedBudgetReorder,
): Boolean {
    if (operation.status !in setOf("conflict", "rejected")) return false
    val refreshed =
        host.synchronize(
            operation.budgetId,
            request.encryptionPassword,
            retryQueuedBudgetMutations = false,
        )
    if (refreshed == null) return false
    val replacementOperationId = Uuid.random().toString()
    val replacement =
        budgetMutationRequestForRetry(
            request,
            operation.lastErrorCode,
            replacementOperationId,
        ) ?: return false
    val nextQueue =
        if (budgetReorderQueue.moves.any { it.operationId == operation.operationId }) {
            budgetReorderQueue.replaceOperationId(operation.operationId, replacementOperationId)
        } else {
            budgetReorderQueue.enqueue(replacement)
        }
    budgetQueuePersistenceMutex.withLock {
        persistBudgetReorderQueueNow(nextQueue, setOf(operation.operationId))
        budgetReorderQueue = nextQueue
    }
    return true
}

private suspend fun BudgetEditQueue.resumeAfterReview(
    localRepository: LocalBudgetRepository,
    operation: OutboxOperationEntity,
    request: QueuedBudgetReorder,
    operationId: String,
): Boolean {
    val retryRequest =
        budgetMutationRequestForRetry(
            request,
            operation.lastErrorCode,
            operation.operationId,
        ) ?: return false
    val retried =
        runCatching { localRepository.retryBudgetMutationAfterReview(operationId) }
            .getOrNull() ?: return false
    val resumedRequest = retryRequest.copy(operationId = retried.operationId)
    if (budgetReorderQueue.moves.none { it.operationId == retried.operationId }) {
        budgetReorderQueue = budgetReorderQueue.enqueue(resumedRequest)
    } else {
        budgetReorderQueue = budgetReorderQueue.resumeAfterConnection()
    }
    persistCurrentBudgetReorderQueue()
    return true
}

internal suspend fun BudgetEditQueue.correctRejectedCategoryRename(
    budgetId: String,
    operationId: String,
    correctedName: String,
    encryptionPassword: String?,
): Boolean {
    budgetMutationCorrectionMessage = null
    if (host.refuseWriteInReadOnlyMode()) {
        budgetMutationCorrectionMessage = host.transactionWriteMessage ?: "Read-only mode is on."
        return false
    }
    if (isPersistingBudgetMutation || isCorrectingBudgetMutation) {
        budgetMutationCorrectionMessage = "Another category change is being saved. Try again in a moment."
        return false
    }
    val localRepository = host.repository
    if (localRepository == null) {
        budgetMutationCorrectionMessage = "Budget data isn’t available on this device. Reopen the budget and try again."
        return false
    }
    if (!restoreBudgetReorderQueue()) {
        budgetMutationCorrectionMessage = BUDGET_QUEUE_RESTORE_ERROR
        return false
    }
    val operation = localRepository.findWrite(operationId)
    if (
        operation == null ||
        operation.budgetId != budgetId ||
        operation.status != "rejected" ||
        operation.action != "RENAME_CATEGORY"
    ) {
        budgetMutationCorrectionMessage = "This rejected category rename is no longer available. Refresh Pending Changes."
        return false
    }
    val request = decodeBudgetReorderQueue(operation.patchJson).moves.singleOrNull()
    if (request == null || request.kind != BudgetReorderKind.RENAME_CATEGORY || request.itemId != operation.recordId) {
        budgetMutationCorrectionMessage = "Tandem couldn’t read this category rename. The rejected change is still available to review."
        return false
    }
    val cleanName = correctedName.trim()
    if (cleanName.isBlank() || cleanName.length > 100) {
        budgetMutationCorrectionMessage = "Enter a category name up to 100 characters."
        return false
    }
    if (cleanName == request.name) {
        budgetMutationCorrectionMessage = "Enter a different category name before trying again."
        return false
    }
    val password = encryptionPassword?.takeIf(String::isNotBlank) ?: request.encryptionPassword
    if (request.requiresEncryptionPassword && password.isNullOrBlank()) {
        budgetMutationCorrectionMessage = "Unlock this encrypted budget before trying again."
        return false
    }

    val replacement =
        request.copy(
            name = cleanName,
            operationId = Uuid.random().toString(),
            encryptionPassword = password,
            refreshBeforeSend = true,
        )
    return saveCorrectedRename(localRepository, budgetId, operationId, replacement)
}

private suspend fun BudgetEditQueue.saveCorrectedRename(
    localRepository: LocalBudgetRepository,
    budgetId: String,
    operationId: String,
    replacement: QueuedBudgetReorder,
): Boolean {
    isCorrectingBudgetMutation = true
    isPersistingBudgetMutation = true
    return try {
        budgetQueuePersistenceMutex.withLock {
            val next = budgetReorderQueueState.replaceRejectedOperation(operationId, replacement)
            persistBudgetReorderQueueNow(next, setOf(operationId))
            budgetReorderQueueState = next
        }
        runCatching { localRepository.readPendingWrites(budgetId) }.onSuccess { host.replacePendingWrites(it) }
        retryQueuedBudgetReorders()
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        budgetMutationCorrectionMessage =
            localStorageFailureMessage(
                error,
                "Couldn’t save the corrected rename on this device. The rejected change is still available to review.",
            )
        false
    } finally {
        isPersistingBudgetMutation = false
        isCorrectingBudgetMutation = false
    }
}

internal fun BudgetEditQueue.clearBudgetMutationCorrectionMessage() {
    budgetMutationCorrectionMessage = null
}

internal fun BudgetEditQueue.completeAppliedBudgetMutationAfterRefresh(request: QueuedBudgetReorder): BudgetReorderQueueState {
    val createdGroupId =
        if (request.kind == BudgetReorderKind.CREATE_CATEGORY_GROUP) {
            host.categoryGroupRecords.firstOrNull { it.displayName == request.name }?.recordId
        } else {
            null
        }
    if (createdGroupId != null) return budgetReorderQueue.completeCategoryGroupCreate(request, createdGroupId)

    val createdCategoryId =
        if (request.kind == BudgetReorderKind.CREATE_CATEGORY) {
            host.categoryRecords
                .firstOrNull { record ->
                    record.displayName == request.name &&
                        record.recordBody()?.let { body ->
                            body.stringValue("group") == request.parentGroupId || body.stringValue("group_id") == request.parentGroupId
                        } == true
                }?.recordId
        } else {
            null
        }
    if (createdCategoryId != null) return budgetReorderQueue.completeCategoryCreate(request, createdCategoryId)

    if (request.kind in setOf(BudgetReorderKind.CREATE_CATEGORY_GROUP, BudgetReorderKind.CREATE_CATEGORY)) {
        return budgetReorderQueue.awaitRefresh(request, "Saved in Actual. Refreshing the new budget item.")
    }
    return budgetReorderQueue.complete(request, refreshFailed = false, message = null)
}

internal fun BudgetEditQueue.clearBudgetReorderMessage(
    budgetId: String,
    scopeId: String,
) {
    budgetReorderQueue = budgetReorderQueue.clearMessage(budgetId, scopeId)
}

internal fun BudgetEditQueue.clearBudgetReorderMessageAfterDelay(
    budgetId: String,
    scopeId: String,
    message: String,
) {
    host.sessionScope.launch {
        delay(6_000)
        val current = budgetReorderQueue.statusFor(budgetId, scopeId)
        if (current.message == message && !budgetReorderQueue.waitingForConnection) {
            budgetReorderQueue = budgetReorderQueue.clearMessage(budgetId, scopeId)
        }
    }
}
