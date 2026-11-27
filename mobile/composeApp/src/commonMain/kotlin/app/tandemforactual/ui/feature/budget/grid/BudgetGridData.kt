package app.tandemforactual.ui.feature.budget.grid

/** One category's figures for one month; [spent] is what went out, never negative. */
internal data class GridAmounts(
    val budgeted: Long,
    val spent: Long,
    val balance: Long,
    val pending: Boolean = false,
)

/** A cell named by category and month, so focus survives reordering and the months sliding past. */
internal data class GridKey(
    val categoryId: String,
    val monthId: String,
)

internal data class GridWrite(
    val categoryId: String,
    val monthId: String,
    val amountMinor: Long,
)

/**
 * What the grid shows: category [rows] in screen order and month [columns]. [previousMonth] is the calendar month before
 * the first column, so "copy last month" works there; [earlierMonth] and [laterMonth] are the budget months the arrows
 * slide to past either edge.
 */
internal class BudgetGridData(
    val rows: List<String>,
    val columns: List<String>,
    val previousMonth: String?,
    val earlierMonth: String?,
    val laterMonth: String?,
    private val amounts: (GridKey) -> GridAmounts?,
) {
    val size = GridSize(rows.size, columns.size)

    fun key(cell: GridCell): GridKey? {
        val month = if (cell.column == -1) previousMonth else columns.getOrNull(cell.column)
        val category = rows.getOrNull(cell.row)
        return if (month == null || category == null) null else GridKey(category, month)
    }

    fun cell(key: GridKey?): GridCell? {
        if (key == null) return null
        val row = rows.indexOf(key.categoryId)
        val column = columns.indexOf(key.monthId)
        return if (row < 0 || column < 0) null else GridCell(row, column)
    }

    fun amounts(key: GridKey): GridAmounts? = amounts.invoke(key)

    fun amounts(cell: GridCell): GridAmounts? = key(cell)?.let(amounts)

    fun budgeted(cell: GridCell): Long? = amounts(cell)?.budgeted
}

/** How the grid reads and writes outside itself: amounts in the budget's format, the outbox and the clipboard. */
internal interface BudgetGridIo {
    val editable: Boolean

    fun format(amountMinor: Long): String

    fun parse(text: String): Long?

    fun write(writes: List<GridWrite>)

    fun showMonth(monthId: String)

    fun copy(text: String)

    fun paste(): String?
}
