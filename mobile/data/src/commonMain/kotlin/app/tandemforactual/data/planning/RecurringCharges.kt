package app.tandemforactual.data.planning

import app.tandemforactual.data.actual.Day
import app.tandemforactual.data.database.SearchableBudgetTransaction

/** How often a recurring charge comes, with the gap between charges it allows and how many fit in a year. */
enum class Cadence(
    val days: IntRange,
    val perYear: Int,
) {
    WEEKLY(6..8, 52),
    MONTHLY(26..35, 12),
    QUARTERLY(84..98, 4),
    YEARLY(350..380, 1),
}

/** A payee charged on a steady rhythm for a similar amount, like a streaming service or a gym. */
data class RecurringCharge(
    val payeeId: String,
    val cadence: Cadence,
    /** The latest charge, as a positive amount. */
    val amountMinor: Long,
    val lastDate: String,
    val nextDate: String,
    val charges: Int,
    /** The latest charge is more than the one before it. */
    val priceRose: Boolean,
    /** No charge has come for well over one cycle, so it may have been cancelled. */
    val stopped: Boolean,
) {
    /** What it costs in an average month. */
    val monthlyMinor: Long get() = amountMinor * cadence.perYear / 12
}

/** Finds subscriptions and other recurring charges in a budget's spending. */
object RecurringCharges {
    private const val MIN_CHARGES = 3
    private const val AMOUNT_SPREAD_PERCENT = 25

    fun find(
        records: List<SearchableBudgetTransaction>,
        today: String,
    ): List<RecurringCharge> =
        records
            .filter { (it.amountMinor ?: 0) < 0 && it.transfer != true && !it.isParent && it.payeeId != null && it.recordDate != null }
            .groupBy { it.payeeId!! }
            .mapNotNull { (payee, charges) -> charge(payee, charges.sortedBy { it.recordDate }, today) }
            .sortedWith(compareBy<RecurringCharge> { it.stopped }.thenByDescending { it.monthlyMinor })

    private fun charge(
        payeeId: String,
        charges: List<SearchableBudgetTransaction>,
        today: String,
    ): RecurringCharge? {
        if (charges.size < MIN_CHARGES) return null
        val days = charges.map { Day.parse(it.recordDate!!.take(10)) }
        val gaps = days.zipWithNext { a, b -> (b.epoch - a.epoch).toInt() }
        val typical = gaps.sorted()[gaps.size / 2]
        val cadence = Cadence.entries.firstOrNull { typical in it.days } ?: return null
        if (gaps.count { it in cadence.days } * 3 < gaps.size * 2) return null
        val amounts = charges.map { -(it.amountMinor ?: 0) }
        val middle = amounts.sorted()[amounts.size / 2]
        if (amounts.any { kotlin.math.abs(it - middle) * 100 > middle * AMOUNT_SPREAD_PERCENT }) return null
        val last = days.last()
        val next =
            when (cadence) {
                Cadence.WEEKLY -> last.plus(7).toString()
                Cadence.MONTHLY -> sameDayLater(last.toString(), 1)
                Cadence.QUARTERLY -> sameDayLater(last.toString(), 3)
                Cadence.YEARLY -> sameDayLater(last.toString(), 12)
            }
        return RecurringCharge(
            payeeId = payeeId,
            cadence = cadence,
            amountMinor = amounts.last(),
            lastDate = last.toString(),
            nextDate = next,
            charges = charges.size,
            priceRose = amounts.last() > amounts[amounts.size - 2],
            stopped = Day.parse(today).epoch - last.epoch > typical * 3 / 2,
        )
    }

    /** The same day of the month [months] later, or that month's last day when it's shorter. */
    private fun sameDayLater(
        date: String,
        months: Int,
    ): String {
        val month = SpendingInsights.shiftMonth(date.take(7), months)
        val day = minOf(date.drop(8).toInt(), SpendingInsights.daysInMonth(month))
        return "$month-${day.toString().padStart(2, '0')}"
    }
}
