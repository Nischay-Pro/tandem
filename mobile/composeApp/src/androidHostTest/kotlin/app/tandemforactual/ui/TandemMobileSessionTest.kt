package app.tandemforactual.ui

import app.tandemforactual.api.client.TandemApiClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TandemMobileSessionTest {
    @Test
    fun retryKeepsSyncAttentionVisibleUntilSuccess() {
        val previousFailure = SyncPresentationState().failed("Could not sync this budget.")

        val retrying = previousFailure.begin()

        assertTrue(retrying.isSyncing)
        assertEquals("Could not sync this budget.", retrying.error)
        assertEquals(SyncPresentationState(), retrying.succeeded())
    }

    @Test
    fun stoppingACancelledRetryKeepsThePreviousSyncAttention() {
        val previousFailure = SyncPresentationState().failed("Could not sync this budget.")

        val stopped = previousFailure.begin().stopped()

        assertFalse(stopped.isSyncing)
        assertEquals(previousFailure.error, stopped.error)
    }

    @Test
    fun restoringStoredDeviceSessionDoesNotWaitForTheRemoteBudgetCatalog() =
        runBlocking {
            val savedSession =
                """{"serverUrl":"https://budget.example.test","accessToken":"saved-token","device":{"deviceId":"device-1","principalId":"person-1","budgetIds":["budget-1"]}}"""
            val credentialStore =
                object : DeviceCredentialStore {
                    override fun read(): String = savedSession

                    override fun write(value: String) = Unit

                    override fun clear() = Unit
                }
            val session =
                TandemMobileSession(
                    apiClient = TandemApiClient(),
                    database = null,
                    nowEpochMillis = { 0L },
                    credentialStore = credentialStore,
                )

            assertEquals("https://budget.example.test", session.restoreStoredSession())
            assertTrue(session.isAuthenticated)
            assertTrue(session.budgets.isEmpty())
            assertFalse(session.budgetCatalogLoaded)
        }

    @Test
    fun transientCredentialReadFailureKeepsStoredCredentialAndCanBeRetried() =
        runBlocking {
            var readAttempts = 0
            var clearCalls = 0
            val credentialStore =
                object : DeviceCredentialStore {
                    override fun read(): String? {
                        readAttempts += 1
                        if (readAttempts == 1) error("Keychain is temporarily unavailable")
                        return null
                    }

                    override fun write(value: String) = Unit

                    override fun clear() {
                        clearCalls += 1
                    }
                }
            val session =
                TandemMobileSession(
                    apiClient = TandemApiClient(),
                    database = null,
                    nowEpochMillis = { 0L },
                    credentialStore = credentialStore,
                )

            assertNull(session.restoreStoredSession())
            assertEquals(0, clearCalls)
            assertFalse(session.isCredentialStored)
            assertTrue(session.credentialStorageError.orEmpty().contains("Retry"))

            assertNull(session.restoreStoredSession())
            assertEquals(2, readAttempts)
            assertEquals(0, clearCalls)
            assertNull(session.credentialStorageError)
        }
}
