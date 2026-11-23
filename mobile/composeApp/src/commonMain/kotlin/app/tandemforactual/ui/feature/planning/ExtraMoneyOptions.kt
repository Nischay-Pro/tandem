@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.planning

import androidx.compose.runtime.Composable
import app.tandemforactual.data.planning.ExtraMoneyComparison
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import com.composables.icons.lucide.Briefcase
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.TrendingDown

/** Each place the extra could go, with its trade-off, or a way to add the details it needs. */
@Composable
internal fun ExtraMoneyOptions(
    comparison: ExtraMoneyComparison,
    money: (Long) -> String,
    open: (AppRoute) -> Unit,
) {
    val extra = money(comparison.extraMinor)
    SectionCard("Where it could go", "Side by side, not ranked. Your situation decides.") {
        val match = comparison.matchGainMinor
        ListRow(
            title = "Employer match",
            subtitle =
                when {
                    match == null -> "Add your 401(k) match to see what you’d gain."
                    match > 0 -> "${money(match)} more from your employer this year. Check when it vests."
                    else -> "You’re already on track for the full match."
                },
            leading = RowLeading.Icon(Lucide.Briefcase),
            onClick = if (match == null) ({ open(AppRoute.RETIREMENT) }) else null,
        )
        DividerLine()
        val debt = comparison.debt
        val saved = debt?.interestSavedMinor
        ListRow(
            title = "Highest-interest debt",
            subtitle =
                when {
                    debt == null -> "Add APRs and minimum payments to compare."
                    saved == null -> "Clears your debt in ${months(
                        debt.monthsWithExtra,
                    )}. Minimum payments alone never would."
                    debt.monthsSooner == 0 -> "Saves ${money(saved)} in interest."
                    else -> "Saves ${money(saved)} in interest and clears debt ${months(debt.monthsSooner ?: 0)} sooner."
                },
            leading = RowLeading.Icon(Lucide.TrendingDown),
            onClick = if (debt == null) ({ open(AppRoute.DEBT_PAYOFF) }) else null,
        )
        DividerLine()
        val buffer = comparison.buffer
        ListRow(
            title = "Cash cushion",
            subtitle =
                buffer?.let {
                    "Covers ${tenths(it.tenthsNow)} months of spending now, ${tenths(it.tenthsAfterYear)} after a year of $extra a month."
                } ?: "Record a month of spending to compare.",
            leading = RowLeading.Icon(Lucide.PiggyBank),
        )
        DividerLine()
        val goal = comparison.goal
        ListRow(
            title = "Savings goal",
            subtitle =
                goal?.let {
                    "${it.goal.name} is fully funded in ${months(
                        it.months,
                    )} with this alone."
                } ?: "Add a savings goal to compare.",
            leading = RowLeading.Icon(Lucide.Flag),
            onClick = if (goal == null) ({ open(AppRoute.GOAL_TIMELINE) }) else null,
        )
        DividerLine()
        ListRow(
            title = "Before you decide",
            subtitle = "Tandem doesn’t move money or give tax advice. Check fees, vesting and eligibility first.",
            leading = RowLeading.Icon(Lucide.Info),
        )
    }
}

private fun months(count: Int) = if (count == 1) "1 month" else "${formatInteger(count)} months"

private fun tenths(value: Long) = if (value % 10 == 0L) "${value / 10}" else "${value / 10}.${value % 10}"
