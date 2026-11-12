package app.tandemforactual.data.database

import android.content.Context
import androidx.room3.useWriterConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidDatabasePersistenceTest {
    @Test
    fun fullLocalDatabaseDoesNotPartiallyQueueWriteAndRemainsUsable() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.deleteDatabase(DATABASE_NAME)
            val database = openTandemDatabase(tandemDatabaseBuilder(context))
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.enqueueWrite(createRequest())
                val pages =
                    database.useWriterConnection { connection ->
                        connection.usePrepared("PRAGMA page_count") { statement ->
                            assertTrue(statement.step())
                            statement.getLong(0)
                        }
                    }
                database.useWriterConnection { connection ->
                    connection.usePrepared("PRAGMA max_page_count = ${pages + 1}") { statement ->
                        assertTrue(statement.step())
                    }
                }

                val fullDiskError =
                    runCatching {
                        repository.enqueueWrite(
                            createRequest().copy(
                                operationId = "android-db-full-operation",
                                idempotencyKey = "android-db-full-idempotency",
                                recordId = "android-db-full-transaction",
                                patchJson = """{"notes":"${"x".repeat(512 * 1024)}"}""",
                                optimisticJson = """{"id":"android-db-full-transaction","notes":"${"x".repeat(512 * 1024)}"}""",
                            ),
                        )
                    }.exceptionOrNull()
                assertNotNull(fullDiskError)
                assertTrue(fullDiskError.message.orEmpty().contains("full", ignoreCase = true))

                assertEquals(listOf("android-db-operation"), repository.readPendingWrites(BUDGET_ID).map { it.operationId })
                assertNull(repository.findRecord(BUDGET_ID, "transaction", "android-db-full-transaction"))

                database.useWriterConnection { connection ->
                    connection.usePrepared("PRAGMA max_page_count = ${pages + 1_000}") { statement ->
                        assertTrue(statement.step())
                    }
                }
                repository.enqueueWrite(
                    createRequest().copy(
                        operationId = "android-db-recovered-operation",
                        idempotencyKey = "android-db-recovered-idempotency",
                        recordId = "android-db-recovered-transaction",
                        optimisticJson = """{"id":"android-db-recovered-transaction","amount":1200}""",
                    ),
                )
                assertEquals(2, repository.readPendingWrites(BUDGET_ID).size)
            } finally {
                database.close()
                context.deleteDatabase(DATABASE_NAME)
            }
            Unit
        }

    @Test
    fun queuedWriteSurvivesAndroidDatabaseCloseAndReopen() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.deleteDatabase(DATABASE_NAME)
            try {
                val firstDatabase = openTandemDatabase(tandemDatabaseBuilder(context))
                try {
                    LocalBudgetRepository(firstDatabase) { 100L }.enqueueWrite(createRequest())
                } finally {
                    firstDatabase.close()
                }

                val reopenedDatabase = openTandemDatabase(tandemDatabaseBuilder(context))
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
    fun appAndWorkerDatabaseInstancesShareInvalidationEvents() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.deleteDatabase(DATABASE_NAME)
            val appDatabase = openTandemDatabase(tandemDatabaseBuilder(context))
            val workerDatabase = openTandemDatabase(tandemDatabaseBuilder(context))
            val initialQueryObserved = CompletableDeferred<Unit>()
            try {
                val visibleRecords =
                    async(Dispatchers.IO) {
                        LocalBudgetRepository(workerDatabase) { 100L }
                            .observeRecords(BUDGET_ID, "transaction")
                            .onEach { records -> if (records.isEmpty()) initialQueryObserved.complete(Unit) }
                            .first { records -> records.isNotEmpty() }
                    }
                withTimeout(5_000) { initialQueryObserved.await() }

                appDatabase
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
                workerDatabase.close()
                appDatabase.close()
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

    private companion object {
        const val DATABASE_NAME = "tandem.db"
        const val BUDGET_ID = "android-database-integration"
        const val RECORD_ID = "android-database-transaction"
    }
}
