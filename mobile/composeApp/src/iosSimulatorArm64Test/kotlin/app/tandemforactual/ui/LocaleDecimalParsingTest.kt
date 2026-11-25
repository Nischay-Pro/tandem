package app.tandemforactual.ui

import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.platformParseDecimalMinorUnits
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

        assertEquals(150_000L, platformParseDecimalMinorUnits("1,500", yen))
        assertNull(platformParseDecimalMinorUnits("1.50", yen))

        assertEquals(1_250L, platformParseDecimalMinorUnits("12.50", usd))
        assertNull(platformParseDecimalMinorUnits("12,50", usd))

        assertEquals(1_234L, platformParseDecimalMinorUnits("12.34", threeDigitUs))
        assertNull(platformParseDecimalMinorUnits("12.345", threeDigitUs), "a third decimal can't be stored")
        assertEquals(1_234L, platformParseDecimalMinorUnits("12,34", threeDigitGerman))
        assertNull(platformParseDecimalMinorUnits("12,345", threeDigitGerman))
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
