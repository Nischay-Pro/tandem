package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HingeSplitTest {
    @Test
    fun panesMeetAtTheHinge() {
        assertEquals(400f to 10f, hingeSplit(WindowHinge(1_000f, 1_025f), containerLeftPx = 0f, containerWidthPx = 2_000f, density = 2.5f))
        assertEquals(360f to 0f, hingeSplit(WindowHinge(1_100f, 1_100f), containerLeftPx = 200f, containerWidthPx = 2_000f, density = 2.5f))
    }

    @Test
    fun hingesThatLeaveAPaneTooNarrowAreIgnored() {
        assertNull(hingeSplit(null, 0f, 2_000f, 2.5f))
        assertNull(hingeSplit(WindowHinge(600f, 600f), 0f, 2_000f, 2.5f))
        assertNull(hingeSplit(WindowHinge(1_500f, 1_500f), 0f, 2_000f, 2.5f))
        assertNull(hingeSplit(WindowHinge(1_000f, 1_000f), 0f, 2_000f, 0f))
    }
}
