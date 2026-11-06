package app.tandemforactual.ui

import app.tandemforactual.data.store.BudgetDisplaySettings
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitWeekday
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCurrencyCode
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.currentLocale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

internal actual fun platformFormatCurrency(
    minorUnits: Long,
    displaySettings: BudgetDisplaySettings?,
): String {
    if (displaySettings == null) {
        val formatter =
            NSNumberFormatter().apply {
                locale = NSLocale.currentLocale
                numberStyle = NSNumberFormatterCurrencyStyle
            }
        return formatter.stringFromNumber(NSNumber(minorUnits.toDouble() / 100.0)) ?: ""
    }
    val fractionDigits = displaySettings.currencyFractionDigits()
    val amount = formatBudgetNumber(abs(minorUnits.toDouble()), displaySettings, fractionDigits)
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

internal actual fun platformFormatReportCurrency(
    minorUnits: Long,
    currencyCode: String?,
    fractionDigits: Int?,
): String {
    val formatter =
        NSNumberFormatter().apply {
            locale = NSLocale.currentLocale
            numberStyle = NSNumberFormatterCurrencyStyle
            if (currencyCode != null) this.currencyCode = currencyCode
        }
    val digits = fractionDigits?.coerceIn(0, 6)?.toULong() ?: formatter.maximumFractionDigits
    formatter.minimumFractionDigits = digits
    formatter.maximumFractionDigits = digits
    return formatter.stringFromNumber(NSNumber(minorUnits.toDouble() / 10.0.pow(digits.toDouble()))) ?: ""
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
    val formatter =
        NSNumberFormatter().apply {
            locale = numberLocale(displaySettings)
            numberStyle = NSNumberFormatterDecimalStyle
            usesGroupingSeparator = true
            maximumFractionDigits = displaySettings.currencyFractionDigits().toULong()
        }
    val amount = formatter.numberFromString(input)?.doubleValue ?: return null
    val minorUnits = amount * 10.0.pow(displaySettings.currencyFractionDigits().toDouble())
    if (!minorUnits.isFinite() || abs(minorUnits) > Long.MAX_VALUE.toDouble()) return null
    val rounded = minorUnits.roundToLong()
    return rounded.takeIf { abs(minorUnits - it.toDouble()) < 0.000001 }
}

private fun formatBudgetNumber(
    minorUnits: Long,
    displaySettings: BudgetDisplaySettings?,
    fractionDigits: Int,
    hideFraction: Boolean = displaySettings?.hideFraction == true,
): String = formatBudgetNumber(minorUnits.toDouble(), displaySettings, fractionDigits, hideFraction)

private fun formatBudgetNumber(
    minorUnits: Double,
    displaySettings: BudgetDisplaySettings?,
    fractionDigits: Int,
    hideFraction: Boolean = displaySettings?.hideFraction == true,
): String {
    val visibleDigits = if (hideFraction) 0 else fractionDigits
    val formatter =
        NSNumberFormatter().apply {
            locale = numberLocale(displaySettings)
            numberStyle = NSNumberFormatterDecimalStyle
            usesGroupingSeparator = true
            minimumFractionDigits = visibleDigits.toULong()
            maximumFractionDigits = visibleDigits.toULong()
        }
    val amount = minorUnits / 10.0.pow(fractionDigits.toDouble())
    return formatter.stringFromNumber(NSNumber(amount)) ?: ""
}

private fun numberLocale(displaySettings: BudgetDisplaySettings?): NSLocale =
    when (displaySettings?.numberFormat) {
        "comma-dot" -> NSLocale(localeIdentifier = "en_US")
        "dot-comma" -> NSLocale(localeIdentifier = "de_DE")
        "space-comma" -> NSLocale(localeIdentifier = "fr_FR")
        "apostrophe-dot" -> NSLocale(localeIdentifier = "de_CH")
        "comma-dot-in" -> NSLocale(localeIdentifier = "en_IN")
        else -> NSLocale.currentLocale
    }

internal actual fun platformFormatDate(
    year: Int,
    month: Int,
    day: Int,
    includeYear: Boolean,
): String =
    NSDateFormatter()
        .apply {
            locale = NSLocale.currentLocale
            dateStyle = if (includeYear) NSDateFormatterMediumStyle else NSDateFormatterShortStyle
        }.stringFromDate(calendarDate(year, month, day))

internal actual fun platformTodayIsoDate(): String =
    NSDateFormatter()
        .apply {
            locale = NSLocale(localeIdentifier = "en_US_POSIX")
            dateFormat = "yyyy-MM-dd"
        }.stringFromDate(NSDate())

internal actual fun platformFormatMonth(
    year: Int,
    month: Int,
    abbreviated: Boolean,
): String =
    NSDateFormatter()
        .apply {
            locale = NSLocale.currentLocale
            dateFormat = if (abbreviated) "MMM yyyy" else "MMMM yyyy"
        }.stringFromDate(calendarDate(year, month, 1))

internal actual fun platformFormatInteger(value: Int): String {
    val formatter =
        NSNumberFormatter().apply {
            locale = NSLocale.currentLocale
            numberStyle = NSNumberFormatterDecimalStyle
            maximumFractionDigits = 0uL
        }
    return formatter.stringFromNumber(NSNumber(value)) ?: value.toString()
}

internal actual fun platformCurrencyCode(): String = NSLocale.currentLocale.objectForKey(NSLocaleCurrencyCode) as String

private fun BudgetDisplaySettings?.currencyFractionDigits(): Int {
    if (this != null) return this.currencyFractionDigits
    val formatter =
        NSNumberFormatter().apply {
            locale = NSLocale.currentLocale
            numberStyle = NSNumberFormatterCurrencyStyle
        }
    return formatter.maximumFractionDigits.toInt()
}

internal actual fun platformFirstWeekday(): Int = NSCalendar.currentCalendar.firstWeekday.toInt()

internal actual fun platformWeekday(
    year: Int,
    month: Int,
    day: Int,
): Int = NSCalendar.currentCalendar.component(NSCalendarUnitWeekday, fromDate = calendarDate(year, month, day)).toInt()

internal actual fun platformWeekdayName(
    year: Int,
    month: Int,
    day: Int,
): String =
    NSDateFormatter()
        .apply {
            locale = NSLocale.currentLocale
            dateFormat = "EEEEE"
        }.stringFromDate(calendarDate(year, month, day))

private fun calendarDate(
    year: Int,
    month: Int,
    day: Int,
): NSDate {
    val components =
        NSDateComponents().apply {
            this.year = year.toLong()
            this.month = month.toLong()
            this.day = day.toLong()
            hour = 12
        }
    return NSCalendar.currentCalendar.dateFromComponents(components) ?: NSDate()
}
