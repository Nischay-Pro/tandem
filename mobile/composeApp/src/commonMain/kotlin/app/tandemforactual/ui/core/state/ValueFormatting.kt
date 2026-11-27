@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.state
import androidx.compose.foundation.layout.size
import app.tandemforactual.ui.core.format.CalendarDate
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.formatDate
import app.tandemforactual.ui.core.format.formatSignedCurrency
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.format.platformTodayIsoDate

internal fun AppState.formatCurrency(minorUnits: Long): String = formatCurrency(minorUnits, budgetDisplaySettings)

internal val AppState.currencyFractionDigitsForDisplay: Int
    get() =
        budgetDisplaySettings?.let { settings ->
            if (settings.hideFraction) 0 else settings.currencyFractionDigits
        } ?: 2

internal fun AppState.formatAmount(minorUnits: Long): String = formatAmount(minorUnits, budgetDisplaySettings)

internal fun AppState.formatSignedCurrency(minorUnits: Long): String = formatSignedCurrency(minorUnits, budgetDisplaySettings)

internal fun AppState.parseAmountMinorUnits(value: String): Long? = parseAmountMinorUnits(value, budgetDisplaySettings)

internal fun formatLiveDate(value: String?): String {
    val parts = value?.split('-')
    val year = parts?.getOrNull(0)?.toIntOrNull()
    val month = parts?.getOrNull(1)?.toIntOrNull()
    val day = parts?.getOrNull(2)?.take(2)?.toIntOrNull()
    return if (year != null && month != null && day != null) {
        formatDate(CalendarDate(year, month, day))
    } else {
        value ?: "Undated"
    }
}

internal fun formatTransactionListDate(
    value: String?,
    todayIso: String = platformTodayIsoDate(),
): String {
    val date = parseCalendarDate(value) ?: return formatLiveDate(value)
    val today = parseCalendarDate(todayIso) ?: return formatDate(date)
    return when (calendarDayNumber(today) - calendarDayNumber(date)) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> formatDate(date)
    }
}

private fun parseCalendarDate(value: String?): CalendarDate? {
    val parts = value?.take(10)?.split('-') ?: return null
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    if (year < 1 || month !in 1..12 || day !in 1..calendarDaysInMonth(year, month)) return null
    return CalendarDate(year, month, day)
}

private fun calendarDaysInMonth(
    year: Int,
    month: Int,
): Int =
    when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

internal fun calendarDayNumber(date: CalendarDate): Long {
    val adjustedYear = date.year - if (date.month <= 2) 1 else 0
    val era = adjustedYear / 400
    val yearOfEra = adjustedYear - era * 400
    val adjustedMonth = date.month + if (date.month > 2) -3 else 9
    val dayOfYear = (153 * adjustedMonth + 2) / 5 + date.day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era.toLong() * 146_097 + dayOfEra
}
