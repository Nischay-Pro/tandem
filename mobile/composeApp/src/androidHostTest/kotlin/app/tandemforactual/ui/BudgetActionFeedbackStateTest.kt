package app.tandemforactual.ui

import app.tandemforactual.ui.components.InlineMessageKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BudgetActionFeedbackStateTest {
    @Test
    fun partialSuccessKeepsItsWarningKindOnTheOwningRoute() {
        val state = BudgetActionFeedbackState()
        val accountDetail = AppRouteEntry(AppRoute.ACCOUNT_DETAIL, 1)
        state.changeRoute(accountDetail)

        state.show("Actual was updated, but this device could not refresh the budget.", InlineMessageKind.WARNING, accountDetail, "Refresh")

        assertEquals(InlineMessageKind.WARNING, state.messageFor(accountDetail)?.kind)
        assertEquals("Refresh", state.messageFor(accountDetail)?.actionLabel)
        assertNull(state.messageFor(AppRouteEntry(AppRoute.ACCOUNTS, 2)))
    }

    @Test
    fun retryableBudgetActionUsesTheTryAgainLabelOnItsOwningRoute() {
        val state = BudgetActionFeedbackState()
        val schedule = AppRouteEntry(AppRoute.SCHEDULES, 2)
        state.changeRoute(schedule)

        state.show("Couldn’t save this schedule. Try again.", InlineMessageKind.ERROR, schedule, "Try again")

        assertEquals("Try again", state.messageFor(schedule)?.actionLabel)
    }

    @Test
    fun leavingTheOwningRouteClearsItsMessage() {
        val state = BudgetActionFeedbackState()
        val categoryGroup = AppRouteEntry(AppRoute.CATEGORY_GROUP, 3)
        state.changeRoute(categoryGroup)
        state.show("Could not move this category.", InlineMessageKind.ERROR, categoryGroup)

        val activity = AppRouteEntry(AppRoute.ACTIVITY, 4)
        state.changeRoute(activity)

        assertNull(state.messageFor(categoryGroup))
        assertNull(state.messageFor(activity))
    }

    @Test
    fun aLateResultStaysWithItsStartingRoute() {
        val state = BudgetActionFeedbackState()
        val accounts = AppRouteEntry(AppRoute.ACCOUNTS, 5)
        state.changeRoute(accounts)
        val actionOwner = state.currentRouteEntry
        state.changeRoute(AppRouteEntry(AppRoute.ACTIVITY, 6))

        state.show("Could not update this account.", InlineMessageKind.ERROR, actionOwner)

        assertNull(state.messageFor(AppRouteEntry(AppRoute.ACTIVITY, 6)))
        assertEquals("Could not update this account.", state.messageFor(accounts)?.text)
        state.changeRoute(accounts)
        assertNull(state.messageFor(accounts))
    }

    @Test
    fun messagesDoNotLeakBetweenInstancesOfTheSameScreen() {
        val state = BudgetActionFeedbackState()
        val firstCategory = AppRouteEntry(AppRoute.CATEGORY_DETAIL, 7)
        val secondCategory = AppRouteEntry(AppRoute.CATEGORY_DETAIL, 8)
        state.changeRoute(firstCategory)
        state.show("Could not rename this category.", InlineMessageKind.ERROR, firstCategory)

        assertNull(state.messageFor(secondCategory))
    }

    @Test
    fun newValidationMessageDoesNotKeepAStaleRetryAction() {
        val state = BudgetActionFeedbackState()
        val accountDetail = AppRouteEntry(AppRoute.ACCOUNT_DETAIL, 9)
        state.changeRoute(accountDetail)
        state.show("Could not save the account.", InlineMessageKind.ERROR, accountDetail, "Try again")

        state.show("Enter an account name.", InlineMessageKind.ERROR, accountDetail)

        assertNull(state.messageFor(accountDetail)?.actionLabel)
    }
}
