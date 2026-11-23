package app.tandemforactual.ui

import app.tandemforactual.data.planning.PlanningBackupSeal
import app.tandemforactual.ui.core.state.BackupLog
import app.tandemforactual.ui.core.state.decodeBackupLogs
import app.tandemforactual.ui.core.state.encodeBackupLogs
import app.tandemforactual.ui.feature.export.BackupContents
import app.tandemforactual.ui.feature.export.readBackupFile
import app.tandemforactual.ui.feature.export.readPlanningBackup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PlanningBackupTest {
    @Test
    fun readsOnlyTandemPlanningBackups() {
        val backup =
            """{"format":"tandem-planning-backup","version":1,"records":[""" +
                """{"id":"g1","type":"planning.savings_goal","body":{"name":"Trip"}},""" +
                """{"id":"x","type":"budget.transaction","body":{}}]}"""
        val records = readPlanningBackup(backup)!!
        assertEquals(listOf("g1"), records.map { it.first })
        assertEquals("planning.savings_goal", records.single().second)
        assertNull(readPlanningBackup("""{"format":"other"}"""))
        assertNull(readPlanningBackup("not json"))
    }

    @Test
    fun sealedBackupsNeedTheirPassphrase() {
        val plain = """{"format":"tandem-planning-backup","version":1,"records":[{"id":"g1","type":"planning.savings_goal","body":{}}]}"""
        val sealed = PlanningBackupSeal.seal(plain, "passphrase1")
        assertEquals(BackupContents.NeedsPassphrase, readBackupFile(sealed, null))
        assertEquals(BackupContents.WrongPassphrase, readBackupFile(sealed, "nope"))
        assertEquals("g1", assertIs<BackupContents.Records>(readBackupFile(sealed, "passphrase1")).records.single().first)
        assertIs<BackupContents.Records>(readBackupFile(plain, null))
        assertEquals(BackupContents.NotABackup, readBackupFile("{}", null))
        assertEquals(BackupContents.NotABackup, readBackupFile(PlanningBackupSeal.seal("{}", "passphrase1"), "passphrase1"))
    }

    @Test
    fun backupLogRoundTrips() {
        val logs = mapOf("b1" to BackupLog("2026-11-20", null), "b2" to BackupLog(null, "2026-11-21"))
        assertEquals(logs, decodeBackupLogs(encodeBackupLogs(logs)))
        assertEquals(emptyMap(), decodeBackupLogs("broken"))
    }
}
