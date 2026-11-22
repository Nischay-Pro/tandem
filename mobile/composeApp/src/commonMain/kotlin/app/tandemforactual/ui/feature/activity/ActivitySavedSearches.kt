package app.tandemforactual.ui.feature.activity

import app.tandemforactual.ui.core.search.ActivitySearchField
import app.tandemforactual.ui.core.search.ActivitySearchFilter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

internal data class SavedActivitySearch(
    val budgetId: String,
    val name: String,
    val query: String,
    val filters: List<ActivitySearchFilter>,
)

internal fun encodeSavedActivitySearches(searches: List<SavedActivitySearch>): String {
    val retained =
        searches
            .groupBy(SavedActivitySearch::budgetId)
            .values
            .flatMap { budgetSearches -> budgetSearches.takeLast(20) }
    return JsonArray(
        retained.map { saved ->
            buildJsonObject {
                put("budgetId", JsonPrimitive(saved.budgetId))
                put("name", JsonPrimitive(saved.name.take(80)))
                put("query", JsonPrimitive(saved.query.take(300)))
                put(
                    "filters",
                    JsonArray(
                        saved.filters.take(20).map { filter ->
                            buildJsonObject {
                                put("field", JsonPrimitive(filter.field.name))
                                put("value", JsonPrimitive(filter.value.take(300)))
                                put("label", JsonPrimitive(filter.label.take(300)))
                            }
                        },
                    ),
                )
            }
        },
    ).toString()
}

internal fun decodeSavedActivitySearches(serialized: String): List<SavedActivitySearch> {
    val root =
        try {
            Json.parseToJsonElement(serialized) as? JsonArray ?: return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
    return root
        .mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val budgetId = item.stringValue("budgetId") ?: return@mapNotNull null
            val name = item.stringValue("name")?.take(80)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val query = item.stringValue("query")?.take(300) ?: ""
            val filters =
                (item["filters"] as? JsonArray)
                    .orEmpty()
                    .take(20)
                    .mapNotNull { filterElement ->
                        val filter = filterElement as? JsonObject ?: return@mapNotNull null
                        val field =
                            ActivitySearchField.entries.firstOrNull {
                                it.name == filter.stringValue("field")
                            } ?: return@mapNotNull null
                        val value = filter.stringValue("value")?.take(300) ?: ""
                        val label = filter.stringValue("label")?.take(300) ?: return@mapNotNull null
                        ActivitySearchFilter(field, value, label)
                    }
            SavedActivitySearch(budgetId, name, query, filters)
        }.groupBy(SavedActivitySearch::budgetId)
        .values
        .flatMap { budgetSearches -> budgetSearches.takeLast(20) }
}

private fun JsonObject.stringValue(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
