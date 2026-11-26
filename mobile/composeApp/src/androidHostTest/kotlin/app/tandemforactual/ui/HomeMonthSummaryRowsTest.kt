package app.tandemforactual.ui

import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.feature.home.monthSummaryRows
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeMonthSummaryRowsTest {
    private fun month(
        spent: Long,
        income: Long? = 300_000,
    ) = LiveBudgetMonthSummary(
        toBudgetMinor = 50_000,
        incomeAvailableMinor = 450_000,
        totalBudgetedMinor = -200_000,
        totalSpentMinor = spent,
        groups = emptyList(),
        totalIncomeMinor = income,
    )

    @Test
    fun incomeIsThisMonthsIncomeWithoutCarriedOverMoney() {
        assertEquals(
            listOf("Income" to 300_000L, "Budgeted" to 200_000L, "Spent" to 120_000L),
            monthSummaryRows(month(spent = -120_000)),
        )
    }

    @Test
    fun refundsBeyondSpendingShowNothingSpent() {
        assertEquals("Spent" to 0L, monthSummaryRows(month(spent = 4_000)).last())
    }

    @Test
    fun rowsWithoutAnAmountAreLeftOut() {
        assertEquals(listOf("Budgeted", "Spent"), monthSummaryRows(month(spent = 0, income = null)).map { it.first })
        assertEquals(emptyList(), monthSummaryRows(null))
    }
}
