package app.tandemforactual.data.regions.eu.bank.gocardless

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

/** GoCardless Bank Account Data, for banks in the EU and UK. Each link is a requisition signed in through the bank. */
object GoCardless : BrowserBankProvider() {
    override val source = "goCardless"
    override val serverPath = "gocardless"
    override val displayName = "GoCardless"
    override val regions = setOf(Region.EU, Region.UK)
    override val startsFromCurrentBalance = true
    override val countries = EUROPEAN_BANK_COUNTRIES

    override fun transactionsRequest(account: LinkedAccount): JsonObject =
        buildJsonObject {
            put("requisitionId", account.bankId)
            put("accountId", account.providerAccountId)
            put("startDate", account.startDate)
            put("includeBalance", true)
        }

    override fun institutionsRequest(country: String) =
        BankRequest(
            "$serverPath/get-banks",
            buildJsonObject {
                put("country", country)
                put("showDemo", false)
            },
        )

    override fun parseInstitutions(
        data: JsonElement,
        country: String,
    ): List<BankInstitution> =
        (data as? JsonArray).orEmpty().mapNotNull { element ->
            val bank = element as? JsonObject ?: return@mapNotNull null
            BankInstitution(bank.text("id") ?: return@mapNotNull null, bank.text("name").orEmpty(), country)
        }

    override fun startRequest(
        institution: BankInstitution,
        serverUrl: String,
    ) = BankRequest(
        "$serverPath/create-web-token",
        buildJsonObject {
            put("institutionId", institution.id)
            put("accessValidForDays", 90)
        },
    )

    override fun parseStart(response: JsonObject): BrowserLink? {
        val data = response["data"] as? JsonObject ?: return null
        return BrowserLink(data.text("link") ?: return null, data.text("requisitionId") ?: return null)
    }

    override fun statusRequest(handle: String) = BankRequest("$serverPath/get-accounts", buildJsonObject { put("requisitionId", handle) })

    /** The server answers with `requisitionStatus` and no data until the person finishes at the bank. */
    override fun parseStatus(
        response: JsonObject,
        handle: String,
    ): BrowserLinkStatus {
        val data = response["data"] as? JsonObject ?: return BrowserLinkStatus.Pending
        data.text("error_code")?.let { return BrowserLinkStatus.Failed(it) }
        val accounts =
            (data["accounts"] as? JsonArray).orEmpty().mapNotNull { element ->
                val account = element as? JsonObject ?: return@mapNotNull null
                BankProviderAccount(
                    id = account.text("account_id") ?: return@mapNotNull null,
                    name = account.text("name") ?: account.text("official_name").orEmpty(),
                    institution = (account["institution"] as? JsonObject)?.text("name"),
                    bankId = handle,
                    balance = null,
                )
            }
        return BrowserLinkStatus.Linked(accounts)
    }
}
