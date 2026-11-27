package app.tandemforactual.wear

import app.tandemforactual.wear.data.AmountEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountEntryTest {
    @Test
    fun digitsShiftInFromTheRight() {
        val typed = listOf(1, 2, 5, 0).fold(AmountEntry()) { entry, digit -> entry.digit(digit) }

        assertEquals(1250L, typed.minor)
        assertEquals(-1250L, typed.signedMinor)
        assertEquals(125L, typed.backspace().minor)
        assertEquals(1250L, typed.flipSign().signedMinor)
    }

    @Test
    fun wholeUnitCurrenciesTypeWholeUnits() {
        val yen = AmountEntry(fractionDigits = 0).digit(5).digit(2)

        assertEquals(5200L, yen.minor)
        assertEquals(500L, yen.backspace().minor)
    }

    @Test
    fun theCrownMovesWholeUnitsAndNeverBelowZero() {
        assertEquals(1350L, AmountEntry(minor = 1250).turn(1).minor)
        assertEquals(0L, AmountEntry(minor = 50).turn(-1).minor)
    }

    @Test
    fun spokenAmountsAreRead() {
        assertEquals(1250L, AmountEntry.spoken("12.50", 2))
        assertEquals(820L, AmountEntry.spoken("€8,20 for lunch", 2))
        assertEquals(125_000L, AmountEntry.spoken("1,250", 2))
        assertEquals(4_200L, AmountEntry.spoken("42", 0))
        assertNull(AmountEntry.spoken("twelve", 2))
    }

    @Test
    fun theAmountStopsAtItsLimit() {
        val full = AmountEntry(minor = AmountEntry.MAX_MINOR)

        assertEquals(AmountEntry.MAX_MINOR, full.digit(9).minor)
        assertEquals(AmountEntry.MAX_MINOR, full.turn(5).minor)
    }
}
