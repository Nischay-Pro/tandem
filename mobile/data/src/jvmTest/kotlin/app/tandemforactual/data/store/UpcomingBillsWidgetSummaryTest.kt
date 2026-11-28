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

class UpcomingBillsWidgetSummaryTest {
    private fun schedule(
        id: String,
        json: String,
    ) = RemoteBudgetRecord("budget", "schedule", id, "r-$id", json, "g")

    @Test
    fun billsDueInTheNextTwoWeeksShowSoonestFirst() =
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
                            schedule("phone", """{"name":"Phone","next_date":"2026-12-04","amount":-4500}"""),
                            schedule("rent", """{"name":"Rent","next_date":"2026-11-22","amount":-150000}"""),
                            schedule("later", """{"name":"Later","next_date":"2026-12-30","amount":-1}"""),
                        ),
                )
                val open = mapOf("selectedBudgetId" to "budget", "selectedBudget" to "Home")
                val summary = upcomingBillsWidgetSummary(repository, open, "2026-11-21")!!
                assertEquals(listOf("Rent", "Phone"), summary.bills.map { it.name })
                assertEquals(-150_000L, summary.bills.first().amountMinor)

                val hidden = upcomingBillsWidgetSummary(repository, open + ("biometricLockEnabled" to "true"), "2026-11-21")!!
                assertTrue(hidden.amountsHidden && hidden.bills.isEmpty())
                assertNull(upcomingBillsWidgetSummary(repository, emptyMap(), "2026-11-21"))
            } finally {
                database.close()
            }
        }
}
