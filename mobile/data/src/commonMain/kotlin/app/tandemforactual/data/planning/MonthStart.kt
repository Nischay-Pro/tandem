package app.tandemforactual.data.planning

import app.tandemforactual.data.actual.Day
import app.tandemforactual.data.database.SearchableBudgetTransaction

/**
 * The day each month starts on for reports and insights, such as payday. A period is named after the month it starts
 * in, so with day 25 "2026-11" runs from 25 November to 24 December. Day 1 without moving is the calendar month.
 */
data class MonthStart(
    val day: Int,
    val previousWorkingDay: Boolean,
) {
    init {
        require(day in 1..MAX_DAY) { "A month can start on day 1 to $MAX_DAY." }
    }

    val isCalendar: Boolean get() = day == 1 && !previousWorkingDay

    /** The first day of [monthId]'s period, moved back to Friday from a weekend when [previousWorkingDay] is set. */
    fun startOf(monthId: String): String {
        val start = Day.parse("$monthId-${day.toString().padStart(2, '0')}")
        val back =
            when {
                !previousWorkingDay -> 0L
                start.weekday == SATURDAY -> 1L
                start.weekday == SUNDAY -> 2L
                else -> 0L
            }
        return start.plus(-back).toString()
    }

    /** The month whose period holds [dateIso]. */
    fun monthOf(dateIso: String): String {
        val month = dateIso.take(7)
        val next = SpendingInsights.shiftMonth(month, 1)
        return when {
            dateIso >= startOf(next) -> next
            dateIso >= startOf(month) -> month
            else -> SpendingInsights.shiftMonth(month, -1)
        }
    }

    /**
     * [dateIso] moved into its period's month at the same distance from the period's start, so calendar-month sums and
     * day-of-month pacing read periods instead. Days past the month's length stay on its last day.
     */
    fun periodDate(dateIso: String): String {
        if (isCalendar) return dateIso
        val month = monthOf(dateIso)
        val offset = Day.parse(dateIso).epoch - Day.parse(startOf(month)).epoch + 1
        val dayOfMonth = minOf(offset, SpendingInsights.daysInMonth(month).toLong())
        return "$month-${dayOfMonth.toString().padStart(2, '0')}"
    }

    /** [transactions] with their dates moved into periods; dates too short to read stay as they are. */
    fun inPeriods(transactions: List<SearchableBudgetTransaction>): List<SearchableBudgetTransaction> =
        if (isCalendar) {
            transactions
        } else {
            transactions.map { tx ->
                tx.recordDate?.takeIf { it.length >= 10 }?.let { tx.copy(recordDate = periodDate(it.take(10))) } ?: tx
            }
        }

    companion object {
        const val MAX_DAY = 28
        private const val SATURDAY = 6
        private const val SUNDAY = 7
    }
}
