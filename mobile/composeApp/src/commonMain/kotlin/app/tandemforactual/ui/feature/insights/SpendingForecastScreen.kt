@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.insights
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.tandemforactual.data.planning.CategoryForecast
import app.tandemforactual.data.planning.ForecastBasis
import app.tandemforactual.data.planning.ForecastStatus
import app.tandemforactual.data.planning.SpendingInsights
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.rows.SkeletonRows
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.budget.daysInMonth
import app.tandemforactual.ui.feature.reports.reportMonthLabel
import app.tandemforactual.ui.feature.transactions.daysInMonth

/** Where this month’s spending is heading, per category and in total, and which budgets look set to run out. */
@Composable
internal fun SpendingForecastScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    month: LiveBudgetMonthSummary?,
) {
    val palette = tandemPalette
    val today = platformTodayIsoDate()
    val thisMonth = today.take(7)
    if (state.selectedMonthId != thisMonth) {
        PageColumn(verticalSpacing = TandemSpacing.large) {
            EmptyState("Forecasts look ahead from today", "Switch to this month to see where your spending is heading.")
            TandemActionButton("Show this month", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth()) {
                state.selectedMonthId = thisMonth
                state.selectedMonth = reportMonthLabel(thisMonth)
            }
        }
        return
    }
    val transactions = rememberBudgetTransactions(state, mobileSession)
    val names = mobileSession.rememberRecordNames()
    val income = remember(mobileSession.categoryRecords, mobileSession.categoryGroupRecords) { mobileSession.incomeCategoryIds() }
    val limits =
        remember(month) {
            month
                ?.groups
                .orEmpty()
                .filterNot { it.isIncome }
                .flatMap { it.categories }
                .associate { it.id to it.balanceMinor - it.activityMinor }
                .filterValues { it > 0 }
        }
    val forecast =
        remember(transactions, income, limits, today) {
            transactions?.let { SpendingInsights.forecast(SpendingInsights.entries(it, income), thisMonth, today, limits) }
        }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        if (forecast == null || month == null) {
            SkeletonRows()
            return@PageColumn
        }
        val daysLeft = forecast.daysInMonth - forecast.dayOfMonth
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            Text("On pace to spend", color = palette.muted, fontSize = TandemTypography.body)
            Text(
                state.formatCurrency(forecast.projectedMinor),
                color = if (forecast.limitMinor > 0 && forecast.projectedMinor > forecast.limitMinor) palette.negative else palette.text,
                fontSize = TandemTypography.heroTitle,
                fontWeight = FontWeight.SemiBold,
                fontFamily = tandemAlloFamily(),
            )
            Text(
                "${state.formatCurrency(forecast.spentMinor)} spent so far · " +
                    if (daysLeft == 1) "1 day left" else "$daysLeft days left",
                color = palette.muted,
                fontSize = TandemTypography.compactBody,
            )
        }
        if (forecast.limitMinor > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                val scale = maxOf(forecast.projectedMinor, forecast.limitMinor).toFloat()
                ProgressBar(
                    forecast.projectedMinor / scale,
                    if (forecast.projectedMinor > forecast.limitMinor) palette.negative else palette.accent,
                    marker = forecast.limitMinor / scale,
                )
                Row {
                    Text("Projected", Modifier.weight(1f), color = palette.muted, fontSize = TandemTypography.caption)
                    Text(
                        "${state.formatCurrency(forecast.limitMinor)} budgeted",
                        color = palette.muted,
                        fontSize = TandemTypography.caption,
                    )
                }
            }
        }
        val atRisk = forecast.categories.filter { it.status != ForecastStatus.ON_TRACK }.sortedBy { it.runOutDate ?: "" }
        if (atRisk.isEmpty() && forecast.categories.isNotEmpty()) {
            InlineMessage("Every budgeted category is on pace to stay within its budget.", InlineMessageKind.SUCCESS)
        }
        if (atRisk.isNotEmpty()) {
            SectionCard("Heads up", "${atRisk.size} ${if (atRisk.size == 1) "category" else "categories"} may overspend") {
                atRisk.forEachIndexed { index, category ->
                    if (index > 0) DividerLine()
                    Text(
                        warning(state, names.category(category.categoryId), category, forecast.daysInMonth),
                        Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                        color = palette.text,
                        fontSize = TandemTypography.body,
                    )
                }
            }
        }
        val shown = forecast.categories.sortedByDescending { it.projectedMinor }
        if (shown.isEmpty()) {
            EmptyState("Nothing to forecast yet", "Spending recorded this month will be projected here.")
        } else {
            SectionCard("By category", "Spent so far and where it’s heading") {
                shown.forEachIndexed { index, category ->
                    if (index > 0) DividerLine()
                    ForecastRow(state, names.category(category.categoryId), category)
                }
            }
        }
        Text(
            "Estimates follow how your spending usually lands after this point in the month, from up to 6 months of history, " +
                "adjusted for your pace so far. Without history, spending so far is stretched over the month. They’re a guide, not a promise.",
            color = palette.muted,
            fontSize = TandemTypography.label,
        )
    }
}

@Composable
private fun ForecastRow(
    state: AppState,
    name: String,
    category: CategoryForecast,
) {
    val palette = tandemPalette
    val limit = category.limitMinor
    val color =
        when (category.status) {
            ForecastStatus.OVER -> palette.negative
            ForecastStatus.AT_RISK -> palette.warning
            ForecastStatus.ON_TRACK -> palette.accent
        }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Row {
            Text(
                name,
                Modifier.weight(1f).padding(end = TandemSpacing.small),
                color = palette.text,
                fontSize = TandemTypography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.formatCurrency(category.projectedMinor),
                color = if (category.status == ForecastStatus.ON_TRACK) palette.text else color,
                fontSize = TandemTypography.body,
                fontWeight = FontWeight.Medium,
            )
        }
        val scale = maxOf(category.projectedMinor, limit ?: 0, 1).toFloat()
        ProgressBar(category.spentMinor / scale, color, marker = limit?.let { it / scale })
        Text(
            listOfNotNull(
                "${state.formatCurrency(category.spentMinor)} spent",
                limit?.let { "${state.formatCurrency(it)} budgeted" } ?: "Nothing budgeted",
                when (category.basis) {
                    ForecastBasis.HISTORY ->
                        "from ${category.historyMonths} ${if (category.historyMonths == 1) "month" else "months"} of history"
                    ForecastBasis.PACE -> "from this month’s pace"
                    ForecastBasis.FINAL -> null
                },
            ).joinToString(" · "),
            color = palette.muted,
            fontSize = TandemTypography.caption,
        )
    }
}

private fun warning(
    state: AppState,
    name: String,
    category: CategoryForecast,
    daysInMonth: Int,
): String {
    val limit = category.limitMinor ?: 0
    val runOut = category.runOutDate
    return when {
        category.status == ForecastStatus.OVER ->
            "$name is already ${state.formatCurrency(category.spentMinor - limit)} over budget."
        runOut != null -> {
            val early = daysInMonth - runOut.substring(8, 10).toInt()
            val days = if (early == 1) "1 day" else "$early days"
            val whenText = if (early <= 0) "by the end of the month" else "around ${formatLiveDate(runOut)}, $days early"
            "At this pace, $name could run out $whenText and end ${state.formatCurrency(category.projectedMinor - limit)} over."
        }
        else -> "$name could end ${state.formatCurrency(category.projectedMinor - limit)} over budget."
    }
}
