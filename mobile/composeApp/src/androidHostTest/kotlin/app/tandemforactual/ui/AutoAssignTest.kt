package app.tandemforactual.ui

import app.tandemforactual.ui.core.state.AutoAssignChange
import app.tandemforactual.ui.core.state.AutoAssignRule
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.monthBefore
import app.tandemforactual.ui.core.state.planAutoAssign
import kotlin.test.Test
import kotlin.test.assertEquals

class AutoAssignTest {
    private fun month(
        vararg categories: LiveCategorySummary,
        income: LiveCategorySummary = LiveCategorySummary("pay", "Pay", 0, 900_000, 0),
    ) = LiveBudgetMonthSummary(
        toBudgetMinor = 0,
        incomeAvailableMinor = null,
        totalBudgetedMinor = null,
        totalSpentMinor = null,
        groups =
            listOf(
                LiveCategoryGroupSummary("spend", "Spending", categories.toList()),
                LiveCategoryGroupSummary("in", "Income", listOf(income), isIncome = true),
            ),
    )

    private fun category(
        id: String,
        assigned: Long,
        activity: Long,
        balance: Long = assigned + activity,
    ) = LiveCategorySummary(id, id.replaceFirstChar(Char::uppercase), assigned, activity, balance)

    private val now = month(category("food", 10_000, -30_000), category("rent", 100_000, -100_000), category("fun", 0, -2_000))
    private val october = month(category("food", 40_000, -35_000), category("rent", 100_000, -100_000), category("fun", 5_000, -6_000))
    private val september = month(category("food", 40_000, -25_000), category("rent", 100_000, -100_000), category("fun", 5_000, 0))
    private val august = month(category("food", 40_000, -30_000), category("rent", 100_000, -100_000), category("fun", 5_000, -3_000))

    private fun plan(
        rule: AutoAssignRule,
        available: Long,
        history: List<LiveBudgetMonthSummary> = listOf(october, september, august),
    ) = planAutoAssign(rule, now, history, available).associate { it.categoryId to it.addedMinor }

    @Test
    fun coversOverspendingLargestFirstWithinWhatIsLeft() {
        assertEquals(mapOf("food" to 20_000L, "fun" to 2_000L), plan(AutoAssignRule.COVER_OVERSPENDING, 100_000))
        assertEquals(mapOf("food" to 15_000L), plan(AutoAssignRule.COVER_OVERSPENDING, 15_000))
        assertEquals(emptyMap(), plan(AutoAssignRule.COVER_OVERSPENDING, 0))
    }

    @Test
    fun fillsUpToLastMonthsSpendingOrAssignments() {
        assertEquals(mapOf("food" to 25_000L, "fun" to 6_000L), plan(AutoAssignRule.SPENT_LAST_MONTH, 100_000))
        assertEquals(mapOf("food" to 30_000L, "fun" to 5_000L), plan(AutoAssignRule.ASSIGNED_LAST_MONTH, 100_000))
        assertEquals(emptyMap(), plan(AutoAssignRule.SPENT_LAST_MONTH, 100_000, history = emptyList()))
    }

    @Test
    fun averagesSpendingOverTheMonthsAvailable() {
        assertEquals(mapOf("food" to 20_000L, "fun" to 3_000L), plan(AutoAssignRule.AVERAGE_SPENT, 100_000))
        assertEquals(mapOf("food" to 20_000L, "fun" to 3_000L), plan(AutoAssignRule.AVERAGE_SPENT, 100_000, listOf(october, september)))
        assertEquals(emptyMap(), plan(AutoAssignRule.AVERAGE_SPENT, 100_000, emptyList()))
        assertEquals(
            listOf(AutoAssignChange("food", "Food", 10_000, 30_000)),
            planAutoAssign(AutoAssignRule.AVERAGE_SPENT, now, listOf(october, september, august), 20_000),
        )
    }

    @Test
    fun stepsBackAMonth() {
        assertEquals("2026-10", monthBefore("2026-11"))
        assertEquals("2025-12", monthBefore("2026-01"))
    }
}
