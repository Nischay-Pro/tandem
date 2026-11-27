@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.insights
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.data.planning.SpendingInsights
import app.tandemforactual.data.planning.TrendBaseline
import app.tandemforactual.data.planning.TrendDirection
import app.tandemforactual.data.planning.TrendReport
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.RecordNames
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.incomeCategoryIds
import app.tandemforactual.ui.core.state.rememberBudgetTransactions
import app.tandemforactual.ui.core.state.rememberRecordNames
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.rows.SkeletonRows
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.PageColumns
import app.tandemforactual.ui.design.templates.financeIcon
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette

/** Spending in each category this month against last month or a recent average, biggest moves first. */
@Composable
internal fun SpendingTrendsScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val palette = tandemPalette
    var baseline by rememberSaveable { mutableStateOf(TrendBaseline.LAST_MONTH) }
    val transactions = rememberBudgetTransactions(state, mobileSession)
    val names = mobileSession.rememberRecordNames()
    val income = remember(mobileSession.categoryRecords, mobileSession.categoryGroupRecords) { mobileSession.incomeCategoryIds() }
    val entries = remember(transactions, income) { transactions?.let { SpendingInsights.entries(it, income) } }
    val monthId = state.selectedMonthId
    val today = platformTodayIsoDate()
    val report = remember(entries, monthId, baseline, today) { entries?.let { SpendingInsights.trends(it, monthId, baseline, today) } }
    val comparedWith =
        when (baseline) {
            TrendBaseline.LAST_MONTH -> "last month"
            else -> "your ${report?.baselineMonths ?: baseline.months}-month average"
        }
    if (report == null) {
        PageColumn(verticalSpacing = TandemSpacing.large) { SkeletonRows() }
        return
    }
    PageColumns(
        primary = {
            SpendingTrendsHeadline(state, report, comparedWith)
            FilterPills(
                listOf(
                    TrendBaseline.LAST_MONTH to "Last month",
                    TrendBaseline.AVERAGE_3 to "3-month average",
                    TrendBaseline.AVERAGE_6 to "6-month average",
                ),
                baseline,
                { baseline = it },
            )
            report.throughDay?.let { day ->
                InlineMessage(
                    "${state.selectedMonth} isn’t over yet, so each month is counted up to day $day.",
                    InlineMessageKind.INFO,
                )
            }
        },
        secondary = {
            when {
                report.baselineMonths == 0 ->
                    EmptyStateCard(
                        "Not enough history yet",
                        "Trends appear once there’s a month of spending before ${state.selectedMonth}.",
                    )
                report.categories.isEmpty() ->
                    EmptyStateCard(
                        "No spending to compare",
                        "Nothing was spent in ${state.selectedMonth} or before it.",
                    )
                else -> {
                    CategoryTrendsSection(state, report, names, baseline, comparedWith)
                }
            }
            Text(
                "Transfers and income aren’t counted. Changes within 5% show as about the same.",
                color = palette.muted,
                fontSize = TandemTypography.label,
            )
        },
    )
}

/** This month's spending total and how it compares with the baseline. */
@Composable
private fun SpendingTrendsHeadline(
    state: AppState,
    report: TrendReport,
    comparedWith: String,
) {
    val palette = tandemPalette
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
        Text("Spent in ${state.selectedMonth}", color = palette.muted, fontSize = TandemTypography.body)
        Text(
            state.formatCurrency(report.total.currentMinor),
            color = palette.text,
            fontSize = TandemTypography.heroTitle,
            fontWeight = FontWeight.SemiBold,
            fontFamily = tandemAlloFamily(),
        )
        if (report.baselineMonths > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                TrendBadge(report.total.direction, report.total.changeBasisPoints)
                Text(
                    changeSentence(state, report.total.changeMinor, report.total.direction, comparedWith),
                    color = palette.muted,
                    fontSize = TandemTypography.compactBody,
                )
            }
        }
    }
}

/** Each category's spending this month against the baseline. */
@Composable
private fun CategoryTrendsSection(
    state: AppState,
    report: TrendReport,
    names: RecordNames,
    baseline: TrendBaseline,
    comparedWith: String,
) {
    val palette = tandemPalette
    val up = report.categories.count { it.direction == TrendDirection.UP }
    val down = report.categories.count { it.direction == TrendDirection.DOWN }
    SectionCard("By category", "$up up and $down down against $comparedWith") {
        report.categories.forEachIndexed { index, trend ->
            if (index > 0) DividerLine()
            val name = names.category(trend.categoryId)
            ListRow(
                name,
                "${state.formatCurrency(
                    trend.baselineMinor,
                )} ${if (baseline == TrendBaseline.LAST_MONTH) "last month" else "on average"}",
                leading = RowLeading.Icon(financeIcon(name)),
                options = ListRowOptions(showChevron = false),
                trailingContent = {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
                    ) {
                        Text(
                            state.formatCurrency(trend.currentMinor),
                            color = palette.text,
                            fontSize = TandemTypography.body,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                        TrendBadge(trend.direction, trend.changeBasisPoints)
                    }
                },
            )
        }
    }
}

private fun changeSentence(
    state: AppState,
    changeMinor: Long,
    direction: TrendDirection,
    comparedWith: String,
): String =
    when (direction) {
        TrendDirection.FLAT -> "In line with $comparedWith"
        TrendDirection.UP -> "${state.formatCurrency(changeMinor)} more than $comparedWith"
        TrendDirection.DOWN -> "${state.formatCurrency(-changeMinor)} less than $comparedWith"
    }
