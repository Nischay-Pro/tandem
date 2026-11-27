package app.tandemforactual.data.store

data class OverspentCategory(
    val id: String,
    val name: String,
    val balanceMinor: Long,
)

/** Expense categories with a negative balance in one Actual budget month; income and hidden categories are left out. */
fun overspentCategories(budgetMonthJson: String): List<OverspentCategory> =
    budgetMonthCategories(budgetMonthJson)
        .filter { it.balanceMinor < 0 }
        .map { OverspentCategory(it.id, it.name, it.balanceMinor) }

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
