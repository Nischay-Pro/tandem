package app.tandemforactual.ui

import app.tandemforactual.ui.core.session.AutomaticBackupSettings
import app.tandemforactual.ui.core.session.AutomaticBackups
import app.tandemforactual.ui.core.session.SyncNetwork
import app.tandemforactual.ui.core.state.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
            AutomaticBackups.fileName("Our Home!", "2026-11-24-0930", sealed = false),
        )
        assertEquals("tandem-backup-budget-2026-11-24-0930.tandembackup", AutomaticBackups.fileName("  ", "2026-11-24-0930", sealed = true))
        val names =
            listOf(
                "tandem-backup-our-home-2026-11-20-0900.zip",
                "tandem-backup-our-home-2026-11-22-0900.tandembackup",
                "tandem-backup-our-home-2026-11-24-0900.zip",
                "tandem-backup-other-2026-11-01-0900.zip",
                "notes.txt",
            )
        assertEquals(listOf("tandem-backup-our-home-2026-11-20-0900.zip"), AutomaticBackups.toDelete(names, "Our Home!", keep = 2))
        assertEquals(emptyList(), AutomaticBackups.toDelete(names, "Our Home!", keep = 7))
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
}
