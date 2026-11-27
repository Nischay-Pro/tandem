package app.tandemforactual.android

import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import app.tandemforactual.ui.app.KeyboardCommand

/** The shortcut table as Android's keyboard shortcuts helper (Meta+/) lists it, one group per scope; Compose handles the keys. */
internal fun keyboardShortcutGroups(commands: List<KeyboardCommand>): List<KeyboardShortcutGroup> =
    commands
        .filter { it.enabled }
        .groupBy { it.group }
        .map { (group, rows) ->
            KeyboardShortcutGroup(
                group,
                rows
                    .distinctBy { it.title to it.keyCode }
                    .map { KeyboardShortcutInfo(it.title, Key(it.keyCode).nativeKeyCode, it.metaState()) },
            )
        }

private fun KeyboardCommand.metaState(): Int =
    (if (command) KeyEvent.META_CTRL_ON else 0) or (if (shift) KeyEvent.META_SHIFT_ON else 0)
