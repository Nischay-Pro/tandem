package app.tandemforactual.data.store

import androidx.room3.Room
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OverspentCategoryTest {
    @Test
    fun findsOverspentExpenseCategoriesOnly() {
        val month =
            """
            {"month":"2026-11","categoryGroups":[
              {"id":"living","is_income":false,"categories":[
                {"id":"groceries","name":"Groceries","balance":-1240},
                {"id":"rent","name":"Rent","balance":0},
                {"id":"old","name":"Old","balance":-500,"hidden":true},
                {"id":"broken","name":"Broken"},
                {"name":"No id","balance":-1}
              ]},
              {"id":"archive","hidden":true,"categories":[{"id":"a","name":"A","balance":-9}]},
              {"id":"income","is_income":true,"categories":[{"id":"salary","name":"Salary","balance":-100}]},
              {"id":"empty"},
              "not a group"
            ]}
            """

        assertEquals(listOf(OverspentCategory("groceries", "Groceries", -1240)), overspentCategories(month))
        assertEquals(emptyList(), overspentCategories("not json"))
        assertEquals(emptyList(), overspentCategories("[]"))
        assertEquals(emptyList(), overspentCategories("""{"month":"2026-11"}"""))
    }

    @Test
    fun onlyNewCategoriesAreAlerted() {
        val current = listOf(OverspentCategory("a", "A", -1), OverspentCategory("b", "B", -2))
        assertEquals(listOf(OverspentCategory("b", "B", -2)), newlyOverspent(current, setOf("a")))
    }

    @Test
    fun messagesNameTheCategories() {
        fun cats(vararg names: String) = names.map { OverspentCategory(it, it, -1) }
        assertEquals(null, overspendingMessage(emptyList()))
        assertEquals("Fuel is over budget this month.", overspendingMessage(cats("Fuel")))
        assertEquals("Fuel and Rent are over budget this month.", overspendingMessage(cats("Fuel", "Rent")))
        assertEquals("Fuel, Rent, and 2 more are over budget this month.", overspendingMessage(cats("Fuel", "Rent", "A", "B")))
    }

    @Test
    fun alertsOncePerOverspentCategory() =
        runBlocking {
            val database =
                openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val repository = LocalBudgetRepository(database) { 1L }
                val preferences = AppPreferencesStore(database)
                val month =
                    """{"month":"2026-11","categoryGroups":[{"id":"g","categories":[{"id":"fuel","name":"Fuel","balance":-500}]}]}"""
                repository.applyFullSnapshot(
                    budgetId = "budget",
                    generation = "generation",
                    cursor = "1",
                    expectedCheckpoint = null,
                    records = listOf(RemoteBudgetRecord("budget", "budget-month", "2026-11", "r1", month, "generation")),
                )

                assertEquals("Fuel is over budget this month.", overspendingAlert(repository, preferences, "budget", "2026-11"))
                assertNull(overspendingAlert(repository, preferences, "budget", "2026-11"))
                assertNull(overspendingAlert(repository, preferences, "budget", "2026-12"))
            } finally {
                database.close()
            }
        }
}
