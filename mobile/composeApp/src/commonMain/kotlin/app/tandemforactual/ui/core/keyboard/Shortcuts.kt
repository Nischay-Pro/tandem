package app.tandemforactual.ui.core.keyboard

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Where a shortcut works. A [preview] scope sees the key before the focused control does. Only a scope that is quiet
 * [whileTyping] may use a bare letter or symbol, so typing into a field never runs a shortcut.
 */
internal enum class ShortcutScope(
    val title: String,
    val preview: Boolean,
    val whileTyping: Boolean,
) {
    GLOBAL("Anywhere", preview = true, whileTyping = true),
    LIST("Lists and screens", preview = false, whileTyping = false),
    EDITOR("Forms", preview = false, whileTyping = true),
    TEXT_FIELD("Forms", preview = true, whileTyping = true),
    GRID("Budget grid", preview = false, whileTyping = false),
}

internal enum class ShortcutMenu { FILE, GO, HELP }

internal enum class ShortcutAction {
    NEW_TRANSACTION,
    SEARCH,
    SYNC,
    BACK,
    ESCAPE_BACK,
    SETTINGS,
    TAB,
    SHORTCUTS,
    SAVE,
    SUBMIT,
    CLOSE,
    GRID_MOVE,
    GRID_EXTEND,
    GRID_NEXT,
    GRID_PREVIOUS,
    GRID_EDIT,
    GRID_TYPE,
    GRID_CANCEL,
    GRID_CLEAR,
    GRID_COPY_PREVIOUS,
    GRID_SET_SPENT,
    GRID_FILL_DOWN,
    GRID_FILL_RIGHT,
    GRID_COPY,
    GRID_PASTE,
    GRID_UNDO,
    GRID_REDO,
}

/** ⌘ on iPad and Mac keyboards, Ctrl elsewhere, when [command] is set. [symbol] is what the cheat sheet shows. */
internal data class ShortcutKey(
    val key: Key,
    val symbol: String,
    val command: Boolean = false,
    val shift: Boolean = false,
) {
    val printable: Boolean get() = symbol.length == 1 && symbol.single() !in KEY_GLYPHS

    fun matches(press: KeyPress): Boolean = press.key == key && press.command == command && press.shift == shift && !press.alt
}

private const val KEY_GLYPHS = "←↑→↓"

internal data class KeyPress(
    val key: Key,
    val command: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
)

internal fun KeyEvent.keyPress(): KeyPress? =
    if (type == KeyEventType.KeyDown) KeyPress(key, isMetaPressed || isCtrlPressed, isShiftPressed, isAltPressed) else null

/** One row of the table; the platforms list and the menu bar use the first of its [keys]. [tab] is a navigation index. */
internal data class Shortcut(
    val id: String,
    val action: ShortcutAction,
    val label: String,
    val scope: ShortcutScope,
    val keys: List<ShortcutKey>,
    val menu: ShortcutMenu? = null,
    val tab: Int? = null,
)

private fun command(
    key: Key,
    symbol: String,
) = ShortcutKey(key, symbol, command = true)

private fun global(
    id: String,
    action: ShortcutAction,
    label: String,
    key: ShortcutKey,
    menu: ShortcutMenu,
    tab: Int? = null,
) = Shortcut(id, action, label, ShortcutScope.GLOBAL, listOf(key), menu, tab)

internal val TAB_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five)

/** Every shortcut in the app. Tab rows are named after the navigation tab they open; see [shortcutTable]. */
internal val SHORTCUTS: List<Shortcut> =
    listOf(
        global("new-transaction", ShortcutAction.NEW_TRANSACTION, "New transaction", command(Key.N, "n"), ShortcutMenu.FILE),
        global("search", ShortcutAction.SEARCH, "Search activity", command(Key.F, "f"), ShortcutMenu.GO),
        global("sync", ShortcutAction.SYNC, "Sync now", command(Key.R, "r"), ShortcutMenu.GO),
        global("back", ShortcutAction.BACK, "Back", command(Key.LeftBracket, "["), ShortcutMenu.GO),
        global("settings", ShortcutAction.SETTINGS, "Settings", command(Key.Comma, ","), ShortcutMenu.GO),
    ) +
        TAB_KEYS.mapIndexed { index, key ->
            global("tab-${index + 1}", ShortcutAction.TAB, "Tab ${index + 1}", command(key, "${index + 1}"), ShortcutMenu.GO, index)
        } +
        listOf(
            global("shortcuts", ShortcutAction.SHORTCUTS, "Keyboard shortcuts", command(Key.Slash, "/"), ShortcutMenu.HELP),
            Shortcut(
                "shortcuts-list",
                ShortcutAction.SHORTCUTS,
                "Keyboard shortcuts",
                ShortcutScope.LIST,
                listOf(ShortcutKey(Key.Slash, "?", shift = true)),
            ),
            Shortcut("escape-back", ShortcutAction.ESCAPE_BACK, "Back", ShortcutScope.LIST, listOf(ShortcutKey(Key.Escape, "Esc"))),
            Shortcut(
                "save",
                ShortcutAction.SAVE,
                "Save",
                ShortcutScope.TEXT_FIELD,
                listOf(command(Key.S, "s"), command(Key.Enter, "Enter"), command(Key.NumPadEnter, "Enter")),
            ),
            Shortcut(
                "submit",
                ShortcutAction.SUBMIT,
                "Confirm",
                ShortcutScope.EDITOR,
                listOf(ShortcutKey(Key.Enter, "Enter"), ShortcutKey(Key.NumPadEnter, "Enter")),
            ),
            Shortcut("close", ShortcutAction.CLOSE, "Close", ShortcutScope.EDITOR, listOf(ShortcutKey(Key.Escape, "Esc"))),
        ) +
        GRID_SHORTCUTS

/** The table with each tab row named after [tabTitles], in navigation order; digits past the last tab are dropped. */
internal fun shortcutTable(tabTitles: List<String>): List<Shortcut> =
    SHORTCUTS.mapNotNull { shortcut ->
        val tab = shortcut.tab ?: return@mapNotNull shortcut
        tabTitles.getOrNull(tab)?.let { shortcut.copy(label = it) }
    }

internal fun List<Shortcut>.match(
    press: KeyPress?,
    scope: ShortcutScope,
): Shortcut? = press?.let { firstOrNull { row -> row.scope == scope && row.keys.any { it.matches(press) } } }
