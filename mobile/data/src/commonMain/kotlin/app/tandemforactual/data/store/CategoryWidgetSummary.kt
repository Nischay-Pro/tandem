package app.tandemforactual.data.store

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** A category with what's left to spend in it this month; a negative balance means it's overspent. */
data class WidgetCategory(
    val id: String,
    val name: String,
    val groupName: String,
    val balanceMinor: Long,
)

/** What a "left to spend" widget shows; [categories] is empty while amounts are hidden. */
data class CategoryWidgetSummary(
    val budgetName: String,
    val categories: List<WidgetCategory>,
    val amountsHidden: Boolean,
    val displaySettings: BudgetDisplaySettings?,
    val locked: Boolean = false,
)

data class WidgetAccount(
    val id: String,
    val name: String,
)

/** Expense categories in budget order with their balance in one Actual budget month; income and hidden ones are left out. */
fun budgetMonthCategories(budgetMonthJson: String): List<WidgetCategory> {
    val month = runCatching { Json.parseToJsonElement(budgetMonthJson) as? JsonObject }.getOrNull() ?: return emptyList()
    val groups = month["categoryGroups"] as? JsonArray ?: return emptyList()
    return groups
        .mapNotNull { it as? JsonObject }
        .filterNot { it.boolean("is_income") || it.boolean("hidden") }
        .flatMap { group ->
            val groupName = group.string("name").orEmpty()
            (group["categories"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { category -> category to groupName } }
        }.filterNot { (category, _) -> category.boolean("is_income") || category.boolean("hidden") }
        .mapNotNull { (category, groupName) ->
            val id = category.string("id") ?: return@mapNotNull null
            val name = category.string("name") ?: return@mapNotNull null
            val balance = (category["balance"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            WidgetCategory(id, name, groupName, balance)
        }
}

/** The chosen categories, in the order chosen, for the month of [todayIso]; ones no longer in the budget are left out. */
suspend fun categoryWidgetSummary(
    repository: LocalBudgetRepository,
    preferences: Map<String, String>,
    todayIso: String,
    categoryIds: List<String>,
): CategoryWidgetSummary? {
    val budget = widgetBudget(preferences) ?: return null
    if (budget.amountsHidden) {
        return CategoryWidgetSummary(budget.name, emptyList(), amountsHidden = true, displaySettings = null, locked = budget.locked)
    }
    val byId = monthCategories(repository, budget.id, todayIso).associateBy(WidgetCategory::id)
    return CategoryWidgetSummary(
        budget.name,
        categoryIds.distinct().mapNotNull(byId::get),
        amountsHidden = false,
        displaySettings = repository.readBudgetDisplaySettings(budget.id),
    )
}

/** Categories a widget can be set to show, or null when no budget is open or app lock or encryption keeps it closed. */
suspend fun widgetCategoryChoices(
    repository: LocalBudgetRepository,
    preferences: Map<String, String>,
    todayIso: String,
): List<WidgetCategory>? {
    val budget = widgetBudget(preferences)?.takeUnless { it.closed } ?: return null
    return monthCategories(repository, budget.id, todayIso)
}

/** Open accounts a quick-add widget can start from, or null when no budget is open or app lock or encryption keeps it closed. */
suspend fun widgetAccountChoices(
    repository: LocalBudgetRepository,
    preferences: Map<String, String>,
): List<WidgetAccount>? {
    val budget = widgetBudget(preferences)?.takeUnless { it.closed } ?: return null
    return repository
        .findRecords(budget.id, "account")
        .mapNotNull { record -> (record.localJson ?: record.remoteJson)?.let(::parseObject) }
        .filterNot { it.boolean("closed") }
        .sortedWith(compareBy({ (it["sort_order"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0 }, { it.string("name") }))
        .mapNotNull { account -> account.string("id")?.let { id -> WidgetAccount(id, account.string("name") ?: id) } }
}

private suspend fun monthCategories(
    repository: LocalBudgetRepository,
    budgetId: String,
    todayIso: String,
): List<WidgetCategory> {
    val record = repository.findRecord(budgetId, "budget-month", todayIso.take(7)) ?: return emptyList()
    return (record.localJson ?: record.remoteJson)?.let(::budgetMonthCategories).orEmpty()
}

private fun parseObject(json: String): JsonObject? = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull()

private fun JsonObject.boolean(name: String): Boolean = (this[name] as? JsonPrimitive)?.booleanOrNull == true

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
