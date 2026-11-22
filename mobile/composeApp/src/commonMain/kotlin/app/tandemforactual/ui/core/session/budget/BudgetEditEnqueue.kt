package app.tandemforactual.ui.core.session.budget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.tandemforactual.ui.core.session.BUDGET_ORDER_FLUSH_INTERVAL_MS
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits
import app.tandemforactual.ui.core.session.enqueueIfMissing
import app.tandemforactual.ui.core.session.enqueueOrderDraft
import app.tandemforactual.ui.core.session.hasOrderDraftsAtHead
import app.tandemforactual.ui.core.session.hasWorkBehindOrderDrafts
import app.tandemforactual.ui.core.session.isOrderDraft
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

/**
 * Queues a reorder. With [baseOrder] (the server order the screen showed before
 * this drop), the change is held on the device and sent with other reorders at
 * the next flush; see [requestBudgetOrderFlush].
 */
internal suspend fun BudgetEditQueue.enqueueBudgetReorder(
    budgetId: String,
    scopeId: String,
    kind: BudgetReorderKind,
    itemId: String,
    targetId: String?,
    desiredOrder: List<String>,
    encryptionPassword: String?,
    baseOrder: List<String> = emptyList(),
): Boolean =
    persistBudgetReorderQueueMutation { queue ->
        val move = QueuedBudgetReorder(budgetId, scopeId, kind, itemId, targetId, desiredOrder, encryptionPassword, baseOrder)
        if (move.isOrderDraft()) queue.enqueueOrderDraft(move) else queue.enqueueIfMissing(move)
    }.also { queued -> if (queued) noteOrderDraftQueued() }

/** Sends held reorders now instead of waiting for the next periodic flush. */
internal fun BudgetEditQueue.requestBudgetOrderFlush() {
    budgetOrderFlushSignal += 1
}

internal fun BudgetEditQueue.noteOrderDraftQueued() {
    if (orderDraftsHeldSinceEpochMs == null && budgetReorderQueue.hasOrderDraftsAtHead()) {
        orderDraftsHeldSinceEpochMs = host.nowEpochMillis()
    }
}

/**
 * Waits until held reorder drafts at the head of the queue should be sent: after
 * [BUDGET_ORDER_FLUSH_INTERVAL_MS], on [requestBudgetOrderFlush], or as soon as
 * another kind of change is queued behind them.
 */
internal suspend fun BudgetEditQueue.awaitBudgetOrderFlush() {
    val heldSince = orderDraftsHeldSinceEpochMs ?: host.nowEpochMillis().also { orderDraftsHeldSinceEpochMs = it }
    val observedFlushSignal = budgetOrderFlushSignal
    val remaining = (heldSince + BUDGET_ORDER_FLUSH_INTERVAL_MS - host.nowEpochMillis()).coerceAtLeast(0)
    withTimeoutOrNull(remaining) {
        snapshotFlow {
            host.isSigningOut ||
                budgetOrderFlushSignal != observedFlushSignal ||
                !budgetReorderQueue.hasOrderDraftsAtHead() ||
                budgetReorderQueue.hasWorkBehindOrderDrafts()
        }.first { it }
    }
}

internal suspend fun BudgetEditQueue.enqueueOfflineBudgetMutation(
    budgetId: String,
    kind: BudgetReorderKind,
    itemId: String,
    scopeId: String,
    payload: JsonObject,
    encryptionPassword: String?,
    name: String? = null,
): Boolean {
    if (budgetId.isBlank() || itemId.isBlank()) {
        host.feedback.budgetActionMessage = "Open a budget before making this change."
        return false
    }
    if (host.refuseWriteInReadOnlyMode()) {
        host.feedback.budgetActionMessage = host.transactionWriteMessage
        return false
    }
    if (!host.isCurrentIdentityCacheOwner()) {
        host.feedback.budgetActionMessage = "Sign in to the server and account that own this budget before changing it."
        return false
    }
    val queued =
        persistBudgetReorderQueueMutation { queue ->
            queue.enqueueIfMissing(
                QueuedBudgetReorder(
                    budgetId = budgetId,
                    scopeId = scopeId,
                    kind = kind,
                    itemId = itemId,
                    targetId = null,
                    desiredOrder = emptyList(),
                    encryptionPassword = encryptionPassword,
                    name = name,
                    payloadJson = payload.toString(),
                ),
            )
        }
    if (queued) {
        if (!host.isServerReachable) {
            host.feedback.setBudgetActionMessage(
                "Saved on this device. It will sync when Actual is reachable.",
                InlineMessageKind.INFO,
            )
        }
        retryQueuedBudgetReorders(budgetId, encryptionPassword)
    }
    return queued
}
