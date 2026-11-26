package app.tandemforactual.data.actual

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** A calendar day counted from 1970-01-01. */
internal data class Day(
    val epoch: Long,
) : Comparable<Day> {
    val year get() = civilFromDays(epoch).first
    val month get() = civilFromDays(epoch).second
    val dayOfMonth get() = civilFromDays(epoch).third

    /** 1 = Monday … 7 = Sunday. */
    val weekday get() = ((epoch + 3).mod(7L) + 1).toInt()

    fun plus(days: Long) = Day(epoch + days)

    override fun compareTo(other: Day) = epoch.compareTo(other.epoch)

    override fun toString() = ActualMonths.dateString(year * 10000 + month * 100 + dayOfMonth)

    companion object {
        fun of(
            year: Long,
            month: Long,
            day: Long,
        ) = Day(daysFromCivil(year, month, day))

        fun parse(iso: String): Day = of(iso.substring(0, 4).toLong(), iso.substring(5, 7).toLong(), iso.substring(8, 10).toLong())
    }
}

/** Actual's recurring date config (`RecurConfig`), as stored in a schedule's date condition. */
internal data class RecurConfig(
    val frequency: String,
    val start: Day,
    val interval: Int,
    val patterns: List<Pair<String, Int>>,
    val skipWeekend: Boolean,
    val weekendSolveMode: String,
    val count: Int?,
    val end: Day?,
) {
    companion object {
        fun parse(json: JsonObject): RecurConfig {
            fun text(key: String) = (json[key] as? JsonPrimitive)?.contentOrNull
            val endMode = text("endMode")
            return RecurConfig(
                frequency = text("frequency") ?: error("frequency is required"),
                start = Day.parse(text("start") ?: error("start is required")),
                interval = (json["interval"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 } ?: 1,
                patterns =
                    (json["patterns"] as? JsonArray).orEmpty().map {
                        val pattern = it as JsonObject
                        (pattern["type"] as JsonPrimitive).content to (pattern["value"] as JsonPrimitive).content.toInt()
                    },
                skipWeekend = (json["skipWeekend"] as? JsonPrimitive)?.contentOrNull == "true",
                weekendSolveMode = text("weekendSolveMode") ?: "after",
                count = if (endMode == "after_n_occurrences") (json["endOccurrences"] as? JsonPrimitive)?.intOrNull else null,
                end = if (endMode == "on_date") text("endDate")?.let(Day::parse) else null,
            )
        }
    }
}

/**
 * Actual's schedule dates (`shared/schedules.ts` on `@rschedule/core`): each recurrence rule generates RFC 5545-style
 * occurrences, a monthly config with both day and weekday patterns is the union of two rules, and occurrences on a
 * weekend move to the nearest weekday when the config asks for it.
 */
internal object ActualRecurrence {
    private const val MAX_EMPTY_PERIODS = 5_000
    private val weekdays =
        mapOf(
            "monday" to 1,
            "tuesday" to 2,
            "wednesday" to 3,
            "thursday" to 4,
            "friday" to 5,
            "saturday" to 6,
            "sunday" to 7,
        )

    private data class Rule(
        val config: RecurConfig,
        val days: List<Int> = emptyList(),
        val weekdays: List<Pair<Int, Int>> = emptyList(),
    )

    private fun rules(config: RecurConfig): List<Rule> {
        if (config.frequency !in setOf("daily", "weekly", "monthly", "yearly")) error("Invalid recurring date config")
        if (config.frequency != "monthly" || config.patterns.isEmpty()) return listOf(Rule(config))
        val days = config.patterns.filter { it.first == "day" }.map { it.second }
        val names =
            config.patterns.filter { it.first != "day" }.map { (type, nth) ->
                val code = type.take(2).lowercase()
                (weekdays.entries.firstOrNull { it.key.take(2) == code }?.value ?: error("Invalid recurring date config")) to nth
            }
        return listOfNotNull(
            days
                .takeIf {
                    it.isNotEmpty()
                }?.let { Rule(config, days = it) },
            names.takeIf { it.isNotEmpty() }?.let { Rule(config, weekdays = it) },
        )
    }

    private fun lastDay(
        year: Long,
        month: Long,
    ) = daysInMonth(year, month)

    /** The period [day] falls in, so a search can start there instead of at the schedule's first period. */
    private fun periodOf(
        config: RecurConfig,
        day: Day,
    ): Long {
        val start = config.start
        val units =
            when (config.frequency) {
                "daily" -> day.epoch - start.epoch
                "weekly" -> (day.epoch - start.epoch).floorDiv(7L)
                "monthly" -> (day.year * 12 + day.month) - (start.year * 12 + start.month)
                else -> day.year - start.year
            }
        return maxOf(0L, units.floorDiv(config.interval.toLong()))
    }

    private fun candidates(
        rule: Rule,
        period: Long,
    ): List<Day> {
        val config = rule.config
        val start = config.start
        val step = period * config.interval
        return when (config.frequency) {
            "daily" -> listOf(start.plus(step))
            "weekly" -> listOf(start.plus(step * 7))
            "yearly" -> {
                val year = start.year + step
                if (start.dayOfMonth <= lastDay(year, start.month)) listOf(Day.of(year, start.month, start.dayOfMonth)) else emptyList()
            }
            else -> {
                val index = start.year * 12 + (start.month - 1) + step
                val year = index.floorDiv(12L)
                val month = index.mod(12L) + 1
                val last = lastDay(year, month)
                when {
                    rule.days.isNotEmpty() ->
                        rule.days.mapNotNull { value ->
                            val day = if (value > 0) value.toLong() else last + value + 1
                            if (day in 1..last) Day.of(year, month, day) else null
                        }
                    rule.weekdays.isNotEmpty() ->
                        rule.weekdays.mapNotNull { (weekday, nth) ->
                            val first = Day.of(year, month, 1)
                            val matching = (0 until last).map { first.plus(it) }.filter { it.weekday == weekday }
                            when {
                                nth > 0 -> matching.getOrNull(nth - 1)
                                nth < 0 -> matching.getOrNull(matching.size + nth)
                                else -> null
                            }
                        }
                    start.dayOfMonth <= last -> listOf(Day.of(year, month, start.dayOfMonth))
                    else -> emptyList()
                }.distinct().sorted()
            }
        }
    }

    /** Occurrences of one rule on or after [from] in order, honouring its count and end date. */
    private fun occurrences(
        rule: Rule,
        from: Day,
    ): Sequence<Day> =
        sequence {
            val config = rule.config
            var produced = 0
            var period = if (config.count == null) periodOf(config, from) else 0L
            var empty = 0
            while (empty < MAX_EMPTY_PERIODS) {
                val days = candidates(rule, period++).filter { it >= config.start }
                empty = if (days.isEmpty()) empty + 1 else 0
                for (day in days) {
                    if (config.end != null && day > config.end) return@sequence
                    if (day >= from) yield(day)
                    produced++
                    if (config.count != null && produced >= config.count) return@sequence
                }
            }
        }

    /** The final occurrence of a rule that ends, searching back from its end date. */
    private fun last(rule: Rule): Day? {
        val config = rule.config
        if (config.count != null) return occurrences(rule, config.start).lastOrNull()
        val end = config.end ?: return null
        return generateSequence(periodOf(config, end)) { it - 1 }
            .takeWhile { it >= 0 }
            .firstNotNullOfOrNull { period -> candidates(rule, period).filter { it >= config.start && it <= end }.maxOrNull() }
    }

    private fun skipWeekend(
        day: Day,
        mode: String,
    ): Day =
        when (day.weekday) {
            6 -> if (mode == "after") day.plus(2) else day.plus(-1)
            7 -> if (mode == "after") day.plus(1) else day.plus(-2)
            else -> day
        }

    /** `occursBetween`, both ends included, before any weekend adjustment. */
    fun occursBetween(
        config: RecurConfig,
        from: Day,
        to: Day,
    ): Boolean = rules(config).any { rule -> occurrences(rule, from).firstOrNull()?.let { it <= to } == true }

    /** `getNextDate`: the first occurrence on or after [from], or the final occurrence of a finished schedule. */
    fun nextDate(
        config: RecurConfig,
        from: Day,
        noSkipWeekend: Boolean = false,
    ): Day? {
        val all = rules(config)
        val date =
            all.mapNotNull { rule -> occurrences(rule, from).firstOrNull() }.minOrNull()
                ?: all.mapNotNull(::last).maxOrNull()
                ?: return null
        return if (config.skipWeekend && !noSkipWeekend) skipWeekend(date, config.weekendSolveMode) else date
    }

    /** `getNextDateAfter`: the next weekday-adjusted date strictly after [after], looking a few occurrences ahead. */
    fun nextDateAfter(
        config: RecurConfig,
        after: Day,
    ): Day? {
        var start = after.plus(-1)
        return (0 until 7).firstNotNullOfOrNull {
            val occurrence = nextDate(config, start, noSkipWeekend = true)
            if (occurrence == null || occurrence < start) return null
            val next = nextDate(config, start)
            if (next != null && next > after) {
                next
            } else {
                start = occurrence.plus(1)
                null
            }
        }
    }

    /** `getNextDateFrom` in `schedules/app.ts`: also catches an earlier occurrence moved onto [today] by weekend skipping. */
    fun nextDateFrom(
        config: RecurConfig,
        today: Day,
    ): Day? {
        val next = nextDate(config, today)
        val moved = nextDateAfter(config, today.plus(-1))
        return if (moved != null && (next == null || moved < next)) moved else next
    }

    /** A schedule date condition's value: a fixed `YYYY-MM-DD` date or a recurring config; anything else has no next date. */
    fun nextDateFrom(
        value: JsonElement?,
        today: Day,
    ): Day? =
        when {
            value is JsonPrimitive && value.isString && value.content.length == 10 -> Day.parse(value.content)
            value is JsonObject && value["frequency"] != null -> nextDateFrom(RecurConfig.parse(value), today)
            else -> null
        }

    fun nextDateAfter(
        value: JsonElement?,
        after: Day,
    ): Day? =
        when {
            value is JsonPrimitive && value.isString && value.content.length == 10 -> Day.parse(value.content)
            value is JsonObject && value["frequency"] != null -> nextDateAfter(RecurConfig.parse(value), after)
            else -> null
        }
}
