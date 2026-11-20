package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedTransactionTextTest {
    @Test
    fun readsAmountAndPayeeFromCardAlerts() {
        val alert = parseSharedTransactionText("You spent $1,234.50 at STARBUCKS COFFEE on card ending 1234.")!!
        assertEquals(123_450L, alert.amountMinor)
        assertEquals("STARBUCKS COFFEE", alert.payeeHint)
        assertFalse(alert.isIncome)

        val trailing = parseSharedTransactionText("Payment of 89.9 EUR to City Power via SEPA")!!
        assertEquals(8_990L, trailing.amountMinor)
        assertEquals("City Power", trailing.payeeHint)

        val income = parseSharedTransactionText("Salary of ₹50,000 credited from Acme Ltd.")!!
        assertEquals(5_000_000L, income.amountMinor)
        assertTrue(income.isIncome)
        assertEquals("Acme Ltd", income.payeeHint)
    }

    @Test
    fun copesWithTextItCannotRead() {
        assertNull(parseSharedTransactionText("   "))
        val plain = parseSharedTransactionText("Dinner with friends")!!
        assertNull(plain.amountMinor)
        assertNull(plain.payeeHint)
        assertEquals("Dinner with friends", plain.original)
        assertEquals(1_234L, parseSharedTransactionText("Charged $12.345 at Shop")!!.amountMinor)
    }

    @Test
    fun matchesExistingPayeesLoosely() {
        val payees = listOf("1" to "Starbucks", "2" to "City Power", "3" to "Shell")
        assertEquals("1" to "Starbucks", matchSharedPayee("STARBUCKS COFFEE", payees))
        assertEquals("2" to "City Power", matchSharedPayee("city power", payees))
        assertNull(matchSharedPayee("Corner Market", payees))
        assertNull(matchSharedPayee(null, payees))
        assertNull(matchSharedPayee("a", payees))
    }
}
