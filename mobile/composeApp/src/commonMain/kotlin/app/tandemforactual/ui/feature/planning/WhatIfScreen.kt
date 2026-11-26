@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.planning
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.data.planning.DebtInput
import app.tandemforactual.data.planning.DebtKind
import app.tandemforactual.data.planning.DebtPlan
import app.tandemforactual.data.planning.DebtTerms
import app.tandemforactual.data.planning.GoalInput
import app.tandemforactual.data.planning.GoalSplit
import app.tandemforactual.data.planning.GoalTimeline
import app.tandemforactual.data.planning.PayoffStrategy
import app.tandemforactual.data.planning.PlanningMath
import app.tandemforactual.data.planning.SavingsGoal
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.monthLabel
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.monthMoneyIn
import app.tandemforactual.ui.core.state.rememberMonthTransactions
import app.tandemforactual.ui.core.ui.saved
import app.tandemforactual.ui.design.cards.IntroCard
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
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
) {
    val palette = tandemPalette
    val transactions = rememberMonthTransactions(state, mobileSession)
    if (month == null) {
        PageColumn { EmptyStateCard("Open a budget month first", "What-if uses this month’s income and spending as the starting point.") }
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
    val debts = whatIfDebts(liveAccounts, mobileSession.planning.debtTerms)
    val goals = mobileSession.planning.savingsGoals
    val today = platformTodayIsoDate()

    val basePlan = PlanningMath.plan(debts, 0, PayoffStrategy.AVALANCHE)
    val newPlan = PlanningMath.plan(debts, debtExtra, PayoffStrategy.AVALANCHE)
    PageColumn(verticalSpacing = TandemSpacing.large) {
        IntroCard(
            "See how earning or spending a little differently moves your debt-free date and savings goals. Nothing here changes your budget.",
        )
        WhatIfSummary(state, leftover, income, spending)
        WhatIfControls(
            state = state,
            steps =
                WhatIfSteps.steps(income, spending, state.budgetDisplaySettings?.currencyCode),
            incomeChange = incomeChange,
            spendingChange = spendingChange,
            toDebt = toDebt,
            onIncomeChange = { incomeChange = it },
            onSpendingChange = { spendingChange = it },
            onToDebtChange = { toDebt = it },
        )
        if (debts.isNotEmpty()) WhatIfDebtCard(state, debtExtra, basePlan, newPlan, { monthLabel(today, it) })
        if (goals.isNotEmpty()) WhatIfGoalsCard(state, goals, goalExtra, { it.saved(month, liveAccounts) }, { monthLabel(today, it) })
        if (debts.isEmpty() && goals.isEmpty()) {
            EmptyStateCard("Add a debt or a savings goal", "What-if shows how changes move your debt-free date and goal dates.")
        }
    }
}

internal fun whatIfDebts(
    liveAccounts: List<LiveAccountSummary>,
    debtTerms: List<DebtTerms>,
): List<DebtInput> =
    liveAccounts.mapNotNull { account ->
        val terms = debtTerms.firstOrNull { it.accountId == account.id }
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

/** What is left over each month after the changes, and this month's money in and out. */
@Composable
private fun WhatIfSummary(
    state: AppState,
    leftover: Long,
    income: Long,
    spending: Long,
) {
    val palette = tandemPalette
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
}

/** The income, spending and debt-or-goals split choices. */
@Composable
private fun WhatIfControls(
    state: AppState,
    steps: List<Long>,
    incomeChange: Long,
    spendingChange: Long,
    toDebt: Int,
    onIncomeChange: (Long) -> Unit,
    onSpendingChange: (Long) -> Unit,
    onToDebtChange: (Int) -> Unit,
) {
    val palette = tandemPalette
    var custom by remember { mutableStateOf<Pair<WhatIfRow, Boolean>?>(null) }
    WhatIfStepRow("If I earned each month", state, steps, incomeChange, onIncomeChange) { custom = WhatIfRow.INCOME to it }
    WhatIfStepRow("If I spent each month", state, steps, spendingChange, onSpendingChange) { custom = WhatIfRow.SPENDING to it }
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Text("And put what’s left toward", color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.Medium)
        FilterPills(
            listOf(100 to "All to debt", 75 to "Mostly debt", 50 to "Half and half", 25 to "Mostly goals", 0 to "All to goals"),
            toDebt,
            onToDebtChange,
        )
    }
    WhatIfAmountDialog(state, custom?.second, onDismiss = { custom = null }) { amount ->
        when (custom?.first) {
            WhatIfRow.INCOME -> onIncomeChange(amount)
            WhatIfRow.SPENDING -> onSpendingChange(amount)
            null -> Unit
        }
        custom = null
    }
}

/** The debt-free date now and with the extra payment, plus the interest saved. */
@Composable
private fun WhatIfDebtCard(
    state: AppState,
    debtExtra: Long,
    basePlan: DebtPlan?,
    newPlan: DebtPlan?,
    monthLabel: (Int) -> String,
) {
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

/** When each savings goal is reached with the leftover shared between them. */
@Composable
private fun WhatIfGoalsCard(
    state: AppState,
    goals: List<SavingsGoal>,
    goalExtra: Long,
    saved: (SavingsGoal) -> Long?,
    monthLabel: (Int) -> String,
) {
    val remaining = goals.map { (it.targetMinor - (saved(it) ?: 0)).coerceAtLeast(0) }
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
