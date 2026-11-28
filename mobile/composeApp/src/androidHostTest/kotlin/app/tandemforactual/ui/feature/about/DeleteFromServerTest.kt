package app.tandemforactual.ui.feature.about

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteFromServerTest {
    @Test
    fun theBudgetsNameMustBeTypedToDelete() {
        assertTrue(deleteNameMatches(" family budget ", "Family Budget"))
        assertFalse(deleteNameMatches("Family", "Family Budget"))
        assertFalse(deleteNameMatches("", ""))
    }
}
