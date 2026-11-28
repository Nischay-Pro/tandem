package app.tandemforactual.ui.feature.accounts

import app.tandemforactual.data.planning.Compounding
import app.tandemforactual.data.planning.DepositTerms
import kotlin.test.Test
import kotlin.test.assertEquals

class DepositCardTest {
    @Test
    fun monthsUntilMaturityCountWholeMonthsAndAtLeastOne() {
        assertEquals(19, monthsUntil("2026-11-28", "2028-06-30"))
        assertEquals(1, monthsUntil("2026-11-28", "2026-11-30"))
        assertEquals(1, monthsUntil("2026-11-28", "2025-01-01"))
    }

    @Test
    fun checkpointsFallOnTheIntervalAndAlwaysIncludeTheEnd() {
        val growth = (1..7).map { it * 100L }
        assertEquals(listOf(3 to 300L, 6 to 600L, 7 to 700L), growthCheckpoints(growth, 3))
        assertEquals(listOf(3 to 300L, 6 to 600L), growthCheckpoints(growth.take(6), 3))
    }

    @Test
    fun theFormGivesTermsOrSaysWhatToFix() {
        val terms = depositTermsFrom("d", "fd", "7.25", Compounding.QUARTERLY, "50", "2028-06-30", null).getOrThrow()
        assertEquals(DepositTerms("d", "fd", 725, Compounding.QUARTERLY, 5_000, "2028-06-30"), terms)
        assertEquals(0L, depositTermsFrom("d", "fd", "5", Compounding.YEARLY, "", "", null).getOrThrow().monthlyContributionMinor)

        fun problem(
            rate: String,
            amount: String,
            date: String,
        ) = depositTermsFrom("d", "fd", rate, Compounding.MONTHLY, amount, date, null).exceptionOrNull()?.message
        assertEquals("Enter the yearly rate as a percentage, like 7.25.", problem("abc", "", ""))
        assertEquals("Enter the monthly amount.", problem("5", "-3", ""))
        assertEquals("Enter the maturity date as YYYY-MM-DD.", problem("5", "", "June 2028"))
    }
}
