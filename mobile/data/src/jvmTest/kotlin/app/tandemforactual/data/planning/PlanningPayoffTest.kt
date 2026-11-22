package app.tandemforactual.data.planning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanningPayoffTest {
    private val card = DebtInput("card", 200_000, 2_400, 5_000)
    private val car = DebtInput("car", 500_000, 600, 15_000)

    @Test
    fun avalanchePaysTheCostliestDebtFirstAndSavesInterest() {
        val avalanche = PlanningMath.plan(listOf(car, card), 10_000, PayoffStrategy.AVALANCHE)!!
        val snowball = PlanningMath.plan(listOf(car, card), 10_000, PayoffStrategy.SNOWBALL)!!
        assertEquals(listOf("card", "car"), avalanche.order)
        assertEquals(listOf("card", "car"), snowball.order)
        assertTrue(avalanche.payoffMonth.getValue("card") < avalanche.payoffMonth.getValue("car"))
        assertEquals(avalanche.months, avalanche.balances.size)
        assertEquals(0L, avalanche.balances.last())
        assertEquals(avalanche, snowball)

        val small = DebtInput("small", 50_000, 500, 2_500)
        val costly = DebtInput("costly", 300_000, 2_900, 9_000)
        val a = PlanningMath.plan(listOf(small, costly), 5_000, PayoffStrategy.AVALANCHE)!!
        val s = PlanningMath.plan(listOf(small, costly), 5_000, PayoffStrategy.SNOWBALL)!!
        assertEquals(listOf("costly", "small"), a.order)
        assertEquals(listOf("small", "costly"), s.order)
        assertTrue(a.totalInterestMinor < s.totalInterestMinor)
        assertTrue(s.payoffMonth.getValue("small") < a.payoffMonth.getValue("small"))
    }

    @Test
    fun aSingleDebtMatchesTheMinimumPaymentEstimate() {
        val plan = PlanningMath.plan(listOf(DebtInput("loan", 100_000, 1_200, 10_000)), 0, PayoffStrategy.AVALANCHE)!!
        val estimate = PlanningMath.payoff(100_000, 1_200, 10_000)!!
        assertEquals(estimate.months, plan.months)
        assertEquals(estimate.totalInterestMinor, plan.totalInterestMinor)
    }

    @Test
    fun refusesPlansThatNeverFinishAndHandlesPaidDebts() {
        assertNull(PlanningMath.plan(listOf(DebtInput("x", 1_000_000, 3_000, 10)), 0, PayoffStrategy.SNOWBALL))
        assertNull(PlanningMath.plan(listOf(DebtInput("x", 10_000_000, 1_200, 100_001)), 0, PayoffStrategy.AVALANCHE))
        val done = PlanningMath.plan(listOf(DebtInput("paid", 0, 1_000, 1_000)), 0, PayoffStrategy.AVALANCHE)!!
        assertEquals(0, done.months)
        assertEquals(mapOf("paid" to 0), done.payoffMonth)
    }
}
