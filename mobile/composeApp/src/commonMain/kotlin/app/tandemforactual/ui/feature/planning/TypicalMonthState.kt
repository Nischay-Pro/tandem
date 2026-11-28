package app.tandemforactual.ui.feature.planning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.tandemforactual.data.planning.MonthMoney
import app.tandemforactual.data.planning.TypicalMonth
import app.tandemforactual.data.planning.TypicalMonthSource
import app.tandemforactual.data.planning.TypicalMonths
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.incomeCategoryIds
import app.tandemforactual.ui.core.state.isOffBudgetAccount
import app.tandemforactual.ui.core.state.monthMoneyIn
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.rememberBudgetTransactions
import app.tandemforactual.ui.core.state.rememberMonthTransactions

/** The month's income and spending, or a recent average when the month isn't over yet. */
@Composable
internal fun rememberTypicalMonth(
    state: AppState,
    mobileSession: TandemMobileSession,
    month: LiveBudgetMonthSummary,
): TypicalMonth {
    val records = rememberBudgetTransactions(state, mobileSession)
    val transactions = rememberMonthTransactions(state, mobileSession)
    val today = platformTodayIsoDate()
    val chosen =
        MonthMoney(
            monthMoneyIn(state, month, transactions).first.coerceAtLeast(0),
            (-month.groups.filterNot { it.isIncome }.sumOf { it.activityMinor }).coerceAtLeast(0),
        )
    val income = remember(mobileSession.categoryRecords, mobileSession.categoryGroupRecords) { mobileSession.incomeCategoryIds() }
    val offBudget =
        remember(mobileSession.accountRecords) {
            mobileSession.accountRecords.filter { it.recordBody().isOffBudgetAccount() }.mapTo(mutableSetOf()) { it.recordId }
        }
    val startingBalances =
        month.groups
            .filter { it.isIncome }
            .flatMap { it.categories }
            .filter { it.name == "Starting Balances" }
            .mapTo(mutableSetOf()) { it.id }
    return remember(records, state.selectedMonthId, today, chosen, income, startingBalances, offBudget) {
        TypicalMonths.of(records.orEmpty(), state.selectedMonthId, today, chosen, income, startingBalances, offBudget)
    }
}

/** Says where [typical]'s figures come from. */
internal fun typicalMonthLabel(
    typical: TypicalMonth,
    selectedMonth: String,
): String =
    when (typical.source) {
        TypicalMonthSource.CHOSEN_MONTH -> {
            "From $selectedMonth"
        }

        TypicalMonthSource.RECENT_FULL_MONTHS -> {
            if (typical.fullMonths == 1) "From last month" else "Average of the last ${typical.fullMonths} full months"
        }

        TypicalMonthSource.MONTH_SO_FAR -> {
            "$selectedMonth so far"
        }
    }
