package app.tandemforactual.ui.core.watch

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.RemoteBudgetRecord
import app.tandemforactual.data.store.WatchSettings
import app.tandemforactual.data.store.applyFullSnapshot
import app.tandemforactual.data.store.readPendingWrites
import app.tandemforactual.ui.core.automation.AutomationAccess
import app.tandemforactual.ui.core.automation.AutomationErrorCode
import app.tandemforactual.ui.core.automation.AutomationException
import app.tandemforactual.ui.core.automation.AutomationTarget
import app.tandemforactual.ui.core.automation.automatedTransactionSources
import app.tandemforactual.watch.WatchCodec
import app.tandemforactual.watch.WatchQuickAdd
import app.tandemforactual.watch.WatchQuickAddResult
import app.tandemforactual.watch.WatchQuickAddStatus
import app.tandemforactual.watch.WatchState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
class WatchSummaryTest {
    private val open = mapOf("selectedBudgetId" to BUDGET, "selectedBudget" to "Household")

    @Test
    fun aVisibleSummaryCarriesCategoriesRecentSpendingAndBills() =
        withBudget { database ->
            val summary = assertNotNull(summary(database, open))

            assertEquals(WatchState.VISIBLE, summary.state)
            assertEquals("Household", summary.budgetName)
            assertEquals(7525, summary.toBudgetMinor)
            assertEquals(32000, summary.leftToSpendMinor)
            assertEquals(listOf("groceries", "dining"), summary.categories.map { it.id })
            val groceries = summary.categories.first()
            assertEquals(10000, groceries.budgetedMinor)
            assertEquals(12000, groceries.spentMinor)
            assertTrue(groceries.over)
            assertEquals(listOf("2026-11-20", "2026-11-18", "2026-11-10"), groceries.recent.map { it.date })
            assertEquals("Green Market", groceries.recent.first().payee)
            assertEquals(listOf("rent"), summary.quickCategories.map { it.id })
            assertEquals(listOf("Corner Café", "Green Market"), summary.recentPayees)
            assertEquals("Checking", summary.accountName)
            assertEquals(listOf("Internet"), summary.bills.map { it.name })
            assertEquals(-6000L, summary.bills.single().amountMinor)
            assertTrue(WatchCodec.encode(summary).size <= WatchCodec.MAX_SUMMARY_BYTES)
        }

    @Test
    fun theWatchsOwnPinsComeBeforeTheBudgetsPins() =
        withBudget { database ->
            val budgetPins = open + ("pinnedCategoryIds" to "other:groceries\u001f$BUDGET:dining")
            assertEquals(listOf("dining"), summary(database, budgetPins)?.categories?.map { it.id })

            val watchPins = budgetPins + WatchSettings(pinnedCategoryIds = listOf("rent", "groceries")).toPreferences()
            assertEquals(listOf("rent", "groceries"), summary(database, watchPins)?.categories?.map { it.id })
        }

    @Test
    fun hiddenAmountsNeverLeaveThePhone() =
        withBudget { database ->
            val summary = assertNotNull(summary(database, open + ("privacyModeEnabled" to "true")))
            val json = WatchCodec.encode(summary).decodeToString()

            assertEquals(WatchState.AMOUNTS_HIDDEN, summary.state)
            assertNull(summary.leftToSpendMinor)
            assertNull(summary.toBudgetMinor)
            assertTrue(summary.categories.all { it.budgetedMinor == null && it.spentMinor == null && it.leftMinor == null })
            assertTrue(summary.categories.all { it.recent.isEmpty() })
            assertNull(summary.bills.single().amountMinor)
            listOf("32000", "7525", "4500", "6000").forEach { assertFalse(it in json, json) }
        }

    @Test
    fun aLockedEncryptedBudgetSendsOnlyItsName() =
        withBudget { database ->
            val encrypted = open + ("selectedBudgetEncrypted" to "true")
            var opened = false
            val withoutReads =
                assertNotNull(
                    watchSummary(database, encrypted, TODAY, NOW) {
                        opened = true
                        database
                    },
                )
            assertEquals(WatchState.LOCKED, withoutReads.state)
            assertTrue(withoutReads.categories.isEmpty() && withoutReads.bills.isEmpty())
            assertFalse(opened)

            val withReads = encrypted + (AutomationAccess.READS to "true")
            val locked =
                watchSummary(database, withReads, TODAY, NOW) {
                    throw AutomationException(AutomationErrorCode.BUDGET_LOCKED, "locked")
                }
            assertEquals(WatchState.LOCKED, locked?.state)
            assertTrue(locked?.quickAdd == true)
            assertEquals(WatchState.VISIBLE, summary(database, withReads)?.state)
        }

    @Test
    fun noOpenBudgetClearsTheWatch() =
        withBudget { database ->
            assertNull(summary(database, emptyMap()))
            assertNull(summary(database, mapOf("selectedBudgetId" to "")))
        }

    @Test
    fun aWatchEntryIsAddedOnceThroughTheOutbox() =
        withBudget { database ->
            val entry = WatchQuickAdd("entry-1", -1250, "groceries", "Corner Café", NOW)

            val first = add(database, open, entry)
            val again = add(database, open, entry)

            assertEquals(WatchQuickAddStatus.ADDED, first.result.status)
            assertEquals(WatchQuickAddStatus.REPEATED, again.result.status)
            val write = LocalBudgetRepository(database) { NOW }.readPendingWrites(BUDGET).single()
            val patch = Json.parseToJsonElement(write.patchJson).jsonObject
            assertEquals(JsonPrimitive(-1250), patch["amount"])
            assertEquals(JsonPrimitive("groceries"), patch["category"])
            assertEquals(JsonPrimitive("cafe"), patch["payee"])
            assertEquals(JsonPrimitive("checking"), patch["account"])
            assertEquals(JsonPrimitive("2026-11-25"), patch["date"])
            assertEquals(mapOf(write.recordId to WATCH_SOURCE), automatedTransactionSources(database, BUDGET))
        }

    @Test
    fun aWatchEntryIsRefusedOrHeldWithAReason() =
        withBudget { database ->
            val entry = WatchQuickAdd("entry-2", 900, null, null, NOW)
            val off = open + WatchSettings(quickAddEnabled = false).toPreferences()
            assertEquals(WatchQuickAddStatus.REFUSED, add(database, off, entry).result.status)
            assertEquals(WatchQuickAddStatus.REFUSED, add(database, open + ("readOnlyMode" to "true"), entry).result.status)

            val encrypted = open + ("selectedBudgetEncrypted" to "true")
            val lockedOpen: suspend (AutomationTarget) -> TandemDatabase = {
                throw AutomationException(AutomationErrorCode.BUDGET_LOCKED, "locked")
            }
            val held = addFromWatch(database, encrypted, entry, "2026-11-25", NOW, lockedOpen)
            assertEquals(WatchQuickAddResult("entry-2", WatchQuickAddStatus.LOCKED, WATCH_LOCKED_MESSAGE), held.result)

            val inbox =
                addFromWatch(database, encrypted, entry, "2026-11-25", NOW, lockedOpen) { _, locked ->
                    WatchQuickAddOutcome(WatchQuickAddResult(locked.entryId, WatchQuickAddStatus.ADDED))
                }
            assertEquals(WatchQuickAddStatus.ADDED, inbox.result.status)
            assertTrue(LocalBudgetRepository(database) { NOW }.readPendingWrites(BUDGET).isEmpty())
        }

    private suspend fun summary(
        database: TandemDatabase,
        preferences: Map<String, String>,
    ) = watchSummary(database, preferences, TODAY, NOW) { database }

    private suspend fun add(
        database: TandemDatabase,
        preferences: Map<String, String>,
        entry: WatchQuickAdd,
    ) = addFromWatch(database, preferences, entry, "2026-11-25", NOW, openBudget = { database })

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
            record("account", "old", """{"id":"old","name":"Old card","offbudget":false,"closed":true}"""),
            record("category", "groceries", """{"id":"groceries","name":"Groceries"}"""),
            record("category", "dining", """{"id":"dining","name":"Dining"}"""),
            record("category", "rent", """{"id":"rent","name":"Rent"}"""),
            record("payee", "cafe", """{"id":"cafe","name":"Corner Café"}"""),
            record("payee", "market", """{"id":"market","name":"Green Market"}"""),
            transaction("t1", "groceries", "market", -4500, "2026-11-20"),
            transaction("t2", "groceries", "market", -1200, "2026-11-18"),
            transaction("t3", "dining", "cafe", -800, "2026-11-25"),
            transaction("t4", "groceries", "cafe", -300, "2026-11-10"),
            transaction("t5", "rent", "market", -100000, "2026-11-01"),
            record(
                "schedule",
                "internet",
                """{"id":"internet","name":"Internet","next_date":"2026-11-30","amount":-6000,"completed":false}""",
            ),
            record(
                "budget-month",
                "2026-11",
                """{"month":"2026-11","toBudget":7525,"totalBalance":32000,"categoryGroups":[""" +
                    """{"id":"food","name":"Food","categories":[""" +
                    """{"id":"groceries","name":"Groceries","budgeted":10000,"spent":-12000,"balance":-2000},""" +
                    """{"id":"dining","name":"Dining","budgeted":5000,"spent":-800,"balance":4200}]},""" +
                    """{"id":"home","name":"Home","categories":[{"id":"rent","name":"Rent","budgeted":0,"spent":0,"balance":0}]},""" +
                    """{"id":"income","name":"Income","is_income":true,"categories":[{"id":"pay","name":"Pay","budgeted":0}]}]}""",
            ),
        )

    private fun transaction(
        id: String,
        category: String,
        payee: String,
        amount: Long,
        date: String,
    ) = RemoteBudgetRecord(
        budgetId = BUDGET,
        recordType = "transaction",
        recordId = id,
        revision = "revision-1",
        bodyJson =
            """{"id":"$id","account":"checking","category":"$category","payee":"$payee","amount":$amount,"date":"$date"}""",
        generation = "generation-1",
        recordDate = date,
        accountId = "checking",
        categoryId = category,
        payeeId = payee,
        amountMinor = amount,
        budgetMonth = date.take(7),
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
    )

    private companion object {
        const val BUDGET = "budget-1"
        const val TODAY = "2026-11-26"
        const val NOW = 1_795_000_000_000L
    }
}
