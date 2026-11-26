package app.tandemforactual.ui.core.format

import kotlin.test.Test
import kotlin.test.assertEquals

class TodayDateTest {
    @Test
    fun wakesJustAfterTheNextLocalMidnight() {
        assertEquals(24 * 60 * 60_000L, millisUntilNextDay(0))
        assertEquals(60 * 60_000L, millisUntilNextDay(23 * 60))
        assertEquals(60_000L, millisUntilNextDay(23 * 60 + 59))
    }

    @Test
    fun neverSleepsForLessThanAMinute() {
        assertEquals(60_000L, millisUntilNextDay(24 * 60))
        assertEquals(60_000L, millisUntilNextDay(24 * 60 + 30))
    }
}
