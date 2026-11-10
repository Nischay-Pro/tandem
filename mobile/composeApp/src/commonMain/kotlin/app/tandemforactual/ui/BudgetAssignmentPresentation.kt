package app.tandemforactual.ui

internal enum class BudgetAssignmentKind {
    TO_BUDGET,
    OVERBUDGETED,
    ALL_ASSIGNED,
}

internal data class BudgetAssignmentPresentation(
    val label: String,
    val amountMinor: Long,
    val kind: BudgetAssignmentKind,
)

internal fun presentBudgetAssignment(amountMinor: Long): BudgetAssignmentPresentation =
    when {
        amountMinor < 0 ->
            BudgetAssignmentPresentation(
                label = "Overbudgeted",
                amountMinor = if (amountMinor == Long.MIN_VALUE) Long.MAX_VALUE else -amountMinor,
                kind = BudgetAssignmentKind.OVERBUDGETED,
            )
        amountMinor > 0 ->
            BudgetAssignmentPresentation(
                label = "To Budget",
                amountMinor = amountMinor,
                kind = BudgetAssignmentKind.TO_BUDGET,
            )
        else ->
            BudgetAssignmentPresentation(
                label = "All money assigned",
                amountMinor = 0,
                kind = BudgetAssignmentKind.ALL_ASSIGNED,
            )
    }
