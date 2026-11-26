package app.tandemforactual.ui.core.session.budget

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.RemoteBudgetRecord
import app.tandemforactual.data.store.applyFullSnapshot
import app.tandemforactual.data.store.readPendingWrites
import app.tandemforactual.ui.core.session.DeviceCredentialStore
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.restoreStoredSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountReconcileWriteTest {
    @Test
    fun reconcileLocksClearedTransactionsAndOnlyUnclearsWhatTheUserUnticked() =
        runBlocking {
            val database =
                Room
                    .inMemoryDatabaseBuilder<TandemDatabase>()
                    .setDriver(AndroidSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            try {
                val session = TandemMobileSession(TandemApiClient(), database, { 1_000L }, credentialStore = storedSession())
                session.restoreStoredSession()
                assertTrue(session.isAuthenticated)
                val repository = LocalBudgetRepository(database) { 1_000L }
                repository.applyFullSnapshot(
                    budgetId = "budget-1",
                    generation = "generation-1",
                    cursor = "cursor-1",
                    expectedCheckpoint = null,
                    records =
                        listOf(
                            transaction("already-cleared", cleared = true),
                            transaction("unticked", cleared = true),
                            transaction("newly-ticked", cleared = false),
                            transaction("untouched", cleared = false),
                        ),
                )

                assertTrue(
                    session.budgetEdits.markTransactionsReconciled(
                        "budget-1",
                        "checking",
                        setOf("already-cleared", "unticked", "newly-ticked", "untouched"),
                        mapOf("newly-ticked" to true, "unticked" to false),
                    ),
                    session.transactionWriteMessage,
                )

                val patches =
                    repository.readPendingWrites("budget-1").associate { write ->
                        write.recordId to Json.parseToJsonElement(write.patchJson).jsonObject.booleans()
                    }
                assertEquals(
                    mapOf(
                        "already-cleared" to mapOf("reconciled" to true),
                        "unticked" to mapOf("cleared" to false),
                        "newly-ticked" to mapOf("cleared" to true, "reconciled" to true),
                    ),
                    patches,
                )
            } finally {
                database.close()
            }
        }

    private fun JsonObject.booleans() = mapValues { it.value.jsonPrimitive.boolean }

    private fun transaction(
        id: String,
        cleared: Boolean,
    ) = RemoteBudgetRecord(
        budgetId = "budget-1",
        recordType = "transaction",
        recordId = id,
        revision = "revision-$id",
        bodyJson = """{"id":"$id","amount":-1000,"account":"checking","date":"2026-11-01","cleared":$cleared,"reconciled":false}""",
        generation = "generation-1",
        accountId = "checking",
    )

    private fun storedSession() =
        object : DeviceCredentialStore {
            override fun read(): String =
                """{"serverUrl":"https://budget.example.test","token":"saved-token","deviceId":"device-1","principalId":"person-1","budgetIds":["budget-1"]}"""

            override fun write(value: String) = Unit

            override fun clear() = Unit
        }
}
