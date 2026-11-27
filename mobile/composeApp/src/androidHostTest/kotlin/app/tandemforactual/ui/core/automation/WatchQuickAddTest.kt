package app.tandemforactual.ui.core.automation

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.inbox.InboxFile
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
    private val inbox = MemoryInboxFile()
    private val open = mapOf("selectedBudgetId" to BUDGET, "lastTransactionAccountId" to "checking")

    @Test
    fun addsToTheLastUsedAccountAndNamesTheWatch() =
        withBudget { database ->
            val reply = addWatchQuickAdd(database, open, WatchQuickAddEntry(ENTRY, "-4.50", "groceries", "Corner Café"), TODAY, NOW, inbox)

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
            assertTrue(addWatchQuickAdd(database, open, entry, TODAY, NOW, inbox).added)
            val again = addWatchQuickAdd(database, open, entry, TODAY, NOW + 1000, inbox)

            assertTrue(again.added)
            assertEquals("Already added.", again.message)
            assertEquals(1, LocalBudgetRepository(database) { NOW }.readPendingWrites(BUDGET).size)
        }

    @Test
    fun refusesWithTheReasonInsteadOfAdding() =
        withBudget { database ->
            val off = open + WatchSettings(quickAddEnabled = false).toPreferences()
            val readOnly = open + ("readOnlyMode" to "true")

            assertFalse(addWatchQuickAdd(database, off, WatchQuickAddEntry(ENTRY, "-1"), TODAY, NOW, inbox).added)
            assertFalse(addWatchQuickAdd(database, readOnly, WatchQuickAddEntry(ENTRY, "-1"), TODAY, NOW, inbox).added)
            assertFalse(addWatchQuickAdd(database, open, WatchQuickAddEntry("../x", "-1"), TODAY, NOW, inbox).added)
            assertFalse(addWatchQuickAdd(database, open, WatchQuickAddEntry(ENTRY, "4.50"), TODAY, NOW, inbox).added)
            assertTrue(LocalBudgetRepository(database) { NOW }.readPendingWrites(BUDGET).isEmpty())
        }

    @Test
    fun anEncryptedBudgetSealsTheEntryOnceItHasBeenUnlockedOnThisDevice() =
        withBudget { vault ->
            val main = database()
            try {
                val encrypted = open + ("selectedBudgetEncrypted" to "true")
                val early = addWatchQuickAdd(main, encrypted, WatchQuickAddEntry(ENTRY, "-4.50"), TODAY, NOW, inbox)
                assertFalse(early.added)
                assertTrue(early.message.contains("unlock it once"))
                assertTrue(inbox.lines.isEmpty())

                openSealedInbox(vault, main, inbox, BUDGET, PASSWORD, TODAY)
                val sealed = addWatchQuickAdd(main, encrypted, WatchQuickAddEntry(ENTRY, "-4.50"), TODAY, NOW, inbox)
                assertTrue(sealed.added)
                assertEquals(AutomatedTransaction.SEALED_SUMMARY, sealed.message)
                assertTrue(LocalBudgetRepository(main) { NOW }.readPendingWrites(BUDGET).isEmpty())

                assertEquals(1, openSealedInbox(vault, main, inbox, BUDGET, PASSWORD, TODAY).added)
                val write = LocalBudgetRepository(vault) { NOW }.readPendingWrites(BUDGET).single()
                assertEquals(JsonPrimitive("checking"), Json.parseToJsonElement(write.patchJson).jsonObject["account"])
                assertEquals(WATCH_SOURCE, automatedTransactionSources(vault, BUDGET)[write.recordId])
            } finally {
                main.close()
            }
        }

    private fun withBudget(block: suspend (TandemDatabase) -> Unit) =
        runBlocking {
            val database = database()
            try {
                LocalBudgetRepository(database) { NOW }.applyFullSnapshot(BUDGET, "generation-1", "cursor-1", null, records())
                block(database)
            } finally {
                database.close()
            }
        }

    private fun database(): TandemDatabase =
        Room
            .inMemoryDatabaseBuilder<TandemDatabase>()
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

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

    private class MemoryInboxFile : InboxFile {
        val lines = mutableListOf<String>()

        override suspend fun append(line: String) {
            lines += line
        }

        override suspend fun lines(): List<String> = lines.toList()

        override suspend fun clear() {
            lines.clear()
        }
    }

    private companion object {
        const val BUDGET = "budget-1"
        const val PASSWORD = "correct horse"
        const val ENTRY = "6F1C2D3E-4B5A-4C6D-8E7F-90A1B2C3D4E5"
        const val TODAY = "2026-11-26"
        const val NOW = 1_795_000_000_000L
    }
}
