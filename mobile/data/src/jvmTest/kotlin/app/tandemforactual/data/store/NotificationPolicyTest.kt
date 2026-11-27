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
        assertEquals(emptySet(), defaults.enabled)
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

    @Test
    fun encryptedBudgetsGetGenericTextUnlessDetailsAreAllowed() {
        val private = NotificationPolicy.read(emptyMap())
        val detailed = NotificationPolicy.read(mapOf(NotificationPolicy.ENCRYPTED_DETAILS to "true"))
        assertFalse(private.encryptedDetails)
        assertTrue(detailed.encryptedDetails)
        val expected =
            mapOf(
                AlertKind.OVERSPENDING to "A category is over budget this month.",
                AlertKind.UPCOMING_BILLS to "A bill is due soon.",
                AlertKind.LARGE_TRANSACTION to "New transaction to review.",
                AlertKind.UNCATEGORIZED to "New transaction to review.",
                AlertKind.BANK_SYNC_FAILURE to "A bank connection needs attention.",
                AlertKind.NEEDS_REVIEW to "Open Tandem to see what changed.",
            )
        expected.forEach { (kind, text) ->
            val alert = LocalAlert(kind, "Title", "€12.40 at Corner Café came in.")
            assertEquals(LocalAlert(kind, "Title", text), private.forBudget(alert, encrypted = true))
            assertEquals(alert, private.forBudget(alert, encrypted = false))
            assertEquals(alert, detailed.forBudget(alert, encrypted = true))
        }
        assertEquals("A category is over budget this month.", private.overspendingForBudget("Groceries is over budget.", encrypted = true))
        assertEquals("Groceries is over budget.", private.overspendingForBudget("Groceries is over budget.", encrypted = false))
        assertEquals("Groceries is over budget.", detailed.overspendingForBudget("Groceries is over budget.", encrypted = true))
        assertNull(private.overspendingForBudget(null, encrypted = true))
    }

    private val allAlertsOn = AlertKind.entries.associate { it.preference to "true" }

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
            accountId = text("account"),
            categoryId = text("category"),
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
            val policy = NotificationPolicy.read(allAlertsOn)
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
            val policy = NotificationPolicy.read(allAlertsOn)
            val format = { minor: Long -> "$$minor" }
            assertNull(largeTransactionAlert(repository, preferences, "budget", "2026-11-21", policy, format))
            assertEquals("big", preferences.read()["internal:announced-large:budget"])

            preferences.putValue("internal:announced-large:budget", "")
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
    fun announcesNewUncategorizedTransactionsOnceAfterAFirstLook() =
        withRepository { repository, preferences ->
            val policy = NotificationPolicy.read(allAlertsOn)
            val format = { minor: Long -> "$$minor" }

            suspend fun alert(hide: Boolean = false) =
                uncategorizedAlert(repository, preferences, "budget", "2026-11-21", policy.copy(hideAmounts = hide), NOON, format)
            val key = "internal:announced-uncategorized:budget"
            repository.load(
                listOf(
                    record("account", "checking", """{"name":"Checking"}"""),
                    record("account", "loan", """{"name":"Loan","offbudget":true}"""),
                    record("payee", "shop", """{"name":"Costco"}"""),
                    record("transaction", "fresh", """{"date":"2026-11-20","amount":-1200,"account":"checking","payee":"shop"}"""),
                    record("transaction", "nopayee", """{"date":"2026-11-21","amount":-7,"account":"checking"}"""),
                    record("transaction", "sorted", """{"date":"2026-11-20","amount":-1,"account":"checking","category":"food"}"""),
                    record("transaction", "offbudget", """{"date":"2026-11-20","amount":-1,"account":"loan"}"""),
                    record("transaction", "moved", """{"date":"2026-11-20","amount":-1,"account":"checking","transferId":"x"}"""),
                    record("transaction", "split", """{"date":"2026-11-20","amount":-1,"account":"checking","isParent":true}"""),
                    record("transaction", "old", """{"date":"2026-10-01","amount":-1,"account":"checking"}"""),
                ),
            )
            assertNull(alert(), "the first look only notes what is already waiting")
            assertEquals("nopayee,fresh", preferences.read()[key])
            assertNull(alert(), "announced once")

            preferences.putValue(key, "nopayee")
            val single = alert()!!
            assertEquals(AlertKind.UNCATEGORIZED, single.kind)
            assertEquals("New transaction to categorize", single.title)
            assertEquals("$1200 at Costco needs a category.", single.message)
            preferences.putValue(key, "nopayee")
            assertEquals("A transaction at Costco needs a category.", alert(hide = true)?.message)
            preferences.putValue(key, "fresh")
            assertEquals("$7 needs a category.", alert()?.message)
            preferences.putValue(key, "")
            val both = alert()!!
            assertEquals("New transactions to categorize", both.title)
            assertEquals("2 new transactions need a category.", both.message)
        }

    @Test
    fun largeSpendingWithoutPayee() =
        withRepository { repository, preferences ->
            repository.load(listOf(record("transaction", "big", """{"date":"2026-11-20","amount":-80000}""")))
            val policy = NotificationPolicy.read(allAlertsOn)
            preferences.putValue("internal:announced-large:budget", "")
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
    fun largeSpendingCountsSplitsByTotalAndSkipsTransfers() =
        withRepository { repository, preferences ->
            repository.load(
                listOf(
                    record("payee", "shop", """{"name":"Costco"}"""),
                    record("transaction", "move", """{"date":"2026-11-20","amount":-90000,"transferId":"other"}"""),
                    record("transaction", "costco", """{"date":"2026-11-20","amount":-60000,"payee":"shop","isParent":true}"""),
                    record(
                        "transaction",
                        "line-1",
                        """{"date":"2026-11-20","amount":-55000,"payee":"shop","isChild":true,"parentId":"costco"}""",
                    ),
                    record(
                        "transaction",
                        "line-2",
                        """{"date":"2026-11-20","amount":-5000,"payee":"shop","isChild":true,"parentId":"costco"}""",
                    ),
                ),
            )
            val policy = NotificationPolicy.read(allAlertsOn)
            preferences.putValue("internal:announced-large:budget", "")
            assertEquals(
                "$60000 at Costco came in.",
                largeTransactionAlert(repository, preferences, "budget", "2026-11-21", policy) { "$$it" }?.message,
            )
            assertEquals("costco", preferences.read()["internal:announced-large:budget"])
        }

    @Test
    fun announcesBankFailuresUntilTheyRecover() =
        withRepository { _, preferences ->
            val policy = NotificationPolicy.read(allAlertsOn)
            assertEquals(
                "Checking couldn’t sync with its bank. Open Bank connections to fix it.",
                bankSyncFailureAlert(preferences, "budget", listOf("Checking"), policy, NOON)?.message,
            )
            assertNull(bankSyncFailureAlert(preferences, "budget", listOf("Checking"), policy, NOON))
            assertEquals(
                "2 accounts couldn’t sync with their banks. Open Bank connections to fix them.",
                bankSyncFailureAlert(preferences, "budget", listOf("Checking", "Savings", "Card"), policy, NOON)?.message,
            )
            assertNull(bankSyncFailureAlert(preferences, "budget", emptyList(), policy, NOON))
            assertEquals(AlertKind.BANK_SYNC_FAILURE, bankSyncFailureAlert(preferences, "budget", listOf("Checking"), policy, NOON)?.kind)
        }

    @Test
    fun holdsNewUncategorizedTransactionsUntilQuietHoursEnd() =
        withRepository { repository, preferences ->
            val night = NotificationPolicy.read(allAlertsOn + mapOf(NotificationPolicy.QUIET_HOURS to "1320-420"))
            val key = "internal:announced-uncategorized:budget"

            suspend fun alert(
                minute: Int,
                policy: NotificationPolicy = night,
            ) = uncategorizedAlert(repository, preferences, "budget", "2026-11-21", policy, minute) { "$$it" }
            repository.load(
                listOf(
                    record("transaction", "seen", """{"date":"2026-11-20","amount":-5}"""),
                    record("transaction", "fresh", """{"date":"2026-11-21","amount":-1200}"""),
                ),
            )
            preferences.putValue(key, "seen,gone")
            assertNull(alert(23 * 60), "silent during quiet hours")
            assertEquals("seen", preferences.read()[key])
            assertNull(alert(60))
            assertEquals("$1200 needs a category.", alert(8 * 60)?.message, "shown once quiet hours end")
            assertNull(alert(8 * 60), "not repeated")
            assertNull(alert(9 * 60))

            preferences.putValue(key, "seen")
            assertNull(alert(NOON, night.copy(enabled = emptySet())), "turned off drops it rather than holding it")
            assertEquals("fresh,seen", preferences.read()[key])
            assertNull(alert(NOON))
        }

    @Test
    fun holdsNewBankFailuresUntilQuietHoursEnd() =
        withRepository { _, preferences ->
            val night = NotificationPolicy.read(allAlertsOn + mapOf(NotificationPolicy.QUIET_HOURS to "1320-420"))
            val key = "internal:announced-bank-failures:budget"
            assertNull(bankSyncFailureAlert(preferences, "budget", listOf("Checking"), night, 23 * 60))
            assertEquals("", preferences.read()[key])
            assertEquals(
                "Checking couldn’t sync with its bank. Open Bank connections to fix it.",
                bankSyncFailureAlert(preferences, "budget", listOf("Checking"), night, 8 * 60)?.message,
            )
            assertNull(bankSyncFailureAlert(preferences, "budget", listOf("Checking"), night, 9 * 60), "not repeated")

            assertNull(bankSyncFailureAlert(preferences, "budget", emptyList(), night, 23 * 60), "recovery is still noted")
            assertNull(bankSyncFailureAlert(preferences, "budget", listOf("Checking"), night, 23 * 60))
            assertEquals(AlertKind.BANK_SYNC_FAILURE, bankSyncFailureAlert(preferences, "budget", listOf("Checking"), night, 8 * 60)?.kind)

            val off = night.copy(enabled = emptySet())
            assertNull(bankSyncFailureAlert(preferences, "budget", listOf("Savings"), off, NOON))
            assertEquals("Savings", preferences.read()[key])
            assertNull(bankSyncFailureAlert(preferences, "budget", listOf("Savings"), night, NOON))
        }
}

private const val NOON = 12 * 60
