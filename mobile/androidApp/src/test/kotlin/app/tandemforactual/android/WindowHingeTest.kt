package app.tandemforactual.android

import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import androidx.window.testing.layout.FoldingFeature as fakeFold
import androidx.window.testing.layout.TestWindowLayoutInfo
import app.tandemforactual.ui.app.WindowHinge
import app.tandemforactual.ui.design.theme.TabletopFold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WindowHingeTest {
    private val window = Rect(0, 0, 2208, 1840)

    @Test
    fun aHalfOpenedBookFoldSplitsTheWindowAtTheFold() {
        val fold = fakeFold(window, state = FoldingFeature.State.HALF_OPENED, orientation = FoldingFeature.Orientation.VERTICAL)

        assertEquals(WindowHinge(1104f, 1104f), TestWindowLayoutInfo(listOf(fold)).separatingVerticalHinge())
    }

    @Test
    fun aPhysicalHingeKeepsItsWidthBetweenThePanes() {
        val hinge = fakeFold(window, size = 40, state = FoldingFeature.State.FLAT, orientation = FoldingFeature.Orientation.VERTICAL)

        assertEquals(WindowHinge(1084f, 1124f), TestWindowLayoutInfo(listOf(hinge)).separatingVerticalHinge())
    }

    @Test
    fun aFlatFoldDoesNotSplitTheWindow() {
        val fold = fakeFold(window, state = FoldingFeature.State.FLAT, orientation = FoldingFeature.Orientation.VERTICAL)

        assertNull(TestWindowLayoutInfo(listOf(fold)).separatingVerticalHinge())
    }

    @Test
    fun aTabletopFoldKeepsOneLayout() {
        val fold = fakeFold(window, state = FoldingFeature.State.HALF_OPENED, orientation = FoldingFeature.Orientation.HORIZONTAL)
        val hinge = fakeFold(window, size = 40, state = FoldingFeature.State.FLAT, orientation = FoldingFeature.Orientation.HORIZONTAL)

        assertNull(TestWindowLayoutInfo(listOf(fold)).separatingVerticalHinge())
        assertNull(TestWindowLayoutInfo(listOf(hinge)).separatingVerticalHinge())
    }

    @Test
    fun aWindowWithoutFoldsHasNoHinge() {
        assertNull(TestWindowLayoutInfo(emptyList()).separatingVerticalHinge())
    }

    @Test
    fun aHalfOpenedTabletopFoldReportsWhereTheWindowBends() {
        val fold = fakeFold(window, state = FoldingFeature.State.HALF_OPENED, orientation = FoldingFeature.Orientation.HORIZONTAL)

        assertEquals(TabletopFold(920f, 920f, 1840f), TestWindowLayoutInfo(listOf(fold)).tabletopFold(1840f))
    }

    @Test
    fun aBookOrFlatFoldIsNotATabletop() {
        val book = fakeFold(window, state = FoldingFeature.State.HALF_OPENED, orientation = FoldingFeature.Orientation.VERTICAL)
        val flat = fakeFold(window, state = FoldingFeature.State.FLAT, orientation = FoldingFeature.Orientation.HORIZONTAL)

        assertNull(TestWindowLayoutInfo(listOf(book)).tabletopFold(1840f))
        assertNull(TestWindowLayoutInfo(listOf(flat)).tabletopFold(1840f))
    }
}
