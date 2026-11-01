package org.actualbudget.companion.data.database

import kotlinx.coroutines.runBlocking
import org.actualbudget.companion.data.store.LocalBudgetRepository
import org.actualbudget.companion.data.store.LocalWriteAction
import org.actualbudget.companion.data.store.LocalWriteRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IosDatabasePersistenceTest {
    @Test
    fun queuedWriteSurvivesIosDatabaseCloseAndReopen() =
        runBlocking {
            val firstDatabase = openCompanionDatabase(companionDatabaseBuilder())
            try {
                firstDatabase.clearAllTables()
                LocalBudgetRepository(firstDatabase) { 100L }.enqueueWrite(createRequest())
            } finally {
                firstDatabase.close()
            }

            val reopenedDatabase = openCompanionDatabase(companionDatabaseBuilder())
            try {
                val repository = LocalBudgetRepository(reopenedDatabase) { 200L }
                assertEquals("queued", repository.readPendingWrites(BUDGET_ID).single().status)
                assertNotNull(repository.findRecord(BUDGET_ID, "transaction", RECORD_ID)?.localJson)
            } finally {
                reopenedDatabase.clearAllTables()
                reopenedDatabase.close()
            }
            Unit
        }

    private fun createRequest() =
        LocalWriteRequest(
            operationId = "ios-db-operation",
            idempotencyKey = "ios-db-idempotency",
            budgetId = BUDGET_ID,
            recordType = "transaction",
            recordId = RECORD_ID,
            action = LocalWriteAction.CREATE,
            baseRevision = null,
            baseSnapshotJson = null,
            patchJson = "{\"amount\":1200}",
            optimisticJson = "{\"id\":\"$RECORD_ID\",\"amount\":1200}",
            createdAtEpochMs = 100L,
        )

    private companion object {
        const val BUDGET_ID = "ios-database-integration"
        const val RECORD_ID = "ios-database-transaction"
    }
}
