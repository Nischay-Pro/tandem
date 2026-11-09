package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BudgetReorderFloatingActionTest {
    @Test
    fun hidesCreateActionWhileReordering() {
        assertFalse(shouldShowBudgetCreateAction(isLiveBudget = true, isReordering = true))
    }

    @Test
    fun keepsCreateActionVisibleForLiveBudgetOutsideReorderMode() {
        assertTrue(shouldShowBudgetCreateAction(isLiveBudget = true, isReordering = false))
    }

    @Test
    fun hidesCreateActionWhenThereIsNoLiveBudget() {
        assertFalse(shouldShowBudgetCreateAction(isLiveBudget = false, isReordering = false))
    }
}
