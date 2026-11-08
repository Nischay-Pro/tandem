package app.tandemforactual.ui

import app.tandemforactual.api.client.TandemApiClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TandemMobileSessionTest {
    @Test
    fun activityRefreshKeepsOnePageAndCapsLargeLists() {
        assertEquals(50, activityTransactionRefreshLimit(0))
        assertEquals(50, activityTransactionRefreshLimit(50))
        assertEquals(125, activityTransactionRefreshLimit(125))
        assertEquals(200, activityTransactionRefreshLimit(5_000))
    }

    @Test
    fun transactionEditPatchIncludesPayeeCategoryDateAndAccountChanges() {
        val base =
            Json
                .parseToJsonElement(
                    """{"account":"account-1","amount":-1200,"date":"2026-11-01","payee":"payee-1","category":"category-1","notes":"before"}""",
                ).jsonObject

        val patch =
            buildTransactionEditPatch(
                base = base,
                signedAmount = -1_200,
                fallbackCurrentAmount = -1_200,
                notes = "after",
                details = TransactionEditDetails("account-2", "payee-2", "category-2", "2026-11-05"),
            )

        assertEquals("account-2", patch.getValue("account").jsonPrimitive.content)
        assertEquals("payee-2", patch.getValue("payee").jsonPrimitive.content)
        assertEquals("category-2", patch.getValue("category").jsonPrimitive.content)
        assertEquals("2026-11-05", patch.getValue("date").jsonPrimitive.content)
        assertEquals("after", patch.getValue("notes").jsonPrimitive.content)
    }

    @Test
    fun transactionEditPatchCanClearOptionalFields() {
        val base =
            Json
                .parseToJsonElement(
                    """{"account":"account-1","amount":100,"date":"2026-11-01","payee":"payee-1","category":"category-1"}""",
                ).jsonObject

        val patch =
            buildTransactionEditPatch(
                base = base,
                signedAmount = 100,
                fallbackCurrentAmount = 0,
                notes = "",
                details = TransactionEditDetails("account-1", null, null, "2026-11-01"),
            )

        assertEquals(
            JsonObject(mapOf("payee" to kotlinx.serialization.json.JsonNull, "category" to kotlinx.serialization.json.JsonNull)),
            patch,
        )
    }

    @Test
    fun transactionWritesRejectAmountsAboveTheApiLimitBeforeStorageAccess() =
        runBlocking {
            val session =
                TandemMobileSession(
                    apiClient = TandemApiClient(),
                    database = null,
                    nowEpochMillis = { 0L },
                )
            val overLimit = formatAmount(MAX_TRANSACTION_AMOUNT_MINOR_UNITS + 1)

            session.updateTransaction("budget-1", "transaction-1", overLimit, "note", -100, null)
            assertTrue(session.transactionWriteMessage.orEmpty().startsWith("Largest amount is"))

            assertFalse(
                session.createTransaction(
                    "budget-1",
                    "account-1",
                    null,
                    null,
                    overLimit,
                    "2026-05-14",
                    "note",
                    null,
                ),
            )
            assertTrue(session.transactionWriteMessage.orEmpty().startsWith("Largest amount is"))
        }

    @Test
    fun readOnlyModeBlocksEveryTransactionWriteEntryPoint() =
        runBlocking {
            val session =
                TandemMobileSession(
                    apiClient = TandemApiClient(),
                    database = null,
                    nowEpochMillis = { 0L },
                )
            session.setReadOnlyMode(true)
            val readOnlyMessage = "Read-only mode is on. Turn it off before changing this data."

            session.updateTransaction("budget-1", "transaction-1", "1.00", "note", -100, null)
            assertEquals(readOnlyMessage, session.transactionWriteMessage)

            assertFalse(
                session.createTransaction(
                    "budget-1",
                    "account-1",
                    null,
                    null,
                    "1.00",
                    "2026-05-14",
                    "note",
                    null,
                ),
            )
            assertEquals(readOnlyMessage, session.transactionWriteMessage)

            session.resolveConflict("budget-1", "operation-1", true, null)
            assertEquals(readOnlyMessage, session.transactionWriteMessage)

            session.discardWrite("budget-1", "operation-1")
            assertEquals(readOnlyMessage, session.transactionWriteMessage)

            session.retryUncertainWrite("budget-1", "operation-1", null)
            assertEquals(readOnlyMessage, session.transactionWriteMessage)
        }

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

    @Test
    fun forgettingThisDeviceLocallyClearsTheSessionAndSavedCredential() =
        runBlocking {
            val savedSession =
                """{"serverUrl":"https://budget.example.test","accessToken":"saved-token","device":{"deviceId":"device-1","principalId":"person-1","budgetIds":["budget-1"]}}"""
            var clearCalls = 0
            val credentialStore =
                object : DeviceCredentialStore {
                    override fun read(): String = savedSession

                    override fun write(value: String) = Unit

                    override fun clear() {
                        clearCalls += 1
                    }
                }
            val apiClient = TandemApiClient()
            val session =
                TandemMobileSession(
                    apiClient = apiClient,
                    database = null,
                    nowEpochMillis = { 0L },
                    credentialStore = credentialStore,
                )

            session.restoreStoredSession()
            assertTrue(session.pairedDevice != null)
            assertTrue(apiClient.isAuthenticated)
            assertTrue(session.isCredentialStored)

            session.forgetDeviceLocally()

            assertNull(session.pairedDevice)
            assertFalse(apiClient.isAuthenticated)
            assertNull(apiClient.currentServerUrl)
            assertFalse(session.isCredentialStored)
            assertNull(session.deviceRevocationError)
            assertEquals(1, clearCalls)
        }
}
