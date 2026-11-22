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

/** The rate that turns one [from] into [to], from a rate entered either way round. */
fun rateBetween(
    from: String,
    to: String,
    rates: List<ExchangeRate>,
): ExchangeRate? {
    if (from == to) return null
    rates.firstOrNull { it.from == from && it.to == to && it.value != null }?.let { return it }
    val inverse = rates.firstOrNull { it.from == to && it.to == from && it.value != null } ?: return null
    return ExchangeRate(from, to, (1.0 / inverse.value!!).toString(), inverse.asOf)
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
        val rate = rateBetween(currency, budgetCurrency, rates)
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
