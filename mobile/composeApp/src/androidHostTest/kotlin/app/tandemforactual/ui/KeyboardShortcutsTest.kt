package app.tandemforactual.ui

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyboardShortcutsTest {
    @Test
    fun commandKeysMapToActions() {
        assertEquals(KeyboardShortcut.NewTransaction, keyboardShortcut(Key.N, command = true))
        assertEquals(KeyboardShortcut.Search, keyboardShortcut(Key.F, command = true))
        assertEquals(KeyboardShortcut.Search, keyboardShortcut(Key.K, command = true))
        assertEquals(KeyboardShortcut.Sync, keyboardShortcut(Key.R, command = true))
        assertEquals(KeyboardShortcut.Back, keyboardShortcut(Key.LeftBracket, command = true))
        assertEquals(KeyboardShortcut.Save, keyboardShortcut(Key.S, command = true))
        assertEquals(KeyboardShortcut.Save, keyboardShortcut(Key.Enter, command = true))
        assertEquals(KeyboardShortcut.Menu, keyboardShortcut(Key.Period, command = true))
        assertEquals(KeyboardShortcut.Help, keyboardShortcut(Key.Slash, command = true))
        assertEquals(KeyboardShortcut.Close, keyboardShortcut(Key.Escape, command = false))
        assertEquals(
            (0..4).toList(),
            listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five).map { (keyboardShortcut(it, command = true) as KeyboardShortcut.Tab).index },
        )
    }

    @Test
    fun numberKeysFollowTheTabsOfEachMode() {
        assertEquals(navigationRoutes(InterfaceMode.MINIMAL), (0..3).mapNotNull { shortcutTab(it, InterfaceMode.MINIMAL) })
        assertNull(shortcutTab(4, InterfaceMode.MINIMAL))
        assertEquals(AppRoute.REPORTS, shortcutTab(4, InterfaceMode.ADVANCED))
        val help = shortcutHelpEntries(InterfaceMode.MINIMAL, "Ctrl")
        assertTrue(help.any { it.keys == "Ctrl 1" && it.action == "Go to Home" })
        assertTrue(help.none { it.keys == "Ctrl 5" })
    }

    @Test
    fun plainTypingIsNeverAShortcut() {
        assertNull(keyboardShortcut(Key.N, command = false))
        assertNull(keyboardShortcut(Key.One, command = false))
        assertNull(keyboardShortcut(Key.Q, command = true))
        assertNull(keyboardShortcut(Key.One, command = true, shift = true))
    }

    @Test
    fun saveWithNoFormOpenIsLeftToTheSystem() {
        assertFalse(SaveShortcuts.save())
    }
}
