package app.tandemforactual.ui.core.search

import app.tandemforactual.ui.core.format.CalendarDate
import app.tandemforactual.ui.core.format.formatDate
import app.tandemforactual.ui.core.format.formatMonth

internal fun parseActivitySearchDatePrefixes(value: String): List<String> {
    val trimmed = stripActivitySearchDatePrefix(value)
    val iso = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").matchEntire(trimmed)
    if (iso != null) {
        val year = iso.groupValues[1].toIntOrNull() ?: return emptyList()
        val month = iso.groupValues[2].toIntOrNull() ?: return emptyList()
        val day = iso.groupValues[3].toIntOrNull() ?: return emptyList()
        return validIsoDate(year, month, day)?.let(::listOf) ?: emptyList()
    }

    val numeric = Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{2}|\\d{4})$").matchEntire(trimmed) ?: return emptyList()
    val month = numeric.groupValues[1].toIntOrNull() ?: return emptyList()
    val day = numeric.groupValues[2].toIntOrNull() ?: return emptyList()
    val rawYear = numeric.groupValues[3].toIntOrNull() ?: return emptyList()
    val year = if (numeric.groupValues[3].length == 2) 2000 + rawYear else rawYear
    return validIsoDate(year, month, day)?.let(::listOf) ?: emptyList()
}

private val activitySearchMonthNames =
    listOf(
        listOf("january", "jan"),
        listOf("february", "feb"),
        listOf("march", "mar"),
        listOf("april", "apr"),
        listOf("may"),
        listOf("june", "jun"),
        listOf("july", "jul"),
        listOf("august", "aug"),
        listOf("september", "sep", "sept"),
        listOf("october", "oct"),
        listOf("november", "nov"),
        listOf("december", "dec"),
    )

private data class ActivitySearchDateEndpoint(
    val year: Int?,
    val month: Int,
    val day: Int?,
)

internal fun hasExplicitActivitySearchDatePrefix(value: String): Boolean = Regex("(?i)^date\\s*:").containsMatchIn(value.trim())

internal fun stripActivitySearchDatePrefix(value: String): String = value.trim().replace(Regex("(?i)^date\\s*:\\s*"), "").trim()

internal fun parseActivitySearchDateQuery(value: String): ActivitySearchDateQuery? {
    var expression = stripActivitySearchDatePrefix(value)
    expression = expression.replace(Regex("(?i)^from\\s+"), "").replace(Regex("(?i)^between\\s+"), "")

    val rangeParts =
        Regex("(?i)\\s+(?:to|through|thru|until|and)\\s+|\\s+-\\s+|\\s*[–—]\\s*|\\s*\\.\\.\\s*")
            .split(expression, limit = 2)
            .map(String::trim)
    if (rangeParts.size == 2) {
        val first = parseActivitySearchDateEndpoint(rangeParts[0]) ?: return null
        val second = parseActivitySearchDateEndpoint(rangeParts[1]) ?: return null
        val year = first.year ?: second.year ?: return null
        val firstYear = first.year ?: year
        val secondYear = second.year ?: year
        val start = endpointStart(first, firstYear) ?: return null
        val end = endpointEnd(second, secondYear) ?: return null
        if (start > end) return null
        return ActivitySearchDateQuery(startInclusive = start, endInclusive = end, label = "Date: ${dateRangeLabel(start, end)}")
    }

    val endpoint = parseActivitySearchDateEndpoint(expression) ?: return null
    if (endpoint.year == null) {
        return when {
            endpoint.day == null -> {
                ActivitySearchDateQuery(
                    monthOfYear = endpoint.month,
                    label = "Date: ${activitySearchMonthNames[endpoint.month - 1][0].replaceFirstChar(Char::uppercase)} (any year)",
                )
            }

            else -> {
                ActivitySearchDateQuery(
                    monthDay = "${endpoint.month.toString().padStart(2, '0')}-${endpoint.day.toString().padStart(2, '0')}",
                    label =
                        "Date: ${endpoint.day} ${activitySearchMonthNames[endpoint.month - 1][0].replaceFirstChar(
                            Char::uppercase,
                        )} (any year)",
                )
            }
        }
    }

    val start = endpointStart(endpoint, endpoint.year) ?: return null
    val end = endpointEnd(endpoint, endpoint.year) ?: return null
    val label = if (endpoint.day == null) "Date: ${formatMonth(endpoint.year, endpoint.month)}" else "Date: ${dateRangeLabel(start, end)}"
    return ActivitySearchDateQuery(startInclusive = start, endInclusive = end, label = label)
}

private fun parseActivitySearchDateEndpoint(value: String): ActivitySearchDateEndpoint? {
    val trimmed = value.trim().trimEnd('.', ',')
    val iso = Regex("^(\\d{4})[-/.](\\d{1,2})(?:[-/.](\\d{1,2}))?$").matchEntire(trimmed)
    if (iso != null) {
        val year = iso.groupValues[1].toIntOrNull() ?: return null
        val month = iso.groupValues[2].toIntOrNull() ?: return null
        val day = iso.groupValues[3].takeIf(String::isNotEmpty)?.toIntOrNull()
        if (month !in 1..12) return null
        if (day != null && validIsoDate(year, month, day) == null) return null
        return ActivitySearchDateEndpoint(year, month, day)
    }

    val numericDate = Regex("^(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2}|\\d{4})$").matchEntire(trimmed)
    if (numericDate != null) {
        val first = numericDate.groupValues[1].toIntOrNull() ?: return null
        val second = numericDate.groupValues[2].toIntOrNull() ?: return null
        val rawYear = numericDate.groupValues[3].toIntOrNull() ?: return null
        val year = if (numericDate.groupValues[3].length == 2) 2000 + rawYear else rawYear
        val monthFirst = ActivitySearchDateEndpoint(year, first, second)
        val dayFirst = ActivitySearchDateEndpoint(year, second, first)
        if (trimmed.contains('.') && validIsoDate(year, second, first) != null) return dayFirst
        if (validIsoDate(year, first, second) != null) return monthFirst
        return dayFirst.takeIf { validIsoDate(year, second, first) != null }
    }

    val numericMonth = Regex("^(\\d{1,2})[/.\\-](\\d{4})$").matchEntire(trimmed)
    if (numericMonth != null) {
        val month = numericMonth.groupValues[1].toIntOrNull() ?: return null
        val year = numericMonth.groupValues[2].toIntOrNull() ?: return null
        return ActivitySearchDateEndpoint(year, month, null).takeIf { month in 1..12 && year > 0 }
    }

    val numericDayMonth = Regex("^(\\d{1,2})/(\\d{1,2})$").matchEntire(trimmed)
    if (numericDayMonth != null) {
        val month = numericDayMonth.groupValues[1].toIntOrNull() ?: return null
        val day = numericDayMonth.groupValues[2].toIntOrNull() ?: return null
        return ActivitySearchDateEndpoint(null, month, day).takeIf { month in 1..12 && day in 1..31 }
    }

    val monthYear = Regex("^([a-zA-Z]+)\\.?\\s+(\\d{4})$").matchEntire(trimmed)
    if (monthYear != null) {
        val month = parseMonthName(monthYear.groupValues[1]) ?: return null
        val year = monthYear.groupValues[2].toIntOrNull() ?: return null
        return ActivitySearchDateEndpoint(year, month, null).takeIf { year > 0 }
    }

    val monthDayYear = Regex("^([a-zA-Z]+)\\.?\\s+(\\d{1,2})(?:,?\\s+(\\d{4}))?$").matchEntire(trimmed)
    if (monthDayYear != null) {
        val month = parseMonthName(monthDayYear.groupValues[1]) ?: return null
        val day = monthDayYear.groupValues[2].toIntOrNull() ?: return null
        val year = monthDayYear.groupValues[3].takeIf(String::isNotEmpty)?.toIntOrNull()
        if (day !in 1..31 || (year != null && validIsoDate(year, month, day) == null)) return null
        return ActivitySearchDateEndpoint(year, month, day)
    }

    val dayMonthYear = Regex("^(\\d{1,2})\\s+([a-zA-Z]+)\\.?(?:,?\\s+(\\d{4}))?$").matchEntire(trimmed)
    if (dayMonthYear != null) {
        val day = dayMonthYear.groupValues[1].toIntOrNull() ?: return null
        val month = parseMonthName(dayMonthYear.groupValues[2]) ?: return null
        val year = dayMonthYear.groupValues[3].takeIf(String::isNotEmpty)?.toIntOrNull()
        if (day !in 1..31 || (year != null && validIsoDate(year, month, day) == null)) return null
        return ActivitySearchDateEndpoint(year, month, day)
    }

    val monthOnly = parseMonthName(trimmed) ?: return null
    return ActivitySearchDateEndpoint(null, monthOnly, null)
}

private fun parseMonthName(value: String): Int? =
    activitySearchMonthNames.indexOfFirst { value.trimEnd('.').lowercase() in it }.takeIf { it >= 0 }?.plus(1)

private fun endpointStart(
    endpoint: ActivitySearchDateEndpoint,
    year: Int,
): String? =
    if (endpoint.day == null) {
        validIsoDate(year, endpoint.month, 1)
    } else {
        validIsoDate(year, endpoint.month, endpoint.day)
    }

private fun endpointEnd(
    endpoint: ActivitySearchDateEndpoint,
    year: Int,
): String? =
    if (endpoint.day != null) {
        validIsoDate(year, endpoint.month, endpoint.day)
    } else {
        validIsoDate(year, endpoint.month, daysInSearchMonth(year, endpoint.month))
    }

internal fun daysInSearchMonth(
    year: Int,
    month: Int,
): Int =
    when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

private fun dateRangeLabel(
    start: String,
    end: String,
): String {
    fun display(value: String): String {
        val (year, month, day) = value.split('-').map(String::toInt)
        return formatDate(CalendarDate(year, month, day))
    }
    return if (start == end) display(start) else "${display(start)} – ${display(end)}"
}

internal fun searchDateMonthId(dateQuery: ActivitySearchDateQuery): String? {
    val start = dateQuery.startInclusive ?: return null
    val end = dateQuery.endInclusive ?: return null
    val monthId = start.take(7)
    val parts = monthId.split('-')
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val month = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val lastDay = daysInSearchMonth(year, month)
    return monthId.takeIf { start == "$monthId-01" && end == "$monthId-${lastDay.toString().padStart(2, '0')}" }
}

internal fun String.substringOrNull(
    startIndex: Int,
    endIndex: Int,
): String? = takeIf { length >= endIndex }?.substring(startIndex, endIndex)

private fun validIsoDate(
    year: Int,
    month: Int,
    day: Int,
): String? {
    if (year < 1 || month !in 1..12) return null
    val daysInMonth =
        when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    if (day !in 1..daysInMonth) return null
    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}
