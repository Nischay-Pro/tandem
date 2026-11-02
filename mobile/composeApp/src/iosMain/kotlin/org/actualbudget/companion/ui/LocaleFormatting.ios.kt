package org.actualbudget.companion.ui

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

internal actual fun platformFormatCurrency(minorUnits: Long): String {
    val formatter =
        NSNumberFormatter().apply {
            locale = NSLocale.currentLocale
            numberStyle = NSNumberFormatterCurrencyStyle
        }
    return formatter.stringFromNumber(NSNumber(minorUnits.toDouble() / 100.0)) ?: ""
}

internal actual fun platformFormatDecimal(minorUnits: Long): String {
    val fractionDigits = currencyFractionDigits()
    val formatter =
        NSNumberFormatter().apply {
            locale = NSLocale.currentLocale
            numberStyle = NSNumberFormatterDecimalStyle
            usesGroupingSeparator = false
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }
    return formatter.stringFromNumber(NSNumber(minorUnits.toDouble() / 100.0)) ?: ""
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

private fun currencyFractionDigits(): ULong =
    NSNumberFormatter()
        .apply {
            locale = NSLocale.currentLocale
            numberStyle = NSNumberFormatterCurrencyStyle
        }.maximumFractionDigits

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
