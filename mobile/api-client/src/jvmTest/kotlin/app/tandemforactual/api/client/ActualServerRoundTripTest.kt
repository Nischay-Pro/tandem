package app.tandemforactual.api.client

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.actual.ActualBudgetReader
import app.tandemforactual.data.actual.ActualDatabase
import app.tandemforactual.data.actual.ActualMessageStore
import app.tandemforactual.data.actual.ActualSyncEngine
import app.tandemforactual.data.actual.ActualTransactionOperations
import app.tandemforactual.data.actual.ActualWriter
import app.tandemforactual.data.actual.OperationStatus
import app.tandemforactual.data.actual.ZipArchive
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
import kotlin.test.Test
import kotlin.test.assertEquals

/** Runs only with ACTUAL_TEST_URL set: writes through a real Actual server, then dumps what Tandem reads back. */
class ActualServerRoundTripTest {
    private val url = System.getenv("ACTUAL_TEST_URL")
    private val budget = System.getenv("ACTUAL_TEST_BUDGET") ?: "Parity envelope"
    private val out = System.getenv("ACTUAL_TEST_OUT")

    private class Budget(
        val db: ActualDatabase,
        val store: ActualMessageStore,
        val engine: ActualSyncEngine,
        val clock: app.tandemforactual.data.actual.StoredClock,
    )

    private fun open(client: ActualServerClient): Budget =
        runBlocking {
            val file = client.listFiles().first { it.name == budget && !it.deleted }
            val info = client.fileInfo(file.fileId)
            val entries = ZipArchive.read(client.download(file.fileId))
            val path = File.createTempFile("roundtrip", ".sqlite").apply { writeBytes(entries.getValue("db.sqlite")); deleteOnExit() }
            val db = ActualDatabase(BundledSQLiteDriver().open(path.path))
            val store = ActualMessageStore(db)
            val clock = store.loadClock()
            store.resetNode(clock)
            val engine = ActualSyncEngine(store, client::sync, info.groupId!!, file.fileId) { System.currentTimeMillis() }
            engine.fullSync(clock, null)
            Budget(db, store, engine, clock)
        }

    @Test
    fun writesRoundTripThroughActual() {
        if (url == null) return
        val client = ActualServerClient(HttpClient(Java), url)
        runBlocking { client.signIn(System.getenv("ACTUAL_TEST_PASSWORD") ?: "demo-password") }
        val budget = open(client)
        val writer = ActualWriter(budget.store, budget.clock, System::currentTimeMillis) { UUID.randomUUID().toString() }
        val operations = ActualTransactionOperations(writer)
        val db = budget.db
        val checking = db.first("SELECT id FROM accounts WHERE name = 'Checking'")!!["id"] as String
        val savingsPayee = db.first("SELECT p.id FROM payees p JOIN accounts a ON a.id = p.transfer_acct WHERE a.name = 'Savings'")!!["id"] as String
        val market = db.first("SELECT id FROM payees WHERE name = 'Market' AND tombstone = 0")!!["id"] as String
        val groceries = db.first("SELECT id FROM categories WHERE name = 'Groceries'")!!["id"] as String
        val today = java.time.LocalDate.now().toString()
        fun create(
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
            assertEquals(OperationStatus.COMMITTED, operations.submit(UUID.randomUUID().toString(), id, "create", patch, null).status)
            return id
        }
        val shop = create(market, -1234, groceries)
        val transfer = create(savingsPayee, -5000, null)
        val toDelete = create(market, -1, groceries)
        val reader = ActualBudgetReader(db)
        fun base(id: String) = reader.transaction(id)!!
        assertEquals(
            OperationStatus.COMMITTED,
            operations.submit(UUID.randomUUID().toString(), shop, "update", buildJsonObject { put("amount", -4321); put("cleared", true) }, base(shop)).status,
        )
        assertEquals(
            OperationStatus.COMMITTED,
            operations.submit(UUID.randomUUID().toString(), transfer, "update", buildJsonObject { put("amount", -7000) }, base(transfer)).status,
        )
        assertEquals(OperationStatus.COMMITTED, operations.submit(UUID.randomUUID().toString(), toDelete, "delete", JsonObject(emptyMap()), base(toDelete)).status)
        val stale = base(shop)
        operations.submit(UUID.randomUUID().toString(), shop, "update", buildJsonObject { put("notes", "first") }, stale)
        val conflict = operations.submit(UUID.randomUUID().toString(), shop, "update", buildJsonObject { put("notes", "second") }, stale)
        assertEquals(OperationStatus.CONFLICT, conflict.status)
        assertEquals(listOf("notes"), conflict.conflictingFields)
        runBlocking { budget.engine.fullSync(budget.clock, null) }

        val fresh = open(client)
        val today2 = java.time.LocalDate.now().toString()
        val snapshot = ActualBudgetReader(fresh.db).read(today2)
        val counterpart = ActualBudgetReader(fresh.db).transaction(transfer)!!
        val savingsSide = ActualBudgetReader(fresh.db).transaction((counterpart["transferId"] as JsonPrimitive).content)!!
        assertEquals(JsonPrimitive(7000), savingsSide["amount"])
        if (out != null) {
            val records =
                JsonArray(
                    snapshot.records.map {
                        JsonObject(mapOf("type" to JsonPrimitive(it.type), "id" to JsonPrimitive(it.id), "body" to it.body, "revision" to JsonPrimitive(it.revision)))
                    },
                )
            File(out).writeText(JsonObject(mapOf("today" to JsonPrimitive(today2), "records" to records)).toString())
        }
    }
}
