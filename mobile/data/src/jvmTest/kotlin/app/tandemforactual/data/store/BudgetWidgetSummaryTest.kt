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
        assertEquals(-50L to 0L, budgetMonthTotals("""{"toBudget":-50,"totalSpent":4000}"""))
        assertEquals(0L to 0L, budgetMonthTotals("""{"toBudget":0,"totalSpent":0}"""))
        assertEquals(null to null, budgetMonthTotals("not json"))
        assertEquals(null to null, budgetMonthTotals("[]"))
    }

    @Test
    fun trackingSavingsAreIncomeLessSpending() {
        assertEquals(1_500L, trackingSavedMinor("""{"totalIncome":5000,"totalSpent":-3500}"""))
        assertEquals(-200L, trackingSavedMinor("""{"totalIncome":0,"totalSpent":-200}"""))
        assertNull(trackingSavedMinor("""{"totalSpent":-200}"""))
        assertNull(trackingSavedMinor("""{"totalIncome":5}"""))
        assertNull(trackingSavedMinor("not json"))
        assertEquals("Ready to assign", BudgetWidgetSummary("Home", 0, 0, false, null).headline)
        assertEquals("Saved this month", BudgetWidgetSummary("Home", 0, 0, false, null, tracking = true).headline)
    }

    @Test
    fun trackingBudgetsShowWhatWasSavedInsteadOfToBudget() =
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
                                """{"toBudget":null,"totalIncome":5000,"totalSpent":-3500,"categoryGroups":[]}""",
                                "generation",
                            ),
                            RemoteBudgetRecord(
                                "budget",
                                "budget-settings",
                                "display",
                                "r2",
                                """{"currencyCode":"EUR","currencySymbol":"€","currencyFractionDigits":2,"budgetType":"tracking"}""",
                                "generation",
                            ),
                        ),
                )
                val open = mapOf("selectedBudgetId" to "budget", "selectedBudget" to "Home")

                val summary = budgetWidgetSummary(repository, open, "2026-11-30")!!
                assertEquals(true, summary.tracking)
                assertEquals(1_500L, summary.readyToAssignMinor)
                assertEquals(3_500L, summary.spentMinor)
                assertNull(budgetWidgetSummary(repository, open, "2027-01-01")!!.readyToAssignMinor)
                assertEquals(
                    """{"version":1,"month":"2026-11","state":"ready","budgetName":"Home","readyToAssign":"1500",""" +
                        """"readyToAssignNegative":false,"readyLabel":"Saved this month","spent":"3500","categories":[]}""",
                    widgetSnapshotJson(repository, open, "2026-11-30") { minor, _ -> "$minor" },
                )
            } finally {
                database.close()
            }
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
                                "budget-month",
                                "2026-12",
                                "r3",
                                """{"toBudget":250,"totalSpent":1200}""",
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

                assertNull(budgetWidgetSummary(repository, emptyMap(), "2026-11-26"))
                assertNull(budgetWidgetSummary(repository, mapOf("selectedBudgetId" to " "), "2026-11-26"))

                val summary = budgetWidgetSummary(repository, open, "2026-11-30")!!
                assertEquals("Home", summary.budgetName)
                assertEquals(500L, summary.readyToAssignMinor)
                assertEquals(2500L, summary.spentMinor)
                assertEquals(false, summary.amountsHidden)
                assertEquals("EUR", summary.displaySettings?.currencyCode)

                val nextMonth = budgetWidgetSummary(repository, open, "2026-12-01")!!
                assertEquals(250L, nextMonth.readyToAssignMinor)
                assertEquals(0L, nextMonth.spentMinor)

                val noMonth = budgetWidgetSummary(repository, mapOf("selectedBudgetId" to "budget"), "2027-01-01")!!
                assertEquals("Budget", noMonth.budgetName)
                assertNull(noMonth.readyToAssignMinor)
                assertNull(noMonth.spentMinor)

                listOf("biometricLockEnabled", "privacyModeEnabled", "selectedBudgetEncrypted").forEach { key ->
                    val hidden = budgetWidgetSummary(repository, open + (key to "true"), "2026-11-26")!!
                    assertEquals(true, hidden.amountsHidden, key)
                    assertNull(hidden.readyToAssignMinor)
                    assertNull(hidden.displaySettings)
                }
            } finally {
                database.close()
            }
        }
}
