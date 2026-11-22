package app.tandemforactual.data.planning

import app.tandemforactual.data.actual.ActualRecurrence
import app.tandemforactual.data.actual.Day
import kotlinx.serialization.json.Json

/** The balance at the end of each day from today, and the lowest point it reaches. */
data class CashFlowProjection(
    val daily: List<Long>,
    val lowestMinor: Long,
    val lowestDate: String,
)

object CashFlow {
    /** Every date a schedule falls on from [from] to [to], both included, using Actual's recurrence rules. */
    fun scheduleDates(
        dateValueJson: String,
        from: String,
        to: String,
    ): List<String> {
        val value = runCatching { Json.parseToJsonElement(dateValueJson) }.getOrNull()
        val end = Day.parse(to)
        val dates = mutableListOf<Day>()
        var next = ActualRecurrence.nextDateFrom(value, Day.parse(from))
        while (next != null && next <= end && dates.size < MAX_OCCURRENCES) {
            if (dates.lastOrNull() == next) break
            dates += next
            next = ActualRecurrence.nextDateAfter(value, next)
        }
        return dates.map { it.toString() }
    }

    /** Starts at [startMinor] today and applies each dated amount in [events] on its day, for [days] days. */
    fun project(
        startMinor: Long,
        today: String,
        days: Int,
        events: List<Pair<String, Long>>,
    ): CashFlowProjection {
        val start = Day.parse(today)
        val byDay = events.groupBy({ Day.parse(it.first).epoch - start.epoch }, { it.second })
        var balance = startMinor
        var lowest = startMinor
        var lowestDay = 0L
        val daily =
            (0L..days.toLong()).map { offset ->
                balance += byDay[offset]?.sum() ?: 0
                if (balance < lowest) {
                    lowest = balance
                    lowestDay = offset
                }
                balance
            }
        return CashFlowProjection(daily, lowest, start.plus(lowestDay).toString())
    }

    private const val MAX_OCCURRENCES = 400
}
