package app.tandemforactual.data.store

import androidx.room3.Room
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class NetWorthWidgetSummaryTest {
    private fun record(
        type: String,
        id: String,
        json: String,
    ): RemoteBudgetRecord {
        val body = Json.parseToJsonElement(json) as JsonObject

        fun text(name: String) = (body[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return RemoteBudgetRecord(
            "budget",
            type,
            id,
            "r-$id",
            json,
            "g",
            recordDate = text("date"),
            accountId = text("account"),
            amountMinor = (body["amount"] as? JsonPrimitive)?.content?.toLongOrNull(),
        )
    }

    @Test
    fun netWorthIsWhatOpenAccountsHoldLessWhatTheyOwe() =
        runBlocking {
            val database = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val repository = LocalBudgetRepository(database) { 1L }
                repository.applyFullSnapshot(
                    budgetId = "budget",
                    generation = "g",
                    cursor = "1",
                    expectedCheckpoint = null,
                    records =
                        listOf(
                            record("account", "checking", """{"id":"checking","name":"Checking","closed":false}"""),
                            record("account", "card", """{"id":"card","name":"Card"}"""),
                            record("account", "old", """{"id":"old","name":"Old","closed":true}"""),
                            record("transaction", "pay", """{"date":"2026-11-01","account":"checking","amount":250000}"""),
                            record("transaction", "shop", """{"date":"2026-11-02","account":"card","amount":-40000}"""),
                            record("transaction", "gone", """{"date":"2026-11-03","account":"old","amount":99900}"""),
                        ),
                )
                val open = mapOf("selectedBudgetId" to "budget", "selectedBudget" to "Home")
                val summary = netWorthWidgetSummary(repository, open)!!
                assertEquals(210_000L, summary.netWorthMinor)
                assertEquals(250_000L, summary.assetsMinor)
                assertEquals(40_000L, summary.debtsMinor)
                assertEquals("Home", summary.budgetName)

                val hidden = netWorthWidgetSummary(repository, open + ("privacyModeEnabled" to "true"))!!
                assertTrue(hidden.amountsHidden)
                assertNull(hidden.netWorthMinor)
                assertTrue(netWorthWidgetSummary(repository, open + ("selectedBudgetEncrypted" to "true"))!!.locked)
                assertNull(netWorthWidgetSummary(repository, emptyMap()))
            } finally {
                database.close()
            }
        }
}
