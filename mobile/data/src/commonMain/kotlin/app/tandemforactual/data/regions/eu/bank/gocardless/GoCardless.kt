package app.tandemforactual.data.regions.eu.bank.gocardless

import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.bank.BankLinkMethod
import app.tandemforactual.data.regions.global.bank.BankProvider
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** GoCardless Bank Account Data, for banks in the EU and UK. Each link is a requisition signed in through the bank. */
object GoCardless : BankProvider() {
    override val source = "goCardless"
    override val serverPath = "gocardless"
    override val displayName = "GoCardless"
    override val regions = setOf(Region.EU, Region.UK)
    override val linkMethod = BankLinkMethod.BROWSER
    override val startsFromCurrentBalance = true

    override fun transactionsRequest(account: LinkedAccount): JsonObject =
        buildJsonObject {
            put("requisitionId", account.bankId)
            put("accountId", account.providerAccountId)
            put("startDate", account.startDate)
            put("includeBalance", true)
        }
}
