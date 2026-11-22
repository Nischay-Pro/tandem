@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.insights
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.actual.ActualDates
import app.tandemforactual.data.planning.CashFlow
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.core.state.monthMoneyIn
import app.tandemforactual.ui.design.cards.StatTile
import app.tandemforactual.ui.design.charts.AreaChart
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.Lucide

private data class FlowEvent(
    val date: String,
    val name: String,
    val amountMinor: Long,
)

/** Projects the on-budget balance forward from today using the budget's schedules, and warns before it dips below zero. */
@Composable
internal fun CashFlowScreen(
    state: AppState,
    liveAccounts: List<LiveAccountSummary>,
    schedules: List<LiveScheduleSummary>,
) {
    var horizon by rememberSaveable { mutableStateOf(60) }
    var scrub by remember { mutableStateOf<Int?>(null) }
    val today = platformTodayIsoDate()
    val accounts = liveAccounts.filter { !it.offBudget && !it.closed }
    val start = accounts.sumOf { it.balanceMinor ?: 0 }
    val end = ActualDates.addDays(today, horizon)
    val events =
        remember(schedules, today, horizon) {
            schedules
                .filter { schedule -> schedule.accountId == null || accounts.any { it.id == schedule.accountId } }
                .flatMap { schedule ->
                    val amount = schedule.amountMinor ?: schedule.amountRangeMinor?.let { (low, high) -> (low + high) / 2 } ?: 0
                    CashFlow.scheduleDates(schedule.dateValueJson, today, end).map { FlowEvent(it, schedule.name, amount) }
                }.sortedBy { it.date }
        }
    val projection = remember(start, events, horizon) { CashFlow.project(start, today, horizon, events.map { it.date to it.amountMinor }) }
    val palette = tandemPalette
    PageColumn(verticalSpacing = TandemSpacing.large) {
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            val point = scrub
            Text(
                if (point != null) formatLiveDate(ActualDates.addDays(today, point)) else "In $horizon days",
                color = palette.muted,
                fontSize = TandemTypography.body,
            )
            Text(
                state.formatCurrency(projection.daily[point ?: projection.daily.lastIndex]),
                color = palette.text,
                fontSize = TandemTypography.heroTitle,
                fontWeight = FontWeight.SemiBold,
                fontFamily = tandemAlloFamily(),
            )
            Text(
                "Today ${state.formatCurrency(start)} across ${accounts.size} on-budget accounts",
                color = palette.muted,
                fontSize = TandemTypography.compactBody,
            )
        }
        AreaChart(projection.daily, "Projected balance for the next $horizon days", selected = scrub, onScrub = { scrub = it })
        FilterPills(listOf(30 to "30 days", 60 to "60 days", 90 to "90 days"), horizon, { horizon = it })
        if (projection.lowestMinor < 0) {
            InlineMessage(
                "Your balance dips to ${state.formatCurrency(projection.lowestMinor)} on ${formatLiveDate(projection.lowestDate)}.",
                InlineMessageKind.WARNING,
            )
        }
        if (events.isEmpty()) {
            EmptyState("No scheduled transactions", "Add schedules for bills and paychecks to see where your balance is heading.")
        } else {
            SectionCard("Coming up", "${events.size} scheduled in the next $horizon days") {
                events.take(MAX_FLOW_ROWS).forEachIndexed { index, event ->
                    if (index > 0) DividerLine()
                    ListRow(
                        event.name,
                        formatLiveDate(event.date),
                        state.formatCurrency(event.amountMinor),
                        leading = RowLeading.Icon(Lucide.CalendarClock),
                        options = ListRowOptions(showChevron = false),
                    )
                }
            }
        }
        Text(
            "Schedules with a range use the middle of it. Only on-budget accounts are included.",
            color = palette.muted,
            fontSize = TandemTypography.label,
        )
    }
}

private const val MAX_FLOW_ROWS = 40

/** A look back at the selected month: what came in, what went out, where it went, and the biggest purchases. */
@Composable
internal fun MonthlyReviewScreen(
    state: AppState,
    month: LiveBudgetMonthSummary?,
    transactions: List<TransactionItem>,
) {
    val palette = tandemPalette
    val (income, monthTransactions) = monthMoneyIn(state, month, transactions)
    val categories =
        month
            ?.groups
            .orEmpty()
            .filterNot { it.isIncome }
            .flatMap { it.categories }
    val spent = -categories.sumOf { it.activityMinor }
    val top = categories.filter { it.activityMinor < 0 }.sortedBy { it.activityMinor }.take(6)
    val overspent = categories.filter { it.balanceMinor < 0 }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        MonthReviewHeadline(state, income, spent)
        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            StatTile(
                "Money in",
                state.formatCurrency(income),
                "${monthTransactions.count { it.amountMinorUnits > 0 }} deposits",
                Modifier.weight(1f),
            )
            StatTile(
                "Money out",
                state.formatCurrency(spent),
                "${monthTransactions.count { it.amountMinorUnits < 0 }} purchases",
                Modifier.weight(1f),
            )
        }
        if (top.isNotEmpty()) {
            WhereItWentSection(state, top)
        }
        if (overspent.isNotEmpty()) {
            InlineMessage(
                "${overspent.size} ${if (overspent.size == 1) "category is" else "categories are"} overspent: " +
                    overspent.take(3).joinToString { it.name } + if (overspent.size > 3) " and more." else ".",
                InlineMessageKind.WARNING,
            )
        }
        val biggest = monthTransactions.filter { it.amountMinorUnits < 0 }.sortedBy { it.amountMinorUnits }.take(5)
        if (biggest.isNotEmpty()) {
            SectionCard("Biggest purchases", null) {
                biggest.forEachIndexed { index, item ->
                    if (index > 0) DividerLine()
                    ListRow(
                        item.payee,
                        "${item.category} · ${item.dateLabel}",
                        state.formatCurrency(item.amountMinorUnits),
                        options = ListRowOptions(showChevron = false),
                    )
                }
            }
        }
        if (monthTransactions.isEmpty()) {
            EmptyState(
                "Nothing recorded this month yet",
                "Transactions for ${state.selectedMonth} will be summarized here.",
            )
        }
    }
}

/** The month's name and whether it saved or overspent overall. */
@Composable
private fun MonthReviewHeadline(
    state: AppState,
    income: Long,
    spent: Long,
) {
    val palette = tandemPalette
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
        Text("${state.selectedMonth} in review", color = palette.muted, fontSize = TandemTypography.body)
        Text(
            if (income >=
                spent
            ) {
                "Saved ${state.formatCurrency(income - spent)}"
            } else {
                "Spent ${state.formatCurrency(spent - income)} more than earned"
            },
            color = palette.text,
            fontSize = TandemTypography.heroTitle,
            lineHeight = TandemTypography.headingLineHeight * 1.2f,
            fontWeight = FontWeight.SemiBold,
            fontFamily = tandemAlloFamily(),
        )
    }
}

/** The top spending categories, each with a bar against the largest. */
@Composable
private fun WhereItWentSection(
    state: AppState,
    top: List<LiveCategorySummary>,
) {
    val palette = tandemPalette
    SectionCard("Where it went", "Spending by category against what you budgeted") {
        val largest = -top.first().activityMinor
        top.forEachIndexed { index, category ->
            if (index > 0) DividerLine()
            val used = -category.activityMinor
            Column(
                Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row {
                    Text(category.name, Modifier.weight(1f), color = palette.text, fontSize = TandemTypography.body)
                    Text(
                        state.formatCurrency(used),
                        color = palette.text,
                        fontSize = TandemTypography.body,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(TandemShapes.pill))
                        .background(palette.raised),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((used.toFloat() / largest).coerceIn(0.02f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(TandemShapes.pill))
                            .background(if (category.balanceMinor < 0) palette.negative else palette.accent),
                    )
                }
                Text(
                    if (category.assignedMinor >
                        0
                    ) {
                        "${state.formatCurrency(category.assignedMinor)} budgeted"
                    } else {
                        "Nothing budgeted"
                    },
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                )
            }
        }
    }
}
