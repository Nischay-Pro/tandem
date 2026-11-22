package app.tandemforactual.data.actual

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Turns the app's rule form (each condition with one of `text`, `ids`, `amountMinor` or `range`, plus `inflow`/`outflow`, and
 * each action with `text` or `cleared`) into Actual's `value` and `options`. Rules already in Actual's form pass through.
 */
object RuleWire {
    fun toActual(rule: JsonObject): JsonObject {
        val conditions = (rule["conditions"] as? JsonArray)?.let { JsonArray(it.map { c -> condition(c.jsonObject) }) }
        val actions = (rule["actions"] as? JsonArray)?.let { JsonArray(it.map { a -> action(a.jsonObject) }) }
        return JsonObject(
            rule +
                listOfNotNull(conditions?.let { "conditions" to it }, actions?.let { "actions" to it }),
        )
    }

    private fun condition(condition: JsonObject): JsonObject {
        if ("value" in condition) return condition
        val value = condition["ids"] ?: condition["range"] ?: condition["amountMinor"] ?: condition["text"] ?: JsonNull
        val options = listOf("inflow", "outflow").filter { (condition[it] as? JsonPrimitive)?.content == "true" }
        return JsonObject(
            condition.filterKeys { it !in WIRE_KEYS } +
                ("value" to value) +
                listOfNotNull(
                    options.takeIf { it.isNotEmpty() }?.let { "options" to JsonObject(it.associateWith { JsonPrimitive(true) }) },
                ),
        )
    }

    private fun action(action: JsonObject): JsonObject {
        if ("value" in action) return action
        return JsonObject(
            action.filterKeys { it != "text" && it != "cleared" } + ("value" to (action["cleared"] ?: action["text"] ?: JsonNull)),
        )
    }

    /** A rule as Actual's `rules` table stores it, with internal column names, for loading through [ActualRules]. */
    fun row(rule: JsonObject): Map<String, Any?> {
        val actual = toActual(rule)
        return mapOf(
            "id" to "preview",
            "stage" to null,
            "conditions_op" to ((actual["conditionsOp"] as? JsonPrimitive)?.content ?: "and"),
            "conditions" to internal(actual["conditions"] as? JsonArray ?: JsonArray(emptyList())),
            "actions" to "[]",
        )
    }

    fun internal(items: JsonArray): String =
        stringifyJson(
            JsonArray(
                items.map { item ->
                    val obj = item.jsonObject
                    val field = (obj["field"] as? JsonPrimitive)?.content
                    internalFields[field]?.let { JsonObject(obj + ("field" to JsonPrimitive(it))) } ?: obj
                },
            ),
        )

    private val internalFields =
        mapOf(
            "is_parent" to "isParent",
            "is_child" to "isChild",
            "account" to "acct",
            "imported_id" to "financial_id",
            "imported_payee" to "imported_description",
            "transfer_id" to "transferred_id",
            "payee" to "description",
        )

    private val WIRE_KEYS = setOf("text", "ids", "amountMinor", "range", "inflow", "outflow")
}
