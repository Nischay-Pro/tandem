package app.tandemforactual.ui.design.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowSizeClassesTest {
    @Test
    fun widthClassesFollowMaterialBreakpoints() {
        assertEquals(WindowWidthClass.COMPACT, WindowWidthClass.of(0f))
        assertEquals(WindowWidthClass.COMPACT, WindowWidthClass.of(599.9f))
        assertEquals(WindowWidthClass.MEDIUM, WindowWidthClass.of(600f))
        assertEquals(WindowWidthClass.MEDIUM, WindowWidthClass.of(839f))
        assertEquals(WindowWidthClass.EXPANDED, WindowWidthClass.of(840f))
        assertEquals(WindowWidthClass.EXPANDED, WindowWidthClass.of(1_199f))
        assertEquals(WindowWidthClass.LARGE, WindowWidthClass.of(1_200f))
        assertEquals(WindowWidthClass.LARGE, WindowWidthClass.of(1_599f))
        assertEquals(WindowWidthClass.EXTRA_LARGE, WindowWidthClass.of(1_600f))
    }

    @Test
    fun heightClassesFollowMaterialBreakpoints() {
        assertEquals(WindowHeightClass.COMPACT, WindowHeightClass.of(479f))
        assertEquals(WindowHeightClass.MEDIUM, WindowHeightClass.of(480f))
        assertEquals(WindowHeightClass.MEDIUM, WindowHeightClass.of(899f))
        assertEquals(WindowHeightClass.EXPANDED, WindowHeightClass.of(900f))
    }

    @Test
    fun railNeedsMediumWidthAndHeight() {
        assertFalse(WindowSizeInfo(411f, 891f).usesRail)
        assertTrue(WindowSizeInfo(600f, 960f).usesRail)
        assertTrue(WindowSizeInfo(1_280f, 800f).usesRail)
        assertFalse(WindowSizeInfo(891f, 411f).usesRail)
        assertFalse(WindowSizeInfo(500f, 420f).usesRail)
        assertFalse(WindowSizeInfo(1_000f, 479f).usesRail)
    }

    @Test
    fun editorsFloatOnceTheWindowHasRoomAroundThem() {
        assertFalse(WindowSizeInfo(411f, 891f).floatsEditors)
        assertFalse(WindowSizeInfo(891f, 411f).floatsEditors)
        assertTrue(WindowSizeInfo(600f, 960f).floatsEditors)
        assertTrue(WindowSizeInfo(1_366f, 1_024f).floatsEditors)
    }

    @Test
    fun tabletopFoldMeasuresTheFlatHalf() {
        assertEquals(1_000f, TabletopFold(top = 1_100f, bottom = 1_140f, windowHeight = 2_140f).belowFold)
    }

    @Test
    fun shortWindowsAreCompactHeightWhateverTheirOrientation() {
        assertTrue(WindowSizeInfo(891f, 411f).compactHeight)
        assertTrue(WindowSizeInfo(260f, 300f).compactHeight)
        assertFalse(WindowSizeInfo(1_280f, 800f).compactHeight)
        assertFalse(WindowSizeInfo(800f, 1_280f).compactHeight)
    }

    @Test
    fun contentWidthLeavesRoomForTheRailOnly() {
        assertEquals(760f, WindowSizeInfo(840f, 900f).contentWidthDp)
        assertEquals(891f, WindowSizeInfo(891f, 411f).contentWidthDp)
        assertEquals(411f, WindowSizeInfo(411f, 891f).contentWidthDp)
    }
}
