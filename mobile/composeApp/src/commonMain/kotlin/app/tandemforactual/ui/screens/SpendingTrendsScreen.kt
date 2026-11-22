@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

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
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.FilterPills
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SkeletonRows

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
    val report = remember(entries, monthId, baseline) { entries?.let { SpendingInsights.trends(it, monthId, baseline) } }
    val comparedWith =
        when (baseline) {
            TrendBaseline.LAST_MONTH -> "last month"
            else -> "your ${report?.baselineMonths ?: baseline.months}-month average"
        }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        if (report == null) {
            SkeletonRows()
            return@PageColumn
        }
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
        FilterPills(
            listOf(
                TrendBaseline.LAST_MONTH to "Last month",
                TrendBaseline.AVERAGE_3 to "3-month average",
                TrendBaseline.AVERAGE_6 to "6-month average",
            ),
            baseline,
            { baseline = it },
        )
        if (monthId == platformTodayIsoDate().take(7)) {
            InlineMessage("${state.selectedMonth} isn’t over yet, so it’s compared with full months.", InlineMessageKind.INFO)
        }
        when {
            report.baselineMonths == 0 ->
                EmptyState("Not enough history yet", "Trends appear once there’s a month of spending before ${state.selectedMonth}.")
            report.categories.isEmpty() -> EmptyState("No spending to compare", "Nothing was spent in ${state.selectedMonth} or before it.")
            else -> {
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
        }
        Text(
            "Transfers and income aren’t counted. Changes within 5% show as about the same.",
            color = palette.muted,
            fontSize = TandemTypography.label,
        )
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
