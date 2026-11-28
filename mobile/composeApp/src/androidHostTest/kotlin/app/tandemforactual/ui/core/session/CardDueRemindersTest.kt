package app.tandemforactual.ui.core.session

import app.tandemforactual.data.planning.DebtKind
import app.tandemforactual.data.planning.DebtTerms
import app.tandemforactual.ui.core.format.displayTransactionDate
import app.tandemforactual.ui.core.state.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CardDueRemindersTest {
    private val accounts = mapOf("visa" to ("Visa" to -45_000L), "amex" to ("Amex" to 0L), "loan" to ("Car loan" to -900_000L))

    @Test
    fun theNextDueDateRollsIntoNextMonthAndFitsShortMonths() {
        assertEquals("2026-11-30", nextDueDate(30, "2026-11-28"))
        assertEquals("2026-12-05", nextDueDate(5, "2026-11-28"))
        assertEquals("2027-02-28", nextDueDate(31, "2027-02-01"))
        assertEquals("2026-12-31", nextDueDate(31, "2026-12-31"))
        assertEquals("2027-01-05", nextDueDate(5, "2026-12-28"))
    }

    @Test
    fun onlyCardsOwingMoneyAndDueWithinTheLeadDaysAreListed() {
        val terms =
            listOf(
                DebtTerms("1", "visa", dueDay = 30, kind = DebtKind.CARD),
                DebtTerms("2", "amex", dueDay = 29, kind = DebtKind.CARD),
                DebtTerms("3", "loan", dueDay = 29, kind = DebtKind.LOAN),
                DebtTerms("4", "gone", dueDay = 29, kind = DebtKind.CARD),
            )
        val due = cardsDue(terms, accounts, "2026-11-28", leadDays = 3)
        assertEquals(listOf(CardDue("visa", "Visa", "2026-11-30", 45_000)), due)
        assertEquals("visa@2026-11-30", due.single().key)
        assertEquals(emptyList(), cardsDue(terms, accounts, "2026-11-20", leadDays = 3))
    }

    @Test
    fun theMessageNamesTheCardAndHidesTheAmountWhenAsked() {
        val visa = CardDue("visa", "Visa", "2026-11-30", 45_000)
        val format = { minor: Long -> "$" + minor / 100 }
        val date = displayTransactionDate("2026-11-30")
        assertEquals("Visa: $450 owed, due $date.", cardDueMessage(listOf(visa), false, format))
        assertEquals("Visa: due $date.", cardDueMessage(listOf(visa), true, format))
        assertEquals("Visa and 1 more cards are due soon.", cardDueMessage(listOf(visa, visa.copy(name = "Amex")), false, format))
        assertNull(cardDueMessage(emptyList(), false, format))
        assertFalse(AppState().cardDueAlerts)
    }
}
