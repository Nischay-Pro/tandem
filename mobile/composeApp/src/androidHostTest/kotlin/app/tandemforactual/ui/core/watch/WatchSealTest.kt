package app.tandemforactual.ui.core.watch

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.inbox.InboxFile
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.RemoteBudgetRecord
import app.tandemforactual.data.store.applyFullSnapshot
import app.tandemforactual.data.store.readPendingWrites
import app.tandemforactual.ui.core.automation.AutomationTarget
import app.tandemforactual.ui.core.automation.automatedTransactionSources
import app.tandemforactual.ui.core.automation.openSealedInbox
import app.tandemforactual.watch.WatchQuickAdd
import app.tandemforactual.watch.WatchQuickAddStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
class WatchSealTest {
    private val inbox = MemoryInboxFile()
    private val target = AutomationTarget(BUDGET, encrypted = true)
    private val entry = WatchQuickAdd("entry-0001", -1240, createdAtEpochMs = NOW)
    private val lastAccount = mapOf("lastTransactionAccountId" to "checking")

    @Test
    fun aBudgetNeverUnlockedOnThisPhoneLeavesTheEntryOnTheWatch() =
        withBudgets { _, main ->
            assertNull(sealFromWatch(main, inbox, lastAccount, target, entry, TODAY, NOW))
            assertTrue(inbox.lines.isEmpty())
        }

    @Test
    fun withoutAnAccountUsedBeforeTheEntryStaysOnTheWatch() =
        withBudgets { vault, main ->
            openSealedInbox(vault, main, inbox, BUDGET, PASSWORD, TODAY)
            assertNull(sealFromWatch(main, inbox, emptyMap(), target, entry, TODAY, NOW))
            assertTrue(inbox.lines.isEmpty())
        }

    @Test
    fun aSealedEntryIsAddedOnceAtTheNextUnlockEvenWhenTheWatchSentItTwice() =
        withBudgets { vault, main ->
            openSealedInbox(vault, main, inbox, BUDGET, PASSWORD, TODAY)
            val outcome = assertNotNull(sealFromWatch(main, inbox, lastAccount, target, entry, TODAY, NOW))
            assertEquals(WatchQuickAddStatus.ADDED, outcome.result.status)
            assertEquals(WATCH_SEALED_MESSAGE, outcome.result.message)
            assertTrue(assertNotNull(outcome.added).sealed)
            sealFromWatch(main, inbox, lastAccount, target, entry, TODAY, NOW)

            assertEquals(1, openSealedInbox(vault, main, inbox, BUDGET, PASSWORD, TODAY).added)
            val write = LocalBudgetRepository(vault) { NOW }.readPendingWrites(BUDGET).single()
            val patch = Json.parseToJsonElement(write.patchJson).jsonObject
            assertEquals(JsonPrimitive(-1240), patch["amount"])
            assertEquals(JsonPrimitive("checking"), patch["account"])
            assertEquals(WATCH_SOURCE, automatedTransactionSources(vault, BUDGET)[write.recordId])
        }

    private fun withBudgets(block: suspend (vault: TandemDatabase, main: TandemDatabase) -> Unit) =
        runBlocking {
            val vault = database()
            val main = database()
            try {
                LocalBudgetRepository(vault) { NOW }.applyFullSnapshot(BUDGET, "generation-1", "cursor-1", null, records())
                block(vault, main)
            } finally {
                vault.close()
                main.close()
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
        )

    private fun record(
        type: String,
        id: String,
        body: String,
    ) = RemoteBudgetRecord(
        budgetId = BUDGET,
        recordType = type,
        recordId = id,
        revision = "revision-1",
        bodyJson = body,
        generation = "generation-1",
        recordDate = null,
        accountId = null,
        amountMinor = null,
        budgetMonth = null,
    )

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
        const val TODAY = "2026-11-26"
        const val NOW = 1_795_000_000_000L
        const val PASSWORD = "correct horse"
    }
}
