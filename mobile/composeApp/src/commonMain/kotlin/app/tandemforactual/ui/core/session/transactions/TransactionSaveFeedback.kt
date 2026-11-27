package app.tandemforactual.ui.core.session.transactions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** What a transaction puts in one category: its signed amount in a budget month (`yyyy-MM`). */
internal data class CategoryEntry(
    val categoryId: String,
    val month: String,
    val amountMinor: Long,
)

internal data class CategorySaveFeedback(
    val title: String,
    val text: String,
    val over: Boolean,
    val detail: String?,
)

/** The category a save is reported against, the one holding the most money, and how many other categories it touched. */
internal fun mainCategory(entries: List<CategoryEntry>): Pair<CategoryEntry, Int>? {
    val byCategory = entries.filter { it.categoryId.isNotBlank() }.groupBy { it.categoryId }
    val main = byCategory.values.maxByOrNull { lines -> lines.sumOf { kotlin.math.abs(it.amountMinor) } }?.first() ?: return null
    return main to byCategory.size - 1
}

/** How much a save moved [categoryId]'s money in [month]: what is there now less what was there before. */
internal fun categoryChange(
    categoryId: String,
    month: String,
    before: List<CategoryEntry>,
    after: List<CategoryEntry>,
): Long {
    fun List<CategoryEntry>.total() = filter { it.categoryId == categoryId && it.month == month }.sumOf { it.amountMinor }
    return after.total() - before.total()
}

/** A category's balance in a budget-month record, or null when the month doesn't list it. */
internal fun categoryMonthBalance(
    monthJson: String?,
    categoryId: String,
): Long? {
    val month = monthJson?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return null
    return (month["categoryGroups"] as? JsonArray)
        .orEmpty()
        .asSequence()
        .mapNotNull { it as? JsonObject }
        .flatMap { (it["categories"] as? JsonArray).orEmpty().asSequence() }
        .mapNotNull { it as? JsonObject }
        .firstOrNull { (it["id"] as? JsonPrimitive)?.contentOrNull == categoryId }
        ?.let { (it["balance"] as? JsonPrimitive)?.longOrNull }
}

/**
 * The category's balance once the save is in: the refreshed month when a sync already brought it back, otherwise the
 * balance before plus the change, which is still waiting to sync. The flag says whether it is that estimate.
 */
internal fun balanceAfterSave(
    categoryId: String,
    beforeJson: String?,
    afterJson: String?,
    changeMinor: Long,
): Pair<Long, Boolean>? {
    val after = categoryMonthBalance(afterJson, categoryId)
    if (after != null && afterJson != beforeJson) return after to false
    val before = categoryMonthBalance(beforeJson, categoryId) ?: return null
    return before + changeMinor to (changeMinor != 0L)
}

/** The confirmation shown after a save; it says "over" in words, so it never relies on colour, and hides amounts when asked. */
internal fun categorySaveFeedback(
    categoryName: String,
    otherCategories: Int,
    balanceMinor: Long,
    estimated: Boolean,
    hideAmounts: Boolean,
    monthPhrase: String,
    format: (Long) -> String,
): CategorySaveFeedback {
    val over = balanceMinor < 0
    val text =
        when {
            hideAmounts && over -> "$categoryName is over budget $monthPhrase"
            hideAmounts -> "$categoryName is within budget $monthPhrase"
            over -> "$categoryName · over by ${format(-balanceMinor)} $monthPhrase"
            else -> "$categoryName · ${format(balanceMinor)} left $monthPhrase"
        }
    return CategorySaveFeedback(
        title = if (otherCategories > 0) "Saved across ${otherCategories + 1} categories" else "Saved",
        text = text,
        over = over,
        detail = "Includes changes waiting to sync".takeIf { estimated },
    )
}
