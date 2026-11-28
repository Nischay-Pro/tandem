package app.tandemforactual.ui.feature.activity

import app.tandemforactual.ui.core.format.formatMonth
import app.tandemforactual.ui.core.search.ActivitySearchField
import app.tandemforactual.ui.core.search.ActivitySearchFilter
import app.tandemforactual.ui.core.search.ActivitySearchRecord
import app.tandemforactual.ui.core.search.ActivitySortOrder
import app.tandemforactual.ui.core.search.daysInSearchMonth
import app.tandemforactual.ui.core.search.isActivitySearchMonthId
import app.tandemforactual.ui.core.state.TransactionItem

internal fun recentActivitySearchMonthFilters(records: List<ActivitySearchRecord>): List<ActivitySearchFilter> =
    records
        .asSequence()
        .map { it.dateIso.take(7) }
        .filter(::isActivitySearchMonthId)
        .distinct()
        .sortedDescending()
        .take(3)
        .map { monthId ->
            val year = monthId.substringBefore('-').toInt()
            val month = monthId.substringAfter('-').toInt()
            ActivitySearchFilter(ActivitySearchField.DATE_MONTH, monthId, "Date: ${formatMonth(year, month)}")
        }.toList()

internal fun toggleActivitySearchFilter(
    filters: List<ActivitySearchFilter>,
    selected: ActivitySearchFilter,
): List<ActivitySearchFilter> {
    val existing = filters.firstOrNull { it.field == selected.field && it.value == selected.value }
    if (existing != null) return filters - existing
    if (selected.field == ActivitySearchField.DATE_MONTH) {
        val equivalentActiveDateFilters = filters.filter { activityDateFilterCoversMonth(it, selected.value) }
        if (equivalentActiveDateFilters.isNotEmpty()) return filters - equivalentActiveDateFilters.toSet()
    }

    val retained =
        if (
            selected.field in
            setOf(
                ActivitySearchField.DATE_MONTH,
                ActivitySearchField.DATE_ON,
                ActivitySearchField.DATE_RANGE,
                ActivitySearchField.DATE_MONTH_OF_YEAR,
                ActivitySearchField.DATE_MONTH_DAY,
            )
        ) {
            filters.filterNot {
                it.field in
                    setOf(
                        ActivitySearchField.DATE_MONTH,
                        ActivitySearchField.DATE_ON,
                        ActivitySearchField.DATE_RANGE,
                        ActivitySearchField.DATE_MONTH_OF_YEAR,
                        ActivitySearchField.DATE_MONTH_DAY,
                    )
            }
        } else {
            filters
        }
    return retained + selected
}

internal fun isActivitySearchFilterActive(
    filters: List<ActivitySearchFilter>,
    quickFilter: ActivitySearchFilter,
): Boolean {
    if (filters.any { it.field == quickFilter.field && it.value == quickFilter.value }) return true
    return quickFilter.field == ActivitySearchField.DATE_MONTH &&
        filters.any { activityDateFilterCoversMonth(it, quickFilter.value) }
}

internal fun sortActivitySearchIndices(
    indices: List<Int>,
    records: List<ActivitySearchRecord>,
    order: ActivitySortOrder,
): List<Int> =
    indices.filter { it in records.indices }.sortedWith { firstIndex, secondIndex ->
        val first = records[firstIndex]
        val second = records[secondIndex]
        val primary =
            when (order) {
                ActivitySortOrder.NEWEST_FIRST -> {
                    second.dateIso.compareTo(first.dateIso)
                }

                ActivitySortOrder.OLDEST_FIRST -> {
                    first.dateIso.compareTo(second.dateIso)
                }

                ActivitySortOrder.AMOUNT_HIGH_TO_LOW -> {
                    kotlin.math.abs(second.amountMinorUnits).compareTo(kotlin.math.abs(first.amountMinorUnits))
                }

                ActivitySortOrder.AMOUNT_LOW_TO_HIGH -> {
                    kotlin.math.abs(first.amountMinorUnits).compareTo(kotlin.math.abs(second.amountMinorUnits))
                }

                ActivitySortOrder.PAYEE_A_TO_Z -> {
                    first.payee.compareTo(second.payee, ignoreCase = true)
                }
            }
        if (primary != 0) primary else second.dateIso.compareTo(first.dateIso)
    }

internal fun sortActivityTransactions(
    transactions: List<TransactionItem>,
    order: ActivitySortOrder,
): List<TransactionItem> =
    if (order == ActivitySortOrder.NEWEST_FIRST) {
        transactions
    } else {
        transactions.sortedWith { first, second ->
            val primary =
                when (order) {
                    ActivitySortOrder.NEWEST_FIRST -> {
                        second.dateIso.compareTo(first.dateIso)
                    }

                    ActivitySortOrder.OLDEST_FIRST -> {
                        first.dateIso.compareTo(second.dateIso)
                    }

                    ActivitySortOrder.AMOUNT_HIGH_TO_LOW -> {
                        kotlin.math.abs(second.amountMinorUnits).compareTo(kotlin.math.abs(first.amountMinorUnits))
                    }

                    ActivitySortOrder.AMOUNT_LOW_TO_HIGH -> {
                        kotlin.math.abs(first.amountMinorUnits).compareTo(kotlin.math.abs(second.amountMinorUnits))
                    }

                    ActivitySortOrder.PAYEE_A_TO_Z -> {
                        first.payee.compareTo(second.payee, ignoreCase = true)
                    }
                }
            if (primary != 0) primary else second.dateIso.compareTo(first.dateIso)
        }
    }

internal class ActivityTransactionSortCache {
    private var budgetId: String? = null
    private var source: List<TransactionItem>? = null
    private val byOrder = mutableMapOf<ActivitySortOrder, List<TransactionItem>>()

    fun get(
        budgetId: String,
        source: List<TransactionItem>,
        order: ActivitySortOrder,
    ): List<TransactionItem>? {
        resetFor(budgetId, source)
        return byOrder[order]
    }

    fun put(
        budgetId: String,
        source: List<TransactionItem>,
        order: ActivitySortOrder,
        sorted: List<TransactionItem>,
    ) {
        resetFor(budgetId, source)
        byOrder[order] = sorted
    }

    private fun resetFor(
        budgetId: String,
        source: List<TransactionItem>,
    ) {
        if (this.budgetId != budgetId || this.source !== source) {
            this.budgetId = budgetId
            this.source = source
            byOrder.clear()
        }
    }
}

private fun activityDateFilterCoversMonth(
    filter: ActivitySearchFilter,
    monthId: String,
): Boolean {
    if (!isActivitySearchMonthId(monthId)) return false

    val year = monthId.substringBefore('-').toIntOrNull() ?: return false
    val month = monthId.substringAfter('-').toIntOrNull() ?: return false
    val first = "$monthId-01"
    val last = "$monthId-${daysInSearchMonth(year, month).toString().padStart(2, '0')}"
    return when (filter.field) {
        ActivitySearchField.DATE_MONTH_OF_YEAR -> {
            filter.value.toIntOrNull() == month
        }

        ActivitySearchField.DATE_RANGE -> {
            val range = filter.value.split("..", limit = 2)
            range.size == 2 && range[0] <= first && range[1] >= last
        }

        else -> {
            false
        }
    }
}
