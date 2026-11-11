package app.tandemforactual.ui

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.RemoteBudgetRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineTransactionEditConsolidationTest {
    @Test
    fun aSecondEditAfterAnOfflineSendFailureUpdatesTheSameWaitingOperation() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applyFullSnapshot(
                    budgetId = BUDGET_ID,
                    generation = GENERATION,
                    cursor = "cursor-1",
                    expectedCheckpoint = null,
                    records = listOf(originalTransaction()),
                )
                val session = TandemMobileSession(TandemApiClient(), database, { 100L })

                assertTrue(
                    session.updateTransaction(
                        budgetId = BUDGET_ID,
                        transactionId = TRANSACTION_ID,
                        amountText = "11.00",
                        notes = "first offline edit",
                        originalAmountMinor = -1_000L,
                        transactionType = "Spending",
                    ),
                )
                val first = repository.readPendingWrites(BUDGET_ID).single()
                val sending = repository.claimNextWrite(BUDGET_ID)
                assertEquals(first.operationId, sending?.operationId)
                repository.returnWriteToQueue(first.operationId, "network-unavailable")

                assertTrue(
                    session.updateTransaction(
                        budgetId = BUDGET_ID,
                        transactionId = TRANSACTION_ID,
                        amountText = "12.00",
                        notes = "revised while offline",
                        originalAmountMinor = -1_100L,
                        transactionType = "Spending",
                    ),
                )

                val waiting = repository.readPendingWrites(BUDGET_ID).single()
                val patch = Json.parseToJsonElement(waiting.patchJson).jsonObject
                val optimistic =
                    Json
                        .parseToJsonElement(
                            repository.findRecord(BUDGET_ID, "transaction", TRANSACTION_ID)?.localJson
                                ?: error("Expected the edited transaction overlay"),
                        ).jsonObject

                assertEquals(first.operationId, waiting.operationId)
                assertEquals(first.idempotencyKey, waiting.idempotencyKey)
                assertEquals(1, waiting.attemptCount)
                assertEquals("queued", waiting.status)
                assertEquals(
                    -1_200L,
                    patch
                        .getValue("amount")
                        .jsonPrimitive
                        .content
                        .toLong(),
                )
                assertEquals(
                    "revised while offline",
                    patch
                        .getValue("notes")
                        .jsonPrimitive
                        .content,
                )
                assertEquals(
                    -1_200L,
                    optimistic
                        .getValue("amount")
                        .jsonPrimitive
                        .content
                        .toLong(),
                )
                assertEquals(
                    "revised while offline",
                    optimistic
                        .getValue("notes")
                        .jsonPrimitive
                        .content,
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun anEditThatIsCurrentlySendingCannotBeConsolidated() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applyFullSnapshot(
                    budgetId = BUDGET_ID,
                    generation = GENERATION,
                    cursor = "cursor-1",
                    expectedCheckpoint = null,
                    records = listOf(originalTransaction()),
                )
                val session = TandemMobileSession(TandemApiClient(), database, { 100L })
                assertTrue(
                    session.updateTransaction(
                        budgetId = BUDGET_ID,
                        transactionId = TRANSACTION_ID,
                        amountText = "11.00",
                        notes = "first edit",
                        originalAmountMinor = -1_000L,
                        transactionType = "Spending",
                    ),
                )
                repository.claimNextWrite(BUDGET_ID)

                val saved =
                    session.updateTransaction(
                        budgetId = BUDGET_ID,
                        transactionId = TRANSACTION_ID,
                        amountText = "12.00",
                        notes = "second edit",
                        originalAmountMinor = -1_100L,
                        transactionType = "Spending",
                    )

                assertFalse(saved)
                assertEquals(blockedTransactionEditMessage("sending"), session.transactionWriteMessage)
                assertEquals(1, repository.readPendingWrites(BUDGET_ID).size)
                assertEquals("sending", repository.readPendingWrites(BUDGET_ID).single().status)
            } finally {
                database.close()
            }
        }

    @Test
    fun aSecondEditAfterARemotePullUsesTheLatestActualRevision() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applyFullSnapshot(
                    budgetId = BUDGET_ID,
                    generation = GENERATION,
                    cursor = "cursor-1",
                    expectedCheckpoint = null,
                    records = listOf(originalTransaction()),
                )
                val session = TandemMobileSession(TandemApiClient(), database, { 100L })
                assertTrue(
                    session.updateTransaction(
                        budgetId = BUDGET_ID,
                        transactionId = TRANSACTION_ID,
                        amountText = "11.00",
                        notes = "first offline edit",
                        originalAmountMinor = -1_000L,
                        transactionType = "Spending",
                    ),
                )
                val first = repository.readPendingWrites(BUDGET_ID).single()
                repository.claimNextWrite(BUDGET_ID)
                repository.returnWriteToQueue(first.operationId, "network-unavailable")

                val updatedActual =
                    originalTransaction().copy(
                        revision = "revision-2",
                        bodyJson = originalTransaction().bodyJson.replace("-1000", "-1500"),
                        amountMinor = -1_500L,
                    )
                repository.applyFullSnapshot(
                    budgetId = BUDGET_ID,
                    generation = GENERATION,
                    cursor = "cursor-2",
                    expectedCheckpoint = repository.readSyncCheckpoint(BUDGET_ID),
                    records = listOf(updatedActual),
                )

                assertTrue(
                    session.updateTransaction(
                        budgetId = BUDGET_ID,
                        transactionId = TRANSACTION_ID,
                        amountText = "14.00",
                        notes = "revised after pull",
                        originalAmountMinor = -1_100L,
                        transactionType = "Spending",
                    ),
                )

                val merged = repository.readPendingWrites(BUDGET_ID).single()
                val patch = Json.parseToJsonElement(merged.patchJson).jsonObject
                val rebasedSnapshot =
                    Json
                        .parseToJsonElement(merged.baseSnapshotJson ?: error("Missing edit base"))
                        .jsonObject
                assertEquals(first.operationId, merged.operationId)
                assertEquals("revision-2", merged.baseRevision)
                assertEquals(
                    -1_500L,
                    rebasedSnapshot
                        .getValue("amount")
                        .jsonPrimitive
                        .content
                        .toLong(),
                )
                assertEquals(
                    -1_400L,
                    patch
                        .getValue("amount")
                        .jsonPrimitive
                        .content
                        .toLong(),
                )
                assertEquals("revised after pull", patch.getValue("notes").jsonPrimitive.content)
            } finally {
                database.close()
            }
        }

    @Test
    fun revertingAFailedOfflineEditToActualRemovesTheWaitingEdit() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applyFullSnapshot(
                    budgetId = BUDGET_ID,
                    generation = GENERATION,
                    cursor = "cursor-1",
                    expectedCheckpoint = null,
                    records = listOf(originalTransaction()),
                )
                val session = TandemMobileSession(TandemApiClient(), database, { 100L })
                assertTrue(
                    session.updateTransaction(
                        budgetId = BUDGET_ID,
                        transactionId = TRANSACTION_ID,
                        amountText = "11.00",
                        notes = "offline edit",
                        originalAmountMinor = -1_000L,
                        transactionType = "Spending",
                    ),
                )
                val first = repository.readPendingWrites(BUDGET_ID).single()
                repository.claimNextWrite(BUDGET_ID)
                repository.returnWriteToQueue(first.operationId, "network-unavailable")

                assertTrue(
                    session.updateTransaction(
                        budgetId = BUDGET_ID,
                        transactionId = TRANSACTION_ID,
                        amountText = "10.00",
                        notes = "original",
                        originalAmountMinor = -1_100L,
                        transactionType = "Spending",
                    ),
                )

                assertEquals("discarded", repository.findWrite(first.operationId)?.status)
                val restored = repository.findRecord(BUDGET_ID, "transaction", TRANSACTION_ID)
                assertEquals(originalTransaction().bodyJson, restored?.remoteJson)
                assertNull(repository.findRecord(BUDGET_ID, "transaction", TRANSACTION_ID)?.localJson)
                assertEquals(
                    "Your transaction matches Actual. Removed the waiting edit.",
                    session.transactionWriteMessage,
                )
            } finally {
                database.close()
            }
        }

    private fun newDatabase(): TandemDatabase =
        Room
            .inMemoryDatabaseBuilder<TandemDatabase>()
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private fun originalTransaction() =
        RemoteBudgetRecord(
            budgetId = BUDGET_ID,
            recordType = "transaction",
            recordId = TRANSACTION_ID,
            revision = "revision-1",
            bodyJson = """{"id":"$TRANSACTION_ID","amount":-1000,"date":"2026-11-01","account":"checking","notes":"original"}""",
            generation = GENERATION,
            recordDate = "2026-11-01",
            accountId = "checking",
            amountMinor = -1_000L,
            budgetMonth = "2026-11",
        )

    private companion object {
        const val BUDGET_ID = "budget-1"
        const val GENERATION = "generation-1"
        const val TRANSACTION_ID = "transaction-1"
    }
}
