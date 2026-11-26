package app.tandemforactual.data.planning

import kotlin.math.roundToLong

/**
 * A total in the budget's currency built from balances in several currencies. It's a reporting view only: Actual's own
 * balances are never changed. [approximate] is true whenever any balance went through an exchange rate.
 */
data class ConvertedTotal(
    val totalMinor: Long,
    val approximate: Boolean,
    /** Currencies with no usable rate; their balances are left out of [totalMinor]. */
    val missingRates: Set<String>,
    /** The oldest rate date used, so the person can tell how current the total is. */
    val oldestRateDate: String?,
)

/**
 * The rate that turns one [from] into [to], from rates entered either way round. With [onOrBefore], it's the latest
 * entry dated on or before that day, or the earliest entry when none is that old; without it, the newest entry.
 */
fun rateBetween(
    from: String,
    to: String,
    rates: List<ExchangeRate>,
    onOrBefore: String? = null,
): ExchangeRate? {
    if (from == to) return null
    val history = rateHistory(from, to, rates)
    return history.lastOrNull { onOrBefore == null || it.asOf <= onOrBefore } ?: history.firstOrNull()
}

/** Every usable entry for one pair as a [from] to [to] rate, oldest first; a direct entry wins over an inverse one on the same day. */
fun rateHistory(
    from: String,
    to: String,
    rates: List<ExchangeRate>,
): List<ExchangeRate> {
    val direct = rates.filter { it.from == from && it.to == to && it.value != null }
    val inverse =
        rates
            .filter { it.from == to && it.to == from && it.value != null }
            .map { ExchangeRate(from, to, (1.0 / it.value!!).toString(), it.asOf) }
    return (inverse.map { it to 0 } + direct.map { it to 1 })
        .sortedWith(compareBy({ it.first.asOf }, { it.second }))
        .map { it.first }
}

fun convertMinor(
    amountMinor: Long,
    rate: ExchangeRate,
): Long = (amountMinor * rate.value!!).roundToLong()

/** Adds up balances given as (amount, currency or null for the budget's own currency) in [budgetCurrency]. */
fun convertedTotal(
    balances: List<Pair<Long, String?>>,
    budgetCurrency: String,
    rates: List<ExchangeRate>,
    onOrBefore: String? = null,
): ConvertedTotal {
    var total = 0L
    var approximate = false
    val missing = mutableSetOf<String>()
    val dates = mutableListOf<String>()
    for ((amount, currency) in balances) {
        if (currency == null || currency == budgetCurrency) {
            total += amount
            continue
        }
        val rate = rateBetween(currency, budgetCurrency, rates, onOrBefore)
        if (rate == null) {
            missing += currency
            continue
        }
        total += convertMinor(amount, rate)
        approximate = true
        dates += rate.asOf
    }
    return ConvertedTotal(total, approximate, missing, dates.minOrNull())
}
