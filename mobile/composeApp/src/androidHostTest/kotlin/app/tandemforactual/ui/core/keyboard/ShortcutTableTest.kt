package app.tandemforactual.ui.core.keyboard

import androidx.compose.ui.input.key.Key
import app.tandemforactual.ui.app.navigationRoutes
import app.tandemforactual.ui.core.state.InterfaceMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortcutTableTest {
    private fun ShortcutKey.chord() = Triple(key, command, shift)

    @Test
    fun everyScopesKeysAreUnique() {
        ShortcutScope.entries.forEach { scope ->
            val chords = SHORTCUTS.filter { it.scope == scope }.flatMap { row -> row.keys.map { it.chord() } }
            assertEquals(chords.distinct(), chords, "$scope")
        }
    }

    @Test
    fun scopedKeysNeverShadowGlobalOnes() {
        val global = SHORTCUTS.filter { it.scope == ShortcutScope.GLOBAL }.flatMap { row -> row.keys.map { it.chord() } }.toSet()
        val clashes = SHORTCUTS.filter { it.scope != ShortcutScope.GLOBAL }.filter { row -> row.keys.any { it.chord() in global } }
        assertEquals(emptyList(), clashes.map { it.id })
    }

    @Test
    fun bareLettersAndSymbolsOnlyWorkWhenNobodyIsTyping() {
        val typed = SHORTCUTS.filter { row -> row.scope.whileTyping && row.keys.any { it.printable && !it.command } }
        assertEquals(emptyList(), typed.map { it.id })
    }

    @Test
    fun idsAreUnique() {
        assertEquals(SHORTCUTS.map { it.id }.distinct(), SHORTCUTS.map { it.id })
    }

    @Test
    fun tabRowsFollowTheNavigationOrderInEachMode() {
        InterfaceMode.entries.forEach { mode ->
            val routes = navigationRoutes(mode)
            val tabs = shortcutTable(routes.map { it.name }).filter { it.action == ShortcutAction.TAB }
            assertEquals(routes.map { it.name }, tabs.map { it.label }, "$mode")
            assertEquals(routes.indices.toList(), tabs.map { it.tab }, "$mode")
            assertEquals(routes.indices.map { "${it + 1}" }, tabs.map { it.keys.single().symbol }, "$mode")
            assertTrue(routes.size <= TAB_KEYS.size, "$mode has more tabs than digit keys")
        }
    }

    @Test
    fun keysFindTheirRowOnlyInTheirScope() {
        val table = shortcutTable(navigationRoutes(InterfaceMode.MINIMAL).map { it.name })
        assertEquals("new-transaction", table.match(KeyPress(Key.N, command = true), ShortcutScope.GLOBAL)?.id)
        assertEquals("shortcuts", table.match(KeyPress(Key.Slash, command = true), ShortcutScope.GLOBAL)?.id)
        assertEquals("shortcuts-list", table.match(KeyPress(Key.Slash, shift = true), ShortcutScope.LIST)?.id)
        assertEquals("save", table.match(KeyPress(Key.Enter, command = true), ShortcutScope.TEXT_FIELD)?.id)
        assertEquals("close", table.match(KeyPress(Key.Escape), ShortcutScope.EDITOR)?.id)
        assertNull(table.match(KeyPress(Key.N), ShortcutScope.GLOBAL))
        assertNull(table.match(KeyPress(Key.N, command = true, alt = true), ShortcutScope.GLOBAL))
        assertNull(table.match(KeyPress(Key.Slash, shift = true), ShortcutScope.GLOBAL))
        assertNull(table.match(KeyPress(Key.Five, command = true), ShortcutScope.GLOBAL))
        assertNull(table.match(null, ShortcutScope.GLOBAL))
    }
}
