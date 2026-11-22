package app.tandemforactual.data.regions

import app.tandemforactual.data.regions.eu.bank.enablebanking.EnableBanking
import app.tandemforactual.data.regions.eu.bank.gocardless.GoCardless
import app.tandemforactual.data.regions.global.bank.BankInstitution
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.data.regions.global.bank.BrowserLink
import app.tandemforactual.data.regions.global.bank.BrowserLinkStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserBankProvidersTest {
    private fun json(text: String) = Json.parseToJsonElement(text)

    private fun obj(text: String) = json(text).jsonObject

    private val bank = BankInstitution("BANK_X", "Bank X", "GB")

    @Test
    fun goCardlessFollowsActualsRequisitionFlow() {
        assertTrue("GB" in GoCardless.countries)
        assertEquals("gocardless/get-banks", GoCardless.institutionsRequest("GB").route)
        assertEquals("""{"country":"GB","showDemo":false}""", GoCardless.institutionsRequest("GB").body.toString())
        assertEquals(
            listOf(bank),
            GoCardless.parseInstitutions(json("""[{"id":"BANK_X","name":"Bank X"},{"name":"No id"},"x"]"""), "GB"),
        )
        assertEquals(BankInstitution("B", "", "DE"), GoCardless.parseInstitutions(json("""[{"id":"B"}]"""), "DE").single())
        assertEquals(emptyList(), GoCardless.parseInstitutions(json("{}"), "GB"))
        val start = GoCardless.startRequest(bank, "https://budget.example")
        assertEquals("gocardless/create-web-token", start.route)
        assertEquals("""{"institutionId":"BANK_X","accessValidForDays":90}""", start.body.toString())
        assertEquals(
            BrowserLink("https://ob.gocardless.com/x", "req-1"),
            GoCardless.parseStart(obj("""{"data":{"link":"https://ob.gocardless.com/x","requisitionId":"req-1"}}""")),
        )
        assertNull(GoCardless.parseStart(obj("""{"status":"ok"}""")))
        assertNull(GoCardless.parseStart(obj("""{"data":{"requisitionId":"r"}}""")))
        assertNull(GoCardless.parseStart(obj("""{"data":{"link":"l"}}""")))
        assertEquals("""{"requisitionId":"req-1"}""", GoCardless.statusRequest("req-1").body.toString())
        assertEquals("gocardless/get-accounts", GoCardless.statusRequest("req-1").route)
        assertEquals(BrowserLinkStatus.Pending, GoCardless.parseStatus(obj("""{"status":"ok","requisitionStatus":"CR"}"""), "req-1"))
        assertEquals(
            BrowserLinkStatus.Failed("ACCESS_DENIED"),
            GoCardless.parseStatus(obj("""{"data":{"error_code":"ACCESS_DENIED"}}"""), "req-1"),
        )
        assertEquals(
            BrowserLinkStatus.Linked(
                listOf(
                    BankProviderAccount("acc-1", "Current", "Bank X", "req-1", null),
                    BankProviderAccount("acc-2", "Official", null, "req-1", null),
                    BankProviderAccount("acc-3", "", null, "req-1", null),
                ),
            ),
            GoCardless.parseStatus(
                obj(
                    """{"data":{"accounts":[{"account_id":"acc-1","name":"Current","institution":{"name":"Bank X"}},""" +
                        """{"account_id":"acc-2","official_name":"Official"},{"account_id":"acc-3"},{"name":"No id"},"x"]}}""",
                ),
                "req-1",
            ),
        )
        assertEquals(BrowserLinkStatus.Linked(emptyList()), GoCardless.parseStatus(obj("""{"data":{}}"""), "req-1"))
    }

    @Test
    fun enableBankingFollowsActualsSessionFlow() {
        assertEquals("enablebanking/aspsps", EnableBanking.institutionsRequest("FI").route)
        assertEquals("""{"country":"FI"}""", EnableBanking.institutionsRequest("FI").body.toString())
        assertEquals(
            listOf(BankInstitution("Nordea", "Nordea", "FI"), BankInstitution("OP", "OP", "SE")),
            EnableBanking.parseInstitutions(
                json("""{"aspsps":[{"name":"Nordea","country":"FI"},{"name":"OP"},{"country":"FI"},"x"]}"""),
                "SE",
            ),
        )
        assertEquals(emptyList(), EnableBanking.parseInstitutions(json("[]"), "FI"))
        val start = EnableBanking.startRequest(BankInstitution("Nordea", "Nordea", "FI"), "https://budget.example/")
        assertEquals("enablebanking/start-auth", start.route)
        assertEquals(
            """{"aspsp":{"name":"Nordea","country":"FI"},"redirectUrl":"https://budget.example/enablebanking/auth_callback",""" +
                """"psuType":"personal"}""",
            start.body.toString(),
        )
        assertEquals(
            BrowserLink("https://bank/auth", "st"),
            EnableBanking.parseStart(obj("""{"data":{"url":"https://bank/auth","state":"st"}}""")),
        )
        assertNull(EnableBanking.parseStart(obj("{}")))
        assertNull(EnableBanking.parseStart(obj("""{"data":{"state":"st"}}""")))
        assertNull(EnableBanking.parseStart(obj("""{"data":{"url":"u"}}""")))
        assertEquals("""{"state":"st"}""", EnableBanking.statusRequest("st").body.toString())
        assertEquals("enablebanking/poll-auth", EnableBanking.statusRequest("st").route)
        assertEquals(BrowserLinkStatus.Pending, EnableBanking.parseStatus(obj("{}"), "st"))
        assertEquals(BrowserLinkStatus.Pending, EnableBanking.parseStatus(obj("""{"data":{"error":"Polling timed out"}}"""), "st"))
        assertEquals(BrowserLinkStatus.Failed("denied"), EnableBanking.parseStatus(obj("""{"data":{"error":"denied"}}"""), "st"))
        assertEquals(
            BrowserLinkStatus.Failed("INVALID_INPUT"),
            EnableBanking.parseStatus(obj("""{"data":{"error_code":"INVALID_INPUT"}}"""), "st"),
        )
        assertEquals(
            BrowserLinkStatus.Linked(
                listOf(
                    BankProviderAccount("uid-1", "Checking", "Nordea", "uid-1", "12.5"),
                    BankProviderAccount("uid-2", "uid-2", null, "uid-2", null),
                ),
            ),
            EnableBanking.parseStatus(
                obj(
                    """{"data":{"session_id":"s","accounts":[{"account_id":"uid-1","name":"Checking","institution":"Nordea",""" +
                        """"balance":12.5},{"account_id":"uid-2"},{"name":"No id"},"x"]}}""",
                ),
                "st",
            ),
        )
    }
}
