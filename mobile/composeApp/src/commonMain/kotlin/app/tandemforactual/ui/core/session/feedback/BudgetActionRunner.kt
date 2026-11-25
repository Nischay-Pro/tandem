package app.tandemforactual.ui.core.session.feedback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.ui.core.session.BudgetActionFeedbackState
import app.tandemforactual.ui.core.session.SessionHost
import app.tandemforactual.ui.core.session.feedback.BudgetActionRunner
import app.tandemforactual.ui.core.session.shouldKeepBudgetMutationQueued
import app.tandemforactual.ui.core.session.userFacingMessage
import app.tandemforactual.ui.core.state.AppRouteEntry
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Runs a budget change against Actual and reports how it went: the message for the screen that asked, a retry,
 * and whether the change landed but the refresh after it failed.
 */
internal class BudgetActionRunner(
    private val host: SessionHost,
) {
    private var budgetActionRetryInFlight = false

    internal val budgetActionFeedbackState = BudgetActionFeedbackState()

    internal var suppressBudgetActionFeedback = false

    internal val currentBudgetActionMessageOwner: AppRouteEntry?
        get() = if (suppressBudgetActionFeedback) null else budgetActionFeedbackState.currentRouteEntry

    var budgetActionMessage: String?
        get() = budgetActionFeedbackState.currentRouteEntry?.let { budgetActionFeedbackState.messageFor(it)?.text }
        set(value) {
            if (value == null) {
                budgetActionFeedbackState.clear(currentBudgetActionMessageOwner)
                budgetActionRetryAction = null
            } else {
                budgetActionRetryAction = null
                budgetActionFeedbackState.show(value, InlineMessageKind.ERROR, currentBudgetActionMessageOwner)
            }
        }

    val budgetActionMessageKind: InlineMessageKind
        get() {
            val routeEntry = budgetActionFeedbackState.currentRouteEntry
            return routeEntry?.let { budgetActionFeedbackState.messageFor(it)?.kind } ?: InlineMessageKind.ERROR
        }

    val budgetActionMessageActionLabel: String?
        get() {
            val routeEntry = budgetActionFeedbackState.currentRouteEntry
            return routeEntry?.let { budgetActionFeedbackState.messageFor(it)?.actionLabel }
        }

    fun retryBudgetAction() {
        if (isPerformingBudgetAction || budgetActionRetryInFlight) return
        val retry = budgetActionRetryAction ?: return
        budgetActionRetryInFlight = true
        host.sessionScope.launch {
            try {
                retry()
            } finally {
                budgetActionRetryInFlight = false
            }
        }
    }

    internal var budgetActionRetryAction: (suspend () -> Boolean)? = null

    var budgetActionWasAppliedButRefreshFailed by mutableStateOf(false)

    internal var budgetActionShouldRetry by mutableStateOf(false)

    internal var lastBudgetActionHttpStatus: Int? = null

    internal var lastBudgetActionErrorCode: String? = null

    var isPerformingBudgetAction by mutableStateOf(false)

    fun clearBudgetActionMessage() {
        budgetActionFeedbackState.clear(budgetActionFeedbackState.currentRouteEntry)
        budgetActionRetryAction = null
    }

    fun setBudgetActionRoute(routeEntry: AppRouteEntry) {
        if (budgetActionFeedbackState.currentRouteEntry != routeEntry) budgetActionRetryAction = null
        budgetActionFeedbackState.changeRoute(routeEntry)
    }

    internal fun setBudgetActionMessage(
        text: String,
        kind: InlineMessageKind = InlineMessageKind.ERROR,
        ownerRoute: AppRouteEntry? = currentBudgetActionMessageOwner,
        actionLabel: String? = null,
        retryAction: (suspend () -> Boolean)? = null,
    ) {
        budgetActionRetryAction = retryAction
        budgetActionFeedbackState.show(text, kind, ownerRoute, actionLabel)
    }

    suspend fun <T> withoutBudgetActionFeedback(action: suspend () -> T): T {
        val wasSuppressed = suppressBudgetActionFeedback
        suppressBudgetActionFeedback = true
        return try {
            action()
        } finally {
            suppressBudgetActionFeedback = wasSuppressed
        }
    }

    internal suspend fun performBudgetAction(
        budgetId: String,
        encryptionPassword: String?,
        failureMessage: String,
        messageOwner: AppRouteEntry? = currentBudgetActionMessageOwner,
        action: suspend () -> Unit,
    ): Boolean {
        budgetActionMessage = null
        budgetActionWasAppliedButRefreshFailed = false
        lastBudgetActionHttpStatus = null
        lastBudgetActionErrorCode = null
        budgetActionShouldRetry = true
        if (host.refuseWriteInReadOnlyMode()) {
            host.transactionWriteMessage?.let { setBudgetActionMessage(it, ownerRoute = messageOwner) }
            return false
        }
        if (budgetId.isBlank() || !host.apiClient.isAuthenticated) {
            setBudgetActionMessage("Sign in and open a budget before changing it.", ownerRoute = messageOwner)
            return false
        }
        if (!host.isCurrentIdentityCacheOwner()) {
            setBudgetActionMessage(
                "Sign in to the server and account that own this budget before changing it.",
                ownerRoute = messageOwner,
            )
            return false
        }
        if (isPerformingBudgetAction) return false
        isPerformingBudgetAction = true
        budgetActionShouldRetry = false
        var actionApplied = false
        try {
            host.apiClient.withExclusiveSession {
                action()
                actionApplied = true
            }
            if (host.synchronize(budgetId, encryptionPassword, background = suppressBudgetActionFeedback) == null) {
                reportAppliedButNotRefreshed(budgetId, encryptionPassword, messageOwner, host.syncError ?: REFRESH_FAILED_MESSAGE)
                return false
            }
            budgetActionRetryAction = null
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            budgetActionShouldRetry = shouldKeepBudgetMutationQueued(error.httpStatus)
            lastBudgetActionHttpStatus = error.httpStatus
            lastBudgetActionErrorCode = error.errorCode
            host.markServerReachable(error.httpStatus != null && !budgetActionShouldRetry)
            val failure =
                Failure(error.userFacingMessage(failureMessage), budgetActionShouldRetry, showRejection = error.httpStatus != null)
            reportFailure(budgetId, encryptionPassword, failureMessage, messageOwner, action, actionApplied, failure)
            return false
        } catch (_: Exception) {
            host.markServerReachable(false)
            budgetActionShouldRetry = true
            lastBudgetActionErrorCode = "network-error"
            reportFailure(
                budgetId,
                encryptionPassword,
                failureMessage,
                messageOwner,
                action,
                actionApplied,
                Failure(failureMessage, canRetry = true),
            )
            return false
        } finally {
            isPerformingBudgetAction = false
        }
    }

    internal suspend fun retryBudgetActionRefresh(
        budgetId: String,
        encryptionPassword: String?,
        messageOwner: AppRouteEntry?,
        failureMessage: String,
    ): Boolean {
        if (host.synchronize(budgetId, encryptionPassword) == null) {
            setBudgetActionMessage(
                failureMessage,
                InlineMessageKind.WARNING,
                messageOwner,
                actionLabel = "Refresh",
                retryAction = { retryBudgetActionRefresh(budgetId, encryptionPassword, messageOwner, failureMessage) },
            )
            return false
        }
        budgetActionWasAppliedButRefreshFailed = false
        clearBudgetActionMessage()
        return true
    }

    /** The change reached Actual but the refresh after it failed: say so, and offer to refresh. */
    private fun reportAppliedButNotRefreshed(
        budgetId: String,
        encryptionPassword: String?,
        messageOwner: AppRouteEntry?,
        refreshMessage: String,
    ) {
        budgetActionWasAppliedButRefreshFailed = true
        setBudgetActionMessage(
            refreshMessage,
            InlineMessageKind.WARNING,
            messageOwner,
            actionLabel = "Refresh",
            retryAction = { retryBudgetActionRefresh(budgetId, encryptionPassword, messageOwner, refreshMessage) },
        )
    }

    private class Failure(
        val message: String,
        val canRetry: Boolean,
        val showRejection: Boolean = true,
    )

    /**
     * Network failures are retried by the persistent queue, so a transport error isn't shown as if Actual rejected the
     * change; only real rejections are.
     */
    private fun reportFailure(
        budgetId: String,
        encryptionPassword: String?,
        failureMessage: String,
        messageOwner: AppRouteEntry?,
        action: suspend () -> Unit,
        actionApplied: Boolean,
        failure: Failure,
    ) {
        when {
            actionApplied -> reportAppliedButNotRefreshed(budgetId, encryptionPassword, messageOwner, REFRESH_FAILED_MESSAGE)
            failure.canRetry ->
                setBudgetActionMessage(
                    failure.message,
                    ownerRoute = messageOwner,
                    actionLabel = "Try again",
                    retryAction = { performBudgetAction(budgetId, encryptionPassword, failureMessage, messageOwner, action) },
                )
            failure.showRejection -> setBudgetActionMessage(failure.message, ownerRoute = messageOwner)
        }
    }

    private companion object {
        const val REFRESH_FAILED_MESSAGE = "Actual was updated, but Tandem couldn’t refresh the budget. Sync to load the latest data."
    }
}
