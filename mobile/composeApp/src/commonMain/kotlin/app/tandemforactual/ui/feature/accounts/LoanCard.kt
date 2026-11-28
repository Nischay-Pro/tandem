@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.data.planning.AmortisationRow
import app.tandemforactual.data.planning.DebtKind
import app.tandemforactual.data.planning.PlanningMath
import app.tandemforactual.data.planning.SpendingInsights
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.format.reportMonthLabel
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.MoneyAmountInput

/** How much sooner a loan is cleared and how much interest is saved when [faster] pays more each month than [base]. */
internal fun extraPaymentSavings(
    base: List<AmortisationRow>,
    faster: List<AmortisationRow>,
): Pair<Int, Long> = (base.size - faster.size) to (base.sumOf { it.interestMinor } - faster.sumOf { it.interestMinor })

/** The month a schedule of [months] payments starting next month ends, as `yyyy-MM`. */
internal fun payoffMonth(
    today: String,
    months: Int,
): String = SpendingInsights.shiftMonth(today.take(7), months)

/** A loan's payoff from its terms, what an extra monthly payment would save, and its next year of payments. */
@Composable
internal fun LoanCard(
    state: AppState,
    mobileSession: TandemMobileSession,
    account: LiveAccountSummary,
) {
    val terms = mobileSession.planning.debtTerms.firstOrNull { it.accountId == account.id }
    if (terms?.kind != DebtKind.LOAN) return
    val apr = terms.aprBasisPoints ?: return
    val payment = terms.minimumPaymentMinor ?: return
    val owed = -(account.balanceMinor ?: return)
    if (owed <= 0) return
    var extraText by rememberSaveable(account.id) { mutableStateOf("") }
    val extra = state.parseAmountMinorUnits(extraText)?.coerceAtLeast(0) ?: 0L
    val base = remember(owed, apr, payment) { PlanningMath.amortisation(owed, apr, payment) }
    val faster = remember(owed, apr, payment, extra) { PlanningMath.amortisation(owed, apr, payment + extra).takeIf { extra > 0 } }
    val money = { minor: Long -> if (state.privacyModeEnabled) "•••" else state.formatCurrency(minor) }
    var paying by remember(account.id) { mutableStateOf(false) }
    loanPayment(owed, apr, payment, 0)?.let { split ->
        LoanPaymentSheet(paying, account, split, state, mobileSession) { paying = false }
    }
    SectionCard("Loan", detail = "From its APR and monthly payment") {
        if (base == null) {
            BodyText("The monthly payment doesn’t cover the interest, so this loan wouldn’t be paid off.")
            return@SectionCard
        }
        val today = platformTodayIsoDate()
        ListRow(
            "Paid off in ${reportMonthLabel(payoffMonth(today, base.size))}",
            "${base.size} more payments",
            trailing = "${money(base.sumOf { it.interestMinor })} interest",
            options = ListRowOptions(showChevron = false),
        )
        if (!state.readOnlyMode) ListRow("Record a payment", "Principal, interest and escrow", onClick = { paying = true })
        MoneyAmountInput("Extra each month", extraText, state.formatCurrency(0), { extraText = it })
        faster?.let { extraPaymentSavings(base, it) }?.let { (months, interest) ->
            BodyText("Paid off $months months sooner, saving ${money(interest)} in interest.")
        }
        (faster ?: base).take(SCHEDULE_SHOWN).forEach { row ->
            DividerLine()
            ListRow(
                reportMonthLabel(payoffMonth(today, row.month)),
                "${money(row.interestMinor)} interest · ${money(row.principalMinor)} principal",
                trailing = money(row.balanceMinor),
                options = ListRowOptions(showChevron = false),
            )
        }
    }
}

private const val SCHEDULE_SHOWN = 12
