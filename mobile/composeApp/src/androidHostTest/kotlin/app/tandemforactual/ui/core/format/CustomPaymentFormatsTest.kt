package app.tandemforactual.ui.core.format

import app.tandemforactual.data.planning.CustomPaymentFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CustomPaymentFormatsTest {
    private val good = """spent (?<amount>[\d.]+) at (?<payee>\w+)"""

    @Test
    fun aPatternMustCompileNameTheAmountAndReadItsSample() {
        assertNull(paymentFormatProblem(good, "You spent 12.40 at Cafe"))
        assertEquals("Enter a pattern.", paymentFormatProblem(" ", "x"))
        assertEquals("Name the amount with (?<amount>…).", paymentFormatProblem("spent ([\\d.]+)", "spent 4"))
        assertEquals("This isn’t a valid regular expression.", paymentFormatProblem("(?<amount>[\\d", "spent 4"))
        assertEquals("The pattern doesn’t read the sample message.", paymentFormatProblem(good, "Nothing to see"))
        assertNotNull(paymentFormatProblem("(?<amount>\\d)" + "x".repeat(600), "1"))
    }

    @Test
    fun aRepeatInsideARepeatIsRefused() {
        assertEquals(
            "Avoid a repeat inside a repeat, such as (a+)+. It can make matching very slow.",
            paymentFormatProblem("(?<amount>(\\d+)+)", "12"),
        )
        assertNull(paymentFormatProblem("(?<amount>\\d+)(?: on (?<date>\\S+))?", "12 on 1/2"))
    }

    @Test
    fun onlyUsableFormatsAreTriedAndTheirReadingIsDescribed() {
        val saved = listOf(CustomPaymentFormat("a", "Mine", good, "You spent 12.40 at Cafe"), CustomPaymentFormat("b", "Broken", "(", "x"))
        val formats = customPaymentFormats(saved)
        assertEquals(listOf("Mine"), formats.map { it.name })
        val read = assertNotNull(readPaymentText("I spent 3.00 at Deli", formats))
        assertEquals("$3.00 out · payee Deli", describeReadPayment(read) { "$" + it / 100 + "." + (it % 100).toString().padStart(2, '0') })
    }
}
