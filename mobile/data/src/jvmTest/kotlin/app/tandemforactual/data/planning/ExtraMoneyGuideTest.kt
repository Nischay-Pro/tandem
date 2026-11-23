package app.tandemforactual.data.planning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtraMoneyGuideTest {
    private val card = DebtInput("visa", 368_500, 2_499, 10_000)
    private val trip = GoalGap("g1", "Trip", 120_000)

    @Test
    fun comparesEveryPlaceTheMoneyCouldGo() {
        val result = ExtraMoneyGuide.compare(10_000, 75_000, listOf(card), 318_475, 138_700, listOf(trip, GoalGap("g2", "Done", 0)))
        assertEquals(75_000, result.matchGainMinor)
        assertTrue(result.debt!!.interestSavedMinor!! > 0)
        assertTrue(result.debt!!.monthsSooner!! > 0)
        assertEquals(22, result.buffer!!.tenthsNow)
        assertEquals(31, result.buffer!!.tenthsAfterYear)
        assertEquals(GoalEffect(trip, 12), result.goal)
        assertFalse(result.thinBuffer)
    }

    @Test
    fun leavesOutWhatItCantWorkOut() {
        val result = ExtraMoneyGuide.compare(-5, null, emptyList(), 50_000, 0, listOf(trip))
        assertEquals(0, result.extraMinor)
        assertNull(result.matchGainMinor)
        assertNull(result.debt)
        assertNull(result.buffer)
        assertNull(result.goal)
        assertFalse(result.thinBuffer)
        assertNull(ExtraMoneyGuide.compare(1, null, emptyList(), 0, 1, emptyList()).goal)
    }

    @Test
    fun flagsAThinBufferAndDebtsTheMinimumsNeverClear() {
        val result = ExtraMoneyGuide.compare(100_000, null, listOf(DebtInput("loan", 1_000_000, 3_000, 100)), -1, 2_000_000, emptyList())
        assertTrue(result.thinBuffer)
        assertEquals(0, result.buffer!!.tenthsNow)
        assertNull(result.debt!!.interestSavedMinor)
        assertNull(result.debt!!.monthsSooner)
        assertTrue(result.debt!!.monthsWithExtra > 0)
        val hopeless = ExtraMoneyGuide.compare(100, null, listOf(DebtInput("loan", 1_000_000, 3_000, 100)), 0, 1, emptyList())
        assertNull(hopeless.debt)
    }
}
