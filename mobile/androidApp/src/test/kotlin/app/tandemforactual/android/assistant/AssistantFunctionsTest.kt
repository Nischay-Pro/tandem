package app.tandemforactual.android.assistant

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionDeniedException
import androidx.appfunctions.AppFunctionDisabledException
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.testing.AppFunctionTestRule
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.work.testing.WorkManagerTestInitHelper
import app.tandemforactual.android.AndroidInboxFile
import app.tandemforactual.android.TandemProcess
import app.tandemforactual.android.tasker.AutomationCalls
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.inbox.InboxKeyring
import app.tandemforactual.data.inbox.InboxPublicKeys
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.RemoteBudgetRecord
import app.tandemforactual.data.store.applyFullSnapshot
import app.tandemforactual.data.store.readPendingWrites
import app.tandemforactual.ui.core.automation.AutomatedTransaction
import app.tandemforactual.ui.core.automation.AutomationAccess
import app.tandemforactual.ui.core.automation.AutomationErrorCode
import app.tandemforactual.ui.core.automation.AutomationException
import app.tandemforactual.ui.core.automation.AutomationTarget
import app.tandemforactual.ui.core.automation.AutomationUndo
import app.tandemforactual.ui.core.automation.automatedTransactionSources
import app.tandemforactual.ui.core.automation.openSealedInbox
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AssistantFunctionsTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @get:Rule
    val appFunctions = AppFunctionTestRule(context)

    private lateinit var database: TandemDatabase
    private val month = LocalDate.now().toString().take(7)

    @Before
    fun openBudget() =
        runBlocking {
            WorkManagerTestInitHelper.initializeTestWorkManager(context)
            shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            database =
                Room
                    .inMemoryDatabaseBuilder<TandemDatabase>()
                    .setDriver(AndroidSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            TandemProcess.useDatabase(database)
            LocalBudgetRepository(database) { 0L }.applyFullSnapshot(BUDGET, "generation-1", "cursor-1", null, records())
            preferences("selectedBudgetId" to BUDGET, AutomationAccess.WRITES to "true", AutomationAccess.READS to "true")
        }

    @After
    fun closeBudget() {
        AndroidInboxFile.deleteAll(context)
        database.close()
    }

    @Test
    fun theGeneratedSchemaListsBothFunctions() {
        val xml = javaClass.classLoader!!.getResource("assets/tandem_app_functions.xml")!!.readText()
        assertTrue(xml.contains("logTransaction"))
        assertTrue(xml.contains("leftToSpend"))
    }

    @Test
    fun logTransactionQueuesItOnceAndNamesTheAssistant() =
        runBlocking {
            val transaction = AssistantTransaction(amount = "12.40", direction = "out", account = "Checking", externalId = "sms-1")
            val added = success<AssistantLoggedTransaction>(log(transaction))
            assertFalse(added.repeated)
            assertTrue(added.summary.startsWith("Added"))
            val writes = LocalBudgetRepository(database) { 0L }.readPendingWrites(BUDGET)
            assertEquals(listOf(added.transactionId), writes.map { it.recordId })
            assertEquals(ASSISTANT_SOURCE, automatedTransactionSources(database, BUDGET)[added.transactionId])
            val notification = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
            assertEquals("Added by Assistant. Undo removes it.", notification.extras.getString("android.text"))
            val again = success<AssistantLoggedTransaction>(log(transaction))
            assertTrue(again.repeated)
            assertEquals(added.transactionId, again.transactionId)
            assertEquals(1, LocalBudgetRepository(database) { 0L }.readPendingWrites(BUDGET).size)
        }

    @Test
    fun logTransactionRefusesWhileWritesAreOffOrTheInputIsWrong() =
        runBlocking {
            assertTrue(failure(log(AssistantTransaction(amount = "-5", account = "Nowhere"))) is AppFunctionInvalidArgumentException)
            preferences(AutomationAccess.WRITES to "false")
            val off = failure(log(AssistantTransaction(amount = "-5", account = "Checking")))
            assertTrue(off is AppFunctionDisabledException)
            assertTrue(off.errorMessage.orEmpty().startsWith("Adding transactions is off"))
            assertTrue(LocalBudgetRepository(database) { 0L }.readPendingWrites(BUDGET).isEmpty())
        }

    @Test
    fun anEncryptedBudgetFailsWithoutAsking() =
        runBlocking {
            preferences("selectedBudgetEncrypted" to "true")
            val locked = failure(log(AssistantTransaction(amount = "-5", account = "Checking")))
            assertTrue(locked is AppFunctionDeniedException)
            assertTrue(locked.errorMessage.orEmpty().contains("encrypted budget is locked"))
        }

    @Test
    fun aLockedEncryptedBudgetSealsTheEntryForTheNextUnlock() =
        runBlocking {
            preferences("selectedBudgetEncrypted" to "true")
            InboxPublicKeys.publish(database, BUDGET, InboxKeyring(database, BUDGET).ensure("budget password"))
            val transaction = AssistantTransaction(amount = "-5", account = "Checking", payee = "Bakery")
            val saved = success<AssistantLoggedTransaction>(log(transaction))
            assertEquals(AutomatedTransaction.SEALED_SUMMARY, saved.summary)
            assertTrue(LocalBudgetRepository(database) { 0L }.readPendingWrites(BUDGET).isEmpty())
            val inbox = AndroidInboxFile.forBudget(context, BUDGET)
            val sealed = inbox.lines().joinToString("\n")
            assertFalse(sealed.contains("Bakery") || sealed.contains("Checking"))
            val notification = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
            assertEquals("Transaction saved", notification.extras.getString("android.title"))

            val result = openSealedInbox(database, database, inbox, BUDGET, "budget password", LocalDate.now().toString())
            assertEquals(1, result.added)
            assertEquals(listOf(saved.transactionId), LocalBudgetRepository(database) { 0L }.readPendingWrites(BUDGET).map { it.recordId })
            assertTrue(inbox.lines().isEmpty())
        }

    @Test
    fun undoTakesBackASealedEntryBeforeItsAdded() =
        runBlocking {
            preferences("selectedBudgetEncrypted" to "true")
            InboxPublicKeys.publish(database, BUDGET, InboxKeyring(database, BUDGET).ensure("budget password"))
            val saved = success<AssistantLoggedTransaction>(log(AssistantTransaction(amount = "-5", account = "Checking")))
            val target = AutomationTarget(BUDGET, encrypted = true)
            assertEquals(AutomationUndo.REMOVED, AutomationCalls.undo(context, target, saved.transactionId))
            val inbox = AndroidInboxFile.forBudget(context, BUDGET)
            val result = openSealedInbox(database, database, inbox, BUDGET, "budget password", LocalDate.now().toString())
            assertEquals(0, result.added)
            assertTrue(LocalBudgetRepository(database) { 0L }.readPendingWrites(BUDGET).isEmpty())
        }

    @Test
    fun leftToSpendReadsTheCategory() =
        runBlocking {
            val left = success<AssistantLeftToSpend>(leftToSpend("groceries"))
            assertEquals("-20.00", left.amount)
            assertEquals("100.00", left.budgeted)
            assertEquals("120.00", left.spent)
            assertTrue(left.overspent)
            assertTrue(left.sentence.startsWith("groceries is"))
            assertTrue(failure(leftToSpend("Rent")) is AppFunctionElementNotFoundException)
            assertTrue(failure(leftToSpend("Groceries", month = "2026/11")) is AppFunctionInvalidArgumentException)
        }

    @Test
    fun leftToSpendStaysClosedWhileReadsAreOffLockedOrHidden() =
        runBlocking {
            preferences("biometricLockEnabled" to "true")
            assertTrue(failure(leftToSpend("Groceries")) is AppFunctionDeniedException)
            preferences("biometricLockEnabled" to "false", "privacyModeEnabled" to "true")
            assertTrue(failure(leftToSpend("Groceries")) is AppFunctionDeniedException)
            preferences(AutomationAccess.READS to "false")
            assertTrue(failure(leftToSpend("Groceries")) is AppFunctionDisabledException)
        }

    @Test
    fun directionSignsTheAmount() {
        fun amount(
            text: String,
            direction: String?,
        ) = AssistantTransaction(amount = text, direction = direction, account = "Checking").toAutomation().amount
        assertEquals("-12.40", amount("12.40", "out"))
        assertEquals("+3", amount(" 3 ", "In"))
        assertEquals("-7", amount("-7", "out"))
        assertEquals("-7", amount("-7", null))
        assertEquals("-7", amount("-7", " "))
        listOf("+7" to "out", "-7" to "in", "7" to "sideways").forEach { (text, direction) ->
            val error = runCatching { amount(text, direction) }.exceptionOrNull() as AutomationException
            assertEquals(AutomationErrorCode.AMOUNT, error.code)
        }
    }

    @Test
    fun unexpectedFailuresAskToOpenTandem() =
        runBlocking {
            val error = runCatching { assistantCall { error("disk full") } }.exceptionOrNull()
            assertTrue(error is AppFunctionAppUnknownException)
            val saveFailed = AutomationException(AutomationErrorCode.SAVE_FAILED, "Couldn’t save.").toAppFunctionException()
            assertTrue(saveFailed is AppFunctionAppUnknownException)
            assertEquals("Couldn’t save.", saveFailed.errorMessage)
            val readOnly = AutomationException(AutomationErrorCode.READ_ONLY, "Lock editing is on.").toAppFunctionException()
            assertTrue(readOnly is AppFunctionDeniedException)
        }

    private suspend fun preferences(vararg values: Pair<String, String>) {
        val store = AppPreferencesStore(database)
        values.forEach { (name, value) -> store.putValue(name, value) }
    }

    private suspend fun function(name: String): AppFunctionMetadata =
        appFunctions
            .getAppFunctionManager()
            .searchAppFunctions(AppFunctionSearchSpec(packageNames = setOf(context.packageName)))
            .single { it.id.endsWith("#$name") }

    private suspend fun log(transaction: AssistantTransaction): ExecuteAppFunctionResponse {
        val function = function("logTransaction")
        val parameters =
            AppFunctionData
                .Builder(function.parameters, function.packageMetadata.components)
                .setAppFunctionData("transaction", AppFunctionData.serialize(transaction, AssistantTransaction::class.java))
                .build()
        return execute(function, parameters)
    }

    private suspend fun leftToSpend(
        category: String,
        month: String = this.month,
    ): ExecuteAppFunctionResponse {
        val function = function("leftToSpend")
        val parameters =
            AppFunctionData
                .Builder(function.parameters, function.packageMetadata.components)
                .setString("category", category)
                .setString("month", month)
                .build()
        return execute(function, parameters)
    }

    private suspend fun execute(
        function: AppFunctionMetadata,
        parameters: AppFunctionData,
    ) = appFunctions.getAppFunctionManager().executeAppFunction(ExecuteAppFunctionRequest(context.packageName, function.id, parameters))

    private inline fun <reified T : Any> success(response: ExecuteAppFunctionResponse): T =
        (response as ExecuteAppFunctionResponse.Success)
            .returnValue
            .getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)!!
            .deserialize(T::class.java)

    private fun failure(response: ExecuteAppFunctionResponse): AppFunctionException = (response as ExecuteAppFunctionResponse.Error).error

    private fun records() =
        listOf(
            record("account", "checking", """{"id":"checking","name":"Checking","offbudget":false,"closed":false}"""),
            record("category", "groceries", """{"id":"groceries","name":"Groceries","hidden":false}"""),
            record(
                "budget-month",
                month,
                """{"month":"$month","toBudget":7525,"totalBudgeted":50000,"totalSpent":-18000,"totalBalance":32000,""" +
                    """"categoryGroups":[{"id":"food","name":"Food","categories":[""" +
                    """{"id":"groceries","name":"Groceries","budgeted":10000,"spent":-12000,"balance":-2000}]}]}""",
            ),
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

    private companion object {
        const val BUDGET = "budget-1"
    }
}
