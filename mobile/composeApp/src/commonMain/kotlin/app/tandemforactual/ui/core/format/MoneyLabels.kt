package app.tandemforactual.ui.core.format

import app.tandemforactual.data.planning.ConvertedTotal
import app.tandemforactual.data.planning.convertMinor
import app.tandemforactual.data.planning.convertedTotal
import app.tandemforactual.data.planning.rateBetween
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.formatCurrency

/** The currency the budget itself is kept in. */
internal val AppState.budgetCurrencyCode: String get() = budgetDisplaySettings?.currencyCode ?: platformCurrencyCode()

/** An amount in the account's own currency when it has one, else in the budget's currency. */
internal fun AppState.formatAccountMoney(
    minorUnits: Long,
    currency: String?,
): String =
    if (currency == null || currency == budgetCurrencyCode) {
        formatCurrency(minorUnits)
    } else {
        platformFormatReportCurrency(minorUnits, currency, budgetDisplaySettings?.currencyFractionDigits ?: 2)
    }

/** A converted total, marked with ≈ whenever an exchange rate was used. */
internal fun AppState.formatConverted(total: ConvertedTotal): String =
    if (total.approximate) "≈ ${formatCurrency(total.totalMinor)}" else formatCurrency(total.totalMinor)

/** The total of these accounts in the budget's currency, using the rates the person entered. */
internal fun TandemMobileSession.convertedBalance(
    state: AppState,
    accounts: List<Pair<String, Long?>>,
): ConvertedTotal =
    convertedTotal(
        accounts.map { (id, balance) ->
            (balance ?: 0L) to planning.accountCurrencies[id]
        },
        state.budgetCurrencyCode,
        planning.exchangeRates,
    )

/** "≈ $1,234 in USD" for an account in another currency, or null when it's in the budget's currency or has no rate. */
internal fun TandemMobileSession.approximateInBudgetCurrency(
    state: AppState,
    accountId: String,
    balanceMinor: Long?,
): String? {
    val currency = planning.accountCurrencies[accountId]?.takeIf { it != state.budgetCurrencyCode } ?: return null
    val rate = rateBetween(currency, state.budgetCurrencyCode, planning.exchangeRates) ?: return null
    return "≈ ${state.formatCurrency(convertMinor(balanceMinor ?: 0L, rate))} in ${state.budgetCurrencyCode}"
}

internal fun LiveScheduleSummary.amountLabel(state: AppState): String =
    when {
        amountMinor != null -> state.formatCurrency(amountMinor)
        amountRangeMinor != null -> "${state.formatCurrency(amountRangeMinor.first)}–${state.formatCurrency(amountRangeMinor.second)}"
        else -> "Variable"
    }

/** A whole percentage from basis points, such as 1,250 as “13%”. */
internal fun wholePercent(basisPoints: Int): String = "${(kotlin.math.abs(basisPoints) + 50) / 100}%"
