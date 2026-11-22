@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState

internal fun openNeedsReview(
    state: AppState,
    navigation: AppNavigation,
    transactionReview: OutboxOperationEntity?,
    scheduleReview: OutboxOperationEntity?,
    budgetMutationReview: OutboxOperationEntity?,
): Boolean {
    transactionReview?.let { operation ->
        state.selectedConflictOperationId = operation.operationId
        navigation.open(if (operation.status == "conflict") AppRoute.CONFLICT_REVIEW else AppRoute.UNCERTAIN_WRITE)
        return true
    }
    scheduleReview?.let { operation ->
        state.pendingScheduleToOpenId = operation.recordId
        navigation.open(AppRoute.SCHEDULES)
        return true
    }
    budgetMutationReview?.let { operation ->
        state.selectedConflictOperationId = operation.operationId
        navigation.open(AppRoute.UNCERTAIN_WRITE)
        return true
    }
    return false
}
