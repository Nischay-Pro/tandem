package app.tandemforactual.data.regions

import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.data.regions.br.bank.pluggyai.PluggyAi
import app.tandemforactual.data.regions.eu.bank.enablebanking.EnableBanking
import app.tandemforactual.data.regions.eu.bank.gocardless.GoCardless
import app.tandemforactual.data.regions.global.bank.BankLinkMethod
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.data.regions.global.bank.BankProviders
import app.tandemforactual.data.regions.nz.bank.akahu.Akahu
import app.tandemforactual.data.regions.us.bank.simplefin.SimpleFin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BankProvidersTest {
    private val account = LinkedAccount("a", "Checking", "x", "PROV-1", "bank-1", "Big Bank", "2026-08-01")

    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun registryFindsProvidersBySourceAndRegion() {
        assertEquals(SimpleFin, BankProviders.forSource("simpleFin"))
        assertEquals(GoCardless, BankProviders.forSource(null))
        assertEquals(GoCardless, BankProviders.forSource("unknown"))
        assertEquals(listOf(SimpleFin), BankProviders.forRegion(Region.US))
        assertEquals(listOf(GoCardless, EnableBanking), BankProviders.forRegion(Region.EU))
        assertTrue(BankProviders.forRegion(Region.IN).isEmpty())
        assertEquals("Everywhere", Region.GLOBAL.displayName)
        assertEquals(
            5,
            BankProviders.all
                .map { it.source }
                .toSet()
                .size,
        )
        BankProviders.all.forEach { provider ->
            assertEquals("${provider.serverPath}/status", provider.statusRoute)
            assertEquals("${provider.serverPath}/accounts", provider.accountsRoute)
            assertEquals("${provider.serverPath}/transactions", provider.transactionsRoute)
            assertTrue(provider.displayName.isNotBlank())
            assertEquals("PROV-1", provider.transactionsRequest(account)["accountId"]!!.toString().trim('"'))
        }
    }

    @Test
    fun providersBuildActualsRequestsAndReadAccounts() {
        assertEquals(
            """{"requisitionId":"bank-1","accountId":"PROV-1","startDate":"2026-08-01","includeBalance":true}""",
            GoCardless.transactionsRequest(account).toString(),
        )
        assertEquals("Big Bank", EnableBanking.transactionsRequest(account)["aspspName"].toString().trim('"'))
        assertTrue(GoCardless.startsFromCurrentBalance)
        assertEquals(false, SimpleFin.startsFromCurrentBalance)
        assertEquals(BankLinkMethod.BROWSER, GoCardless.linkMethod)
        assertEquals(emptyList(), GoCardless.parseAccounts(json("""{"accounts":[{"id":"x"}]}""")))
        assertEquals(
            listOf(
                BankProviderAccount("SF-9", "Card", "Credit Union", "cu.example", "-4.10"),
                BankProviderAccount("SF-10", "Plain", null, "org-1", null),
                BankProviderAccount("", "", null, "", null),
            ),
            SimpleFin.parseAccounts(
                json(
                    """{"accounts":[{"id":"SF-9","name":"Card","balance":"-4.10","org":{"name":"Credit Union","domain":"cu.example"}},""" +
                        """{"id":"SF-10","name":"Plain","org":{"id":"org-1"}},{}]}""",
                ),
            ),
        )
        assertEquals(
            listOf(BankProviderAccount("P-1", "Conta", "Pluggy.ai", "pluggyai", "10.5")),
            PluggyAi.parseAccounts(json("""{"accounts":[{"id":"P-1","name":"Conta","balance":10.5}]}""")),
        )
        assertEquals(
            listOf(
                BankProviderAccount("acc_1", "Everyday", "ANZ", "akahu", null),
                BankProviderAccount("acc_2", "Savings", "Akahu", "akahu", null),
            ),
            Akahu.parseAccounts(
                json("""{"accounts":[{"_id":"acc_1","name":"Everyday","connection":"ANZ"},{"id":"acc_2","name":"Savings"}]}"""),
            ),
        )
        assertEquals(emptyList(), SimpleFin.parseAccounts(json("{}")))
    }
}
