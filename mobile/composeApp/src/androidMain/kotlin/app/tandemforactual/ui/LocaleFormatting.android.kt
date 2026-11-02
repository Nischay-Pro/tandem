package app.tandemforactual.ui

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

internal actual fun platformFormatCurrency(minorUnits: Long): String =
    NumberFormat.getCurrencyInstance(Locale.getDefault()).format(BigDecimal.valueOf(minorUnits, 2))

internal actual fun platformFormatDecimal(minorUnits: Long): String {
    val locale = Locale.getDefault()
    val fractionDigits = runCatching { Currency.getInstance(locale).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)
    return NumberFormat
        .getNumberInstance(locale)
        .apply {
            isGroupingUsed = false
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }.format(BigDecimal.valueOf(minorUnits, 2))
}

internal actual fun platformParseDecimalMinorUnits(value: String): Long? {
    val input = value.trim()
    if (input.isEmpty()) return null
    val position = ParsePosition(0)
    val parsed = NumberFormat.getNumberInstance(Locale.getDefault()).parse(input, position) ?: return null
    if (position.index != input.length || position.errorIndex >= 0) return null
    return runCatching {
        BigDecimal(parsed.toString()).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    }.getOrNull()
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
