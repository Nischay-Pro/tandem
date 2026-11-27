package app.tandemforactual.data.planning

import app.tandemforactual.data.database.SearchableBudgetTransaction

/** Money in and out over one month. */
data class MonthMoney(
    val incomeMinor: Long,
    val spendingMinor: Long,
)

/** Where a month's figures come from. */
enum class TypicalMonthSource {
    /** The chosen month is over, so its own figures are used. */
    CHOSEN_MONTH,

    /** The chosen month isn't over, so recent full months are averaged. */
    RECENT_FULL_MONTHS,

    /** The chosen month isn't over and there are no full months yet, so spending so far is used. */
    MONTH_SO_FAR,
}

data class TypicalMonth(
    val money: MonthMoney,
    val source: TypicalMonthSource,
    val fullMonths: Int,
)

/** A typical month of income and spending for planning, so a half-finished month isn't treated as a whole one. */
object TypicalMonths {
    fun of(
        records: List<SearchableBudgetTransaction>,
        chosenMonthId: String,
        today: String,
        chosen: MonthMoney,
        incomeCategoryIds: Set<String>,
        skippedCategoryIds: Set<String>,
        offBudgetAccountIds: Set<String>,
        months: Int = 3,
    ): TypicalMonth {
        val todayMonthId = today.take(7)
        if (chosenMonthId < todayMonthId) return TypicalMonth(chosen, TypicalMonthSource.CHOSEN_MONTH, 0)
        val counted = records.filter { !it.isParent && it.accountId !in offBudgetAccountIds && it.amountMinor != null }
        val first = counted.mapNotNull { it.recordDate?.takeIf { date -> date.length >= 10 }?.take(7) }.minOrNull()
        val full =
            (1..months)
                .map { SpendingInsights.shiftMonth(todayMonthId, -it) }
                .filter { first != null && it >= first }
        if (full.isEmpty()) return TypicalMonth(chosen, TypicalMonthSource.MONTH_SO_FAR, 0)
        val byMonth = counted.groupBy { it.recordDate?.take(7) }
        val totals = full.map { month -> money(byMonth[month].orEmpty(), incomeCategoryIds, skippedCategoryIds) }
        return TypicalMonth(
            MonthMoney(
                (totals.sumOf { it.incomeMinor } / full.size).coerceAtLeast(0),
                (totals.sumOf { it.spendingMinor } / full.size).coerceAtLeast(0),
            ),
            TypicalMonthSource.RECENT_FULL_MONTHS,
            full.size,
        )
    }

    private fun money(
        records: List<SearchableBudgetTransaction>,
        incomeCategoryIds: Set<String>,
        skippedCategoryIds: Set<String>,
    ): MonthMoney {
        var income = 0L
        var spending = 0L
        records.forEach { record ->
            val amount = record.amountMinor ?: 0L
            val category = record.categoryId
            when {
                category == null -> if (amount > 0 && record.transfer != true) income += amount
                category in skippedCategoryIds -> Unit
                category in incomeCategoryIds -> income += amount
                else -> spending -= amount
            }
        }
        return MonthMoney(income, spending)
    }
}
