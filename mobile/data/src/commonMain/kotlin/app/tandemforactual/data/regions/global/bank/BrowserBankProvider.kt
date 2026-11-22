package app.tandemforactual.data.regions.global.bank

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A bank a browser-link provider can connect to. */
data class BankInstitution(
    val id: String,
    val name: String,
    val country: String,
)

/** A POST to the Actual server: its route under the server address, and its JSON body. */
data class BankRequest(
    val route: String,
    val body: JsonObject,
)

/** A started bank sign-in: the page to open, and the handle that asks the server whether it finished. */
data class BrowserLink(
    val url: String,
    val handle: String,
)

/** Where a bank sign-in stands when the server is asked. */
sealed class BrowserLinkStatus {
    data object Pending : BrowserLinkStatus()

    data class Linked(
        val accounts: List<BankProviderAccount>,
    ) : BrowserLinkStatus()

    data class Failed(
        val reason: String,
    ) : BrowserLinkStatus()
}

/**
 * A provider linked by signing in at the bank in a browser, through the same Actual server routes Actual's web app
 * uses: list a country's banks, start a sign-in that returns a bank page, then ask until the sign-in finishes.
 */
abstract class BrowserBankProvider : BankProvider() {
    final override val linkMethod = BankLinkMethod.BROWSER

    /** Two-letter country codes the provider serves, in the order to offer them. */
    abstract val countries: List<String>

    abstract fun institutionsRequest(country: String): BankRequest

    abstract fun parseInstitutions(
        data: JsonElement,
        country: String,
    ): List<BankInstitution>

    /** [serverUrl] is where the bank sends the person back, so the server can finish the link. */
    abstract fun startRequest(
        institution: BankInstitution,
        serverUrl: String,
    ): BankRequest

    abstract fun parseStart(response: JsonObject): BrowserLink?

    abstract fun statusRequest(handle: String): BankRequest

    abstract fun parseStatus(
        response: JsonObject,
        handle: String,
    ): BrowserLinkStatus
}

/** Countries GoCardless and Enable Banking both serve: the EU, EEA and the UK. */
internal val EUROPEAN_BANK_COUNTRIES =
    listOf(
        "GB",
        "DE",
        "FR",
        "ES",
        "IT",
        "NL",
        "BE",
        "IE",
        "AT",
        "PT",
        "FI",
        "SE",
        "DK",
        "NO",
        "PL",
        "CZ",
        "SK",
        "SI",
        "HR",
        "HU",
        "RO",
        "BG",
        "GR",
        "CY",
        "MT",
        "LU",
        "EE",
        "LV",
        "LT",
        "IS",
        "LI",
    )
