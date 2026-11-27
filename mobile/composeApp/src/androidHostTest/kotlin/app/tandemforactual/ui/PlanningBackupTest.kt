package app.tandemforactual.ui

import app.tandemforactual.data.planning.OpenedBackup
import app.tandemforactual.ui.core.state.BackupLog
import app.tandemforactual.ui.core.state.decodeBackupLogs
import app.tandemforactual.ui.core.state.encodeBackupLogs
import app.tandemforactual.ui.feature.export.describeBackup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlanningBackupTest {
    private fun contents(
        database: ByteArray?,
        records: List<Triple<String, String, JsonObject>>?,
        budgetId: String? = "b1",
    ) = OpenedBackup.Contents(database, null, records, budgetId, "Cabin", "2026-11-20", null)

    @Test
    fun saysWhatABackupHolds() {
        val goal = Triple("g1", "planning.savings_goal", JsonObject(emptyMap()))
        assertTrue(describeBackup(contents(byteArrayOf(1), listOf(goal)), "b1").contains("your Actual budget and Tandem extras (1 item)"))
        assertTrue(describeBackup(contents(null, listOf(goal, goal)), "b1").contains("holds Tandem extras (2 items)"))
        assertTrue(describeBackup(contents(byteArrayOf(1), null), "b1").contains("holds your Actual budget,"))
        assertTrue(describeBackup(contents(null, listOf(goal), "b2"), "b1").endsWith("from “Cabin”."))
    }

    @Test
    fun backupLogRoundTrips() {
        val logs = mapOf("b1" to BackupLog("2026-11-20"), "b2" to BackupLog(null))
        assertEquals(logs, decodeBackupLogs(encodeBackupLogs(logs)))
        assertEquals(mapOf("b1" to BackupLog("2026-11-19")), decodeBackupLogs("""{"b1":{"backedUpOn":"2026-11-19"}}"""))
        assertEquals(emptyMap(), decodeBackupLogs("broken"))
    }
}
