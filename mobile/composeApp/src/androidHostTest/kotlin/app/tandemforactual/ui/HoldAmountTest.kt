package app.tandemforactual.ui

import app.tandemforactual.ui.feature.budget.holdAmountAllowed
import app.tandemforactual.ui.feature.budget.suggestedHoldAmount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HoldAmountTest {
    @Test
    fun anOverbudgetedMonthSuggestsLoweringTheHoldToCoverIt() {
        assertEquals(80_000L, suggestedHoldAmount(readyToAssignMinor = -20_000, heldMinor = 100_000))
        assertEquals(100_000L, suggestedHoldAmount(readyToAssignMinor = 0, heldMinor = 100_000))
        assertEquals(0L, suggestedHoldAmount(readyToAssignMinor = -150_000, heldMinor = 100_000))
        assertEquals(50_000L, suggestedHoldAmount(readyToAssignMinor = 50_000, heldMinor = 0))
        assertEquals(0L, suggestedHoldAmount(readyToAssignMinor = -1, heldMinor = 0))
    }

    @Test
    fun theHoldCanChangeUpToWhatIsReadyPlusAlreadyHeld() {
        assertTrue(holdAmountAllowed(80_000, readyToAssignMinor = -20_000, heldMinor = 100_000))
        assertFalse(holdAmountAllowed(80_001, readyToAssignMinor = -20_000, heldMinor = 100_000))
        assertFalse(holdAmountAllowed(100_000, readyToAssignMinor = 0, heldMinor = 100_000))
        assertTrue(holdAmountAllowed(120_000, readyToAssignMinor = 20_000, heldMinor = 100_000))
        assertFalse(holdAmountAllowed(0, readyToAssignMinor = 5_000, heldMinor = 0))
    }
}
