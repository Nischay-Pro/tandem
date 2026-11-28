package app.tandemforactual.ui.core.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentTextFormatsTest {
    @Test
    fun everyBuiltInExampleIsReadByItsOwnFormat() {
        BUILT_IN_PAYMENT_FORMATS.forEach { format ->
            assertTrue(format.examples.isNotEmpty(), format.name)
            format.examples.forEach { example ->
                val read = assertNotNull(readPaymentText(example, listOf(format)), "${format.name}: $example")
                assertTrue(read.amountMinor > 0, example)
                format.income?.let { assertEquals(it, read.isIncome, example) }
            }
        }
    }

    @Test
    fun aCardChargeGivesPayeeAccountAndDate() {
        val shared = assertNotNull(parseSharedTransactionText(BUILT_IN_PAYMENT_FORMATS.first().examples.first()))
        assertEquals(1_240L, shared.amountMinor)
        assertFalse(shared.isIncome)
        assertEquals("BLUE BOTTLE COFFEE", shared.payeeHint)
        assertEquals("1234", shared.accountDigits)
        assertEquals("11/28/2026", shared.dateText)
    }

    @Test
    fun aFormatsTypeGroupDecidesTheDirectionAndRefIsKept() {
        val format =
            PaymentTextFormat(
                "Test bank",
                Regex("""(?<type>debited|credited) (?<amount>[\d.]+) .*?ref (?<ref>\d+)""", RegexOption.IGNORE_CASE),
                emptyList(),
            )
        val credit = assertNotNull(readPaymentText("Credited 15.00 to you, ref 998", listOf(format)))
        assertTrue(credit.isIncome)
        assertEquals("998", credit.reference)
        assertFalse(assertNotNull(readPaymentText("Debited 15.00 from you ref 1", listOf(format))).isIncome)
        assertNull(readPaymentText("Nothing here", listOf(format)))
    }

    @Test
    fun anythingElseFallsBackToTheGeneralReading() {
        val shared = assertNotNull(parseSharedTransactionText("You spent $9.00 at Corner Shop"))
        assertEquals(900L, shared.amountMinor)
        assertEquals("Corner Shop", shared.payeeHint)
        assertNull(shared.accountDigits)
    }

    @Test
    fun anAccountIsPickedOnlyWhenOneNameCarriesTheDigits() {
        val accounts = listOf("a" to "Visa 1234", "b" to "Savings 51234", "c" to "Checking")
        assertEquals("a" to "Visa 1234", matchAccountByDigits("1234", accounts))
        assertNull(matchAccountByDigits("9999", accounts))
        assertNull(matchAccountByDigits("12", accounts))
        assertNull(matchAccountByDigits("1234", accounts + ("d" to "Amex 1234")))
    }
}
