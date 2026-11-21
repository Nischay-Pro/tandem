package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingActionsHiddenTest {
    @Test
    fun hideWhileScrollingForwardAndReturnWhenScrollingBack() {
        assertTrue(floatingActionsHidden(false, 2 to 0, 2 to 40))
        assertTrue(floatingActionsHidden(false, 2 to 300, 3 to 0))
        assertFalse(floatingActionsHidden(true, 4 to 0, 3 to 200))
        assertFalse(floatingActionsHidden(true, 3 to 80, 3 to 20))
    }

    @Test
    fun smallMovementsKeepStateAndTheTopAlwaysShows() {
        assertTrue(floatingActionsHidden(true, 3 to 100, 3 to 104))
        assertFalse(floatingActionsHidden(false, 3 to 100, 3 to 104))
        assertFalse(floatingActionsHidden(true, 0 to 200, 0 to 10))
    }
}
