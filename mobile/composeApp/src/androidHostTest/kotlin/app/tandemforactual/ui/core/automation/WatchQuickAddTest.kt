package app.tandemforactual.ui.core.automation

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.RemoteBudgetRecord
import app.tandemforactual.data.store.WatchSettings
import app.tandemforactual.data.store.applyFullSnapshot
import app.tandemforactual.data.store.readPendingWrites
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class WatchQuickAddTest {
    private val open = mapOf("selectedBudgetId" to BUDGET, "lastTransactionAccountId" to "checking")

    @Test
    fun addsToTheLastUsedAccountAndNamesTheWatch() =
        withBudget { database ->
            val reply = addWatchQuickAdd(database, open, WatchQuickAddEntry(ENTRY, "-4.50", "groceries", "Corner Café"), TODAY, NOW)

            assertTrue(reply.added)
            assertEquals(ENTRY, reply.entryId)
            val write = LocalBudgetRepository(database) { NOW }.readPendingWrites(BUDGET).single()
            val patch = Json.parseToJsonElement(write.patchJson).jsonObject
            assertEquals(JsonPrimitive("checking"), patch["account"])
            assertEquals(JsonPrimitive(-450), patch["amount"])
            assertEquals(JsonPrimitive("groceries"), patch["category"])
            assertEquals(WATCH_SOURCE, automatedTransactionSources(database, BUDGET)[write.recordId])
        }

    @Test
    fun aResentEntryAddsNothingNew() =
        withBudget { database ->
            val entry = WatchQuickAddEntry(ENTRY, "-4.50")
            assertTrue(addWatchQuickAdd(database, open, entry, TODAY, NOW).added)
            val again = addWatchQuickAdd(database, open, entry, TODAY, NOW + 1000)

            assertTrue(again.added)
            assertEquals("Already added.", again.message)
            assertEquals(1, LocalBudgetRepository(database) { NOW }.readPendingWrites(BUDGET).size)
        }

    @Test
    fun refusesWithTheReasonInsteadOfAdding() =
        withBudget { database ->
            val off = open + WatchSettings(quickAddEnabled = false).toPreferences()
            val encrypted = open + ("selectedBudgetEncrypted" to "true")
            val readOnly = open + ("readOnlyMode" to "true")

            assertFalse(addWatchQuickAdd(database, off, WatchQuickAddEntry(ENTRY, "-1"), TODAY, NOW).added)
            val locked = addWatchQuickAdd(database, encrypted, WatchQuickAddEntry(ENTRY, "-1"), TODAY, NOW)
            assertFalse(locked.added)
            assertTrue(locked.message.startsWith("Open Tandem to unlock"))
            assertFalse(addWatchQuickAdd(database, readOnly, WatchQuickAddEntry(ENTRY, "-1"), TODAY, NOW).added)
            assertFalse(addWatchQuickAdd(database, open, WatchQuickAddEntry("../x", "-1"), TODAY, NOW).added)
            assertFalse(addWatchQuickAdd(database, open, WatchQuickAddEntry(ENTRY, "4.50"), TODAY, NOW).added)
            assertTrue(LocalBudgetRepository(database) { NOW }.readPendingWrites(BUDGET).isEmpty())
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
            record("account", "checking", """{"id":"checking","name":"Checking","offbudget":false,"closed":false}"""),
            record("category", "groceries", """{"id":"groceries","name":"Groceries","hidden":false}"""),
            record("payee", "cafe", """{"id":"cafe","name":"Corner Café"}"""),
        )

    private fun record(
        type: String,
        id: String,
        body: String,
    ) = RemoteBudgetRecord(BUDGET, type, id, "revision-1", body, "generation-1")

    private companion object {
        const val BUDGET = "budget-1"
        const val ENTRY = "6F1C2D3E-4B5A-4C6D-8E7F-90A1B2C3D4E5"
        const val TODAY = "2026-11-26"
        const val NOW = 1_795_000_000_000L
    }
}
