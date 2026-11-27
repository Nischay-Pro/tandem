package app.tandemforactual.ui.feature.budget.grid

/** Which of a month's figures the budget grid shows in each cell. */
internal enum class GridValue(
    val label: String,
) {
    ALL("All"),
    BUDGETED("Budgeted"),
    SPENT("Spent"),
    BALANCE("Balance"),
    ;

    /** The figures shown: all three in order, or just the one chosen. */
    val columns: List<GridValue> get() = if (this == ALL) listOf(BUDGETED, SPENT, BALANCE) else listOf(this)
}
