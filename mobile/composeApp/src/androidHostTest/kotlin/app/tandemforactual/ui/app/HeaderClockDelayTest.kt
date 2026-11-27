package app.tandemforactual.ui.app

import app.tandemforactual.ui.app.shell.headerClockDelayMs
import kotlin.test.Test
import kotlin.test.assertEquals

class HeaderClockDelayTest {
    @Test
    fun waitsForTheNextMinuteSinceTheLastSync() {
        val synced = 1_000_000L
        assertEquals(60_000L, headerClockDelayMs(synced, synced))
        assertEquals(45_000L, headerClockDelayMs(synced + 15_000L, synced))
        assertEquals(50_000L, headerClockDelayMs(synced + 10 * 60_000L + 10_000L, synced))
    }

    @Test
    fun alignsToTheWallClockMinuteWithoutARecentSync() {
        assertEquals(60_000L, headerClockDelayMs(120_000L, null))
        assertEquals(20_000L, headerClockDelayMs(160_000L, null))
        assertEquals(20_000L, headerClockDelayMs(7_200_000L + 40_000L, 0L))
        assertEquals(20_000L, headerClockDelayMs(160_000L, 500_000L))
    }

    @Test
    fun neverWaitsLessThanASecond() {
        assertEquals(1_000L, headerClockDelayMs(179_700L, null))
        assertEquals(1_000L, headerClockDelayMs(59_999L, 0L))
    }
}
