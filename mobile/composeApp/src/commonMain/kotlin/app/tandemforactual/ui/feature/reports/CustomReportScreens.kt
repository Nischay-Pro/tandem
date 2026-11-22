@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.planning.CustomReport
import app.tandemforactual.data.planning.ReportFlow
import app.tandemforactual.data.planning.ReportGrouping
import app.tandemforactual.data.planning.ReportRange
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.feature.insights.RecordNames

internal val ReportRange.label: String
    get() =
        when (this) {
            ReportRange.THIS_MONTH -> "This month"
            ReportRange.LAST_MONTH -> "Last month"
            ReportRange.LAST_3_MONTHS -> "Last 3 months"
            ReportRange.LAST_6_MONTHS -> "Last 6 months"
            ReportRange.LAST_12_MONTHS -> "Last 12 months"
            ReportRange.YEAR_TO_DATE -> "Year to date"
            ReportRange.LAST_YEAR -> "Last year"
            ReportRange.CUSTOM -> "Custom"
        }

internal val ReportFlow.label: String
    get() =
        when (this) {
            ReportFlow.SPENDING -> "Spending"
            ReportFlow.INCOME -> "Income"
            ReportFlow.NET -> "Net"
        }

internal val ReportGrouping.label: String
    get() =
        when (this) {
            ReportGrouping.CATEGORY -> "Category"
            ReportGrouping.PAYEE -> "Payee"
            ReportGrouping.ACCOUNT -> "Account"
        }

internal fun CustomReport.summary(): String {
    val filters = accountIds.size + categoryIds.size + payeeIds.size
    return listOfNotNull(
        "${flow.label} by ${grouping.label.lowercase()}",
        if (range == ReportRange.CUSTOM) "${formatLiveDate(startDate)} – ${formatLiveDate(endDate)}" else range.label,
        filters.takeIf { it > 0 }?.let { if (it == 1) "1 filter" else "$it filters" },
    ).joinToString(" · ")
}

internal fun RecordNames.group(
    grouping: ReportGrouping,
    key: String?,
): String =
    when (grouping) {
        ReportGrouping.CATEGORY -> category(key)
        ReportGrouping.PAYEE -> key?.let { payees[it] ?: "Deleted payee" } ?: "No payee"
        ReportGrouping.ACCOUNT -> key?.let { accounts[it] ?: "Deleted account" } ?: "No account"
    }

internal enum class ReportFilterKind(
    val title: String,
) {
    ACCOUNTS("Accounts"),
    CATEGORIES("Categories"),
    PAYEES("Payees"),
    ;

    fun selected(report: CustomReport): List<String> =
        when (this) {
            ACCOUNTS -> report.accountIds
            CATEGORIES -> report.categoryIds
            PAYEES -> report.payeeIds
        }

    fun apply(
        report: CustomReport,
        ids: List<String>,
    ): CustomReport =
        when (this) {
            ACCOUNTS -> report.copy(accountIds = ids)
            CATEGORIES -> report.copy(categoryIds = ids)
            PAYEES -> report.copy(payeeIds = ids)
        }
}
