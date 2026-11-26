package app.tandemforactual.ui

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.feature.export.BackupFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlanningBackupShareTest {
    @Test
    fun openingTheShareSheetDoesNotRecordABackup(): Unit =
        runBlocking {
            val database = newPlanningTestDatabase()
            try {
                val session = TandemMobileSession(TandemApiClient(), database, { 100L })
                val state = AppState().apply { selectedBudgetId = "b1" }
                val shared = mutableListOf<String>()
                coroutineScope {
                    val flow = BackupFlow(state, session, this, { name, _, _ -> shared += name }, {})
                    flow.startBackup()
                    flow.submit("passphrase1")
                }
                assertEquals(1, shared.size)
                assertFalse(state.backupLogJson.contains("backedUpOn"))
            } finally {
                database.close()
            }
        }
}

internal fun newPlanningTestDatabase(): TandemDatabase =
    Room
        .inMemoryDatabaseBuilder<TandemDatabase>()
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
