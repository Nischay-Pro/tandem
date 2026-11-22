package app.tandemforactual.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

internal sealed interface KeyboardShortcut {
    data object NewTransaction : KeyboardShortcut

    data object Search : KeyboardShortcut

    data object Sync : KeyboardShortcut

    data object Back : KeyboardShortcut

    data object Close : KeyboardShortcut

    data object Save : KeyboardShortcut

    data object Menu : KeyboardShortcut

    data object Help : KeyboardShortcut

    data class Tab(
        val index: Int,
    ) : KeyboardShortcut
}

private val TAB_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five)

/** ⌘ on iPad and Mac keyboards, Ctrl elsewhere. Escape needs no modifier. */
internal fun keyboardShortcut(
    key: Key,
    command: Boolean,
    shift: Boolean = false,
): KeyboardShortcut? {
    if (key == Key.Escape) return KeyboardShortcut.Close
    if (!command) return null
    return when (key) {
        Key.N -> KeyboardShortcut.NewTransaction
        Key.F, Key.K -> KeyboardShortcut.Search
        Key.R -> KeyboardShortcut.Sync
        Key.S, Key.Enter, Key.NumPadEnter -> KeyboardShortcut.Save
        Key.LeftBracket -> KeyboardShortcut.Back
        Key.Period -> KeyboardShortcut.Menu
        Key.Slash -> KeyboardShortcut.Help
        else -> TAB_KEYS.indexOf(key).takeIf { it >= 0 && !shift }?.let { KeyboardShortcut.Tab(it) }
    }
}

internal fun KeyEvent.keyboardShortcut(): KeyboardShortcut? =
    if (type == KeyEventType.KeyDown) keyboardShortcut(key, isMetaPressed || isCtrlPressed, isShiftPressed) else null

/** The tab a number key opens: the same order as the tab bar or rail for the current mode. */
internal fun shortcutTab(
    index: Int,
    mode: InterfaceMode,
): AppRoute? = navigationRoutes(mode).getOrNull(index)

/** Whether keyboards here label the shortcut modifier ⌘ rather than Ctrl. */
internal expect fun platformUsesCommandKey(): Boolean

/** One line of the shortcuts help sheet. */
internal data class ShortcutHelpEntry(
    val keys: String,
    val action: String,
)

internal fun shortcutHelpEntries(
    mode: InterfaceMode,
    commandKey: String,
): List<ShortcutHelpEntry> =
    buildList {
        add(ShortcutHelpEntry("$commandKey N", "Add a transaction"))
        add(ShortcutHelpEntry("$commandKey F", "Search transactions"))
        add(ShortcutHelpEntry("$commandKey S", "Save the open form"))
        add(ShortcutHelpEntry("$commandKey R", "Sync now"))
        add(ShortcutHelpEntry("$commandKey .", "Open or close the menu"))
        add(ShortcutHelpEntry("Esc", "Close a sheet or the menu"))
        add(ShortcutHelpEntry("$commandKey [", "Go back"))
        navigationRoutes(mode).forEachIndexed { index, route -> add(ShortcutHelpEntry("$commandKey ${index + 1}", "Go to ${route.title}")) }
        add(ShortcutHelpEntry("$commandKey /", "Show these shortcuts"))
    }

/**
 * Forms that can be saved from the keyboard register here while they're on screen; ⌘S or ⌘Enter saves the one opened
 * last, so a sheet over a screen wins.
 */
internal object SaveShortcuts {
    private class Handler(
        val enabled: () -> Boolean,
        val save: () -> Unit,
    )

    private val handlers = mutableListOf<Handler>()

    fun save(): Boolean {
        val handler = handlers.lastOrNull() ?: return false
        if (handler.enabled()) handler.save()
        return true
    }

    @Composable
    fun Register(
        enabled: Boolean,
        onSave: () -> Unit,
    ) {
        val latestEnabled by rememberUpdatedState(enabled)
        val latestSave by rememberUpdatedState(onSave)
        DisposableEffect(Unit) {
            val handler = Handler({ latestEnabled }, { latestSave() })
            handlers += handler
            onDispose { handlers -= handler }
        }
    }
}

/** Escape and save for content in its own window, such as a sheet, where the app's shortcuts don't reach. */
internal fun Modifier.sheetShortcutKeys(onClose: (() -> Unit)?): Modifier =
    onPreviewKeyEvent { event ->
        when (event.keyboardShortcut()) {
            KeyboardShortcut.Close -> onClose?.let { it() } != null
            KeyboardShortcut.Save -> SaveShortcuts.save()
            else -> false
        }
    }
