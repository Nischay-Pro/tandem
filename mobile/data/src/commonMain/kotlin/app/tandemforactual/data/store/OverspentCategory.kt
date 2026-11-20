package app.tandemforactual.data.store

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

data class OverspentCategory(
    val id: String,
    val name: String,
    val balanceMinor: Long,
)

/** Expense categories with a negative balance in one Actual budget month; income and hidden categories are left out. */
fun overspentCategories(budgetMonthJson: String): List<OverspentCategory> {
    val month = runCatching { Json.parseToJsonElement(budgetMonthJson) as? JsonObject }.getOrNull() ?: return emptyList()
    val groups = month["categoryGroups"] as? JsonArray ?: return emptyList()
    return groups
        .mapNotNull { it as? JsonObject }
        .filterNot { it.boolean("is_income") || it.boolean("hidden") }
        .flatMap { group -> (group["categories"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject } }
        .filterNot { it.boolean("is_income") || it.boolean("hidden") }
        .mapNotNull { category ->
            val id = (category["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val name = (category["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val balance = (category["balance"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            if (balance < 0) OverspentCategory(id, name, balance) else null
        }
}

/** Which overspent categories are new since the last alert, given the ids already alerted this month. */
fun newlyOverspent(
    current: List<OverspentCategory>,
    alreadyAlerted: Set<String>,
): List<OverspentCategory> = current.filterNot { it.id in alreadyAlerted }

fun overspendingMessage(categories: List<OverspentCategory>): String? {
    val names = categories.map { it.name }
    return when (names.size) {
        0 -> null
        1 -> "${names[0]} is over budget this month."
        2 -> "${names[0]} and ${names[1]} are over budget this month."
        else -> "${names[0]}, ${names[1]}, and ${names.size - 2} more are over budget this month."
    }
}

/**
 * The alert to show after a background sync, or null. Remembers which categories were over budget so a category
 * alerts once, and again only if it recovers and goes over later.
 */
suspend fun overspendingAlert(
    repository: LocalBudgetRepository,
    preferences: AppPreferencesStore,
    budgetId: String,
    monthId: String,
): String? {
    val record = repository.findRecord(budgetId, "budget-month", monthId) ?: return null
    val current = overspentCategories(record.localJson ?: record.remoteJson ?: return null)
    val key = "internal:overspent-categories:$budgetId:$monthId"
    val previous =
        preferences
            .read()[key]
            .orEmpty()
            .split(',')
            .filter(String::isNotEmpty)
            .toSet()
    preferences.putValue(key, current.joinToString(",") { it.id })
    return overspendingMessage(newlyOverspent(current, previous))
}

private fun JsonObject.boolean(name: String): Boolean = (this[name] as? JsonPrimitive)?.booleanOrNull == true
