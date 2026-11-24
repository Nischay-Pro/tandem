package app.tandemforactual.ui

import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.navigation.openNeedsReview
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class SyncCenterNavigationTest {
    @Test
    fun needsReviewOpensTheFirstConflictedTransaction() {
        val state = AppState()
        var openedRoute: AppRoute? = null
        val navigation = navigation { openedRoute = it }
        val conflict = operation("transaction-conflict", "transaction", "conflict")

        val opened = openNeedsReview(state, navigation, conflict, null, null)

        assertTrue(opened)
        assertEquals("transaction-conflict", state.selectedConflictOperationId)
        assertEquals(AppRoute.CONFLICT_REVIEW, openedRoute)
    }

    @Test
    fun needsReviewOpensTheScheduleWithItsPendingAction() {
        val state = AppState()
        var openedRoute: AppRoute? = null
        val navigation = navigation { openedRoute = it }
        val scheduleAction = operation("schedule-conflict", "schedule-action", "conflict", "schedule-1")

        val opened = openNeedsReview(state, navigation, null, scheduleAction, null)

        assertTrue(opened)
        assertEquals("schedule-1", state.pendingScheduleToOpenId)
        assertEquals(AppRoute.SCHEDULES, openedRoute)
    }

    private fun navigation(onOpen: (AppRoute) -> Unit) =
        AppNavigation(
            open = onOpen,
            reset = {},
            replaceTop = {},
            newTransaction = { _ -> },
            selectTab = {},
            back = {},
            backToList = {},
        )

    private fun operation(
        operationId: String,
        recordType: String,
        status: String,
        recordId: String = "transaction-1",
    ) = OutboxOperationEntity(
        operationId = operationId,
        idempotencyKey = "key-$operationId",
        budgetId = "budget-1",
        recordType = recordType,
        recordId = recordId,
        action = "UPDATE",
        baseRevision = "revision-1",
        baseSnapshotJson = "{}",
        patchJson = "{}",
        optimisticJson = "{}",
        optimisticRecordDate = "2026-11-18",
        optimisticAccountId = "account-1",
        optimisticCategoryId = "category-1",
        optimisticPayeeId = "payee-1",
        optimisticAmountMinor = 100L,
        optimisticBudgetMonth = "2026-11",
        status = status,
        attemptCount = 1,
        serverOperationId = "server-$operationId",
        lastErrorCode = null,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 2L,
    )
}
