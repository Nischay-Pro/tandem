package app.tandemforactual.ui

import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.BudgetSyncSummary
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionConflictResolutionTest {
    @Test
    fun keepingActualDiscardsTheEditOnlyAfterServerConfirmsTheConflict() =
        runBlocking {
            val store = FakeStore()
            val sync = FakeSync(store)

            val message = workflow(store, sync).resolve(BUDGET, CONFLICT_ID, keepDeviceVersion = false, encryptionPassword = null)

            assertTrue(sync.confirmationRequested)
            assertEquals("discarded", store.writes[CONFLICT_ID]?.status)
            assertTrue(store.requests.isEmpty())
            assertEquals(0, sync.syncCalls)
            assertEquals("The server confirmed Actual’s latest version. Tandem kept that version and removed your edit.", message)
        }

    @Test
    fun serverFailureLeavesTheOriginalConflictAndItsEditUntouched() =
        runBlocking {
            val store = FakeStore()
            val sync = FakeSync(store, confirmationResult = false)

            val message = workflow(store, sync).resolve(BUDGET, CONFLICT_ID, keepDeviceVersion = false, encryptionPassword = null)

            assertEquals("conflict", store.writes[CONFLICT_ID]?.status)
            assertTrue(store.requests.isEmpty())
            assertEquals(0, sync.syncCalls)
            assertTrue(message.contains("The server hasn’t confirmed your choice"))
            assertTrue(message.contains("Your edit remains on this device"))
            assertTrue(message.contains("Check the connection and try again"))
        }

    @Test
    fun keepingEditRebasesItsPatchOnTheLatestActualSnapshotAndConfirmsTheWrite() =
        runBlocking {
            val store = FakeStore()
            val sync = FakeSync(store)

            val message = workflow(store, sync).resolve(BUDGET, CONFLICT_ID, keepDeviceVersion = true, encryptionPassword = null)

            val request = store.requests.single()
            val rebased = Json.parseToJsonElement(checkNotNull(request.optimisticJson)).jsonObject
            assertEquals("revision-latest", request.baseRevision)
            assertEquals(store.record.remoteJson, request.baseSnapshotJson)
            assertEquals("""{"amount":1200}""", request.patchJson)
            assertEquals(1200L, rebased.getValue("amount").jsonPrimitive.longOrNull)
            assertEquals("Actual’s newer note", rebased.getValue("notes").jsonPrimitive.content)
            assertEquals(CONFLICT_ID, request.supersedesConflictOperationId)
            assertEquals("superseded", store.writes[CONFLICT_ID]?.status)
            assertEquals("committed", store.writes[REPLACEMENT_ID]?.status)
            assertEquals("The server confirmed your edit in Actual.", message)
        }

    @Test
    fun keepingDeletionRetriesAgainstTheLatestActualRevision() =
        runBlocking {
            val store = FakeStore(action = LocalWriteAction.DELETE.name)
            val sync = FakeSync(store)

            val message = workflow(store, sync).resolve(BUDGET, CONFLICT_ID, keepDeviceVersion = true, encryptionPassword = null)

            val request = store.requests.single()
            assertEquals(LocalWriteAction.DELETE, request.action)
            assertEquals("revision-latest", request.baseRevision)
            assertEquals("{}", request.patchJson)
            assertEquals(store.record.remoteJson, request.optimisticJson)
            assertEquals("The server confirmed the deletion in Actual.", message)
        }

    @Test
    fun alreadyDeletedTransactionClearsPendingDeletionWithoutAnotherWrite() =
        runBlocking {
            val store = FakeStore(action = LocalWriteAction.DELETE.name, remoteDeleted = true)
            val sync = FakeSync(store)

            val message = workflow(store, sync).resolve(BUDGET, CONFLICT_ID, keepDeviceVersion = true, encryptionPassword = null)

            assertEquals("discarded", store.writes[CONFLICT_ID]?.status)
            assertTrue(store.requests.isEmpty())
            assertEquals(0, sync.syncCalls)
            assertTrue(message.contains("Tandem removed the waiting deletion"))
        }

    @Test
    fun editCannotBeReappliedAfterActualDeletedTheTransaction() =
        runBlocking {
            val store = FakeStore(remoteDeleted = true)
            val sync = FakeSync(store)

            val message = workflow(store, sync).resolve(BUDGET, CONFLICT_ID, keepDeviceVersion = true, encryptionPassword = null)

            assertEquals("conflict", store.writes[CONFLICT_ID]?.status)
            assertTrue(store.requests.isEmpty())
            assertTrue(message.contains("Sync the budget and try again"))
        }

    @Test
    fun aSecondConflictKeepsTheRebasedEditAvailableForReview() =
        runBlocking {
            val store = FakeStore()
            val sync = FakeSync(store, writeStatusAfterSync = "conflict")

            val message = workflow(store, sync).resolve(BUDGET, CONFLICT_ID, keepDeviceVersion = true, encryptionPassword = null)

            assertEquals("superseded", store.writes[CONFLICT_ID]?.status)
            assertEquals("conflict", store.writes[REPLACEMENT_ID]?.status)
            assertTrue(message.contains("Review the versions again"))
        }

    @Test
    fun failedSyncLeavesTheRebasedEditQueuedAndSupersedesTheOldConflict() =
        runBlocking {
            val store = FakeStore()
            val sync = FakeSync(store, syncResult = null, writeStatusAfterSync = null)

            val message = workflow(store, sync).resolve(BUDGET, CONFLICT_ID, keepDeviceVersion = true, encryptionPassword = null)

            assertEquals("superseded", store.writes[CONFLICT_ID]?.status)
            assertEquals("queued", store.writes[REPLACEMENT_ID]?.status)
            assertTrue(message.contains("Check the connection and try again"))
        }

    private fun workflow(
        store: FakeStore,
        sync: FakeSync,
    ) = TransactionConflictResolution(
        store = store,
        sync = sync,
        nowEpochMillis = { 123L },
        newOperationId = { REPLACEMENT_ID },
    )

    private inner class FakeStore(
        action: String = LocalWriteAction.UPDATE.name,
        remoteDeleted: Boolean = false,
    ) : ConflictResolutionStore {
        val writes = mutableMapOf(CONFLICT_ID to conflictedWrite(action))
        val requests = mutableListOf<LocalWriteRequest>()
        val record = latestRecord(remoteDeleted)

        override suspend fun readPendingWrites(budgetId: String): List<OutboxOperationEntity> =
            writes.values.filter { it.budgetId == budgetId }

        override suspend fun findRecord(
            budgetId: String,
            recordType: String,
            recordId: String,
        ): BudgetRecordEntity? = record.takeIf { it.budgetId == budgetId && it.recordType == recordType && it.recordId == recordId }

        override suspend fun discardWrite(operationId: String) {
            writes[operationId] = checkNotNull(writes[operationId]).copy(status = "discarded")
        }

        override suspend fun enqueueWrite(request: LocalWriteRequest) {
            requests += request
            request.supersedesConflictOperationId?.let { oldId ->
                writes[oldId] = checkNotNull(writes[oldId]).copy(status = "superseded")
            }
            writes[request.operationId] = request.toOutboxOperation()
        }

        override suspend fun findWrite(operationId: String): OutboxOperationEntity? = writes[operationId]
    }

    private inner class FakeSync(
        private val store: FakeStore,
        private val confirmationResult: Boolean = true,
        private val syncResult: BudgetSyncSummary? = successfulSync,
        private val writeStatusAfterSync: String? = "committed",
    ) : ConflictResolutionSync {
        var confirmationRequested = false
        var syncCalls = 0

        override suspend fun confirmConflictWithServer(
            budgetId: String,
            operationId: String,
            encryptionPassword: String?,
        ): Boolean {
            confirmationRequested = true
            return confirmationResult
        }

        override suspend fun synchronize(
            budgetId: String,
            encryptionPassword: String?,
            retryQueuedBudgetMutations: Boolean,
        ): BudgetSyncSummary? {
            syncCalls += 1
            assertFalse(retryQueuedBudgetMutations)
            if (writeStatusAfterSync != null) {
                store.writes[REPLACEMENT_ID] =
                    checkNotNull(store.writes[REPLACEMENT_ID]).copy(status = writeStatusAfterSync)
            }
            return syncResult
        }
    }

    private fun conflictedWrite(action: String) =
        OutboxOperationEntity(
            operationId = CONFLICT_ID,
            idempotencyKey = "old-key",
            budgetId = BUDGET,
            recordType = "transaction",
            recordId = "transaction-1",
            action = action,
            baseRevision = "revision-old",
            baseSnapshotJson = """{"id":"transaction-1","amount":1000}""",
            patchJson = if (action == LocalWriteAction.DELETE.name) "{}" else """{"amount":1200}""",
            optimisticJson = """{"id":"transaction-1","amount":1200}""",
            optimisticRecordDate = "2026-11-05",
            optimisticAccountId = "checking",
            optimisticCategoryId = "food",
            optimisticPayeeId = "market",
            optimisticAmountMinor = 1200L,
            optimisticBudgetMonth = "2026-11",
            status = "conflict",
            attemptCount = 1,
            serverOperationId = "server-operation-1",
            lastErrorCode = "concurrent-edit",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 2L,
        )

    private fun latestRecord(remoteDeleted: Boolean) =
        BudgetRecordEntity(
            budgetId = BUDGET,
            recordType = "transaction",
            recordId = "transaction-1",
            remoteJson =
                """{"id":"transaction-1","amount":1500,"date":"2026-11-05","account":"checking","category":"food","payee":"market","month":"2026-11","notes":"Actual’s newer note"}""",
            remoteRevision = "revision-latest",
            localJson = """{"id":"transaction-1","amount":1200}""",
            localDeleted = false,
            remoteDeleted = remoteDeleted,
            syncGeneration = "generation-1",
            recordDate = "2026-11-05",
            accountId = "checking",
            categoryId = "food",
            payeeId = "market",
            amountMinor = 1500L,
            budgetMonth = "2026-11",
        )

    private fun LocalWriteRequest.toOutboxOperation() =
        OutboxOperationEntity(
            operationId = operationId,
            idempotencyKey = idempotencyKey,
            budgetId = budgetId,
            recordType = recordType,
            recordId = recordId,
            action = action.name,
            baseRevision = baseRevision,
            baseSnapshotJson = baseSnapshotJson,
            patchJson = patchJson,
            optimisticJson = optimisticJson,
            optimisticRecordDate = optimisticProjection?.recordDate,
            optimisticAccountId = optimisticProjection?.accountId,
            optimisticCategoryId = optimisticProjection?.categoryId,
            optimisticPayeeId = optimisticProjection?.payeeId,
            optimisticAmountMinor = optimisticProjection?.amountMinor,
            optimisticBudgetMonth = optimisticProjection?.budgetMonth,
            status = "queued",
            attemptCount = 0,
            serverOperationId = null,
            lastErrorCode = null,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = createdAtEpochMs,
        )

    companion object {
        private const val BUDGET = "budget-1"
        private const val CONFLICT_ID = "conflict-operation"
        private const val REPLACEMENT_ID = "replacement-operation"
        private val successfulSync = BudgetSyncSummary(false, 0, 0, 1, 0, 0, 0)
    }
}
