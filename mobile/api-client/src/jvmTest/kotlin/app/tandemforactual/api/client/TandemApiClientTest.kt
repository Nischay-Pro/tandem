package app.tandemforactual.api.client

import app.tandemforactual.api.generated.Api
import app.tandemforactual.api.generated.Auth
import app.tandemforactual.api.generated.api.AuthenticationApi
import app.tandemforactual.api.generated.api.BudgetsApi
import app.tandemforactual.api.generated.api.TransactionsApi
import com.kroegerama.openapi.kmp.gen.companion.AuthPlugin
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class TandemApiClientTest {
    @Test
    fun postingOrSkippingScheduleRequiresDisplayedNextDate() =
        runBlocking {
            val client = TandemApiClient()

            assertFailsWith<IllegalArgumentException> {
                client.manageBudgetSchedule("budget-1", "schedule-1", "post", null, null)
            }
            assertFailsWith<IllegalArgumentException> {
                client.manageBudgetSchedule("budget-1", "schedule-1", "skip", null, null)
            }
            Unit
        }

    @Test
    fun readsActualLoginMethodsAndAvailableOptions() =
        runBlocking {
            val methods =
                readActualLoginMethods(
                    """{"bootstrapped":true,"loginMethod":"password","availableLoginMethods":[null,{"method":"password","displayName":"Password","active":true}]}""",
                )

            assertTrue(methods.bootstrapped)
            assertEquals("password", methods.loginMethod)
            assertEquals(
                listOf(ActualLoginMethodOption("password", "Password", true)),
                methods.availableLoginMethods,
            )
        }

    @Test
    fun absentActualLoginMethodFieldsUseEmptyDefaults() =
        runBlocking {
            val methods = readActualLoginMethods("""{"bootstrapped":false,"loginMethod":null}""")

            assertFalse(methods.bootstrapped)
            assertNull(methods.loginMethod)
            assertTrue(methods.availableLoginMethods.isEmpty())
        }

    @Test
    fun readsActualLoginMethodsFromTandemWithoutSendingCredentials() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(
                        if (request.url.encodedPath == "/api/v1/budgets") {
                            budgetsResponse()
                        } else {
                            """{"bootstrapped":true,"loginMethod":"password","availableLoginMethods":[{"method":"password","displayName":"Password","active":true}]}"""
                        },
                        headers = jsonHeaders(),
                    )
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                client.restoreSession("https://tandem.example", "stored-device-token")
                client.listBudgets()
                val methods = client.readActualLoginMethods("https://tandem.example/gateway")

                assertEquals("password", methods.loginMethod)
                assertEquals(
                    listOf("/api/v1/budgets", "/api/v1/auth/methods"),
                    requests.map { it.url.encodedPath },
                )
                assertEquals("Bearer stored-device-token", requests.first().headers[HttpHeaders.Authorization])
                assertNull(requests.last().headers[HttpHeaders.Authorization])
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun malformedActualLoginMethodFieldsAreRejected() =
        runBlocking {
            val invalidLoginMethod =
                captureProtocolException {
                    readActualLoginMethods(
                        """{"bootstrapped":true,"loginMethod":[],"availableLoginMethods":[]}""",
                    )
                }
            assertEquals("Server response contains an invalid loginMethod", invalidLoginMethod.message)

            val invalidBootstrapFlag =
                captureProtocolException {
                    readActualLoginMethods(
                        """{"bootstrapped":"yes","availableLoginMethods":[]}""",
                    )
                }
            assertEquals("Server response is missing a valid bootstrapped", invalidBootstrapFlag.message)
        }

    @Test
    fun actualOnlyAddressIsDistinguishedFromAnUnreachableAddress() =
        runBlocking {
            suspend fun failureFor(responses: Map<String, Pair<String, HttpStatusCode>>): TandemApiException {
                val requests = mutableListOf<HttpRequestData>()
                val engine =
                    MockEngine { request ->
                        requests += request
                        val (body, status) = responses[request.url.encodedPath] ?: ("" to HttpStatusCode.NotFound)
                        respond(body, status, jsonHeaders())
                    }
                val previousClient = Api.client
                val previousBaseUrl = Api.baseUrl
                val client = TandemApiClient()
                val cleanup = configureApi(engine, client.bearerAuth)
                try {
                    val failure = captureApiException { client.readActualLoginMethods("https://actual.example") }
                    assertTrue(requests.all { it.headers[HttpHeaders.Authorization] == null })
                    return failure
                } finally {
                    cleanup()
                    Api.baseUrl = previousBaseUrl
                    Api.client.close()
                    previousClient.close()
                }
            }

            val actualOnly =
                failureFor(
                    mapOf(
                        "/api/v1/auth/methods" to ("<html>Not found</html>" to HttpStatusCode.NotFound),
                        "/api/v1/health/ready" to
                            (
                                """{"error":{"code":"proxy_unavailable","message":"private detail"}}""" to
                                    HttpStatusCode.InternalServerError
                            ),
                        "/account/needs-bootstrap" to
                            ("""{"status":"ok","data":{"bootstrapped":true}}""" to HttpStatusCode.OK),
                    ),
                )
            assertEquals("actual_only", actualOnly.errorCode)

            val unreachable =
                failureFor(
                    mapOf(
                        "/api/v1/auth/methods" to ("" to HttpStatusCode.NotFound),
                        "/api/v1/health/ready" to ("" to HttpStatusCode.NotFound),
                        "/account/needs-bootstrap" to ("" to HttpStatusCode.NotFound),
                    ),
                )
            assertEquals("address_unreachable", unreachable.errorCode)
        }

    @Test
    fun classifiesTandemReadinessAndMissingAuthRouteWithoutCallingActual() =
        runBlocking {
            suspend fun failureFor(
                healthBody: String,
                healthStatus: HttpStatusCode,
            ): Pair<TandemApiException, List<HttpRequestData>> {
                val requests = mutableListOf<HttpRequestData>()
                val engine =
                    MockEngine { request ->
                        requests += request
                        when (request.url.encodedPath) {
                            "/api/v1/auth/methods" -> respond("", HttpStatusCode.NotFound, jsonHeaders())
                            "/api/v1/health/ready" -> respond(healthBody, healthStatus, jsonHeaders())
                            else -> respond("Unexpected request", HttpStatusCode.NotFound, jsonHeaders())
                        }
                    }
                val previousClient = Api.client
                val previousBaseUrl = Api.baseUrl
                val client = TandemApiClient()
                val cleanup = configureApi(engine, client.bearerAuth)
                try {
                    val failure = captureApiException { client.readActualLoginMethods("https://tandem.example/gateway") }
                    assertTrue(requests.all { it.headers[HttpHeaders.Authorization] == null })
                    return failure to requests
                } finally {
                    cleanup()
                    Api.baseUrl = previousBaseUrl
                    Api.client.close()
                    previousClient.close()
                }
            }

            val notReady = failureFor("""{"status":"not_ready"}""", HttpStatusCode.ServiceUnavailable)
            assertEquals("tandem_not_ready", notReady.first.errorCode)
            assertEquals(listOf("/api/v1/auth/methods", "/api/v1/health/ready"), notReady.second.map { it.url.encodedPath })

            val routeUnavailable = failureFor("""{"status":"ok"}""", HttpStatusCode.OK)
            assertEquals("tandem_route_unavailable", routeUnavailable.first.errorCode)
            assertEquals(listOf("/api/v1/auth/methods", "/api/v1/health/ready"), routeUnavailable.second.map { it.url.encodedPath })
        }

    @Test
    fun preservesTheLoginMethodErrorWhenReadinessConfirmsTandemIsHealthy() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    when (request.url.encodedPath) {
                        "/api/v1/auth/methods" ->
                            respond(
                                """{"error":{"code":"login_methods_unavailable","message":"private detail"}}""",
                                HttpStatusCode.ServiceUnavailable,
                                jsonHeaders(),
                            )
                        "/api/v1/health/ready" -> respond("""{"status":"ok"}""", HttpStatusCode.OK, jsonHeaders())
                        else -> respond("Unexpected request", HttpStatusCode.NotFound, jsonHeaders())
                    }
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                val failure = captureApiException { client.readActualLoginMethods("https://tandem.example") }

                assertEquals("login_methods_unavailable", failure.errorCode)
                assertEquals(HttpStatusCode.ServiceUnavailable.value, failure.httpStatus)
                assertEquals(listOf("/api/v1/auth/methods", "/api/v1/health/ready"), requests.map { it.url.encodedPath })
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun unreachableAddressIsClassifiedWithoutSendingCredentials() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    throw IllegalStateException("offline")
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                val failure = captureApiException { client.readActualLoginMethods("https://offline.example") }

                assertEquals("address_unreachable", failure.errorCode)
                assertEquals(3, requests.size)
                assertTrue(requests.all { it.headers[HttpHeaders.Authorization] == null })
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun tandemCanReportAnActualConnectionFailureDuringLoginMethodDiscovery() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(
                        """{"error":{"code":"actual_unavailable","message":"private detail"}}""",
                        HttpStatusCode.ServiceUnavailable,
                        jsonHeaders(),
                    )
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                val failure = captureApiException { client.readActualLoginMethods("https://tandem.example") }

                assertEquals("actual_unavailable", failure.errorCode)
                assertEquals(1, requests.size)
                assertNull(requests.single().headers[HttpHeaders.Authorization])
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun deviceNamesAreTrimmedSanitizedAndLimitedToOneHundredCharacters() {
        assertEquals("Test Phone", normalizeDeviceName("\u0000 Test\nPhone\u007f "))
        assertEquals(100, normalizeDeviceName("A".repeat(120)).length)
        val emojiName = normalizeDeviceName("😀".repeat(120))
        assertEquals(100, emojiName.codePointCount(0, emojiName.length))
        assertEquals("Mobile device", normalizeDeviceName("\u0000\u009f\uFEFF"))
    }

    @Test
    fun queuedCategoryRetriesReuseTheirPersistedIdempotencyKey() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond("""{"status":"ok","accountId":"account-created"}""", headers = jsonHeaders())
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                client.restoreSession("https://tandem.example", "device-token")
                val operationId = "00000000-0000-0000-0000-000000000001"
                repeat(2) {
                    client.manageBudgetCategory(
                        budgetId = "budget-1",
                        categoryId = "category-1",
                        action = "hide",
                        targetId = null,
                        encryptionPassword = null,
                        idempotencyKey = operationId,
                    )
                }

                assertEquals(2, requests.size)
                assertEquals(operationId, requests[0].headers["Idempotency-Key"])
                assertEquals(operationId, requests[1].headers["Idempotency-Key"])
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun forgettingSessionWaitsForAnActiveMultiRequestSync() =
        runBlocking {
            val client = TandemApiClient()
            client.restoreSession("https://tandem.example", "device-token")
            val syncStarted = CompletableDeferred<Unit>()
            val finishSync = CompletableDeferred<Unit>()
            val sync =
                async {
                    client.withExclusiveSession {
                        syncStarted.complete(Unit)
                        finishSync.await()
                    }
                }
            syncStarted.await()

            val forget = async { client.forgetSession() }
            assertFalse(forget.isCompleted)

            finishSync.complete(Unit)
            sync.await()
            forget.await()
            assertFalse(client.isAuthenticated)
        }

    @Test
    fun revokingCurrentDeviceUsesItsAuthenticatedServerIdentity() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond("", HttpStatusCode.NoContent)
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            val deviceId = Uuid.parse("6d860805-fdd6-4fe4-8e67-668c9900fe1f")
            try {
                client.restoreStoredSession(
                    """{"serverUrl":"https://tandem.example","accessToken":"saved-token","device":{"deviceId":"$deviceId","principalId":"person-1","budgetIds":["budget-1"]}}""",
                )

                client.revokeCurrentDevice()

                assertEquals(1, requests.size)
                assertEquals("DELETE", requests.single().method.value)
                assertEquals("/api/v1/devices/$deviceId", requests.single().url.encodedPath)
                assertEquals("Bearer saved-token", requests.single().headers[HttpHeaders.Authorization])
                assertTrue(client.isAuthenticated)
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun signInAndBudgetCatalogUseTheGeneratedApi() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    when (request.url.encodedPath) {
                        "/api/v1/auth/login" -> respond(signedInResponse(), headers = jsonHeaders())
                        "/api/v1/budgets" -> respond(budgetsResponse(), headers = jsonHeaders())
                        else -> error("Unexpected API path ${request.url.encodedPath}")
                    }
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                val signedIn = client.signInWithPassword("https://tandem.example/", "  actual-test-password  ", " Test phone ")

                assertEquals("device-1", signedIn.deviceId)
                assertEquals("household-owner", signedIn.principalId)
                assertEquals(listOf("budget-1", "budget-2"), signedIn.budgetIds)
                assertTrue(client.isAuthenticated)
                assertEquals("https://tandem.example", client.currentServerUrl)
                val storedSession = client.exportStoredSession()
                assertTrue(storedSession.orEmpty().contains("test-device-token"))
                assertFalse(storedSession.orEmpty().contains("actual-test-password"))
                assertNull(requests.first().headers[HttpHeaders.Authorization])
                assertEquals("/api/v1/auth/login", requests.first().url.encodedPath)
                assertEquals(
                    """{"password":"  actual-test-password  ","deviceName":"Test phone"}""",
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
                assertEquals("sign_in_required", unauthenticated.errorCode)
                assertEquals("Sign in to open your budgets.", unauthenticated.message)

                val restored = client.restoreStoredSession(storedSession.orEmpty())
                assertEquals(signedIn, restored)
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
    fun actualOpenIdSignInUsesBrowserRedirectAndExchangesOneUseCode() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    when (request.url.encodedPath) {
                        "/api/v1/auth/openid/start" ->
                            respond(
                                """{"redirectUrl":"https://identity.example/sign-in"}""",
                                headers = jsonHeaders(),
                            )
                        "/api/v1/auth/openid/complete" -> respond(signedInResponse(), headers = jsonHeaders())
                        else -> error("Unexpected API path ${request.url.encodedPath}")
                    }
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
                val challenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
                val callbackState = "S".repeat(43)
                val redirectUrl =
                    client.startActualOpenIdSignIn(
                        "https://tandem.example",
                        "Test phone",
                        challenge,
                        callbackState,
                    )
                assertEquals("https://identity.example/sign-in", redirectUrl)
                val signedIn = client.completeActualOpenIdSignIn("https://tandem.example", "C".repeat(43), verifier)

                assertEquals("device-1", signedIn.deviceId)
                assertTrue(client.isAuthenticated)
                assertEquals(
                    listOf("/api/v1/auth/openid/start", "/api/v1/auth/openid/complete"),
                    requests.map { it.url.encodedPath },
                )
                assertTrue(requests.all { it.headers[HttpHeaders.Authorization] == null })
                assertEquals(
                    """{"deviceName":"Test phone","codeChallenge":"$challenge","callbackState":"$callbackState"}""",
                    (requests[0].body as TextContent).text,
                )
                assertEquals(
                    """{"code":"${"C".repeat(43)}","codeVerifier":"$verifier"}""",
                    (requests[1].body as TextContent).text,
                )
                assertFalse(requests[0].body.toString().contains(verifier))
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun generatedBrowserHandoffRouteKeepsStateInThePath() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond("", HttpStatusCode.OK, jsonHeaders())
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                val state = "S".repeat(43)
                val response = AuthenticationApi.startActualOpenIdBrowserHandoff(state)

                assertTrue(response.isRight())
                assertEquals(HttpMethod.Get, requests.single().method)
                assertEquals(
                    "/api/v1/auth/openid/browser-start/$state",
                    requests.single().url.encodedPath,
                )
                assertTrue(requests.single().headers[HttpHeaders.Authorization] == null)
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun invalidServerAndRejectedSignInReturnSanitizedErrors() =
        runBlocking {
            var status = HttpStatusCode.Unauthorized
            var responseBody = """{"error":{"code":"invalid_credentials","message":"private server detail"}}"""
            val engine =
                MockEngine { request ->
                    assertEquals("/api/v1/auth/login", request.url.encodedPath)
                    respond(responseBody, status, jsonHeaders())
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                val invalidUrl = captureApiException { client.signInWithPassword("http://tandem.example", "actual-test-password", "Phone") }
                assertEquals("invalid-server-url", invalidUrl.errorCode)
                val insecureLoopbackUrl =
                    captureApiException { client.signInWithPassword("http://localhost:19753", "actual-test-password", "Phone") }
                assertEquals("invalid-server-url", insecureLoopbackUrl.errorCode)
                val unconfiguredLanUrl =
                    captureApiException { client.signInWithPassword("http://192.168.1.20", "actual-test-password", "Phone") }
                assertEquals("invalid-server-url", unconfiguredLanUrl.errorCode)
                val malformedUrl =
                    captureApiException { client.signInWithPassword("https://tandem.example:invalid", "actual-test-password", "Phone") }
                assertEquals("invalid-server-url", malformedUrl.errorCode)
                val blankHostUrl = captureApiException { client.signInWithPassword("https://?budget=1", "actual-test-password", "Phone") }
                assertEquals("invalid-server-url", blankHostUrl.errorCode)

                val unauthorized =
                    captureApiException { client.signInWithPassword("https://tandem.example", "actual-test-password", "Phone") }
                assertEquals("invalid_credentials", unauthorized.errorCode)
                assertEquals(401, unauthorized.httpStatus)
                assertEquals("That password doesn’t match this Actual server. Check the password.", unauthorized.message)

                status = HttpStatusCode.BadRequest
                responseBody = """{"error":{"code":"invalid_request","message":"private server detail"}}"""
                val rejected = captureApiException { client.signInWithPassword("https://tandem.example", "wrong-test-password", "Phone") }
                assertEquals("invalid_request", rejected.errorCode)
                assertEquals(400, rejected.httpStatus)
                assertEquals("Tandem couldn’t start sign-in. Check the server address and try again.", rejected.message)
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
    fun clientAllowsHttpOnlyForPrivateNetworkHosts() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(signedInResponse(), headers = jsonHeaders())
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient(allowInsecurePrivateNetwork = true)
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                client.signInWithPassword("http://192.168.1.20:19753", "actual-test-password", "Phone")
                val requestUrl = requests.last().url
                assertEquals("192.168.1.20", requestUrl.host)
                assertEquals("http", requestUrl.protocol.name)
                client.signInWithPassword("http://10.0.0.12:19753", "actual-test-password", "Phone")
                client.signInWithPassword("http://172.31.0.8:19753", "actual-test-password", "Phone")
                client.signInWithPassword("http://[fd12::1]:19753", "actual-test-password", "Phone")
                client.signInWithPassword("http://[fe80::1]:19753", "actual-test-password", "Phone")
                client.signInWithPassword("http://localhost:19753", "actual-test-password", "Phone")
                client.signInWithPassword("http://tandem.localhost:19753", "actual-test-password", "Phone")
                client.signInWithPassword("http://[fc00:0000:0000:0000:0000:0000:0000:0001]:19753", "actual-test-password", "Phone")
                client.signInWithPassword("http://[fe80:0000:0000:0000:0000:0000:0000:0001]:19753", "actual-test-password", "Phone")

                listOf(
                    "http://1.1.1.1:19753",
                    "http://tandem.example:19753",
                    "http://tandem.home:19753",
                    "http://tandem.lan:19753",
                    "http://actual.local:19753",
                    "http://fe80:19753",
                    "http://[2001:0db8:0000:0000:0000:0000:0000:0001]:19753",
                    "http://fd12:19753",
                    "http://fe80::not-an-address:19753",
                    "http://192.168..1:19753",
                    "http://192.168.001.1:19753",
                ).forEach { address ->
                    val rejected = captureApiException { client.signInWithPassword(address, "actual-test-password", "Phone") }
                    assertEquals("invalid-server-url", rejected.errorCode, address)
                }
                assertEquals(9, requests.size)
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun serverRedirectsAreNotFollowed() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(
                        "",
                        HttpStatusCode.Found,
                        headersOf(HttpHeaders.Location, "http://public.example/api/v1/auth/login"),
                    )
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                captureApiException { client.signInWithPassword("https://tandem.example", "actual-test-password", "Phone") }
                assertEquals(1, requests.size)
                assertEquals("tandem.example", requests.single().url.host)
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun rejectedBudgetCatalogRequestsExplainSignInOrAccessScope() =
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
                assertEquals("Tandem can’t verify this device. Sign in again.", unauthorized.message)
                assertFalse(unauthorized.message.orEmpty().contains("private server detail"))

                responseBody = """{"error":{"code":"invalid_device_credential","message":"private server detail"}}"""
                val invalidCredential = captureApiException { client.listBudgets() }
                assertEquals("invalid_device_credential", invalidCredential.errorCode)
                assertEquals(401, invalidCredential.httpStatus)
                assertEquals("Your sign-in is no longer active. Sign in again.", invalidCredential.message)
                assertFalse(invalidCredential.message.orEmpty().contains("private server detail"))

                responseBody = """{"error":{"message":"private server detail"}}"""
                status = HttpStatusCode.Forbidden
                val forbidden = captureApiException { client.listBudgets() }
                assertEquals("http-403", forbidden.errorCode)
                assertEquals(403, forbidden.httpStatus)
                assertEquals("This device doesn’t have access to this budget. Ask the budget owner to grant access.", forbidden.message)

                status = HttpStatusCode.BadGateway
                val unavailable = captureApiException { client.listBudgets() }
                assertEquals(502, unavailable.httpStatus)
                assertEquals("Tandem is temporarily unavailable. Try again shortly.", unavailable.message)
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
    fun malformedSignInAndBudgetPayloadsAreRejectedAsProtocolErrors() =
        runBlocking {
            var response = """{"deviceId":"device-1","principalId":"owner","budgetIds":[""] ,"accessToken":"token"}"""
            var path = "/api/v1/auth/login"
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
                val badSignInResponse =
                    captureProtocolException { client.signInWithPassword("https://tandem.example", "actual-test-password", "Phone") }
                assertEquals("Server response contains an invalid budgetIds entry", badSignInResponse.message)

                response = """{"deviceId":"device-1","principalId":"owner","budgetIds":"budget-1","accessToken":"token"}"""
                val badBudgetIds =
                    captureProtocolException { client.signInWithPassword("https://tandem.example", "actual-test-password", "Phone") }
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
    fun budgetMaintenanceRequestsForwardPasswordsAndSendExpectedBodies() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(
                        if (request.url.encodedPath.endsWith("/categories") && request.method == HttpMethod.Post) {
                            """{"status":"ok","categoryId":"category-created"}"""
                        } else if (request.url.encodedPath.endsWith("/accounts") && request.method == HttpMethod.Post) {
                            """{"status":"ok","accountId":"account-created"}"""
                        } else if (request.url.encodedPath.endsWith("/payees") && request.method == HttpMethod.Post) {
                            """{"status":"ok","payeeId":"payee-created"}"""
                        } else if (request.url.encodedPath.endsWith("/category-groups") && request.method == HttpMethod.Post) {
                            """{"status":"ok","categoryGroupId":"group-created"}"""
                        } else {
                            """{"status":"ok"}"""
                        },
                        headers = jsonHeaders(),
                    )
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                client.restoreSession("https://tandem.example", "device-token")
                client.runBudgetBankSync("budget-1", null)
                assertEquals(
                    "account-created",
                    client.createBudgetAccount("budget-1", "Savings", true, "credit-group", "budget-password"),
                )
                client.closeBudgetAccount("budget-1", "account-1", "account-2", "category-1", "budget-password")
                client.renameBudgetAccount("budget-1", "account-1", "Daily checking", null)
                client.reopenBudgetAccount("budget-1", "account-1", null)
                client.renameBudgetCategory("budget-1", "category-1", "Home", null)
                assertEquals("category-created", client.createBudgetCategory("budget-1", "group-1", "Dining", null))
                assertEquals("payee-created", client.createBudgetPayee("budget-1", "Corner Market", null))
                client.setBudgetCategoryAmount("budget-1", "category-1", "2026-11", 125_000, "budget-password")
                client.setBudgetCategoryCarryover("budget-1", "category-1", "2026-11", true, "budget-password")
                client.copyBudgetMonth("budget-1", "2026-11", "budget-password")
                client.moveMoneyBetweenCategories("budget-1", "2026-11", "category-1", "category-2", 12_500, "budget-password")
                client.deleteBudgetCategory("budget-1", "category-1", null, null)
                client.manageBudgetSchedule("budget-1", "schedule-1", "rename", "Rent", "budget-password")
                client.manageBudgetSchedule(
                    "budget-1",
                    "schedule-1",
                    "skip",
                    null,
                    null,
                    expectedNextDate = "2026-11-15",
                    idempotencyKeyOverride = "00000000-0000-4000-8000-000000000001",
                )
                client.manageBudgetSchedule(
                    "budget-1",
                    "schedule-1",
                    "post",
                    null,
                    null,
                    expectedNextDate = "2026-11-15",
                    idempotencyKeyOverride = "00000000-0000-4000-8000-000000000002",
                )
                client.manageBudgetSchedule("budget-1", "schedule-1", "delete", null, null)
                client.manageBudgetSchedule(
                    "budget-1",
                    "schedule-1",
                    "edit",
                    null,
                    "budget-password",
                    buildJsonObject {
                        put("accountId", "account-1")
                        put("payeeId", "payee-1")
                        put("amountMinor", -12_500)
                        put("amountOp", "is")
                        put(
                            "date",
                            buildJsonObject {
                                put("frequency", "monthly")
                                put("start", "2026-12-01")
                            },
                        )
                    },
                )
                client.manageBudgetPayee("budget-1", "payee-1", "rename", "Store", null, null)
                client.manageBudgetPayee("budget-1", "payee-1", "merge", null, "payee-2", null)
                client.manageBudgetPayee("budget-1", "payee-1", "delete", null, null, "budget-password")
                client.manageBudgetPayee("budget-1", "payee-1", "default-category", null, null, "budget-password", null)
                client.holdBudgetForNextMonth("budget-1", "2026-11", 12_500, "budget-password")
                client.resetBudgetHold("budget-1", "2026-11", null)
                client.withGeneratedApi {
                    BudgetsApi.runBudgetBankSync(idempotencyKey = "direct-1", budgetId = "budget-1")
                    BudgetsApi.createBudgetAccount(idempotencyKey = "direct-2", budgetId = "budget-1")
                    BudgetsApi.closeBudgetAccount(idempotencyKey = "direct-3", budgetId = "budget-1", accountId = "account-1")
                    BudgetsApi.renameBudgetAccount(idempotencyKey = "direct-4", budgetId = "budget-1", accountId = "account-1")
                    BudgetsApi.reopenBudgetAccount(idempotencyKey = "direct-5", budgetId = "budget-1", accountId = "account-1")
                    BudgetsApi.renameBudgetCategory(idempotencyKey = "direct-6", budgetId = "budget-1", categoryId = "category-1")
                    BudgetsApi.createBudgetCategory(idempotencyKey = "direct-7", budgetId = "budget-1")
                    BudgetsApi.createBudgetPayee(idempotencyKey = "direct-8", budgetId = "budget-1")
                    BudgetsApi.setBudgetCategoryAmount(idempotencyKey = "direct-9", budgetId = "budget-1", categoryId = "category-1")
                    BudgetsApi.setBudgetCategoryCarryover(
                        idempotencyKey = "direct-10",
                        budgetId = "budget-1",
                        categoryId = "category-1",
                        body =
                            buildJsonObject {
                                put("month", "2026-11")
                                put("enabled", true)
                            }.toMap(),
                    )
                    BudgetsApi.manageBudgetMonth(
                        idempotencyKey = "direct-11",
                        budgetId = "budget-1",
                        month = "2026-11",
                        body = buildJsonObject { put("action", "copy-last-month") }.toMap(),
                    )
                    BudgetsApi.moveMoneyBetweenCategories(idempotencyKey = "direct-12", budgetId = "budget-1")
                    BudgetsApi.deleteBudgetCategory(idempotencyKey = "direct-13", budgetId = "budget-1", categoryId = "category-1")
                    BudgetsApi.manageBudgetSchedule(
                        idempotencyKey = "direct-14",
                        budgetId = "budget-1",
                        scheduleId = "schedule-1",
                        body =
                            buildJsonObject {
                                put("action", "skip")
                                put("expectedNextDate", "2026-11-15")
                            }.toMap(),
                    )
                    BudgetsApi.manageBudgetPayee(
                        idempotencyKey = "direct-15",
                        budgetId = "budget-1",
                        payeeId = "payee-1",
                        body = buildJsonObject { put("action", "delete") }.toMap(),
                    )
                    BudgetsApi.getBudgetSnapshot(budgetId = "budget-1")
                    BudgetsApi.getBudgetSnapshotPage(budgetId = "budget-1")
                    BudgetsApi.getBudgetChanges(budgetId = "budget-1", generation = "generation", cursor = "0")
                    TransactionsApi.getBudgetTransaction(budgetId = "budget-1", transactionId = "transaction-1")
                }
                client.moveBudgetAccount("budget-1", "account-1", "account-2", "budget-password")
                assertEquals("group-created", client.createBudgetCategoryGroup("budget-1", "Needs", false, "budget-password"))
                client.manageBudgetCategoryGroup("budget-1", "group-1", "rename", "Essentials", null, "budget-password")
                client.manageBudgetCategoryGroup("budget-1", "group-2", "move", null, "group-1", null)
                client.manageBudgetCategory("budget-1", "category-1", "move", "category-2", "budget-password")
                client.manageBudgetCategory("budget-1", "category-3", "hide", null, null)
                client.applyBudgetTemplate("budget-1", "2026-11", listOf("category-1" to 10_000L), "budget-password")

                assertEquals(
                    listOf(
                        "POST /api/v1/budgets/budget-1/bank-sync",
                        "POST /api/v1/budgets/budget-1/accounts",
                        "POST /api/v1/budgets/budget-1/accounts/account-1/close",
                        "PATCH /api/v1/budgets/budget-1/accounts/account-1",
                        "POST /api/v1/budgets/budget-1/accounts/account-1/reopen",
                        "PATCH /api/v1/budgets/budget-1/categories/category-1",
                        "POST /api/v1/budgets/budget-1/categories",
                        "POST /api/v1/budgets/budget-1/payees",
                        "PUT /api/v1/budgets/budget-1/categories/category-1/budget-amount",
                        "PUT /api/v1/budgets/budget-1/categories/category-1/carryover",
                        "POST /api/v1/budgets/budget-1/months/2026-11/actions",
                        "POST /api/v1/budgets/budget-1/categories/move-money",
                        "DELETE /api/v1/budgets/budget-1/categories/category-1",
                        "POST /api/v1/budgets/budget-1/schedules/schedule-1/actions",
                        "POST /api/v1/budgets/budget-1/schedules/schedule-1/actions",
                        "POST /api/v1/budgets/budget-1/schedules/schedule-1/actions",
                        "POST /api/v1/budgets/budget-1/schedules/schedule-1/actions",
                        "POST /api/v1/budgets/budget-1/schedules/schedule-1/actions",
                        "POST /api/v1/budgets/budget-1/payees/payee-1/actions",
                        "POST /api/v1/budgets/budget-1/payees/payee-1/actions",
                        "POST /api/v1/budgets/budget-1/payees/payee-1/actions",
                        "POST /api/v1/budgets/budget-1/payees/payee-1/actions",
                        "POST /api/v1/budgets/budget-1/months/2026-11/actions",
                        "POST /api/v1/budgets/budget-1/months/2026-11/actions",
                    ),
                    requests.take(24).map { "${it.method.value} ${it.url.encodedPath}" },
                )
                assertEquals(null, requests[0].headers["x-budget-encryption-password"])
                assertEquals("budget-password", requests[1].headers["x-budget-encryption-password"])
                assertEquals("budget-password", requests[2].headers["x-budget-encryption-password"])
                assertEquals("budget-password", requests[8].headers["x-budget-encryption-password"])
                assertEquals("budget-password", requests[9].headers["x-budget-encryption-password"])
                assertEquals("budget-password", requests[10].headers["x-budget-encryption-password"])
                val idempotencyKeys = requests.take(24).map { it.headers["Idempotency-Key"] }
                assertTrue(idempotencyKeys.all { key -> key?.let { runCatching { Uuid.parse(it) }.isSuccess } == true })
                assertEquals(24, idempotencyKeys.distinct().size)
                assertTrue((requests[1].body as TextContent).text.contains("\"name\":\"Savings\""))
                assertTrue((requests[1].body as TextContent).text.contains("\"offbudget\":true"))
                assertTrue((requests[1].body as TextContent).text.contains("\"accountGroupId\":\"credit-group\""))
                assertTrue((requests[2].body as TextContent).text.contains("\"transferAccountId\":\"account-2\""))
                assertTrue((requests[2].body as TextContent).text.contains("\"transferCategoryId\":\"category-1\""))
                assertTrue((requests[3].body as TextContent).text.contains("\"name\":\"Daily checking\""))
                assertTrue((requests[5].body as TextContent).text.contains("\"name\":\"Home\""))
                assertTrue((requests[6].body as TextContent).text.contains("\"categoryGroupId\":\"group-1\""))
                assertTrue((requests[7].body as TextContent).text.contains("\"name\":\"Corner Market\""))
                assertTrue((requests[8].body as TextContent).text.contains("\"month\":\"2026-11\""))
                assertTrue((requests[8].body as TextContent).text.contains("\"amountMinor\":125000"))
                assertTrue((requests[9].body as TextContent).text.contains("\"enabled\":true"))
                assertTrue((requests[10].body as TextContent).text.contains("\"action\":\"copy-last-month\""))
                assertTrue((requests[11].body as TextContent).text.contains("\"fromCategoryId\":\"category-1\""))
                assertTrue((requests[11].body as TextContent).text.contains("\"toCategoryId\":\"category-2\""))
                assertTrue((requests[12].body as TextContent).text.contains("\"transferCategoryId\":null"))
                assertEquals("budget-password", requests[13].headers["x-budget-encryption-password"])
                assertTrue((requests[13].body as TextContent).text.contains("\"action\":\"rename\""))
                assertTrue((requests[13].body as TextContent).text.contains("\"name\":\"Rent\""))
                assertTrue((requests[14].body as TextContent).text.contains("\"action\":\"skip\""))
                assertTrue((requests[14].body as TextContent).text.contains("\"expectedNextDate\":\"2026-11-15\""))
                assertEquals("00000000-0000-4000-8000-000000000001", requests[14].headers["Idempotency-Key"])
                assertTrue((requests[15].body as TextContent).text.contains("\"action\":\"post\""))
                assertTrue((requests[15].body as TextContent).text.contains("\"expectedNextDate\":\"2026-11-15\""))
                assertEquals("00000000-0000-4000-8000-000000000002", requests[15].headers["Idempotency-Key"])
                assertTrue((requests[16].body as TextContent).text.contains("\"action\":\"delete\""))
                assertTrue((requests[17].body as TextContent).text.contains("\"action\":\"edit\""))
                assertTrue((requests[17].body as TextContent).text.contains("\"accountId\":\"account-1\""))
                assertTrue((requests[17].body as TextContent).text.contains("\"amountMinor\":-12500"))
                assertEquals("budget-password", requests[17].headers["x-budget-encryption-password"])
                assertTrue((requests[18].body as TextContent).text.contains("\"action\":\"rename\""))
                assertTrue((requests[18].body as TextContent).text.contains("\"name\":\"Store\""))
                assertTrue((requests[19].body as TextContent).text.contains("\"targetPayeeId\":\"payee-2\""))
                assertEquals("budget-password", requests[20].headers["x-budget-encryption-password"])
                assertTrue((requests[21].body as TextContent).text.contains("\"action\":\"default-category\""))
                assertTrue((requests[21].body as TextContent).text.contains("\"categoryId\":null"))
                assertEquals("budget-password", requests[21].headers["x-budget-encryption-password"])
                assertTrue((requests[22].body as TextContent).text.contains("\"action\":\"hold-next-month\""))
                assertTrue((requests[22].body as TextContent).text.contains("\"amountMinor\":12500"))
                assertEquals("budget-password", requests[22].headers["x-budget-encryption-password"])
                assertTrue((requests[23].body as TextContent).text.contains("\"action\":\"reset-hold\""))
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
                assertEquals("Tandem isn’t responding at this address. Check the server address.", failure.message)
                assertFalse(failure.message.orEmpty().contains("private transport detail"))
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun categoryCreationOperationsMapGeneratedApiErrors() =
        runBlocking {
            val engine =
                MockEngine {
                    respond(
                        """{"error":{"code":"invalid_request"}}""",
                        HttpStatusCode.BadRequest,
                        jsonHeaders(),
                    )
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                client.restoreSession("https://tandem.example", "device-token")
                val categoryError =
                    captureApiException {
                        client.createBudgetCategory("budget-1", "group-1", "Dining", null)
                    }
                val groupError =
                    captureApiException {
                        client.createBudgetCategoryGroup("budget-1", "Needs", false, null)
                    }
                val payeeError = captureApiException { client.createBudgetPayee("budget-1", "Market", null) }
                assertEquals("invalid_request", categoryError.errorCode)
                assertEquals("invalid_request", groupError.errorCode)
                assertEquals("invalid_request", payeeError.errorCode)
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun maintenanceErrorsUseSafeMessagesForActualAndHttpFailures() =
        runBlocking {
            var status = HttpStatusCode.BadGateway
            var responseBody = """{"error":{"code":"bank_sync_failed","message":"private detail"}}"""
            val engine = MockEngine { respond(responseBody, status, jsonHeaders()) }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                client.restoreSession("https://tandem.example", "device-token")
                val bankFailure = captureApiException { client.runBudgetBankSync("budget-1", null) }
                assertEquals("bank_sync_failed", bankFailure.errorCode)
                assertEquals("Actual couldn’t sync bank transactions. Check the bank connection.", bankFailure.message)
                assertFalse(bankFailure.message.orEmpty().contains("private detail"))

                status = HttpStatusCode.InternalServerError
                responseBody = """{"error":{"code":"actual_operation_failed","message":"private detail"}}"""
                val operationFailure = captureApiException { client.createBudgetAccount("budget-1", "Savings", false, null, null) }
                assertEquals("actual_operation_failed", operationFailure.errorCode)
                assertEquals("Actual couldn’t save this change. Refresh the budget before trying again.", operationFailure.message)

                status = HttpStatusCode.BadRequest
                responseBody = """{"error":{"message":"private detail"}}"""
                val requestFailure = captureApiException { client.renameBudgetCategory("budget-1", "category-1", "Home", null) }
                assertEquals("http-400", requestFailure.errorCode)
                assertEquals("Tandem couldn’t accept this request. Check the details.", requestFailure.message)
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun maintenanceResubmissionAfterTerminalConflictUsesFreshIdempotencyKey() =
        runBlocking {
            for (errorCode in listOf("operation_uncertain", "operation_rejected")) {
                val requests = mutableListOf<HttpRequestData>()
                var responseCount = 0
                val engine =
                    MockEngine { request ->
                        requests += request
                        if (responseCount++ == 0) {
                            respond(
                                """{"error":{"code":"$errorCode"}}""",
                                HttpStatusCode.Conflict,
                                jsonHeaders(),
                            )
                        } else {
                            respond(
                                """{"status":"ok","accountId":"account-created"}""",
                                headers = jsonHeaders(),
                            )
                        }
                    }
                val previousClient = Api.client
                val previousBaseUrl = Api.baseUrl
                val client = TandemApiClient()
                val cleanup = configureApi(engine, client.bearerAuth)
                try {
                    client.restoreSession("https://tandem.example", "device-token")
                    val failure =
                        captureApiException {
                            client.createBudgetAccount("budget-1", "Savings", false, null, null)
                        }
                    assertEquals(errorCode, failure.errorCode)
                    client.createBudgetAccount("budget-1", "Savings", false, null, null)
                    assertEquals(2, requests.size)
                    assertNotEquals(
                        requests[0].headers["Idempotency-Key"],
                        requests[1].headers["Idempotency-Key"],
                    )
                } finally {
                    cleanup()
                    Api.baseUrl = previousBaseUrl
                    Api.client.close()
                    previousClient.close()
                }
            }
        }

    @Test
    fun accountCreationRejectsUnexpectedSuccessStatus() =
        runBlocking {
            val engine =
                MockEngine {
                    respond("""{"status":"failed","accountId":"account-created"}""", headers = jsonHeaders())
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                client.restoreSession("https://tandem.example", "device-token")

                val error =
                    assertFailsWith<TandemApiProtocolException> {
                        client.createBudgetAccount("budget-1", "Savings", false, null, null)
                    }

                assertEquals("Account creation response has an invalid status", error.message)
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun maintenanceRetryAfterTemporaryServerFailureReusesIdempotencyKey() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            var responseCount = 0
            val engine =
                MockEngine { request ->
                    requests += request
                    if (responseCount++ == 0) {
                        respond(
                            """{"error":{"code":"actual_operation_failed"}}""",
                            HttpStatusCode.ServiceUnavailable,
                            jsonHeaders(),
                        )
                    } else {
                        respond("""{"status":"ok","accountId":"account-created"}""", headers = jsonHeaders())
                    }
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                client.restoreSession("https://tandem.example", "device-token")
                assertFailsWith<TandemApiException> {
                    client.createBudgetAccount("budget-1", "Savings", false, null, null)
                }
                client.createBudgetAccount("budget-1", "Savings", false, null, null)

                assertEquals(2, requests.size)
                assertEquals(requests[0].headers["Idempotency-Key"], requests[1].headers["Idempotency-Key"])
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun missingContentEncodingIsInstalledBeforeGeneratedRequests() =
        runBlocking {
            val engine = MockEngine { respond(budgetsResponse(), headers = jsonHeaders()) }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth, installContentEncoding = false)
            try {
                assertNull(Api.client.pluginOrNull(ContentEncoding))
                GeneratedApiAccess.ensureContentEncoding()
                assertTrue(Api.client.pluginOrNull(ContentEncoding) != null)
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun malformedDeviceIdentityIsRejectedBeforeRevocation() =
        runBlocking {
            val client = TandemApiClient()
            client.restoreStoredSession(
                """{"serverUrl":"https://tandem.example","accessToken":"device-token","device":{"deviceId":"not-a-uuid","principalId":"person-1","budgetIds":["budget-1"]}}""",
            )

            val invalidId = captureProtocolException { client.revokeCurrentDevice() }

            assertEquals("Stored device ID is invalid", invalidId.message)
        }

    @Test
    fun unexpectedApiStatusIsReportedAsAProtocolError() =
        runBlocking {
            val error =
                captureProtocolException {
                    verifyApiStatus(isSuccessful = true, hasExpectedStatus = false)
                }

            assertEquals("The server returned an unexpected response", error.message)
        }

    @Test
    fun unsuccessfulDeviceRevocationIsRejected() {
        val error = assertFailsWith<TandemApiException> { verifyDeviceRevocationSuccessful(false) }

        assertEquals("device-revocation-failed", error.errorCode)
    }

    @Test
    fun duplicateStatementReferencesHaveAnActionableMessage() {
        val error = TandemApiException("duplicate_imported_id", 400)

        assertEquals("Statement rows share a reference. Map a column with unique values.", error.message)
    }

    @Test
    fun budgetMutationErrorCodesHaveDistinctActionableMessages() {
        val messages =
            mapOf(
                "operation_uncertain" to "Tandem can’t confirm whether Actual saved this change. Check Actual before changing it again.",
                "operation_in_progress" to "Tandem is still processing this change. Wait for it to finish.",
                "operation_rejected" to "Actual rejected this change earlier. Refresh the budget before trying again.",
                "idempotency_key_reused" to "This change is linked to a different request. Refresh the budget before trying again.",
                "operation_result_unavailable" to
                    "Actual saved this change, but Tandem hasn’t refreshed the budget. Sync the budget to update this device.",
            )

        messages.forEach { (errorCode, message) ->
            assertEquals(message, TandemApiException(errorCode, httpStatus = 409).message, errorCode)
        }
    }

    @Test
    fun authenticationAndBankSyncErrorsHaveActionableMessages() {
        val messages =
            mapOf(
                "actual_only" to "We found your Actual Budget service. Make sure the Tandem API Gateway is running at /api/v1/.",
                "invalid_budget_password" to "That password didn’t unlock this encrypted budget. Check the budget password.",
                "sign_in_expired" to "Your sign-in is no longer active. Sign in again.",
                "invalid_device_credential" to "Your sign-in is no longer active. Sign in again.",
                "bank_sync_failed" to "Actual couldn’t sync bank transactions. Check the bank connection.",
                "bank_sync_busy" to "Bank sync is already running. Wait a moment.",
                "statement_preview_failed" to "Tandem couldn’t check which statement rows are already in Actual. Check your connection.",
            )

        messages.forEach { (errorCode, message) ->
            assertEquals(message, TandemApiException(errorCode).message, errorCode)
        }
    }

    @Test
    fun importingStatementTransactionsPreservesMinorUnitsAndValidatesRequestsAndResponses() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            var responseBody = """{"status":"ok","added":1,"updated":2,"errorCount":0}"""
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(responseBody, headers = jsonHeaders())
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                client.restoreSession("https://tandem.example", "device-token")
                val transaction = StatementImportItem("2026-11-08", -1170, "Coffee Shop", "Lunch", "csv:row-1")
                val yenTransaction = StatementImportItem("2026-11-08", 1_500, "Market", null, "csv:row-2")
                val threeDecimalTransaction = StatementImportItem("2026-11-08", -12_345, "Transit", null, "csv:row-3")
                val matchingTransaction = transaction.copy()
                assertEquals(transaction, matchingTransaction)
                assertEquals(transaction.hashCode(), matchingTransaction.hashCode())
                assertEquals("2026-11-08", transaction.component1())
                assertEquals(-1170L, transaction.component2())
                assertEquals("Coffee Shop", transaction.component3())
                assertEquals("Lunch", transaction.component4())
                assertEquals("csv:row-1", transaction.component5())
                val summary = StatementImportSummary(1, 2, 0)
                assertEquals(summary, summary.copy())
                assertEquals(summary.hashCode(), summary.copy().hashCode())
                assertEquals(1, summary.component1())
                assertEquals(2, summary.component2())
                assertEquals(0, summary.component3())

                assertFailsWith<IllegalArgumentException> {
                    client.importBudgetAccountTransactions("budget-1", "account-1", emptyList(), null)
                }
                assertFailsWith<IllegalArgumentException> {
                    client.importBudgetAccountTransactions("budget-1", "account-1", List(1001) { transaction }, null)
                }

                assertEquals(
                    StatementImportSummary(1, 2, 0),
                    client.importBudgetAccountTransactions(
                        "budget-1",
                        "account-1",
                        listOf(transaction, yenTransaction, threeDecimalTransaction),
                        "budget-password",
                    ),
                )
                val request = requests.single()
                assertEquals("POST", request.method.value)
                assertEquals("/api/v1/budgets/budget-1/accounts/account-1/transactions/import", request.url.encodedPath)
                assertEquals("budget-password", request.headers["x-budget-encryption-password"])
                val requestBody = (request.body as TextContent).text
                assertTrue(requestBody.contains("\"amountMinor\":-1170"))
                assertTrue(requestBody.contains("\"amountMinor\":1500"))
                assertTrue(requestBody.contains("\"amountMinor\":-12345"))
                assertTrue(requestBody.contains("\"payeeName\":\"Coffee Shop\""))
                assertTrue(requestBody.contains("\"notes\":\"Lunch\""))
                assertTrue(requestBody.contains("\"importedId\":\"csv:row-1\""))

                responseBody = """{"status":"failed"}"""
                assertFailsWith<TandemApiProtocolException> {
                    client.importBudgetAccountTransactions(
                        "budget-1",
                        "account-1",
                        listOf(transaction.copy(importedId = "csv:bad-status")),
                        null,
                    )
                }
                responseBody = """{"status":"ok","added":"bad","updated":0,"errorCount":0}"""
                assertFailsWith<TandemApiProtocolException> {
                    client.importBudgetAccountTransactions(
                        "budget-1",
                        "account-1",
                        listOf(transaction.copy(importedId = "csv:bad-added")),
                        null,
                    )
                }
                responseBody = """{"status":"ok","added":0,"updated":"bad","errorCount":0}"""
                assertFailsWith<TandemApiProtocolException> {
                    client.importBudgetAccountTransactions(
                        "budget-1",
                        "account-1",
                        listOf(transaction.copy(importedId = "csv:bad-updated")),
                        null,
                    )
                }
                responseBody = """{"status":"ok","added":0,"updated":0,"errorCount":"bad"}"""
                assertFailsWith<TandemApiProtocolException> {
                    client.importBudgetAccountTransactions(
                        "budget-1",
                        "account-1",
                        listOf(transaction.copy(importedId = "csv:bad-errors")),
                        null,
                    )
                }
                Unit
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun previewsStatementImportReferencesWithoutAnIdempotencyKey() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            var responseBody = """{"status":"ok","existingImportedIds":["ref-existing"]}"""
            var responseStatus = HttpStatusCode.OK
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(responseBody, status = responseStatus, headers = jsonHeaders())
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                client.restoreSession("https://tandem.example", "device-token")

                assertFailsWith<IllegalArgumentException> {
                    client.previewBudgetAccountStatementImport("budget-1", "account-1", emptyList(), null)
                }
                assertFailsWith<IllegalArgumentException> {
                    client.previewBudgetAccountStatementImport("budget-1", "account-1", listOf("same", "same"), null)
                }
                val preview =
                    client.previewBudgetAccountStatementImport(
                        "budget-1",
                        "account-1",
                        listOf("ref-new", "ref-existing"),
                        "budget-password",
                    )

                assertEquals(setOf("ref-existing"), preview.existingImportedIds)
                assertEquals(1, preview.newRowCount(2))
                val request = requests.single()
                assertEquals("POST", request.method.value)
                assertEquals(
                    "/api/v1/budgets/budget-1/accounts/account-1/transactions/import/preview",
                    request.url.encodedPath,
                )
                assertEquals("budget-password", request.headers["x-budget-encryption-password"])
                assertEquals(null, request.headers["idempotency-key"])
                assertEquals("{\"importedIds\":[\"ref-new\",\"ref-existing\"]}", (request.body as TextContent).text)

                responseBody = """{"status":"ok","existingImportedIds":["unexpected"]}"""
                assertFailsWith<TandemApiProtocolException> {
                    client.previewBudgetAccountStatementImport("budget-1", "account-1", listOf("ref-new"), null)
                }
                responseBody = """{"status":"ok"}"""
                val missingReferences =
                    assertFailsWith<TandemApiProtocolException> {
                        client.previewBudgetAccountStatementImport("budget-1", "account-1", listOf("ref-new"), null)
                    }
                assertEquals("Statement preview response is missing its references", missingReferences.message)

                responseBody = """{"error":{"code":"statement_preview_failed"}}"""
                responseStatus = HttpStatusCode.ServiceUnavailable
                val previewFailure =
                    assertFailsWith<TandemApiException> {
                        client.previewBudgetAccountStatementImport("budget-1", "account-1", listOf("ref-new"), null)
                    }
                assertEquals(
                    "Tandem couldn’t check which statement rows are already in Actual. Check your connection.",
                    previewFailure.message,
                )
                Unit
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun generatedStatementPreviewUsesOptionalRequestDefaults() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond("""{"status":"ok","existingImportedIds":[]}""", headers = jsonHeaders())
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                TransactionsApi.previewBudgetAccountStatementImport(
                    budgetId = "budget-1",
                    accountId = "account-1",
                )
                TransactionsApi.previewBudgetAccountStatementImport(
                    xBudgetEncryptionPassword = "budget-secret",
                    budgetId = "budget-2",
                    accountId = "account-2",
                    body = emptyMap(),
                )

                assertEquals(2, requests.size)
                assertEquals(
                    "/api/v1/budgets/budget-1/accounts/account-1/transactions/import/preview",
                    requests[0].url.encodedPath,
                )
                assertEquals(null, requests[0].headers["x-budget-encryption-password"])
                assertEquals("{}", (requests[0].body as TextContent).text)
                assertEquals(
                    "/api/v1/budgets/budget-2/accounts/account-2/transactions/import/preview",
                    requests[1].url.encodedPath,
                )
                assertEquals("budget-secret", requests[1].headers["x-budget-encryption-password"])
                assertEquals("{}", (requests[1].body as TextContent).text)
            } finally {
                cleanup()
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }

    @Test
    fun revocationMapsApiErrorsAndUnexpectedHttpStatuses() =
        runBlocking {
            var status = HttpStatusCode.Unauthorized
            var responseBody = """{"error":{"code":"invalid_device_credential","message":"private detail"}}"""
            val engine = MockEngine { respond(responseBody, status, jsonHeaders()) }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                val deviceId = "6d860805-fdd6-4fe4-8e67-668c9900fe1f"
                client.restoreStoredSession(
                    """{"serverUrl":"https://tandem.example","accessToken":"device-token","device":{"deviceId":"$deviceId","principalId":"person-1","budgetIds":["budget-1"]}}""",
                )
                val unauthorized = captureApiException { client.revokeCurrentDevice() }
                assertEquals("invalid_device_credential", unauthorized.errorCode)
                assertEquals(401, unauthorized.httpStatus)
                assertFalse(unauthorized.message.orEmpty().contains("private detail"))

                status = HttpStatusCode.PaymentRequired
                responseBody = "{}"
                val unexpectedStatus = captureApiException { client.revokeCurrentDevice() }
                assertEquals("http-402", unexpectedStatus.errorCode)
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
        installContentEncoding: Boolean = true,
    ): () -> Unit {
        Api.baseUrl = Url("https://tandem.example")
        Api.setAuthProvider(auth)
        Api.updateClient(
            createHttpClient = {
                HttpClient(engine) {
                    followRedirects = false
                    install(ContentNegotiation) { json(Api.json) }
                    if (installContentEncoding) {
                        install(ContentEncoding) {
                            gzip()
                            deflate()
                        }
                    }
                    install(DefaultRequest) { url.takeFrom(Api.baseUrl) }
                    install(AuthPlugin) {
                        authItem { key -> if (key == auth.key) auth.provideAuthItem() else null }
                    }
                }
            },
        )
        return { Api.clearAuthProvider(auth) }
    }

    private suspend fun readActualLoginMethods(responseBody: String): ActualLoginMethods {
        val engine = MockEngine { respond(responseBody, headers = jsonHeaders()) }
        val previousClient = Api.client
        val previousBaseUrl = Api.baseUrl
        val client = TandemApiClient()
        val cleanup = configureApi(engine, client.bearerAuth)
        return try {
            client.readActualLoginMethods("https://actual.example")
        } finally {
            cleanup()
            Api.baseUrl = previousBaseUrl
            Api.client.close()
            previousClient.close()
        }
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

    private fun signedInResponse() =
        """{"deviceId":"device-1","principalId":"household-owner","budgetIds":["budget-1","budget-2"],"accessToken":"test-device-token"}"""

    private fun budgetsResponse() =
        """{"budgets":[{"budgetId":"budget-1","name":"Household","isEncrypted":false},{"budgetId":"budget-2","name":"Travel","isEncrypted":true}]}"""
}
