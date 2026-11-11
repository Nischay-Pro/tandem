package app.tandemforactual.ui

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalRecordProjection
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import app.tandemforactual.data.store.RemoteBudgetRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SignOutCacheClearFailureTest {
    @Test
    fun failedLocalPurgeAfterRevocationDoesNotReportSignOutOrDeleteTheCachedBudget() =
        runBlocking {
            val context: Context = RuntimeEnvironment.getApplication()
            context.deleteDatabase(DATABASE_NAME)
            val database = openDatabase(context)
            val repository = LocalBudgetRepository(database) { NOW }
            try {
                seedCachedBudget(repository, AppPreferencesStore(database), withPendingWrite = false)
                var savedCredential: String? = storedSession()
                var revokeCalls = 0
                var credentialClearCalls = 0
                val credentialStore =
                    object : DeviceCredentialStore {
                        override fun read(): String? = savedCredential

                        override fun write(value: String) {
                            savedCredential = value
                        }

                        override fun clear() {
                            assertEquals(1, revokeCalls, "Actual device revocation must precede credential clearing")
                            credentialClearCalls += 1
                            savedCredential = null
                            database.close()
                        }
                    }
                val apiClient = TandemApiClient()
                val session =
                    TandemMobileSession(
                        apiClient = apiClient,
                        database = database,
                        nowEpochMillis = { NOW },
                        credentialStore = credentialStore,
                        revokeCurrentDeviceForSignOut = {
                            revokeCalls += 1
                            assertNotNull(savedCredential)
                            assertNotNull(repository.findRecord(BUDGET_ID, "transaction", TRANSACTION_ID))
                        },
                    )
                session.restoreStoredSession()

                assertTrue(session.isAuthenticated)
                assertFalse(session.signOutAndClearLocalData())

                assertEquals(1, revokeCalls)
                assertEquals(1, credentialClearCalls)
                assertNull(savedCredential)
                assertTrue(session.isAuthenticated, "The failed purge must not reset the in-memory session")
                assertNotNull(session.authenticatedDevice)
                assertEquals("Couldn’t clear this device’s cached budgets. Try again.", session.signOutError)
                assertFalse(session.isSigningOut)
            } finally {
                database.close()
            }

            val reopenedDatabase = openDatabase(context)
            try {
                val cachedTransaction =
                    LocalBudgetRepository(reopenedDatabase) { NOW }
                        .findRecord(BUDGET_ID, "transaction", TRANSACTION_ID)
                assertEquals(TRANSACTION_JSON, cachedTransaction?.remoteJson)
                assertEquals(
                    """{"serverUrl":"https://actual.example.test","principalId":"principal-1"}""",
                    AppPreferencesStore(reopenedDatabase).read()[CACHE_OWNER_KEY],
                )
            } finally {
                reopenedDatabase.close()
                context.deleteDatabase(DATABASE_NAME)
            }
        }

    @Test
    fun signOutPreflightKeepsAnUnresolvedEditAndDoesNotRevokeTheDevice() =
        runBlocking {
            val database = newInMemoryDatabase()
            try {
                val repository = LocalBudgetRepository(database) { NOW }
                seedCachedBudget(repository, AppPreferencesStore(database), withPendingWrite = true)
                var savedCredential: String? = storedSession()
                var revokeCalls = 0
                var credentialClearCalls = 0
                val credentialStore =
                    object : DeviceCredentialStore {
                        override fun read(): String? = savedCredential

                        override fun write(value: String) {
                            savedCredential = value
                        }

                        override fun clear() {
                            credentialClearCalls += 1
                            savedCredential = null
                        }
                    }
                val apiClient = TandemApiClient()
                val session =
                    TandemMobileSession(
                        apiClient = apiClient,
                        database = database,
                        nowEpochMillis = { NOW },
                        credentialStore = credentialStore,
                        revokeCurrentDeviceForSignOut = { revokeCalls += 1 },
                    )
                session.restoreStoredSession()

                assertFalse(session.signOutAndClearLocalData())

                assertEquals(0, revokeCalls)
                assertEquals(0, credentialClearCalls)
                assertEquals(storedSession(), savedCredential)
                assertEquals("queued", repository.readPendingWrites(BUDGET_ID).single().status)
                assertEquals(OPTIMISTIC_TRANSACTION_JSON, repository.findRecord(BUDGET_ID, "transaction", TRANSACTION_ID)?.localJson)
                assertTrue(session.isAuthenticated)
                assertTrue(session.signOutError.orEmpty().contains("pending changes"))
            } finally {
                database.close()
            }
        }

    private suspend fun seedCachedBudget(
        repository: LocalBudgetRepository,
        preferences: AppPreferencesStore,
        withPendingWrite: Boolean,
    ) {
        repository.applyFullSnapshot(
            budgetId = BUDGET_ID,
            generation = "generation-1",
            cursor = "cursor-1",
            expectedCheckpoint = null,
            records =
                listOf(
                    RemoteBudgetRecord(
                        budgetId = BUDGET_ID,
                        recordType = "transaction",
                        recordId = TRANSACTION_ID,
                        revision = "revision-1",
                        bodyJson = TRANSACTION_JSON,
                        generation = "generation-1",
                    ),
                ),
        )
        preferences.putValue(
            CACHE_OWNER_KEY,
            """{"serverUrl":"https://actual.example.test","principalId":"principal-1"}""",
        )
        if (withPendingWrite) {
            repository.enqueueWrite(
                LocalWriteRequest(
                    operationId = "pending-edit",
                    idempotencyKey = "pending-edit-key",
                    budgetId = BUDGET_ID,
                    recordType = "transaction",
                    recordId = TRANSACTION_ID,
                    action = LocalWriteAction.UPDATE,
                    baseRevision = "revision-1",
                    baseSnapshotJson = TRANSACTION_JSON,
                    patchJson = """{"amount":1200}""",
                    optimisticJson = OPTIMISTIC_TRANSACTION_JSON,
                    createdAtEpochMs = NOW,
                    optimisticProjection = LocalRecordProjection("2026-11-10", "checking", null, null, 1200L, null),
                ),
            )
        }
    }

    private fun openDatabase(context: Context): TandemDatabase =
        Room
            .databaseBuilder<TandemDatabase>(context = context, name = context.getDatabasePath(DATABASE_NAME).absolutePath)
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private fun newInMemoryDatabase(): TandemDatabase =
        Room
            .inMemoryDatabaseBuilder<TandemDatabase>()
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private fun storedSession(): String =
        """{"serverUrl":"https://actual.example.test","accessToken":"saved-token","device":{"deviceId":"device-1","principalId":"principal-1","budgetIds":["$BUDGET_ID"]}}"""

    private companion object {
        const val DATABASE_NAME = "sign-out-cache-clear-failure.db"
        const val BUDGET_ID = "budget-1"
        const val TRANSACTION_ID = "transaction-1"
        const val CACHE_OWNER_KEY = "internal:budget-cache-owner-v1"
        const val NOW = 1_000L
        const val TRANSACTION_JSON = """{"id":"transaction-1","amount":1000}"""
        const val OPTIMISTIC_TRANSACTION_JSON = """{"id":"transaction-1","amount":1200}"""
    }
}
