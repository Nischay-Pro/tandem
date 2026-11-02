package app.tandemforactual.ui

internal data class SampleDate(
    val year: Int,
    val month: Int,
    val day: Int,
)

internal expect fun platformFormatCurrency(minorUnits: Long): String

internal expect fun platformFormatDecimal(minorUnits: Long): String

internal expect fun platformFormatDate(
    year: Int,
    month: Int,
    day: Int,
    includeYear: Boolean,
): String

internal expect fun platformFormatMonth(
    year: Int,
    month: Int,
    abbreviated: Boolean,
): String

internal expect fun platformFormatInteger(value: Int): String

internal expect fun platformCurrencyCode(): String

internal expect fun platformFirstWeekday(): Int

internal expect fun platformWeekday(
    year: Int,
    month: Int,
    day: Int,
): Int

internal expect fun platformWeekdayName(
    year: Int,
    month: Int,
    day: Int,
): String

internal fun formatCurrency(minorUnits: Long): String = platformFormatCurrency(minorUnits)

internal fun formatAmount(minorUnits: Long): String = platformFormatDecimal(minorUnits)

internal fun formatSignedCurrency(minorUnits: Long): String =
    if (minorUnits > 0) "+${formatCurrency(minorUnits)}" else formatCurrency(minorUnits)

internal fun formatDate(date: SampleDate): String = platformFormatDate(date.year, date.month, date.day, includeYear = true)

internal fun formatShortDate(date: SampleDate): String = platformFormatDate(date.year, date.month, date.day, includeYear = false)

internal fun formatMonth(
    year: Int,
    month: Int,
): String = platformFormatMonth(year, month, abbreviated = false)

internal fun formatShortMonth(
    year: Int,
    month: Int,
): String = platformFormatMonth(year, month, abbreviated = true)

internal fun formatInteger(value: Int): String = platformFormatInteger(value)

internal fun formatWeekday(
    year: Int,
    month: Int,
    day: Int,
): String = platformWeekdayName(year, month, day)

internal fun formatFirstWeekday(): String {
    val firstWeekday = platformFirstWeekday()
    val mayFirstWeekday = platformWeekday(2026, 5, 1)
    val representativeDay = 1 + ((firstWeekday - mayFirstWeekday + 7) % 7)
    return formatWeekday(2026, 5, representativeDay)
}
