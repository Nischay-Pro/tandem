package org.actualbudget.companion.api.generated

import com.kroegerama.openapi.kmp.gen.companion.AuthItem
import com.kroegerama.openapi.kmp.gen.companion.AuthPlugin
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.actualbudget.companion.api.generated.api.AuthenticationApi
import org.actualbudget.companion.api.generated.api.DefaultApi
import org.actualbudget.companion.api.generated.api.HealthApi
import org.actualbudget.companion.api.generated.api.TransactionsApi
import org.actualbudget.companion.api.generated.models.ApiV1HealthReady200Response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GeneratedApiSurfaceTest {
    @Test
    fun everyGeneratedOperationUsesItsDocumentedMethodPathAndSecurityHeaders() =
        runBlocking {
            val previousClient = Api.client
            val previousBaseUrl = Api.baseUrl
            val capturedRequests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    capturedRequests += request
                    val responseBody =
                        if (request.url.encodedPath == "/api/v1/health/ready") {
                            """{"status":"ok"}"""
                        } else {
                            "{}"
                        }
                    respond(
                        content = responseBody,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val auth = Auth.BearerAuth { AuthItem.Bearer("device-token") }

            try {
                Api.baseUrl = Url("https://companion.example")
                Api.setAuthProvider(auth)
                Api.updateClient(
                    createHttpClient = {
                        HttpClient(engine) {
                            install(ContentNegotiation) { json(Api.json) }
                            install(DefaultRequest) { url.takeFrom(Api.baseUrl) }
                            install(AuthPlugin) {
                                authItem { key ->
                                    if (key == auth.key) auth.provideAuthItem() else null
                                }
                            }
                        }
                    },
                )

                assertTrue(HealthApi.getLiveness().getOrNull()?.isSuccessful == true)
                val readinessResponse = assertNotNull(HealthApi.getReadiness().getOrNull())
                assertTrue(readinessResponse.isSuccessful)
                val readiness = readinessResponse.data
                assertEquals(ApiV1HealthReady200Response.Status.OK, readiness.status)
                assertEquals("""{"status":"ok"}""", Api.json.encodeToString(readiness))
                assertTrue(DefaultApi.getApiV1OpenapiJson().getOrNull()?.isSuccessful == true)
                assertTrue(
                    AuthenticationApi
                        .pairDevice(
                            buildJsonObject { put("code", "pairing-code") },
                        ).getOrNull()
                        ?.isSuccessful == true,
                )
                assertTrue(AuthenticationApi.getCurrentDevice().getOrNull()?.isSuccessful == true)
                assertTrue(AuthenticationApi.listDevices().getOrNull()?.isSuccessful == true)

                val deviceId = Uuid.parse("6d860805-fdd6-4fe4-8e67-668c9900fe1f")
                assertTrue(AuthenticationApi.revokeDevice(deviceId).getOrNull()?.isSuccessful == true)

                assertTrue(
                    TransactionsApi
                        .getBudgetTransaction(
                            xBudgetEncryptionPassword = "budget-secret",
                            budgetId = "budget-1",
                            transactionId = "transaction-1",
                        ).getOrNull()
                        ?.isSuccessful == true,
                )
                assertTrue(
                    TransactionsApi
                        .submitTransactionOperation(
                            xBudgetEncryptionPassword = "budget-secret",
                            idempotencyKey = "operation-key-1",
                            budgetId = "budget-1",
                            transactionId = "transaction-1",
                            body = buildJsonObject { put("type", "update") },
                        ).getOrNull()
                        ?.isSuccessful == true,
                )
                val operationId = Uuid.parse("6461dd06-cfe0-4854-9ced-16b7011fb87d")
                assertTrue(
                    TransactionsApi.getTransactionOperation(operationId).getOrNull()?.isSuccessful == true,
                )
                assertTrue(
                    TransactionsApi
                        .getBudgetTransaction(
                            budgetId = "budget-2",
                            transactionId = "transaction-2",
                        ).getOrNull()
                        ?.isSuccessful == true,
                )
                assertTrue(
                    TransactionsApi
                        .submitTransactionOperation(
                            idempotencyKey = "operation-key-defaults",
                            budgetId = "budget-2",
                            transactionId = "transaction-2",
                        ).getOrNull()
                        ?.isSuccessful == true,
                )

                assertEquals(
                    listOf(
                        HttpMethod.Get to "/api/v1/health/live",
                        HttpMethod.Get to "/api/v1/health/ready",
                        HttpMethod.Get to "/api/v1/openapi.json",
                        HttpMethod.Post to "/api/v1/auth/pair",
                        HttpMethod.Get to "/api/v1/auth/me",
                        HttpMethod.Get to "/api/v1/devices",
                        HttpMethod.Delete to "/api/v1/devices/$deviceId",
                        HttpMethod.Get to "/api/v1/budgets/budget-1/transactions/transaction-1",
                        HttpMethod.Post to "/api/v1/budgets/budget-1/transactions/transaction-1/operations",
                        HttpMethod.Get to "/api/v1/operations/$operationId",
                        HttpMethod.Get to "/api/v1/budgets/budget-2/transactions/transaction-2",
                        HttpMethod.Post to "/api/v1/budgets/budget-2/transactions/transaction-2/operations",
                    ),
                    capturedRequests.map { it.method to it.url.encodedPath },
                )

                capturedRequests.forEachIndexed { index, request ->
                    if (index in 4..11) {
                        assertEquals("Bearer device-token", request.headers[HttpHeaders.Authorization])
                    } else {
                        assertNull(request.headers[HttpHeaders.Authorization])
                    }
                }
                assertEquals("budget-secret", capturedRequests[7].headers["x-budget-encryption-password"])
                assertEquals("budget-secret", capturedRequests[8].headers["x-budget-encryption-password"])
                assertEquals("operation-key-1", capturedRequests[8].headers["idempotency-key"])
                assertNull(capturedRequests[10].headers["x-budget-encryption-password"])
                assertNull(capturedRequests[11].headers["x-budget-encryption-password"])
                assertEquals("operation-key-defaults", capturedRequests[11].headers["idempotency-key"])
                assertNotNull(capturedRequests[3].body)
                assertNotNull(capturedRequests[8].body)
                Unit
            } finally {
                Api.clearAuthProvider(auth)
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }
}
