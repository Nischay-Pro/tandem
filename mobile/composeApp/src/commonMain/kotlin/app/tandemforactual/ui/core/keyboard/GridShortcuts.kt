package app.tandemforactual.ui.core.keyboard

import androidx.compose.ui.input.key.Key

internal val GRID_ARROWS =
    mapOf(
        Key.DirectionUp to (-1 to 0),
        Key.DirectionDown to (1 to 0),
        Key.DirectionLeft to (0 to -1),
        Key.DirectionRight to (0 to 1),
    )

private val ARROW_SYMBOLS = listOf("↑", "↓", "←", "→")

internal val GRID_DIGITS =
    listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)

private val NUMPAD_DIGITS =
    listOf(
        Key.NumPad0,
        Key.NumPad1,
        Key.NumPad2,
        Key.NumPad3,
        Key.NumPad4,
        Key.NumPad5,
        Key.NumPad6,
        Key.NumPad7,
        Key.NumPad8,
        Key.NumPad9,
    )

/** The digit a [GRID_DIGITS] or number pad key types. */
internal fun gridDigit(key: Key): Int? =
    GRID_DIGITS.indexOf(key).takeIf { it >= 0 } ?: NUMPAD_DIGITS.indexOf(key).takeIf { it >= 0 }

private fun grid(
    id: String,
    action: ShortcutAction,
    label: String,
    vararg keys: ShortcutKey,
) = Shortcut(id, action, label, ShortcutScope.GRID, keys.toList())

private fun arrows(shift: Boolean) = GRID_ARROWS.keys.mapIndexed { index, key -> ShortcutKey(key, ARROW_SYMBOLS[index], shift = shift) }

private fun digits() = (GRID_DIGITS + NUMPAD_DIGITS).map { ShortcutKey(it, "0–9") }

private fun bare(
    key: Key,
    symbol: String,
) = ShortcutKey(key, symbol)

private fun command(
    key: Key,
    symbol: String,
    shift: Boolean = false,
) = ShortcutKey(key, symbol, command = true, shift = shift)

/**
 * The budget grid's rows. Arrows, Tab, Enter and Esc also work while a cell is being edited, where they save or drop the
 * typed amount first; the bare letters and digits only work on a cell that is not being edited.
 */
internal val GRID_SHORTCUTS: List<Shortcut> =
    listOf(
        grid("grid-move", ShortcutAction.GRID_MOVE, "Move between cells", *arrows(shift = false).toTypedArray()),
        grid("grid-extend", ShortcutAction.GRID_EXTEND, "Select several cells", *arrows(shift = true).toTypedArray()),
        grid("grid-next", ShortcutAction.GRID_NEXT, "Next cell", bare(Key.Tab, "Tab")),
        grid("grid-previous", ShortcutAction.GRID_PREVIOUS, "Previous cell", ShortcutKey(Key.Tab, "Tab", shift = true)),
        grid(
            "grid-edit",
            ShortcutAction.GRID_EDIT,
            "Edit or save the amount",
            bare(Key.Enter, "Enter"),
            bare(Key.NumPadEnter, "Enter"),
            bare(Key.F2, "F2"),
        ),
        grid("grid-type", ShortcutAction.GRID_TYPE, "Type a new amount", *digits().toTypedArray()),
        grid("grid-cancel", ShortcutAction.GRID_CANCEL, "Cancel editing", bare(Key.Escape, "Esc")),
        grid("grid-clear", ShortcutAction.GRID_CLEAR, "Set to zero", bare(Key.Delete, "Delete"), bare(Key.Backspace, "Backspace")),
        grid("grid-copy-previous", ShortcutAction.GRID_COPY_PREVIOUS, "Copy last month", bare(Key.L, "l")),
        grid("grid-set-spent", ShortcutAction.GRID_SET_SPENT, "Budget what was spent", bare(Key.S, "s")),
        grid("grid-fill-down", ShortcutAction.GRID_FILL_DOWN, "Fill down", command(Key.D, "d")),
        grid("grid-fill-right", ShortcutAction.GRID_FILL_RIGHT, "Fill right", command(Key.R, "r", shift = true)),
        grid("grid-copy", ShortcutAction.GRID_COPY, "Copy", command(Key.C, "c")),
        grid("grid-paste", ShortcutAction.GRID_PASTE, "Paste", command(Key.V, "v")),
        grid("grid-undo", ShortcutAction.GRID_UNDO, "Undo", command(Key.Z, "z")),
        grid("grid-redo", ShortcutAction.GRID_REDO, "Redo", command(Key.Z, "z", shift = true), command(Key.Y, "y")),
    )
