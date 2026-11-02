package app.tandemforactual.api.client

import app.tandemforactual.api.generated.Api
import app.tandemforactual.api.generated.Auth
import com.kroegerama.openapi.kmp.gen.companion.AuthPlugin
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TandemApiClientTest {
    @Test
    fun pairingAndBudgetCatalogUseTheGeneratedApi() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    when (request.url.encodedPath) {
                        "/api/v1/auth/pair" -> respond(pairResponse(), headers = jsonHeaders())
                        "/api/v1/budgets" -> respond(budgetsResponse(), headers = jsonHeaders())
                        else -> error("Unexpected API path ${request.url.encodedPath}")
                    }
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                val paired = client.pair("https://tandem.example/", " 4DF7-J1M9 ", " Pixel 10a ")

                assertEquals("device-1", paired.deviceId)
                assertEquals("household-owner", paired.principalId)
                assertEquals(listOf("budget-1", "budget-2"), paired.budgetIds)
                assertTrue(client.isAuthenticated)
                assertEquals("https://tandem.example", client.currentServerUrl)
                val storedSession = client.exportStoredSession()
                assertTrue(storedSession.orEmpty().contains("test-device-token"))
                assertFalse(storedSession.orEmpty().contains("4DF7-J1M9"))
                assertNull(requests.first().headers[HttpHeaders.Authorization])
                assertEquals("/api/v1/auth/pair", requests.first().url.encodedPath)
                assertEquals(
                    """{"code":"4DF7-J1M9","deviceName":"Pixel 10a"}""",
                    (requests.first().body as TextContent).text,
                )

                val budgets = client.listBudgets()
                assertEquals(
                    listOf(
                        BudgetCatalogItem("budget-1", "Household", false),
                        BudgetCatalogItem("budget-2", "Travel", true),
                    ),
                    budgets,
                )
                assertEquals("Bearer test-device-token", requests.last().headers[HttpHeaders.Authorization])

                client.forgetSession()
                assertFalse(client.isAuthenticated)
                val unauthenticated = captureApiException { client.listBudgets() }
                assertEquals("not-paired", unauthenticated.errorCode)
                assertEquals("Pair this device before loading its budgets.", unauthenticated.message)

                val restored = client.restoreStoredSession(storedSession.orEmpty())
                assertEquals(paired, restored)
                assertTrue(client.isAuthenticated)
                assertEquals(2, client.listBudgets().size)
                assertEquals("Bearer test-device-token", requests.last().headers[HttpHeaders.Authorization])
                assertEquals(3, requests.size)
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun invalidServerAndRejectedPairingReturnSanitizedErrors() =
        runBlocking {
            var status = HttpStatusCode.Unauthorized
            val engine =
                MockEngine { request ->
                    assertEquals("/api/v1/auth/pair", request.url.encodedPath)
                    respond("""{"error":{"code":"invalid_pairing_code","message":"private server detail"}}""", status, jsonHeaders())
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                val invalidUrl = captureApiException { client.pair("http://tandem.example", "4DF7-J1M9", "Phone") }
                assertEquals("invalid-server-url", invalidUrl.errorCode)
                val insecureLoopbackUrl = captureApiException { client.pair("http://localhost:19753", "4DF7-J1M9", "Phone") }
                assertEquals("invalid-server-url", insecureLoopbackUrl.errorCode)
                val malformedUrl = captureApiException { client.pair("https://tandem.example:invalid", "4DF7-J1M9", "Phone") }
                assertEquals("invalid-server-url", malformedUrl.errorCode)
                val blankHostUrl = captureApiException { client.pair("https://?budget=1", "4DF7-J1M9", "Phone") }
                assertEquals("invalid-server-url", blankHostUrl.errorCode)

                val unauthorized = captureApiException { client.pair("https://tandem.example", "4DF7-J1M9", "Phone") }
                assertEquals("invalid_pairing_code", unauthorized.errorCode)
                assertEquals(401, unauthorized.httpStatus)
                assertEquals("The pairing code is invalid, expired, or already used.", unauthorized.message)

                status = HttpStatusCode.BadRequest
                val rejected = captureApiException { client.pair("https://tandem.example", "NOPE-0000", "Phone") }
                assertEquals("http-400", rejected.errorCode)
                assertEquals(400, rejected.httpStatus)
                assertEquals("Check the pairing code and try again.", rejected.message)
                assertFalse(rejected.message.orEmpty().contains("host detail"))
                assertFalse(client.isAuthenticated)
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun debugClientAllowsHttpOnlyForLoopbackHosts() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(pairResponse(), headers = jsonHeaders())
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient(allowInsecureLoopback = true)
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                client.pair("http://localhost:19753", "4DF7-J1M9", "Pixel 10a")
                val requestUrl = requests.single().url
                assertEquals("localhost", requestUrl.host)
                assertEquals("http", requestUrl.protocol.name)

                val publicHost = captureApiException { client.pair("http://tandem.example", "4DF7-J1M9", "Pixel 10a") }
                assertEquals("invalid-server-url", publicHost.errorCode)
                val lanHost = captureApiException { client.pair("http://192.168.1.20", "4DF7-J1M9", "Pixel 10a") }
                assertEquals("invalid-server-url", lanHost.errorCode)
                assertEquals(1, requests.size)
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun rejectedBudgetCatalogRequestsExplainRePairingOrAccessScope() =
        runBlocking {
            var status = HttpStatusCode.Unauthorized
            var responseBody = """{"error":{"message":"private server detail"}}"""
            val engine =
                MockEngine {
                    delay(1)
                    respond(responseBody, status, jsonHeaders())
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                client.restoreSession("https://tandem.example", "device-token")

                val unauthorized = captureApiException { client.listBudgets() }
                assertEquals("http-401", unauthorized.errorCode)
                assertEquals(401, unauthorized.httpStatus)
                assertEquals("The server could not verify this request. Check server access and try again.", unauthorized.message)
                assertFalse(unauthorized.message.orEmpty().contains("private server detail"))

                responseBody = """{"error":{"code":"invalid_device_credential","message":"private server detail"}}"""
                val invalidCredential = captureApiException { client.listBudgets() }
                assertEquals("invalid_device_credential", invalidCredential.errorCode)
                assertEquals(401, invalidCredential.httpStatus)
                assertEquals("This device needs to be paired again.", invalidCredential.message)
                assertFalse(invalidCredential.message.orEmpty().contains("private server detail"))

                status = HttpStatusCode.Forbidden
                val forbidden = captureApiException { client.listBudgets() }
                assertEquals("http-403", forbidden.errorCode)
                assertEquals(403, forbidden.httpStatus)
                assertEquals("This device is not allowed to access that budget.", forbidden.message)

                status = HttpStatusCode.BadGateway
                val unavailable = captureApiException { client.listBudgets() }
                assertEquals(502, unavailable.httpStatus)
                assertEquals("The Tandem server could not read Actual. Try again later.", unavailable.message)
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun restoringSessionRejectsEmptyTokenAndUsesRestoredCredential() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(budgetsResponse(), headers = jsonHeaders())
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                val emptyToken = captureProtocolException { client.restoreSession("https://tandem.example", " ") }
                assertEquals("Stored device credential is empty", emptyToken.message)
                assertFalse(client.isAuthenticated)

                client.restoreSession(" https://tandem.example/ ", "restored-device-token")
                assertTrue(client.isAuthenticated)
                assertEquals(2, client.listBudgets().size)
                assertEquals("Bearer restored-device-token", requests.single().headers[HttpHeaders.Authorization])
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun restoringStoredSessionRejectsMalformedAndIncompletePayloads() =
        runBlocking {
            val client = TandemApiClient()
            assertEquals(
                "Stored device session is invalid",
                captureProtocolException { client.restoreStoredSession("not-json") }.message,
            )
            assertEquals(
                "Server response is missing a valid serverUrl",
                captureProtocolException { client.restoreStoredSession("{}") }.message,
            )
            assertEquals(
                "Stored device session has no device",
                captureProtocolException {
                    client.restoreStoredSession("""{"serverUrl":"https://tandem.example","accessToken":"token"}""")
                }.message,
            )
            assertFalse(client.isAuthenticated)
        }

    @Test
    fun malformedPairingAndBudgetPayloadsAreRejectedAsProtocolErrors() =
        runBlocking {
            var response = """{"deviceId":"device-1","principalId":"owner","budgetIds":[""] ,"accessToken":"token"}"""
            var path = "/api/v1/auth/pair"
            val engine =
                MockEngine { request ->
                    assertEquals(path, request.url.encodedPath)
                    respond(response, headers = jsonHeaders())
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                val badPair = captureProtocolException { client.pair("https://tandem.example", "4DF7-J1M9", "Phone") }
                assertEquals("Server response contains an invalid budgetIds entry", badPair.message)

                response = """{"deviceId":"device-1","principalId":"owner","budgetIds":"budget-1","accessToken":"token"}"""
                val badBudgetIds = captureProtocolException { client.pair("https://tandem.example", "4DF7-J1M9", "Phone") }
                assertEquals("Server response is missing a valid budgetIds", badBudgetIds.message)

                client.restoreSession("https://tandem.example", "token")
                path = "/api/v1/budgets"
                response = "{}"
                val missingCatalog = captureProtocolException { client.listBudgets() }
                assertEquals("Budget catalog response is missing its budgets", missingCatalog.message)

                response = """{"budgets":[{"budgetId":"budget-1","name":"Household","isEncrypted":"yes"}]}"""
                val badEncryptionFlag = captureProtocolException { client.listBudgets() }
                assertEquals("Budget catalog item has an invalid encryption flag", badEncryptionFlag.message)

                response = """{"budgets":[null]}"""
                val badItem = captureProtocolException { client.listBudgets() }
                assertEquals("Budget catalog contains an invalid item", badItem.message)

                response = """{"budgets":[]}"""
                assertTrue(client.listBudgets().isEmpty())
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun transportFailuresAreSanitized() =
        runBlocking {
            val engine = MockEngine { throw IllegalStateException("private transport detail") }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                client.restoreSession("https://tandem.example", "device-token")
                val failure = captureApiException { client.listBudgets() }
                assertEquals("network-error", failure.errorCode)
                assertEquals("Could not connect to the Tandem server. Check the address and connection.", failure.message)
                assertFalse(failure.message.orEmpty().contains("private transport detail"))
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    private fun configureApi(
        engine: MockEngine,
        auth: Auth.BearerAuth,
    ): () -> Unit {
        Api.baseUrl = Url("https://tandem.example")
        Api.setAuthProvider(auth)
        Api.updateClient(
            createHttpClient = {
                HttpClient(engine) {
                    install(ContentNegotiation) { json(Api.json) }
                    install(DefaultRequest) { url.takeFrom(Api.baseUrl) }
                    install(AuthPlugin) {
                        authItem { key -> if (key == auth.key) auth.provideAuthItem() else null }
                    }
                }
            },
        )
        return { Api.clearAuthProvider(auth) }
    }

    private suspend fun captureApiException(block: suspend () -> Any?): TandemApiException =
        try {
            block()
            error("Expected a Tandem API exception")
        } catch (exception: TandemApiException) {
            exception
        }

    private suspend fun captureProtocolException(block: suspend () -> Any?): TandemApiProtocolException =
        try {
            block()
            error("Expected a Tandem protocol exception")
        } catch (exception: TandemApiProtocolException) {
            exception
        }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun pairResponse() =
        """{"deviceId":"device-1","principalId":"household-owner","budgetIds":["budget-1","budget-2"],"accessToken":"test-device-token"}"""

    private fun budgetsResponse() =
        """{"budgets":[{"budgetId":"budget-1","name":"Household","isEncrypted":false},{"budgetId":"budget-2","name":"Travel","isEncrypted":true}]}"""
}
