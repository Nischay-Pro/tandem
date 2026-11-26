package app.tandemforactual.data.actual

import app.tandemforactual.data.store.BudgetDisplaySettings
import kotlin.test.Test
import kotlin.test.assertEquals

class ActualMovementNotesTest {
    private fun settings(
        numberFormat: String?,
        places: Int = 2,
    ) = BudgetDisplaySettings(
        currencyCode = "USD",
        currencySymbol = "$",
        currencyFractionDigits = places,
        numberFormat = numberFormat,
        hideFraction = false,
        currencySymbolPosition = "before",
        currencySpaceBetweenAmountAndSymbol = false,
    )

    @Test
    fun amountsFollowTheBudgetsNumberFormat() {
        assertEquals("1,234,567.89", formatActualAmount(123_456_789, settings(null)))
        assertEquals("1,234,567.89", formatActualAmount(123_456_789, settings("comma-dot")))
        assertEquals("1.234.567,89", formatActualAmount(123_456_789, settings("dot-comma")))
        assertEquals("1 234 567,89", formatActualAmount(123_456_789, settings("space-comma")))
        assertEquals("1’234’567.89", formatActualAmount(123_456_789, settings("apostrophe-dot")))
        assertEquals("12,34,567.89", formatActualAmount(123_456_789, settings("comma-dot-in")))
        assertEquals("999.00", formatActualAmount(99_900, settings("comma-dot-in")))
        assertEquals("0.05", formatActualAmount(5, settings(null)))
        assertEquals("-12.50", formatActualAmount(-1_250, settings(null)))
    }

    @Test
    fun amountsUseTheCurrencysDecimalPlaces() {
        assertEquals("1,500", formatActualAmount(150_000, settings(null, places = 0)))
        assertEquals("2", formatActualAmount(150, settings(null, places = 0)))
        assertEquals("-2", formatActualAmount(-150, settings(null, places = 0)))
        assertEquals("0", formatActualAmount(-49, settings(null, places = 0)))
        assertEquals("12.340", formatActualAmount(1_234, settings(null, places = 3)))
    }
}
