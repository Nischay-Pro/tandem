package app.tandemforactual.ui

import app.tandemforactual.data.store.BudgetDisplaySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocaleDecimalParsingTest {
    @Test
    fun currencyScaleAndBudgetSeparatorsAreUsedForParsing() {
        val yen = displaySettings("JPY", "comma-dot", 0)
        val usd = displaySettings("USD", "comma-dot", 2)
        val threeDigitUs = displaySettings("KWD", "comma-dot", 3)
        val threeDigitGerman = displaySettings("KWD", "dot-comma", 3)

        assertEquals(1_500L, platformParseDecimalMinorUnits("1,500", yen))
        assertNull(platformParseDecimalMinorUnits("1.50", yen))

        assertEquals(1_250L, platformParseDecimalMinorUnits("12.50", usd))
        assertNull(platformParseDecimalMinorUnits("12,50", usd))

        assertEquals(12_345L, platformParseDecimalMinorUnits("12.345", threeDigitUs))
        assertNull(platformParseDecimalMinorUnits("12,345", threeDigitUs))
        assertEquals(12_345L, platformParseDecimalMinorUnits("12,345", threeDigitGerman))
        assertNull(platformParseDecimalMinorUnits("12.345", threeDigitGerman))
    }

    private fun displaySettings(
        currencyCode: String,
        numberFormat: String,
        fractionDigits: Int,
    ) = BudgetDisplaySettings(
        currencyCode = currencyCode,
        currencySymbol = currencyCode,
        currencyFractionDigits = fractionDigits,
        numberFormat = numberFormat,
        hideFraction = false,
        currencySymbolPosition = "before",
        currencySpaceBetweenAmountAndSymbol = false,
    )
}
