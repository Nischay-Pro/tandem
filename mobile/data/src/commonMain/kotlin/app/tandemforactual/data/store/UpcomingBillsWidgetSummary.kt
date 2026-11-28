package app.tandemforactual.data.store

import app.tandemforactual.data.actual.ActualDates

/** What the upcoming bills widget shows: the open budget's schedules due soon, soonest first; none while hidden. */
data class UpcomingBillsWidgetSummary(
    val budgetName: String,
    val bills: List<DueSchedule>,
    val amountsHidden: Boolean,
    val displaySettings: BudgetDisplaySettings?,
    val locked: Boolean = false,
)

/** How far ahead the widget looks for bills. */
const val UPCOMING_BILLS_WIDGET_DAYS = 14

/**
 * Null when no budget is open. Bills are open schedules due from [todayIso] through [UPCOMING_BILLS_WIDGET_DAYS] days
 * ahead; names and amounts both stay hidden while app lock, privacy mode or encryption protects the budget.
 */
suspend fun upcomingBillsWidgetSummary(
    repository: LocalBudgetRepository,
    preferences: Map<String, String>,
    todayIso: String,
): UpcomingBillsWidgetSummary? {
    val budget = widgetBudget(preferences) ?: return null
    if (budget.amountsHidden) {
        return UpcomingBillsWidgetSummary(budget.name, emptyList(), amountsHidden = true, displaySettings = null, locked = budget.locked)
    }
    val bills = dueSchedules(repository, budget.id, todayIso, ActualDates.addDays(todayIso, UPCOMING_BILLS_WIDGET_DAYS))
    return UpcomingBillsWidgetSummary(
        budget.name,
        bills,
        amountsHidden = false,
        displaySettings = repository.readBudgetDisplaySettings(budget.id),
    )
}
