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
)

/** Month-over-month spending trends, worked out on the device from recorded transactions. */
object SpendingInsights {
    private const val FLAT_BASIS_POINTS = 500

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

    /** Spending per category in [monthId] against [baseline], biggest change first. */
    fun trends(
        entries: List<SpendingEntry>,
        monthId: String,
        baseline: TrendBaseline,
    ): TrendReport {
        val byMonth = entries.groupBy { it.date.take(7) }
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
        return TrendReport(trends, total, earlier.size)
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
