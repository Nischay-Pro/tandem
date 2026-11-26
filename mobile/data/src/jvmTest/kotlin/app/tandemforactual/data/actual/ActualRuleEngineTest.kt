package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
    fun findsPaymentsCarryingATag() {
        val db = fixtureDb()
        val account = db.first("SELECT id FROM accounts WHERE tombstone = 0")!!["id"]
        db.execute(
            "INSERT INTO transactions (id, acct, amount, date, notes, isParent, isChild, tombstone, cleared, sort_order) VALUES " +
                "('zelle-1', ?, 4000, 20261103, 'Zelle #phone-plan #alex', 0, 0, 0, 1, 1), " +
                "('zelle-2', ?, 4000, 20261104, '#phone-plan2', 0, 0, 0, 1, 2), " +
                "('zelle-3', ?, 4000, 20250104, '#phone-plan', 0, 0, 0, 1, 3)",
            account,
            account,
            account,
        )
        val found = ActualBudgetReader(db).taggedPayments("phone-plan", "2026-01-01")
        assertEquals(listOf("zelle-1"), found.map { it.id })
        assertEquals("2026-11-03", found.single().date)
        assertEquals(4000, found.single().amountMinor)
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
    fun matchesDateBetweenRulesInclusively() {
        val db = fixtureDb()
        val engine = ActualRuleEngine(db) { error("unused") }
        db.execute(
            "INSERT INTO rules (id, stage, conditions_op, conditions, actions, tombstone) VALUES ('date-range', 'post', 'and', ?, ?, 0)",
            """[{"op":"isbetween","field":"date","value":{"num1":"2026-11-30","num2":"2026-11-01"}}]""",
            """[{"op":"set","field":"notes","value":"November"}]""",
        )

        fun notes(date: String?) = engine.run(mapOf("id" to "t", "date" to date, "amount" to -100L)) { "generated" }["notes"]
        assertEquals(listOf("November", "November", "November"), listOf("2026-11-01", "2026-11-15", "2026-11-30").map(::notes))
        assertEquals(listOf(null, null, null), listOf("2026-10-31", "2026-12-01", null).map(::notes))
    }

    @Test
    fun previewsDateBetweenRulesAndDropsInvalidRanges() {
        val db = fixtureDb()
        val engine = ActualRuleEngine(db) { error("unused") }
        val account = db.first("SELECT id FROM accounts WHERE tombstone = 0")!!["id"]
        db.execute(
            "INSERT INTO transactions (id, acct, amount, date, isParent, isChild, tombstone, cleared, sort_order) VALUES " +
                "('range-before', ?, -1, 20261031, 0, 0, 0, 1, 1), ('range-start', ?, -1, 20261101, 0, 0, 0, 1, 2), " +
                "('range-end', ?, -1, 20261130, 0, 0, 0, 1, 3), ('range-after', ?, -1, 20261201, 0, 0, 0, 1, 4)",
            account,
            account,
            account,
            account,
        )

        fun preview(range: String) =
            engine
                .preview(
                    Json
                        .parseToJsonElement("""{"conditions":[{"field":"date","op":"isbetween","range":$range}]}""")
                        .jsonObject,
                ).filter { it.startsWith("range-") }
        assertEquals(listOf("range-end", "range-start"), preview("""{"num1":"2026-11-01","num2":"2026-11-30"}"""))
        listOf(
            """{"num1":"2026-11","num2":"2026-11-30"}""",
            """{"num1":"2026-11-01","num2":"2026-02-30"}""",
            """{"num1":20261101,"num2":"2026-11-30"}""",
            """{"num1":"2026-11-01"}""",
            "\"2026-11-01\"",
        ).forEach { assertEquals(emptyList(), preview(it), it) }
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
