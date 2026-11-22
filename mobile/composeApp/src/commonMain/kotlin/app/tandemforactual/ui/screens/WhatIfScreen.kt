@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.data.planning.DebtInput
import app.tandemforactual.data.planning.DebtKind
import app.tandemforactual.data.planning.PayoffStrategy
import app.tandemforactual.data.planning.PlanningMath
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
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
    var incomeChange by rememberSaveable { mutableFloatStateOf(0f) }
    var spendingChange by rememberSaveable { mutableFloatStateOf(0f) }
    var toDebt by rememberSaveable { mutableFloatStateOf(0.5f) }
    val income = monthMoneyIn(state, month, transactions).first.coerceAtLeast(0)
    val spending = (-month.groups.filterNot { it.isIncome }.sumOf { it.activityMinor }).coerceAtLeast(0)
    val leftover = income + incomeChange.toLong() * 100 - (spending + spendingChange.toLong() * 100)
    val spare = leftover.coerceAtLeast(0)
    val debtExtra = (spare * toDebt).toLong()
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
        SectionCard("Try a change", null) {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                WhatIfSlider("Income each month", incomeChange, -2_000f..2_000f, state) { incomeChange = it }
                WhatIfSlider("Spending each month", spendingChange, -2_000f..2_000f, state) { spendingChange = it }
                Text("Split what’s left", color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.Medium)
                Row {
                    Text("Debt ${(toDebt * 100).toInt()}%", Modifier.weight(1f), color = palette.muted, fontSize = TandemTypography.label)
                    Text("Savings goals ${100 - (toDebt * 100).toInt()}%", color = palette.muted, fontSize = TandemTypography.label)
                }
                Slider(toDebt, { toDebt = (it * 20).toInt() / 20f }, colors = sliderColors())
            }
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
            SectionCard("Savings goals", "${state.formatCurrency(goalExtra)} a month shared between them") {
                goals.forEachIndexed { index, goal ->
                    if (index > 0) DividerLine()
                    val remaining = (goal.targetMinor - (goal.saved(month, liveAccounts) ?: 0)).coerceAtLeast(0)
                    val share = if (goals.isEmpty()) 0 else goalExtra / goals.size
                    val months =
                        if (remaining == 0L) {
                            0
                        } else if (share > 0) {
                            ((remaining + share - 1) / share).toInt()
                        } else {
                            null
                        }
                    ListRow(
                        goal.name,
                        "${state.formatCurrency(remaining)} to go",
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
        Text(
            "Nothing here changes your budget. Debts with an APR and minimum and your savings goals are included.",
            color = palette.muted,
            fontSize = TandemTypography.label,
        )
    }
}

@Composable
private fun WhatIfSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    state: AppState,
    onChange: (Float) -> Unit,
) {
    val palette = tandemPalette
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.Medium)
            Text(
                (if (value > 0) "+" else "") + state.formatCurrency(value.toLong() * 100),
                color = if (value == 0f) palette.muted else palette.accent,
                fontSize = TandemTypography.body,
            )
        }
        Slider(value, { onChange((it / 50).toInt() * 50f) }, valueRange = range, colors = sliderColors())
    }
}

@Composable
private fun sliderColors() =
    SliderDefaults.colors(
        thumbColor = tandemPalette.accent,
        activeTrackColor = tandemPalette.accent,
        inactiveTrackColor = tandemPalette.raised,
    )
