package app.tandemforactual.api.client

import app.tandemforactual.api.generated.Api
import app.tandemforactual.api.generated.Auth
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
    fun deviceNamesAreTrimmedSanitizedAndLimitedToOneHundredCharacters() {
        assertEquals("Pixel 10a", normalizeDeviceName("\u0000 Pixel\n10a\u007f "))
        assertEquals(100, normalizeDeviceName("A".repeat(120)).length)
        val emojiName = normalizeDeviceName("😀".repeat(120))
        assertEquals(100, emojiName.codePointCount(0, emojiName.length))
        assertEquals("Mobile device", normalizeDeviceName("\u0000\u009f\uFEFF"))
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
            var responseBody = """{"error":{"code":"invalid_pairing_code","message":"private server detail"}}"""
            val engine =
                MockEngine { request ->
                    assertEquals("/api/v1/auth/pair", request.url.encodedPath)
                    respond(responseBody, status, jsonHeaders())
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
                val unconfiguredLanUrl = captureApiException { client.pair("http://192.168.1.20", "4DF7-J1M9", "Phone") }
                assertEquals("invalid-server-url", unconfiguredLanUrl.errorCode)
                val malformedUrl = captureApiException { client.pair("https://tandem.example:invalid", "4DF7-J1M9", "Phone") }
                assertEquals("invalid-server-url", malformedUrl.errorCode)
                val blankHostUrl = captureApiException { client.pair("https://?budget=1", "4DF7-J1M9", "Phone") }
                assertEquals("invalid-server-url", blankHostUrl.errorCode)

                val unauthorized = captureApiException { client.pair("https://tandem.example", "4DF7-J1M9", "Phone") }
                assertEquals("invalid_pairing_code", unauthorized.errorCode)
                assertEquals(401, unauthorized.httpStatus)
                assertEquals("The pairing code is invalid, expired, or already used.", unauthorized.message)

                status = HttpStatusCode.BadRequest
                responseBody = """{"error":{"code":"invalid_request","message":"private server detail"}}"""
                val rejected = captureApiException { client.pair("https://tandem.example", "NOPE-0000", "Phone") }
                assertEquals("invalid_request", rejected.errorCode)
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
    fun clientAllowsHttpOnlyForPrivateNetworkHosts() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(pairResponse(), headers = jsonHeaders())
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient(allowInsecurePrivateNetwork = true)
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                client.pair("http://192.168.1.20:19753", "4DF7-J1M9", "Phone")
                val requestUrl = requests.last().url
                assertEquals("192.168.1.20", requestUrl.host)
                assertEquals("http", requestUrl.protocol.name)
                client.pair("http://10.0.0.12:19753", "4DF7-J1M9", "Phone")
                client.pair("http://172.31.0.8:19753", "4DF7-J1M9", "Phone")
                client.pair("http://[fd12::1]:19753", "4DF7-J1M9", "Phone")
                client.pair("http://[fe80::1]:19753", "4DF7-J1M9", "Phone")
                client.pair("http://localhost:19753", "4DF7-J1M9", "Phone")

                listOf(
                    "http://1.1.1.1:19753",
                    "http://tandem.example:19753",
                    "http://tandem.home:19753",
                    "http://tandem.lan:19753",
                    "http://actual.local:19753",
                    "http://fe80:19753",
                    "http://fd12:19753",
                    "http://fe80::not-an-address:19753",
                    "http://192.168..1:19753",
                    "http://192.168.001.1:19753",
                ).forEach { address ->
                    val rejected = captureApiException { client.pair(address, "4DF7-J1M9", "Phone") }
                    assertEquals("invalid-server-url", rejected.errorCode, address)
                }
                assertEquals(6, requests.size)
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
                        headersOf(HttpHeaders.Location, "http://public.example/api/v1/auth/pair"),
                    )
                }
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val client = TandemApiClient()
            val cleanup = configureApi(engine, client.bearerAuth)
            try {
                captureApiException { client.pair("https://tandem.example", "4DF7-J1M9", "Phone") }
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
                assertEquals("The server couldn’t verify this request. Check the server address and try again.", unauthorized.message)
                assertFalse(unauthorized.message.orEmpty().contains("private server detail"))

                responseBody = """{"error":{"code":"invalid_device_credential","message":"private server detail"}}"""
                val invalidCredential = captureApiException { client.listBudgets() }
                assertEquals("invalid_device_credential", invalidCredential.errorCode)
                assertEquals(401, invalidCredential.httpStatus)
                assertEquals("This device needs to be paired again.", invalidCredential.message)
                assertFalse(invalidCredential.message.orEmpty().contains("private server detail"))

                responseBody = """{"error":{"message":"private server detail"}}"""
                status = HttpStatusCode.Forbidden
                val forbidden = captureApiException { client.listBudgets() }
                assertEquals("http-403", forbidden.errorCode)
                assertEquals(403, forbidden.httpStatus)
                assertEquals("This device can’t open that budget.", forbidden.message)

                status = HttpStatusCode.BadGateway
                val unavailable = captureApiException { client.listBudgets() }
                assertEquals(502, unavailable.httpStatus)
                assertEquals("The Tandem server couldn’t read Actual. Try again later.", unavailable.message)
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
    fun budgetMaintenanceRequestsForwardPasswordsAndSendExpectedBodies() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(
                        if (request.url.encodedPath.endsWith("/categories") && request.method == HttpMethod.Post) {
                            """{"status":"ok","categoryId":"category-created"}"""
                        } else if (request.url.encodedPath.endsWith("/payees") && request.method == HttpMethod.Post) {
                            """{"status":"ok","payeeId":"payee-created"}"""
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
                client.createBudgetAccount("budget-1", "Savings", true, "credit-group", "budget-password")
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
                client.manageBudgetSchedule("budget-1", "schedule-1", "skip", null, null)
                client.manageBudgetSchedule("budget-1", "schedule-1", "post", null, null)
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
                        body = buildJsonObject { put("action", "skip") }.toMap(),
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
                assertTrue((requests[15].body as TextContent).text.contains("\"action\":\"post\""))
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
                assertEquals("Couldn’t reach the Tandem server. Check the address and connection.", failure.message)
                assertFalse(failure.message.orEmpty().contains("private transport detail"))
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
                assertEquals("Actual couldn’t complete bank sync. Check its provider setup and try again.", bankFailure.message)
                assertFalse(bankFailure.message.orEmpty().contains("private detail"))

                status = HttpStatusCode.InternalServerError
                responseBody = """{"error":{"code":"actual_operation_failed","message":"private detail"}}"""
                val operationFailure = captureApiException { client.createBudgetAccount("budget-1", "Savings", false, null, null) }
                assertEquals("actual_operation_failed", operationFailure.errorCode)
                assertEquals("Actual couldn’t complete this change. Check the budget and try again.", operationFailure.message)

                status = HttpStatusCode.BadRequest
                responseBody = """{"error":{"message":"private detail"}}"""
                val requestFailure = captureApiException { client.renameBudgetCategory("budget-1", "category-1", "Home", null) }
                assertEquals("http-400", requestFailure.errorCode)
                assertEquals("The server rejected this request. Check the details and try again.", requestFailure.message)
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
                                """{"status":"ok"}""",
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
