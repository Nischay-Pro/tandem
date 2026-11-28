package app.tandemforactual.ui.core.automation

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.inbox.InboxFile
import app.tandemforactual.data.inbox.InboxPublicKeys
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.RemoteBudgetRecord
import app.tandemforactual.data.store.applyFullSnapshot
import app.tandemforactual.data.store.readPendingWrites
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
class SealedAutomationTest {
    private val inbox = MemoryInboxFile()

    @Test
    fun aLockedBudgetNeedsOneUnlockBeforeAutomationCanSealForIt() =
        withBudgets { _, main ->
            val error =
                try {
                    seal(main, coffee())
                    null
                } catch (failure: AutomationException) {
                    failure
                }
            assertEquals(AutomationErrorCode.BUDGET_LOCKED, error?.code)
            assertTrue(inbox.lines.isEmpty())
        }

    @Test
    fun lockedWritesAreSealedAndAddedExactlyOnceOnUnlock() =
        withBudgets { vault, main ->
            unlock(vault, main)
            val saved = seal(main, coffee())
            assertTrue(saved.sealed)
            assertEquals("Saved. It will be added when you next open Tandem.", saved.summary)
            assertFalse(inbox.lines.joinToString().contains("Corner"))
            assertTrue(pending(vault).isEmpty())

            val result = unlock(vault, main)
            assertEquals(SealedInboxResult(1, emptyList(), 0), result)
            assertNull(result.message)
            val write = pending(vault).single()
            assertEquals(saved.transactionId, write.recordId)
            val patch = Json.parseToJsonElement(write.patchJson).jsonObject
            assertEquals(JsonPrimitive(-1240), patch["amount"])
            assertEquals(JsonPrimitive("cafe"), patch["payee"])
            assertEquals(JsonPrimitive("2026-11-20"), patch["date"])
            assertEquals("Tasker", automatedTransactionSources(vault, BUDGET)[saved.transactionId])
            assertEquals(SealedInboxResult(0, emptyList(), 0), unlock(vault, main))
            assertEquals(1, pending(vault).size)
        }

    @Test
    fun aLockedTransferIsSealedWithItsOtherAccount() =
        withBudgets { vault, main ->
            unlock(vault, main)
            seal(main, AutomationTransactionInput("40", account = "Checking", transferTo = "Savings"))
            assertEquals(SealedInboxResult(1, emptyList(), 0), unlock(vault, main))
            val patch = Json.parseToJsonElement(pending(vault).single().patchJson).jsonObject
            assertEquals(JsonPrimitive(-4000), patch["amount"])
            assertEquals(JsonPrimitive("to-savings"), patch["payee"])
        }

    @Test
    fun anEntryAddedJustBeforeTheAppWasKilledIsNotAddedTwice() =
        withBudgets { vault, main ->
            unlock(vault, main)
            val saved = seal(main, coffee())
            addAutomatedTransaction(vault, BUDGET, coffee().copy(date = "2026-11-20"), "Tasker", TODAY, NOW, saved.transactionId)
            assertEquals(1, pending(vault).size)

            assertEquals(0, unlock(vault, main).added)
            assertEquals(listOf(saved.transactionId), pending(vault).map { it.recordId })
            assertTrue(inbox.lines.isEmpty())
        }

    @Test
    fun aPasswordChangeMakesANewKeyAfterAddingWhatTheOldOneSealed() =
        withBudgets { vault, main ->
            unlock(vault, main)
            val oldKey = assertNotNull(InboxPublicKeys.read(main, BUDGET))
            val before = seal(main, coffee())
            assertEquals(1, unlock(vault, main, password = "new password").added)
            val newKey = assertNotNull(InboxPublicKeys.read(main, BUDGET))
            assertFalse(oldKey.contentEquals(newKey))
            val after = seal(main, coffee(externalId = "sms-2"))
            assertEquals(1, unlock(vault, main, password = "new password").added)
            assertEquals(setOf(before.transactionId, after.transactionId), pending(vault).map { it.recordId }.toSet())
            assertContentEquals(newKey, InboxPublicKeys.read(main, BUDGET))
        }

    @Test
    fun undoTakesBackAnEntryBeforeTheUnlock() =
        withBudgets { vault, main ->
            unlock(vault, main)
            val undone = seal(main, coffee())
            val kept = seal(main, coffee(externalId = "sms-2"))
            assertTrue(withdrawSealedTransaction(inbox, BUDGET, undone.transactionId))
            assertFalse(withdrawSealedTransaction(inbox, BUDGET, undone.transactionId))
            assertFalse(withdrawSealedTransaction(inbox, BUDGET, "not-a-transaction"))
            assertEquals(1, unlock(vault, main).added)
            assertEquals(listOf(kept.transactionId), pending(vault).map { it.recordId })
            assertFalse(withdrawSealedTransaction(inbox, BUDGET, kept.transactionId))
        }

    @Test
    fun entriesThatNoLongerFitTheBudgetAreReportedNotLost() =
        withBudgets { vault, main ->
            unlock(vault, main)
            seal(main, coffee().copy(account = "Closed card"))
            seal(main, coffee(externalId = "sms-9"))
            seal(main, coffee(externalId = "sms-9"))
            val result = unlock(vault, main)
            assertEquals(1, result.added)
            assertEquals(listOf("No account named “Closed card” in this budget."), result.failures)
            assertEquals(
                "Tandem couldn’t add 1 transaction saved while this budget was locked. No account named “Closed card” in this budget.",
                result.message,
            )
            inbox.lines += "e " + "A".repeat(200)
            assertEquals(UNREADABLE_ONE, unlock(vault, main).message)
            assertEquals(UNREADABLE_TWO, SealedInboxResult(0, emptyList(), 2).message)
        }

    @Test
    fun onlyWhatNeedsNoBudgetIsCheckedBeforeSealing() {
        assertEquals("2026-11-26", checkedForSealing(coffee().copy(date = null), TODAY).date)
        listOf(
            coffee().copy(amount = null) to AutomationErrorCode.AMOUNT,
            coffee().copy(amount = "12.40") to AutomationErrorCode.AMOUNT,
            coffee().copy(amount = "12.4", amountFormat = AutomationAmountFormat.PLAIN) to AutomationErrorCode.AMOUNT,
            coffee().copy(account = "%account") to AutomationErrorCode.ACCOUNT,
            coffee().copy(date = "20/11/2026") to AutomationErrorCode.DATE,
            coffee().copy(cleared = "maybe") to AutomationErrorCode.CLEARED,
        ).forEach { (input, code) ->
            assertEquals(code, assertFailsWith<AutomationException> { checkedForSealing(input, TODAY) }.code, "for $input")
        }
        assertEquals("-3.5", checkedForSealing(coffee().copy(amount = "-3.5", amountFormat = AutomationAmountFormat.PLAIN), TODAY).amount)
    }

    private fun coffee(externalId: String = "sms-1") =
        AutomationTransactionInput(
            amount = "-12.40",
            payee = "Corner Café",
            account = "Checking",
            date = "2026-11-20",
            externalId = externalId,
        )

    private suspend fun seal(
        main: TandemDatabase,
        input: AutomationTransactionInput,
    ) = sealAutomatedTransaction(main, inbox, BUDGET, input, "Tasker", TODAY, NOW)

    private suspend fun unlock(
        vault: TandemDatabase,
        main: TandemDatabase,
        password: String = "correct horse",
    ) = openSealedInbox(vault, main, inbox, BUDGET, password, TODAY)

    private suspend fun pending(vault: TandemDatabase) = LocalBudgetRepository(vault) { NOW }.readPendingWrites(BUDGET)

    private fun withBudgets(block: suspend (vault: TandemDatabase, main: TandemDatabase) -> Unit) =
        runBlocking {
            val vault = database()
            val main = database()
            try {
                LocalBudgetRepository(vault) { NOW }.applyFullSnapshot(BUDGET, "generation-1", "cursor-1", null, records())
                block(vault, main)
                assertFalse(AppPreferencesStore(main).read().values.any { it.contains("Corner") })
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
            record("payee", "cafe", """{"id":"cafe","name":"Corner Café"}"""),
            record("account", "savings", """{"id":"savings","name":"Savings","offbudget":false,"closed":false}"""),
            record("payee", "to-savings", """{"id":"to-savings","name":"","transfer_acct":"savings"}"""),
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
        const val UNREADABLE_ONE = "Tandem couldn’t read 1 transaction saved while this budget was locked. Add them again."
        const val UNREADABLE_TWO = "Tandem couldn’t read 2 transactions saved while this budget was locked. Add them again."
    }
}
