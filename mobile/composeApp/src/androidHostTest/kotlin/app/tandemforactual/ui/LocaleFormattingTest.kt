package app.tandemforactual.ui

import app.tandemforactual.data.store.BudgetDisplaySettings
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocaleFormattingTest {
    @Test
    fun transactionDatesUseThePhoneLocaleMediumDateFormat() {
        withPhoneLocale(Locale.US) {
            val expected = "May 14, 2026"
            assertEquals(expected, platformFormatDate(2026, 5, 14, includeYear = true))
            assertEquals(expected, formatLiveDate("2026-05-14"))
        }
        withPhoneLocale(Locale.UK) {
            val expected = "14 May 2026"
            assertEquals(expected, platformFormatDate(2026, 5, 14, includeYear = true))
            assertEquals(expected, formatLiveDate("2026-05-14"))
        }
    }

    @Test
    fun selectedBudgetCurrencyAndPrecisionOverrideThePhoneLocale() {
        withPhoneLocale(Locale.JAPAN) {
            val cad = displaySettings(currencyCode = "CAD", numberFormat = "comma-dot")
            assertEquals("CA$1,234.56", platformFormatCurrency(123_456, cad))
            assertEquals("-CA$1,234.56", platformFormatCurrency(-123_456, cad))

            val spacedSuffixCad = cad.copy(currencySymbolPosition = "after", currencySpaceBetweenAmountAndSymbol = true)
            assertEquals("-1,234.56\u202FCA$", platformFormatCurrency(-123_456, spacedSuffixCad))

            val usd = displaySettings(currencyCode = "USD", numberFormat = "comma-dot")

            assertEquals("$1,234.56", platformFormatCurrency(123_456, usd))
            assertEquals("1,234.56", platformFormatDecimal(123_456, usd))
            assertEquals(123_456L, platformParseDecimalMinorUnits("1,234.56", usd))

            val jpy = displaySettings(currencyCode = "JPY", numberFormat = "comma-dot")
            assertEquals("¥123,456", platformFormatCurrency(123_456, jpy))
            assertEquals("123,456", platformFormatDecimal(123_456, jpy))
            assertEquals(123_456L, platformParseDecimalMinorUnits("123,456", jpy))

            val clp =
                displaySettings(
                    currencyCode = "CLP",
                    currencySymbol = "CLP$",
                    currencyFractionDigits = 2,
                    numberFormat = "dot-comma",
                )
            assertEquals("CLP$1.234,56", platformFormatCurrency(123_456, clp))
            assertEquals(123_456L, platformParseDecimalMinorUnits("1.234,56", clp))

            val irr =
                displaySettings(
                    currencyCode = "IRR",
                    currencySymbol = "﷼",
                    currencyFractionDigits = 0,
                    numberFormat = "comma-dot",
                    currencySymbolPosition = "after",
                )
            assertEquals("123,456﷼", platformFormatCurrency(123_456, irr))
            assertEquals("123,456", platformFormatDecimal(123_456, irr))
            assertEquals(123_456L, platformParseDecimalMinorUnits("123,456", irr))
        }
    }

    @Test
    fun actualNumberFormatIsUsedAndHiddenFractionsDoNotChangeEditValues() {
        withPhoneLocale(Locale.JAPAN) {
            val german = displaySettings(currencyCode = "USD", numberFormat = "dot-comma")
            assertEquals("$1.234,56", platformFormatCurrency(123_456, german))
            assertEquals(123_456L, platformParseDecimalMinorUnits("1.234,56", german))

            val hideFraction = german.copy(hideFraction = true)
            assertEquals("$1.235", platformFormatCurrency(123_456, hideFraction))
            assertEquals("1.234,56", platformFormatDecimal(123_456, hideFraction))
            assertEquals(123_456L, platformParseDecimalMinorUnits("1.234,56", hideFraction))
        }
    }

    @Test
    fun amountParsingRejectsSwappedDecimalMarksAndMalformedGrouping() {
        val us = displaySettings(currencyCode = "USD", numberFormat = "comma-dot")
        val germany = displaySettings(currencyCode = "EUR", numberFormat = "dot-comma", currencySymbol = "€")

        assertEquals(1_250L, platformParseDecimalMinorUnits("12.50", us))
        assertEquals(1_250L, platformParseDecimalMinorUnits("12.5", us))
        assertNull(platformParseDecimalMinorUnits("12,50", us))
        assertNull(platformParseDecimalMinorUnits("1,5", us))
        assertEquals(123_456L, platformParseDecimalMinorUnits("1,234.56", us))
        assertNull(platformParseDecimalMinorUnits("12,34.56", us))

        assertEquals(1_250L, platformParseDecimalMinorUnits("12,50", germany))
        assertEquals(1_250L, platformParseDecimalMinorUnits("12,5", germany))
        assertNull(platformParseDecimalMinorUnits("12.50", germany))
        assertNull(platformParseDecimalMinorUnits("12.5", germany))
        assertEquals(123_456L, platformParseDecimalMinorUnits("1.234,56", germany))
        assertEquals(123_456L, platformParseDecimalMinorUnits("1234,56", germany))
        assertNull(platformParseDecimalMinorUnits("1.23.4,56", germany))
    }

    @Test
    fun amountParsingRejectsTheOtherDecimalMarkForEveryBudgetFormat() {
        val cases =
            listOf(
                displaySettings("USD", "comma-dot") to "12,50",
                displaySettings("EUR", "dot-comma", "€") to "12.50",
                displaySettings("EUR", "space-comma", "€") to "12.50",
                displaySettings("CHF", "apostrophe-dot", "CHF") to "12,50",
                displaySettings("INR", "comma-dot-in", "₹") to "12,50",
            )

        cases.forEach { (settings, swappedDecimal) ->
            assertNull(platformParseDecimalMinorUnits(swappedDecimal, settings), settings.numberFormat)
        }
    }

    @Test
    fun threeDecimalAmountsRejectGroupingThatCouldBeAKeyboardDecimalMark() {
        val us = displaySettings("USD", "comma-dot", currencyFractionDigits = 3)
        val germany = displaySettings("EUR", "dot-comma", "€", currencyFractionDigits = 3)

        assertEquals(12_345L, platformParseDecimalMinorUnits("12.345", us))
        assertNull(platformParseDecimalMinorUnits("12,345", us))
        assertEquals("12.345", platformFormatDecimal(12_345L, us))

        assertEquals(12_345L, platformParseDecimalMinorUnits("12,345", germany))
        assertNull(platformParseDecimalMinorUnits("12.345", germany))
        assertEquals("12,345", platformFormatDecimal(12_345L, germany))
    }

    @Test
    fun invalidAmountMessageNamesTheBudgetDecimalMark() {
        val us = displaySettings(currencyCode = "USD", numberFormat = "comma-dot")
        val germany = displaySettings(currencyCode = "EUR", numberFormat = "dot-comma", currencySymbol = "€")
        val yen = displaySettings(currencyCode = "JPY", numberFormat = "comma-dot", currencySymbol = "¥", currencyFractionDigits = 0)

        assertEquals("Use a period for decimals in this budget, such as 12.00.", transactionAmountInputError("12,50", us))
        assertEquals("Use a comma for decimals in this budget, such as 12,00.", transactionAmountInputError("12.50", germany))
        assertEquals("This budget doesn’t use decimals. Enter a whole amount, such as 12.", transactionAmountInputError("12.50", yen))
    }

    @Test
    fun nonCanonicalParsedAmountsRequireAFormattedPreviewConfirmation() {
        val us = displaySettings(currencyCode = "USD", numberFormat = "comma-dot")
        val germany = displaySettings(currencyCode = "EUR", numberFormat = "dot-comma", currencySymbol = "€")

        assertFalse(transactionAmountNeedsFormatConfirmation("1,234.56", 123_456, us))
        assertTrue(transactionAmountNeedsFormatConfirmation("1234,56", 123_456, germany))
        assertEquals("1.234,56", formatAmount(123_456, germany))
    }

    @Test
    fun amountParsingUsesValidGroupingForEveryBudgetNumberFormat() {
        val examples =
            listOf(
                displaySettings("USD", "comma-dot") to "1,234,567.89",
                displaySettings("EUR", "dot-comma", "€") to "1.234.567,89",
                displaySettings("EUR", "space-comma", "€") to "1\u202f234\u202f567,89",
                displaySettings("CHF", "apostrophe-dot", "CHF") to "1’234’567.89",
                displaySettings("INR", "comma-dot-in", "₹") to "12,34,567.89",
            )

        examples.forEach { (settings, formatted) ->
            assertEquals(123_456_789L, platformParseDecimalMinorUnits(formatted, settings), formatted)
            val platformFormatted = platformFormatDecimal(123_456_789L, settings)
            assertEquals(
                123_456_789L,
                platformParseDecimalMinorUnits(platformFormatted, settings),
                "${settings.numberFormat}: $platformFormatted",
            )
        }

        val indian = displaySettings("INR", "comma-dot-in", "₹")
        assertEquals(123_456_789L, platformParseDecimalMinorUnits("1,234,567.89", indian))
        assertNull(platformParseDecimalMinorUnits("12,34.56", indian))
        assertEquals(123_456L, platformParseDecimalMinorUnits("123,456", displaySettings("JPY", "comma-dot", "¥", 0)))
        assertNull(platformParseDecimalMinorUnits("123.45", displaySettings("JPY", "comma-dot", "¥", 0)))
    }

    @Test
    fun reportCurrencyUsesPhoneLocaleWithBudgetCurrencyPrecision() {
        withPhoneLocale(Locale.GERMANY) {
            val usd = platformFormatReportCurrency(123_456, "USD", 2)
            assertTrue(usd.contains("1.234,56"), usd)

            val jpy = platformFormatReportCurrency(123_456, "JPY", 0)
            assertTrue(jpy.contains("123.456"), jpy)
            assertFalse(jpy.contains(","), jpy)
        }
    }

    private fun displaySettings(
        currencyCode: String,
        numberFormat: String,
        currencySymbol: String =
            when (currencyCode) {
                "CAD" -> "CA$"
                "JPY" -> "¥"
                else -> "$"
            },
        currencyFractionDigits: Int = if (currencyCode == "JPY") 0 else 2,
        currencySymbolPosition: String = "before",
    ): BudgetDisplaySettings =
        BudgetDisplaySettings(
            currencyCode = currencyCode,
            currencySymbol = currencySymbol,
            currencyFractionDigits = currencyFractionDigits,
            numberFormat = numberFormat,
            hideFraction = false,
            currencySymbolPosition = currencySymbolPosition,
            currencySpaceBetweenAmountAndSymbol = false,
        )

    private inline fun withPhoneLocale(
        locale: Locale,
        block: () -> Unit,
    ) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}
