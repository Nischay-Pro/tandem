package app.tandemforactual.data.store

import androidx.room3.Room
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class WidgetSnapshotTest {
    private val format = { minor: Long, settings: BudgetDisplaySettings? -> "${settings?.currencySymbol ?: "?"}$minor" }

    @Test
    fun overspendingSaysOver() {
        assertEquals("5 over", categoryBalanceText(-5) { "$it" })
        assertEquals("0", categoryBalanceText(0) { "$it" })
    }

    @Test
    fun snapshotHoldsOnlyThisMonthsTotalsAndCategories() =
        runBlocking {
            val database =
                openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val repository = LocalBudgetRepository(database) { 1L }
                repository.applyFullSnapshot(
                    budgetId = "budget",
                    generation = "generation",
                    cursor = "1",
                    expectedCheckpoint = null,
                    records =
                        listOf(
                            record("budget-month", "2026-11", MONTH),
                            record("budget-month", "2026-12", """{"categoryGroups":[]}"""),
                            record("budget-settings", "display", DISPLAY),
                        ),
                )
                val open = mapOf("selectedBudgetId" to "budget", "selectedBudget" to "Home")

                assertEquals(
                    """{"version":1,"month":"2026-11","state":"none"}""",
                    widgetSnapshotJson(repository, emptyMap(), "2026-11-26", format),
                )
                assertEquals(
                    """{"version":1,"month":"2026-11","state":"hidden","budgetName":"Home"}""",
                    widgetSnapshotJson(repository, open + ("privacyModeEnabled" to "true"), "2026-11-26", format),
                )
                assertEquals(
                    """{"version":1,"month":"2026-11","state":"ready","budgetName":"Home","readyToAssign":"E-50",""" +
                        """"readyToAssignNegative":true,"spent":"E2500","categories":[{"id":"food","name":"Food","group":"Living",""" +
                        """"amount":"E-1240","text":"E1240 over","overspent":true},{"id":"rent","name":"Rent","group":"Living",""" +
                        """"amount":"E300","text":"E300","overspent":false}]}""",
                    widgetSnapshotJson(repository, open, "2026-11-26", format),
                )
                assertEquals(
                    """{"version":1,"month":"2026-12","state":"ready","budgetName":"Home","categories":[]}""",
                    widgetSnapshotJson(repository, open, "2026-12-01", format),
                )
                assertEquals(
                    """{"version":1,"month":"2027-01","state":"ready","budgetName":"Home","categories":[]}""",
                    widgetSnapshotJson(repository, open, "2027-01-01", format),
                )
            } finally {
                database.close()
            }
        }

    private fun record(
        type: String,
        id: String,
        json: String,
    ) = RemoteBudgetRecord("budget", type, id, "r-$id", json, "generation")

    private companion object {
        const val DISPLAY = """{"currencyCode":"EUR","currencySymbol":"E","currencyFractionDigits":2}"""
        const val MONTH =
            """{"toBudget":-50,"totalSpent":-2500,"categoryGroups":[{"id":"living","name":"Living","categories":[""" +
                """{"id":"food","name":"Food","balance":-1240},{"id":"rent","name":"Rent","balance":300}]}]}"""
    }
}
