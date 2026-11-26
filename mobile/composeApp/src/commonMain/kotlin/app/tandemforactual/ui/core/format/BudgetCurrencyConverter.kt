package app.tandemforactual.ui.core.format

import app.tandemforactual.data.planning.ConvertedTotal
import app.tandemforactual.data.planning.ExchangeRate
import app.tandemforactual.data.planning.convertMinor
import app.tandemforactual.data.planning.convertedTotal
import app.tandemforactual.data.planning.rateBetween
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState

/** Turns account balances into the budget's currency with the rates the person entered. */
internal data class BudgetCurrencyConverter(
    val accountCurrencies: Map<String, String>,
    val budgetCurrency: String,
    val rates: List<ExchangeRate>,
) {
    /** The amount in the budget's currency, or null when the account's currency has no rate. */
    fun convert(
        accountId: String,
        amountMinor: Long,
    ): Long? {
        val currency = accountCurrencies[accountId]?.takeIf { it != budgetCurrency } ?: return amountMinor
        return rateBetween(currency, budgetCurrency, rates)?.let { convertMinor(amountMinor, it) }
    }

    fun total(accounts: List<Pair<String, Long?>>): ConvertedTotal =
        convertedTotal(accounts.map { (id, balance) -> (balance ?: 0L) to accountCurrencies[id] }, budgetCurrency, rates)
}

internal fun TandemMobileSession.budgetCurrencyConverter(state: AppState): BudgetCurrencyConverter =
    BudgetCurrencyConverter(planning.accountCurrencies, state.budgetCurrencyCode, planning.exchangeRates)
