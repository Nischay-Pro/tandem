package app.tandemforactual.ui.feature.accounts

import app.tandemforactual.data.planning.PlanningMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoanCardTest {
    @Test
    fun anExtraPaymentClearsTheLoanSoonerWithLessInterest() {
        val base = PlanningMath.amortisation(1_000_000, 600, 20_000)!!
        val faster = PlanningMath.amortisation(1_000_000, 600, 30_000)!!
        val (months, interest) = extraPaymentSavings(base, faster)
        assertEquals(base.size - faster.size, months)
        assertTrue(months > 0 && interest > 0)
        assertEquals(0 to 0L, extraPaymentSavings(base, base))
    }

    @Test
    fun thePayoffMonthCountsFromThisMonth() {
        assertEquals("2026-12", payoffMonth("2026-11-28", 1))
        assertEquals("2027-11", payoffMonth("2026-11-28", 12))
    }
}
