package app.tandemforactual.ui.feature.budget.grid

import app.tandemforactual.ui.feature.budget.previousMonthId
import kotlin.math.roundToLong

/** Ways to fill a block of budget cells at once; [months] is how far back an average looks. */
internal enum class BulkFill(
    val label: String,
    val months: Int,
) {
    SET("Amount", 0),
    PERCENT("Change by %", 0),
    LAST_YEAR("Last year", 0),
    AVERAGE_3("3-month avg", 3),
    AVERAGE_6("6-month avg", 6),
    AVERAGE_12("12-month avg", 12),
}

/** What a category budgeted and spent in one month, spent as a positive number. */
internal data class MonthFigures(
    val budgeted: Long,
    val spent: Long,
)

internal fun monthsBefore(
    monthId: String,
    count: Int,
): String? = (1..count).fold<Int, String?>(monthId) { month, _ -> month?.let(::previousMonthId) }

/**
 * A cell's amount after [fill], moved by [percent]: a set amount is used as typed, a change starts from what's budgeted,
 * last year from the same month a year back, and an average from what was spent in the months before. Null when there's
 * nothing to base it on.
 */
internal fun bulkAmount(
    fill: BulkFill,
    key: GridKey,
    current: Long,
    amount: Long?,
    percent: Long,
    history: (GridKey) -> MonthFigures?,
): Long? {
    fun figures(back: Int) = monthsBefore(key.monthId, back)?.let { history(GridKey(key.categoryId, it)) }
    val base =
        when (fill) {
            BulkFill.SET -> return amount?.coerceAtLeast(0)
            BulkFill.PERCENT -> current
            BulkFill.LAST_YEAR -> figures(MONTHS_IN_YEAR)?.budgeted
            else -> (1..fill.months).mapNotNull { figures(it)?.spent }.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
        } ?: return null
    return (base * (PERCENT_WHOLE + percent) / PERCENT_WHOLE.toDouble()).roundToLong().coerceAtLeast(0)
}

/** Every month a fill over [targets] may look back to. */
internal fun bulkHistoryMonths(targets: Collection<String>): Set<String> =
    targets.flatMap { target -> (1..MONTHS_IN_YEAR).mapNotNull { monthsBefore(target, it) } }.toSet()

private const val MONTHS_IN_YEAR = 12
private const val PERCENT_WHOLE = 100L
