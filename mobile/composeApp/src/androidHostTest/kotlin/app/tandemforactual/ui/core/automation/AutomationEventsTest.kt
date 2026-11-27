package app.tandemforactual.ui.core.automation

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.RemoteBudgetRecord
import app.tandemforactual.data.store.applyFullSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class AutomationEventsTest {
    @Test
    fun eventsFireOnlyWithReadsOnAndAmountsVisible() {
        assertFalse(budgetEventsAllowed(mapOf("selectedBudgetId" to BUDGET)))
        assertTrue(budgetEventsAllowed(OPEN))
        assertFalse(budgetEventsAllowed(OPEN + ("biometricLockEnabled" to "true")))
        assertFalse(budgetEventsAllowed(OPEN + ("privacyModeEnabled" to "true")))
        assertFalse(syncEventsAllowed(mapOf("selectedBudgetId" to BUDGET)))
        assertTrue(syncEventsAllowed(OPEN + ("biometricLockEnabled" to "true") + ("privacyModeEnabled" to "true")))
    }

    @Test
    fun closedReadsSendNothingAndKeepTheOccurrenceForLater() =
        withBudget { database ->
            val sent = mutableListOf<AutomationEvent>()
            announceAfterSync(database, database, BUDGET, mapOf("selectedBudgetId" to BUDGET), TODAY, NOW, sent::add)
            announceAfterSync(database, database, BUDGET, OPEN + ("privacyModeEnabled" to "true"), TODAY, NOW, sent::add)
            announceAfterSync(database, database, BUDGET, OPEN + ("biometricLockEnabled" to "true"), TODAY, NOW, sent::add)
            assertEquals(emptyList(), sent)
            announceAfterSync(database, database, BUDGET, OPEN, TODAY, NOW, sent::add)
            assertEquals(
                listOf(AutomationEventKind.CATEGORY_OVERSPENT, AutomationEventKind.CATEGORY_OVERSPENT, AutomationEventKind.SCHEDULE_DUE),
                sent.map { it.kind },
            )
        }

    @Test
    fun eachOccurrenceFiresOnceWithItsValues() =
        withBudget { database ->
            val sent = mutableListOf<AutomationEvent>()
            announceAfterSync(database, database, BUDGET, OPEN, TODAY, NOW, sent::add)
            val (groceries, fuel, due) = sent
            assertEquals(mapOf("category" to "Groceries", "category_id" to "groceries", "overspent_by" to "20.00"), groceries.values)
            assertEquals(mapOf("category" to "Fuel", "category_id" to "fuel", "overspent_by" to "5.00"), fuel.values)
            assertEquals(mapOf("schedule" to "Rent", "amount" to "-1500.00", "due_date" to TODAY), due.values)
            announceAfterSync(database, database, BUDGET, OPEN, TODAY, NOW + 1, sent::add)
            assertEquals(3, sent.size)
            assertTrue(AppPreferencesStore(database).read().keys.none { it.startsWith("internal:overspent-categories") })
            announceAfterSync(database, database, BUDGET, OPEN, "2026-11-28", NOW + 2, sent::add)
            assertEquals(listOf("Phone"), sent.drop(3).map { it.values["schedule"] })
            assertEquals("", sent.last().values["amount"])
        }

    @Test
    fun storedEventsAreFoundByIdUntilTheyExpire() =
        withBudget { database ->
            val sent = mutableListOf<AutomationEvent>()
            announceAfterSync(database, database, BUDGET, OPEN, TODAY, NOW, sent::add)
            val store = AutomationEventStore(database)
            assertEquals(sent.first(), store.find(sent.first().id, NOW + 1))
            assertNull(store.find("unknown", NOW))
            assertNull(store.find(sent.first().id, NOW + HOUR))
            store.add(emptyList(), NOW)
            AppPreferencesStore(database).putValue("internal:automation-events-v1", """[1,{"id":"x"},{"id":"y","kind":"NOPE","at":1}]""")
            assertNull(store.find("x", NOW))
            store.add(List(60) { AutomationEvent("e$it", AutomationEventKind.SYNC_FAILED, emptyMap(), NOW) }, NOW)
            assertNull(store.find("e0", NOW))
            assertEquals("e59", store.find("e59", NOW)?.id)
        }

    @Test
    fun aSyncFailureFiresOnceUntilASyncWorks() =
        withBudget { database ->
            val sent = mutableListOf<AutomationEvent>()
            announceSyncFailure(database, mapOf("selectedBudgetId" to BUDGET), "Down", NOW, sent::add)
            assertEquals(emptyList(), sent)
            announceSyncFailure(database, OPEN + ("biometricLockEnabled" to "true"), "Down", NOW, sent::add)
            announceSyncFailure(database, OPEN, "Down again", NOW, sent::add)
            assertEquals(listOf(mapOf("error" to "Down")), sent.map { it.values })
            assertEquals(sent.single(), AutomationEventStore(database).find(sent.single().id, NOW))
            announceAfterSync(database, database, BUDGET, mapOf("selectedBudgetId" to BUDGET), TODAY, NOW, sent::add)
            announceSyncFailure(database, OPEN, "Down later", NOW, sent::add)
            assertEquals("Down later", sent.last().values["error"])
        }

    @Test
    fun eventsMatchTheCategoryAskedFor() {
        val values = mapOf("category" to "Groceries", "category_id" to "g")
        val event = AutomationEvent("id", AutomationEventKind.CATEGORY_OVERSPENT, values, NOW)
        assertTrue(event.matchesCategory(null))
        assertTrue(event.matchesCategory("%category"))
        assertTrue(event.matchesCategory("g"))
        assertTrue(event.matchesCategory(" groceries "))
        assertFalse(event.matchesCategory("Rent"))
    }

    @Test
    fun overBudgetStateReadsThisMonthFromTheDevice() =
        withBudget { database ->
            val any = categoriesOverBudget(database, BUDGET, " ", TODAY)
            assertEquals(listOf("Groceries", "Fuel"), any.map { it.name })
            assertEquals(
                mapOf("category" to "Groceries, Fuel", "overspent_by" to "25.00"),
                overBudgetValues(database, BUDGET, any),
            )
            assertEquals(listOf("fuel"), categoriesOverBudget(database, BUDGET, "FUEL", TODAY).map { it.id })
            assertEquals(listOf("fuel"), categoriesOverBudget(database, BUDGET, "fuel", TODAY).map { it.id })
            assertEquals(emptyList(), categoriesOverBudget(database, BUDGET, "Rent", TODAY))
            assertEquals(emptyList(), categoriesOverBudget(database, BUDGET, null, "2026-12-01"))
        }

    private fun withBudget(block: suspend (TandemDatabase) -> Unit) =
        runBlocking {
            val database =
                Room
                    .inMemoryDatabaseBuilder<TandemDatabase>()
                    .setDriver(AndroidSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            try {
                LocalBudgetRepository(database) { NOW }.applyFullSnapshot(BUDGET, "generation-1", "cursor-1", null, records())
                block(database)
            } finally {
                database.close()
            }
        }

    private fun records() =
        listOf(
            record("schedule", "rent", """{"name":"Rent","next_date":"$TODAY","amount":-150000}"""),
            record("schedule", "phone", """{"name":"Phone","next_date":"2026-11-28","amount":{"num1":1,"num2":2}}"""),
            record("schedule", "later", """{"name":"Later","next_date":"2026-12-30","amount":-1}"""),
            record(
                "budget-month",
                "2026-11",
                """{"month":"2026-11","categoryGroups":[{"id":"food","name":"Food","categories":[""" +
                    """{"id":"groceries","name":"Groceries","balance":-2000},""" +
                    """{"id":"fuel","name":"Fuel","balance":-500},{"id":"rent","name":"Rent","balance":100}]}]}""",
            ),
        )

    private fun record(
        type: String,
        id: String,
        body: String,
    ) = RemoteBudgetRecord(BUDGET, type, id, "revision-1", body, "generation-1")

    private companion object {
        const val BUDGET = "budget-1"
        const val TODAY = "2026-11-26"
        const val NOW = 1_795_000_000_000L
        const val HOUR = 60L * 60 * 1000
        val OPEN = mapOf("selectedBudgetId" to BUDGET, AutomationAccess.READS to "true")
    }
}
