@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.data.planning.DebtInput
import app.tandemforactual.data.planning.DebtKind
import app.tandemforactual.data.planning.GoalInput
import app.tandemforactual.data.planning.GoalSplit
import app.tandemforactual.data.planning.GoalTimeline
import app.tandemforactual.data.planning.PayoffStrategy
import app.tandemforactual.data.planning.PlanningMath
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.FilterPills
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.model.LiveAccountSummary
import app.tandemforactual.ui.model.LiveBudgetMonthSummary
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank

/**
 * Tries out changes to monthly income and spending and how the leftover is split, then shows when debts are paid off and
 * each savings goal is reached, next to how things stand today.
 */
@Composable
internal fun WhatIfScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    liveAccounts: List<LiveAccountSummary>,
    month: LiveBudgetMonthSummary?,
    transactions: List<TransactionItem>,
) {
    val palette = tandemPalette
    if (month == null) {
        PageColumn { EmptyState("Open a budget month first", "What-if uses this month’s income and spending as the starting point.") }
        return
    }
    var incomeChange by rememberSaveable { mutableStateOf(0L) }
    var spendingChange by rememberSaveable { mutableStateOf(0L) }
    var toDebt by rememberSaveable { mutableStateOf(50) }
    val income = monthMoneyIn(state, month, transactions).first.coerceAtLeast(0)
    val spending = (-month.groups.filterNot { it.isIncome }.sumOf { it.activityMinor }).coerceAtLeast(0)
    val leftover = income + incomeChange - (spending + spendingChange)
    val spare = leftover.coerceAtLeast(0)
    val debtExtra = spare * toDebt / 100
    val goalExtra = spare - debtExtra
    val debts =
        liveAccounts.mapNotNull { account ->
            val terms = mobileSession.debtTerms.firstOrNull { it.accountId == account.id }
            val owed = -(account.balanceMinor ?: 0)
            val apr = terms?.aprBasisPoints
            val minimum = terms?.minimumPaymentMinor
            if (account.closed ||
                owed <= 0 ||
                terms?.kind == DebtKind.NOT_DEBT ||
                terms?.paysInFull == true ||
                apr == null ||
                minimum == null
            ) {
                null
            } else {
                DebtInput(account.id, owed, apr, minimum)
            }
        }
    val goals = mobileSession.savingsGoals
    val today = platformTodayIsoDate()

    fun monthLabel(offset: Int): String {
        val (y, m) = today.split('-').let { it[0].toInt() to it[1].toInt() }
        val index = y * 12 + (m - 1) + offset
        return platformFormatMonth(index / 12, index % 12 + 1, abbreviated = false)
    }
    val basePlan = PlanningMath.plan(debts, 0, PayoffStrategy.AVALANCHE)
    val newPlan = PlanningMath.plan(debts, debtExtra, PayoffStrategy.AVALANCHE)
    PageColumn(verticalSpacing = TandemSpacing.large) {
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            Text("Left over each month", color = palette.muted, fontSize = TandemTypography.body)
            Text(
                state.formatCurrency(leftover),
                color = if (leftover < 0) palette.negative else palette.text,
                fontSize = TandemTypography.heroTitle,
                fontWeight = FontWeight.SemiBold,
                fontFamily = tandemAlloFamily(),
            )
            Text(
                "From ${state.selectedMonth}: ${state.formatCurrency(income)} in, ${state.formatCurrency(spending)} out",
                color = palette.muted,
                fontSize = TandemTypography.compactBody,
            )
        }
        Text(
            "See how earning or spending a little differently moves your debt-free date and savings goals. Nothing here changes your budget.",
            color = palette.muted,
            fontSize = TandemTypography.compactBody,
        )
        val steps = listOf(-50_000L, -25_000L, -10_000L, 0L, 10_000L, 25_000L, 50_000L)

        fun stepLabel(it: Long) =
            if (it ==
                0L
            ) {
                "No change"
            } else {
                (if (it > 0) "+" else "−") + state.formatCurrency(kotlin.math.abs(it)).replace(Regex("[.,]00$"), "")
            }
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            Text("If I earned each month", color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.Medium)
            FilterPills(steps.map { it to stepLabel(it) }, incomeChange, { incomeChange = it })
        }
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            Text("If I spent each month", color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.Medium)
            FilterPills(steps.map { it to stepLabel(it) }, spendingChange, { spendingChange = it })
        }
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            Text("And put what’s left toward", color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.Medium)
            FilterPills(
                listOf(100 to "All to debt", 75 to "Mostly debt", 50 to "Half and half", 25 to "Mostly goals", 0 to "All to goals"),
                toDebt,
                { toDebt = it },
            )
        }
        if (debts.isNotEmpty()) {
            SectionCard("Debt", "${state.formatCurrency(debtExtra)} extra a month") {
                ListRow(
                    "Debt-free",
                    basePlan?.let { "Now: ${monthLabel(it.months)}" } ?: "Now: not with minimums alone",
                    newPlan?.let { monthLabel(it.months) } ?: "Not yet",
                    leading = RowLeading.Icon(Lucide.CreditCard),
                    options = ListRowOptions(showChevron = false),
                )
                if (basePlan != null && newPlan != null && basePlan.totalInterestMinor > newPlan.totalInterestMinor) {
                    DividerLine()
                    ListRow(
                        "Interest saved",
                        null,
                        state.formatCurrency(basePlan.totalInterestMinor - newPlan.totalInterestMinor),
                        leading = RowLeading.None,
                        options = ListRowOptions(showChevron = false),
                    )
                }
            }
        }
        if (goals.isNotEmpty()) {
            val remaining = goals.map { (it.targetMinor - (it.saved(month, liveAccounts) ?: 0)).coerceAtLeast(0) }
            val timeline = GoalTimeline.plan(goals.mapIndexed { i, goal -> GoalInput(goal.id, remaining[i]) }, goalExtra, GoalSplit.EVEN)
            SectionCard("Savings goals", "${state.formatCurrency(goalExtra)} a month shared between them") {
                goals.forEachIndexed { index, goal ->
                    if (index > 0) DividerLine()
                    val months = timeline.goals[index].reachedMonth
                    ListRow(
                        goal.name,
                        "${state.formatCurrency(remaining[index])} to go",
                        when (months) {
                            null -> "Not yet"
                            0 -> "Reached"
                            else -> monthLabel(months)
                        },
                        leading = RowLeading.Icon(Lucide.PiggyBank),
                        options = ListRowOptions(showChevron = false),
                    )
                }
            }
        }
        if (debts.isEmpty() && goals.isEmpty()) {
            EmptyState("Add a debt or a savings goal", "What-if shows how changes move your debt-free date and goal dates.")
        }
    }
}
