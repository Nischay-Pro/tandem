package app.tandemforactual.ui

import app.tandemforactual.ui.feature.banking.customSyncInterval
import app.tandemforactual.ui.feature.banking.syncIntervalLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BankSyncScheduleTest {
    @Test
    fun namesIntervalsTheWayActualDoes() {
        assertEquals("Never", syncIntervalLabel(0))
        assertEquals("Every 12 hours", syncIntervalLabel(720))
        assertEquals("Every day", syncIntervalLabel(1_440))
        assertEquals("Every week", syncIntervalLabel(10_080))
        assertEquals("Every 2 hours", syncIntervalLabel(120))
        assertEquals("Every 90 minutes", syncIntervalLabel(90))
        assertEquals("Every 3 days", syncIntervalLabel(4_320))
        assertEquals("Every hour", syncIntervalLabel(60))
    }

    @Test
    fun customIntervalsKeepActualsFifteenMinuteFloor() {
        assertEquals(180, customSyncInterval("3", 60))
        assertEquals(15, customSyncInterval("5", 1))
        assertNull(customSyncInterval("0", 60))
        assertNull(customSyncInterval("", 60))
    }
}
