package app.tandemforactual.ui.core.session.budget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.tandemforactual.data.store.beginBudgetMutation
import app.tandemforactual.data.store.readPendingWrites
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits
import app.tandemforactual.ui.core.session.budgetMutationFailureMessage
import app.tandemforactual.ui.core.session.failHead
import app.tandemforactual.ui.core.session.foldOrderDrafts
import app.tandemforactual.ui.core.session.hasOrderDraftsAtHead
import app.tandemforactual.ui.core.session.holdForReview
import app.tandemforactual.ui.core.session.isOrderDraft
import app.tandemforactual.ui.core.session.resumeAfterConnection
import app.tandemforactual.ui.core.session.waitForConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

/** Runs outside editor screens so leaving a route cannot cancel unsent reorder operations. */
internal suspend fun BudgetEditQueue.processQueuedBudgetReorders() {
    if (host.isSigningOut) return
    while (!host.isSigningOut && !restoreBudgetReorderQueue()) delay(1_000)
    if (host.isSigningOut) return
    var observedRetrySignal = budgetReorderRetrySignal
    while (true) {
        var request = snapshotFlow { budgetReorderQueue.moves.firstOrNull() }.first { it != null } ?: continue
        snapshotFlow { isCorrectingBudgetMutation }.first { !it }
        if (budgetReorderQueue.moves.firstOrNull() != request) continue
        if (request.isOrderDraft()) {
            // Reorders wait on the device and go out together in one request.
            awaitBudgetOrderFlush()
            if (host.isSigningOut) return
            if (!budgetReorderQueue.hasOrderDraftsAtHead()) continue
            snapshotFlow { isPersistingBudgetMutation }.first { !it }
            try {
                budgetQueuePersistenceMutex.withLock {
                    val folded = budgetReorderQueueState.foldOrderDrafts()
                    persistBudgetReorderQueueNow(folded)
                    budgetReorderQueueState = folded
                }
                orderDraftsHeldSinceEpochMs = null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                delay(1_000)
            }
            continue
        }
        persistCurrentBudgetReorderQueue()
        if (request.requiresEncryptionPassword && request.encryptionPassword.isNullOrBlank()) {
            observedRetrySignal = snapshotFlow { budgetReorderRetrySignal }.first { it != observedRetrySignal }
            continue
        }
        if (budgetReorderQueue.waitingForConnection) {
            observedRetrySignal = snapshotFlow { budgetReorderRetrySignal }.first { it != observedRetrySignal }
            budgetReorderQueue = budgetReorderQueue.resumeAfterConnection()
        } else {
            observedRetrySignal = budgetReorderRetrySignal
        }
        if (!host.isServerReachable || !host.apiClient.isAuthenticated) {
            budgetReorderQueue =
                budgetReorderQueue.waitForConnection(
                    request,
                    "Saved on this device. Waiting for a connection to Tandem.",
                )
            observedRetrySignal = snapshotFlow { budgetReorderRetrySignal }.first { it != observedRetrySignal }
            budgetReorderQueue = budgetReorderQueue.resumeAfterConnection()
            continue
        }
        if (host.missingServerFeature(request.kind) != null) {
            budgetReorderQueue =
                budgetReorderQueue.waitForConnection(
                    request,
                    "Saved on this device. Waiting for your Tandem server to be updated.",
                )
            observedRetrySignal = snapshotFlow { budgetReorderRetrySignal }.first { it != observedRetrySignal }
            budgetReorderQueue = budgetReorderQueue.resumeAfterConnection()
            continue
        }
        snapshotFlow { isCorrectingBudgetMutation || host.isPerformingBudgetAction || host.isSyncing }.first { !it }
        if (budgetReorderQueue.moves.firstOrNull() != request) continue

        if (request.refreshBeforeSend) {
            val refreshed =
                host.synchronize(
                    budgetId = request.budgetId,
                    encryptionPassword = request.encryptionPassword,
                    sendQueuedWrites = false,
                    retryQueuedBudgetMutations = false,
                    background = true,
                )
            if (refreshed == null) {
                budgetReorderQueue =
                    budgetReorderQueue.waitForConnection(
                        request,
                        "Saved on this device. Waiting to refresh Actual before trying again.",
                    )
                persistCurrentBudgetReorderQueue()
                continue
            }
            val refreshedRequest = budgetReorderQueue.moves.firstOrNull { it.operationId == request.operationId } ?: continue
            val readyRequest = refreshedRequest.copy(refreshBeforeSend = false)
            try {
                budgetQueuePersistenceMutex.withLock {
                    val next =
                        budgetReorderQueueState.copy(
                            moves =
                                budgetReorderQueueState.moves.map { move ->
                                    if (move.operationId == request.operationId) readyRequest else move
                                },
                        )
                    persistBudgetReorderQueueNow(next)
                    budgetReorderQueueState = next
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                budgetReorderQueue =
                    budgetReorderQueue.waitForConnection(
                        request,
                        "Saved on this device. Tandem couldn’t save the refreshed change before retrying.",
                    )
                persistCurrentBudgetReorderQueue()
                continue
            }
            request = readyRequest
        }

        var mutationStarted = host.repository?.beginBudgetMutation(request.operationId) == true
        if (!mutationStarted) {
            persistCurrentBudgetReorderQueue()
            mutationStarted = host.repository?.beginBudgetMutation(request.operationId) == true
        }
        if (!mutationStarted) {
            val stored = host.repository?.readPendingWrites(request.budgetId)?.firstOrNull { it.operationId == request.operationId }
            val stateMessage =
                when (stored?.status) {
                    "uncertain", "conflict", "rejected" -> budgetMutationFailureMessage(stored.lastErrorCode)
                    "discarded", "committed" -> "This change is no longer pending."
                    else -> null
                }
            if (stateMessage != null) {
                budgetReorderQueue =
                    if (stored?.status in setOf("uncertain", "conflict")) {
                        budgetReorderQueue.holdForReview(request, stateMessage)
                    } else {
                        budgetReorderQueue.failHead(request, stateMessage)
                    }
                persistCurrentBudgetReorderQueue()
            } else {
                delay(100)
            }
            continue
        }

        var createdCategoryGroupId: String? = null
        var createdCategoryId: String? = null
        var createdPayeeId: String? = null
        var createdAccountId: String? = null
        host.feedback.budgetActionWasAppliedButRefreshFailed = false
        host.feedback.budgetActionShouldRetry = false
        host.feedback.lastBudgetActionHttpStatus = null
        host.feedback.lastBudgetActionErrorCode = null
        val isRefreshOnlyRequest =
            request.refreshOnly ||
                request.kind in
                setOf(
                    BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE,
                    BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME,
                    BudgetReorderKind.REFRESH_CATEGORY_CREATE,
                    BudgetReorderKind.REFRESH_CATEGORY_RENAME,
                )
        val moved =
            host.withoutBudgetActionFeedback {
                if (isRefreshOnlyRequest) {
                    host.synchronize(request.budgetId, request.encryptionPassword, background = true) != null
                } else {
                    val dispatched = dispatch(request)
                    createdCategoryGroupId = dispatched.categoryGroupId
                    createdCategoryId = dispatched.categoryId
                    createdPayeeId = dispatched.payeeId
                    createdAccountId = dispatched.accountId
                    dispatched.moved
                }
            }
        settleAfterSend(request, moved, isRefreshOnlyRequest, createdCategoryGroupId, createdCategoryId, createdPayeeId, createdAccountId)
        persistCurrentBudgetReorderQueue()
    }
}
