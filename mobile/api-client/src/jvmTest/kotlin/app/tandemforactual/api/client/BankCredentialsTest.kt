package app.tandemforactual.api.client

import app.tandemforactual.data.regions.us.bank.simplefin.SimpleFin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class BankCredentialsTest {
    private suspend fun signedIn(server: FakeActualServer): TandemApiClient =
        TandemApiClient(http = server.http, budgetFiles = TempBudgetFiles()).also {
            it.signInWithPassword("https://budget.example", "test", "Phone")
        }

    @Test
    fun asksForTheOpenBudgetsAccountsWithItsFileId(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val access = signedIn(server).bankProviderAccounts(SimpleFin, "group")
            assertEquals(BankCredentialSource.SERVER, access.source)
            assertEquals("SF-9", access.accounts!!.first().id)
            assertEquals(listOf<String?>("file", "file"), server.simpleFinFileIds)
            assertEquals(
                "budget_unavailable",
                assertFailsWith<TandemApiException> { signedIn(server).bankProviderAccounts(SimpleFin, "old") }.errorCode,
            )
        }

    @Test
    fun aBudgetsOwnTokenWinsAndRemovingItFallsBackToTheServers(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val client = signedIn(server)
            server.secrets["simplefin_accessKey:file"] = "stale"
            client.saveSimpleFinToken("group", "  setup-token \n", BankCredentialSource.BUDGET)
            assertEquals(mapOf("simplefin_token:file" to "setup-token"), server.secrets.toMap())
            val own = client.bankProviderAccounts(SimpleFin, "group")
            assertEquals(BankCredentialSource.BUDGET, own.source)
            assertEquals(listOf("SF-B"), own.accounts!!.map { it.id })
            client.removeSimpleFinBudgetToken("group")
            assertTrue(server.secrets.isEmpty())
            val fallback = client.bankProviderAccounts(SimpleFin, "group").accounts!!
            assertEquals("SF-9", fallback.first().id)
            assertTrue(server.simpleFinFileIds.all { it == "file" })
        }

    @Test
    fun aBrokenBudgetTokenStaysTheBudgetsAndNeverFallsBack(): Unit =
        runBlocking {
            val server = FakeActualServer()
            server.secrets["simplefin_accessKey:file"] = "Forbidden"
            assertEquals(BankProviderAccess(BankCredentialSource.BUDGET, null), signedIn(server).bankProviderAccounts(SimpleFin, "group"))
        }

    @Test
    fun movingToTheServersTokenDropsTheBudgetsOwn(): Unit =
        runBlocking {
            val server = FakeActualServer()
            server.simpleFinConfigured = false
            val client = signedIn(server)
            assertEquals(BankProviderAccess(null, null), client.bankProviderAccounts(SimpleFin, "group"))
            assertTrue(client.canManageServerBankCredentials())
            client.saveSimpleFinToken("group", "budget-token", BankCredentialSource.BUDGET)
            client.saveSimpleFinToken("group", "server-token", BankCredentialSource.SERVER)
            assertEquals(mapOf("simplefin_token" to "server-token"), server.secrets.toMap())
            assertEquals(BankCredentialSource.SERVER, client.bankProviderAccounts(SimpleFin, "group").source)
            client.saveSimpleFinToken("group", "again", BankCredentialSource.SERVER)
            assertEquals(mapOf("simplefin_token" to "again"), server.secrets.toMap())
        }

    @Test
    fun onlyAdminsSetTheServersToken(): Unit =
        runBlocking {
            val server = FakeActualServer()
            server.admin = false
            val client = signedIn(server)
            assertFalse(client.canManageServerBankCredentials())
            val denied =
                assertFailsWith<TandemApiException> {
                    client.saveSimpleFinToken("group", "token", BankCredentialSource.SERVER)
                }
            assertEquals("not_admin", denied.errorCode)
            assertEquals(403, denied.httpStatus)
            assertTrue(server.secrets.isEmpty())
            client.saveSimpleFinToken("group", "token", BankCredentialSource.BUDGET)
            assertEquals("token", server.secrets["simplefin_token:file"])
        }

    @Test
    fun reportsBudgetsTheUserCannotManage(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val client = ActualServerClient(server.http, "https://budget.example")
            client.token = "session"
            val denied = assertFailsWith<ActualServerException> { client.setSecret("simplefin_token", "x", "shared") }
            assertEquals("file-access-denied", denied.reason)
            assertEquals(403, denied.httpStatus)
            val removal = assertFailsWith<ActualServerException> { client.removeSecret("simplefin_token", "shared") }
            assertEquals("file-access-denied", removal.reason)
            assertNull(server.secrets["simplefin_token"])
        }
}
