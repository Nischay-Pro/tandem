package app.tandemforactual.ui

import app.tandemforactual.ui.feature.accounts.balanceAdjustmentMinor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountBalanceAdjustmentTest {
    @Test
    fun addsTheDifferenceBetweenTheBalanceAndTheNewOne() {
        assertEquals(-150_000L, balanceAdjustmentMinor(2_000_000, 1_850_000))
        assertEquals(25_000L, balanceAdjustmentMinor(-25_000, 0))
        assertEquals(-5_000L, balanceAdjustmentMinor(-1_200_000, -1_205_000))
        assertNull(balanceAdjustmentMinor(42, 42))
    }
}
