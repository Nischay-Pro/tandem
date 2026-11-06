package app.tandemforactual.ui.reports

import app.tandemforactual.data.database.AccountDayAggregate
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.CategoryMonthAggregate
import app.tandemforactual.data.database.MonthlyIncomeSpendingAggregate
import app.tandemforactual.ui.formatMonth
import app.tandemforactual.ui.formatShortMonth
import app.tandemforactual.ui.model.LiveAccountSummary
import app.tandemforactual.ui.model.booleanValue
import app.tandemforactual.ui.model.longValue
import app.tandemforactual.ui.model.recordBody
import app.tandemforactual.ui.model.stringValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

internal enum class ReportKind(
    val title: String,
    val detailDescription: String,
) {
    SPENDING_BY_CATEGORY("Spending by category", "Where the selected period’s spending went."),
    SPENDING_OVER_TIME("Spending over time", "Monthly spending with the period average."),
    INCOME_AND_SPENDING("Income and spending", "Monthly income, spending, and the difference."),
    BUDGETED_AND_SPENT("Budgeted and spent", "Category amounts planned and spent."),
    NET_WORTH("Net worth", "Estimated from current balances and transaction history."),
}

internal enum class ReportPeriodMode {
    MONTH,
    RANGE,
}

internal data class ReportWindow(
    val startDate: String,
    val throughDate: String,
    val startMonthId: String,
    val endMonthId: String,
    val monthIds: List<String>,
) {
    val monthCount: Int get() = monthIds.size
}

internal data class CategorySpendingPoint(
    val categoryId: String,
    val category: String,
    val spendingMinor: Long,
)

internal data class MonthlyMoneyPoint(
    val monthId: String,
    val month: String,
    val incomeMinor: Long,
    val spendingMinor: Long,
) {
    val netMinor: Long get() = incomeMinor - spendingMinor
}

internal data class BudgetedSpentPoint(
    val categoryId: String,
    val category: String,
    val budgetedMinor: Long,
    val spentMinor: Long,
) {
    val isOverBudget: Boolean get() = spentMinor > budgetedMinor
}

internal data class NetWorthPoint(
    val monthId: String,
    val month: String,
    val allAccountsMinor: Long,
    val onBudgetMinor: Long,
)

internal fun createReportWindow(
    endMonthId: String,
    mode: ReportPeriodMode,
    rangeMonths: Int,
): ReportWindow? {
    val end = parseReportMonth(endMonthId) ?: return null
    val count = if (mode == ReportPeriodMode.MONTH) 1 else rangeMonths.coerceIn(3, 12)
    val months = (count - 1 downTo 0).map { offset -> reportMonthId(shiftReportMonth(end, -offset)) }
    val start = parseReportMonth(months.first()) ?: return null
    val firstDate = "${months.first()}-01"
    val lastDay = reportDaysInMonth(end.year, end.month)
    val throughDate = "${months.last()}-${lastDay.toString().padStart(2, '0')}"
    return ReportWindow(firstDate, throughDate, reportMonthId(start), reportMonthId(end), months)
}

internal fun reportMonthIds(
    endMonthId: String,
    count: Int,
): List<String> = createReportWindow(endMonthId, ReportPeriodMode.RANGE, count)?.monthIds.orEmpty()

internal fun shiftReportMonthId(
    monthId: String,
    offset: Int,
): String? = parseReportMonth(monthId)?.let { reportMonthId(shiftReportMonth(it, offset)) }

internal fun reportMonthLabel(monthId: String): String = parseReportMonth(monthId)?.let { formatMonth(it.year, it.month) } ?: monthId

internal fun reportMonthAxisLabel(monthId: String): String =
    parseReportMonth(monthId)?.let { formatShortMonth(it.year, it.month).substringBeforeLast(' ') } ?: monthId

internal fun categorySpendingPoints(
    aggregates: List<CategoryMonthAggregate>,
    categoryNames: Map<String, String>,
): List<CategorySpendingPoint> =
    aggregates
        .groupBy(CategoryMonthAggregate::categoryId)
        .map { (categoryId, monthlyValues) ->
            CategorySpendingPoint(
                categoryId,
                categoryNames[categoryId] ?: categoryId,
                monthlyValues.sumOf(CategoryMonthAggregate::spendingMinor),
            )
        }.sortedWith(compareByDescending<CategorySpendingPoint> { it.spendingMinor }.thenBy { it.category.lowercase() })

internal fun monthlyMoneyPoints(
    aggregates: List<MonthlyIncomeSpendingAggregate>,
    window: ReportWindow,
): List<MonthlyMoneyPoint> {
    val totals = aggregates.associateBy(MonthlyIncomeSpendingAggregate::monthId)
    return window.monthIds.map { monthId ->
        val month = parseReportMonth(monthId)
        val total = totals[monthId]
        MonthlyMoneyPoint(
            monthId,
            month?.let { formatMonth(it.year, it.month) } ?: monthId,
            total?.incomeMinor ?: 0,
            total?.spendingMinor ?: 0,
        )
    }
}

internal fun budgetedSpentPoints(
    budgetMonthRecords: List<BudgetRecordEntity>,
    spendingByCategoryMonth: List<CategoryMonthAggregate>,
    window: ReportWindow,
    categoryNames: Map<String, String>,
): List<BudgetedSpentPoint> {
    val selectedMonthIds = window.monthIds.toSet()
    val budgeted = mutableMapOf<String, Long>()
    val resolvedNames = categoryNames.toMutableMap()
    budgetMonthRecords
        .asSequence()
        .filter { it.recordId in selectedMonthIds && !it.localDeleted && !it.remoteDeleted }
        .mapNotNull { record -> record.recordBody()?.get("categoryGroups") as? JsonArray }
        .flatMap { it.asSequence() }
        .mapNotNull { it as? JsonObject }
        .filterNot { it.booleanValue("hidden") == true }
        .flatMap { it["categories"] as? JsonArray ?: JsonArray(emptyList()) }
        .mapNotNull { it as? JsonObject }
        .filterNot { it.booleanValue("hidden") == true || it.booleanValue("deleted") == true }
        .forEach { category ->
            val categoryId = category.stringValue("id") ?: return@forEach
            val assigned = category.longValue("budgeted") ?: category.longValue("assigned") ?: 0L
            budgeted[categoryId] = (budgeted[categoryId] ?: 0L) + assigned
            category.stringValue("name")?.let { resolvedNames[categoryId] = it }
        }
    val spent =
        spendingByCategoryMonth
            .filter { it.monthId in selectedMonthIds }
            .groupBy(CategoryMonthAggregate::categoryId)
            .mapValues { (_, values) -> values.sumOf(CategoryMonthAggregate::spendingMinor) }
    return (budgeted.keys + spent.keys)
        .map { categoryId ->
            BudgetedSpentPoint(
                categoryId,
                resolvedNames[categoryId] ?: categoryId,
                budgeted[categoryId] ?: 0,
                spent[categoryId] ?: 0,
            )
        }.sortedWith(compareByDescending<BudgetedSpentPoint> { it.spentMinor }.thenBy { it.category.lowercase() })
}

internal fun netWorthPoints(
    accountDays: List<AccountDayAggregate>,
    accounts: List<LiveAccountSummary>,
    monthIds: List<String>,
    todayIsoDate: String,
): List<NetWorthPoint> {
    val todayMonthId = todayIsoDate.take(7)
    val targetMonths = monthIds.filter { it <= todayMonthId }.sortedDescending()
    if (targetMonths.isEmpty()) return emptyList()

    val balances =
        accounts
            .mapNotNull { account -> account.balanceMinor?.let { account.id to (it to !account.offBudget) } }
            .toMap()
            .mapValuesTo(mutableMapOf()) { it.value.first }
    if (balances.isEmpty()) return emptyList()
    val onBudgetIds = accounts.filterNot(LiveAccountSummary::offBudget).mapTo(mutableSetOf(), LiveAccountSummary::id)
    val activityByDate = accountDays.groupBy(AccountDayAggregate::date)
    val activityDates = activityByDate.keys.sortedDescending()
    var activityIndex = 0
    val pointsDescending = mutableListOf<NetWorthPoint>()

    targetMonths.forEach { monthId ->
        val month = parseReportMonth(monthId) ?: return@forEach
        val monthLastDay = reportDaysInMonth(month.year, month.month)
        val monthEndDate = "$monthId-${monthLastDay.toString().padStart(2, '0')}"
        val cutoffDate = if (monthEndDate > todayIsoDate) todayIsoDate else monthEndDate
        while (activityIndex < activityDates.size && activityDates[activityIndex] > cutoffDate) {
            activityByDate.getValue(activityDates[activityIndex]).forEach { activity ->
                if (activity.accountId in balances) {
                    balances[activity.accountId] = balances.getValue(activity.accountId) - activity.amountMinor
                }
            }
            activityIndex += 1
        }
        pointsDescending +=
            NetWorthPoint(
                monthId,
                formatMonth(month.year, month.month),
                balances.values.sum(),
                balances.filterKeys { it in onBudgetIds }.values.sum(),
            )
    }
    return pointsDescending.asReversed()
}

internal fun categorySpendingSummary(
    totalMinor: Long,
    categoryCount: Int,
    formatCurrency: (Long) -> String,
    formatCount: (Int) -> String,
): String = "You spent ${formatCurrency(totalMinor)} across ${formatCount(categoryCount)} categories."

internal fun spendingOverTimeSummary(
    totalMinor: Long,
    monthCount: Int,
    formatCurrency: (Long) -> String,
    formatCount: (Int) -> String,
): String {
    if (monthCount == 0) return "No monthly spending is available for this period."
    return "Average monthly spending was ${formatCurrency(totalMinor / monthCount)} across ${formatCount(monthCount)} months."
}

internal fun incomeSpendingSummary(netMinor: Long): String =
    when {
        netMinor < 0L -> "You are spending more than you make."
        netMinor > 0L -> "You are spending less than you make."
        else -> "You are spending as much as you make."
    }

internal fun budgetedSpentSummary(
    budgetedMinor: Long,
    spentMinor: Long,
    formatCurrency: (Long) -> String,
): String = "You spent ${formatCurrency(spentMinor)} of ${formatCurrency(budgetedMinor)} budgeted."

internal fun netWorthSummary(
    netWorthMinor: Long,
    month: String,
    formatCurrency: (Long) -> String,
): String = "Estimated net worth was ${formatCurrency(netWorthMinor)} at the end of $month."

private data class ReportMonth(
    val year: Int,
    val month: Int,
)

private fun parseReportMonth(monthId: String): ReportMonth? {
    if (monthId.length != 7 || monthId[4] != '-') return null
    val year = monthId.take(4).toIntOrNull() ?: return null
    val month = monthId.takeLast(2).toIntOrNull() ?: return null
    return ReportMonth(year, month).takeIf { year in 1..9999 && month in 1..12 }
}

private fun reportMonthId(month: ReportMonth): String =
    "${month.year.toString().padStart(4, '0')}-${month.month.toString().padStart(2, '0')}"

private fun shiftReportMonth(
    month: ReportMonth,
    offset: Int,
): ReportMonth {
    val index = month.year * 12 + month.month - 1 + offset
    return ReportMonth(index / 12, index.mod(12) + 1)
}

private fun reportDaysInMonth(
    year: Int,
    month: Int,
): Int =
    when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
