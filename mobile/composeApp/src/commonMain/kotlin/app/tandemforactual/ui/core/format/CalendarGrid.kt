package app.tandemforactual.ui.core.format

/** Blank cells before the 1st of [month], so weeks start on the locale's first weekday. */
internal fun leadingBlankDays(
    year: Int,
    month: Int,
): Int = (platformWeekday(year, month, 1) - platformFirstWeekday() + 7) % 7

/** Short weekday names in the locale's week order. */
internal fun weekdayHeaders(): List<String> {
    val start = 1 + (platformFirstWeekday() - platformWeekday(2026, 5, 1) + 7) % 7
    return (0 until 7).map { platformWeekdayName(2026, 5, start + it) }
}
