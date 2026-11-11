package app.tandemforactual.ui

import app.tandemforactual.data.store.BudgetDisplaySettings

internal data class CalendarDate(
    val year: Int,
    val month: Int,
    val day: Int,
)

internal expect fun platformFormatCurrency(
    minorUnits: Long,
    displaySettings: BudgetDisplaySettings?,
): String

internal expect fun platformFormatReportCurrency(
    minorUnits: Long,
    currencyCode: String?,
    fractionDigits: Int?,
): String

internal expect fun platformFormatDecimal(
    minorUnits: Long,
    displaySettings: BudgetDisplaySettings?,
): String

internal expect fun platformParseDecimalMinorUnits(
    value: String,
    displaySettings: BudgetDisplaySettings?,
): Long?

internal expect fun platformFormatDate(
    year: Int,
    month: Int,
    day: Int,
    includeYear: Boolean,
): String

internal expect fun platformFormatDateAtEpochMilliseconds(epochMilliseconds: Long): String

internal expect fun platformIsoDateAtEpochMilliseconds(epochMilliseconds: Long): String

internal expect fun platformTodayIsoDate(): String

internal expect fun platformFormatMonth(
    year: Int,
    month: Int,
    abbreviated: Boolean,
): String

internal expect fun platformFormatInteger(value: Int): String

internal expect fun platformFormatReportPercentage(ratio: Double): String

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

internal expect fun platformFullWeekdayName(
    year: Int,
    month: Int,
    day: Int,
): String

internal fun formatCurrency(
    minorUnits: Long,
    displaySettings: BudgetDisplaySettings? = null,
): String = platformFormatCurrency(minorUnits, displaySettings)

internal fun formatReportCurrency(
    minorUnits: Long,
    displaySettings: BudgetDisplaySettings?,
): String =
    platformFormatReportCurrency(
        minorUnits,
        displaySettings?.currencyCode,
        displaySettings?.currencyFractionDigits,
    )

internal fun formatAmount(
    minorUnits: Long,
    displaySettings: BudgetDisplaySettings? = null,
): String = platformFormatDecimal(minorUnits, displaySettings)

internal fun parseAmountMinorUnits(
    value: String,
    displaySettings: BudgetDisplaySettings? = null,
): Long? = platformParseDecimalMinorUnits(value, displaySettings)

internal fun formatSignedCurrency(
    minorUnits: Long,
    displaySettings: BudgetDisplaySettings? = null,
): String = if (minorUnits > 0) "+${formatCurrency(minorUnits, displaySettings)}" else formatCurrency(minorUnits, displaySettings)

internal fun formatDate(date: CalendarDate): String = platformFormatDate(date.year, date.month, date.day, includeYear = true)

internal fun formatDateAtEpochMilliseconds(epochMilliseconds: Long): String = platformFormatDateAtEpochMilliseconds(epochMilliseconds)

internal fun formatShortDate(date: CalendarDate): String = platformFormatDate(date.year, date.month, date.day, includeYear = false)

internal fun formatMonth(
    year: Int,
    month: Int,
): String = platformFormatMonth(year, month, abbreviated = false)

internal fun formatShortMonth(
    year: Int,
    month: Int,
): String = platformFormatMonth(year, month, abbreviated = true)

internal fun formatInteger(value: Int): String = platformFormatInteger(value)

internal fun formatReportPercentage(ratio: Double): String = platformFormatReportPercentage(ratio)

internal fun formatWeekday(
    year: Int,
    month: Int,
    day: Int,
): String = platformWeekdayName(year, month, day)

internal fun formatFirstWeekday(): String {
    val firstWeekday = platformFirstWeekday()
    val mayFirstWeekday = platformWeekday(2026, 5, 1)
    val representativeDay = 1 + ((firstWeekday - mayFirstWeekday + 7) % 7)
    return platformFullWeekdayName(2026, 5, representativeDay)
}
