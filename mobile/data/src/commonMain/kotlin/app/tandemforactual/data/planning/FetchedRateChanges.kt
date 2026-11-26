package app.tandemforactual.data.planning

/** What to write after fetching rates: the rates to [save] and the older fetched rates to [remove]. */
data class FetchedRateChanges(
    val save: List<ExchangeRate>,
    val remove: List<ExchangeRate>,
)

/**
 * Merges [fetched] rates into the [stored] ones, keeping one fetched rate per pair and month: the month's last, which
 * is the month-end for past months and the newest for this one. A rate the person entered for the same pair and day
 * always wins, and a fetched rate already stored unchanged isn't written again.
 */
fun fetchedRateChanges(
    fetched: List<ExchangeRate>,
    stored: List<ExchangeRate>,
): FetchedRateChanges {
    val entered = stored.filter { !it.fetched }.flatMap { listOf(Triple(it.from, it.to, it.asOf), Triple(it.to, it.from, it.asOf)) }.toSet()
    val storedFetched = stored.filter { it.fetched }
    val kept =
        (storedFetched + fetched.filter { it.fetched && Triple(it.from, it.to, it.asOf) !in entered })
            .groupBy { Triple(it.from, it.to, it.asOf.take(7)) }
            .values
            .map { month -> month.last { it.asOf == month.maxOf(ExchangeRate::asOf) } }
    val keptIds = kept.map { it.id }.toSet()
    return FetchedRateChanges(
        save = kept.filter { it !in storedFetched },
        remove = storedFetched.filter { it.id !in keptIds },
    )
}
