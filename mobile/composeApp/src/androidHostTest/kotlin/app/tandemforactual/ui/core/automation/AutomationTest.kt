package app.tandemforactual.ui.core.automation

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.RemoteBudgetRecord
import app.tandemforactual.data.store.applyFullSnapshot
import app.tandemforactual.data.store.findRecord
import app.tandemforactual.data.store.readPendingWrites
import app.tandemforactual.ui.core.session.BackgroundBudgetKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class AutomationTest {
    @Test
    fun optInsGateEachKindOfCallSeparately() {
        val open = mapOf("selectedBudgetId" to BUDGET)
        assertEquals(AutomationErrorCode.WRITES_OFF, code { automationTarget(open, AutomationKind.WRITE) })
        assertEquals(AutomationErrorCode.READS_OFF, code { automationTarget(open, AutomationKind.READ) })
        val writes = open + (AutomationAccess.WRITES to "true")
        assertEquals(BUDGET, automationTarget(writes, AutomationKind.WRITE).budgetId)
        assertEquals(AutomationErrorCode.READS_OFF, code { automationTarget(writes, AutomationKind.READ) })
        val both = writes + (AutomationAccess.READS to "true")
        assertFalse(automationTarget(both, AutomationKind.READ).encrypted)
        assertEquals(AutomationErrorCode.NO_BUDGET, code { automationTarget(both - "selectedBudgetId", AutomationKind.READ) })
        val locked = both + ("readOnlyMode" to "true")
        assertEquals(AutomationErrorCode.READ_ONLY, code { automationTarget(locked, AutomationKind.WRITE) })
        assertEquals(BUDGET, automationTarget(locked, AutomationKind.READ).budgetId)
    }

    @Test
    fun readsStayClosedWhileAppLockOrHiddenAmountsAreOn() {
        val open = mapOf("selectedBudgetId" to BUDGET, AutomationAccess.READS to "true", AutomationAccess.WRITES to "true")
        val appLock = open + ("biometricLockEnabled" to "true")
        val appLockError = assertFailsWith<AutomationException> { automationTarget(appLock, AutomationKind.READ) }
        assertEquals(AutomationErrorCode.APP_LOCKED, appLockError.code)
        assertTrue(appLockError.message.orEmpty().startsWith("Tandem is locked"))
        val hidden = open + ("privacyModeEnabled" to "true")
        val hiddenError = assertFailsWith<AutomationException> { automationTarget(hidden, AutomationKind.READ) }
        assertEquals(AutomationErrorCode.AMOUNTS_HIDDEN, hiddenError.code)
        assertTrue(hiddenError.message.orEmpty().startsWith("Amounts are hidden in Tandem"))
        assertEquals(BUDGET, automationTarget(appLock + hidden, AutomationKind.WRITE).budgetId)
    }

    @Test
    fun aLockedEncryptedBudgetFailsInsteadOfAsking() =
        withDatabase { database ->
            val encrypted = AutomationTarget(BUDGET, encrypted = true)
            var opened: Pair<String, String>? = null
            val vault: suspend (String, String) -> TandemDatabase? = { id, password ->
                opened = id to password
                database
            }
            assertSame(database, automationDatabase(AutomationTarget(BUDGET, encrypted = false), database, null, vault))
            assertEquals(AutomationErrorCode.BUDGET_LOCKED, suspendCode { automationDatabase(encrypted, database, null, vault) })
            val otherKey = BackgroundBudgetKey.encode("other-budget", "secret")
            assertEquals(AutomationErrorCode.BUDGET_LOCKED, suspendCode { automationDatabase(encrypted, database, otherKey, vault) })
            assertNull(opened)
            val key = BackgroundBudgetKey.encode(BUDGET, "secret")
            assertEquals(
                AutomationErrorCode.BUDGET_LOCKED,
                suspendCode { automationDatabase(encrypted, database, key) { _, _ -> null } },
            )
            assertSame(database, automationDatabase(encrypted, database, key, vault))
            assertEquals(BUDGET to "secret", opened)
        }

    @Test
    fun amountsNeedASignAndTheKeypadsFormat() {
        assertEquals(-1240L, automationAmount("-12.40", null))
        assertEquals(500L, automationAmount(" +5 ", null))
        assertEquals(-1240L, automationAmount("−12.40", null))
        listOf(null, "", "%amount", "12.40", "-12.4.0", "-0", "-abc", "-99999999999").forEach { text ->
            assertEquals(AutomationErrorCode.AMOUNT, code { automationAmount(text, null) }, "for $text")
        }
        assertEquals("2026-11-26", automationDate(null, TODAY))
        assertEquals("2026-11-20", automationDate("2026-11-20", TODAY))
        assertEquals(AutomationErrorCode.DATE, code { automationDate("20/11/2026", TODAY) })
        assertEquals(AutomationErrorCode.DATE, code { automationDate("2099-01-01", TODAY) })
        assertTrue(automationCleared("Yes"))
        assertFalse(automationCleared("%cleared"))
        assertEquals(AutomationErrorCode.CLEARED, code { automationCleared("maybe") })
        assertNull(automationInput("  "))
        assertEquals("Coffee", automationInput(" Coffee "))
    }

    @Test
    fun addsThroughTheOutboxAndResolvesNames() =
        withBudget { database ->
            val added = add(database, AutomationTransactionInput("-12.40", "corner café", "groceries", "Checking", notes = "From SMS"))
            val write = repository(database).readPendingWrites(BUDGET).single()
            assertEquals("CREATE", write.action)
            assertEquals("queued", write.status)
            assertEquals(added.transactionId, write.recordId)
            val patch = Json.parseToJsonElement(write.patchJson).jsonObject
            assertEquals(JsonPrimitive("checking"), patch["account"])
            assertEquals(JsonPrimitive(-1240), patch["amount"])
            assertEquals(JsonPrimitive(TODAY), patch["date"])
            assertEquals(JsonPrimitive("cafe"), patch["payee"])
            assertEquals(JsonPrimitive("groceries"), patch["category"])
            assertEquals(JsonPrimitive(false), patch["cleared"])
            assertNull(patch["payee_name"])
            assertEquals(-1240L, repository(database).findRecord(BUDGET, "transaction", added.transactionId)?.amountMinor)
            assertEquals("Corner Café", added.payeeName)
            assertTrue(added.summary.startsWith("Added ") && added.summary.endsWith("12.40 · Corner Café · Groceries"), added.summary)
            assertEquals(mapOf(added.transactionId to "Tasker"), automatedTransactionSources(database, BUDGET))
        }

    @Test
    fun unknownCategoriesStayUncategorizedAndNewPayeesAreNamed() =
        withBudget { database ->
            add(database, AutomationTransactionInput("+30", "New Shop", "Nope", "savings", cleared = "true"))
            val patch = Json.parseToJsonElement(repository(database).readPendingWrites(BUDGET).single().patchJson).jsonObject
            assertEquals(JsonNull, patch["payee"])
            assertEquals(JsonPrimitive("New Shop"), patch["payee_name"])
            assertEquals(JsonNull, patch["category"])
            assertEquals(JsonPrimitive(true), patch["cleared"])
        }

    @Test
    fun unknownOrClosedAccountsAndTransferPayeesAddNothing() =
        withBudget { database ->
            assertEquals(
                AutomationErrorCode.ACCOUNT,
                suspendCode { add(database, AutomationTransactionInput("-1", account = "Brokerage")) },
            )
            assertEquals(AutomationErrorCode.ACCOUNT, suspendCode { add(database, AutomationTransactionInput("-1", account = "Old card")) })
            assertEquals(AutomationErrorCode.ACCOUNT, suspendCode { add(database, AutomationTransactionInput("-1")) })
            assertEquals(
                AutomationErrorCode.PAYEE,
                suspendCode { add(database, AutomationTransactionInput("-1", "Transfer: Savings", account = "Checking")) },
            )
            assertEquals(AutomationErrorCode.AMOUNT, suspendCode { add(database, AutomationTransactionInput("12", account = "Checking")) })
            assertTrue(repository(database).readPendingWrites(BUDGET).isEmpty())
        }

    @Test
    fun repeatsOfAnExternalIdWithinThirtyDaysAddNothing() =
        withBudget { database ->
            val input = AutomationTransactionInput("-4.50", account = "Checking", externalId = "sms-42")
            val first = add(database, input)
            val again = add(database, input.copy(amount = "-9"), now = NOW + 29L * DAY)
            assertTrue(again.repeated)
            assertEquals(first.transactionId, again.transactionId)
            assertEquals(1, repository(database).readPendingWrites(BUDGET).size)
            val later = add(database, input, now = NOW + 31L * DAY)
            assertFalse(later.repeated)
            assertEquals(2, repository(database).readPendingWrites(BUDGET).size)
            add(database, input.copy(externalId = "%id"))
            add(database, input.copy(externalId = "%id"))
            assertEquals(4, repository(database).readPendingWrites(BUDGET).size)
        }

    @Test
    fun undoRemovesAnEntryThatHasNotSynced() =
        withBudget { database ->
            val added = add(database, AutomationTransactionInput("-2", account = "Checking"))
            assertEquals(AutomationUndo.REMOVED, undoAutomatedTransaction(database, BUDGET, added.transactionId, NOW))
            assertEquals("discarded", repository(database).readPendingWrites(BUDGET).single().status)
            assertEquals(AutomationUndo.OPEN_TANDEM, undoAutomatedTransaction(database, BUDGET, "tandem:missing", NOW))
            assertEquals(AutomationUndo.DELETING, undoAutomatedTransaction(database, BUDGET, "synced", NOW))
            assertEquals("synced", repository(database).readPendingWrites(BUDGET).single { it.action == "DELETE" }.recordId)
        }

    @Test
    fun readsBudgetNumbersFromThisDevice() =
        withBudget { database ->
            val month = readAutomationBudget(database, BUDGET, AutomationReadInput(), TODAY)
            assertEquals(listOf("320.00", "500.00", "180.00", "", "75.25"), month.texts())
            val category = readAutomationBudget(database, BUDGET, AutomationReadInput("Groceries", "checking", "2026-11"), TODAY)
            assertEquals(listOf("-20.00", "100.00", "120.00", "-10.00", "75.25"), category.texts())
            assertEquals(
                AutomationErrorCode.MONTH,
                suspendCode {
                    readAutomationBudget(database, BUDGET, AutomationReadInput(month = "11/2026"), TODAY)
                },
            )
            assertEquals(
                AutomationErrorCode.NOT_ON_DEVICE,
                suspendCode { readAutomationBudget(database, BUDGET, AutomationReadInput(month = "2025-01"), TODAY) },
            )
            assertEquals(
                AutomationErrorCode.CATEGORY,
                suspendCode { readAutomationBudget(database, BUDGET, AutomationReadInput("Fun"), TODAY) },
            )
            assertEquals(
                AutomationErrorCode.ACCOUNT,
                suspendCode { readAutomationBudget(database, BUDGET, AutomationReadInput(account = "x"), TODAY) },
            )
            assertEquals("-12", automationDecimal(-1240, 0))
            assertEquals("0", automationDecimal(-40, 0))
            assertEquals("0.05", automationDecimal(5, 2))
        }

    @Test
    fun choicesComeFromTheLocalBudget() =
        withBudget { database ->
            val choices = automationChoices(database, BUDGET)
            assertEquals(listOf("Checking", "Savings"), choices.accounts)
            assertEquals(listOf("Groceries"), choices.categories)
            assertEquals(listOf("Corner Café"), choices.payees)
        }

    private fun AutomationBudgetNumbers.texts() =
        listOf(leftToSpendMinor, budgetedMinor, spentMinor, accountBalanceMinor, toBudgetMinor).map(::text)

    private suspend fun add(
        database: TandemDatabase,
        input: AutomationTransactionInput,
        now: Long = NOW,
    ) = addAutomatedTransaction(database, BUDGET, input, "Tasker", TODAY, now)

    private fun repository(database: TandemDatabase) = LocalBudgetRepository(database) { NOW }

    private fun code(block: () -> Unit): Int = assertFailsWith<AutomationException> { block() }.code

    private suspend fun suspendCode(block: suspend () -> Unit): Int =
        try {
            block()
            error("Expected the call to fail")
        } catch (error: AutomationException) {
            error.code
        }

    private fun withDatabase(block: suspend (TandemDatabase) -> Unit) =
        runBlocking {
            val database =
                Room
                    .inMemoryDatabaseBuilder<TandemDatabase>()
                    .setDriver(AndroidSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            try {
                block(database)
            } finally {
                database.close()
            }
        }

    private fun withBudget(block: suspend (TandemDatabase) -> Unit) =
        withDatabase { database ->
            repository(database).applyFullSnapshot(BUDGET, "generation-1", "cursor-1", null, budgetRecords())
            block(database)
        }

    private fun budgetRecords() =
        listOf(
            record("account", "checking", """{"id":"checking","name":"Checking","offbudget":false,"closed":false}"""),
            record("account", "savings", """{"id":"savings","name":"Savings","offbudget":true,"closed":false}"""),
            record("account", "old", """{"id":"old","name":"Old card","offbudget":false,"closed":true}"""),
            record("category", "groceries", """{"id":"groceries","name":"Groceries","hidden":false}"""),
            record("category", "hidden", """{"id":"hidden","name":"Archive","hidden":true}"""),
            record("payee", "cafe", """{"id":"cafe","name":"Corner Café"}"""),
            record("payee", "to-savings", """{"id":"to-savings","name":"Transfer: Savings","transfer_acct":"savings"}"""),
            record(
                "transaction",
                "synced",
                """{"id":"synced","account":"checking","amount":-1000,"date":"2026-11-02"}""",
                accountId = "checking",
                amount = -1000,
            ),
            record(
                "budget-month",
                "2026-11",
                """{"month":"2026-11","toBudget":7525,"totalBudgeted":50000,"totalSpent":-18000,"totalBalance":32000,""" +
                    """"categoryGroups":[{"id":"food","name":"Food","categories":[""" +
                    """{"id":"groceries","name":"Groceries","budgeted":10000,"spent":-12000,"balance":-2000}]}]}""",
            ),
        )

    private fun record(
        type: String,
        id: String,
        body: String,
        accountId: String? = null,
        amount: Long? = null,
    ) = RemoteBudgetRecord(
        budgetId = BUDGET,
        recordType = type,
        recordId = id,
        revision = "revision-1",
        bodyJson = body,
        generation = "generation-1",
        recordDate = "2026-11-02".takeIf { type == "transaction" },
        accountId = accountId,
        amountMinor = amount,
        budgetMonth = "2026-11".takeIf { type == "transaction" },
    )

    private companion object {
        const val BUDGET = "budget-1"
        const val TODAY = "2026-11-26"
        const val NOW = 1_795_000_000_000L
        const val DAY = 24L * 60 * 60 * 1000
    }
}
