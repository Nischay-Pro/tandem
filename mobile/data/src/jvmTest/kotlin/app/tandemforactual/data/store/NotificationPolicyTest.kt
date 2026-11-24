package app.tandemforactual.data.store

import androidx.room3.Room
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class NotificationPolicyTest {
    @Test
    fun quietHoursWrapPastMidnight() {
        val night = QuietHours(22 * 60, 7 * 60)
        assertTrue(night.contains(23 * 60))
        assertTrue(night.contains(60))
        assertFalse(night.contains(12 * 60))
        val lunch = QuietHours(12 * 60, 13 * 60)
        assertTrue(lunch.contains(12 * 60 + 30))
        assertFalse(lunch.contains(13 * 60))
        assertEquals(night, QuietHours.parse(night.encode()))
        assertNull(QuietHours.parse(null))
        assertNull(QuietHours.parse("10"))
        assertNull(QuietHours.parse("10-10"))
        assertNull(QuietHours.parse("10-2000"))
        assertNull(QuietHours.parse("a-b"))
    }

    @Test
    fun readsPreferencesWithDefaults() {
        val defaults = NotificationPolicy.read(emptyMap())
        assertEquals(AlertKind.entries.filter { it.onByDefault }.toSet(), defaults.enabled)
        assertNull(defaults.quietHours)
        assertEquals(NotificationPolicy.DEFAULT_LARGE_TRANSACTION_MINOR, defaults.largeTransactionMinor)
        assertEquals(NotificationPolicy.DEFAULT_BILL_LEAD_DAYS, defaults.billLeadDays)
        assertFalse(defaults.hideAmounts)

        val custom =
            NotificationPolicy.read(
                mapOf(
                    "upcomingBillAlerts" to "true",
                    "syncReminders" to "false",
                    NotificationPolicy.QUIET_HOURS to "1320-420",
                    NotificationPolicy.LARGE_TRANSACTION_MINOR to "0",
                    NotificationPolicy.BILL_LEAD_DAYS to "40",
                    "privacyModeEnabled" to "true",
                ),
            )
        assertTrue(AlertKind.UPCOMING_BILLS in custom.enabled)
        assertFalse(AlertKind.SYNC_FAILURE in custom.enabled)
        assertEquals(NotificationPolicy.DEFAULT_LARGE_TRANSACTION_MINOR, custom.largeTransactionMinor)
        assertEquals(14, custom.billLeadDays)
        assertTrue(custom.hideAmounts)
        assertFalse(custom.allows(AlertKind.UPCOMING_BILLS, 23 * 60))
        assertTrue(custom.allows(AlertKind.UPCOMING_BILLS, 12 * 60))
        assertFalse(custom.allows(AlertKind.SYNC_FAILURE, 12 * 60))
        assertEquals(2500L, NotificationPolicy.read(mapOf(NotificationPolicy.LARGE_TRANSACTION_MINOR to "2500")).largeTransactionMinor)
    }

    private fun withRepository(block: suspend (LocalBudgetRepository, AppPreferencesStore) -> Unit) =
        runBlocking {
            val database =
                openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                block(LocalBudgetRepository(database) { 1L }, AppPreferencesStore(database))
            } finally {
                database.close()
            }
        }

    private suspend fun LocalBudgetRepository.load(records: List<RemoteBudgetRecord>) =
        applyFullSnapshot(budgetId = "budget", generation = "g", cursor = "1", expectedCheckpoint = null, records = records)

    private fun record(
        type: String,
        id: String,
        json: String,
    ): RemoteBudgetRecord {
        val body =
            runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(
                    json,
                ) as kotlinx.serialization.json.JsonObject
            }.getOrNull()

        fun text(name: String) = (body?.get(name) as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
        return RemoteBudgetRecord(
            "budget",
            type,
            id,
            "r-$id",
            json,
            "g",
            recordDate = text("date"),
            payeeId = text("payee"),
            amountMinor = (body?.get("amount") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull(),
            displayMetadata = text("name")?.let { BudgetRecordDisplayMetadata(it, null) },
        )
    }

    @Test
    fun announcesEachUpcomingBillOnce() =
        withRepository { repository, preferences ->
            repository.load(
                listOf(
                    record("schedule", "rent", """{"name":"Rent","next_date":"2026-11-22","amount":-150000}"""),
                    record("schedule", "gym", """{"name":"","next_date":"2026-11-23","amount":{"num1":1,"num2":2}}"""),
                    record("schedule", "late", """{"name":"Later","next_date":"2026-12-30","amount":-1}"""),
                    record("schedule", "past", """{"name":"Past","next_date":"2026-11-01"}"""),
                    record("schedule", "done", """{"name":"Done","next_date":"2026-11-22","completed":true}"""),
                    record("schedule", "nodate", """{"name":"No date"}"""),
                ),
            )
            val policy = NotificationPolicy.read(emptyMap())
            val format = { minor: Long -> "$$minor" }
            val first = upcomingBillsAlert(repository, preferences, "budget", "2026-11-21", policy, format)
            assertEquals("Rent and 1 more are due in the next 3 days.", first?.message)
            assertEquals(AlertKind.UPCOMING_BILLS, first?.kind)
            assertNull(upcomingBillsAlert(repository, preferences, "budget", "2026-11-21", policy, format))

            preferences.putValue("internal:announced-bills:budget", "gym@2026-11-23")
            assertEquals(
                "Rent is due soon for $150000.",
                upcomingBillsAlert(repository, preferences, "budget", "2026-11-21", policy, format)?.message,
            )
            preferences.putValue("internal:announced-bills:budget", "rent@2026-11-22")
            assertEquals(
                "A scheduled payment is due soon.",
                upcomingBillsAlert(repository, preferences, "budget", "2026-11-21", policy, format)?.message,
            )
            preferences.putValue("internal:announced-bills:budget", "gym@2026-11-23")
            assertEquals(
                "Rent is due soon.",
                upcomingBillsAlert(repository, preferences, "budget", "2026-11-21", policy.copy(hideAmounts = true), format)?.message,
            )
        }

    @Test
    fun announcesLargeSpendingOnce() =
        withRepository { repository, preferences ->
            repository.load(
                listOf(
                    record("payee", "shop", """{"name":"Costco"}"""),
                    record("transaction", "big", """{"date":"2026-11-20","amount":-80000,"payee":"shop"}"""),
                    record("transaction", "small", """{"date":"2026-11-20","amount":-100,"payee":"shop"}"""),
                    record("transaction", "old", """{"date":"2026-10-01","amount":-90000}"""),
                    record("transaction", "income", """{"date":"2026-11-20","amount":90000}"""),
                ),
            )
            val policy = NotificationPolicy.read(emptyMap())
            val format = { minor: Long -> "$$minor" }
            assertEquals(
                "$80000 at Costco came in.",
                largeTransactionAlert(repository, preferences, "budget", "2026-11-21", policy, format)?.message,
            )
            assertNull(largeTransactionAlert(repository, preferences, "budget", "2026-11-21", policy, format))

            preferences.putValue("internal:announced-large:budget", "")
            assertEquals(
                "A large transaction at Costco came in.",
                largeTransactionAlert(repository, preferences, "budget", "2026-11-21", policy.copy(hideAmounts = true), format)?.message,
            )
            preferences.putValue("internal:announced-large:budget", "")
            assertEquals(
                "2 large transactions came in. Open Tandem to check them.",
                largeTransactionAlert(repository, preferences, "budget", "2026-11-21", policy.copy(largeTransactionMinor = 50), format)
                    ?.message,
            )
        }

    @Test
    fun largeSpendingWithoutPayee() =
        withRepository { repository, preferences ->
            repository.load(listOf(record("transaction", "big", """{"date":"2026-11-20","amount":-80000}""")))
            val policy = NotificationPolicy.read(emptyMap())
            assertEquals(
                "$80000 came in.",
                largeTransactionAlert(repository, preferences, "budget", "2026-11-21", policy) { "$$it" }?.message,
            )
            preferences.putValue("internal:announced-large:budget", "")
            assertEquals(
                "A large transaction came in.",
                largeTransactionAlert(repository, preferences, "budget", "2026-11-21", policy.copy(hideAmounts = true)) { "$$it" }
                    ?.message,
            )
        }

    @Test
    fun announcesBankFailuresUntilTheyRecover() =
        withRepository { _, preferences ->
            assertEquals(
                "Checking couldn’t sync with its bank. Open Bank connections to fix it.",
                bankSyncFailureAlert(preferences, "budget", listOf("Checking"))?.message,
            )
            assertNull(bankSyncFailureAlert(preferences, "budget", listOf("Checking")))
            assertEquals(
                "2 accounts couldn’t sync with their banks. Open Bank connections to fix them.",
                bankSyncFailureAlert(preferences, "budget", listOf("Checking", "Savings", "Card"))?.message,
            )
            assertNull(bankSyncFailureAlert(preferences, "budget", emptyList()))
            assertEquals(AlertKind.BANK_SYNC_FAILURE, bankSyncFailureAlert(preferences, "budget", listOf("Checking"))?.kind)
        }
}
