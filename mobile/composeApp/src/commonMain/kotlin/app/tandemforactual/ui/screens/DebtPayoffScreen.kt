@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.tandemforactual.data.planning.DebtInput
import app.tandemforactual.data.planning.PayoffStrategy
import app.tandemforactual.data.planning.PlanningMath
import app.tandemforactual.ui.components.AreaChart
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.FilterPills
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.model.LiveAccountSummary
import app.tandemforactual.ui.model.LiveBudgetMonthSummary
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Lucide

private val EXTRA_STEPS = listOf(0L, 5_000L, 10_000L, 25_000L, 50_000L, 100_000L)

/** Plans paying off every debt with a known APR and minimum, comparing highest-interest-first with smallest-balance-first. */
@Composable
internal fun DebtPayoffScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    liveAccounts: List<LiveAccountSummary>,
    month: LiveBudgetMonthSummary?,
) {
    val editors = rememberPlanningEditors(state, mobileSession, month)
    var strategy by rememberSaveable { mutableStateOf(PayoffStrategy.AVALANCHE) }
    var extra by rememberSaveable { mutableStateOf(10_000L) }
    var scrub by remember { mutableStateOf<Int?>(null) }
    val debts = liveAccounts.filter { !it.closed && (it.balanceMinor ?: 0) < 0 }
    val terms = mobileSession.debtTerms
    val ready =
        debts.mapNotNull { account ->
            val term = terms.firstOrNull { it.accountId == account.id }
            val apr = term?.aprBasisPoints
            val minimum = term?.minimumPaymentMinor
            if (apr == null || minimum == null) null else DebtInput(account.id, -(account.balanceMinor ?: 0), apr, minimum)
        }
    val missing = debts.filter { account -> ready.none { it.id == account.id } }
    val plan = remember(ready, extra, strategy) { PlanningMath.plan(ready, extra, strategy) }
    val other =
        remember(ready, extra, strategy) {
            PlanningMath.plan(
                ready,
                extra,
                if (strategy ==
                    PayoffStrategy.AVALANCHE
                ) {
                    PayoffStrategy.SNOWBALL
                } else {
                    PayoffStrategy.AVALANCHE
                },
            )
        }
    val minimumsOnly = remember(ready) { PlanningMath.plan(ready, 0, strategy) }
    val today = platformTodayIsoDate()

    fun monthLabel(offset: Int): String {
        val (y, m) = today.split('-').let { it[0].toInt() to it[1].toInt() }
        val index = y * 12 + (m - 1) + offset
        return platformFormatMonth(index / 12, index % 12 + 1, abbreviated = false)
    }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        if (debts.isEmpty()) {
            EmptyState("No debts to plan", "Accounts with a balance owed, like credit cards and loans, appear here.")
            return@PageColumn
        }
        val palette = tandemPalette
        if (ready.isEmpty()) {
            EmptyState("Add your debt details", "Enter each debt’s APR and minimum payment to see when you’ll be debt-free.")
            NeedsDetails(missing) { editors.debtAccount = it }
            return@PageColumn
        }
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            val point = scrub?.let { plan?.balances?.getOrNull(it) }
            Text(
                if (point != null) "Owed after ${monthLabel(scrub!! + 1)}" else "Debt-free by",
                color = palette.muted,
                fontSize = TandemTypography.body,
            )
            Text(
                when {
                    point != null -> state.formatCurrency(point)
                    plan == null -> "Not with these payments"
                    plan.months == 0 -> "Already debt-free"
                    else -> monthLabel(plan.months)
                },
                color = palette.text,
                fontSize = TandemTypography.heroTitle,
                fontWeight = FontWeight.SemiBold,
                fontFamily = tandemAlloFamily(),
            )
            Text(
                when {
                    plan == null -> "The payments don’t cover the interest. Add more each month."
                    else -> "${plan.months} months · ${state.formatCurrency(plan.totalInterestMinor)} interest"
                },
                color = if (plan == null) palette.negative else palette.muted,
                fontSize = TandemTypography.compactBody,
            )
        }
        plan?.let {
            AreaChart(
                listOf(
                    ready.sumOf { d ->
                        d.balanceMinor
                    },
                ) + it.balances,
                "Total owed each month until debt-free",
                selected = scrub,
                onScrub = { i ->
                    scrub =
                        i?.minus(1)?.takeIf { s -> s >= 0 }
                },
            )
        }
        FilterPills(
            listOf(PayoffStrategy.AVALANCHE to "Highest interest first", PayoffStrategy.SNOWBALL to "Smallest balance first"),
            strategy,
            { strategy = it },
        )
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            Text("Extra each month", color = palette.muted, fontSize = TandemTypography.label, fontWeight = FontWeight.SemiBold)
            FilterPills(EXTRA_STEPS.map { it to if (it == 0L) "None" else "+${state.formatCurrency(it)}" }, extra, { extra = it })
        }
        if (plan != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                val saved = minimumsOnly?.let { it.totalInterestMinor - plan.totalInterestMinor }
                StatTile("Interest saved", saved?.let(state::formatCurrency) ?: "—", "vs minimums only", Modifier.weight(1f))
                val compare = other?.let { plan.totalInterestMinor - it.totalInterestMinor }
                StatTile(
                    "Other method",
                    other?.let { "${it.months} months" } ?: "—",
                    compare?.let { if (it <= 0) "costs ${state.formatCurrency(-it)} more" else "saves ${state.formatCurrency(it)}" } ?: "",
                    Modifier.weight(1f),
                )
            }
            SectionCard(
                "Payoff order",
                if (strategy ==
                    PayoffStrategy.AVALANCHE
                ) {
                    "Extra goes to the highest APR"
                } else {
                    "Extra goes to the smallest balance"
                },
            ) {
                plan.order.forEachIndexed { index, id ->
                    if (index > 0) DividerLine()
                    val account = debts.first { it.id == id }
                    val term = terms.first { it.accountId == id }
                    ListRow(
                        "${index + 1}. ${account.name}",
                        "${formatRateBasisPoints(term.aprBasisPoints!!)} APR · ${state.formatCurrency(term.minimumPaymentMinor!!)} minimum",
                        "Paid off ${monthLabel(plan.payoffMonth.getValue(id))}",
                        leading = RowLeading.Icon(Lucide.CreditCard),
                        onClick = { editors.debtAccount = account },
                    )
                }
            }
        }
        NeedsDetails(missing) { editors.debtAccount = it }
        Text(
            "Estimates use the APR and minimums you entered, compounded monthly. Promotional rates and fees aren’t included.",
            color = palette.muted,
            fontSize = TandemTypography.label,
        )
    }
}

@Composable
private fun NeedsDetails(
    missing: List<LiveAccountSummary>,
    onEdit: (LiveAccountSummary) -> Unit,
) {
    if (missing.isEmpty()) return
    SectionCard("Needs details", "Add the APR and minimum payment to include these") {
        missing.forEachIndexed { index, account ->
            if (index > 0) DividerLine()
            ListRow(account.name, "Not in the plan yet", leading = RowLeading.Icon(Lucide.CircleAlert), onClick = { onEdit(account) })
        }
    }
}

@Composable
internal fun StatTile(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    Column(
        modifier
            .clip(RoundedCornerShape(TandemShapes.large))
            .background(palette.surface)
            .padding(TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
    ) {
        Text(label, color = palette.muted, fontSize = TandemTypography.label)
        Text(value, color = palette.text, fontSize = TandemTypography.cardTitle, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(detail, Modifier.fillMaxWidth(), color = palette.muted, fontSize = TandemTypography.caption, maxLines = 2)
    }
}
