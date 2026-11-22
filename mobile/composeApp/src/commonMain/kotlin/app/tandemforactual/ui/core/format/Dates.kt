package app.tandemforactual.ui.core.format

internal fun currentMonthId(): String = platformTodayIsoDate().take(7).takeIf { it.length == 7 } ?: "2026-05"

internal fun currentMonthLabel(): String {
    val parts = currentMonthId().split('-')
    val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
    val month = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: 5
    return formatMonth(year, month)
}

internal fun parseIsoDate(value: String): Triple<Int, Int, Int>? {
    val parts = value.split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    return Triple(year, month, day).takeIf { year > 0 && month in 1..12 && day in 1..daysInMonth(year, month) }
}

internal fun daysInMonth(
    year: Int,
    month: Int,
): Int =
    when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

internal fun transactionIsoDate(
    year: Int,
    month: Int,
    day: Int,
): String = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

internal fun displayTransactionDate(value: String): String =
    parseIsoDate(value)?.let { (year, month, day) -> formatDate(CalendarDate(year, month, day)) } ?: value

internal fun daysInMonth(monthId: String): Int? {
    val year = monthId.substringBefore('-').toIntOrNull() ?: return null
    return when (monthId.substringAfter('-', "").toIntOrNull()) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        else -> null
    }
}

internal fun reportMonthLabel(monthId: String): String = parseReportMonth(monthId)?.let { formatMonth(it.year, it.month) } ?: monthId

internal data class ReportMonth(
    val year: Int,
    val month: Int,
)

internal fun parseReportMonth(monthId: String): ReportMonth? {
    if (monthId.length != 7 || monthId[4] != '-') return null
    val year = monthId.take(4).toIntOrNull() ?: return null
    val month = monthId.takeLast(2).toIntOrNull() ?: return null
    return ReportMonth(year, month).takeIf { year in 1..9999 && month in 1..12 }
}

/** A month a number of months after the current one, written out, such as “March 2027”. */
internal fun monthFromNow(
    today: String,
    offset: Int,
): String {
    val index = today.take(4).toInt() * 12 + today.substring(5, 7).toInt() - 1 + offset
    return platformFormatMonth(index / 12, index % 12 + 1, abbreviated = false)
}

/** The month [offset] months after the month of [today], written out in full. */
internal fun monthLabel(
    today: String,
    offset: Int,
): String {
    val (y, m) = today.split('-').let { it[0].toInt() to it[1].toInt() }
    val index = y * 12 + (m - 1) + offset
    return platformFormatMonth(index / 12, index % 12 + 1, abbreviated = false)
}
