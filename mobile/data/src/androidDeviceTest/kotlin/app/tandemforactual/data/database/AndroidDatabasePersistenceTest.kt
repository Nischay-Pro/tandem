package app.tandemforactual.data.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class AndroidDatabasePersistenceTest {
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
        )

    private companion object {
        const val DATABASE_NAME = "tandem.db"
        const val BUDGET_ID = "android-database-integration"
        const val RECORD_ID = "android-database-transaction"
    }
}
