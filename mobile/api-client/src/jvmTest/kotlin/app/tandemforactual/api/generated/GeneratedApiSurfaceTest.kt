package app.tandemforactual.api.generated

import com.kroegerama.openapi.kmp.gen.companion.AuthItem
import com.kroegerama.openapi.kmp.gen.companion.AuthPlugin
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import app.tandemforactual.api.generated.api.AuthenticationApi
import app.tandemforactual.api.generated.api.BudgetsApi
import app.tandemforactual.api.generated.api.DefaultApi
import app.tandemforactual.api.generated.api.HealthApi
import app.tandemforactual.api.generated.api.TransactionsApi
import app.tandemforactual.api.generated.models.ApiV1HealthReady200Response
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
                    val responseBody = """{"status":"ok"}"""
                    respond(
                        content = ByteReadChannel(gzip(responseBody)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType to
                                listOf(ContentType.Application.Json.toString()),
                            HttpHeaders.ContentEncoding to listOf("gzip"),
                        ),
                    )
                }
            val auth = Auth.BearerAuth { AuthItem.Bearer("device-token") }

            try {
                Api.baseUrl = Url("https://tandem.example")
                Api.setAuthProvider(auth)
                Api.updateClient(
                    createHttpClient = {
                        HttpClient(engine) {
                            install(ContentNegotiation) { json(Api.json) }
                            install(ContentEncoding) {
                                gzip()
                                deflate()
                            }
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
                assertEquals(readiness, ApiV1HealthReady200Response(readiness.status))
                assertEquals(readiness.hashCode(), ApiV1HealthReady200Response(readiness.status).hashCode())
                assertTrue(readiness.toString().contains("status=OK"))
                assertTrue(!readiness.equals("not-a-readiness-response"))
                assertEquals("""{"status":"ok"}""", Api.json.encodeToString(readiness))
                assertTrue(DefaultApi.getApiV1OpenapiJson().getOrNull()?.isSuccessful == true)
                assertTrue(AuthenticationApi.getActualLoginMethods().getOrNull()?.isSuccessful == true)
                assertTrue(AuthenticationApi.completeActualOpenIdReturn("state-1", "token-1").getOrNull()?.isSuccessful == true)
                assertTrue(
                    AuthenticationApi
                        .completeActualOpenIdSignIn(
                            buildJsonObject {
                                put("code", "C".repeat(43))
                                put("codeVerifier", "V".repeat(43))
                            },
                        )
                        .getOrNull()
                        ?.isSuccessful == true,
                )
                assertTrue(
                    AuthenticationApi
                        .signInWithActualPassword(
                            buildJsonObject { put("password", "actual-password") },
                        ).getOrNull()
                        ?.isSuccessful == true,
                )
                assertTrue(AuthenticationApi.getCurrentDevice().getOrNull()?.isSuccessful == true)
                assertTrue(AuthenticationApi.listDevices().getOrNull()?.isSuccessful == true)

                val deviceId = Uuid.parse("6d860805-fdd6-4fe4-8e67-668c9900fe1f")
                assertTrue(AuthenticationApi.revokeDevice(deviceId).getOrNull()?.isSuccessful == true)

                assertTrue(
                    BudgetsApi
                        .getBudgetSnapshot(
                            xBudgetEncryptionPassword = "budget-secret",
                            budgetId = "budget-1",
                        ).getOrNull()
                        ?.isSuccessful == true,
                )
                assertTrue(
                    BudgetsApi
                        .getBudgetSnapshot(budgetId = "budget-2")
                        .getOrNull()
                        ?.isSuccessful == true,
                )
                assertTrue(
                    BudgetsApi
                        .getBudgetChanges(
                            xBudgetEncryptionPassword = "budget-secret",
                            budgetId = "budget-1",
                            generation = "d9d69fa5-45fb-48ee-9a8c-99bf49f873bd",
                            cursor = "12",
                            throughCursor = "20",
                            limit = 50,
                        ).getOrNull()
                        ?.isSuccessful == true,
                )
                assertTrue(
                    BudgetsApi
                        .getBudgetChanges(
                            budgetId = "budget-2",
                            generation = "d9d69fa5-45fb-48ee-9a8c-99bf49f873bd",
                            cursor = "0",
                        ).getOrNull()
                        ?.isSuccessful == true,
                )

                assertTrue(
                    TransactionsApi
                        .getBudgetTransaction(
                            xBudgetEncryptionPassword = "budget-secret",
                            budgetId = "budget-1",
                            transactionId = "transaction-1",
                        ).getOrNull()
                        ?.isSuccessful == true,
                )
                assertEquals("gzip,deflate", capturedRequests.first().headers[HttpHeaders.AcceptEncoding])
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
                    TransactionsApi
                        .getTransactionOperation(
                            xBudgetEncryptionPassword = "budget-secret",
                            operationId = operationId,
                        ).getOrNull()
                        ?.isSuccessful == true,
                )
                assertTrue(TransactionsApi.getTransactionOperation(operationId = operationId).getOrNull()?.isSuccessful == true)
                assertTrue(TransactionsApi.discardUncertainTransactionOperation(operationId).getOrNull()?.isSuccessful == true)
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

                assertTrue(
                    TransactionsApi
                        .importBudgetAccountTransactions(
                            idempotencyKey = "statement-defaults",
                            budgetId = "budget-2",
                            accountId = "account-2",
                        ).getOrNull()
                        ?.isSuccessful == true,
                )
                assertTrue(
                    BudgetsApi
                        .moveBudgetAccount(idempotencyKey = "account-move-defaults", budgetId = "budget-2", accountId = "account-2")
                        .getOrNull()
                        ?.isSuccessful == true,
                )
                assertTrue(
                    BudgetsApi
                        .createBudgetCategoryGroup(idempotencyKey = "group-create-defaults", budgetId = "budget-2")
                        .getOrNull()
                        ?.isSuccessful == true,
                )
                assertTrue(
                    BudgetsApi
                        .manageBudgetCategoryGroup(idempotencyKey = "group-manage-defaults", budgetId = "budget-2", groupId = "group-2")
                        .getOrNull()
                        ?.isSuccessful == true,
                )
                assertTrue(
                    BudgetsApi
                        .manageBudgetCategory(idempotencyKey = "category-manage-defaults", budgetId = "budget-2", categoryId = "category-2")
                        .getOrNull()
                        ?.isSuccessful == true,
                )
                assertTrue(
                    BudgetsApi
                        .setBudgetCategoryCarryover(idempotencyKey = "carryover-defaults", budgetId = "budget-2", categoryId = "category-2")
                        .getOrNull()
                        ?.isSuccessful == true,
                )
                assertTrue(
                    BudgetsApi
                        .manageBudgetMonth(idempotencyKey = "month-manage-defaults", budgetId = "budget-2", month = "2026-11")
                        .getOrNull()
                        ?.isSuccessful == true,
                )
                assertTrue(
                    BudgetsApi
                        .manageBudgetSchedule(idempotencyKey = "schedule-manage-defaults", budgetId = "budget-2", scheduleId = "schedule-2")
                        .getOrNull()
                        ?.isSuccessful == true,
                )
                assertTrue(
                    BudgetsApi
                        .manageBudgetPayee(idempotencyKey = "payee-manage-defaults", budgetId = "budget-2", payeeId = "payee-2")
                        .getOrNull()
                        ?.isSuccessful == true,
                )

                assertEquals(
                    listOf(
                        HttpMethod.Get to "/api/v1/health/live",
                        HttpMethod.Get to "/api/v1/health/ready",
                        HttpMethod.Get to "/api/v1/openapi.json",
                        HttpMethod.Get to "/api/v1/auth/methods",
                        HttpMethod.Get to "/api/v1/auth/actual-return/state-1/openid-cb",
                        HttpMethod.Post to "/api/v1/auth/openid/complete",
                        HttpMethod.Post to "/api/v1/auth/login",
                        HttpMethod.Get to "/api/v1/auth/me",
                        HttpMethod.Get to "/api/v1/devices",
                        HttpMethod.Delete to "/api/v1/devices/$deviceId",
                        HttpMethod.Get to "/api/v1/budgets/budget-1/snapshot",
                        HttpMethod.Get to "/api/v1/budgets/budget-2/snapshot",
                        HttpMethod.Get to "/api/v1/budgets/budget-1/changes",
                        HttpMethod.Get to "/api/v1/budgets/budget-2/changes",
                        HttpMethod.Get to "/api/v1/budgets/budget-1/transactions/transaction-1",
                        HttpMethod.Post to "/api/v1/budgets/budget-1/transactions/transaction-1/operations",
                        HttpMethod.Get to "/api/v1/operations/$operationId",
                        HttpMethod.Get to "/api/v1/operations/$operationId",
                        HttpMethod.Post to "/api/v1/operations/$operationId/discard",
                        HttpMethod.Get to "/api/v1/budgets/budget-2/transactions/transaction-2",
                        HttpMethod.Post to "/api/v1/budgets/budget-2/transactions/transaction-2/operations",
                        HttpMethod.Post to "/api/v1/budgets/budget-2/accounts/account-2/transactions/import",
                        HttpMethod.Post to "/api/v1/budgets/budget-2/accounts/account-2/move",
                        HttpMethod.Post to "/api/v1/budgets/budget-2/category-groups",
                        HttpMethod.Patch to "/api/v1/budgets/budget-2/category-groups/group-2",
                        HttpMethod.Post to "/api/v1/budgets/budget-2/categories/category-2/actions",
                        HttpMethod.Put to "/api/v1/budgets/budget-2/categories/category-2/carryover",
                        HttpMethod.Post to "/api/v1/budgets/budget-2/months/2026-11/actions",
                        HttpMethod.Post to "/api/v1/budgets/budget-2/schedules/schedule-2/actions",
                        HttpMethod.Post to "/api/v1/budgets/budget-2/payees/payee-2/actions",
                    ),
                    capturedRequests.map { it.method to it.url.encodedPath },
                )

                capturedRequests.forEachIndexed { index, request ->
                    if (index >= 7) {
                        assertEquals("Bearer device-token", request.headers[HttpHeaders.Authorization])
                    } else {
                        assertNull(request.headers[HttpHeaders.Authorization])
                    }
                }
                assertEquals("budget-secret", capturedRequests[10].headers["x-budget-encryption-password"])
                assertNull(capturedRequests[11].headers["x-budget-encryption-password"])
                assertEquals("budget-secret", capturedRequests[12].headers["x-budget-encryption-password"])
                assertNull(capturedRequests[13].headers["x-budget-encryption-password"])
                assertEquals("d9d69fa5-45fb-48ee-9a8c-99bf49f873bd", capturedRequests[12].url.parameters["generation"])
                assertEquals("12", capturedRequests[12].url.parameters["cursor"])
                assertEquals("20", capturedRequests[12].url.parameters["throughCursor"])
                assertEquals("50", capturedRequests[12].url.parameters["limit"])
                assertEquals("0", capturedRequests[13].url.parameters["cursor"])
                assertNull(capturedRequests[13].url.parameters["throughCursor"])
                assertNull(capturedRequests[13].url.parameters["limit"])
                assertEquals("budget-secret", capturedRequests[14].headers["x-budget-encryption-password"])
                assertEquals("budget-secret", capturedRequests[15].headers["x-budget-encryption-password"])
                assertEquals("operation-key-1", capturedRequests[15].headers["idempotency-key"])
                assertEquals("budget-secret", capturedRequests[16].headers["x-budget-encryption-password"])
                assertNull(capturedRequests[17].headers["x-budget-encryption-password"])
                assertNull(capturedRequests[18].headers["x-budget-encryption-password"])
                assertNull(capturedRequests[19].headers["x-budget-encryption-password"])
                assertEquals("operation-key-defaults", capturedRequests[20].headers["idempotency-key"])
                assertNotNull(capturedRequests[6].body)
                assertNotNull(capturedRequests[15].body)
                Unit
            } finally {
                Api.clearAuthProvider(auth)
                Api.baseUrl = previousBaseUrl
                Api.client.close()
                previousClient.close()
            }
        }
}

private fun gzip(value: String): ByteArray {
    val output = ByteArrayOutputStream()
    GZIPOutputStream(output).use { it.write(value.encodeToByteArray()) }
    return output.toByteArray()
}
