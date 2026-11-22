package app.tandemforactual.data.regions.us.bank.simplefin

import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.bank.BankLinkMethod
import app.tandemforactual.data.regions.global.bank.BankProvider
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.data.regions.global.bank.accounts
import app.tandemforactual.data.regions.global.bank.text
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** SimpleFIN Bridge, for banks in the United States and Canada. */
object SimpleFin : BankProvider() {
    override val source = "simpleFin"
    override val serverPath = "simplefin"
    override val displayName = "SimpleFIN"
    override val regions = setOf(Region.US, Region.CA)
    override val linkMethod = BankLinkMethod.LIST_ACCOUNTS

    override fun transactionsRequest(account: LinkedAccount): JsonObject =
        buildJsonObject {
            put("accountId", account.providerAccountId)
            put("startDate", account.startDate)
        }

    override fun parseAccounts(data: JsonObject) =
        data.accounts { account ->
            val org = account["org"]?.jsonObject
            BankProviderAccount(
                id = account.text("id").orEmpty(),
                name = account.text("name").orEmpty(),
                institution = org?.text("name"),
                bankId = org?.text("domain") ?: org?.text("id") ?: "",
                balance = account.text("balance"),
            )
        }
}
