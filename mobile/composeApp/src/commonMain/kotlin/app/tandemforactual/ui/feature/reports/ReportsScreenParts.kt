@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.format.formatReportCurrency
import app.tandemforactual.ui.core.state.ReportKind
import app.tandemforactual.ui.design.theme.tandemPalette

internal fun positiveAmount(value: Long): Long = if (value < 0L) -value else value

internal fun reportHasNoData(
    kind: ReportKind,
    data: ReportDataBundle,
): Boolean {
    val monthly = monthlyPoints(kind, data)
    return when (kind) {
        ReportKind.SPENDING_BY_CATEGORY -> {
            data.categories.isEmpty()
        }

        ReportKind.SPENDING_OVER_TIME, ReportKind.INCOME_AND_SPENDING -> {
            monthly.isEmpty() || monthly.all { it.incomeMinor == 0L && it.spendingMinor == 0L }
        }

        ReportKind.BUDGETED_AND_SPENT -> {
            data.budgetedSpent.isEmpty()
        }

        ReportKind.NET_WORTH -> {
            data.netWorth.isEmpty()
        }
    }
}

internal fun monthlyPoints(
    kind: ReportKind,
    data: ReportDataBundle,
): List<MonthlyMoneyPoint> = if (kind == ReportKind.INCOME_AND_SPENDING) data.incomeAndSpending else data.monthly

internal fun reportSummary(
    kind: ReportKind,
    data: ReportDataBundle,
    netWorthScope: String,
): String {
    val format: (Long) -> String = { amount -> formatReportCurrency(amount, data.displaySettings) }
    val monthly = monthlyPoints(kind, data)
    return when (kind) {
        ReportKind.SPENDING_BY_CATEGORY -> {
            categorySpendingSummary(
                data.categories.sumOf(CategorySpendingPoint::spendingMinor),
                data.categories.size,
                format,
                ::formatInteger,
            )
        }

        ReportKind.SPENDING_OVER_TIME -> {
            spendingOverTimeSummary(monthlySpendingAverage(monthly), format, ::formatInteger)
        }

        ReportKind.INCOME_AND_SPENDING -> {
            incomeSpendingSummary(monthly.sumOf(MonthlyMoneyPoint::netMinor))
        }

        ReportKind.BUDGETED_AND_SPENT -> {
            budgetedSpentSummary(
                data.budgetedSpent.sumOf(BudgetedSpentPoint::budgetedMinor),
                data.budgetedSpent.sumOf(BudgetedSpentPoint::spentMinor),
                format,
            )
        }

        ReportKind.NET_WORTH -> {
            val point = data.netWorth.lastOrNull()
            if (point == null) {
                "Net worth is unavailable for this period."
            } else {
                netWorthSummary(if (netWorthScope == "On budget") point.onBudgetMinor else point.allAccountsMinor, point.month, format)
            }
        }
    }
}

@Composable
internal fun reportSummaryColor(
    kind: ReportKind,
    data: ReportDataBundle,
): androidx.compose.ui.graphics.Color =
    if (kind != ReportKind.INCOME_AND_SPENDING) {
        tandemPalette.muted
    } else {
        when (monthlyPoints(kind, data).sumOf(MonthlyMoneyPoint::netMinor).compareTo(0L)) {
            1 -> tandemPalette.positive
            -1 -> tandemPalette.negative
            else -> tandemPalette.muted
        }
    }

internal fun reportChartDescription(
    kind: ReportKind,
    data: ReportDataBundle,
    netWorthScope: String,
): String =
    "${kind.title}. ${reportSummary(kind, data, netWorthScope)} " +
        when (kind) {
            ReportKind.SPENDING_BY_CATEGORY -> {
                data.categories.joinToString {
                    "${it.category}: ${formatReportCurrency(it.spendingMinor, data.displaySettings)}"
                }
            }

            ReportKind.SPENDING_OVER_TIME -> {
                monthlyPoints(kind, data).joinToString {
                    "${it.month}: ${formatReportCurrency(it.spendingMinor, data.displaySettings)}"
                }
            }

            ReportKind.INCOME_AND_SPENDING -> {
                monthlyPoints(kind, data).joinToString {
                    "${it.month}: income ${formatReportCurrency(
                        it.incomeMinor,
                        data.displaySettings,
                    )}, spending ${formatReportCurrency(it.spendingMinor, data.displaySettings)}"
                }
            }

            ReportKind.BUDGETED_AND_SPENT -> {
                data.budgetedSpent.joinToString {
                    "${it.category}: budgeted ${formatReportCurrency(
                        it.budgetedMinor,
                        data.displaySettings,
                    )}, spent ${formatReportCurrency(it.spentMinor, data.displaySettings)}"
                }
            }

            ReportKind.NET_WORTH -> {
                data.netWorth.joinToString { point ->
                    val assets = if (netWorthScope == "On budget") point.onBudgetAssetsMinor else point.allAccountsAssetsMinor
                    val debts = if (netWorthScope == "On budget") point.onBudgetDebtsMinor else point.allAccountsDebtsMinor
                    val value = assets - debts
                    "${point.month}: net worth ${formatReportCurrency(
                        value,
                        data.displaySettings,
                    )}, assets ${formatReportCurrency(
                        assets,
                        data.displaySettings,
                    )}, debts ${formatReportCurrency(debts, data.displaySettings)}"
                }
            }
        }

@Composable
internal fun netAmountColor(amountMinor: Long): androidx.compose.ui.graphics.Color =
    when {
        amountMinor > 0L -> tandemPalette.positive
        amountMinor < 0L -> tandemPalette.negative
        else -> tandemPalette.muted
    }

internal fun List<Long>.averageLong(): Long = if (isEmpty()) 0L else sum() / size
