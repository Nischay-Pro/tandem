package app.tandemforactual.ui.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AccountIconsTest {
    @Test
    fun eachSavedNameFindsOneSymbol() {
        val names = accountIconChoices.map { it.first }
        assertEquals(names.size, names.toSet().size)
        names.forEach { assertNotNull(accountIconFor(it), it) }
        assertNull(accountIconFor(null))
        assertNull(accountIconFor("retired-symbol"))
    }
}
