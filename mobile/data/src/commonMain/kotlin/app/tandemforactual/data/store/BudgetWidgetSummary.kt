package app.tandemforactual.data.store

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** What a home-screen widget shows for the selected budget; amounts are null when unknown or hidden. */
data class BudgetWidgetSummary(
    val budgetName: String,
    val readyToAssignMinor: Long?,
    val spentMinor: Long?,
    val amountsHidden: Boolean,
    val displaySettings: BudgetDisplaySettings?,
)

private val WIDGET_HIDING_PREFERENCES = listOf("biometricLockEnabled", "privacyModeEnabled", "selectedBudgetEncrypted")

/** Ready to assign and net spending this month from an Actual budget month record; refunds beyond spending count as zero. */
fun budgetMonthTotals(budgetMonthJson: String): Pair<Long?, Long?> {
    val month = runCatching { Json.parseToJsonElement(budgetMonthJson) as? JsonObject }.getOrNull() ?: return null to null

    fun long(name: String) = (month[name] as? JsonPrimitive)?.longOrNull
    return long("toBudget") to long("totalSpent")?.let { (-it).coerceAtLeast(0) }
}

/** Null when no budget is open. Amounts are for the month of [todayIso] and stay hidden while app lock, privacy mode or encryption protects the budget. */
suspend fun budgetWidgetSummary(
    repository: LocalBudgetRepository,
    preferences: Map<String, String>,
    todayIso: String,
): BudgetWidgetSummary? {
    val budgetId = preferences["selectedBudgetId"]?.takeIf(String::isNotBlank) ?: return null
    val name = preferences["selectedBudget"]?.takeIf(String::isNotBlank) ?: "Budget"
    if (WIDGET_HIDING_PREFERENCES.any { preferences[it] == "true" }) {
        return BudgetWidgetSummary(name, null, null, amountsHidden = true, displaySettings = null)
    }
    val record = repository.findRecord(budgetId, "budget-month", todayIso.take(7))
    val (ready, spent) = (record?.localJson ?: record?.remoteJson)?.let(::budgetMonthTotals) ?: (null to null)
    return BudgetWidgetSummary(name, ready, spent, amountsHidden = false, displaySettings = repository.readBudgetDisplaySettings(budgetId))
}
