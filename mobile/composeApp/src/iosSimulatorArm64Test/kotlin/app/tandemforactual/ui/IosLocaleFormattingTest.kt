package app.tandemforactual.ui

import app.tandemforactual.data.store.BudgetDisplaySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class IosLocaleFormattingTest {
    @Test
    fun cachedIosFormattersPreserveBudgetCurrencyAndNumberRules() {
        val settings =
            BudgetDisplaySettings(
                currencyCode = "USD",
                currencySymbol = "$",
                currencyFractionDigits = 2,
                numberFormat = "comma-dot",
                hideFraction = false,
                currencySymbolPosition = "before",
                currencySpaceBetweenAmountAndSymbol = false,
            )

        repeat(20) {
            assertEquals("$1,234.56", platformFormatCurrency(123_456, settings))
            assertEquals("1,234.56", platformFormatDecimal(123_456, settings))
            assertEquals(123_456L, platformParseDecimalMinorUnits("1,234.56", settings))
        }
    }

    @Test
    fun iosDateAndReportFormattersReturnStableNonemptyValues() {
        val month = platformFormatMonth(2026, 11, abbreviated = false)
        assertFalse(month.isBlank())
        assertEquals(month, platformFormatMonth(2026, 11, abbreviated = false))
        assertEquals(platformFormatDate(2026, 11, 7, includeYear = true), platformFormatDate(2026, 11, 7, includeYear = true))
        assertFalse(platformFormatReportPercentage(0.125).isBlank())
        assertFalse(platformFormatReportCurrency(123_456, "USD", 2).isBlank())
    }
}
