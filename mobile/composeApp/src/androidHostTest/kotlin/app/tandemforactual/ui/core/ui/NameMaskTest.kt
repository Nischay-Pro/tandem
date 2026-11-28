package app.tandemforactual.ui.core.ui

import app.tandemforactual.ui.core.state.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NameMaskTest {
    @Test
    fun hidingNamesIsKeptAndOffByDefault() {
        val state = AppState()
        assertFalse(state.privacyMaskNames)
        state.privacyMaskNames = true
        assertTrue(AppState().apply { restorePersistentSettings(state.persistentSettings()) }.privacyMaskNames)
        assertEquals(5, MASKED_NAME.length)
    }
}
