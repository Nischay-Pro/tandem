package app.tandemforactual.ui.feature.budget.grid

import app.tandemforactual.ui.core.keyboard.GRID_ARROWS
import app.tandemforactual.ui.core.keyboard.KeyPress
import app.tandemforactual.ui.core.keyboard.SHORTCUTS
import app.tandemforactual.ui.core.keyboard.ShortcutAction
import app.tandemforactual.ui.core.keyboard.ShortcutScope
import app.tandemforactual.ui.core.keyboard.gridDigit
import app.tandemforactual.ui.core.keyboard.match

private val WRITES =
    setOf(
        ShortcutAction.GRID_EDIT,
        ShortcutAction.GRID_TYPE,
        ShortcutAction.GRID_CLEAR,
        ShortcutAction.GRID_COPY_PREVIOUS,
        ShortcutAction.GRID_SET_SPENT,
        ShortcutAction.GRID_FILL_DOWN,
        ShortcutAction.GRID_FILL_RIGHT,
        ShortcutAction.GRID_PASTE,
        ShortcutAction.GRID_UNDO,
        ShortcutAction.GRID_REDO,
    )

/** The grid rows of the shortcut table for a focused cell that is not being typed into. */
internal fun BudgetGridState.onCellKey(
    press: KeyPress?,
    data: BudgetGridData,
    io: BudgetGridIo,
): Boolean {
    val shortcut = SHORTCUTS.match(press, ShortcutScope.GRID) ?: return false
    val range = range(data) ?: return false
    if (shortcut.action in WRITES && !io.editable) return false
    when (shortcut.action) {
        ShortcutAction.GRID_MOVE -> move(press, range.focus, data, io, extend = false)
        ShortcutAction.GRID_EXTEND -> move(press, range.focus, data, io, extend = true)
        ShortcutAction.GRID_NEXT -> data.key(range.focus.stepped(forward = true, data.size))?.let(::focusOn)
        ShortcutAction.GRID_PREVIOUS -> data.key(range.focus.stepped(forward = false, data.size))?.let(::focusOn)
        ShortcutAction.GRID_EDIT -> startEditing(data, io, null)
        ShortcutAction.GRID_TYPE -> startEditing(data, io, press?.let { gridDigit(it.key) }?.toString())
        ShortcutAction.GRID_CANCEL -> if (range.single) return false else focus?.let(::focusOn)
        else -> return onRangeKey(shortcut.action, range, data, io)
    }
    return true
}

private fun BudgetGridState.onRangeKey(
    action: ShortcutAction,
    range: GridRange,
    data: BudgetGridData,
    io: BudgetGridIo,
): Boolean {
    val budgeted = data::budgeted
    when (action) {
        ShortcutAction.GRID_CLEAR -> apply(range.setTo({ 0L }, budgeted), data, io)
        ShortcutAction.GRID_COPY_PREVIOUS -> apply(range.copyPrevious(budgeted), data, io)
        ShortcutAction.GRID_SET_SPENT -> apply(range.setTo({ data.amounts(it)?.spent }, budgeted), data, io)
        ShortcutAction.GRID_FILL_DOWN -> apply(range.fillDown(budgeted), data, io)
        ShortcutAction.GRID_FILL_RIGHT -> apply(range.fillRight(budgeted), data, io)
        ShortcutAction.GRID_COPY -> io.copy(range.toTsv { cell -> data.budgeted(cell)?.let(io::format).orEmpty() })
        ShortcutAction.GRID_PASTE -> io.paste()?.let { apply(pasteTsv(it, range, data.size, io::parse, budgeted), data, io) }
        ShortcutAction.GRID_UNDO -> undo(io)
        ShortcutAction.GRID_REDO -> redo(io)
        else -> return false
    }
    return true
}

/** Arrows inside the months shown; past the first or last month the months slide along by one. */
private fun BudgetGridState.move(
    press: KeyPress?,
    from: GridCell,
    data: BudgetGridData,
    io: BudgetGridIo,
    extend: Boolean,
) {
    val (rows, columns) = GRID_ARROWS[press?.key] ?: return
    val edge = from.column + columns
    val slideTo =
        when {
            edge < 0 -> data.earlierMonth
            edge >= data.size.columns -> data.laterMonth
            else -> null
        }
    val target = data.key(from.moved(rows, columns, data.size)) ?: return
    if (slideTo != null && !extend) {
        focusOn(target.copy(monthId = slideTo))
        io.showMonth(slideTo)
    } else {
        focusOn(target, extend)
    }
}

/** Keys reaching the cell being typed into: Enter, Tab and up or down save and move on, Esc drops the change. */
internal fun BudgetGridState.onEditorKey(
    press: KeyPress?,
    data: BudgetGridData,
    io: BudgetGridIo,
): Boolean {
    val shortcut = SHORTCUTS.match(press, ShortcutScope.GRID) ?: return false
    val from = data.cell(focus) ?: return false
    val target =
        when (shortcut.action) {
            ShortcutAction.GRID_CANCEL -> {
                cancel()
                return true
            }

            ShortcutAction.GRID_EDIT -> {
                from.moved(1, 0, data.size)
            }

            ShortcutAction.GRID_NEXT -> {
                from.stepped(forward = true, data.size)
            }

            ShortcutAction.GRID_PREVIOUS -> {
                from.stepped(forward = false, data.size)
            }

            ShortcutAction.GRID_MOVE -> {
                GRID_ARROWS[press?.key]?.takeIf { it.second == 0 }?.let { from.moved(it.first, 0, data.size) }
            }

            else -> {
                null
            }
        } ?: return false
    if (commit(data, io)) data.key(target)?.let(::focusOn)
    return true
}

/** A menu choice on [key]: the cell takes focus, then the action runs on it as its shortcut would. */
internal fun BudgetGridState.perform(
    key: GridKey,
    action: ShortcutAction,
    data: BudgetGridData,
    io: BudgetGridIo,
) {
    if (editing) finishEditing(data, io)
    focusOn(key)
    if (action == ShortcutAction.GRID_EDIT) {
        startEditing(data, io, null)
    } else if (io.editable) {
        range(data)?.let { onRangeKey(action, it, data, io) }
    }
}
