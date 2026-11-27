package app.tandemforactual.data.store

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * What a home-screen widget shows for the selected budget; amounts are null when unknown or hidden.
 * For a tracking budget, [readyToAssignMinor] is what was saved this month and [headline] says so.
 */
data class BudgetWidgetSummary(
    val budgetName: String,
    val readyToAssignMinor: Long?,
    val spentMinor: Long?,
    val amountsHidden: Boolean,
    val displaySettings: BudgetDisplaySettings?,
    val locked: Boolean = false,
    val tracking: Boolean = false,
) {
    val headline: String get() = if (tracking) "Saved this month" else "Ready to assign"
}

private val WIDGET_CLOSING_PREFERENCES = listOf("biometricLockEnabled", "selectedBudgetEncrypted")

internal class WidgetBudget(
    val id: String,
    val name: String,
    val closed: Boolean,
    val amountsHidden: Boolean,
    val locked: Boolean,
)

/** The open budget as widgets see it: closed while app lock or encryption protects it, and amounts hidden then or in privacy mode. */
internal fun widgetBudget(preferences: Map<String, String>): WidgetBudget? {
    val id = preferences["selectedBudgetId"]?.takeIf(String::isNotBlank) ?: return null
    val name = preferences["selectedBudget"]?.takeIf(String::isNotBlank) ?: "Budget"
    val closed = WIDGET_CLOSING_PREFERENCES.any { preferences[it] == "true" }
    val locked = preferences["selectedBudgetEncrypted"] == "true"
    return WidgetBudget(id, name, closed, amountsHidden = closed || preferences["privacyModeEnabled"] == "true", locked = locked)
}

/** Ready to assign and net spending this month from an Actual budget month record; refunds beyond spending count as zero. */
fun budgetMonthTotals(budgetMonthJson: String): Pair<Long?, Long?> {
    val month = runCatching { Json.parseToJsonElement(budgetMonthJson) as? JsonObject }.getOrNull() ?: return null to null

    fun long(name: String) = (month[name] as? JsonPrimitive)?.longOrNull
    return long("toBudget") to long("totalSpent")?.let { (-it).coerceAtLeast(0) }
}

/** A tracking month's savings, Actual's `real-saved`: income received less net spending. */
fun trackingSavedMinor(budgetMonthJson: String): Long? {
    val month = runCatching { Json.parseToJsonElement(budgetMonthJson) as? JsonObject }.getOrNull() ?: return null

    fun long(name: String) = (month[name] as? JsonPrimitive)?.longOrNull
    val income = long("totalIncome") ?: return null
    val spent = long("totalSpent") ?: return null
    return income + spent
}

/** Null when no budget is open. Amounts are for the month of [todayIso] and stay hidden while app lock, privacy mode or encryption protects the budget. */
suspend fun budgetWidgetSummary(
    repository: LocalBudgetRepository,
    preferences: Map<String, String>,
    todayIso: String,
): BudgetWidgetSummary? {
    val budget = widgetBudget(preferences) ?: return null
    if (budget.amountsHidden) {
        return BudgetWidgetSummary(budget.name, null, null, amountsHidden = true, displaySettings = null, locked = budget.locked)
    }
    val record = repository.findRecord(budget.id, "budget-month", todayIso.take(7))
    val json = record?.localJson ?: record?.remoteJson
    val (ready, spent) = json?.let(::budgetMonthTotals) ?: (null to null)
    val settings = repository.readBudgetDisplaySettings(budget.id)
    val tracking = settings?.budgetType == "tracking"
    val headline = if (tracking) json?.let(::trackingSavedMinor) else ready
    return BudgetWidgetSummary(budget.name, headline, spent, amountsHidden = false, displaySettings = settings, tracking = tracking)
}
