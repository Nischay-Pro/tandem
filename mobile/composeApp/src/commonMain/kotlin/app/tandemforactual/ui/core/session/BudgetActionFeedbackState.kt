package app.tandemforactual.ui.core.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.state.AppRouteEntry
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind

internal fun pendingDeviceChangeNotice(): String =
    "Any changes waiting to sync stay on this device. Sign in to the same server and account to sync them."

@Composable
internal fun budgetActionFeedbackMessage(mobileSession: TandemMobileSession) {
    val message = mobileSession.budgetActionMessage ?: return
    val actionLabel = mobileSession.feedback.budgetActionMessageActionLabel
    InlineMessage(
        message,
        mobileSession.feedback.budgetActionMessageKind,
        actionLabel = actionLabel,
        onAction = if (actionLabel == null) null else ({ mobileSession.feedback.retryBudgetAction() }),
    )
}

internal class BudgetActionFeedbackState {
    var currentRouteEntry by mutableStateOf<AppRouteEntry?>(null)
        internal set

    var feedback by mutableStateOf<BudgetActionFeedback?>(null)
        internal set

    fun changeRoute(routeEntry: AppRouteEntry) {
        if (currentRouteEntry == routeEntry) return
        currentRouteEntry = routeEntry
        feedback = null
    }

    fun show(
        text: String,
        kind: InlineMessageKind,
        ownerRoute: AppRouteEntry?,
        actionLabel: String? = null,
    ) {
        if (ownerRoute != null) feedback = BudgetActionFeedback(text, kind, ownerRoute, actionLabel)
    }

    fun clear() {
        feedback = null
    }

    fun clear(route: AppRouteEntry?) {
        if (feedback?.ownerRoute == route) feedback = null
    }

    fun messageFor(routeEntry: AppRouteEntry): BudgetActionFeedback? = feedback?.takeIf { it.ownerRoute == routeEntry }
}
