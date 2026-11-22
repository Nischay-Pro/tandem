package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActualRuleEngineTest {
    private fun fixtureDb(): ActualDatabase {
        val entries = ZipArchive.read(checkNotNull(javaClass.getResource("/actual/rules-parity/budget.zip")).readBytes())
        val file =
            File.createTempFile("rules", ".sqlite").apply {
                writeBytes(entries.getValue("db.sqlite"))
                deleteOnExit()
            }
        val db = ActualDatabase(BundledSQLiteDriver().open(file.path))
        val store = ActualMessageStore(db)
        val clock = store.loadClock()
        val server = SyncProtocol.decodeResponse(checkNotNull(javaClass.getResource("/actual/rules-parity/sync-response.bin")).readBytes())
        runBlocking {
            ActualSyncEngine(
                store,
                { request -> SyncResponse(server.messages.filter { it.timestamp > request.since }, server.merkle) },
                "g",
                "f",
            ) {
                System.currentTimeMillis()
            }.fullSync(clock, null)
        }
        return db
    }

    private fun toKotlin(element: JsonElement): Any? =
        when (element) {
            JsonNull -> null
            is JsonPrimitive ->
                if (element.isString) {
                    element.content
                } else {
                    element.booleanOrNull ?: element.longOrNull
                        ?: element.content.toDouble()
                }
            is JsonArray -> element.map(::toKotlin)
            is JsonObject -> element.mapValues { toKotlin(it.value) }
        }

    private fun toJson(value: Any?): JsonElement =
        when (value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value.toLong())
            is String -> JsonPrimitive(value)
            is List<*> -> JsonArray(value.map(::toJson))
            is Map<*, *> -> JsonObject(value.entries.associate { it.key as String to toJson(it.value) })
            else -> error("unexpected $value")
        }

    private fun normalize(element: JsonElement): JsonElement =
        when (element) {
            is JsonObject ->
                JsonObject(
                    element.filterKeys { !it.startsWith("_") }.mapValues { (key, value) ->
                        if (key == "id" &&
                            element["is_child"] == JsonPrimitive(true)
                        ) {
                            JsonPrimitive("child")
                        } else {
                            normalize(value)
                        }
                    },
                )
            is JsonArray -> JsonArray(element.map(::normalize))
            else -> element
        }

    @Test
    fun previewsWhichTransactionsARuleMatches() {
        val db = fixtureDb()
        val engine = ActualRuleEngine(db) { error("unused") }
        val account = db.first("SELECT id FROM accounts WHERE tombstone = 0")!!["id"]
        db.execute(
            "INSERT INTO transactions (id, acct, amount, date, isParent, isChild, tombstone, cleared, sort_order) " +
                "VALUES ('preview-1', ?, -4321, 20261120, 0, 0, 0, 1, 1)",
            account,
        )
        val row = db.first("SELECT id, amount FROM v_transactions WHERE is_parent = 0 ORDER BY date DESC")!!
        val rule =
            kotlinx.serialization.json.Json
                .parseToJsonElement(
                    """{"conditionsOp":"and","conditions":[{"op":"is","field":"amount","type":"number","value":${row["amount"]}}]}""",
                ).jsonObject
        assertTrue(row["id"] in engine.preview(rule))
        assertEquals(emptyList(), engine.preview(kotlinx.serialization.json.JsonObject(emptyMap())))
        assertEquals(1, engine.preview(rule, limit = 1).size)
        val wire =
            kotlinx.serialization.json.Json
                .parseToJsonElement(
                    """{"conditionsOp":"or","conditions":[{"field":"amount","op":"is","amountMinor":-4321},""" +
                        """{"field":"notes","op":"is","ids":["x"]},""" +
                        """{"field":"notes","op":"contains","range":{"num1":1,"num2":2},"inflow":true},{"field":"notes","op":"is"}]}""",
                ).jsonObject
        assertTrue("preview-1" in engine.preview(wire))
        val health = ActualDataHealth(db).rows(listOf(row["id"] as String))
        assertEquals(row["amount"], health.single().amount)
    }

    @Test
    fun rulesMatchActualsOwnEngine() {
        val db = fixtureDb()
        val engine = ActualRuleEngine(db) { error("Actual already created every payee these cases need") }
        val cases = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/actual/rules-cases.json")).readText()).jsonArray
        cases.forEachIndexed { index, case ->
            @Suppress("UNCHECKED_CAST")
            val input = toKotlin(case.jsonObject.getValue("input")) as Map<String, Any?>
            val result = engine.run(input) { "generated" }
            assertEquals(normalize(case.jsonObject.getValue("output")), normalize(toJson(result)), "case $index")
        }
        db.close()
    }
}
