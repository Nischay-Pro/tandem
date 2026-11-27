package app.tandemforactual.data.planning

import app.tandemforactual.data.actual.Day
import app.tandemforactual.data.database.SearchableBudgetTransaction

/** Money spent in one category on one day. A refund is negative. */
data class SpendingEntry(
    val categoryId: String,
    val date: String,
    val amountMinor: Long,
)

/** What this month is compared with: last month alone, or the average of the months before it. */
enum class TrendBaseline(
    val months: Int,
) {
    LAST_MONTH(1),
    AVERAGE_3(3),
    AVERAGE_6(6),
}

enum class TrendDirection { UP, DOWN, FLAT }

data class CategoryTrend(
    val categoryId: String,
    val currentMinor: Long,
    val baselineMinor: Long,
    val direction: TrendDirection,
    /** The change as a share of the baseline, or null when there was nothing to compare with. */
    val changeBasisPoints: Int?,
) {
    val changeMinor: Long get() = currentMinor - baselineMinor
}

data class TrendReport(
    val categories: List<CategoryTrend>,
    val total: CategoryTrend,
    /** How many earlier months the baseline actually covers; fewer than asked when the history is shorter. */
    val baselineMonths: Int,
    /** The day of the month both sides are counted up to while the month isn't over, or null for whole months. */
    val throughDay: Int?,
)

/** How a category's month-end spending was estimated. */
enum class ForecastBasis {
    /** Recent months show how much usually lands after today, scaled to this month's pace. */
    HISTORY,

    /** No history yet, so spending so far is stretched over the whole month. */
    PACE,

    /** The month is over, so the spending is final. */
    FINAL,
}

enum class ForecastStatus { ON_TRACK, AT_RISK, OVER }

data class CategoryForecast(
    val categoryId: String,
    val spentMinor: Long,
    val projectedMinor: Long,
    val limitMinor: Long?,
    val basis: ForecastBasis,
    val historyMonths: Int,
    val status: ForecastStatus,
    /** The estimated day the budget runs out, when it's on pace to run out before the month ends. */
    val runOutDate: String?,
)

data class SpendingForecast(
    val monthId: String,
    val dayOfMonth: Int,
    val daysInMonth: Int,
    val categories: List<CategoryForecast>,
    val spentMinor: Long,
    val projectedMinor: Long,
    val limitMinor: Long,
    /** The projection for categories with a budget only, to set against [limitMinor]. */
    val budgetedProjectedMinor: Long,
)

/** Month-over-month spending trends and month-end spending estimates, worked out on the device from recorded transactions. */
object SpendingInsights {
    private const val FLAT_BASIS_POINTS = 500
    private const val MIN_PACE_PERCENT = 50
    private const val MAX_PACE_PERCENT = 200

    /**
     * Spending from the budget's transactions, matching the reports: transfers, split parents and income categories are
     * left out, and an uncategorized deposit is income rather than a refund.
     */
    fun entries(
        records: List<SearchableBudgetTransaction>,
        incomeCategoryIds: Set<String>,
    ): List<SpendingEntry> =
        records.mapNotNull { record ->
            val date = record.recordDate?.takeIf { it.length >= 10 } ?: return@mapNotNull null
            val amount = record.amountMinor ?: return@mapNotNull null
            val category = record.categoryId
            when {
                record.isParent || record.transfer == true -> null
                category == null -> if (amount < 0) SpendingEntry("", date.take(10), -amount) else null
                category in incomeCategoryIds -> null
                else -> SpendingEntry(category, date.take(10), -amount)
            }
        }

    /**
     * Spending per category in [monthId] against [baseline], biggest change first. While [today] is inside [monthId], both
     * sides only count spending up to today's day of the month.
     */
    fun trends(
        entries: List<SpendingEntry>,
        monthId: String,
        baseline: TrendBaseline,
        today: String,
    ): TrendReport {
        val throughDay = today.takeIf { it.take(7) == monthId }?.substring(8, 10)?.toInt()
        val byMonth =
            entries
                .filter { throughDay == null || it.date.substring(8, 10).toInt() <= throughDay }
                .groupBy { it.date.take(7) }
        val earlier = historyMonths(entries, monthId, baseline.months)
        val current = totals(byMonth[monthId].orEmpty())
        val history = earlier.map { totals(byMonth[it].orEmpty()) }
        val ids = (current.keys + history.flatMap { it.keys }).toSet().sorted()
        val trends =
            ids
                .map { id -> trend(id, current[id] ?: 0, average(history.map { it[id] ?: 0 })) }
                .filterNot { it.currentMinor == 0L && it.baselineMinor == 0L }
                .sortedWith(compareByDescending<CategoryTrend> { kotlin.math.abs(it.changeMinor) }.thenBy { it.categoryId })
        val total = trend("", current.values.sum(), average(history.map { it.values.sum() }))
        return TrendReport(trends, total, earlier.size, throughDay)
    }

    /**
     * Where each category's spending lands by the end of [monthId], seen from [today]. [limits] is what each category had
     * to spend this month, carryover included; a category without one is projected but never flagged.
     */
    fun forecast(
        entries: List<SpendingEntry>,
        monthId: String,
        today: String,
        limits: Map<String, Long>,
        historyMonths: Int = 6,
    ): SpendingForecast {
        val length = daysInMonth(monthId)
        val day =
            when {
                today.take(7) > monthId -> length
                today.take(7) < monthId -> 0
                else -> today.substring(8, 10).toInt()
            }
        val byMonth = entries.groupBy { it.date.take(7) }
        val earlierMonths = historyMonths(entries, monthId, historyMonths)
        val earlier = earlierMonths.map { byMonth[it].orEmpty() }
        val firstMonths =
            entries
                .filter { it.date.take(7) < monthId }
                .groupBy { it.categoryId }
                .mapValues { (_, list) -> list.minOf { it.date.take(7) } }
        val current = byMonth[monthId].orEmpty().filter { it.date.substring(8, 10).toInt() <= day }
        val spent = totals(current)
        val ids = (spent.keys + limits.keys + earlier.flatMap { month -> month.map { it.categoryId } }).toSet().sorted()
        val categories =
            ids
                .map { id ->
                    val first = firstMonths[id]
                    val history =
                        earlierMonths.indices
                            .filter { first != null && earlierMonths[it] >= first }
                            .map { index -> earlier[index].filter { it.categoryId == id } }
                    categoryForecast(id, spent[id] ?: 0, limits[id], history, monthId, day, length)
                }.filterNot { it.spentMinor == 0L && it.projectedMinor == 0L && it.limitMinor == null }
        return SpendingForecast(
            monthId,
            day,
            length,
            categories,
            categories.sumOf { it.spentMinor },
            categories.sumOf { it.projectedMinor },
            categories.sumOf { (it.limitMinor ?: 0).coerceAtLeast(0) },
            categories.filter { it.limitMinor != null }.sumOf { it.projectedMinor },
        )
    }

    private fun categoryForecast(
        id: String,
        spent: Long,
        limit: Long?,
        history: List<List<SpendingEntry>>,
        monthId: String,
        day: Int,
        length: Int,
    ): CategoryForecast {
        val remainingDays = length - day
        val (basis, projected) =
            when {
                remainingDays == 0 -> ForecastBasis.FINAL to spent
                history.isNotEmpty() -> {
                    val toDate =
                        average(
                            history.map { month ->
                                month.filter { it.date.substring(8, 10).toInt() <= day }.sumOf { it.amountMinor }
                            },
                        )
                    val total = average(history.map { month -> month.sumOf { it.amountMinor } })
                    val after = (total - toDate).coerceAtLeast(0)
                    val projected =
                        if (toDate <= 0) {
                            maxOf(spent, total)
                        } else {
                            val pace = (spent * 100 / toDate).coerceIn(MIN_PACE_PERCENT.toLong(), MAX_PACE_PERCENT.toLong())
                            spent + after * pace / 100
                        }
                    ForecastBasis.HISTORY to projected.coerceAtLeast(spent)
                }
                day == 0 -> ForecastBasis.PACE to spent
                else -> ForecastBasis.PACE to spent + (spent.coerceAtLeast(0) * remainingDays + day - 1) / day
            }
        val status =
            when {
                limit == null -> ForecastStatus.ON_TRACK
                spent > limit -> ForecastStatus.OVER
                projected > limit -> ForecastStatus.AT_RISK
                else -> ForecastStatus.ON_TRACK
            }
        val runOut =
            if (status == ForecastStatus.AT_RISK && limit != null) {
                val perDay = (projected - spent).toDouble() / remainingDays
                val days =
                    kotlin.math
                        .ceil((limit - spent) / perDay)
                        .toLong()
                        .coerceIn(1, remainingDays.toLong())
                Day.parse("$monthId-01").plus(day - 1L + days).toString()
            } else {
                null
            }
        return CategoryForecast(id, spent, projected, limit, basis, history.size, status, runOut)
    }

    private fun trend(
        id: String,
        current: Long,
        baseline: Long,
    ): CategoryTrend {
        val change = current - baseline
        val share = if (baseline > 0) (change * 10_000 / baseline).toInt() else null
        val direction =
            when {
                change == 0L || share != null && kotlin.math.abs(share) < FLAT_BASIS_POINTS -> TrendDirection.FLAT
                change > 0 -> TrendDirection.UP
                else -> TrendDirection.DOWN
            }
        return CategoryTrend(id, current, baseline, direction, share)
    }

    /** Up to [count] months before [monthId], newest first, never earlier than the first month with any spending. */
    private fun historyMonths(
        entries: List<SpendingEntry>,
        monthId: String,
        count: Int,
    ): List<String> {
        val first = entries.minOfOrNull { it.date.take(7) } ?: return emptyList()
        return (1..count).map { shiftMonth(monthId, -it) }.filter { it >= first }
    }

    private fun totals(entries: List<SpendingEntry>): Map<String, Long> =
        entries.groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }

    private fun average(values: List<Long>): Long = if (values.isEmpty()) 0 else values.sum() / values.size

    /** The month [offset] months from [monthId], as yyyy-MM. */
    fun shiftMonth(
        monthId: String,
        offset: Int,
    ): String {
        val index = monthId.take(4).toInt() * 12 + monthId.substring(5, 7).toInt() - 1 + offset
        return "${(index / 12).toString().padStart(4, '0')}-${(index % 12 + 1).toString().padStart(2, '0')}"
    }

    fun daysInMonth(monthId: String): Int {
        val start = Day.parse("$monthId-01")
        val next = Day.parse("${shiftMonth(monthId, 1)}-01")
        return (next.epoch - start.epoch).toInt()
    }
}
