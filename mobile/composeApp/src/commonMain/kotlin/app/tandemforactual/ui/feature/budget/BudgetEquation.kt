package app.tandemforactual.ui.feature.budget

import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import kotlin.math.abs

/** One figure in a month's equation; [subtract] puts a minus before it. */
internal data class EquationTerm(
    val label: String,
    val minor: Long,
    val subtract: Boolean = false,
)

/** A month's figures written as the sum that produces its result. */
internal data class BudgetEquation(
    val terms: List<EquationTerm>,
    val result: EquationTerm,
)

/**
 * Envelope: available − overspent last month − budgeted − held = to budget, with budgeted taken as what makes the
 * sum come out, so it agrees with to budget even while edits wait to sync. Tracking: income − spent = saved.
 */
internal fun budgetEquation(
    month: LiveBudgetMonthSummary,
    tracking: Boolean,
): BudgetEquation? {
    if (tracking) {
        val income = month.totalIncomeMinor ?: return null
        val spent = abs(month.totalSpentMinor ?: return null)
        return BudgetEquation(
            listOf(EquationTerm("Income", income), EquationTerm("Spent", spent, subtract = true)),
            EquationTerm("Saved", income - spent),
        )
    }
    val available = month.incomeAvailableMinor ?: return null
    val toBudget = month.toBudgetMinor ?: return null
    val overspent = abs(month.lastMonthOverspentMinor ?: 0L)
    val held = month.heldForNextMonthMinor ?: 0L
    val terms =
        listOfNotNull(
            EquationTerm("Available", available),
            EquationTerm("Overspent last month", overspent, subtract = true).takeIf { overspent != 0L },
            EquationTerm("Budgeted", available - overspent - held - toBudget, subtract = true),
            EquationTerm("Held", held, subtract = true).takeIf { held != 0L },
        )
    return BudgetEquation(terms, EquationTerm("To budget", toBudget))
}
