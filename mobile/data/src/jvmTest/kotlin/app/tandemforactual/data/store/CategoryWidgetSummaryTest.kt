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

class CategoryWidgetSummaryTest {
    private val month =
        """
        {"month":"2026-11","categoryGroups":[
          {"id":"living","name":"Living","categories":[
            {"id":"groceries","name":"Groceries","balance":-1240},
            {"id":"rent","name":"Rent","balance":0},
            {"id":"old","name":"Old","balance":-500,"hidden":true},
            {"id":"bonus","name":"Bonus","balance":5,"is_income":true},
            {"id":"broken","name":"Broken"},
            {"id":"nameless","balance":3},
            {"name":"No id","balance":-1},
            "not a category"
          ]},
          {"id":"fun","categories":[{"id":"games","name":"Games","balance":4200}]},
          {"id":"archive","hidden":true,"categories":[{"id":"a","name":"A","balance":-9}]},
          {"id":"income","is_income":true,"categories":[{"id":"salary","name":"Salary","balance":-100}]},
          {"id":"empty"},
          "not a group"
        ]}
        """

    @Test
    fun monthCategoriesKeepVisibleExpenseCategoriesInBudgetOrder() {
        assertEquals(
            listOf(
                WidgetCategory("groceries", "Groceries", "Living", -1240),
                WidgetCategory("rent", "Rent", "Living", 0),
                WidgetCategory("games", "Games", "", 4200),
            ),
            budgetMonthCategories(month),
        )
        assertEquals(emptyList(), budgetMonthCategories("not json"))
        assertEquals(emptyList(), budgetMonthCategories("[]"))
        assertEquals(emptyList(), budgetMonthCategories("""{"month":"2026-11"}"""))
    }

    @Test
    fun summaryShowsTheChosenCategoriesUnlessTheBudgetIsProtected() =
        withRepository { repository ->
            val open = mapOf("selectedBudgetId" to "budget", "selectedBudget" to "Home")

            assertNull(categoryWidgetSummary(repository, emptyMap(), "2026-11-26", listOf("rent")))

            val summary = categoryWidgetSummary(repository, open, "2026-11-26", listOf("games", "gone", "groceries", "games"))!!
            assertEquals("Home", summary.budgetName)
            assertEquals(listOf("games", "groceries"), summary.categories.map { it.id })
            assertEquals(false, summary.amountsHidden)
            assertEquals("EUR", summary.displaySettings?.currencyCode)

            assertEquals(emptyList(), categoryWidgetSummary(repository, open, "2027-01-01", listOf("rent"))!!.categories)

            listOf("biometricLockEnabled", "privacyModeEnabled", "selectedBudgetEncrypted").forEach { key ->
                val hidden = categoryWidgetSummary(repository, open + (key to "true"), "2026-11-26", listOf("rent"))!!
                assertTrue(hidden.amountsHidden, key)
                assertEquals(emptyList(), hidden.categories)
                assertNull(hidden.displaySettings)
                assertEquals(key == "selectedBudgetEncrypted", hidden.locked, key)
            }
        }

    @Test
    fun choicesNeedAnOpenUnlockedBudget() =
        withRepository { repository ->
            val open = mapOf("selectedBudgetId" to "budget")

            assertEquals(listOf("groceries", "rent", "games"), widgetCategoryChoices(repository, open, "2026-11-01")!!.map { it.id })
            assertEquals(3, widgetCategoryChoices(repository, open + ("privacyModeEnabled" to "true"), "2026-11-01")!!.size)
            assertEquals(
                listOf(WidgetAccount("savings", "Savings"), WidgetAccount("cash", "Cash"), WidgetAccount("checking", "checking")),
                widgetAccountChoices(repository, open),
            )
            assertNull(widgetCategoryChoices(repository, emptyMap(), "2026-11-01"))
            assertNull(widgetAccountChoices(repository, emptyMap()))
            listOf("biometricLockEnabled", "selectedBudgetEncrypted").forEach { key ->
                assertNull(widgetCategoryChoices(repository, open + (key to "true"), "2026-11-01"), key)
                assertNull(widgetAccountChoices(repository, open + (key to "true")), key)
            }
        }

    private fun withRepository(block: suspend (LocalBudgetRepository) -> Unit) =
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
                            record("budget-month", "2026-11", month),
                            record("budget-settings", "display", DISPLAY),
                            record("account", "checking", """{"id":"checking","sort_order":3}"""),
                            record("account", "cash", """{"id":"cash","name":"Cash","sort_order":2}"""),
                            record("account", "savings", """{"id":"savings","name":"Savings"}"""),
                            record("account", "old", """{"id":"old","name":"Old","closed":true}"""),
                            record("account", "nameless", """{"name":"No id","sort_order":9}"""),
                        ),
                )
                block(repository)
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
    }
}
