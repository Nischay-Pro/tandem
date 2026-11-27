package app.tandemforactual.ui

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.planning.BudgetBackupFile
import app.tandemforactual.ui.core.session.BackupSecrets
import app.tandemforactual.ui.core.session.DeviceCredentialStore
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.backupLog
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.feature.export.BackupEncryption
import app.tandemforactual.ui.feature.export.BackupFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

internal class MemoryCredentialStore(
    var value: String? = null,
) : DeviceCredentialStore {
    override fun read(): String? = value

    override fun write(value: String) {
        this.value = value
    }

    override fun clear() {
        value = null
    }
}

internal val TEST_DATABASE = "SQLite format 3\u0000".encodeToByteArray()

@RunWith(RobolectricTestRunner::class)
class PlanningBackupShareTest {
    private class Shared(
        val name: String,
        val mimeType: String,
        val bytes: ByteArray,
    )

    private fun backUp(
        encrypted: Boolean,
        stored: String?,
        build: (String?) -> ByteArray = { BudgetBackupFile.build(TEST_DATABASE, "{}", null, it) },
        afterAsking: (BackupEncryption) -> Unit = {},
    ): Triple<List<Shared>, List<InlineMessageKind>, AppState> =
        runBlocking {
            val state =
                AppState().apply {
                    selectedBudgetId = "b1"
                    selectedBudget = "Home"
                    selectedBudgetEncrypted = encrypted
                }
            val encryption = BackupEncryption(BackupSecrets(MemoryCredentialStore(stored), MemoryCredentialStore()), encrypted)
            val shared = mutableListOf<Shared>()
            val reports = mutableListOf<InlineMessageKind>()
            coroutineScope {
                val session = TandemMobileSession(TandemApiClient(), null, { 100L })
                val flow =
                    BackupFlow(state, session, this, encryption, { n, m, b -> shared += Shared(n, m, b) }, { reports += it.second }) {
                        build(it)
                    }
                flow.backUpNow()
                afterAsking(encryption)
            }
            Triple(shared, reports, state)
        }

    @Test
    fun aPlainBudgetSharesOneZipWithBothPartsAndRecordsTheDay() {
        val (shared, reports, state) = backUp(encrypted = false, stored = null)
        val file = shared.single()
        assertTrue(file.name.startsWith("tandem-backup-home-") && file.name.endsWith(".zip"))
        assertEquals("application/zip", file.mimeType)
        assertTrue(reports.isEmpty())
        assertTrue(state.backupLog.sharedOn != null)
    }

    @Test
    fun theStoredPassphraseEncryptsEveryBackup() {
        val (shared) = backUp(encrypted = false, stored = "correct horse")
        assertTrue(BudgetBackupFile.isSealed(shared.single().bytes))
        assertTrue(shared.single().name.endsWith(".tandembackup"))
    }

    @Test
    fun anEncryptedBudgetSetsAPassphraseBeforeItsFirstBackup() {
        var asked = false
        val (shared) =
            backUp(encrypted = true, stored = null) { encryption ->
                asked = encryption.settingPassphrase
                encryption.closePassphrase("correct horse")
            }
        assertTrue(asked)
        assertTrue(BudgetBackupFile.isSealed(shared.single().bytes))
    }

    @Test
    fun anEncryptedBudgetIsNeverSharedUnencrypted() {
        val unsealed = { _: String? -> BudgetBackupFile.build(TEST_DATABASE, "{}", null, null) }
        val (shared, reports, state) = backUp(encrypted = true, stored = "correct horse", build = unsealed)
        assertTrue(shared.isEmpty())
        assertEquals(listOf(InlineMessageKind.ERROR), reports)
        assertNull(state.backupLog.sharedOn)
    }
}

internal fun newPlanningTestDatabase(): TandemDatabase =
    Room
        .inMemoryDatabaseBuilder<TandemDatabase>()
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
