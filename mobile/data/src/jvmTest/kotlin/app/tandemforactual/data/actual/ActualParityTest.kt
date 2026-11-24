package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ActualParityTest {
    private fun resource(path: String): ByteArray = checkNotNull(javaClass.getResource("/actual/$path")).readBytes()

    private fun parity(name: String) {
        val entries = ZipArchive.read(resource("$name/budget.zip"))
        val file = File.createTempFile("actual-$name", ".sqlite").apply { writeBytes(entries.getValue("db.sqlite")) }
        val db = ActualDatabase(BundledSQLiteDriver().open(file.path))
        try {
            val meta = Json.parseToJsonElement(entries.getValue("metadata.json").decodeToString()).jsonObject
            val store = ActualMessageStore(db)
            val clock = store.loadClock()
            if (meta["resetClock"]?.jsonPrimitive?.content == "true") store.resetNode(clock)
            val server = SyncProtocol.decodeResponse(resource("$name/sync-response.bin"))
            val engine =
                ActualSyncEngine(store, { request ->
                    SyncResponse(server.messages.filter { it.timestamp > request.since }, server.merkle)
                }, "group", "file", now = System::currentTimeMillis)
            val result = runBlocking { engine.fullSync(clock, null) }
            assertEquals(server.messages.size.toLong(), db.first("SELECT count(*) AS n FROM messages_crdt")?.get("n"))
            assertEquals(server.messages.size, result.received.size)
            assertEquals(MerkleTrie.fromJson(server.merkle).hash, clock.merkle.hash)

            val expected = Json.parseToJsonElement(resource("$name/snapshot.json").decodeToString()).jsonObject
            val snapshot = ActualBudgetReader(db).read(expected.getValue("today").jsonPrimitive.content)
            val want =
                expected
                    .getValue("records")
                    .jsonArray
                    .map { it.jsonObject }
                    .associateBy { it.key() }
            val got = snapshot.records.associateBy { "${it.type}/${it.id}" }
            assertEquals(want.keys.sorted(), got.keys.sorted())
            want.forEach { (key, record) ->
                assertEquals(record.getValue("body"), got.getValue(key).body, key)
                assertEquals(record.getValue("revision").jsonPrimitive.content, got.getValue(key).revision, "$key revision")
            }
            val settings = expected.getValue("displaySettings").jsonObject
            assertEquals(settings.getValue("budgetType").jsonPrimitive.content, snapshot.displaySettings.budgetType)
            assertEquals(settings.getValue("currencyCode").jsonPrimitive.content, snapshot.displaySettings.currencyCode)
        } finally {
            db.close()
            file.delete()
        }
    }

    private fun JsonObject.key() = "${getValue("type").jsonPrimitive.content}/${getValue("id").jsonPrimitive.content}"

    @Test
    fun envelopeBudgetMatchesActual() = parity("parity-envelope")

    @Test
    fun trackingBudgetMatchesActual() = parity("parity-tracking")
}
