@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.tandemforactual.data.planning.AccountKind
import app.tandemforactual.data.planning.Compounding
import app.tandemforactual.data.planning.DepositMath
import app.tandemforactual.data.planning.DepositTerms
import app.tandemforactual.data.planning.SpendingInsights
import app.tandemforactual.ui.core.format.displayTransactionDate
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.format.reportMonthLabel
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.ui.formatRateBasisPoints
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank
import kotlinx.coroutines.launch

/** Whole months from [today]'s month to the month of [date], at least one. */
internal fun monthsUntil(
    today: String,
    date: String,
): Int {
    val start = today.take(4).toInt() * MONTHS_IN_YEAR + today.substring(5, 7).toInt()
    val end = date.take(4).toInt() * MONTHS_IN_YEAR + date.substring(5, 7).toInt()
    return (end - start).coerceAtLeast(1)
}

/** Every [every]th month's balance from [growth], always ending with the last, as (months ahead, balance). */
internal fun growthCheckpoints(
    growth: List<Long>,
    every: Int,
): List<Pair<Int, Long>> =
    growth
        .mapIndexedNotNull { index, balance -> (index + 1 to balance).takeIf { (index + 1) % every == 0 || index == growth.lastIndex } }

/** A savings account's deposit terms: what it will hold at maturity and how it grows, or a way to add them. */
@Composable
internal fun DepositCard(
    state: AppState,
    mobileSession: TandemMobileSession,
    account: LiveAccountSummary,
) {
    val terms = mobileSession.planning.depositTerms.firstOrNull { it.accountId == account.id }
    var editing by remember(account.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DepositTermsSheet(editing, account, terms, state, onDismiss = { editing = false }) { saved ->
        scope.launch {
            if (saved == null) {
                terms?.let { mobileSession.planning.deleteDepositTerms(state.selectedBudgetId, it.id) }
            } else {
                mobileSession.planning.saveDepositTerms(state.selectedBudgetId, saved)
            }
        }
        editing = false
    }
    if (terms == null) {
        if (account.kind != AccountKind.SAVINGS || state.readOnlyMode) return
        SurfaceCard {
            ListRow(
                "Set up as a deposit",
                "Add its rate and maturity to see how it grows",
                leading = RowLeading.Icon(Lucide.PiggyBank),
                onClick = { editing = true },
            )
        }
        return
    }
    DepositGrowth(state, account, terms) { editing = true }
}

@Composable
private fun DepositGrowth(
    state: AppState,
    account: LiveAccountSummary,
    terms: DepositTerms,
    onEdit: () -> Unit,
) {
    val today = platformTodayIsoDate()
    val months = terms.maturesOn?.let { monthsUntil(today, it) } ?: MONTHS_IN_YEAR
    val balance = account.balanceMinor ?: 0L
    val growth = remember(balance, terms, months) { DepositMath.growth(balance, terms, months) }
    val money = { minor: Long -> if (state.privacyModeEnabled) "•••" else state.formatCurrency(minor) }
    val final = growth.lastOrNull() ?: balance
    val added = terms.monthlyContributionMinor * months
    val compounding = terms.compounding.name.lowercase()
    SectionCard("Deposit", detail = "${formatRateBasisPoints(terms.rateBasisPoints)} a year, compounded $compounding") {
        ListRow(
            terms.maturesOn?.let { "At maturity, ${displayTransactionDate(it)}" } ?: "In a year",
            "${money(final - balance - added)} interest" + if (added > 0) " · ${money(added)} added" else "",
            trailing = money(final),
            options = ListRowOptions(showChevron = false),
        )
        val every = if (months > MONTHS_IN_YEAR * 2) MONTHS_IN_YEAR else QUARTER
        growthCheckpoints(growth, every).takeLast(CHECKPOINTS).forEach { (ahead, value) ->
            DividerLine()
            ListRow(
                reportMonthLabel(SpendingInsights.shiftMonth(today.take(7), ahead)),
                trailing = money(value),
                options = ListRowOptions(showChevron = false),
            )
        }
        DividerLine()
        ListRow("Edit deposit", onClick = onEdit.takeUnless { state.readOnlyMode })
    }
}

internal const val MONTHS_IN_YEAR = 12
private const val QUARTER = 3
private const val CHECKPOINTS = 8

internal val compoundingLabels =
    listOf(Compounding.MONTHLY to "Monthly", Compounding.QUARTERLY to "Quarterly", Compounding.YEARLY to "Yearly")
