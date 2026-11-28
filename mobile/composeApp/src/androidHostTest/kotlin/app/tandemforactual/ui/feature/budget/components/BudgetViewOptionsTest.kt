package app.tandemforactual.ui.feature.budget.components

import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BudgetViewOptionsTest {
    private fun category(
        balance: Long,
        activity: Long,
    ) = LiveCategorySummary("c", "Dining", assignedMinor = 5_000, activityMinor = activity, balanceMinor = balance)

    @Test
    fun onlySpendingCategoriesWithNothingLeftCountAsFullySpent() {
        val spending = group(isIncome = false)
        assertTrue(isFullySpent(spending, category(balance = 0, activity = -5_000)))
        assertFalse(isFullySpent(spending, category(balance = 0, activity = 0)))
        assertFalse(isFullySpent(spending, category(balance = -200, activity = -5_200)))
        assertFalse(isFullySpent(spending, category(balance = 1_000, activity = -4_000)))
        assertFalse(isFullySpent(group(isIncome = true), category(balance = 0, activity = 5_000)))
    }

    @Test
    fun viewOptionsAreKeptWithTheOtherSettings() {
        val state = AppState()
        assertEquals(Triple(false, true, true), Triple(state.budgetHideSpentCategories, state.budgetStatusColors, state.budgetGroupTotals))
        state.budgetHideSpentCategories = true
        state.budgetStatusColors = false
        state.budgetGroupTotals = false
        val restored = AppState().apply { restorePersistentSettings(state.persistentSettings()) }
        val saved = Triple(restored.budgetHideSpentCategories, restored.budgetStatusColors, restored.budgetGroupTotals)
        assertEquals(Triple(true, false, false), saved)
    }

    private fun group(isIncome: Boolean) = LiveCategoryGroupSummary("g", "Everyday", emptyList(), isIncome)
}
