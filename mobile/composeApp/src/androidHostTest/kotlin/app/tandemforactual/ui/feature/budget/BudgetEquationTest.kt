package app.tandemforactual.ui.feature.budget

import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.feature.budget.grid.GridValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BudgetEquationTest {
    private fun month(
        toBudget: Long? = 12_709,
        available: Long? = 500_000,
        overspent: Long? = -2_500,
        held: Long? = 10_000,
        income: Long? = 600_000,
        spent: Long? = -420_000,
    ) = LiveBudgetMonthSummary(
        toBudgetMinor = toBudget,
        incomeAvailableMinor = available,
        totalBudgetedMinor = null,
        totalSpentMinor = spent,
        groups = emptyList(),
        heldForNextMonthMinor = held,
        totalIncomeMinor = income,
        lastMonthOverspentMinor = overspent,
    )

    @Test
    fun anEnvelopeMonthAddsUpToWhatIsLeftToBudget() {
        val equation = budgetEquation(month(), tracking = false)!!
        assertEquals(
            listOf(
                EquationTerm("Available", 500_000),
                EquationTerm("Overspent last month", 2_500, subtract = true),
                EquationTerm("Budgeted", 474_791, subtract = true),
                EquationTerm("Held", 10_000, subtract = true),
            ),
            equation.terms,
        )
        assertEquals(EquationTerm("To budget", 12_709), equation.result)
        val total = equation.terms.sumOf { if (it.subtract) -it.minor else it.minor }
        assertEquals(equation.result.minor, total)
    }

    @Test
    fun nothingOverspentOrHeldLeavesThoseTermsOut() {
        val equation = budgetEquation(month(overspent = null, held = null), tracking = false)!!
        assertEquals(listOf("Available", "Budgeted"), equation.terms.map { it.label })
        assertNull(budgetEquation(month(available = null), tracking = false))
        assertNull(budgetEquation(month(toBudget = null), tracking = false))
    }

    @Test
    fun aTrackingMonthSubtractsSpendingFromIncome() {
        val equation = budgetEquation(month(), tracking = true)!!
        assertEquals(listOf(EquationTerm("Income", 600_000), EquationTerm("Spent", 420_000, subtract = true)), equation.terms)
        assertEquals(EquationTerm("Saved", 180_000), equation.result)
        assertNull(budgetEquation(month(income = null), tracking = true))
        assertNull(budgetEquation(month(spent = null), tracking = true))
    }

    @Test
    fun theGridShowsAllThreeFiguresOrJustTheOneChosen() {
        assertEquals(listOf(GridValue.BUDGETED, GridValue.SPENT, GridValue.BALANCE), GridValue.ALL.columns)
        assertEquals(listOf(GridValue.BALANCE), GridValue.BALANCE.columns)
    }
}
