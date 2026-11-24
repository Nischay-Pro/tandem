package app.tandemforactual.ui.feature.retirement

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.RetirementPlans

private val currencyRegions =
    mapOf(
        "USD" to Region.US,
        "CAD" to Region.CA,
        "GBP" to Region.UK,
        "EUR" to Region.EU,
        "BRL" to Region.BR,
        "NZD" to Region.NZ,
        "INR" to Region.IN,
    )

/** Where you've lived first, then the budget's currency, leaving out countries already picked. */
internal fun suggestedRegions(
    available: List<Region>,
    lived: List<Region>,
    currencyCode: String,
    picked: List<Region>,
): List<Region> =
    (lived + listOfNotNull(currencyRegions[currencyCode.uppercase()]))
        .distinct()
        .filter { it in available && it !in picked }
        .take(MAX_SUGGESTED)

/** Matches a country by name, short code or one of its accounts, so "rrsp" finds Canada. */
internal fun searchRegions(
    available: List<Region>,
    query: String,
): List<Region> {
    val words = query.split(' ').map(::squash).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return available.filter { region ->
        val text = squash(region.displayName + region.name + RetirementPlans.forRegion(region).joinToString("") { it.displayName })
        words.all { it in text }
    }
}

private fun squash(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

private const val MAX_SUGGESTED = 3
