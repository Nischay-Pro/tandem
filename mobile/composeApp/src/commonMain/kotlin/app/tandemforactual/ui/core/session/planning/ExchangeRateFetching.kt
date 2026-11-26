package app.tandemforactual.ui.core.session.planning

import androidx.compose.runtime.staticCompositionLocalOf
import app.tandemforactual.api.client.ExchangeRateDay
import app.tandemforactual.api.client.ExchangeRateException
import app.tandemforactual.api.client.ExchangeRateSource
import app.tandemforactual.api.client.FrankfurterRateSource
import app.tandemforactual.data.planning.ExchangeRate
import app.tandemforactual.data.planning.fetchedRateChanges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Opens an exchange-rate source at a server address; blank means Frankfurter’s public server. */
internal val LocalExchangeRateSource = staticCompositionLocalOf<(String) -> ExchangeRateSource> { { FrankfurterRateSource.connect(it) } }

/** Whether Tandem fetches exchange rates, from which server, and how the last attempt went. Saved with the app's settings. */
data class ExchangeRateFetching(
    val enabled: Boolean = false,
    val server: String = "",
    val checked: String = "",
    val fetchedOn: String = "",
    val error: String = "",
) {
    fun toPreferences(): Map<String, String> =
        mapOf(ENABLED to enabled.toString(), SERVER to server, CHECKED to checked, FETCHED_ON to fetchedOn, ERROR to error)

    /**
     * Fetches the rates for [budgetId] unless that already happened under [key]. A failure keeps the stored rates and
     * is reported in plain words; one that may pass soon leaves [key] unchecked so the next sync tries again.
     */
    internal suspend fun update(
        planning: PlanningController,
        budgetId: String,
        budgetCurrency: String,
        source: (String) -> ExchangeRateSource,
        today: String,
        historyStart: String?,
        key: String,
    ): ExchangeRateFetching {
        if (!enabled || checked == key) return this
        return try {
            planning.fetchExchangeRates(budgetId, budgetCurrency, source(server), today, historyStart)
            copy(checked = key, fetchedOn = today, error = "")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val transient = error is ExchangeRateException.Unreachable || error is ExchangeRateException.RateLimited
            copy(checked = if (transient) checked else key, error = failureMessage(error))
        }
    }

    private fun failureMessage(error: Exception): String {
        val name = if (server.isBlank()) "Frankfurter" else "your exchange-rate server"
        val subject = name.replaceFirstChar(Char::uppercase)
        val problem =
            when (error) {
                is ExchangeRateException.Unreachable -> "Couldn’t reach $name."
                is ExchangeRateException.RateLimited -> "$subject is busy right now."
                is ExchangeRateException.UnknownCurrency -> "$subject has no rates for one of your currencies."
                is ExchangeRateException.InvalidAddress -> "The exchange-rate server address isn’t one Tandem can use."
                is ExchangeRateException.BadResponse -> "$subject sent rates Tandem couldn’t read."
                else -> "Tandem couldn’t save the new rates."
            }
        return "$problem Rates stay as they were."
    }

    companion object {
        const val ENABLED = "exchangeRatesEnabled"
        const val SERVER = "exchangeRatesServer"
        const val CHECKED = "exchangeRatesChecked"
        const val FETCHED_ON = "exchangeRatesFetchedOn"
        const val ERROR = "exchangeRatesError"
        const val SOURCE = "frankfurter"

        fun from(preferences: Map<String, String>) =
            ExchangeRateFetching(
                enabled = preferences[ENABLED]?.toBooleanStrictOrNull() ?: false,
                server = preferences[SERVER].orEmpty(),
                checked = preferences[CHECKED].orEmpty(),
                fetchedOn = preferences[FETCHED_ON].orEmpty(),
                error = preferences[ERROR].orEmpty(),
            )
    }
}

/**
 * Fetches today's rates from the budget's currency to each account currency, and the budget's history for a currency
 * with no fetched rates yet. Only month-ends and the newest rate are kept, and a rate entered by hand is never replaced.
 */
internal suspend fun PlanningController.fetchExchangeRates(
    budgetId: String,
    budgetCurrency: String,
    source: ExchangeRateSource,
    today: String,
    historyStart: String?,
) {
    val store = planningStore(budgetId) ?: return
    val foreign =
        store
            .accountCurrencies()
            .first()
            .map { it.currency }
            .filter { it.isNotBlank() && it != budgetCurrency }
            .toSet()
    if (foreign.isEmpty()) return
    val stored = store.exchangeRates().first()
    val unfetched = foreign.filter { code -> stored.none { it.fetched && it.from == budgetCurrency && it.to == code } }.toSet()
    val history =
        if (unfetched.isNotEmpty() && historyStart != null && historyStart < today) {
            source.range(budgetCurrency, unfetched, historyStart, today)
        } else {
            emptyList()
        }
    val latest = source.latest(budgetCurrency, foreign)
    val changes = fetchedRateChanges((history + latest).flatMap { it.toRates() }, stored)
    changes.remove.forEach { store.deleteExchangeRate(it) }
    changes.save.forEach { store.save(it) }
}

private fun ExchangeRateDay.toRates() =
    rates.map { (code, rate) -> ExchangeRate(base, code, rate, date, source = ExchangeRateFetching.SOURCE) }
