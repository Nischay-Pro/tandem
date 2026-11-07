package app.tandemforactual.api.client

import app.tandemforactual.api.generated.Api
import app.tandemforactual.api.generated.Auth
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.BudgetChange
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.data.store.BudgetRecordDisplayMetadata
import app.tandemforactual.data.store.BudgetSyncProtocolException
import app.tandemforactual.data.store.BudgetSyncRemoteException
import app.tandemforactual.data.store.RemoteOperationStatus
import com.kroegerama.openapi.kmp.gen.companion.AuthItem
import com.kroegerama.openapi.kmp.gen.companion.AuthPlugin
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeneratedBudgetSyncRemoteTest {
    @Test
    fun generatedClientReadsSnapshotPagesAndSerializesTheContinuationKey() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(snapshotPageJson(), headers = jsonHeaders())
                }
            val cleanup = configureApi(engine)
            try {
                val remote = GeneratedBudgetSyncRemote(testApiClient())
                val first = remote.readSnapshotPage(BUDGET, null, null, null, null, 2_000, "encrypted-budget")
                assertEquals(BUDGET, first.budgetId)
                assertEquals(GENERATION, first.generation)
                assertEquals("1000", first.cursor)
                assertEquals(1, first.records.size)
                assertTrue(first.hasMore)
                assertEquals("transaction", first.nextAfterType)
                assertEquals(TXN, first.nextAfterId)
                assertEquals(BudgetRecordDisplayMetadata("Corner Market", "weekly groceries"), first.records.single().displayMetadata)

                remote.readSnapshotPage(BUDGET, GENERATION, "1000", "transaction", TXN, 2_000, null)
                assertEquals("transaction", requests[1].url.parameters["afterType"])
                assertEquals(TXN, requests[1].url.parameters["afterId"])
                assertEquals(GENERATION, requests[1].url.parameters["generation"])
                assertEquals("1000", requests[1].url.parameters["cursor"])
                assertNull(requests[1].headers["x-budget-encryption-password"])
            } finally {
                cleanup()
            }
        }

    @Test
    fun generatedClientMapsEverySnapshotContinuationRecordType() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(snapshotPageJson(), headers = jsonHeaders())
                }
            val cleanup = configureApi(engine)
            try {
                val remote = GeneratedBudgetSyncRemote(testApiClient())
                val recordTypes =
                    listOf(
                        "account",
                        "account-group",
                        "category",
                        "category-group",
                        "payee",
                        "transaction",
                        "budget-month",
                    )
                recordTypes.forEach { recordType ->
                    remote.readSnapshotPage(BUDGET, null, null, recordType, "record-1", 100, null)
                }
                assertEquals(recordTypes, requests.map { it.url.parameters["afterType"] })
                assertProtocolException {
                    runBlocking { remote.readSnapshotPage(BUDGET, null, null, "unknown", "record-1", 100, null) }
                }
            } finally {
                cleanup()
            }
        }

    @Test
    fun generatedClientMapsSnapshotsFeedWritesAndTransactionReads() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            var transactionStatus = HttpStatusCode.OK
            val engine =
                MockEngine { request ->
                    requests += request
                    when {
                        request.url.encodedPath.endsWith("/snapshot") -> respond(snapshotJson(), headers = jsonHeaders())
                        request.url.encodedPath.endsWith("/changes") -> respond(changePageJson(), headers = jsonHeaders())
                        request.url.encodedPath.endsWith("/operations") -> respond(operationJson("queued"), headers = jsonHeaders())
                        request.url.encodedPath.startsWith("/api/v1/operations/") ->
                            respond(
                                operationJson("committed"),
                                headers = jsonHeaders(),
                            )
                        request.url.encodedPath.contains("/transactions/") ->
                            respond(transactionJson(), status = transactionStatus, headers = jsonHeaders())
                        else -> respond("{}", headers = jsonHeaders())
                    }
                }
            val cleanup = configureApi(engine)
            try {
                val apiClient = testApiClient()
                val remote = GeneratedBudgetSyncRemote(apiClient)
                val snapshot = remote.readSnapshot(BUDGET, "encrypted-budget")
                assertEquals(GENERATION, snapshot.generation)
                assertEquals("1000", snapshot.cursor)
                assertEquals("checking", snapshot.records.single().accountId)
                assertEquals(1200L, snapshot.records.single().amountMinor)
                assertEquals(BudgetRecordDisplayMetadata("Corner Market", "weekly groceries"), snapshot.records.single().displayMetadata)

                val page = remote.readChanges(BUDGET, GENERATION, "1000", null, "encrypted-budget")
                assertEquals(2, page.changes.size)
                assertTrue(page.changes.first() is BudgetChange.Upsert)
                assertTrue(page.changes.last() is BudgetChange.Delete)
                assertEquals("2", page.nextCursor)

                val update = remote.submitOperation(outboxOperation("UPDATE"), "encrypted-budget")
                val delete = remote.submitOperation(outboxOperation("DELETE").copy(patchJson = "{}"), null)
                val stableTransactionId = "tandem:8c88495a-65d9-4531-8392-93bff2c65374"
                val create =
                    remote.submitOperation(
                        outboxOperation("CREATE").copy(
                            recordId = stableTransactionId,
                            baseRevision = null,
                            baseSnapshotJson = null,
                            patchJson =
                                "{\"account\":\"checking\",\"amount\":-1200,\"date\":\"2026-10-01\",\"payee\":null,\"category\":null,\"notes\":\"\",\"cleared\":false}",
                        ),
                        null,
                    )
                assertEquals(RemoteOperationStatus.QUEUED, update.status)
                assertEquals(RemoteOperationStatus.QUEUED, delete.status)
                assertEquals(RemoteOperationStatus.QUEUED, create.status)

                val status = remote.readOperation(OPERATION_ID)
                assertEquals(RemoteOperationStatus.COMMITTED, status.status)
                val transaction = remote.readTransaction(BUDGET, TXN, "encrypted-budget")
                assertEquals(TXN, transaction?.transactionId)
                assertEquals("checking", transaction?.projection?.accountId)
                assertEquals(BudgetRecordDisplayMetadata("Corner Market", "weekly groceries"), transaction?.displayMetadata)
                assertNull(requests[0].url.parameters["cursor"])
                assertEquals("encrypted-budget", requests[0].headers["x-budget-encryption-password"])
                assertEquals("1000", requests[1].url.parameters["cursor"])
                assertNull(requests[1].url.parameters["throughCursor"])
                assertEquals("encrypted-budget", requests[1].headers["x-budget-encryption-password"])
                assertEquals("stable-key", requests[2].headers["idempotency-key"])
                assertEquals("encrypted-budget", requests[2].headers["x-budget-encryption-password"])
                assertEquals("stable-key", requests[3].headers["idempotency-key"])
                assertNull(requests[3].headers["x-budget-encryption-password"])
                assertEquals("stable-key", requests[4].headers["idempotency-key"])
                assertNull(requests[4].headers["x-budget-encryption-password"])
                val createBody =
                    Json
                        .parseToJsonElement(
                            (requests[4].body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                        ).jsonObject
                assertEquals("create", createBody["action"]?.toString()?.trim('"'))
                assertNull(createBody["baseRevision"])
                assertNull(createBody["baseSnapshot"])
                assertEquals("Bearer sync-test-token", requests[0].headers[HttpHeaders.Authorization])
                assertTrue(requests[2].body.toString().isNotBlank())

                transactionStatus = HttpStatusCode.NotFound
                assertNull(remote.readTransaction(BUDGET, TXN, null))

                transactionStatus = HttpStatusCode.Forbidden
                val forbidden = captureSyncException { remote.readTransaction(BUDGET, TXN, null) }
                assertEquals(403, forbidden.httpStatus)
            } finally {
                cleanup()
            }
        }

    @Test
    fun generatedClientMapsHttpAndTransportFailuresWithoutLeakingMessages() =
        runBlocking {
            var mode = "http"
            val engine =
                MockEngine {
                    when (mode) {
                        "http" -> respond("{}", status = HttpStatusCode.BadGateway, headers = jsonHeaders())
                        "io" -> throw java.io.IOException("private host detail")
                        else -> respond(snapshotJson(), headers = jsonHeaders())
                    }
                }
            val cleanup = configureApi(engine)
            try {
                val apiClient = testApiClient()
                val remote = GeneratedBudgetSyncRemote(apiClient)
                val httpError = captureSyncException { remote.readSnapshot(BUDGET, null) }
                assertEquals("http-502", httpError.errorCode)
                assertEquals(502, httpError.httpStatus)
                assertTrue(!httpError.message.orEmpty().contains("private host detail"))

                mode = "io"
                val transportError = captureSyncException { remote.readSnapshot(BUDGET, null) }
                assertEquals("network-error", transportError.errorCode)
                assertNull(transportError.httpStatus)
                val transactionTransportError = captureSyncException { remote.readTransaction(BUDGET, TXN, null) }
                assertNull(transactionTransportError.httpStatus)

                mode = "ok"
                val invalidOperationId = captureProtocolException { remote.readOperation("not-a-uuid") }
                assertTrue(invalidOperationId.message.orEmpty().contains("UUID"))
                val invalidBase =
                    captureProtocolException {
                        remote.submitOperation(outboxOperation("UPDATE").copy(baseSnapshotJson = null), null)
                    }
                assertTrue(invalidBase.message.orEmpty().contains("base snapshot"))
                val missingRevision =
                    captureProtocolException {
                        remote.submitOperation(outboxOperation("UPDATE").copy(baseRevision = null), null)
                    }
                assertTrue(missingRevision.message.orEmpty().contains("base revision"))
                val invalidPatch =
                    captureProtocolException {
                        remote.submitOperation(outboxOperation("UPDATE").copy(patchJson = "[]"), null)
                    }
                assertTrue(invalidPatch.message.orEmpty().contains("JSON object"))
                val unsupportedAction =
                    captureProtocolException {
                        remote.submitOperation(outboxOperation("TRANSFER"), null)
                    }
                assertTrue(unsupportedAction.message.orEmpty().contains("not supported"))
            } finally {
                cleanup()
            }
        }

    @Test
    fun protocolParserRejectsMalformedAndMismatchedResponses() {
        val duplicateRecords =
            mapOf(
                "budgetId" to Json.parseToJsonElement("\"$BUDGET\""),
                "generation" to Json.parseToJsonElement("\"$GENERATION\""),
                "cursor" to Json.parseToJsonElement("\"0\""),
                "records" to Json.parseToJsonElement("[${recordJson()},${recordJson()}]"),
            )
        assertProtocolException { BudgetSyncJson.parseSnapshot(duplicateRecords, BUDGET) }

        val wrongBudget = Json.parseToJsonElement(snapshotJson()).jsonObject
        assertProtocolException { BudgetSyncJson.parseSnapshot(wrongBudget, "other-budget") }

        val invalidCursor =
            mapOf(
                "budgetId" to Json.parseToJsonElement("\"$BUDGET\""),
                "generation" to Json.parseToJsonElement("\"$GENERATION\""),
                "cursor" to Json.parseToJsonElement("\"01\""),
                "records" to Json.parseToJsonElement("[]"),
            )
        assertProtocolException { BudgetSyncJson.parseSnapshot(invalidCursor, BUDGET) }

        val duplicatePage =
            Json
                .parseToJsonElement(snapshotPageJson().replace("[${recordJson()}]", "[${recordJson()},${recordJson()}]"))
                .jsonObject
        assertProtocolException { BudgetSyncJson.parseSnapshotPage(duplicatePage, BUDGET) }

        val invalidPageContinuation =
            Json
                .parseToJsonElement(snapshotPageJson().replace("\"nextAfterId\":\"$TXN\"", "\"nextAfterId\":null"))
                .jsonObject
        assertProtocolException { BudgetSyncJson.parseSnapshotPage(invalidPageContinuation, BUDGET) }

        val resetPageWithRecords =
            Json
                .parseToJsonElement(snapshotPageJson().replace("\"resetRequired\":false", "\"resetRequired\":true"))
                .jsonObject
        assertProtocolException { BudgetSyncJson.parseSnapshotPage(resetPageWithRecords, BUDGET) }

        val invalidPageRecordType =
            Json
                .parseToJsonElement(snapshotPageJson().replace("\"nextAfterType\":\"transaction\"", "\"nextAfterType\":{}"))
                .jsonObject
        assertProtocolException { BudgetSyncJson.parseSnapshotPage(invalidPageRecordType, BUDGET) }

        val invalidDelete =
            Json
                .parseToJsonElement(
                    changePageJson().replace("\"body\":null", "\"body\":{}"),
                ).jsonObject
        assertProtocolException { BudgetSyncJson.parseChanges(invalidDelete, BUDGET) }
        val unknownChange =
            Json
                .parseToJsonElement(
                    changePageJson().replace("\"operation\":\"delete\"", "\"operation\":\"move\""),
                ).jsonObject
        assertProtocolException { BudgetSyncJson.parseChanges(unknownChange, BUDGET) }

        val stringAmount = Json.parseToJsonElement(snapshotJson().replace("\"amount\":1200", "\"amount\":\"1200\""))
        assertProtocolException { BudgetSyncJson.parseSnapshot(stringAmount.jsonObject, BUDGET) }
        val objectAmount = Json.parseToJsonElement(snapshotJson().replace("\"amount\":1200", "\"amount\":{}"))
        assertProtocolException { BudgetSyncJson.parseSnapshot(objectAmount.jsonObject, BUDGET) }

        val mismatchedTransaction = Json.parseToJsonElement(transactionJson().replace(TXN, "other-transaction")).jsonObject
        assertProtocolException { BudgetSyncJson.parseTransaction(mismatchedTransaction, TXN) }
        val unknownStatus = Json.parseToJsonElement(operationJson("retrying")).jsonObject
        assertProtocolException { BudgetSyncJson.parseOperation(unknownStatus) }
        assertProtocolException { BudgetSyncJson.parseJsonObject("[]") }
        assertProtocolException { BudgetSyncJson.parseJsonObject("{") }

        assertEquals(
            RemoteOperationStatus.APPLYING,
            BudgetSyncJson.parseOperation(Json.parseToJsonElement(operationJson("applying")).jsonObject).status,
        )
        assertEquals(
            RemoteOperationStatus.CONFLICT,
            BudgetSyncJson.parseOperation(Json.parseToJsonElement(operationJson("conflict")).jsonObject).status,
        )
        assertEquals(
            RemoteOperationStatus.UNCERTAIN,
            BudgetSyncJson.parseOperation(Json.parseToJsonElement(operationJson("uncertain")).jsonObject).status,
        )
        assertEquals(
            RemoteOperationStatus.REJECTED,
            BudgetSyncJson.parseOperation(Json.parseToJsonElement(operationJson("rejected")).jsonObject).status,
        )

        val blankStatus = Json.parseToJsonElement(operationJson("queued").replace("queued", " ")).jsonObject
        assertProtocolException { BudgetSyncJson.parseOperation(blankStatus) }
        val nonStringError = Json.parseToJsonElement(operationJson("queued").replace("null", "123")).jsonObject
        assertProtocolException { BudgetSyncJson.parseOperation(nonStringError) }
        val invalidBoolean =
            Json
                .parseToJsonElement(changePageJson().replace("\"hasMore\":false", "\"hasMore\":\"false\""))
                .jsonObject
        assertProtocolException { BudgetSyncJson.parseChanges(invalidBoolean, BUDGET) }
        val numericBoolean =
            Json
                .parseToJsonElement(changePageJson().replace("\"hasMore\":false", "\"hasMore\":1"))
                .jsonObject
        assertProtocolException { BudgetSyncJson.parseChanges(numericBoolean, BUDGET) }
        val objectBoolean =
            Json
                .parseToJsonElement(changePageJson().replace("\"hasMore\":false", "\"hasMore\":{}"))
                .jsonObject
        assertProtocolException { BudgetSyncJson.parseChanges(objectBoolean, BUDGET) }
        val missingRecords = Json.parseToJsonElement(snapshotJson().replace(Regex(",\"records\":\\[.*]"), "")).jsonObject
        assertProtocolException { BudgetSyncJson.parseSnapshot(missingRecords, BUDGET) }
    }

    @Test
    fun protocolParserReadsActualBudgetDisplaySettingsAndRejectsUnsupportedFormats() {
        val displaySettingsJson =
            """{"currencyCode":"CAD","currencySymbol":"CA$","currencyFractionDigits":2,"numberFormat":"space-comma","hideFraction":false,"currencySymbolPosition":"after","currencySpaceBetweenAmountAndSymbol":true}"""
        val expectedSettings =
            BudgetDisplaySettings(
                currencyCode = "CAD",
                currencySymbol = "CA$",
                currencyFractionDigits = 2,
                numberFormat = "space-comma",
                hideFraction = false,
                currencySymbolPosition = "after",
                currencySpaceBetweenAmountAndSymbol = true,
            )
        val snapshot =
            Json
                .parseToJsonElement(snapshotJson().replace("\"records\":", "\"displaySettings\":$displaySettingsJson,\"records\":"))
                .jsonObject
        val page =
            Json
                .parseToJsonElement(changePageJson().replace("\"changes\":", "\"displaySettings\":$displaySettingsJson,\"changes\":"))
                .jsonObject

        assertEquals(expectedSettings, BudgetSyncJson.parseSnapshot(snapshot, BUDGET).displaySettings)
        assertEquals(expectedSettings, BudgetSyncJson.parseChanges(page, BUDGET).displaySettings)
        assertNull(BudgetSyncJson.parseSnapshot(Json.parseToJsonElement(snapshotJson()).jsonObject, BUDGET).displaySettings)

        val invalidCurrency = Json.parseToJsonElement(displaySettingsJson.replace("CAD", "CA")).toString()
        assertProtocolException {
            BudgetSyncJson.parseSnapshot(
                Json
                    .parseToJsonElement(
                        snapshotJson().replace("\"records\":", "\"displaySettings\":$invalidCurrency,\"records\":"),
                    ).jsonObject,
                BUDGET,
            )
        }
        val unsupportedNumberFormat = displaySettingsJson.replace("space-comma", "unsupported")
        assertProtocolException {
            BudgetSyncJson.parseChanges(
                Json
                    .parseToJsonElement(
                        changePageJson().replace("\"changes\":", "\"displaySettings\":$unsupportedNumberFormat,\"changes\":"),
                    ).jsonObject,
                BUDGET,
            )
        }

        val unsupportedSymbolPosition = displaySettingsJson.replace("\"after\"", "\"center\"")
        assertProtocolException {
            BudgetSyncJson.parseSnapshot(
                Json
                    .parseToJsonElement(
                        snapshotJson().replace("\"records\":", "\"displaySettings\":$unsupportedSymbolPosition,\"records\":"),
                    ).jsonObject,
                BUDGET,
            )
        }

        val unsupportedFractionDigits = displaySettingsJson.replace("\"currencyFractionDigits\":2", "\"currencyFractionDigits\":7")
        assertProtocolException {
            BudgetSyncJson.parseSnapshot(
                Json
                    .parseToJsonElement(
                        snapshotJson().replace("\"records\":", "\"displaySettings\":$unsupportedFractionDigits,\"records\":"),
                    ).jsonObject,
                BUDGET,
            )
        }

        val nonIntegerFractionDigits = displaySettingsJson.replace("\"currencyFractionDigits\":2", "\"currencyFractionDigits\":{}")
        assertProtocolException {
            BudgetSyncJson.parseSnapshot(
                Json
                    .parseToJsonElement(
                        snapshotJson().replace("\"records\":", "\"displaySettings\":$nonIntegerFractionDigits,\"records\":"),
                    ).jsonObject,
                BUDGET,
            )
        }

        val missingFractionDigits = displaySettingsJson.replace("\"currencyFractionDigits\":2,", "")
        assertProtocolException {
            BudgetSyncJson.parseSnapshot(
                Json
                    .parseToJsonElement(
                        snapshotJson().replace("\"records\":", "\"displaySettings\":$missingFractionDigits,\"records\":"),
                    ).jsonObject,
                BUDGET,
            )
        }
    }

    private fun configureApi(engine: MockEngine): () -> Unit {
        val previousClient = Api.client
        val previousBaseUrl = Api.baseUrl
        val auth = Auth.BearerAuth { AuthItem.Bearer("sync-test-token") }
        Api.baseUrl = Url("https://companion.example")
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
                        authItem { key -> if (key == auth.key) auth.provideAuthItem() else null }
                    }
                }
            },
        )
        return {
            Api.clearAuthProvider(auth)
            Api.baseUrl = previousBaseUrl
            Api.client.close()
            previousClient.close()
        }
    }

    private suspend fun testApiClient(): TandemApiClient =
        TandemApiClient().apply {
            restoreSession("https://companion.example", "sync-test-token")
        }

    private fun outboxOperation(action: String) =
        OutboxOperationEntity(
            operationId = OPERATION_ID,
            idempotencyKey = "stable-key",
            budgetId = BUDGET,
            recordType = "transaction",
            recordId = TXN,
            action = action,
            baseRevision = "revision-1",
            baseSnapshotJson = "{\"id\":\"$TXN\",\"amount\":1000}",
            patchJson = "{\"amount\":1200}",
            optimisticJson = "{\"id\":\"$TXN\",\"amount\":1200}",
            optimisticRecordDate = "2026-10-01",
            optimisticAccountId = "checking",
            optimisticCategoryId = "food",
            optimisticPayeeId = "market",
            optimisticAmountMinor = 1200L,
            optimisticBudgetMonth = null,
            status = "sending",
            attemptCount = 1,
            serverOperationId = null,
            lastErrorCode = null,
            createdAtEpochMs = 100L,
            updatedAtEpochMs = 100L,
        )

    private suspend fun captureSyncException(block: suspend () -> Any?): BudgetSyncRemoteException =
        try {
            block()
            error("Expected a remote exception")
        } catch (exception: BudgetSyncRemoteException) {
            exception
        }

    private suspend fun captureProtocolException(block: suspend () -> Any?): BudgetSyncProtocolException =
        try {
            block()
            error("Expected a protocol exception")
        } catch (exception: BudgetSyncProtocolException) {
            exception
        }

    private fun assertProtocolException(block: () -> Any?) {
        try {
            block()
            error("Expected a protocol exception")
        } catch (_: BudgetSyncProtocolException) {
            // Expected malformed contract data.
        }
    }

    private fun snapshotJson(): String = """{"budgetId":"$BUDGET","generation":"$GENERATION","cursor":"1000","records":[${recordJson()}]}"""

    private fun snapshotPageJson() =
        """{"budgetId":"$BUDGET","generation":"$GENERATION","cursor":"1000","records":[${recordJson()}],"hasMore":true,"resetRequired":false,"nextAfterType":"transaction","nextAfterId":"$TXN","displaySettings":null}"""

    private fun recordJson() =
        """{"type":"transaction","id":"$TXN","revision":"revision-1","body":{"id":"$TXN","name":"Corner Market","notes":"weekly groceries","account":"checking","amount":1200,"date":"2026-10-01","category":"food","payee":"market"}}"""

    private fun changePageJson() =
        """{"budgetId":"$BUDGET","generation":"$GENERATION","currentCursor":"2","throughCursor":"2","nextCursor":"2","hasMore":false,"resetRequired":false,"changes":[{"cursor":"1","type":"transaction","id":"$TXN","operation":"upsert","revision":"revision-1","body":{"id":"$TXN","account":"checking","amount":1200,"date":"2026-10-01"}},{"cursor":"2","type":"transaction","id":"txn-deleted","operation":"delete","revision":"revision-2","body":null}]}"""

    private fun transactionJson() =
        """{"transaction":{"id":"$TXN","name":"Corner Market","notes":"weekly groceries","account":"checking","amount":1200,"date":"2026-10-01","category":"food","payee":"market"},"revision":"revision-1"}"""

    private fun operationJson(status: String) =
        """{"operation":{"operationId":"$OPERATION_ID","budgetId":"$BUDGET","transactionId":"$TXN","status":"$status","errorCode":null},"conflict":null}"""

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private companion object {
        const val BUDGET = "budget-1"
        const val TXN = "txn-1"
        const val GENERATION = "d9d69fa5-45fb-48ee-9a8c-99bf49f873bd"
        const val OPERATION_ID = "6461dd06-cfe0-4854-9ced-16b7011fb87d"
    }
}
