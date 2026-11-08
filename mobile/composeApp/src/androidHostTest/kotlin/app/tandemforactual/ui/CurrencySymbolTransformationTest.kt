package app.tandemforactual.ui

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencySymbolTransformationTest {
    @Test
    fun negativeCurrencyKeepsMinusBeforePrefixSymbolAndMapsCaret() {
        val transformed = currencySymbolTransformation("$", afterAmount = false, addSpace = false).filter(AnnotatedString("-1,234.50"))

        assertEquals("-$1,234.50", transformed.text.text)
        assertEquals(0, transformed.offsetMapping.originalToTransformed(0))
        assertEquals(2, transformed.offsetMapping.originalToTransformed(1))
        assertEquals(1, transformed.offsetMapping.transformedToOriginal(2))
    }

    @Test
    fun suffixCurrencyKeepsTheSignAndLocaleSpacing() {
        val transformed = currencySymbolTransformation("CA$", afterAmount = true, addSpace = true).filter(AnnotatedString("-42.00"))

        assertEquals("-42.00\u202FCA$", transformed.text.text)
        assertEquals(6, transformed.offsetMapping.originalToTransformed(6))
        assertEquals(6, transformed.offsetMapping.transformedToOriginal(10))
    }
}
