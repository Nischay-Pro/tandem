package app.tandemforactual.data.regions.br.bank.pluggyai

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

/** Pluggy.ai, for banks in Brazil. The server keeps the Pluggy item ids and lists their accounts. */
object PluggyAi : BankProvider() {
    override val source = "pluggyai"
    override val serverPath = "pluggyai"
    override val displayName = "Pluggy.ai"
    override val regions = setOf(Region.BR)
    override val linkMethod = BankLinkMethod.LIST_ACCOUNTS

    override fun transactionsRequest(account: LinkedAccount): JsonObject =
        buildJsonObject {
            put("accountId", account.providerAccountId)
            put("startDate", account.startDate)
        }

    override fun parseAccounts(data: JsonObject) =
        data.accounts { account ->
            BankProviderAccount(
                id = account.text("id").orEmpty(),
                name = account.text("name").orEmpty(),
                institution = displayName,
                bankId = source,
                balance = account.text("balance"),
            )
        }
}
