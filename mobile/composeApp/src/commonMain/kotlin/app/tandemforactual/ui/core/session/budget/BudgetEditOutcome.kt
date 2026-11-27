package app.tandemforactual.ui.core.session.budget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.store.completeBudgetMutation
import app.tandemforactual.data.store.markBudgetMutationConflict
import app.tandemforactual.data.store.markBudgetMutationRejected
import app.tandemforactual.data.store.markBudgetMutationUncertain
import app.tandemforactual.data.store.remapPendingAccountReferences
import app.tandemforactual.data.store.remapPendingCategoryReferences
import app.tandemforactual.data.store.remapPendingPayeeReferences
import app.tandemforactual.data.store.returnBudgetMutationToQueue
import app.tandemforactual.data.store.saveLocalPayee
import app.tandemforactual.ui.core.session.BudgetMutationFailureDisposition
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.BudgetReorderQueueState
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.awaitCategoryCreateRefresh
import app.tandemforactual.ui.core.session.awaitCategoryGroupRefresh
import app.tandemforactual.ui.core.session.awaitRefresh
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits
import app.tandemforactual.ui.core.session.budgetMutationFailureDisposition
import app.tandemforactual.ui.core.session.budgetMutationFailureMessage
import app.tandemforactual.ui.core.session.budgetMutationNeedsRefresh
import app.tandemforactual.ui.core.session.complete
import app.tandemforactual.ui.core.session.completeCategoryCreate
import app.tandemforactual.ui.core.session.completeCategoryGroupCreate
import app.tandemforactual.ui.core.session.failHead
import app.tandemforactual.ui.core.session.holdForReview
import app.tandemforactual.ui.core.session.shouldCompleteBudgetMutationAfterOutcome
import app.tandemforactual.ui.core.session.waitForConnection
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.stringValue

/** Outcome of a sent edit, as reported by Actual. */
internal data class BudgetSendOutcome(
    val refreshFailed: Boolean,
    val operationOutcomeUncertain: Boolean,
    val operationResultUnavailable: Boolean,
    val resultRefreshSucceeded: Boolean,
)

/** After an edit is sent: records the ids Actual gave anything new, marks the stored mutation, and moves the queue on. */
internal suspend fun BudgetEditQueue.settleAfterSend(
    request: QueuedBudgetReorder,
    moved: Boolean,
    isRefreshOnlyRequest: Boolean,
    createdCategoryGroupId: String?,
    createdCategoryId: String?,
    createdPayeeId: String?,
    createdAccountId: String?,
) {
    remapCreatedIds(request, createdCategoryId, createdPayeeId, createdAccountId)
    val outcome = recordStoredOutcome(request, moved, isRefreshOnlyRequest)
    val next =
        createdRecordQueue(
            request,
            moved,
            outcome.refreshFailed,
            createdCategoryGroupId,
            createdCategoryId,
            createdPayeeId,
            createdAccountId,
        )
    if (next != null) {
        budgetReorderQueue = next
    } else {
        advanceQueueAfterSend(request, moved, outcome)
    }
    if (moved &&
        request.kind in
        setOf(
            BudgetReorderKind.CREATE_CATEGORY,
            BudgetReorderKind.REFRESH_CATEGORY_CREATE,
            BudgetReorderKind.CREATE_PAYEE,
            BudgetReorderKind.CREATE_ACCOUNT,
        ) &&
        !hasPendingTransactionDependency(request.budgetId)
    ) {
        host.synchronize(request.budgetId, request.encryptionPassword, background = true)
    }
}

private suspend fun BudgetEditQueue.remapCreatedIds(
    request: QueuedBudgetReorder,
    createdCategoryIdAfterAction: String?,
    createdPayeeIdAfterAction: String?,
    createdAccountIdAfterAction: String?,
) {
    if (createdCategoryIdAfterAction != null) {
        host.repository?.remapPendingCategoryReferences(request.budgetId, request.itemId, createdCategoryIdAfterAction)
    }
    if (createdPayeeIdAfterAction != null) {
        host.repository?.remapPendingPayeeReferences(request.budgetId, request.itemId, createdPayeeIdAfterAction)
        request.name?.let { host.repository?.saveLocalPayee(request.budgetId, createdPayeeIdAfterAction, it) }
    }
    if (createdAccountIdAfterAction != null) {
        host.repository?.remapPendingAccountReferences(request.budgetId, request.itemId, createdAccountIdAfterAction)
        edits.remapQueuedAccountReferences(request.operationId, request.itemId, createdAccountIdAfterAction)
        host.planning.moveAccountKind(request.budgetId, request.itemId, createdAccountIdAfterAction)
    }
}

private suspend fun BudgetEditQueue.recordStoredOutcome(
    request: QueuedBudgetReorder,
    moved: Boolean,
    isRefreshOnlyRequest: Boolean,
): BudgetSendOutcome {
    val refreshFailed = host.feedback.budgetActionWasAppliedButRefreshFailed
    val failureDisposition = budgetMutationFailureDisposition(host.feedback.lastBudgetActionErrorCode)
    val operationOutcomeUncertain =
        failureDisposition in
            setOf(
                BudgetMutationFailureDisposition.OPERATION_IN_PROGRESS,
                BudgetMutationFailureDisposition.OPERATION_UNCERTAIN,
            ) ||
            host.feedback.lastBudgetActionHttpStatus == 408 ||
            host.feedback.lastBudgetActionHttpStatus in 500..599
    val operationResultUnavailable = budgetMutationNeedsRefresh(host.feedback.lastBudgetActionErrorCode)
    val resultRefreshSucceeded =
        operationResultUnavailable &&
            host.withoutBudgetActionFeedback {
                host.synchronize(
                    request.budgetId,
                    request.encryptionPassword,
                    retryQueuedBudgetMutations = false,
                    background = true,
                ) != null
            }
    if (
        shouldCompleteBudgetMutationAfterOutcome(
            mutationApplied = moved,
            refreshFailedAfterMutation = refreshFailed,
            resultUnavailable = operationResultUnavailable,
            resultRefreshSucceeded = resultRefreshSucceeded,
        )
    ) {
        host.repository?.completeBudgetMutation(request.operationId)
    } else if (operationOutcomeUncertain) {
        host.repository?.markBudgetMutationUncertain(
            request.operationId,
            host.feedback.lastBudgetActionErrorCode ?: "http-${host.feedback.lastBudgetActionHttpStatus}",
        )
    } else if (isRefreshOnlyRequest) {
        host.repository?.returnBudgetMutationToQueue(request.operationId, "refresh-unavailable")
    } else if (host.feedback.lastBudgetActionErrorCode == "operation_rejected") {
        host.repository?.markBudgetMutationRejected(request.operationId, host.feedback.lastBudgetActionErrorCode!!)
    } else if (host.feedback.lastBudgetActionHttpStatus == 409) {
        host.repository?.markBudgetMutationConflict(request.operationId, host.feedback.lastBudgetActionErrorCode ?: "http-409")
    } else if (host.feedback.budgetActionShouldRetry) {
        host.repository?.returnBudgetMutationToQueue(request.operationId, host.feedback.lastBudgetActionErrorCode ?: "retry-later")
    } else {
        host.repository?.markBudgetMutationRejected(
            request.operationId,
            host.feedback.lastBudgetActionErrorCode ?: "local-validation",
        )
    }
    return BudgetSendOutcome(refreshFailed, operationOutcomeUncertain, operationResultUnavailable, resultRefreshSucceeded)
}

/** Next queue state for creates, renames and their refreshes, or null when the general rules apply. */
private fun BudgetEditQueue.createdRecordQueue(
    request: QueuedBudgetReorder,
    moved: Boolean,
    refreshFailed: Boolean,
    createdCategoryGroupIdAfterAction: String?,
    createdCategoryIdAfterAction: String?,
    createdPayeeIdAfterAction: String?,
    createdAccountIdAfterAction: String?,
): BudgetReorderQueueState? {
    var next: BudgetReorderQueueState? = null
    if (request.kind == BudgetReorderKind.CREATE_CATEGORY_GROUP && refreshFailed && createdCategoryGroupIdAfterAction != null) {
        next =
            budgetReorderQueue.awaitCategoryGroupRefresh(
                request,
                createdCategoryGroupIdAfterAction,
                BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE,
                "Created in Actual. Waiting for the budget to refresh.",
            )
    } else if (request.kind == BudgetReorderKind.RENAME_CATEGORY_GROUP && refreshFailed) {
        next =
            budgetReorderQueue.awaitCategoryGroupRefresh(
                request,
                request.itemId,
                BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME,
                "Renamed in Actual. Waiting for the budget to refresh.",
            )
    } else if (request.kind == BudgetReorderKind.RENAME_CATEGORY && refreshFailed) {
        next = budgetReorderQueue.awaitRefresh(request, "Renamed in Actual. Waiting for the budget to refresh.")
    } else if (request.kind == BudgetReorderKind.CREATE_CATEGORY_GROUP && moved && createdCategoryGroupIdAfterAction != null) {
        next = budgetReorderQueue.completeCategoryGroupCreate(request, createdCategoryGroupIdAfterAction)
    } else if (request.kind == BudgetReorderKind.CREATE_CATEGORY && refreshFailed && createdCategoryIdAfterAction != null) {
        next =
            budgetReorderQueue.awaitCategoryCreateRefresh(
                request,
                createdCategoryIdAfterAction,
                "Created in Actual. Waiting for the budget to refresh.",
            )
    } else if (request.kind == BudgetReorderKind.CREATE_CATEGORY && moved && createdCategoryIdAfterAction != null) {
        next = budgetReorderQueue.completeCategoryCreate(request, createdCategoryIdAfterAction)
    } else if (request.kind == BudgetReorderKind.CREATE_PAYEE && createdPayeeIdAfterAction != null) {
        next =
            budgetReorderQueue.complete(
                request,
                refreshFailed,
                "Added in Actual. Waiting for the budget to refresh.",
            )
    } else if (request.kind == BudgetReorderKind.CREATE_ACCOUNT && createdAccountIdAfterAction != null && refreshFailed) {
        next = budgetReorderQueue.awaitRefresh(request, "Created in Actual. Waiting for the budget to refresh.")
    } else if (request.kind == BudgetReorderKind.CREATE_ACCOUNT && createdAccountIdAfterAction != null) {
        next = budgetReorderQueue.complete(request, refreshFailed = false, message = null)
    } else if (request.refreshOnly && request.kind == BudgetReorderKind.CREATE_CATEGORY_GROUP && moved) {
        val actualGroupId = host.categoryGroupRecords.firstOrNull { it.displayName == request.name }?.recordId
        next =
            if (actualGroupId != null) {
                budgetReorderQueue.completeCategoryGroupCreate(request, actualGroupId)
            } else {
                budgetReorderQueue.awaitRefresh(request, "Saved in Actual. Refreshing the new category group.")
            }
    } else if (request.refreshOnly && request.kind == BudgetReorderKind.CREATE_CATEGORY && moved) {
        val actualCategoryId =
            host.categoryRecords
                .firstOrNull { record ->
                    record.displayName == request.name &&
                        record.recordBody()?.let { body ->
                            body.stringValue("group") == request.parentGroupId ||
                                body.stringValue("group_id") == request.parentGroupId
                        } == true
                }?.recordId
        next =
            if (actualCategoryId != null) {
                budgetReorderQueue.completeCategoryCreate(request, actualCategoryId)
            } else {
                budgetReorderQueue.awaitRefresh(request, "Saved in Actual. Refreshing the new category.")
            }
    }
    return next
}

private suspend fun BudgetEditQueue.advanceQueueAfterSend(
    request: QueuedBudgetReorder,
    moved: Boolean,
    outcome: BudgetSendOutcome,
) {
    val (refreshFailed, operationOutcomeUncertain, operationResultUnavailable, resultRefreshSucceeded) = outcome
    if (refreshFailed) {
        // The mutation has already been accepted. Keep the optimistic
        // state and show its retryable refresh status in the queue;
        // the sync transport error belongs on the cloud indicator.
        budgetReorderQueue = budgetReorderQueue.awaitRefresh(request, "Saved in Actual. Waiting for the budget to refresh.")
    } else if (operationResultUnavailable) {
        budgetReorderQueue =
            if (resultRefreshSucceeded) {
                completeAppliedBudgetMutationAfterRefresh(request)
            } else {
                budgetReorderQueue.awaitRefresh(request, "Saved in Actual. Waiting for the budget to refresh.")
            }
    } else if (operationOutcomeUncertain) {
        val message = budgetMutationFailureMessage(host.feedback.lastBudgetActionErrorCode)
        budgetReorderQueue =
            budgetReorderQueue.holdForReview(
                request,
                message,
            )
    } else if (host.feedback.lastBudgetActionHttpStatus == 409) {
        host.withoutBudgetActionFeedback {
            host.synchronize(
                request.budgetId,
                request.encryptionPassword,
                retryQueuedBudgetMutations = false,
                background = true,
            )
        }
        val message = budgetMutationFailureMessage(host.feedback.lastBudgetActionErrorCode)
        budgetReorderQueue = budgetReorderQueue.failHead(request, message)
    } else if ((
            request.refreshOnly ||
                request.kind in
                setOf(
                    BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE,
                    BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME,
                    BudgetReorderKind.REFRESH_CATEGORY_CREATE,
                    BudgetReorderKind.REFRESH_CATEGORY_RENAME,
                )
        ) &&
        !moved
    ) {
        budgetReorderQueue =
            budgetReorderQueue.waitForConnection(
                request,
                "Waiting for a connection to refresh this category group.",
            )
    } else if (moved) {
        budgetReorderQueue = budgetReorderQueue.complete(request, refreshFailed, null)
    } else if (host.feedback.budgetActionShouldRetry || !host.isServerReachable || !host.apiClient.isAuthenticated) {
        budgetReorderQueue =
            budgetReorderQueue.waitForConnection(
                request,
                "Saved on this device. Waiting for Tandem to recover.",
            )
    } else {
        val failure = budgetMutationFailureMessage(request.kind, null)
        budgetReorderQueue = budgetReorderQueue.failHead(request, failure)
        clearBudgetReorderMessageAfterDelay(request.budgetId, request.scopeId, failure)
    }
}
