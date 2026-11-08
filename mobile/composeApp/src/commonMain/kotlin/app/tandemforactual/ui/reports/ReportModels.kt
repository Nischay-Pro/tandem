package app.tandemforactual.ui.reports

import app.tandemforactual.data.database.AccountDayAggregate
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.CategoryMonthAggregate
import app.tandemforactual.data.database.MonthlyIncomeSpendingAggregate
import app.tandemforactual.ui.formatMonth
import app.tandemforactual.ui.formatReportPercentage
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

internal enum class ReportRangePreset(
    val label: String,
    val monthCount: Int?,
) {
    LAST_3_MONTHS("Last 3 months", 3),
    LAST_6_MONTHS("Last 6 months", 6),
    LAST_12_MONTHS("Last 12 months", 12),
    YEAR_TO_DATE("Year to date", null),
    LAST_YEAR("Last year", null),
}

internal data class ReportWindow(
    val startDate: String,
    val throughDate: String,
    val startMonthId: String,
    val endMonthId: String,
    val monthIds: List<String>,
    val allDates: Boolean = false,
) {
    val monthCount: Int get() = monthIds.size
}

internal data class CategorySpendingPoint(
    val categoryId: String,
    val category: String,
    val spendingMinor: Long,
)

internal const val OTHER_CATEGORY_CHART_ID = "__tandem_report_other__"

internal fun categoryChartPoints(categories: List<CategorySpendingPoint>): List<CategorySpendingPoint> {
    if (categories.size <= 1) return categories
    val total = categories.sumOf { it.spendingMinor.coerceAtLeast(0L) }
    if (total <= 0L) return categories.take(8)

    val visible =
        categories
            .filter { it.spendingMinor.toDouble() / total.toDouble() >= 0.04 }
            .take(7)
            .ifEmpty { categories.take(7) }
    val visibleIds = visible.mapTo(mutableSetOf(), CategorySpendingPoint::categoryId)
    val otherAmount =
        categories
            .asSequence()
            .filterNot { it.categoryId in visibleIds }
            .sumOf { it.spendingMinor.coerceAtLeast(0L) }
    return if (otherAmount > 0L) {
        visible + CategorySpendingPoint(OTHER_CATEGORY_CHART_ID, "Other", otherAmount)
    } else {
        visible
    }
}

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
    val allAccountsAssetsMinor: Long,
    val allAccountsDebtsMinor: Long,
    val onBudgetAssetsMinor: Long,
    val onBudgetDebtsMinor: Long,
) {
    val allAccountsMinor: Long get() = allAccountsAssetsMinor - allAccountsDebtsMinor
    val onBudgetMinor: Long get() = onBudgetAssetsMinor - onBudgetDebtsMinor
}

internal fun categoryPercentage(
    amount: Long,
    total: Long,
): String = formatReportPercentage(if (total <= 0L || amount <= 0L) 0.0 else amount.toDouble() / total.toDouble())

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

internal fun createReportWindow(
    endMonthId: String,
    mode: ReportPeriodMode,
    rangePreset: ReportRangePreset,
    todayIsoDate: String,
): ReportWindow? {
    if (mode == ReportPeriodMode.MONTH) return createReportWindow(endMonthId, mode, 1)
    rangePreset.monthCount?.let { count -> return createReportWindow(endMonthId, ReportPeriodMode.RANGE, count) }

    val today = todayIsoDate.takeIf { it.length >= 10 } ?: return null
    val todayMonthId = today.take(7)
    val todayMonth = parseReportMonth(todayMonthId) ?: return null
    return when (rangePreset) {
        ReportRangePreset.YEAR_TO_DATE -> {
            val startMonth = "${todayMonth.year.toString().padStart(4, '0')}-01"
            ReportWindow(
                "${todayMonth.year.toString().padStart(4, '0')}-01-01",
                today,
                startMonth,
                todayMonthId,
                reportMonthsBetween(startMonth, todayMonthId),
            )
        }
        ReportRangePreset.LAST_YEAR -> {
            val year = todayMonth.year - 1
            val firstMonth = "${year.toString().padStart(4, '0')}-01"
            val lastMonth = "${year.toString().padStart(4, '0')}-12"
            ReportWindow(
                "$firstMonth-01",
                "$lastMonth-31",
                firstMonth,
                lastMonth,
                reportMonthsBetween(firstMonth, lastMonth),
            )
        }
        else -> createReportWindow(endMonthId, ReportPeriodMode.RANGE, 6)
    }
}

internal fun createReportHistoryWindow(
    availableMonthIds: List<String>,
    todayIsoDate: String,
): ReportWindow? {
    val today = todayIsoDate.takeIf { it.length >= 10 } ?: return null
    val todayMonthId = today.take(7)
    if (parseReportMonth(todayMonthId) == null) return null
    val startMonthId =
        availableMonthIds
            .filter { parseReportMonth(it) != null && it <= todayMonthId }
            .minOrNull()
            ?.takeIf { it <= todayMonthId } ?: todayMonthId
    return ReportWindow(
        startDate = "$startMonthId-01",
        throughDate = today,
        startMonthId = startMonthId,
        endMonthId = todayMonthId,
        monthIds = reportMonthsBetween(startMonthId, todayMonthId),
        allDates = true,
    )
}

private fun reportMonthsBetween(
    startMonthId: String,
    endMonthId: String,
): List<String> {
    val start = parseReportMonth(startMonthId) ?: return emptyList()
    val end = parseReportMonth(endMonthId) ?: return emptyList()
    if (start.year * 12 + start.month > end.year * 12 + end.month) return emptyList()
    val result = mutableListOf<String>()
    var current = start
    while (current.year < end.year || current.year == end.year && current.month <= end.month) {
        result += reportMonthId(current)
        current = shiftReportMonth(current, 1)
    }
    return result
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
                monthlyValues.sumOf(CategoryMonthAggregate::spendingMinor).coerceAtLeast(0L),
            )
        }.sortedWith(compareByDescending<CategorySpendingPoint> { it.spendingMinor }.thenBy { it.category.lowercase() })

internal fun monthlyMoneyPoints(
    aggregates: List<MonthlyIncomeSpendingAggregate>,
    window: ReportWindow,
): List<MonthlyMoneyPoint> {
    val totals = aggregates.associateBy(MonthlyIncomeSpendingAggregate::monthId)
    val monthIds =
        if (window.allDates) {
            val firstMonthId = totals.keys.minOrNull() ?: return emptyList()
            reportMonthsBetween(firstMonthId, window.endMonthId)
        } else {
            window.monthIds
        }
    return monthIds.map { monthId ->
        val month = parseReportMonth(monthId)
        val total = totals[monthId]
        MonthlyMoneyPoint(
            monthId,
            month?.let { formatMonth(it.year, it.month) } ?: monthId,
            total?.incomeMinor ?: 0,
            (total?.spendingMinor ?: 0).coerceAtLeast(0L),
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
        .filter { (window.allDates || it.recordId in selectedMonthIds) && !it.localDeleted && !it.remoteDeleted }
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
            .filter { window.allDates || it.monthId in selectedMonthIds }
            .groupBy(CategoryMonthAggregate::categoryId)
            .mapValues { (_, values) -> values.sumOf(CategoryMonthAggregate::spendingMinor).coerceAtLeast(0L) }
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
                balances.values.filter { it > 0 }.sum(),
                balances.values.filter { it < 0 }.sumOf { -it },
                balances
                    .filterKeys { it in onBudgetIds }
                    .values
                    .filter { it > 0 }
                    .sum(),
                balances
                    .filterKeys { it in onBudgetIds }
                    .values
                    .filter { it < 0 }
                    .sumOf { -it },
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
