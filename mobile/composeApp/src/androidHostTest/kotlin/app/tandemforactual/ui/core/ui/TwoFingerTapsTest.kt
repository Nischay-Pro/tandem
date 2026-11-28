package app.tandemforactual.ui.core.ui

import app.tandemforactual.ui.core.state.AppState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwoFingerTapsTest {
    private fun taps() = TwoFingerTaps(tapMillis = 300, gapMillis = 400)

    @Test
    fun twoQuickTwoFingerTapsMakeADoubleTap() {
        val taps = taps()
        assertFalse(taps.onGesture(2, moved = false, startMillis = 0, endMillis = 120))
        assertTrue(taps.onGesture(2, moved = false, startMillis = 300, endMillis = 420))
        assertFalse(taps.onGesture(2, moved = false, startMillis = 600, endMillis = 700))
    }

    @Test
    fun slowMovingOneFingerOrFarApartGesturesDoNotCount() {
        val taps = taps()
        assertFalse(taps.onGesture(2, moved = false, startMillis = 0, endMillis = 100))
        assertFalse(taps.onGesture(2, moved = false, startMillis = 900, endMillis = 1_000))
        assertFalse(taps.onGesture(1, moved = false, startMillis = 1_100, endMillis = 1_150))
        assertFalse(taps.onGesture(2, moved = false, startMillis = 1_200, endMillis = 1_250))
        assertFalse(taps.onGesture(2, moved = true, startMillis = 1_300, endMillis = 1_350))
        assertFalse(taps.onGesture(2, moved = false, startMillis = 2_000, endMillis = 2_500))
        assertFalse(taps.onGesture(3, moved = false, startMillis = 2_600, endMillis = 2_650))
        assertFalse(AppState().hideAmountsGesture)
    }
}
