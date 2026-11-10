package app.tandemforactual.ui

import app.tandemforactual.data.store.BudgetDisplaySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

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
    fun amountParserRejectsSwappedMarksAndMalformedGroupingForEveryBudgetFormat() {
        val formats =
            listOf(
                "comma-dot" to ("12,50" to "12,34.56"),
                "dot-comma" to ("12.50" to "12.34,56"),
                "space-comma" to ("12.50" to "12\u202f34,56"),
                "apostrophe-dot" to ("12,50" to "12’34.56"),
                "comma-dot-in" to ("12,50" to "123,45,678.89"),
            )

        formats.forEach { (numberFormat, invalidInputs) ->
            val settings = settings(numberFormat)
            val formatted = platformFormatDecimal(123_456_789L, settings)
            assertEquals(123_456_789L, platformParseDecimalMinorUnits(formatted, settings), numberFormat)
            assertNull(platformParseDecimalMinorUnits(invalidInputs.first, settings), "$numberFormat decimal mark")
            assertNull(platformParseDecimalMinorUnits(invalidInputs.second, settings), "$numberFormat grouping")
            if (numberFormat == "comma-dot-in") {
                assertEquals(123_456_789L, platformParseDecimalMinorUnits("1,234,567.89", settings), "western grouping")
            }
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

    private fun settings(numberFormat: String) =
        BudgetDisplaySettings(
            currencyCode = "USD",
            currencySymbol = "$",
            currencyFractionDigits = 2,
            numberFormat = numberFormat,
            hideFraction = false,
            currencySymbolPosition = "before",
            currencySpaceBetweenAmountAndSymbol = false,
        )
}
