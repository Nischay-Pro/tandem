package app.tandemforactual.ui

import app.tandemforactual.ui.feature.export.readPlanningBackup
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
