package app.tandemforactual.ui.design.money

import androidx.compose.ui.unit.em
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmountTextFormattingTest {
    @Test
    fun stylesFractionDigitsForEveryAmountInOneLine() {
        val result = amountAnnotatedString("$1,234.56 · -$25.19")

        assertEquals("$1,234.56 · -$25.19", result.text)
        assertEquals(listOf(7 to 9, 17 to 19), result.spanStyles.map { it.start to it.end })
        assertTrue(result.spanStyles.all { it.item.fontSize == 0.75.em })
    }

    @Test
    fun supportsLocaleDecimalCommaAndConfiguredFractionLength() {
        val result = amountAnnotatedString("1.234,567 €", fractionDigits = 3)

        assertEquals("1.234,567 €", result.text)
        assertEquals(listOf(6 to 9), result.spanStyles.map { it.start to it.end })
    }

    @Test
    fun leavesWholeNumberAndHiddenFractionsUnstyled() {
        assertTrue(amountAnnotatedString("$1,234").spanStyles.isEmpty())
        assertTrue(amountAnnotatedString("$1,234.56", fractionDigits = 0).spanStyles.isEmpty())
    }
}
