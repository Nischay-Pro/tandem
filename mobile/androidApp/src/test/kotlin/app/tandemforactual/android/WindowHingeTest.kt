package app.tandemforactual.android

import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import app.tandemforactual.ui.app.WindowHinge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.window.testing.layout.FoldingFeature as fakeFold

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
}
