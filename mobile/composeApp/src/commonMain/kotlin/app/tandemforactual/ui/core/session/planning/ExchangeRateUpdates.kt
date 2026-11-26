@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.session.planning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.tandemforactual.ui.core.format.budgetCurrencyCode
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState

/**
 * Fetches exchange rates while fetching is on: once a day for the open budget, when it opens or syncs, and again when
 * its currencies or the rate server change. Nothing is fetched while it's off.
 */
@Composable
internal fun FetchExchangeRatesWhenOn(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val fetching = state.exchangeRateFetching
    val budgetId = state.selectedBudgetId
    val budgetCurrency = state.budgetCurrencyCode
    val foreign =
        mobileSession.planning.accountCurrencies.values
            .filter { it != budgetCurrency }
            .toSortedSet()
            .joinToString(",")
    val source = LocalExchangeRateSource.current
    LaunchedEffect(fetching.enabled, fetching.server, budgetId, budgetCurrency, foreign, mobileSession.lastSyncSummary) {
        if (!fetching.enabled || budgetId.isBlank() || foreign.isEmpty()) return@LaunchedEffect
        val today = platformTodayIsoDate()
        val historyStart =
            mobileSession.budgetMonthIds
                .filter { MONTH.matches(it) && it <= today.take(7) }
                .minOrNull()
                ?.let { "$it-01" }
        val key = "$budgetId $today $budgetCurrency $foreign ${fetching.server}"
        val result = state.exchangeRateFetching.update(mobileSession.planning, budgetId, budgetCurrency, source, today, historyStart, key)
        state.exchangeRateFetching =
            state.exchangeRateFetching.copy(checked = result.checked, fetchedOn = result.fetchedOn, error = result.error)
    }
}

private val MONTH = Regex("^\\d{4}-\\d{2}$")
