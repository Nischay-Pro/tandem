package app.tandemforactual.ui

import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.feature.budget.StillToSpend
import app.tandemforactual.ui.feature.budget.canQuickAssign
import app.tandemforactual.ui.feature.budget.stillToSpend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StillToSpendTest {
    private fun category(balance: Long) = LiveCategorySummary("c$balance", "C", 0, 0, balance)

    @Test
    fun addsWhatIsLeftInExpenseCategoriesOnly() {
        val groups =
            listOf(
                LiveCategoryGroupSummary("g", "Living", listOf(category(300_00), category(-50_00), category(100_00))),
                LiveCategoryGroupSummary("i", "Income", listOf(category(5_000_00)), isIncome = true),
            )
        assertEquals(StillToSpend(400_00, 36_36), stillToSpend(groups, 20, 30))
        assertEquals(StillToSpend(400_00, 400_00), stillToSpend(groups, 30, 30))
        assertNull(stillToSpend(groups, 31, 30))
        assertNull(stillToSpend(groups, 1, 0))
    }

    @Test
    fun quickAssignOnlyForEmptyCategoriesWithHistory() {
        assertTrue(canQuickAssign(false, false, 0, 120_00, true))
        assertFalse(canQuickAssign(true, false, 0, 120_00, true))
        assertFalse(canQuickAssign(false, true, 0, 120_00, true))
        assertFalse(canQuickAssign(false, false, 50_00, 120_00, true))
        assertFalse(canQuickAssign(false, false, 0, 0, true))
        assertFalse(canQuickAssign(false, false, 0, 120_00, false))
    }
}
