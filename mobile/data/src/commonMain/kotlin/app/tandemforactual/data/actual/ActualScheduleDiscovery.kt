package app.tandemforactual.data.actual

import kotlin.math.abs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A recurring payment found in the history, offered as a new schedule the way Actual's "Find schedules" does. */
data class DiscoveredSchedule(
    val accountId: String,
    val payeeId: String,
    val amountMinor: Long,
    val exactAmount: Boolean,
    val dateJson: String,
    val exactDate: Boolean,
)

/** Recurring payments in the budget that have no schedule yet, as of [today]. */
fun discoverSchedules(
    db: ActualDatabase,
    today: String,
): List<DiscoveredSchedule> = ActualScheduleDiscovery(db, Day.parse(today)).find()

/**
 * `schedule/discover` (`find-schedules.ts`): for each open account, tries weekly, fortnightly and monthly patterns around
 * its latest transaction, keeps the best-ranked pattern per payee, then moves each start back while older payments
 * still match.
 */
internal class ActualScheduleDiscovery(
    private val db: ActualDatabase,
    private val today: Day,
) {
    private class Candidate(
        val rank: Double,
        val amount: Long,
        val account: String,
        val payee: String?,
        val config: JsonObject,
        val exactDate: Boolean,
        val exactAmount: Boolean,
    )

    private class Payment(
        val date: Day,
        val amount: Long,
        val payee: String?,
    )

    private class Occurrence(
        val date: Day,
        val payments: List<Payment>,
    )

    fun find(): List<DiscoveredSchedule> {
        val accounts = db.query("SELECT id FROM accounts WHERE tombstone = 0 AND (closed IS NULL OR closed = 0) ORDER BY sort_order, id")
        val candidates =
            accounts.flatMap { row ->
                val account = row["id"] as String
                val latest =
                    db.first(
                        "SELECT date FROM v_transactions WHERE account = ? AND parent_id IS NULL ORDER BY date DESC LIMIT 1",
                        account,
                    )?.get("date") as Number?
                latest?.let { patterns(Day.parse(ActualMonths.dateString(it.toLong())), account) }.orEmpty()
            }
        return candidates
            .filter { it.payee != null }
            .groupBy { it.payee }
            .values
            .map { group -> group.sortedByDescending { it.rank }.first() }
            .map { winner ->
                val config = startDate(winner)
                DiscoveredSchedule(
                    winner.account,
                    winner.payee!!,
                    winner.amount,
                    winner.exactAmount,
                    stringifyJson(config),
                    winner.exactDate,
                )
            }
    }

    private fun patterns(
        latest: Day,
        account: String,
    ): List<Candidate> {
        val weekday = WEEKDAYS[today.weekday - 1]
        val monthly = { start: Day -> if (start.dayOfMonth > 28) null else config("start" to start, "frequency" to "monthly") }
        val lastDay = { start: Day -> config("frequency" to "monthly", "patterns" to listOf(-1), "start" to start, day = true) }
        val firstThird = nthWeekdays(listOf(1, 3), weekday)
        val secondFourth = nthWeekdays(listOf(2, 4), weekday)
        return search(latest.plus(-28), 14, account) { config("frequency" to "weekly", "start" to it) } +
            search(latest.plus(-49), 14, account) { config("frequency" to "weekly", "interval" to 2, "start" to it) } +
            search(addMonths(latest, -4), 62, account, monthly) +
            search(addMonths(latest, -3), 1, account, lastDay) +
            search(addMonths(latest, -4), 1, account, lastDay) +
            search(latest.plus(-56), 14, account, firstThird) +
            search(addMonths(latest, -8), 14, account, secondFourth)
    }

    /** Actual looks for today's weekday here, not the start's. */
    private fun nthWeekdays(
        nths: List<Int>,
        weekday: String,
    ): (Day) -> JsonObject? = { start -> config("start" to start, "frequency" to "monthly", "patterns" to nths).withWeekday(weekday) }

    private fun search(
        baseStart: Day,
        days: Int,
        account: String,
        configFor: (Day) -> JsonObject?,
    ): List<Candidate> =
        (0 until days).flatMap { offset ->
            val config = configFor(baseStart.plus(offset.toLong())) ?: return@flatMap emptyList()
            val dates = ActualRecurrence.upcoming(RecurConfig.parse(config), Day.parse(config.value("start") as String), 3)
            match(dates.map { Occurrence(it, payments(it, account)) }, config, account)
        }

    /** `getTransactions`: unscheduled, non-transfer payments in [account] within two days of [date]. */
    private fun payments(
        date: Day,
        account: String,
    ): List<Payment> =
        db
            .query(
                "SELECT t.date, t.amount, t.payee FROM v_transactions t LEFT JOIN payees p ON p.id = t.payee " +
                    "WHERE t.account = ? AND t.schedule IS NULL AND t.is_child = 0 AND p.transfer_acct IS NULL " +
                    "AND t.date >= ? AND t.date <= ? ORDER BY t.date, t.sort_order, t.id",
                account,
                ActualMonths.dateInt(date.plus(-2).toString()),
                ActualMonths.dateInt(date.plus(2).toString()),
            ).map {
                val day = Day.parse(ActualMonths.dateString((it["date"] as Number).toLong()))
                Payment(day, (it["amount"] as Number).toLong(), it["payee"] as String?)
            }

    /** `matchSchedules`: each payment on the latest date that recurs, by payee and approximate amount, on every earlier one. */
    private fun match(
        all: List<Occurrence>,
        config: JsonObject,
        account: String,
    ): List<Candidate> {
        val reversed = all.reversed()
        val base = reversed.first()
        val earlier = reversed.drop(1)
        return base.payments.mapNotNull { payment ->
            val threshold = threshold(payment.amount)
            val found =
                earlier.map { occurrence ->
                    occurrence.payments
                        .firstOrNull { abs(it.amount - payment.amount) <= threshold }
                        ?.takeIf { it.payee == payment.payee }
                        ?.let { it to rank(occurrence.date, it.date) }
                }
            if (found.any { it == null }) return@mapNotNull null
            val rank = found.sumOf { it!!.second } + rank(base.date, payment.date)
            Candidate(
                rank = rank,
                amount = payment.amount,
                account = account,
                payee = payment.payee,
                config = config,
                exactDate = rank == all.size.toDouble(),
                exactAmount = found.all { it!!.first.amount == payment.amount },
            )
        }
    }

    /** `findStartDate`: moves the start back one interval at a time while a payment still matches its first date. */
    private fun startDate(candidate: Candidate): JsonObject {
        var config = candidate.config
        while (true) {
            val interval = (config["interval"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 1L
            val start = Day.parse(config.value("start") as String)
            val earlier = if (config.value("frequency") == "weekly") start.plus(-7 * interval) else addMonths(start, -interval)
            val moved = JsonObject(config + ("start" to JsonPrimitive(earlier.toString())))
            val first = ActualRecurrence.upcoming(RecurConfig.parse(moved), earlier, 1).firstOrNull() ?: return config
            if (first >= start || !matches(candidate, first)) return config
            config = moved
        }
    }

    private fun matches(
        candidate: Candidate,
        date: Day,
    ): Boolean {
        val spread = if (candidate.exactAmount) 0L else threshold(candidate.amount)
        val days = if (candidate.exactDate) 0L else 2L
        return db.first(
            "SELECT 1 AS found FROM v_transactions WHERE is_parent = 0 AND account = ? AND payee = ? " +
                "AND amount >= ? AND amount <= ? AND date >= ? AND date <= ?",
            candidate.account,
            candidate.payee,
            candidate.amount - spread,
            candidate.amount + spread,
            ActualMonths.dateInt(date.plus(-days).toString()),
            ActualMonths.dateInt(date.plus(days).toString()),
        ) != null
    }

    private fun threshold(amount: Long) = jsMathRound(abs(amount) * 0.075).toLong()

    private fun rank(
        a: Day,
        b: Day,
    ) = 1.0 / (abs(a.epoch - b.epoch) + 1)

    private fun config(
        vararg entries: Pair<String, Any>,
        day: Boolean = false,
    ): JsonObject =
        JsonObject(
            entries.associate { (key, value) ->
                key to
                    when (value) {
                        is Day -> JsonPrimitive(value.toString())
                        is Int -> JsonPrimitive(value)
                        is List<*> -> JsonArray(value.map { pattern(if (day) "day" else "", it as Int) })
                        else -> JsonPrimitive(value.toString())
                    }
            },
        )

    private fun pattern(
        type: String,
        value: Int,
    ) = JsonObject(linkedMapOf("type" to JsonPrimitive(type), "value" to JsonPrimitive(value)))

    private fun JsonObject.withWeekday(weekday: String): JsonObject {
        val patterns = (this["patterns"] as JsonArray).map { JsonObject((it as JsonObject) + ("type" to JsonPrimitive(weekday))) }
        return JsonObject(this + ("patterns" to JsonArray(patterns)))
    }

    private fun addMonths(
        day: Day,
        months: Long,
    ): Day {
        val index = day.year * 12 + (day.month - 1) + months
        val year = index.floorDiv(12L)
        val month = index.mod(12L) + 1
        return Day.of(year, month, minOf(day.dayOfMonth, daysInMonth(year, month)))
    }
}

private val WEEKDAYS = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
