package app.tandemforactual.data.store

import androidx.room3.Room
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class BudgetWidgetSummaryTest {
    @Test
    fun monthTotalsReadReadyToAssignAndSpending() {
        assertEquals(12_00L to 340_50L, budgetMonthTotals("""{"toBudget":1200,"totalSpent":-34050}"""))
        assertEquals(null to null, budgetMonthTotals("""{"toBudget":"x"}"""))
        assertEquals(null to null, budgetMonthTotals("not json"))
        assertEquals(null to null, budgetMonthTotals("[]"))
    }

    @Test
    fun summaryFollowsTheSelectedBudgetAndPrivacySettings() =
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
                            RemoteBudgetRecord(
                                "budget",
                                "budget-month",
                                "2026-11",
                                "r1",
                                """{"toBudget":500,"totalSpent":-2500}""",
                                "generation",
                            ),
                            RemoteBudgetRecord(
                                "budget",
                                "budget-settings",
                                "display",
                                "r2",
                                """{"currencyCode":"EUR","currencySymbol":"€","currencyFractionDigits":2}""",
                                "generation",
                            ),
                        ),
                )
                val open = mapOf("selectedBudgetId" to "budget", "selectedBudget" to "Home")

                assertNull(budgetWidgetSummary(repository, emptyMap(), "2026-11"))
                assertNull(budgetWidgetSummary(repository, mapOf("selectedBudgetId" to " "), "2026-11"))

                val summary = budgetWidgetSummary(repository, open, "2026-11")!!
                assertEquals("Home", summary.budgetName)
                assertEquals(500L, summary.readyToAssignMinor)
                assertEquals(2500L, summary.spentMinor)
                assertEquals(false, summary.amountsHidden)
                assertEquals("EUR", summary.displaySettings?.currencyCode)

                val noMonth = budgetWidgetSummary(repository, mapOf("selectedBudgetId" to "budget"), "2026-12")!!
                assertEquals("Budget", noMonth.budgetName)
                assertNull(noMonth.readyToAssignMinor)
                assertNull(noMonth.spentMinor)

                listOf("biometricLockEnabled", "privacyModeEnabled", "selectedBudgetEncrypted").forEach { key ->
                    val hidden = budgetWidgetSummary(repository, open + (key to "true"), "2026-11")!!
                    assertEquals(true, hidden.amountsHidden, key)
                    assertNull(hidden.readyToAssignMinor)
                    assertNull(hidden.displaySettings)
                }
            } finally {
                database.close()
            }
        }
}
