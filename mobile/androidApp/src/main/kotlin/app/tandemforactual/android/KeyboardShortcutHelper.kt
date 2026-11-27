package app.tandemforactual.android

import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import app.tandemforactual.ui.app.KeyboardCommand

/** The app's shortcuts as Android's keyboard shortcuts helper (Meta+/) lists them; Compose handles the keys themselves. */
internal fun keyboardShortcutGroup(commands: List<KeyboardCommand>): KeyboardShortcutGroup? {
    val shortcuts =
        commands
            .filter { it.enabled && it.key.length == 1 }
            .map { KeyboardShortcutInfo(it.title, it.key.single(), KeyEvent.META_CTRL_ON) }
    return if (shortcuts.isEmpty()) null else KeyboardShortcutGroup("Tandem", shortcuts)
}
