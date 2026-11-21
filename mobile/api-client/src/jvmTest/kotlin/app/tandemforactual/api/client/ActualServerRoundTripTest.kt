package app.tandemforactual.api.client

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.actual.ActualDatabase
import app.tandemforactual.data.actual.OperationStatus
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.RemoteOperationStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Runs only with ACTUAL_TEST_URL set: writes through a real Actual server, then dumps what Tandem reads back. */
class ActualServerRoundTripTest {
    private val url = System.getenv("ACTUAL_TEST_URL")
    private val budget = System.getenv("ACTUAL_TEST_BUDGET") ?: "Parity envelope"
    private val out = System.getenv("ACTUAL_TEST_OUT")

    private suspend fun download(
        client: ActualServerClient,
        fileId: String,
        path: File,
    ): ActualDirectBudget =
        ActualDirectBudget.download(client, fileId, System::currentTimeMillis, { UUID.randomUUID().toString() }) {
            path.writeBytes(it)
            openDatabase(path)
        }

    private fun openDatabase(path: File) = ActualDatabase(BundledSQLiteDriver().open(path.path))

    @Test
    fun writesRoundTripThroughActual(): Unit = runBlocking {
        if (url == null) return@runBlocking
        val directory = createTempDirectory("actual-roundtrip").toFile()
        try {
            HttpClient(Java).use { client ->
                verifyRoundTrip(ActualServerClient(client, url), directory)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    private suspend fun verifyRoundTrip(
        client: ActualServerClient,
        directory: File,
    ) {
        client.signIn(System.getenv("ACTUAL_TEST_PASSWORD") ?: "demo-password")
        val fileId = client.listFiles().first { it.name == budget && !it.deleted }.fileId
        val path = directory.resolve("initial.sqlite")
        val initial = download(client, fileId, path)
        val conflictId = UUID.randomUUID().toString()
        val (shop, transfer, toDelete) = try {
            initial.sync()

            val (checking, savingsPayee, market, groceries) = openDatabase(path).let { db ->
                try {
                    listOf(
                        db.first("SELECT id FROM accounts WHERE name = 'Checking'")!!["id"] as String,
                        db.first("SELECT p.id FROM payees p JOIN accounts a ON a.id = p.transfer_acct WHERE a.name = 'Savings'")!!["id"] as String,
                        db.first("SELECT id FROM payees WHERE name = 'Market' AND tombstone = 0")!!["id"] as String,
                        db.first("SELECT id FROM categories WHERE name = 'Groceries'")!!["id"] as String,
                    )
                } finally {
                    db.close()
                }
            }
            val today = java.time.LocalDate.now().toString()
            suspend fun create(
                payee: String,
                amount: Long,
                category: String?,
            ): String {
                val id = "tandem:${UUID.randomUUID()}"
                val patch =
                    buildJsonObject {
                        put("account", checking)
                        put("amount", amount)
                        put("date", today)
                        put("payee", payee)
                        put("category", category)
                        put("notes", "round trip")
                        put("cleared", false)
                    }
                assertEquals(OperationStatus.COMMITTED, initial.submit(UUID.randomUUID().toString(), id, "create", patch, null).status)
                return id
            }
            val shop = create(market, -1234, groceries)
            val transfer = create(savingsPayee, -5000, null)
            val toDelete = create(market, -1, groceries)
            suspend fun base(id: String) = initial.transaction(id)!!
            assertEquals(
                OperationStatus.COMMITTED,
                initial.submit(UUID.randomUUID().toString(), shop, "update", buildJsonObject { put("amount", -4321); put("cleared", true) }, base(shop)).status,
            )
            val transferUpdate = outboxUpdate(initial.groupId, transfer, base(transfer))
            assertEquals(RemoteOperationStatus.COMMITTED, initial.submit(transferUpdate).status)
            assertEquals(OperationStatus.COMMITTED, initial.submit(UUID.randomUUID().toString(), toDelete, "delete", JsonObject(emptyMap()), base(toDelete)).status)
            val stale = base(shop)
            initial.submit(UUID.randomUUID().toString(), shop, "update", buildJsonObject { put("notes", "first") }, stale)
            val conflict = initial.submit(conflictId, shop, "update", buildJsonObject { put("notes", "second") }, stale)
            assertEquals(OperationStatus.CONFLICT, conflict.status)
            assertEquals(listOf("notes"), conflict.conflictingFields)
            listOf(shop, transfer, toDelete)
        } finally {
            initial.close()
        }
        assertFailsWith<IllegalArgumentException> {
            ActualDirectBudget.reopen(openDatabase(path), client, fileId, "another-group", System::currentTimeMillis) { UUID.randomUUID().toString() }
        }
        val reopened = ActualDirectBudget.reopen(openDatabase(path), client, fileId, initial.groupId, System::currentTimeMillis) {
            UUID.randomUUID().toString()
        }
        try {
            val retried = reopened.submit(conflictId, shop, "update", buildJsonObject { put("notes", "retried") }, reopened.transaction(shop))
            assertEquals(OperationStatus.CONFLICT, retried.status)
            assertEquals(listOf("notes"), retried.conflictingFields)
            val projection = reopened.refreshProjection(java.time.LocalDate.now().toString())
            assertEquals(reopened.groupId, projection.budgetId)
            assertEquals(-7000L, projection.records.single { it.recordType == "transaction" && it.recordId == transfer }.amountMinor)
        } finally {
            reopened.close()
        }

        val fresh = download(client, fileId, directory.resolve("fresh.sqlite"))
        try {
            fresh.preview(java.time.LocalDate.now().toString())
            verifyDownload(fresh, shop, transfer, toDelete)
        } finally {
            fresh.close()
        }
    }

    private fun outboxUpdate(
        budgetId: String,
        transactionId: String,
        base: JsonObject,
    ): OutboxOperationEntity {
        val operationId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        return OutboxOperationEntity(
            operationId = operationId,
            idempotencyKey = operationId,
            budgetId = budgetId,
            recordType = "transaction",
            recordId = transactionId,
            action = "UPDATE",
            baseRevision = null,
            baseSnapshotJson = base.toString(),
            patchJson = """{"amount":-7000}""",
            optimisticJson = null,
            optimisticRecordDate = null,
            optimisticAccountId = null,
            optimisticCategoryId = null,
            optimisticPayeeId = null,
            optimisticAmountMinor = -7000,
            optimisticBudgetMonth = null,
            status = "sending",
            attemptCount = 1,
            serverOperationId = null,
            lastErrorCode = null,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
    }

    private suspend fun verifyDownload(
        fresh: ActualDirectBudget,
        shop: String,
        transfer: String,
        toDelete: String,
    ) {
        val today2 = java.time.LocalDate.now().toString()
        val savedShop = fresh.transaction(shop)!!
        assertEquals(JsonPrimitive(-4321), savedShop["amount"])
        assertEquals(JsonPrimitive(true), savedShop["cleared"])
        assertEquals(JsonPrimitive("first"), savedShop["notes"])
        assertNull(fresh.transaction(toDelete))
        val checkingSide = fresh.transaction(transfer)!!
        assertEquals(JsonPrimitive(-7000), checkingSide["amount"])
        val savingsSide = fresh.transaction((checkingSide["transferId"] as JsonPrimitive).content)!!
        assertEquals(JsonPrimitive(7000), savingsSide["amount"])
        if (out != null) {
            val snapshot = fresh.snapshot(today2)
            val records =
                JsonArray(
                    snapshot.records.map {
                        JsonObject(mapOf("type" to JsonPrimitive(it.type), "id" to JsonPrimitive(it.id), "body" to it.body, "revision" to JsonPrimitive(it.revision)))
                    },
                )
            val written = buildJsonObject {
                put("shop", shop)
                put("transfer", transfer)
                put("deleted", toDelete)
            }
            File(out).writeText(JsonObject(mapOf("today" to JsonPrimitive(today2), "records" to records, "written" to written)).toString())
        }
    }
}
