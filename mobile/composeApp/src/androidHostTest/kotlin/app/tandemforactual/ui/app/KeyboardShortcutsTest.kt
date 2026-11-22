package app.tandemforactual.ui.app

import androidx.compose.ui.input.key.Key
import app.tandemforactual.ui.core.state.AppRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyboardShortcutsTest {
    @Test
    fun commandKeysMapToActions() {
        assertEquals(KeyboardShortcut.NewTransaction, keyboardShortcut(Key.N, command = true))
        assertEquals(KeyboardShortcut.Search, keyboardShortcut(Key.F, command = true))
        assertEquals(KeyboardShortcut.Sync, keyboardShortcut(Key.R, command = true))
        assertEquals(KeyboardShortcut.Back, keyboardShortcut(Key.LeftBracket, command = true))
        assertEquals(
            listOf(AppRoute.BUDGET, AppRoute.ACCOUNTS, AppRoute.ACTIVITY, AppRoute.REPORTS, AppRoute.SETTINGS),
            listOf(
                Key.One,
                Key.Two,
                Key.Three,
                Key.Four,
                Key.Five,
            ).map { (keyboardShortcut(it, command = true) as KeyboardShortcut.Tab).route },
        )
    }

    @Test
    fun plainTypingIsNeverAShortcut() {
        assertNull(keyboardShortcut(Key.N, command = false))
        assertNull(keyboardShortcut(Key.One, command = false))
        assertNull(keyboardShortcut(Key.Q, command = true))
    }
}
