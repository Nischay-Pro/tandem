package app.tandemforactual.ui

import app.tandemforactual.ui.core.session.AutomaticBackupResult
import app.tandemforactual.ui.core.session.AutomaticBackupSettings
import app.tandemforactual.ui.core.session.AutomaticBackups
import app.tandemforactual.ui.core.session.BackupDestination
import app.tandemforactual.ui.core.session.BackupProblem
import app.tandemforactual.ui.core.session.BackupProblemException
import app.tandemforactual.ui.core.session.BackupSecrets
import app.tandemforactual.ui.core.session.DeviceCredentialStore
import app.tandemforactual.ui.core.session.SyncNetwork
import app.tandemforactual.ui.core.session.automaticBackupSummary
import app.tandemforactual.ui.core.state.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class AutomaticBackupsTest {
    private val on = AutomaticBackupSettings(enabled = true, intervalHours = 24, folder = "content://tree/Documents")
    private val hour = 3_600_000L

    @Test
    fun backsUpOnceTheIntervalPassesAndOnlyWithAFolder() {
        assertTrue(AutomaticBackups.due(on, null, 0))
        assertFalse(AutomaticBackups.due(on, 10 * hour, 33 * hour))
        assertTrue(AutomaticBackups.due(on, 10 * hour, 34 * hour))
        assertFalse(AutomaticBackups.due(on.copy(enabled = false), null, 0))
        assertFalse(AutomaticBackups.due(on.copy(folder = null), null, 0))
        assertTrue(AutomaticBackups.due(on.copy(folder = null, webDavUrl = "https://cloud.example/dav"), null, 0))
    }

    @Test
    fun namesBackupsAndKeepsOnlyTheNewest() {
        assertEquals(
            "tandem-backup-our-home-2026-11-24-0930.zip",
            AutomaticBackups.fileName("Our Home!", "id-1", "2026-11-24-0930", sealed = false),
        )
        assertEquals(
            "tandem-backup-budget-2026-11-24-0930.tandembackup",
            AutomaticBackups.fileName("  ", "id-1", "2026-11-24-0930", sealed = true),
        )
        val names =
            listOf(
                "tandem-backup-our-home-2026-11-20-0900.zip",
                "tandem-backup-our-home-2026-11-22-0900.tandembackup",
                "tandem-backup-our-home-2026-11-24-0900.zip",
                "tandem-backup-other-2026-11-01-0900.zip",
                "notes.txt",
            )
        assertEquals(listOf("tandem-backup-our-home-2026-11-20-0900.zip"), AutomaticBackups.toDelete(names, "Our Home!", "id-1", keep = 2))
        assertEquals(emptyList(), AutomaticBackups.toDelete(names, "Our Home!", "id-1", keep = 7))
    }

    @Test
    fun leavesOtherBudgetsBackupsAlone() {
        val names =
            listOf(
                "tandem-backup-home-2026-11-20-0900.zip",
                "tandem-backup-home-2026-11-24-0900.zip",
                "tandem-backup-home-office-2026-11-25-0900.zip",
                "tandem-backup-home-office-2026-11-26-0900.zip",
                "tandem-backup-home-2026-11-24-0900-copy.zip",
            )
        assertEquals(listOf("tandem-backup-home-2026-11-20-0900.zip"), AutomaticBackups.toDelete(names, "Home", "a", keep = 1))
    }

    @Test
    fun givesLongNamesSharingAPrefixTheirOwnFiles() {
        val first = "Household budget for the whole family 2025 edition"
        val second = "Household budget for the whole family 2026 edition"
        val firstName = AutomaticBackups.fileName(first, "budget-a", "2026-11-24-0930", sealed = false)
        val secondName = AutomaticBackups.fileName(second, "budget-b", "2026-11-25-0930", sealed = false)
        assertEquals(firstName, AutomaticBackups.fileName(first, "budget-a", "2026-11-24-0930", sealed = false))
        val names = listOf(firstName, secondName)
        assertEquals(listOf(firstName), AutomaticBackups.toDelete(names, first, "budget-a", keep = 0))
        assertEquals(listOf(secondName), AutomaticBackups.toDelete(names, second, "budget-b", keep = 0))
    }

    @Test
    fun neverCountsPartialCopiesAndCleansThemUp() {
        val names =
            listOf(
                "tandem-backup-home-2026-11-20-0900.zip",
                "tandem-backup-home-2026-11-22-0900.zip",
                "tandem-backup-home-2026-11-26-0900.zip.partial",
                "tandem-backup-home-2026-11-26-0910.zip.partial",
                "tandem-backup-home-office-2026-11-26-0900.zip.partial",
            )
        assertEquals(
            listOf("tandem-backup-home-2026-11-26-0900.zip.partial", "tandem-backup-home-2026-11-26-0910.zip.partial"),
            AutomaticBackups.toDelete(names, "Home", "a", keep = 2),
        )
    }

    @Test
    fun savesUnderAPartialNameAndRemovesItWhenTheWriteFails(): Unit =
        runBlocking {
            val folder = mutableMapOf<String, ByteArray>()
            val rename: suspend (String, String) -> Unit = { from, to -> folder[to] = folder.remove(from)!! }
            val delete: suspend (String) -> Unit = { folder.remove(it) }
            AutomaticBackups.saveCompletely("b.zip", { folder[it] = byteArrayOf(1) }, rename, delete)
            assertEquals(setOf("b.zip"), folder.keys)
            assertFailsWith<IllegalStateException> {
                AutomaticBackups.saveCompletely(
                    "c.zip",
                    {
                        folder[it] = byteArrayOf(2)
                        error("disk full")
                    },
                    rename,
                    delete,
                )
            }
            assertFailsWith<IllegalStateException> {
                AutomaticBackups.saveCompletely("d.zip", { folder[it] = byteArrayOf(3) }, { _, _ -> error("rename failed") }, delete)
            }
            assertEquals(setOf("b.zip"), folder.keys)
        }

    @Test
    fun savesTheChoicesWithTheAppSettings() {
        val chosen =
            on.copy(
                intervalHours = 168,
                network = SyncNetwork.WIFI,
                keep = 14,
                folderLabel = "Documents",
                webDavUrl = "https://dav",
                webDavUser = "sam",
            )
        assertEquals(chosen, AutomaticBackupSettings.from(chosen.toPreferences()))
        assertEquals(AutomaticBackupSettings(), AutomaticBackupSettings.from(mapOf("autoBackupKeep" to "0", "autoBackupNetwork" to "x")))
        val state = AppState()
        state.automaticBackup = chosen
        val restored = AppState()
        restored.restorePersistentSettings(state.persistentSettings() + (AutomaticBackupSettings.LAST_RUN to "42"))
        assertEquals(chosen, restored.automaticBackup)
        assertEquals(42L, restored.automaticBackupLastRun)
    }

    private class FlakyStore(
        var value: String? = null,
        var broken: Boolean = false,
    ) : DeviceCredentialStore {
        override fun read(): String? = if (broken) error("keystore unavailable") else value

        override fun write(value: String) {
            if (broken) error("keystore unavailable")
            this.value = value
        }

        override fun clear() {
            if (broken) error("keystore unavailable")
            value = null
        }
    }

    @Test
    fun treatsAnUnreadablePassphraseAsSetAndReportsFailedSaves() {
        val store = FlakyStore()
        val secrets = BackupSecrets(store, FlakyStore())
        assertFalse(secrets.hasPassphrase())
        assertTrue(secrets.setPassphrase("secret"))
        assertTrue(secrets.hasPassphrase())
        store.broken = true
        assertTrue(secrets.hasPassphrase())
        assertFalse(secrets.setPassphrase(null))
        assertFalse(secrets.setPassphrase("other"))
        store.broken = false
        assertEquals("secret", store.value)
        assertTrue(secrets.setPassphrase(null))
        assertFalse(secrets.hasPassphrase())
    }

    @Test
    fun reportsAFailedWebDavPasswordSave() {
        val store = FlakyStore(broken = true)
        val secrets = BackupSecrets(FlakyStore(), store)
        assertFalse(secrets.setWebDavPassword("pw"))
        store.broken = false
        assertTrue(secrets.setWebDavPassword("pw"))
        assertEquals("pw", store.value)
    }

    @Test
    fun aMissingWebDavPasswordFailsTheRunWithoutMovingTheLastBackup() =
        runBlocking {
            val result =
                AutomaticBackupResult.deliver(toFolder = null, toWebDav = { throw BackupProblemException(BackupProblem.WEBDAV_PASSWORD) })
            assertEquals(AutomaticBackupResult.failed(BackupProblem.WEBDAV_PASSWORD), result)
            val saved = result.preferences(now = 42)
            assertNull(saved[AutomaticBackupSettings.LAST_RUN])
            val state = AppState()
            state.automaticBackup = on.copy(folder = null, webDavUrl = "https://dav")
            state.restoreAutomaticBackupStatus(saved)
            assertNull(state.automaticBackupLastRun)
            assertEquals(
                "Last backup failed: Tandem can’t read the WebDAV password. Set up WebDAV again.",
                automaticBackupSummary(state.automaticBackup, null, state.automaticBackupResult),
            )
        }

    @Test
    fun keepsTheFolderCopyWhenWebDavFailsAndSaysSo() =
        runBlocking {
            val result = AutomaticBackupResult.deliver(toFolder = {}, toWebDav = { error("503") })
            assertEquals(setOf(BackupDestination.FOLDER), result.savedTo)
            assertEquals(BackupProblem.WEBDAV, result.problem)
            val saved = result.preferences(now = 42)
            assertEquals("42", saved[AutomaticBackupSettings.LAST_RUN])
            assertEquals(result, AutomaticBackupResult.decode(saved[AutomaticBackupResult.KEY]))
            val settings = on.copy(webDavUrl = "https://dav")
            assertEquals("Saved to the folder; WebDAV upload failed", automaticBackupSummary(settings, "today", result))
            val lost = AutomaticBackupResult.deliver(toFolder = { error("permission revoked") }, toWebDav = null)
            assertEquals("Last backup failed: Tandem can’t reach the backup folder", automaticBackupSummary(on, "today", lost))
            val both = AutomaticBackupResult.deliver(toFolder = {}, toWebDav = {})
            assertEquals("Last backup today", automaticBackupSummary(settings, "today", both))
        }

    @Test
    fun recordsASkippedRunWithItsReason() {
        val skipped = AutomaticBackupResult.skipped(BackupProblem.SIGNED_OUT)
        val saved = skipped.preferences(now = 42)
        assertNull(saved[AutomaticBackupSettings.LAST_RUN])
        val restored = AppState()
        restored.restorePersistentSettings(on.toPreferences() + saved + (AutomaticBackupSettings.LAST_RUN to "7"))
        assertEquals(skipped, restored.automaticBackupResult)
        assertEquals(7L, restored.automaticBackupLastRun)
        assertEquals("Skipped: sign in to back up", automaticBackupSummary(on, "today", restored.automaticBackupResult))
        assertNull(AutomaticBackupResult.decode("nonsense"))
    }

    @Test
    fun neverWritesAnEncryptedBudgetUnsealed() {
        assertTrue(AutomaticBackups.needsPassphrase(budgetEncrypted = true, hasPassphrase = false))
        assertFalse(AutomaticBackups.needsPassphrase(budgetEncrypted = true, hasPassphrase = true))
        assertFalse(AutomaticBackups.needsPassphrase(budgetEncrypted = false, hasPassphrase = false))
        assertFalse(AutomaticBackups.mayWrite(budgetEncrypted = true, sealed = false))
        assertTrue(AutomaticBackups.mayWrite(budgetEncrypted = true, sealed = true))
        assertTrue(AutomaticBackups.mayWrite(budgetEncrypted = false, sealed = false))
        val skipped = AutomaticBackupResult.skipped(BackupProblem.NEEDS_PASSPHRASE)
        assertEquals(skipped, AutomaticBackupResult.decode(skipped.encode()))
        assertNull(skipped.preferences(now = 42)[AutomaticBackupSettings.LAST_RUN])
        assertEquals(
            "Skipped: set a backup passphrase for this encrypted budget",
            automaticBackupSummary(on, "today", skipped),
        )
    }
}
