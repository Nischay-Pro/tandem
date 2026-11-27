package app.tandemforactual.ui.feature.budget.grid

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

private data class GridEdit(
    val key: GridKey,
    val before: Long,
    val after: Long,
)

/** The focused cell, the selected range, the amount being typed and the undo history of the budget grid. */
@Stable
internal class BudgetGridState {
    var focus by mutableStateOf<GridKey?>(null)
        private set
    var anchor by mutableStateOf<GridKey?>(null)
        private set
    var editing by mutableStateOf(false)
        private set
    var draft by mutableStateOf(TextFieldValue())
    var draftInvalid by mutableStateOf(false)
        private set

    /** Which figures each cell shows. */
    var value by mutableStateOf(GridValue.ALL)

    /** Set when the focused cell should take keyboard focus as soon as it is on screen. */
    var focusRequested by mutableStateOf(false)

    private val undo = ArrayDeque<List<GridEdit>>()
    private val redo = ArrayDeque<List<GridEdit>>()

    fun range(data: BudgetGridData): GridRange? {
        val focus = data.cell(focus) ?: return null
        return GridRange(data.cell(anchor) ?: focus, focus)
    }

    fun selected(
        data: BudgetGridData,
        key: GridKey,
    ): Boolean {
        val range = range(data) ?: return false
        val cell = data.cell(key) ?: return false
        return !range.single && cell in range
    }

    fun focusOn(
        key: GridKey,
        extend: Boolean = false,
    ) {
        focus = key
        if (!extend || anchor == null) anchor = key
        focusRequested = true
    }

    /** Puts a pointer on [key]: a cell being typed into elsewhere is saved first, then this one opens for typing. */
    fun tap(
        key: GridKey,
        data: BudgetGridData,
        io: BudgetGridIo,
    ) {
        if (editing && focus != key) finishEditing(data, io)
        focusOn(key)
        startEditing(data, io, null)
    }

    /** Opens the focused cell for typing, starting from [typed] or from its amount with all of it selected. */
    fun startEditing(
        data: BudgetGridData,
        io: BudgetGridIo,
        typed: String?,
    ) {
        val key = focus ?: return
        if (!io.editable) return
        val text = typed ?: io.format(data.amounts(key)?.budgeted ?: return)
        draft = TextFieldValue(text, if (typed == null) TextRange(0, text.length) else TextRange(text.length))
        draftInvalid = false
        anchor = key
        editing = true
    }

    /** Saves what was typed; false keeps the cell open because it doesn't read as an amount. */
    fun commit(
        data: BudgetGridData,
        io: BudgetGridIo,
    ): Boolean {
        val key = focus
        if (!editing || key == null) return true
        val amount = evaluateAmount(draft.text, io::parse)?.takeIf { it >= 0 }
        if (amount == null) {
            draftInvalid = true
            return false
        }
        val cell = data.cell(key)
        if (cell != null) apply(gridChanges(listOf(cell to amount), data::budgeted), data, io)
        close()
        return true
    }

    fun cancel() {
        if (editing) close()
    }

    /** Leaves the cell when focus goes elsewhere: a readable amount is saved, anything else is dropped. */
    fun finishEditing(
        data: BudgetGridData,
        io: BudgetGridIo,
    ) {
        if (!commit(data, io)) close()
    }

    private fun close() {
        editing = false
        draftInvalid = false
        focusRequested = true
    }

    fun apply(
        changes: List<GridChange>,
        data: BudgetGridData,
        io: BudgetGridIo,
    ) {
        val edits = changes.mapNotNull { change -> data.key(change.cell)?.let { GridEdit(it, change.before, change.after) } }
        if (edits.isEmpty()) return
        undo.addLast(edits)
        if (undo.size > HISTORY) undo.removeFirst()
        redo.clear()
        io.write(edits.map { GridWrite(it.key.categoryId, it.key.monthId, it.after) })
    }

    fun undo(io: BudgetGridIo): Boolean = replay(undo, redo, io, back = true)

    fun redo(io: BudgetGridIo): Boolean = replay(redo, undo, io, back = false)

    private fun replay(
        from: ArrayDeque<List<GridEdit>>,
        to: ArrayDeque<List<GridEdit>>,
        io: BudgetGridIo,
        back: Boolean,
    ): Boolean {
        val edits = from.removeLastOrNull() ?: return false
        to.addLast(edits)
        io.write(edits.map { GridWrite(it.key.categoryId, it.key.monthId, if (back) it.before else it.after) })
        edits.firstOrNull()?.let { focusOn(it.key) }
        return true
    }

    private companion object {
        const val HISTORY = 50
    }
}
