package app.tandemforactual.ui.feature.accounts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardStatementsTest {
    private val transactions =
        listOf(
            "2026-09-30" to -10_000L,
            "2026-10-10" to 5_000L,
            "2026-10-30" to -20_000L,
            "2026-11-05" to 15_000L,
            "2026-11-26" to 25_000L,
        )

    @Test
    fun eachStatementTotalsItsCycleAndWhatWasOwedAtClosing() {
        val (november, october) = cardStatements(transactions, -15_000, closeDay = 24, today = "2026-11-28", count = 2)
        assertEquals(CardStatement("2026-11-24", 20_000, 15_000, 40_000, 25_000), november)
        assertEquals(CardStatement("2026-10-24", 10_000, 5_000, 35_000, 15_000), october)
        assertFalse(november.paid)
    }

    @Test
    fun aStatementIsPaidWhenLaterPaymentsCoverItOrNothingWasOwed() {
        assertTrue(CardStatement("2026-10-24", 0, 0, 10_000, 10_000).paid)
        assertTrue(CardStatement("2026-10-24", 0, 0, -500, 0).paid)
        assertFalse(CardStatement("2026-10-24", 0, 0, 10_000, 9_999).paid)
    }

    @Test
    fun aClosingDayPastTheMonthsEndUsesItsLastDay() {
        assertEquals("2026-02-28", statementCloseDate("2026-02", 31))
        assertEquals("2028-02-29", statementCloseDate("2028-02", 30))
        assertEquals("2026-11-05", statementCloseDate("2026-11", 5))
        assertEquals(1, cardStatements(emptyList(), 0, closeDay = 30, today = "2026-11-28", count = 1).size)
    }
}
