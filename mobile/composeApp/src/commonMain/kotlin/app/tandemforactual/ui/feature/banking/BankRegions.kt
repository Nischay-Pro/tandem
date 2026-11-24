package app.tandemforactual.ui.feature.banking

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.bank.BankProvider

private val CURRENCY_REGIONS =
    mapOf(
        "USD" to Region.US,
        "CAD" to Region.CA,
        "EUR" to Region.EU,
        "GBP" to Region.UK,
        "BRL" to Region.BR,
        "NZD" to Region.NZ,
        "INR" to Region.IN,
    )

/** Where the budget's banks most likely are, from its currency; empty when the currency doesn't say. */
internal fun homeBankRegions(currencyCode: String?): Set<Region> =
    setOfNotNull(CURRENCY_REGIONS[currencyCode?.uppercase()]).let { if (it.isEmpty()) it else it + Region.GLOBAL }

/** Providers serving [home], or already linked, first; the rest. Everything is shown when [home] is unknown. */
internal fun splitBankProviders(
    providers: List<BankProvider>,
    home: Set<Region>,
    linkedSources: Set<String>,
): Pair<List<BankProvider>, List<BankProvider>> =
    if (home.isEmpty()) {
        providers to emptyList()
    } else {
        providers.partition { provider -> provider.source in linkedSources || provider.regions.any { it in home } }
    }
