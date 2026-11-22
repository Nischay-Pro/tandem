package app.tandemforactual.ui

import app.tandemforactual.ui.app.isBackPreviewStale
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackPreviewTest {
    @Test
    fun aPreviewStaysWhileItsGestureIsOnTheScreenItStartedOn() {
        assertFalse(isBackPreviewStale(preview = "settings", gestureOrigin = "payees", current = "payees"))
    }

    @Test
    fun aPreviewStaysWhileTheScreenItRevealsIsBeingPromoted() {
        assertFalse(isBackPreviewStale(preview = "settings", gestureOrigin = "payees", current = "settings"))
    }

    @Test
    fun aPreviewIsDroppedWhenAnotherScreenOpensMidGesture() {
        assertTrue(isBackPreviewStale(preview = "settings", gestureOrigin = "payees", current = "rules"))
        assertTrue(isBackPreviewStale(preview = "settings", gestureOrigin = null, current = "rules"))
    }

    @Test
    fun noPreviewIsNeverStale() {
        assertFalse(isBackPreviewStale(preview = null, gestureOrigin = null, current = "rules"))
    }
}
