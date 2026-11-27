package app.tandemforactual.ui.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.state.AppRouteEntry
import app.tandemforactual.ui.design.feedback.InlineMessageKind

internal data class SyncPresentationState(
    val isSyncing: Boolean = false,
    val error: String? = null,
    val errorCode: String? = null,
    val errorStatus: Int? = null,
) {
    fun begin(): SyncPresentationState = copy(isSyncing = true)

    fun succeeded(): SyncPresentationState = SyncPresentationState()

    fun failed(
        message: String,
        code: String? = null,
        status: Int? = null,
    ): SyncPresentationState = SyncPresentationState(error = message, errorCode = code, errorStatus = status)

    fun stopped(): SyncPresentationState = copy(isSyncing = false)
}

internal data class TransactionEditDetails(
    val accountId: String,
    val payeeId: String?,
    val categoryId: String?,
    val date: String,
)

internal data class ConflictResolutionFeedback(
    val operationId: String,
    val message: String,
    val saved: Boolean = false,
)

internal data class UndoNotice(
    val id: Long,
    val message: String,
    val budgetId: String,
    val transactionIds: Set<String>,
    /** Undoes an action other than a transaction delete; deletes are undone by [transactionIds]. */
    val undo: (suspend () -> Unit)? = null,
    val title: String? = null,
    val kind: InlineMessageKind = InlineMessageKind.INFO,
    val detail: String? = null,
)

internal data class BudgetActionFeedback(
    val text: String,
    val kind: InlineMessageKind,
    val ownerRoute: AppRouteEntry,
    val actionLabel: String?,
)

/** One budget's row in the Sync Center. */
internal data class BudgetSyncOverview(
    val budgetId: String,
    val name: String,
    val open: Boolean,
    val locked: Boolean,
    val lastSyncedAtEpochMs: Long?,
    val failed: Boolean,
    val waiting: Int,
    val needsReview: Int,
)
