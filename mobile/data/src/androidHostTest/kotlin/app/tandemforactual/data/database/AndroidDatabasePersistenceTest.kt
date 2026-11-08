package app.tandemforactual.data.database

import android.content.Context
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalRecordProjection
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidDatabasePersistenceTest {
    @Test
    fun queuedWriteSurvivesAndroidDatabaseCloseAndReopen() =
        runBlocking {
            val context: Context = RuntimeEnvironment.getApplication()
            context.deleteDatabase(DATABASE_NAME)
            try {
                val firstDatabase = openAndroidDatabase(context)
                try {
                    LocalBudgetRepository(firstDatabase) { 100L }.enqueueWrite(createRequest())
                } finally {
                    firstDatabase.close()
                }

                val reopenedDatabase = openAndroidDatabase(context)
                try {
                    val repository = LocalBudgetRepository(reopenedDatabase) { 200L }
                    assertEquals("queued", repository.readPendingWrites(BUDGET_ID).single().status)
                    assertNotNull(repository.findRecord(BUDGET_ID, "transaction", RECORD_ID)?.localJson)
                } finally {
                    reopenedDatabase.close()
                }
            } finally {
                context.deleteDatabase(DATABASE_NAME)
            }
            Unit
        }

    @Test
    fun databaseFlowObservesWrites() =
        runBlocking {
            val context: Context = RuntimeEnvironment.getApplication()
            context.deleteDatabase(DATABASE_NAME)
            val database = openAndroidDatabase(context)
            val initialQueryObserved = CompletableDeferred<Unit>()
            try {
                val visibleRecords =
                    async(Dispatchers.IO) {
                        LocalBudgetRepository(database) { 100L }
                            .observeRecords(BUDGET_ID, "transaction")
                            .onEach { records -> if (records.isEmpty()) initialQueryObserved.complete(Unit) }
                            .first { records -> records.isNotEmpty() }
                    }
                withTimeout(5_000) { initialQueryObserved.await() }

                database
                    .budgetRecordDao()
                    .put(
                        BudgetRecordEntity(
                            budgetId = BUDGET_ID,
                            recordType = "transaction",
                            recordId = "shared-transaction",
                            remoteJson = """{"id":"shared-transaction"}""",
                            remoteRevision = "revision-1",
                            localJson = null,
                            localDeleted = false,
                            remoteDeleted = false,
                            syncGeneration = "generation-1",
                            recordDate = "2026-11-03",
                            accountId = null,
                            categoryId = null,
                            payeeId = null,
                            amountMinor = null,
                            budgetMonth = null,
                        ),
                    )

                assertEquals("shared-transaction", withTimeout(5_000) { visibleRecords.await() }.single().recordId)
            } finally {
                database.close()
                context.deleteDatabase(DATABASE_NAME)
            }
            Unit
        }

    private fun createRequest() =
        LocalWriteRequest(
            operationId = "android-db-operation",
            idempotencyKey = "android-db-idempotency",
            budgetId = BUDGET_ID,
            recordType = "transaction",
            recordId = RECORD_ID,
            action = LocalWriteAction.CREATE,
            baseRevision = null,
            baseSnapshotJson = null,
            patchJson = "{\"amount\":1200}",
            optimisticJson = "{\"id\":\"$RECORD_ID\",\"amount\":1200}",
            createdAtEpochMs = 100L,
            optimisticProjection = LocalRecordProjection("2026-11-03", "checking", "groceries", "market", 1200L, null),
        )

    private fun openAndroidDatabase(context: Context): TandemDatabase =
        tandemDatabaseBuilder(context).setDriver(AndroidSQLiteDriver()).build()

    private companion object {
        const val DATABASE_NAME = "tandem.db"
        const val BUDGET_ID = "android-database-integration"
        const val RECORD_ID = "android-database-transaction"
    }
}
