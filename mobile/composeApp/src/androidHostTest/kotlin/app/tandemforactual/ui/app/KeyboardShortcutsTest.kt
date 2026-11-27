package app.tandemforactual.ui.app

import app.tandemforactual.ui.app.shell.shortcutKeyText
import app.tandemforactual.ui.app.shell.shortcutSections
import app.tandemforactual.ui.core.keyboard.ShortcutMenu
import app.tandemforactual.ui.core.keyboard.shortcutTable
import app.tandemforactual.ui.core.state.InterfaceMode
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyboardShortcutsTest {
    private val table = shortcutTable(navigationRoutes(InterfaceMode.MINIMAL).map { it.name })

    @Test
    fun platformListsCarryEveryRowInTableOrder() {
        val commands = keyboardCommands(table, enabled = true)
        assertEquals(table.map { it.id }, commands.map { it.commandId })
        assertEquals(table.map { it.keys.first().symbol }, commands.map { it.key })
        assertEquals(
            listOf("n", "f", "r", "[", ",", "1", "2", "3", "4", "/"),
            commands.filter { it.menu.isNotEmpty() }.map { it.key },
        )
        assertEquals(table.count { it.menu == ShortcutMenu.GO }, commands.count { it.menu == KeyboardCommands.MENU_GO })
    }

    @Test
    fun aClosedBudgetDisablesOnlyTheShellRows() {
        val commands = keyboardCommands(table, enabled = false)
        assertEquals(listOf("save", "submit", "close"), commands.filter { it.enabled }.map { it.commandId })
    }

    @Test
    fun theCheatSheetGroupsRowsByWhereTheyWork() {
        val sections = shortcutSections(table)
        assertEquals(listOf("Anywhere", "Lists and screens", "Forms", "Budget grid"), sections.map { it.first })
        val forms = sections.first { it.first == "Forms" }.second
        assertEquals(listOf("Save", "Confirm", "Close"), forms.map { it.first })
        assertEquals(listOf("Ctrl S", "Ctrl Enter"), forms.first().second)
        assertEquals("?", shortcutKeyText(table.first { it.id == "shortcuts-list" }.keys.single()))
    }

    @Test
    fun publishingTellsTheListenerOnlyAboutChanges() {
        val seen = mutableListOf<List<KeyboardCommand>>()
        KeyboardCommands.observe { seen += it }
        val commands = keyboardCommands(table.take(1), enabled = false)
        KeyboardCommands.publish(commands)
        KeyboardCommands.publish(commands.toList())
        assertEquals(commands, KeyboardCommands.current)
        assertEquals(commands, seen.last())
        assertEquals(1, seen.count { it == commands })
    }
}
