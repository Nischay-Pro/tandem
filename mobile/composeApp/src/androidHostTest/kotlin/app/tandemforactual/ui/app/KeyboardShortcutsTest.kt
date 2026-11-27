package app.tandemforactual.ui.app

import androidx.compose.ui.input.key.Key
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.InterfaceMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyboardShortcutsTest {
    private val digits = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five)

    @Test
    fun commandKeysMapToActions() {
        val tabs = navigationRoutes(InterfaceMode.ADVANCED)
        assertEquals(KeyboardShortcut.NewTransaction, keyboardShortcut(Key.N, command = true, tabs))
        assertEquals(KeyboardShortcut.Search, keyboardShortcut(Key.F, command = true, tabs))
        assertEquals(KeyboardShortcut.Sync, keyboardShortcut(Key.R, command = true, tabs))
        assertEquals(KeyboardShortcut.Back, keyboardShortcut(Key.LeftBracket, command = true, tabs))
        assertEquals(KeyboardShortcut.Settings, keyboardShortcut(Key.Comma, command = true, tabs))
    }

    @Test
    fun digitsFollowTheTabOrderInEachMode() {
        InterfaceMode.entries.forEach { mode ->
            val tabs = navigationRoutes(mode)
            val routes = digits.mapNotNull { (keyboardShortcut(it, command = true, tabs) as? KeyboardShortcut.Tab)?.route }
            assertEquals(tabs, routes, "$mode")
        }
    }

    @Test
    fun digitsPastTheLastTabDoNothing() {
        val tabs = navigationRoutes(InterfaceMode.MINIMAL)
        assertEquals(4, tabs.size)
        assertNull(keyboardShortcut(Key.Five, command = true, tabs))
    }

    @Test
    fun plainTypingIsNeverAShortcut() {
        val tabs = navigationRoutes(InterfaceMode.ADVANCED)
        assertNull(keyboardShortcut(Key.N, command = false, tabs))
        assertNull(keyboardShortcut(Key.One, command = false, tabs))
        assertNull(keyboardShortcut(Key.Q, command = true, tabs))
    }

    @Test
    fun listedCommandsRunTheSameShortcutsAsTheKeys() {
        val tabs = navigationRoutes(InterfaceMode.MINIMAL)
        val commands = keyboardCommands(tabs.map { it to it.name }, enabled = true)
        assertEquals(listOf("n", "f", "r", "[", ",", "1", "2", "3", "4"), commands.map { it.key })
        assertEquals(
            listOf(
                KeyboardShortcut.NewTransaction,
                KeyboardShortcut.Search,
                KeyboardShortcut.Sync,
                KeyboardShortcut.Back,
                KeyboardShortcut.Settings,
            ) + tabs.map(KeyboardShortcut::Tab),
            commands.map { it.shortcut(tabs) },
        )
        assertEquals(AppRoute.HOME, (commands[5].shortcut(tabs) as KeyboardShortcut.Tab).route)
    }

    @Test
    fun publishingTellsTheListenerOnlyAboutChanges() {
        val seen = mutableListOf<List<KeyboardCommand>>()
        KeyboardCommands.observe { seen += it }
        val commands = keyboardCommands(listOf(AppRoute.HOME to "Home"), enabled = false)
        KeyboardCommands.publish(commands)
        KeyboardCommands.publish(commands.toList())
        assertEquals(commands, KeyboardCommands.current)
        assertEquals(commands, seen.last())
        assertEquals(1, seen.count { it == commands })
    }
}
