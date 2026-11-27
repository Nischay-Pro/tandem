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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActualScheduleCreateTest {
    private class Budget(
        val db: ActualDatabase,
        val edits: ActualBudgetEdits,
    ) {
        val schedules get() = edits.schedules

        fun id(sql: String) = db.first(sql)!!["id"] as String

        val savings get() = id("SELECT id FROM accounts WHERE name = 'Savings'")
        val mystery get() = id("SELECT id FROM payees WHERE name = 'Mystery'")
        val rent get() = id("SELECT id FROM categories WHERE name = 'Rent'")
        val power get() = id("SELECT id FROM categories WHERE name = 'Power'")

        fun rule(scheduleId: String) =
            db.first("SELECT conditions, actions FROM rules WHERE id = (SELECT rule FROM schedules WHERE id = ?)", scheduleId)!!

        fun lastRun() = db.first("SELECT value FROM tandem_prefs WHERE id = 'lastScheduleRun'")?.get("value")
    }

    private fun fixture(today: String = "2026-11-21"): Budget {
        val name = "parity-envelope"
        val entries = ZipArchive.read(checkNotNull(javaClass.getResource("/actual/$name/budget.zip")).readBytes())
        val file =
            File.createTempFile("schedule-create", ".sqlite").apply {
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
            ) { System.currentTimeMillis() }.fullSync(clock, null)
        }
        val writer = ActualWriter(store, clock, System::currentTimeMillis) { UUID.randomUUID().toString() }
        return Budget(db, ActualBudgetEdits(writer, { today }))
    }

    private fun Budget.rejects(
        message: String,
        fields: JsonObject,
    ) = assertEquals(message, assertFailsWith<ActualEditException> { schedules.create("new", fields) }.message)

    private fun monthly(start: String) =
        buildJsonObject {
            put("frequency", "monthly")
            put("start", start)
            put("interval", 1)
            put("patterns", JsonArray(emptyList()))
            put("skipWeekend", false)
            put("weekendSolveMode", "after")
            put("endMode", "never")
        }

    private fun Budget.fields(build: JsonObjectBuilder.() -> Unit = {}) =
        buildJsonObject {
            put("accountId", savings)
            put("payeeId", mystery)
            put("amountOp", "isapprox")
            put("amountMinor", -4_321)
            put("date", monthly("2026-11-02"))
            build()
        }

    @Test
    fun createWritesTheRuleNextDateAndScheduleActualsFormWrites() {
        val budget = fixture()
        budget.schedules.create(
            "s1",
            budget.fields {
                put("name", "  Flat rent ")
                put("categoryId", budget.rent)
                put("postsTransaction", false)
            },
        )
        val rule = budget.rule("s1")
        val date = stringifyJson(monthly("2026-11-02"))
        assertEquals(
            """[{"op":"is","field":"description","value":"${budget.mystery}"},""" +
                """{"op":"is","field":"acct","value":"${budget.savings}"},""" +
                """{"op":"isapprox","field":"date","value":$date},""" +
                """{"op":"isapprox","field":"amount","value":-4321}]""",
            rule["conditions"],
        )
        assertEquals(
            """[{"op":"link-schedule","value":"s1"},{"op":"set","field":"category","value":"${budget.rent}"}]""",
            rule["actions"],
        )
        val row = budget.db.first("SELECT name, posts_transaction, completed FROM schedules WHERE id = 's1'")!!
        assertEquals("Flat rent", row["name"])
        assertEquals(0L, row["posts_transaction"])
        assertNull(row["completed"])
        assertEquals("2026-12-02", budget.schedules.nextDate("s1"))
        assertEquals(
            listOf(20261202L, 20261202L),
            budget.db.first("SELECT local_next_date, base_next_date FROM schedules_next_date WHERE schedule_id = 's1'")!!.let {
                listOf(it["local_next_date"], it["base_next_date"])
            },
        )

        budget.schedules.create("s1", budget.fields { put("name", "Ignored") })
        assertEquals("Flat rent", budget.db.first("SELECT name FROM schedules WHERE id = 's1'")!!["name"])
    }

    @Test
    fun createKeepsAnEmptyPayeeAndSkipsAMissingAccountLikeActual() {
        val budget = fixture()
        budget.schedules.create(
            "s2",
            buildJsonObject {
                put("name", " ")
                put("amountOp", "isbetween")
                put(
                    "amountRangeMinor",
                    buildJsonObject {
                        put("num1", -3_000)
                        put("num2", -1_000)
                    },
                )
                put("date", "2026-12-24")
            },
        )
        val rule = budget.rule("s2")
        assertEquals(
            """[{"op":"is","field":"description","value":null},{"op":"isapprox","field":"date","value":"2026-12-24"},""" +
                """{"op":"isbetween","field":"amount","value":{"num1":-3000,"num2":-1000}}]""",
            rule["conditions"],
        )
        assertEquals("""[{"op":"link-schedule","value":"s2"}]""", rule["actions"])
        val row = budget.db.first("SELECT name, posts_transaction FROM schedules WHERE id = 's2'")!!
        assertNull(row["name"])
        assertNull(row["posts_transaction"])
        assertEquals("2026-12-24", budget.schedules.nextDate("s2"))
    }

    @Test
    fun aPostingScheduleCanRunTheServiceAgainTodayAndPostsOnlyOnce() {
        val budget = fixture("2026-12-02")
        budget.db.execute("CREATE TABLE IF NOT EXISTS tandem_prefs (id TEXT PRIMARY KEY, value TEXT)")
        budget.db.execute("INSERT INTO tandem_prefs (id, value) VALUES ('lastScheduleRun', '2026-12-02')")
        budget.schedules.create(
            "auto",
            budget.fields {
                put("name", "Auto rent")
                put("categoryId", budget.rent)
                put("postsTransaction", true)
            },
        )
        assertNull(budget.lastRun())
        budget.schedules.runService(syncSuccess = true)
        budget.schedules.runService(syncSuccess = true)
        val posted = budget.db.query("SELECT amount, category, date FROM v_transactions WHERE schedule = 'auto'")
        assertEquals(1, posted.size)
        assertEquals(budget.rent, posted.single()["category"])
        assertEquals(20261202L, posted.single()["date"])
    }

    @Test
    fun createLinksTheChosenTransactions() {
        val budget = fixture()
        val transaction = budget.id("SELECT id FROM transactions WHERE tombstone = 0 ORDER BY date DESC, id LIMIT 1")
        budget.schedules.create(
            "linked",
            budget.fields { put("linkTransactionIds", JsonArray(listOf(JsonPrimitive(transaction), JsonPrimitive(transaction)))) },
        )
        assertEquals("linked", budget.db.first("SELECT schedule FROM transactions WHERE id = ?", transaction)!!["schedule"])
        budget.rejects(
            "Choose transactions in this budget",
            budget.fields { put("linkTransactionIds", JsonArray(listOf(JsonPrimitive("missing")))) },
        )
        budget.rejects(
            "Choose transactions in this budget",
            budget.fields { put("linkTransactionIds", JsonArray(listOf(JsonPrimitive(1)))) },
        )
        assertNull(budget.db.first("SELECT id FROM schedules WHERE id = 'new'"))
    }

    @Test
    fun createChecksItsFields() {
        val budget = fixture()
        budget.schedules.create("taken", budget.fields { put("name", "Taken") })
        budget.rejects("A schedule with this name already exists", budget.fields { put("name", "Taken") })
        budget.rejects("Schedule names must be between 1 and 100 characters", budget.fields { put("name", "x".repeat(101)) })
        budget.rejects("Choose a date for this schedule", JsonObject(budget.fields() - "date"))
        budget.rejects("Enter an amount for this schedule", JsonObject(budget.fields() - "amountMinor" - "amountOp"))
        budget.rejects("Choose a category in this budget", budget.fields { put("categoryId", "missing") })
        budget.rejects("Choose a category", budget.fields { put("categoryId", " ") })
        budget.rejects("Choose whether the schedule adds transactions", budget.fields { put("postsTransaction", "yes") })
        budget.rejects("Choose an open account in this budget", budget.fields { put("accountId", "missing") })
    }

    @Test
    fun editSetsReplacesAndClearsTheCategoryAndAutoAdd() {
        val budget = fixture()
        budget.schedules.create("e", budget.fields())
        val ruleId = budget.db.first("SELECT rule FROM schedules WHERE id = 'e'")!!["rule"] as String
        budget.db.execute(
            "UPDATE rules SET actions = ? WHERE id = ?",
            """[{"op":"link-schedule","value":"e"},{"op":"set","field":"category","value":"x","options":{"template":"t"}}]""",
            ruleId,
        )
        budget.schedules.edit("e", buildJsonObject { put("categoryId", budget.rent) })
        budget.schedules.edit(
            "e",
            buildJsonObject {
                put("categoryId", budget.power)
                put("postsTransaction", true)
            },
        )
        val actions = budget.rule("e")["actions"] as String
        assertTrue(budget.power in actions && budget.rent !in actions && "\"template\":\"t\"" in actions)
        assertEquals(1L, budget.db.first("SELECT posts_transaction FROM schedules WHERE id = 'e'")!!["posts_transaction"])
        budget.schedules.edit(
            "e",
            buildJsonObject {
                put("categoryId", JsonNull)
                put("postsTransaction", false)
            },
        )
        assertTrue(budget.power !in (budget.rule("e")["actions"] as String))
        assertEquals(0L, budget.db.first("SELECT posts_transaction FROM schedules WHERE id = 'e'")!!["posts_transaction"])
    }
}
