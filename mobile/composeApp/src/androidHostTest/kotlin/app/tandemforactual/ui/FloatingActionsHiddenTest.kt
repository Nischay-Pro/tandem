package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingActionsHiddenTest {
    private val threshold = 120f

    @Test
    fun hidesOnlyAfterADeliberateScroll() {
        assertFalse(floatingActionsHidden(false, 60f, 3 to 200, threshold))
        assertTrue(floatingActionsHidden(false, 120f, 3 to 200, threshold))
        assertTrue(floatingActionsHidden(true, -60f, 3 to 140, threshold))
        assertFalse(floatingActionsHidden(true, -120f, 3 to 80, threshold))
    }

    @Test
    fun theTopAlwaysShowsTheButtons() {
        assertFalse(floatingActionsHidden(true, 500f, 0 to 40, threshold))
    }

    @Test
    fun itemChangesCountAsAFullStep() {
        assertEquals(threshold, scrollStep(2 to 300, 3 to 0, threshold))
        assertEquals(-threshold, scrollStep(3 to 0, 2 to 300, threshold))
        assertEquals(25f, scrollStep(3 to 10, 3 to 35, threshold))
    }
}
