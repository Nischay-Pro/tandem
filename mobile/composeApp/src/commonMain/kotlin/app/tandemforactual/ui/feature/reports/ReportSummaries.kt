package app.tandemforactual.ui.feature.reports
import app.tandemforactual.ui.core.format.formatCurrency

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
