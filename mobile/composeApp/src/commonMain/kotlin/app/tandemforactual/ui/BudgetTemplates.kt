package app.tandemforactual.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal data class BudgetTemplate(
    val budgetId: String,
    val name: String,
    val assignments: List<Pair<String, Long>>,
)

internal fun encodeBudgetTemplates(templates: List<BudgetTemplate>): String =
    JsonArray(
        templates.takeLast(40).map { template ->
            buildJsonObject {
                put("budgetId", template.budgetId)
                put("name", template.name.take(60))
                put(
                    "assignments",
                    JsonArray(
                        template.assignments.take(500).map { (categoryId, amountMinor) ->
                            buildJsonObject {
                                put("categoryId", categoryId)
                                put("amountMinor", amountMinor)
                            }
                        },
                    ),
                )
            }
        },
    ).toString()

internal fun decodeBudgetTemplates(serialized: String): List<BudgetTemplate> {
    val root =
        try {
            Json.parseToJsonElement(serialized) as? JsonArray ?: return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
    return root
        .mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val budgetId = item.text("budgetId")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val name = item.text("name")?.take(60)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val assignments =
                (item["assignments"] as? JsonArray)
                    .orEmpty()
                    .take(500)
                    .mapNotNull { assignmentElement ->
                        val assignment = assignmentElement as? JsonObject ?: return@mapNotNull null
                        val categoryId = assignment.text("categoryId")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                        val amount =
                            (assignment["amountMinor"] as? JsonPrimitive)
                                ?.longOrNull
                                ?.takeIf { it >= 0 } ?: return@mapNotNull null
                        categoryId to amount
                    }.distinctBy { it.first }
            if (assignments.isEmpty()) null else BudgetTemplate(budgetId, name, assignments)
        }.takeLast(40)
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
