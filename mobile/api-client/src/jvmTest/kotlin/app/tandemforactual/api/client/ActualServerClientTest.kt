package app.tandemforactual.api.client

import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.data.actual.SyncProtocol
import app.tandemforactual.data.actual.SyncRequest
import app.tandemforactual.data.actual.SyncResponse
import app.tandemforactual.data.regions.global.bank.BankProviders
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ActualServerClientTest {
    @Test
    fun performsActualAccountAndBudgetRequests() =
        runBlocking {
            val engine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    if (path.startsWith("/actual/sync/") || path.endsWith("/validate")) {
                        assertEquals("session", request.headers["x-actual-token"])
                    }
                    when (path) {
                        "/actual/account/needs-bootstrap" -> respond("""{"data":{"bootstrapped":true}}""")
                        "/actual/account/login-methods" ->
                            respond(
                                """{"methods":[{"method":"password","active":1},
                                   {"method":"openid","active":true,"displayName":"SSO"},{}]}""",
                            )
                        "/actual/account/login" -> {
                            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                            if ("openid" in body) {
                                assertTrue("callback" in body)
                                respond("""{"data":{"returnUrl":"https://identity.example/login"}}""")
                            } else {
                                assertTrue("test-password" in body)
                                respond("""{"data":{"token":"session"}}""")
                            }
                        }
                        "/actual/account/validate" -> respond("""{"status":"ok"}""")
                        "/actual/sync/list-user-files" ->
                            respond(
                                """{"data":[{"fileId":"file","groupId":"group","name":"Budget","deleted":0},
                                   {"fileId":"old","deleted":true}]}""",
                            )
                        "/actual/sync/get-user-file-info" -> {
                            assertEquals("file", request.headers["x-actual-file-id"])
                            respond("""{"data":{"groupId":"group","name":"Budget","encryptMeta":{"iv":"value"}}}""")
                        }
                        "/actual/sync/download-user-file" -> {
                            assertEquals("file", request.headers["x-actual-file-id"])
                            respond(byteArrayOf(1, 2))
                        }
                        "/actual/sync/user-get-key" -> {
                            assertTrue("file" in (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                            respond("""{"data":{"id":"key","salt":"salt","test":"test"}}""")
                        }
                        "/actual/sync/sync" -> {
                            val sync = SyncProtocol.decodeRequest((request.body as OutgoingContent.ByteArrayContent).bytes())
                            assertEquals("file", sync.fileId)
                            respond(SyncProtocol.encode(SyncResponse(emptyList(), "{}")))
                        }
                        else -> error("Unexpected request to $path")
                    }
                }
            HttpClient(engine).use { http ->
                val client = ActualServerClient(http, "https://budget.example/actual/")
                val status = client.status()
                assertTrue(status.bootstrapped)
                assertEquals(
                    listOf(
                        ActualLoginMethod("password", true, null),
                        ActualLoginMethod("openid", true, "SSO"),
                        ActualLoginMethod("password", false, null),
                    ),
                    status.loginMethods,
                )
                assertEquals("https://identity.example/login", client.startOpenId("https://app.example/callback"))
                assertEquals("session", client.signIn("test-password"))
                assertTrue(client.validate())
                val files = client.listFiles()
                assertEquals("group", files.first().groupId)
                assertTrue(files.last().deleted)
                assertEquals("file", client.fileInfo("file").fileId)
                assertContentEquals(byteArrayOf(1, 2), client.download("file"))
                assertEquals(ActualKeyInfo("key", "salt", "test"), client.keyInfo("file"))
                assertEquals("{}", client.sync(SyncRequest(emptyList(), "file", "group", "", "since")).merkle)
            }
        }

    @Test
    fun asksTheActualServerForEachBankProvider() =
        runBlocking {
            val seen = mutableListOf<String>()
            HttpClient(
                MockEngine { request ->
                    assertEquals("file", request.headers["x-actual-file-id"])
                    seen += request.url.encodedPath + " " + (request.body as TextContent).text
                    respond("""{"status":"ok","data":{"transactions":{"all":[]}}}""")
                },
            ).use { http ->
                val client = ActualServerClient(http, "https://budget.example")
                client.token = "session"
                for (source in listOf("simpleFin", "pluggyai", "akahu", "enableBanking", "goCardless")) {
                    client.bankTransactions(LinkedAccount("a", "A", source, "P1", "req", "Bank", "2026-11-01"), "file")
                }
            }
            assertEquals(
                listOf(
                    """/simplefin/transactions {"accountId":"P1","startDate":"2026-11-01"}""",
                    """/pluggyai/transactions {"accountId":"P1","startDate":"2026-11-01"}""",
                    """/akahu/transactions {"accountId":"P1","startDate":"2026-11-01"}""",
                    """/enablebanking/transactions {"accountId":"P1","startDate":"2026-11-01","aspspName":"Bank"}""",
                    """/gocardless/transactions {"requisitionId":"req","accountId":"P1","startDate":"2026-11-01","includeBalance":true}""",
                ),
                seen,
            )
        }

    @Test
    fun scopesEveryProvidersStatusAndAccountsToTheBudget() =
        runBlocking {
            val seen = mutableListOf<String>()
            HttpClient(
                MockEngine { request ->
                    seen += request.url.encodedPath + " " + request.headers["x-actual-file-id"]
                    respond("""{"status":"ok","data":{"configured":true,"accounts":[]}}""")
                },
            ).use { http ->
                val client = ActualServerClient(http, "https://budget.example")
                client.token = "session"
                for (provider in BankProviders.all) {
                    assertEquals(BankProviderStatus(true, BankCredentialSource.SERVER), client.bankStatus(provider, "file"))
                    client.bankAccounts(provider, "file")
                }
            }
            assertEquals(BankProviders.all.size * 2, seen.size)
            assertTrue(seen.all { it.endsWith(" file") })
        }

    @Test
    fun reportsActualFailuresAndFallsBackToLegacyDiscovery() =
        runBlocking {
            var reply = "{}"
            var status = HttpStatusCode.OK
            HttpClient(
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/account/needs-bootstrap" -> respond("""{"data":{"bootstrapped":false,"loginMethod":"openid"}}""")
                        "/account/login-methods" -> respond("missing", HttpStatusCode.NotFound)
                        else -> respond(reply, status)
                    }
                },
            ).use { http ->
                val client = ActualServerClient(http, "https://budget.example")
                assertEquals(ActualServerStatus(false, listOf(ActualLoginMethod("openid", true, null))), client.status())
                assertEquals("unauthorized", assertFailsWith<ActualServerException> { client.listFiles() }.reason)
                assertEquals("invalid-password", assertFailsWith<ActualServerException> { client.signIn("wrong") }.reason)
                assertEquals(
                    "openid-unavailable",
                    assertFailsWith<ActualServerException> { client.startOpenId("https://app.example") }.reason,
                )
                client.token = "session"
                reply = """{"status":"error","reason":"file-has-reset"}"""
                assertFalse(client.validate())
                assertEquals("file-has-reset", assertFailsWith<ActualServerException> { client.listFiles() }.reason)
                reply = """{"data":[{}]}"""
                assertEquals("invalid-response", assertFailsWith<ActualServerException> { client.listFiles() }.reason)
                reply = "file-has-reset"
                status = HttpStatusCode.Conflict
                assertEquals(
                    "file-has-reset",
                    assertFailsWith<ActualServerException> { client.sync(SyncRequest(emptyList(), "f", "g", "", "")) }.reason,
                )
                reply = "{}"
                for ((code, reason) in listOf(
                    HttpStatusCode.Forbidden to "unauthorized",
                    HttpStatusCode.NotFound to "not-found",
                    HttpStatusCode.BadGateway to "server-unavailable",
                    HttpStatusCode.ServiceUnavailable to "server-unavailable",
                    HttpStatusCode.TooManyRequests to "rate-limited",
                    HttpStatusCode.BadRequest to "network-failure",
                )) {
                    status = code
                    assertEquals(reason, assertFailsWith<ActualServerException> { client.download("file") }.reason)
                }
            }
        }
}
