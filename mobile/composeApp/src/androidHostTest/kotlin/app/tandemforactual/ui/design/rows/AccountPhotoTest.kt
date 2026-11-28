package app.tandemforactual.ui.design.rows

import app.tandemforactual.ui.core.state.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountPhotoTest {
    @Test
    fun photosAreKeptPerBudgetAndAccountOnThisDevice() {
        val state = AppState()
        val key = accountPhotoKey("budget", "checking")
        assertEquals("budget/checking", key)
        state.accountPhotos = mapOf(key to encodeAccountPhoto(byteArrayOf(1, 2, 3)))
        val restored = AppState().apply { restorePersistentSettings(state.persistentSettings()) }
        assertEquals(state.accountPhotos, restored.accountPhotos)
        assertEquals("AQID", restored.accountPhotos[key])
        assertNull(decodeAccountPhoto("not a photo"))
    }
}
