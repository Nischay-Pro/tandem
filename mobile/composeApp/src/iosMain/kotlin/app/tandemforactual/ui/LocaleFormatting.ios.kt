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
import platform.Foundation.NSNumberFormatterPercentStyle
import platform.Foundation.NSRecursiveLock
import platform.Foundation.currentLocale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

internal actual fun platformFormatCurrency(
    minorUnits: Long,
    displaySettings: BudgetDisplaySettings?,
): String {
    if (displaySettings == null) {
        val locale = NSLocale.currentLocale
        return withNumberFormatter("currency:$locale", {
            NSNumberFormatter().apply {
                this.locale = locale
                numberStyle = NSNumberFormatterCurrencyStyle
            }
        }) { formatter ->
            formatter.stringFromNumber(NSNumber(minorUnits.toDouble() / 100.0)) ?: ""
        }
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
    val locale = NSLocale.currentLocale
    val defaultDigits =
        withNumberFormatter("currency:$locale:$currencyCode", {
            NSNumberFormatter().apply {
                this.locale = locale
                numberStyle = NSNumberFormatterCurrencyStyle
                if (currencyCode != null) this.currencyCode = currencyCode
            }
        }) { it.maximumFractionDigits.toInt() }
    val digits = fractionDigits?.coerceIn(0, 6) ?: defaultDigits
    return withNumberFormatter("currency:$locale:$currencyCode:$digits", {
        NSNumberFormatter().apply {
            this.locale = locale
            numberStyle = NSNumberFormatterCurrencyStyle
            if (currencyCode != null) this.currencyCode = currencyCode
            minimumFractionDigits = digits.toULong()
            maximumFractionDigits = digits.toULong()
        }
    }) { formatter ->
        formatter.stringFromNumber(NSNumber(minorUnits.toDouble() / 10.0.pow(digits.toDouble()))) ?: ""
    }
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
    val locale = numberLocale(displaySettings)
    val fractionDigits = displaySettings.currencyFractionDigits()
    val amount =
        withNumberFormatter("decimal:$locale:$fractionDigits", {
            NSNumberFormatter().apply {
                this.locale = locale
                numberStyle = NSNumberFormatterDecimalStyle
                usesGroupingSeparator = true
                maximumFractionDigits = fractionDigits.toULong()
            }
        }) { it.numberFromString(input)?.doubleValue } ?: return null
    val minorUnits = amount * 10.0.pow(fractionDigits.toDouble())
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
    val locale = numberLocale(displaySettings)
    val amount = minorUnits / 10.0.pow(fractionDigits.toDouble())
    return withNumberFormatter("decimal:$locale:$visibleDigits", {
        NSNumberFormatter().apply {
            this.locale = locale
            numberStyle = NSNumberFormatterDecimalStyle
            usesGroupingSeparator = true
            minimumFractionDigits = visibleDigits.toULong()
            maximumFractionDigits = visibleDigits.toULong()
        }
    }) { it.stringFromNumber(NSNumber(amount)) ?: "" }
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
): String {
    val locale = NSLocale.currentLocale
    return withDateFormatter("date:$locale:$includeYear", {
        NSDateFormatter().apply {
            this.locale = locale
            dateStyle = if (includeYear) NSDateFormatterMediumStyle else NSDateFormatterShortStyle
        }
    }) { it.stringFromDate(calendarDate(year, month, day)) }
}

internal actual fun platformTodayIsoDate(): String {
    val locale = NSLocale(localeIdentifier = "en_US_POSIX")
    return withDateFormatter("date:iso", {
        NSDateFormatter().apply {
            this.locale = locale
            dateFormat = "yyyy-MM-dd"
        }
    }) { it.stringFromDate(NSDate()) }
}

internal actual fun platformFormatMonth(
    year: Int,
    month: Int,
    abbreviated: Boolean,
): String {
    val locale = NSLocale.currentLocale
    val pattern = if (abbreviated) "MMM yyyy" else "MMMM yyyy"
    return withDateFormatter("month:$locale:$pattern", {
        NSDateFormatter().apply {
            this.locale = locale
            dateFormat = pattern
        }
    }) { it.stringFromDate(calendarDate(year, month, 1)) }
}

internal actual fun platformFormatInteger(value: Int): String {
    val locale = NSLocale.currentLocale
    return withNumberFormatter("integer:$locale", {
        NSNumberFormatter().apply {
            this.locale = locale
            numberStyle = NSNumberFormatterDecimalStyle
            maximumFractionDigits = 0uL
        }
    }) { it.stringFromNumber(NSNumber(value)) ?: value.toString() }
}

internal actual fun platformFormatReportPercentage(ratio: Double): String {
    val locale = NSLocale.currentLocale
    return withNumberFormatter("percent:$locale", {
        NSNumberFormatter().apply {
            this.locale = locale
            numberStyle = NSNumberFormatterPercentStyle
            minimumFractionDigits = 0uL
            maximumFractionDigits = 1uL
        }
    }) { it.stringFromNumber(NSNumber(ratio.coerceAtLeast(0.0))) ?: "0%" }
}

internal actual fun platformCurrencyCode(): String = NSLocale.currentLocale.objectForKey(NSLocaleCurrencyCode) as String

private fun BudgetDisplaySettings?.currencyFractionDigits(): Int {
    if (this != null) return this.currencyFractionDigits
    val locale = NSLocale.currentLocale
    return withNumberFormatter("currency:$locale", {
        NSNumberFormatter().apply {
            this.locale = locale
            numberStyle = NSNumberFormatterCurrencyStyle
        }
    }) { it.maximumFractionDigits.toInt() }
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
): String {
    val locale = NSLocale.currentLocale
    return withDateFormatter("weekday:$locale", {
        NSDateFormatter().apply {
            this.locale = locale
            dateFormat = "EEEEE"
        }
    }) { it.stringFromDate(calendarDate(year, month, day)) }
}

private object IosFormatterCache {
    private val lock = NSRecursiveLock()
    private val numbers = linkedMapOf<String, NSNumberFormatter>()
    private val dates = linkedMapOf<String, NSDateFormatter>()

    fun <T> withNumber(
        key: String,
        create: () -> NSNumberFormatter,
        use: (NSNumberFormatter) -> T,
    ): T {
        lock.lock()
        try {
            val formatter = numbers.remove(key) ?: create()
            numbers[key] = formatter
            trim(numbers)
            return use(formatter)
        } finally {
            lock.unlock()
        }
    }

    fun <T> withDate(
        key: String,
        create: () -> NSDateFormatter,
        use: (NSDateFormatter) -> T,
    ): T {
        lock.lock()
        try {
            val formatter = dates.remove(key) ?: create()
            dates[key] = formatter
            trim(dates)
            return use(formatter)
        } finally {
            lock.unlock()
        }
    }

    private fun <T> trim(cache: LinkedHashMap<String, T>) {
        while (cache.size > 64) cache.remove(cache.keys.first())
    }
}

private fun <T> withNumberFormatter(
    key: String,
    create: () -> NSNumberFormatter,
    use: (NSNumberFormatter) -> T,
): T = IosFormatterCache.withNumber(key, create, use)

private fun <T> withDateFormatter(
    key: String,
    create: () -> NSDateFormatter,
    use: (NSDateFormatter) -> T,
): T = IosFormatterCache.withDate(key, create, use)

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
