package app.tandemforactual.data.regions.eu.bank.enablebanking

import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.bank.BankLinkMethod
import app.tandemforactual.data.regions.global.bank.BankProvider
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Enable Banking, for banks across Europe. Linking needs a browser sign-in at the bank. */
object EnableBanking : BankProvider() {
    override val source = "enableBanking"
    override val serverPath = "enablebanking"
    override val displayName = "Enable Banking"
    override val regions = setOf(Region.EU, Region.UK)
    override val linkMethod = BankLinkMethod.BROWSER

    override fun transactionsRequest(account: LinkedAccount): JsonObject =
        buildJsonObject {
            put("accountId", account.providerAccountId)
            put("startDate", account.startDate)
            put("aspspName", account.bankName)
        }
}
