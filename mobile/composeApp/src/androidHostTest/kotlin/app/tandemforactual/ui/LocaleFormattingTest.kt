package app.tandemforactual.ui

import app.tandemforactual.data.store.BudgetDisplaySettings
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleFormattingTest {
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
