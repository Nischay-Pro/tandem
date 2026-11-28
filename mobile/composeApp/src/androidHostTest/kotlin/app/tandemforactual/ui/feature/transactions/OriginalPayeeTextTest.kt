package app.tandemforactual.ui.feature.transactions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OriginalPayeeTextTest {
    @Test
    fun theOriginalTextShowsOnlyWhenItDiffersFromThePayee() {
        assertEquals("SQ *BLUE BOTTLE 1234", originalPayeeText(" SQ *BLUE BOTTLE 1234 ", "Blue Bottle"))
        assertNull(originalPayeeText("blue bottle", "Blue Bottle "))
        assertNull(originalPayeeText(" ", "Blue Bottle"))
        assertNull(originalPayeeText(null, "Blue Bottle"))
    }
}
