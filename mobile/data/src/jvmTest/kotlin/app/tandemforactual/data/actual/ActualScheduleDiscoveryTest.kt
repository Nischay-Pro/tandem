package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActualScheduleDiscoveryTest {
    private fun fixture(): Pair<ActualDatabase, ActualBudgetEdits> {
        val name = "parity-envelope"
        val entries = ZipArchive.read(checkNotNull(javaClass.getResource("/actual/$name/budget.zip")).readBytes())
        val file =
            File.createTempFile("schedule-discovery", ".sqlite").apply {
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
        return db to ActualBudgetEdits(writer, { "2026-11-21" })
    }

    private class Budget(
        val db: ActualDatabase,
        val edits: ActualBudgetEdits,
        val account: String,
        val climbing: String,
        val coffee: String,
    )

    private fun budget(): Budget {
        val (db, edits) = fixture()
        val account = edits.createAccount("Club card", false, null)
        val climbing = edits.createPayee("Climbing wall")
        val coffee = edits.createPayee("Corner coffee")
        val payments =
            listOf("07", "08", "09", "10", "11").map { mapOf("date" to "2026-$it-05", "amount" to -4_500L, "payee" to climbing) } +
                listOf("2026-10-26" to -350L, "2026-11-02" to -360L, "2026-11-09" to -340L, "2026-11-16" to -350L).map { (date, amount) ->
                    mapOf("date" to date, "amount" to amount, "payee" to coffee)
                } + mapOf("date" to "2026-09-17", "amount" to -9_999L, "payee" to coffee)
        edits.transactions.addTransactions(account, payments, runTransfers = false)
        return Budget(db, edits, account, climbing, coffee)
    }

    @Test
    fun findsWeeklyAndMonthlyPaymentsAndTheirFirstDateLikeActual() {
        val budget = budget()

        val found = discoverSchedules(budget.db, "2026-11-21").filter { it.accountId == budget.account }

        assertEquals(
            listOf(
                DiscoveredSchedule(budget.account, budget.coffee, -340, false, """{"frequency":"weekly","start":"2026-10-26"}""", true),
                DiscoveredSchedule(budget.account, budget.climbing, -4_500, true, """{"start":"2026-07-05","frequency":"monthly"}""", true),
            ),
            found,
        )
    }

    @Test
    fun creatingAFoundScheduleLinksEveryPaymentItMatches() {
        val budget = budget()
        val climbing = discoverSchedules(budget.db, "2026-11-21").first { it.payeeId == budget.climbing }
        val fields =
            buildJsonObject {
                put("discovered", true)
                put("accountId", climbing.accountId)
                put("payeeId", climbing.payeeId)
                put("amountOp", if (climbing.exactAmount) "is" else "isapprox")
                put("amountMinor", climbing.amountMinor)
                put("date", Json.parseToJsonElement(climbing.dateJson))
                put("dateOp", if (climbing.exactDate) "is" else "isapprox")
            }

        budget.edits.schedules.create("found", fields)

        assertEquals(5L, budget.db.first("SELECT count(*) AS n FROM transactions WHERE schedule = 'found'")!!["n"])
        val conditions = budget.db.first("SELECT conditions FROM rules WHERE id = (SELECT rule FROM schedules WHERE id = 'found')")!!
        assertEquals(
            """[{"op":"is","field":"acct","value":"${budget.account}"},{"op":"is","field":"description","value":"${budget.climbing}"},""" +
                """{"op":"is","field":"date","value":${climbing.dateJson}},{"op":"is","field":"amount","value":-4500}]""",
            conditions["conditions"],
        )
        assertEquals(
            "Choose a schedule found in this budget",
            assertFailsWith<ActualEditException> {
                budget.edits.schedules.create("other", JsonObject(fields + ("dateOp" to JsonPrimitive("on"))))
            }.message,
        )
    }
}
