package app.tandemforactual.ui

import app.tandemforactual.ui.feature.budget.holdAmountAllowed
import app.tandemforactual.ui.feature.budget.suggestedHoldAmount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HoldAmountTest {
    @Test
    fun theSheetSuggestsHoldingEverythingReadyToAssign() {
        assertEquals(50_000L, suggestedHoldAmount(readyToAssignMinor = 50_000))
        assertEquals(0L, suggestedHoldAmount(readyToAssignMinor = 0))
        assertEquals(0L, suggestedHoldAmount(readyToAssignMinor = -1))
    }

    @Test
    fun aHoldAddsUpToWhatIsReadyToAssign() {
        assertTrue(holdAmountAllowed(20_000, readyToAssignMinor = 20_000))
        assertTrue(holdAmountAllowed(1, readyToAssignMinor = 20_000))
        assertFalse(holdAmountAllowed(20_001, readyToAssignMinor = 20_000))
        assertFalse(holdAmountAllowed(1, readyToAssignMinor = 0))
        assertFalse(holdAmountAllowed(1, readyToAssignMinor = -20_000))
        assertFalse(holdAmountAllowed(0, readyToAssignMinor = 5_000))
    }
}
