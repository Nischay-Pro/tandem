package app.tandemforactual.ui.core.state

import app.tandemforactual.data.actual.upcomingScheduleDates
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.format.CalendarDate
import app.tandemforactual.ui.core.format.parseIsoDate

/** Actual's default "upcoming length": scheduled transactions due within a week show in the register. */
internal const val UPCOMING_SCHEDULE_DAYS = 7

internal enum class OccurrenceStatus { MISSED, DUE, UPCOMING }

/** One date a schedule falls on, as the register shows it before the transaction exists. */
internal data class ScheduledOccurrence(
    val schedule: LiveScheduleSummary,
    val date: String,
    val status: OccurrenceStatus,
)

/**
 * The account's unpaid scheduled transactions: a missed or due next date, then every date up to [days] ahead. A schedule
 * already paid by a linked transaction on or after its next date (two days early for an approximate date) is left out,
 * as Actual hides paid schedules.
 */
internal fun scheduledOccurrences(
    schedules: List<LiveScheduleSummary>,
    transactionRecords: List<BudgetRecordEntity>,
    accountId: String,
    today: String,
    days: Int = UPCOMING_SCHEDULE_DAYS,
): List<ScheduledOccurrence> {
    val todayNumber = dayNumber(today) ?: return emptyList()
    val paidFrom = linkedTransactionDates(transactionRecords)
    return schedules
        .filter { it.accountId == accountId }
        .flatMap { schedule ->
            val next = dayNumber(schedule.nextDate) ?: return@flatMap emptyList()
            val paid = paidFrom[schedule.id].orEmpty().any { (dayNumber(it) ?: Long.MIN_VALUE) >= next - 2 }
            if (paid) return@flatMap emptyList()
            val later =
                upcomingScheduleDates(schedule.dateValueJson, maxOf(schedule.nextDate, today), days + 1)
                    .mapNotNull { date -> dayNumber(date)?.takeIf { it in (next + 1)..(todayNumber + days) }?.let { date to it } }
            val first =
                when {
                    next < todayNumber -> OccurrenceStatus.MISSED
                    next == todayNumber -> OccurrenceStatus.DUE
                    next <= todayNumber + days -> OccurrenceStatus.UPCOMING
                    else -> null
                }
            listOfNotNull(first?.let { ScheduledOccurrence(schedule, schedule.nextDate, it) }) +
                later.map { (date, number) ->
                    val status = if (number == todayNumber) OccurrenceStatus.DUE else OccurrenceStatus.UPCOMING
                    ScheduledOccurrence(schedule, date, status)
                }
        }.sortedWith(compareBy({ it.date }, { it.schedule.name.lowercase() }))
}

private fun dayNumber(iso: String): Long? =
    parseIsoDate(iso)?.let { (year, month, day) -> calendarDayNumber(CalendarDate(year, month, day)) }

private fun linkedTransactionDates(records: List<BudgetRecordEntity>): Map<String, List<String>> =
    records
        .mapNotNull { record ->
            if (record.localDeleted || record.remoteDeleted) return@mapNotNull null
            val body = record.recordBody() ?: return@mapNotNull null
            val schedule = body.stringValue("schedule") ?: return@mapNotNull null
            body.stringValue("date")?.let { schedule to it }
        }.groupBy({ it.first }, { it.second })
