package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BudgetMutationRecoveryTest {
    @Test
    fun expiredResultRemainsReviewableUntilBudgetRefreshSucceeds() {
        assertFalse(
            shouldCompleteBudgetMutationAfterOutcome(
                mutationApplied = false,
                refreshFailedAfterMutation = false,
                resultUnavailable = true,
                resultRefreshSucceeded = false,
            ),
        )
        assertTrue(
            shouldCompleteBudgetMutationAfterOutcome(
                mutationApplied = false,
                refreshFailedAfterMutation = false,
                resultUnavailable = true,
                resultRefreshSucceeded = true,
            ),
        )
    }

    @Test
    fun confirmedMutationCanCompleteEvenWhenItsFollowUpRefreshFailed() {
        assertTrue(
            shouldCompleteBudgetMutationAfterOutcome(
                mutationApplied = true,
                refreshFailedAfterMutation = true,
                resultUnavailable = false,
                resultRefreshSucceeded = false,
            ),
        )
    }
}
