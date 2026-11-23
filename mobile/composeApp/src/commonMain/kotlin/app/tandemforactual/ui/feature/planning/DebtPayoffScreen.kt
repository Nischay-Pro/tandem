@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.planning
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.data.planning.DebtInput
import app.tandemforactual.data.planning.DebtKind
import app.tandemforactual.data.planning.DebtPlan
import app.tandemforactual.data.planning.DebtTerms
import app.tandemforactual.data.planning.PayoffStrategy
import app.tandemforactual.data.planning.PlanningMath
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.monthLabel
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.ui.formatRateBasisPoints
import app.tandemforactual.ui.core.ui.rememberPlanningEditors
import app.tandemforactual.ui.core.ui.saved
import app.tandemforactual.ui.design.cards.StatTile
import app.tandemforactual.ui.design.charts.AreaChart
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
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
    val terms = mobileSession.planning.debtTerms

    fun termsFor(account: LiveAccountSummary) = terms.firstOrNull { it.accountId == account.id }
    val debts = liveAccounts.filter { !it.closed && (it.balanceMinor ?: 0) < 0 && termsFor(it)?.kind != DebtKind.NOT_DEBT }
    val paidInFull = debts.filter { termsFor(it)?.paysInFull == true }
    val ready =
        debts.filterNot { it in paidInFull }.mapNotNull { account ->
            val term = termsFor(account)
            val apr = term?.aprBasisPoints
            val minimum = term?.minimumPaymentMinor
            if (apr == null || minimum == null) null else DebtInput(account.id, -(account.balanceMinor ?: 0), apr, minimum)
        }
    val plan = remember(ready, extra, strategy) { PlanningMath.plan(ready, extra, strategy) }
    val other =
        remember(ready, extra, strategy) {
            PlanningMath.plan(ready, extra, if (strategy == PayoffStrategy.AVALANCHE) PayoffStrategy.SNOWBALL else PayoffStrategy.AVALANCHE)
        }
    val minimumsOnly = remember(ready) { PlanningMath.plan(ready, 0, strategy) }
    val today = platformTodayIsoDate()

    PageColumn(verticalSpacing = TandemSpacing.large) {
        if (debts.isEmpty()) {
            EmptyStateCard("No debts to plan", "Accounts with a balance owed, like credit cards and loans, appear here.")
            return@PageColumn
        }
        val palette = tandemPalette
        if (ready.isEmpty()) {
            EmptyStateCard("Add your debt details", "Enter each debt’s APR and minimum payment to see when you’ll be debt-free.")
            YourDebts(debts, ::termsFor, state) { editors.debtAccount = it }
            return@PageColumn
        }
        DebtPayoffSummary(state, plan, scrub, { monthLabel(today, it) })
        plan?.let { DebtPayoffChart(ready, it, scrub) { i -> scrub = i } }
        DebtPayoffControls(state, strategy, extra, { strategy = it }, { extra = it })
        if (plan != null) {
            DebtPayoffComparison(state, plan, other, minimumsOnly, ready.size, strategy)
            PayoffOrderCard(state, plan, strategy, debts, terms, { monthLabel(today, it) }) { editors.debtAccount = it }
        }
        YourDebts(debts, ::termsFor, state) { editors.debtAccount = it }
        Text(
            "Estimates use the APR and minimums you entered, compounded monthly. Promotional rates and fees aren’t included.",
            color = palette.muted,
            fontSize = TandemTypography.label,
        )
    }
}

/** Total owed each month until every debt is paid off, scrubbable to a month. */
@Composable
private fun DebtPayoffChart(
    ready: List<DebtInput>,
    plan: DebtPlan,
    scrub: Int?,
    onScrub: (Int?) -> Unit,
) {
    AreaChart(
        listOf(ready.sumOf { d -> d.balanceMinor }) + plan.balances,
        "Total owed each month until debt-free",
        selected = scrub,
        onScrub = { i -> onScrub(i?.minus(1)?.takeIf { s -> s >= 0 }) },
    )
}

/** The payoff strategy and the extra amount paid each month. */
@Composable
private fun DebtPayoffControls(
    state: AppState,
    strategy: PayoffStrategy,
    extra: Long,
    onStrategyChange: (PayoffStrategy) -> Unit,
    onExtraChange: (Long) -> Unit,
) {
    val palette = tandemPalette
    FilterPills(
        listOf(PayoffStrategy.AVALANCHE to "Highest interest first", PayoffStrategy.SNOWBALL to "Smallest balance first"),
        strategy,
        onStrategyChange,
    )
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Text("Extra each month", color = palette.muted, fontSize = TandemTypography.label, fontWeight = FontWeight.SemiBold)
        FilterPills(EXTRA_STEPS.map { it to if (it == 0L) "None" else "+${state.formatCurrency(it)}" }, extra, onExtraChange)
    }
}

/** When every debt is paid off, or what is owed at the scrubbed month, with the interest it costs. */
@Composable
private fun DebtPayoffSummary(
    state: AppState,
    plan: DebtPlan?,
    scrub: Int?,
    monthLabel: (Int) -> String,
) {
    val palette = tandemPalette
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
}

/** Interest saved against paying minimums only, and how the other strategy compares. */
@Composable
private fun DebtPayoffComparison(
    state: AppState,
    plan: DebtPlan,
    other: DebtPlan?,
    minimumsOnly: DebtPlan?,
    debtCount: Int,
    strategy: PayoffStrategy,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        val saved = minimumsOnly?.let { it.totalInterestMinor - plan.totalInterestMinor }
        StatTile(
            if (saved != null) "Interest saved" else "Minimums alone",
            saved?.let(state::formatCurrency) ?: "Never paid off",
            if (saved != null) "vs minimums only" else "They don’t cover the interest",
            Modifier.weight(1f),
        )
        if (debtCount > 1) {
            val compare = other?.let { plan.totalInterestMinor - it.totalInterestMinor }
            StatTile(
                if (strategy == PayoffStrategy.AVALANCHE) "Smallest first" else "Highest APR first",
                other?.let { "${it.months} months" } ?: "—",
                compare?.let { if (it <= 0) "costs ${state.formatCurrency(-it)} more" else "saves ${state.formatCurrency(it)}" }
                    ?: "",
                Modifier.weight(1f),
            )
        }
    }
}

/** The order debts are paid off in, with each one's rate, minimum and payoff month. */
@Composable
private fun PayoffOrderCard(
    state: AppState,
    plan: DebtPlan,
    strategy: PayoffStrategy,
    debts: List<LiveAccountSummary>,
    terms: List<DebtTerms>,
    monthLabel: (Int) -> String,
    onEdit: (LiveAccountSummary) -> Unit,
) {
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
                onClick = { onEdit(account) },
            )
        }
    }
}

/** Every debt with its rate and minimum in view, tap to edit, and what paying only the minimum on a card costs. */
@Composable
private fun YourDebts(
    debts: List<LiveAccountSummary>,
    termsFor: (LiveAccountSummary) -> DebtTerms?,
    state: AppState,
    onEdit: (LiveAccountSummary) -> Unit,
) {
    SectionCard("Your debts", "Tap one to set its type, APR and minimum") {
        debts.forEachIndexed { index, account ->
            if (index > 0) DividerLine()
            val terms = termsFor(account)
            val owed = -(account.balanceMinor ?: 0)
            val apr = terms?.aprBasisPoints
            val minimum = terms?.minimumPaymentMinor
            val kind =
                when (terms?.kind) {
                    DebtKind.CARD -> "Card"
                    DebtKind.LOAN -> "Loan"
                    else -> null
                }
            val detail =
                when {
                    terms?.paysInFull == true -> "Paid in full each month · no interest"
                    apr != null && minimum != null -> "${formatRateBasisPoints(apr)} APR · ${state.formatCurrency(minimum)} minimum"
                    apr != null -> "${formatRateBasisPoints(apr)} APR · add the minimum"
                    else -> "Add the APR and minimum payment"
                }
            ListRow(
                account.name,
                listOfNotNull(
                    kind,
                    detail,
                ).joinToString(" · "),
                state.formatCurrency(owed),
                leading = RowLeading.Icon(Lucide.CreditCard),
                onClick = {
                    onEdit(account)
                },
            )
            if (terms?.kind == DebtKind.CARD && terms.paysInFull.not() && apr != null && minimum != null) {
                val estimate = PlanningMath.payoff(owed, apr, minimum)
                Text(
                    estimate?.let {
                        "Minimum only: ${it.months} months and ${state.formatCurrency(
                            it.totalInterestMinor,
                        )} in interest. Paying the full statement each month costs no interest."
                    } ?: "The minimum doesn’t cover the interest. Paying more than the minimum is the only way down.",
                    Modifier.padding(start = TandemSpacing.large, end = TandemSpacing.large, bottom = TandemSpacing.medium),
                    color = if (estimate == null) tandemPalette.negative else tandemPalette.muted,
                    fontSize = TandemTypography.label,
                )
            }
        }
    }
}
