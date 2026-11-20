package app.tandemforactual.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveLayoutTest {
    @Test
    fun wideWindowsAndLandscapePhonesUseTheSideRail() {
        assertFalse(usesRailNavigation(widthDp = 412f, isLandscape = false))
        assertTrue(usesRailNavigation(widthDp = 412f, isLandscape = true))
        assertTrue(usesRailNavigation(widthDp = 626f, isLandscape = false))
        assertTrue(usesRailNavigation(widthDp = 800f, isLandscape = false))
        assertFalse(usesRailNavigation(widthDp = 599f, isLandscape = false))
    }

    @Test
    fun contentStaysReadableAndCenteredOnWideWindows() {
        assertEquals(16.dp, readablePadding(412.dp, 16.dp, 720.dp))
        assertEquals(16.dp, readablePadding(740.dp, 16.dp, 720.dp))
        assertEquals(240.dp, readablePadding(1200.dp, 16.dp, 720.dp))
    }
}
