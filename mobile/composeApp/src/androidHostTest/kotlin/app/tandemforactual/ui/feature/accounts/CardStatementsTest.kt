package app.tandemforactual.ui.feature.accounts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardStatementsTest {
    private val transactions =
        listOf(
            "2026-09-30" to -10_000L,
            "2026-10-10" to 5_000L,
            "2026-10-30" to -20_000L,
            "2026-11-05" to 15_000L,
            "2026-11-26" to 25_000L,
            "2026-11-27" to -3_000L,
        )

    private fun statement(
        opened: String,
        closed: String,
        due: String?,
        owed: Long,
        paidSince: Long,
    ) = CardStatement(opened, closed, due, 0, 0, owed, paidSince)

    @Test
    fun eachStatementCoversItsDaysAndTotalsItsCycle() {
        val (november, october) = cardStatements(transactions, -18_000, 22, 20, "2026-11-28", 2)
        assertEquals(CardStatement("2026-10-23", "2026-11-22", "2026-12-20", 20_000, 15_000, 40_000, 25_000), november)
        assertEquals(CardStatement("2026-09-23", "2026-10-22", "2026-11-20", 10_000, 5_000, 35_000, 15_000), october)
        assertFalse(november.paid)
        assertEquals(15_000L, november.stillOwedMinor)
        assertNull(cardStatements(transactions, -18_000, 22, null, "2026-11-28", 1).single().dueOn)
    }

    @Test
    fun theOpenCycleRunsToTheNextCloseWithItsSpendSoFar() {
        assertEquals(CardCycle("2026-11-23", "2026-12-22", 3_000), currentCycle(transactions, 22, "2026-11-28"))
        assertEquals(CardCycle("2026-10-23", "2026-11-22", 20_000), currentCycle(transactions, 22, "2026-11-20"))
    }

    @Test
    fun aStatementIsDueOnTheFirstDueDayAfterItCloses() {
        assertEquals("2026-12-20", statementDueDate("2026-11-22", 20))
        assertEquals("2026-11-25", statementDueDate("2026-11-22", 25))
        assertEquals("2027-02-28", statementDueDate("2027-01-31", 30))
        assertEquals("2027-01-10", statementDueDate("2026-12-24", 10))
        assertEquals(23L, daysBetween("2026-11-27", "2026-12-20"))
    }

    @Test
    fun theNextPaymentIsTheUnpaidStatementOrElseTheOpenCycles() {
        val cycle = CardCycle("2026-11-23", "2026-12-22", 0)
        val unpaid = statement("2026-10-23", "2026-11-22", "2026-12-20", 10_000, 0)
        assertEquals("2026-12-20", nextCardDue(unpaid, cycle, 20, "2026-11-28"))
        assertEquals("2027-01-20", nextCardDue(unpaid.copy(paidSinceMinor = 10_000), cycle, 20, "2026-11-28"))
        assertEquals("2027-01-20", nextCardDue(unpaid, cycle, 20, "2026-12-21"))
        assertNull(nextCardDue(null, cycle, null, "2026-11-28"))
    }

    @Test
    fun aStatementIsPaidWhenLaterPaymentsCoverItOrNothingWasOwed() {
        assertTrue(statement("a", "b", null, 10_000, 10_000).paid)
        assertTrue(statement("a", "b", null, -500, 0).paid)
        assertFalse(statement("a", "b", null, 10_000, 9_999).paid)
        assertEquals("2026-02-28", statementCloseDate("2026-02", 31))
    }
}
