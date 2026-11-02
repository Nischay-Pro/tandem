package app.tandemforactual.ui

import app.tandemforactual.data.store.BudgetDisplaySettings
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.text.NumberFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
import java.util.Date
import java.util.Locale

internal actual fun platformFormatCurrency(
    minorUnits: Long,
    displaySettings: BudgetDisplaySettings?,
): String {
    if (displaySettings == null) {
        val locale = Locale.getDefault()
        val currency = runCatching { Currency.getInstance(locale) }.getOrNull()
        val fractionDigits = currency?.defaultFractionDigits?.takeIf { it >= 0 } ?: 2
        return NumberFormat
            .getCurrencyInstance(locale)
            .apply {
                if (currency != null) this.currency = currency
                minimumFractionDigits = fractionDigits
                maximumFractionDigits = fractionDigits
            }.format(BigDecimal.valueOf(minorUnits).movePointLeft(fractionDigits))
    }

    val fractionDigits = displaySettings.currencyFractionDigits
    val amount = formatBudgetNumber(BigDecimal.valueOf(minorUnits).abs(), displaySettings, fractionDigits)
    val symbol = displaySettings.currencySymbol
    val separator = if (displaySettings.currencySpaceBetweenAmountAndSymbol) "\u202F" else ""
    val formatted =
        if (displaySettings.currencySymbolPosition == "after") {
            "$amount$separator$symbol"
        } else {
            "$symbol$separator$amount"
        }
    return if (minorUnits < 0) "-$formatted" else formatted
}

internal actual fun platformFormatDecimal(
    minorUnits: Long,
    displaySettings: BudgetDisplaySettings?,
): String = formatBudgetNumber(minorUnits, displaySettings, displaySettings.currencyFractionDigits(), hideFraction = false)

internal actual fun platformParseDecimalMinorUnits(
    value: String,
    displaySettings: BudgetDisplaySettings?,
): Long? {
    val input = value.trim()
    if (input.isEmpty()) return null
    val position = ParsePosition(0)
    val parsed = budgetNumberFormat(displaySettings, displaySettings.currencyFractionDigits()).parse(input, position) ?: return null
    if (position.index != input.length || position.errorIndex >= 0) return null
    return runCatching {
        BigDecimal(parsed.toString())
            .movePointRight(displaySettings.currencyFractionDigits())
            .setScale(0, RoundingMode.UNNECESSARY)
            .longValueExact()
    }.getOrNull()
}

private fun formatBudgetNumber(
    minorUnits: Long,
    displaySettings: BudgetDisplaySettings?,
    fractionDigits: Int,
    hideFraction: Boolean = displaySettings?.hideFraction == true,
): String = formatBudgetNumber(BigDecimal.valueOf(minorUnits), displaySettings, fractionDigits, hideFraction)

private fun formatBudgetNumber(
    minorUnits: BigDecimal,
    displaySettings: BudgetDisplaySettings?,
    fractionDigits: Int,
    hideFraction: Boolean = displaySettings?.hideFraction == true,
): String {
    val visibleDigits = if (hideFraction) 0 else fractionDigits
    return budgetNumberFormat(displaySettings, visibleDigits)
        .format(minorUnits.movePointLeft(fractionDigits))
}

private fun budgetNumberFormat(
    displaySettings: BudgetDisplaySettings?,
    fractionDigits: Int,
): NumberFormat {
    val locale =
        when (displaySettings?.numberFormat) {
            "comma-dot" -> Locale.US
            "dot-comma" -> Locale.GERMANY
            "space-comma" -> Locale.FRANCE
            "apostrophe-dot" -> Locale.forLanguageTag("de-CH")
            "comma-dot-in" -> Locale.forLanguageTag("en-IN")
            else -> Locale.getDefault()
        }
    return NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }
}

private fun BudgetDisplaySettings?.currencyFractionDigits(): Int {
    if (this != null) return this.currencyFractionDigits
    val currency =
        runCatching { Currency.getInstance(Locale.getDefault()) }.getOrNull()
    return currency?.defaultFractionDigits?.takeIf { it >= 0 } ?: 2
}

internal actual fun platformFormatDate(
    year: Int,
    month: Int,
    day: Int,
    includeYear: Boolean,
): String =
    DateFormat
        .getDateInstance(
            if (includeYear) DateFormat.MEDIUM else DateFormat.SHORT,
            Locale.getDefault(),
        ).format(calendarDate(year, month, day))

internal actual fun platformFormatMonth(
    year: Int,
    month: Int,
    abbreviated: Boolean,
): String =
    SimpleDateFormat(if (abbreviated) "MMM yyyy" else "MMMM yyyy", Locale.getDefault())
        .format(calendarDate(year, month, 1))

internal actual fun platformFormatInteger(value: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

internal actual fun platformCurrencyCode(): String =
    runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault("XXX")

internal actual fun platformFirstWeekday(): Int = Calendar.getInstance(Locale.getDefault()).firstDayOfWeek

internal actual fun platformWeekday(
    year: Int,
    month: Int,
    day: Int,
): Int = Calendar.getInstance(Locale.getDefault()).apply { time = calendarDate(year, month, day) }.get(Calendar.DAY_OF_WEEK)

internal actual fun platformWeekdayName(
    year: Int,
    month: Int,
    day: Int,
): String = SimpleDateFormat("EEEEE", Locale.getDefault()).format(calendarDate(year, month, day))

private fun calendarDate(
    year: Int,
    month: Int,
    day: Int,
): Date =
    Calendar
        .getInstance(Locale.getDefault())
        .apply {
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }.time
