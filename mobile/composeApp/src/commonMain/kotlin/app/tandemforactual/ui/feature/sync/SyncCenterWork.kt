@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.budgetMutationReviewsFor
import app.tandemforactual.ui.core.session.budget.pendingBudgetMutationsFor
import app.tandemforactual.ui.core.state.AppState

/** The selected budget's sync work, split by what needs review and what is waiting to send. */
internal data class SyncCenterWork(
    val transactionsNeedingReview: List<OutboxOperationEntity>,
    val transactionsWaiting: List<OutboxOperationEntity>,
    val budgetMutations: List<QueuedBudgetReorder>,
    val budgetMutationReviews: List<OutboxOperationEntity>,
    val schedulesNeedingReview: List<OutboxOperationEntity>,
    val schedulesWaiting: List<OutboxOperationEntity>,
) {
    val attentionCount = transactionsNeedingReview.size + budgetMutationReviews.size + schedulesNeedingReview.size
    val waitingCount = transactionsWaiting.size + budgetMutations.size + schedulesWaiting.size
}

internal fun syncCenterWork(
    state: AppState,
    mobileSession: TandemMobileSession,
): SyncCenterWork {
    val transactionsNeedingReview =
        mobileSession.pendingWrites.filter {
            it.budgetId == state.selectedBudgetId &&
                it.recordType == "transaction" &&
                it.status in setOf("conflict", "uncertain", "rejected")
        }
    val transactionsWaiting =
        mobileSession.pendingWrites.filter {
            it.budgetId == state.selectedBudgetId && it.recordType == "transaction" && it.status in setOf("queued", "sending")
        }
    val budgetMutations = mobileSession.budgetQueue.pendingBudgetMutationsFor(state.selectedBudgetId)
    val budgetMutationReviews = mobileSession.budgetQueue.budgetMutationReviewsFor(state.selectedBudgetId)
    val scheduleActions =
        mobileSession.pendingWrites.filter {
            it.budgetId == state.selectedBudgetId &&
                it.recordType == "schedule-action" &&
                it.status in setOf("queued", "sending", "uncertain", "conflict", "rejected")
        }
    val schedulesNeedingReview = scheduleActions.filter { it.status in setOf("uncertain", "conflict", "rejected") }
    val schedulesWaiting = scheduleActions.filter { it.status in setOf("queued", "sending") }
    return SyncCenterWork(
        transactionsNeedingReview,
        transactionsWaiting,
        budgetMutations,
        budgetMutationReviews,
        schedulesNeedingReview,
        schedulesWaiting,
    )
}
