package app.tandemforactual.ui.core.ui

import app.tandemforactual.data.planning.DebtKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DebtTermsFormTest {
    private val card = DebtTermsForm("19.99", "25", "12", "24", "5000", DebtKind.CARD, paysInFull = true, keepPrivate = false)

    @Test
    fun aCardKeepsItsClosingDayAndLimit() {
        val terms = card.toTerms("t", "visa", null).getOrThrow()
        assertEquals(24, terms.statementCloseDay)
        assertEquals(500_000L, terms.creditLimitMinor)
        assertEquals(12, terms.dueDay)
        assertEquals(true, terms.paysInFull)
    }

    @Test
    fun aLoanDropsCardOnlyDetails() {
        val terms = card.copy(kind = DebtKind.LOAN).toTerms("t", "car", null).getOrThrow()
        assertNull(terms.statementCloseDay)
        assertNull(terms.creditLimitMinor)
        assertEquals(false, terms.paysInFull)
    }

    @Test
    fun badEntriesSayWhatToFix() {
        fun problem(form: DebtTermsForm) = form.toTerms("t", "visa", null).exceptionOrNull()?.message
        assertEquals("Enter a closing day between 1 and 31.", problem(card.copy(closeDay = "40")))
        assertEquals("Enter the credit limit.", problem(card.copy(limit = "0")))
        assertEquals("Enter a day between 1 and 31.", problem(card.copy(dueDay = "0")))
        assertEquals("Enter the APR as a percentage, like 19.99.", problem(card.copy(apr = "abc")))
        assertNull(problem(card.copy(closeDay = "", limit = "")))
    }

    @Test
    fun creditUseIsWhatIsLeftAndTheShareInUse() {
        assertEquals(350_000L to 30, creditUse(150_000, 500_000))
        assertEquals(0L to 120, creditUse(600_000, 500_000))
        assertEquals(500_000L to 0, creditUse(-2_000, 500_000))
    }
}
