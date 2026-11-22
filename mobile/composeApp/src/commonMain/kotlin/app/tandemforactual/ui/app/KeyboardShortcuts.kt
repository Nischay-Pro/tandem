package app.tandemforactual.ui.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import app.tandemforactual.ui.core.state.AppRoute

internal sealed interface KeyboardShortcut {
    data object NewTransaction : KeyboardShortcut

    data object Search : KeyboardShortcut

    data object Sync : KeyboardShortcut

    data object Back : KeyboardShortcut

    data class Tab(
        val route: AppRoute,
    ) : KeyboardShortcut
}

private val SHORTCUT_TABS = listOf(AppRoute.BUDGET, AppRoute.ACCOUNTS, AppRoute.ACTIVITY, AppRoute.REPORTS, AppRoute.SETTINGS)

/** ⌘ on iPad and Mac keyboards, Ctrl elsewhere. */
internal fun keyboardShortcut(
    key: Key,
    command: Boolean,
): KeyboardShortcut? {
    if (!command) return null
    return when (key) {
        Key.N -> KeyboardShortcut.NewTransaction
        Key.F -> KeyboardShortcut.Search
        Key.R -> KeyboardShortcut.Sync
        Key.LeftBracket -> KeyboardShortcut.Back
        Key.One -> KeyboardShortcut.Tab(SHORTCUT_TABS[0])
        Key.Two -> KeyboardShortcut.Tab(SHORTCUT_TABS[1])
        Key.Three -> KeyboardShortcut.Tab(SHORTCUT_TABS[2])
        Key.Four -> KeyboardShortcut.Tab(SHORTCUT_TABS[3])
        Key.Five -> KeyboardShortcut.Tab(SHORTCUT_TABS[4])
        else -> null
    }
}

internal fun KeyEvent.keyboardShortcut(): KeyboardShortcut? =
    if (type == KeyEventType.KeyDown) keyboardShortcut(key, isMetaPressed || isCtrlPressed) else null
