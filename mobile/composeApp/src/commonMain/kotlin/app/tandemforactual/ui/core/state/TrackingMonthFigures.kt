package app.tandemforactual.ui.core.state

/** A tracking budget's month: income expected and received, spending budgeted and spent, and what was saved against the plan. */
internal data class TrackingMonthFigures(
    val expectedIncomeMinor: Long,
    val receivedIncomeMinor: Long,
    val budgetedMinor: Long,
    val spentMinor: Long,
    val overspentCategories: Int,
    val overspentMinor: Long,
) {
    val plannedSavingsMinor: Long get() = expectedIncomeMinor - budgetedMinor
    val savedMinor: Long get() = receivedIncomeMinor - spentMinor
    val savedAgainstPlanMinor: Long get() = savedMinor - plannedSavingsMinor
}

/** Actual's tracking summary (`total-budget-income`, `total-budgeted`, `real-saved` and the rest) from the shown groups. */
internal fun trackingMonthFigures(groups: List<LiveCategoryGroupSummary>): TrackingMonthFigures {
    val income = groups.filter { it.isIncome }
    val expenses = groups.filterNot { it.isIncome }.flatMap { it.categories }
    val overspent = expenses.filter { it.balanceMinor < 0 }
    return TrackingMonthFigures(
        expectedIncomeMinor = income.sumOf { it.assignedMinor },
        receivedIncomeMinor = income.sumOf { it.activityMinor },
        budgetedMinor = expenses.sumOf { it.assignedMinor },
        spentMinor = -expenses.sumOf { it.activityMinor },
        overspentCategories = overspent.size,
        overspentMinor = -overspent.sumOf { it.balanceMinor },
    )
}

/** Whether the open budget is a tracking budget, where categories get a plan instead of money. */
internal val AppState.isTrackingBudget: Boolean
    get() = selectedBudgetId.isNotBlank() && budgetDisplaySettings?.budgetType == "tracking"
