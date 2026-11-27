package app.tandemforactual.data.store

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The summary Tandem leaves for widgets that run outside the app, as JSON with amounts already formatted: this month's
 * totals and expense category balances, or only the budget's name while app lock, privacy mode or encryption protect it.
 */
suspend fun widgetSnapshotJson(
    repository: LocalBudgetRepository,
    preferences: Map<String, String>,
    todayIso: String,
    format: (Long, BudgetDisplaySettings?) -> String,
): String {
    val budget = widgetBudget(preferences)
    val summary = budget?.takeUnless { it.amountsHidden }?.let { budgetWidgetSummary(repository, preferences, todayIso) }
    val categories = budget?.takeUnless { it.amountsHidden }?.let { widgetCategoryChoices(repository, preferences, todayIso) }.orEmpty()
    return buildJsonObject {
        put("version", 1)
        put("month", todayIso.take(7))
        put(
            "state",
            when {
                budget == null -> "none"
                budget.locked -> "locked"
                budget.amountsHidden -> "hidden"
                else -> "ready"
            },
        )
        budget?.let { put("budgetName", it.name) }
        if (summary != null) putAmounts(summary, categories, format)
    }.toString()
}

/** A category's balance as widgets show it: overspending says "over" so it never relies on colour alone. */
fun categoryBalanceText(
    balanceMinor: Long,
    format: (Long) -> String,
): String = if (balanceMinor < 0) "${format(-balanceMinor)} over" else format(balanceMinor)

private fun JsonObjectBuilder.putAmounts(
    summary: BudgetWidgetSummary,
    categories: List<WidgetCategory>,
    format: (Long, BudgetDisplaySettings?) -> String,
) {
    val settings = summary.displaySettings
    summary.readyToAssignMinor?.let {
        put("readyToAssign", format(it, settings))
        put("readyToAssignNegative", it < 0)
    }
    if (summary.tracking) put("readyLabel", summary.headline)
    summary.spentMinor?.let { put("spent", format(it, settings)) }
    putJsonArray("categories") {
        categories.forEach { category ->
            addJsonObject {
                put("id", category.id)
                put("name", category.name)
                put("group", category.groupName)
                put("amount", format(category.balanceMinor, settings))
                put("text", categoryBalanceText(category.balanceMinor) { format(it, settings) })
                put("overspent", category.balanceMinor < 0)
            }
        }
    }
}
