package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ActualPostingTest {
    private class Fixture(
        val db: ActualDatabase,
        val writer: ActualWriter,
    ) {
        var today = "2026-11-21"
        val transactions = ActualTransactions(writer)
        val schedules = ActualScheduleEdits(writer, { today }, transactions)
        val engine = ActualRuleEngine(db) { transactions.insertPayee(it) }

        fun id(
            table: String,
            name: String,
        ) = db.first("SELECT id FROM $table WHERE name = ? AND tombstone = 0", name)!!["id"] as String

        fun rule(
            conditions: String,
            actions: String,
            op: String = "and",
            stage: String? = null,
        ) = UUID.randomUUID().toString().also {
            db.execute(
                "INSERT INTO rules (id, stage, conditions, actions, conditions_op, tombstone) VALUES (?, ?, ?, ?, ?, 0)",
                it,
                stage,
                conditions,
                actions,
                op,
            )
        }

        fun run(vararg fields: Pair<String, Any?>) = engine.run(linkedMapOf(*fields)) { "split" }
    }

    private fun fixture(name: String): Fixture {
        val entries = ZipArchive.read(checkNotNull(javaClass.getResource("/actual/$name/budget.zip")).readBytes())
        val file =
            File.createTempFile("posting", ".sqlite").apply {
                writeBytes(entries.getValue("db.sqlite"))
                deleteOnExit()
            }
        val db = ActualDatabase(BundledSQLiteDriver().open(file.path))
        val store = ActualMessageStore(db)
        val clock = store.loadClock()
        val server = SyncProtocol.decodeResponse(checkNotNull(javaClass.getResource("/actual/$name/sync-response.bin")).readBytes())
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
        return Fixture(db, ActualWriter(store, clock, System::currentTimeMillis) { UUID.randomUUID().toString() })
    }

    @Test
    fun conditionsCoverEveryOperator() {
        val f = fixture("rules-engine-parity".let { "rules-parity" })
        f.db.execute("UPDATE rules SET tombstone = 1")
        val checking = f.id("accounts", "Checking")
        val food = f.id("categories", "Food")
        val tag = { notes: String -> f.run("account" to checking, "date" to "2026-05-01", "amount" to -500L, "notes" to notes)["notes"] }
        f.rule(
            """[{"op":"is","field":"amount","value":500,"options":{"outflow":true}}]""",
            """[{"op":"set","field":"notes","value":"outflow"}]""",
        )
        assertEquals("outflow", tag("x"))
        f.rule(
            """[{"op":"is","field":"amount","value":500,"options":{"inflow":true}}]""",
            """[{"op":"set","field":"notes","value":"inflow"}]""",
        )
        assertEquals("inflow", f.run("account" to checking, "date" to "2026-05-01", "amount" to 500L)["notes"])
        assertEquals("outflow", f.run("account" to checking, "date" to "2026-05-01", "amount" to -500L)["notes"])
        assertEquals(null, f.run("account" to checking, "date" to "2026-05-01", "amount" to 7L)["notes"])
        f.db.execute("UPDATE rules SET tombstone = 1")
        f.rule(
            """[{"op":"is","field":"date","value":{"frequency":"monthly","start":"2026-01-01","interval":1,"patterns":[],"skipWeekend":false,"weekendSolveMode":"after","endMode":"never"}}]""",
            """[{"op":"append-notes","value":" recur"}]""",
        )
        f.rule("""[{"op":"isapprox","field":"date","value":"2026-05-03"}]""", """[{"op":"append-notes","value":" near"}]""")
        f.rule("""[{"op":"is","field":"date","value":"2026"}]""", """[{"op":"append-notes","value":" year"}]""")
        f.rule(
            """[{"op":"isNot","field":"notes","value":"skip"},{"op":"notOneOf","field":"imported_payee","value":["a","b"]}]""",
            """[{"op":"append-notes","value":" not"}]""",
        )
        f.rule(
            """[{"op":"lte","field":"amount","value":-500},{"op":"lte","field":"date","value":"2026-05-01"}]""",
            """[{"op":"prepend-notes","value":"low "}]""",
        )
        f.rule("""[{"op":"is","field":"cleared","value":true}]""", """[{"op":"set","field":"payee_name","value":"Fresh payee"}]""")
        val result =
            f.run(
                "account" to checking,
                "date" to "2026-05-01",
                "amount" to -500L,
                "notes" to "start",
                "cleared" to true,
                "imported_payee" to "c",
            )
        val notes = result["notes"] as String
        assertTrue(notes.startsWith("low start near") && notes.endsWith(" not") && " recur" in notes && " year" in notes, notes)
        val fresh = f.id("payees", "Fresh payee")
        assertEquals(fresh, result["payee"])
        f.db.execute("UPDATE rules SET tombstone = 1")
        f.rule("""[{"op":"is","field":"cleared","value":true}]""", """[{"op":"set","field":"payee_name","value":""}]""")
        assertNull(f.run("account" to checking, "date" to "2026-05-01", "amount" to -1L, "cleared" to true, "payee" to fresh)["payee"])
        f.db.execute("UPDATE rules SET tombstone = 1")
        f.rule("""[{"op":"gt","field":"imported_payee","value":"a"}]""", """[{"op":"set","field":"notes","value":"never"}]""")
        f.rule("""[{"op":"onBudget","field":"account"}]""", """[{"op":"set","field":"category","value":null}]""")
        val cleared = f.run("account" to checking, "date" to "2026-05-01", "amount" to -1L, "category" to food, "imported_payee" to "z")
        assertTrue(cleared["category"] == null && "notes" !in cleared)
        f.rule("""[{"op":"and","field":"category","value":["$food"]}]""", """[{"op":"set","field":"notes","value":"and"}]""")
        f.rule(
            """[{"op":"is","field":"amount","value":{"num1":1,"num2":2}},{"op":"is","field":"cleared","value":false}]""",
            """[{"op":"set","field":"notes","value":"broken"}]""",
            op = "or",
        )
        assertTrue(
            "notes" !in f.run("account" to checking, "date" to "2026-05-01", "amount" to -1L, "category" to food, "cleared" to false),
        )
        assertTrue(f.run("account" to "missing", "date" to "2026-05-01", "amount" to -1L, "category" to food).containsKey("category"))
        f.rule("""[{"op":"is","field":"amount","value":-7}]""", """[{"op":"set","field":"notes","value":"x","options":{"formula":"=1"}}]""")
        assertFailsWith<UnsupportedRuleException> { f.run("account" to checking, "date" to "2026-05-01", "amount" to -7L) }
        f.db.execute("UPDATE rules SET tombstone = 1")
        f.rule(
            """[{"op":"is","field":"amount","value":-8}]""",
            """[{"op":"set-split-amount","value":1,"options":{"splitIndex":1,"method":"formula"}}]""",
        )
        assertFailsWith<UnsupportedRuleException> { f.run("account" to checking, "date" to "2026-05-01", "amount" to -8L) }
        f.db.execute("UPDATE rules SET tombstone = 1")
        f.rule(
            """[{"op":"is","field":"amount","value":-9}]""",
            """[{"op":"set-split-amount","value":-4,"options":{"splitIndex":1,"method":"fixed-amount"}}]""",
        )
        @Suppress("UNCHECKED_CAST")
        val split = f.run("account" to checking, "date" to "2026-05-01", "amount" to -9L)["subtransactions"] as List<Map<String, Any?>>
        assertEquals(-4L, split.single()["amount"])
    }

    @Test
    fun addTransactionsResolvesPayeesAndSplits() {
        val f = fixture("parity-envelope")
        val checking = f.id("accounts", "Checking")
        val groceries = f.id("categories", "Groceries")
        val ids =
            f.transactions.addTransactions(
                checking,
                listOf(
                    mapOf("date" to "2026-11-20", "amount" to -100L, "payee_name" to " New Shop "),
                    mapOf("date" to "2026-11-20", "amount" to -200L, "payee_name" to "new shop", "imported_payee" to "  NS "),
                    mapOf("date" to "2026-11-20", "amount" to -300L, "payee_name" to "Market"),
                    mapOf(
                        "date" to "2026-11-20",
                        "amount" to -500L,
                        "subtransactions" to listOf(mapOf("amount" to -200L, "category" to groceries), mapOf("amount" to -100L)),
                    ),
                    mapOf("date" to "2026-11-20", "amount" to -50L, "subtransactions" to listOf(mapOf("amount" to -50L))),
                ),
                runTransfers = false,
            )
        assertEquals(8, ids.size)
        assertNull(f.db.first("SELECT error FROM transactions WHERE id = ?", ids[6])!!["error"])
        val shop = f.id("payees", "New Shop")
        val rows =
            f.db
                .query(
                    "SELECT * FROM v_transactions_internal WHERE id IN (${ids.joinToString { "?" }})",
                    *ids.toTypedArray(),
                ).associateBy {
                    it["id"]
                }
        assertEquals(shop, rows.getValue(ids[0])["payee"])
        assertEquals(shop, rows.getValue(ids[1])["payee"])
        assertEquals("NS", rows.getValue(ids[1])["imported_payee"])
        assertEquals(f.id("payees", "Market"), rows.getValue(ids[2])["payee"])
        assertTrue((rows.getValue(ids[3])["error"] as String).contains("\"difference\":-200"))
        assertEquals(groceries, rows.getValue(ids[4])["category"])
        assertEquals(ids[3], rows.getValue(ids[5])["parent_id"])
        assertFailsWith<IllegalArgumentException> {
            f.transactions.addTransactions(
                checking,
                listOf(mapOf("amount" to 1L)),
                runTransfers = false,
            )
        }
        f.rule("""[{"op":"is","field":"amount","value":-77}]""", """[{"op":"set","field":"payee_name","value":"Named by a rule"}]""")
        val named =
            f.transactions
                .addTransactions(
                    checking,
                    listOf(mapOf("date" to "2026-11-20", "amount" to -77L)),
                    runTransfers = true,
                ).single()
        assertEquals(
            f.id("payees", "Named by a rule"),
            f.db.first("SELECT payee FROM v_transactions_internal WHERE id = ?", named)!!["payee"],
        )
    }

    @Test
    fun schedulesPostAndAdvanceLikeActual() {
        val f = fixture("parity-envelope")
        val id = f.db.first("SELECT id FROM schedules WHERE tombstone = 0")!!["id"] as String
        assertEquals("2026-12-02", f.schedules.nextDate(id))
        assertFailsWith<ActualEditException> { f.schedules.post(id, "2026-12-01") }
        assertFailsWith<ActualEditException> { f.schedules.post("missing", "2026-12-02") }
        val posted = f.schedules.post(id, "2026-12-02")
        val row = f.db.first("SELECT * FROM v_transactions_internal WHERE id = ?", posted)!!
        assertEquals(id, row["schedule"])
        assertEquals(-150000L, row["amount"])
        f.today = "2026-12-02"
        f.schedules.runService(syncSuccess = true)
        assertEquals("2026-12-02", f.schedules.nextDate(id))
        f.today = "2026-12-05"
        f.schedules.runService(syncSuccess = true)
        assertEquals("2027-01-02", f.schedules.nextDate(id))

        f.db.execute("UPDATE schedules SET posts_transaction = 1 WHERE id = ?", id)
        f.today = "2027-03-10"
        f.schedules.runService(syncSuccess = false)
        assertEquals("2027-01-02", f.schedules.nextDate(id))
        f.schedules.runService(syncSuccess = true)
        assertEquals("2027-04-02", f.schedules.nextDate(id))
        assertEquals(4L, f.db.first("SELECT count(*) AS n FROM v_transactions_internal_alive WHERE schedule = ?", id)!!["n"])
        f.today = "2027-04-02"
        f.schedules.runService(syncSuccess = true)
        assertEquals("2027-04-02", f.schedules.nextDate(id))
        f.schedules.runService(syncSuccess = true)
        assertEquals(5L, f.db.first("SELECT count(*) AS n FROM v_transactions_internal_alive WHERE schedule = ?", id)!!["n"])

        val ruleId = f.db.first("SELECT rule FROM schedules WHERE id = ?", id)!!["rule"] as String
        val conditions = f.db.first("SELECT conditions FROM rules WHERE id = ?", ruleId)!!["conditions"] as String
        f.db.execute(
            "UPDATE rules SET conditions = ? WHERE id = ?",
            Regex(
                "\\{\"op\":\"isapprox\",\"field\":\"date\",\"value\":\\{[^}]*\\}[^}]*\\}",
            ).replace(conditions, "{\"op\":\"is\",\"field\":\"date\",\"value\":\"2027-04-02\"}"),
            ruleId,
        )
        f.db.execute("UPDATE schedules SET posts_transaction = 0 WHERE id = ?", id)
        f.today = "2027-04-03"
        f.schedules.runService(syncSuccess = true)
        assertEquals(1L, f.db.first("SELECT completed FROM schedules WHERE id = ?", id)!!["completed"])
        f.db.execute("UPDATE schedules SET completed = 0, posts_transaction = 1 WHERE id = ?", id)
        f.today = "2027-04-01"
        f.schedules.runService(syncSuccess = true)

        val account = f.db.first("SELECT _account FROM v_schedules WHERE id = ?", id)
        assertTrue(account != null)
        f.db.execute("UPDATE accounts SET closed = 1 WHERE name = 'Checking'")
        f.schedules.runService(syncSuccess = true)
    }

    @Test
    fun completedSchedulesNoLongerLinkTransactions() {
        val f = fixture("rules-parity")
        f.db.execute("UPDATE rules SET tombstone = 1")
        val checking = f.id("accounts", "Checking")
        val rule = f.rule("""[{"op":"is","field":"amount","value":-4242}]""", """[{"op":"link-schedule","value":"done"}]""")
        f.db.execute("INSERT INTO schedules (id, name, rule, completed, tombstone) VALUES ('done', 'Done', ?, 0, 0)", rule)
        val linked = { f.run("account" to checking, "date" to "2026-05-01", "amount" to -4242L)["schedule"] }
        assertEquals("done", linked())
        f.db.execute("UPDATE schedules SET completed = 1 WHERE id = 'done'")
        assertNull(linked())
    }

    @Test
    fun schedulesWithoutAnAccountStillAdvance() {
        val f = fixture("parity-envelope")
        val id = f.db.first("SELECT id FROM schedules WHERE tombstone = 0")!!["id"] as String
        val ruleId = f.db.first("SELECT rule FROM schedules WHERE id = ?", id)!!["rule"] as String
        val conditions = f.db.first("SELECT conditions FROM rules WHERE id = ?", ruleId)!!["conditions"] as String
        val withoutAccount =
            JsonArray(Json.parseToJsonElement(conditions).jsonArray.filter { it.jsonObject["field"]?.jsonPrimitive?.content != "acct" })
        f.db.execute("UPDATE rules SET conditions = ? WHERE id = ?", withoutAccount.toString(), ruleId)
        assertNull(f.db.first("SELECT _account FROM v_schedules WHERE id = ?", id)!!["_account"])
        assertEquals("2026-12-02", f.schedules.nextDate(id))

        f.db.execute("UPDATE schedules SET posts_transaction = 1 WHERE id = ?", id)
        f.today = "2026-12-02"
        f.schedules.runService(syncSuccess = true)
        assertEquals(0L, f.db.first("SELECT count(*) AS n FROM v_transactions_internal_alive WHERE schedule = ?", id)!!["n"])

        f.transactions.addTransactions(
            f.id("accounts", "Checking"),
            listOf(mapOf("date" to "2026-12-02", "amount" to -150000L, "schedule" to id)),
            runTransfers = false,
        )
        f.today = "2026-12-05"
        f.schedules.runService(syncSuccess = true)
        assertEquals("2027-01-02", f.schedules.nextDate(id))
    }
}
