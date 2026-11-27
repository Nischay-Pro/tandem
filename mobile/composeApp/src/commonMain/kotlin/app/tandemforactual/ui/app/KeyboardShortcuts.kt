package app.tandemforactual.ui.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import app.tandemforactual.ui.core.state.AppRoute

internal sealed interface KeyboardShortcut {
    data object NewTransaction : KeyboardShortcut

    data object Search : KeyboardShortcut

    data object Sync : KeyboardShortcut

    data object Back : KeyboardShortcut

    data object Settings : KeyboardShortcut

    data class Tab(
        val route: AppRoute,
    ) : KeyboardShortcut
}

private val TAB_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five)

/** ⌘ on iPad and Mac keyboards, Ctrl elsewhere; the digits follow the order of [tabs] on the rail and tab bar. */
internal fun keyboardShortcut(
    key: Key,
    command: Boolean,
    tabs: List<AppRoute>,
): KeyboardShortcut? {
    if (!command) return null
    return when (key) {
        Key.N -> KeyboardShortcut.NewTransaction
        Key.F -> KeyboardShortcut.Search
        Key.R -> KeyboardShortcut.Sync
        Key.LeftBracket -> KeyboardShortcut.Back
        Key.Comma -> KeyboardShortcut.Settings
        else -> tabs.getOrNull(TAB_KEYS.indexOf(key))?.let(KeyboardShortcut::Tab)
    }
}

internal fun KeyEvent.keyboardShortcut(tabs: List<AppRoute>): KeyboardShortcut? =
    if (type == KeyEventType.KeyDown && !isAltPressed && !isShiftPressed) {
        keyboardShortcut(key, isMetaPressed || isCtrlPressed, tabs)
    } else {
        null
    }

/** The shortcut lists the platforms show (Android's Meta+/ helper, the iPad menu bar), in the order they're listed. */
internal fun keyboardCommands(
    tabs: List<Pair<AppRoute, String>>,
    enabled: Boolean,
): List<KeyboardCommand> =
    listOf(
        KeyboardCommand(KeyboardCommand.NEW_TRANSACTION, "New transaction", "n", enabled),
        KeyboardCommand(KeyboardCommand.SEARCH, "Search activity", "f", enabled),
        KeyboardCommand(KeyboardCommand.SYNC, "Sync now", "r", enabled),
        KeyboardCommand(KeyboardCommand.BACK, "Back", "[", enabled),
        KeyboardCommand(KeyboardCommand.SETTINGS, "Settings", ",", enabled),
    ) +
        tabs.take(TAB_KEYS.size).mapIndexed { index, (route, title) ->
            KeyboardCommand(KeyboardCommand.TAB_PREFIX + route.name, title, "${index + 1}", enabled)
        }

internal fun KeyboardCommand.shortcut(tabs: List<AppRoute>): KeyboardShortcut? =
    when (commandId) {
        KeyboardCommand.NEW_TRANSACTION -> KeyboardShortcut.NewTransaction
        KeyboardCommand.SEARCH -> KeyboardShortcut.Search
        KeyboardCommand.SYNC -> KeyboardShortcut.Sync
        KeyboardCommand.BACK -> KeyboardShortcut.Back
        KeyboardCommand.SETTINGS -> KeyboardShortcut.Settings
        else -> tabs.firstOrNull { KeyboardCommand.TAB_PREFIX + it.name == commandId }?.let(KeyboardShortcut::Tab)
    }
