package app.tandemforactual.ui.feature.budget.grid

/** A cell of the budget grid: a category row and a month column. Column -1 is the month before the first one shown. */
internal data class GridCell(
    val row: Int,
    val column: Int,
)

internal data class GridSize(
    val rows: Int,
    val columns: Int,
) {
    val empty: Boolean get() = rows <= 0 || columns <= 0

    fun clamp(cell: GridCell): GridCell = GridCell(cell.row.coerceIn(0, rows - 1), cell.column.coerceIn(0, columns - 1))
}

/** The cells between [anchor] and [focus], both included. */
internal data class GridRange(
    val anchor: GridCell,
    val focus: GridCell,
) {
    val rows: IntRange get() = minOf(anchor.row, focus.row)..maxOf(anchor.row, focus.row)
    val columns: IntRange get() = minOf(anchor.column, focus.column)..maxOf(anchor.column, focus.column)
    val single: Boolean get() = anchor == focus

    operator fun contains(cell: GridCell): Boolean = cell.row in rows && cell.column in columns

    fun cells(): List<GridCell> = rows.flatMap { row -> columns.map { GridCell(row, it) } }
}

/** One amount to write, with the amount it replaces so it can be undone. */
internal data class GridChange(
    val cell: GridCell,
    val before: Long,
    val after: Long,
)

internal fun GridCell.moved(
    rows: Int,
    columns: Int,
    size: GridSize,
): GridCell = size.clamp(GridCell(row + rows, column + columns))

/** Tab order: along the row, then on to the next row's first month. */
internal fun GridCell.stepped(
    forward: Boolean,
    size: GridSize,
): GridCell {
    val index = (row * size.columns + column + if (forward) 1 else -1).coerceIn(0, size.rows * size.columns - 1)
    return GridCell(index / size.columns, index % size.columns)
}

/** Only real changes to amounts that can be budgeted: unknown cells, equal amounts and negatives are left alone. */
internal fun gridChanges(
    targets: List<Pair<GridCell, Long?>>,
    budgeted: (GridCell) -> Long?,
): List<GridChange> =
    targets.mapNotNull { (cell, after) ->
        val before = budgeted(cell)
        if (after == null || before == null || after < 0 || after == before) null else GridChange(cell, before, after)
    }

/** Spreadsheet fill down: the top row copied into the rows below, or the row above copied in for a single row. */
internal fun GridRange.fillDown(budgeted: (GridCell) -> Long?): List<GridChange> {
    val source = if (rows.first == rows.last) rows.first - 1 else rows.first
    if (source < 0) return emptyList()
    val targets = cells().filter { it.row != source }.map { it to budgeted(GridCell(source, it.column)) }
    return gridChanges(targets, budgeted)
}

/** Spreadsheet fill right: the left month copied into the months after it, or last month copied in for a single month. */
internal fun GridRange.fillRight(budgeted: (GridCell) -> Long?): List<GridChange> {
    val source = if (columns.first == columns.last) columns.first - 1 else columns.first
    val targets = cells().filter { it.column != source }.map { it to budgeted(GridCell(it.row, source)) }
    return gridChanges(targets, budgeted)
}

/** Each cell gets what its category had budgeted the month before. */
internal fun GridRange.copyPrevious(budgeted: (GridCell) -> Long?): List<GridChange> =
    gridChanges(cells().map { it to budgeted(GridCell(it.row, it.column - 1)) }, budgeted)

internal fun GridRange.setTo(
    amount: (GridCell) -> Long?,
    budgeted: (GridCell) -> Long?,
): List<GridChange> = gridChanges(cells().map { it to amount(it) }, budgeted)

/** Rows of tab-separated amounts, the way spreadsheets copy a block of cells. */
internal fun GridRange.toTsv(text: (GridCell) -> String): String =
    rows.joinToString("\n") { row -> columns.joinToString("\t") { text(GridCell(row, it)) } }

/**
 * Tab-separated text laid out from the top-left of [range]; a single value fills the whole range. Cells past the grid's
 * edge are dropped, and nothing is written if any value can't be read as an amount.
 */
internal fun pasteTsv(
    text: String,
    range: GridRange,
    size: GridSize,
    parse: (String) -> Long?,
    budgeted: (GridCell) -> Long?,
): List<GridChange> {
    val lines = text.replace("\r\n", "\n").trimEnd('\n').split('\n').map { it.split('\t') }
    val values = lines.map { line -> line.map { parse(it.trim()) ?: return emptyList() } }
    if (values.size == 1 && values.single().size == 1) return range.setTo({ values.single().single() }, budgeted)
    val top = GridCell(range.rows.first, range.columns.first)
    val targets =
        values.flatMapIndexed { row, line -> line.mapIndexed { column, amount -> GridCell(top.row + row, top.column + column) to amount } }
    return gridChanges(targets.filter { (cell) -> cell.row < size.rows && cell.column < size.columns }, budgeted)
}
