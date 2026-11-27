package app.tandemforactual.ui.core.automation

import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.ui.core.platform.iosTandemDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

class IosLockedReadsTest {
    @Test
    fun readsOfALockedEncryptedBudgetAskToOpenTandemWithoutTheOptIn() =
        runBlocking {
            AppPreferencesStore(iosTandemDatabase).save(
                mapOf(
                    "selectedBudgetId" to "budget-1",
                    "selectedBudgetEncrypted" to "true",
                    AutomationAccess.READS to "true",
                    AutomationAccess.WRITES to "true",
                ),
            )
            val failure = CompletableDeferred<IosAutomationFailure?>()
            IosAutomation.leftToSpend("groceries", "Groceries") { _, error -> failure.complete(error) }
            val error = failure.await()
            assertEquals(AutomationErrorCode.BUDGET_LOCKED, error?.code)
            assertEquals("Open Tandem to unlock this budget.", error?.message)

            val records = CompletableDeferred<AutomationRecords>()
            IosAutomation.records { records.complete(it) }
            assertTrue(records.await().accounts.isEmpty())
            assertTrue(records.await().categories.isEmpty())
            AppPreferencesStore(iosTandemDatabase).save(emptyMap())
        }
}
