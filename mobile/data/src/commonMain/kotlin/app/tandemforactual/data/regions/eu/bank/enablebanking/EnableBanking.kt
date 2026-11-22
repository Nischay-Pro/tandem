package app.tandemforactual.data.regions.eu.bank.enablebanking

import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.bank.BankInstitution
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.data.regions.global.bank.BankRequest
import app.tandemforactual.data.regions.global.bank.BrowserBankProvider
import app.tandemforactual.data.regions.global.bank.BrowserLink
import app.tandemforactual.data.regions.global.bank.BrowserLinkStatus
import app.tandemforactual.data.regions.global.bank.EUROPEAN_BANK_COUNTRIES
import app.tandemforactual.data.regions.global.bank.text
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Enable Banking, for banks across Europe. Linking needs a browser sign-in at the bank. */
object EnableBanking : BrowserBankProvider() {
    override val source = "enableBanking"
    override val serverPath = "enablebanking"
    override val displayName = "Enable Banking"
    override val regions = setOf(Region.EU, Region.UK)
    override val countries = EUROPEAN_BANK_COUNTRIES

    override fun transactionsRequest(account: LinkedAccount): JsonObject =
        buildJsonObject {
            put("accountId", account.providerAccountId)
            put("startDate", account.startDate)
            put("aspspName", account.bankName)
        }

    override fun institutionsRequest(country: String) = BankRequest("$serverPath/aspsps", buildJsonObject { put("country", country) })

    override fun parseInstitutions(
        data: JsonElement,
        country: String,
    ): List<BankInstitution> =
        ((data as? JsonObject)?.get("aspsps") as? JsonArray).orEmpty().mapNotNull { element ->
            val bank = element as? JsonObject ?: return@mapNotNull null
            val name = bank.text("name") ?: return@mapNotNull null
            BankInstitution(name, name, bank.text("country") ?: country)
        }

    /** The bank returns to the server's own callback, which finishes the session for the waiting app. */
    override fun startRequest(
        institution: BankInstitution,
        serverUrl: String,
    ) = BankRequest(
        "$serverPath/start-auth",
        buildJsonObject {
            putJsonObject("aspsp") {
                put("name", institution.id)
                put("country", institution.country)
            }
            put("redirectUrl", "${serverUrl.trimEnd('/')}/$serverPath/auth_callback")
            put("psuType", "personal")
        },
    )

    override fun parseStart(response: JsonObject): BrowserLink? {
        val data = response["data"] as? JsonObject ?: return null
        return BrowserLink(data.text("url") ?: return null, data.text("state") ?: return null)
    }

    override fun statusRequest(handle: String) = BankRequest("$serverPath/poll-auth", buildJsonObject { put("state", handle) })

    /** The server holds the request until the bank calls back; a timeout or a superseded wait just means ask again. */
    override fun parseStatus(
        response: JsonObject,
        handle: String,
    ): BrowserLinkStatus {
        val data = response["data"] as? JsonObject ?: return BrowserLinkStatus.Pending
        data.text("error")?.let { error ->
            return if (error in RETRYABLE) BrowserLinkStatus.Pending else BrowserLinkStatus.Failed(error)
        }
        data.text("error_code")?.let { return BrowserLinkStatus.Failed(it) }
        val accounts =
            (data["accounts"] as? JsonArray).orEmpty().mapNotNull { element ->
                val account = element as? JsonObject ?: return@mapNotNull null
                val id = account.text("account_id") ?: return@mapNotNull null
                BankProviderAccount(
                    id = id,
                    name = account.text("name") ?: id,
                    institution = account.text("institution"),
                    bankId = id,
                    balance = account.text("balance"),
                )
            }
        return BrowserLinkStatus.Linked(accounts)
    }

    private val RETRYABLE = setOf("Polling timed out", "Poll superseded", "Client disconnected")
}
