package app.tandemforactual.ui.app

import app.tandemforactual.ui.core.keyboard.Shortcut
import app.tandemforactual.ui.core.keyboard.ShortcutMenu
import app.tandemforactual.ui.core.keyboard.ShortcutScope

/** The shortcut table for the platform lists: Android's Meta+/ helper and the iPad menu bar, in table order. */
internal fun keyboardCommands(
    shortcuts: List<Shortcut>,
    enabled: Boolean,
): List<KeyboardCommand> =
    shortcuts.map { shortcut ->
        val key = shortcut.keys.first()
        KeyboardCommand(
            commandId = shortcut.id,
            title = shortcut.label,
            key = key.symbol,
            keyCode = key.key.keyCode,
            command = key.command,
            shift = key.shift,
            group = shortcut.scope.title,
            menu =
                when (shortcut.menu) {
                    ShortcutMenu.FILE -> KeyboardCommands.MENU_FILE
                    ShortcutMenu.GO -> KeyboardCommands.MENU_GO
                    ShortcutMenu.HELP -> KeyboardCommands.MENU_HELP
                    null -> ""
                },
            enabled = enabled || shortcut.scope == ShortcutScope.EDITOR || shortcut.scope == ShortcutScope.TEXT_FIELD,
        )
    }
