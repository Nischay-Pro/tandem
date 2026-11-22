package app.tandemforactual.data.regions.nz.bank.akahu

import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.bank.BankLinkMethod
import app.tandemforactual.data.regions.global.bank.BankProvider
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.data.regions.global.bank.accounts
import app.tandemforactual.data.regions.global.bank.text
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Akahu, for banks in New Zealand. */
object Akahu : BankProvider() {
    override val source = "akahu"
    override val serverPath = "akahu"
    override val displayName = "Akahu"
    override val regions = setOf(Region.NZ)
    override val linkMethod = BankLinkMethod.LIST_ACCOUNTS

    override fun transactionsRequest(account: LinkedAccount): JsonObject =
        buildJsonObject {
            put("accountId", account.providerAccountId)
            put("startDate", account.startDate)
        }

    override fun parseAccounts(data: JsonObject) =
        data.accounts { account ->
            BankProviderAccount(
                id = account.text("_id") ?: account.text("id").orEmpty(),
                name = account.text("name").orEmpty(),
                institution = account.text("connection") ?: displayName,
                bankId = source,
                balance = account.text("balance"),
            )
        }
}
