package app.tandemforactual.data.actual

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Loads rules the way Actual's `loadRules` and `Rule.serialize` do, dropping rules Actual would reject. */
internal object ActualRules {
    private val publicFields =
        mapOf(
            "isParent" to "is_parent",
            "isChild" to "is_child",
            "acct" to "account",
            "financial_id" to "imported_id",
            "imported_description" to "imported_payee",
            "transferred_id" to "transfer_id",
            "description" to "payee",
        )

    private val fieldTypes =
        mapOf(
            "imported_payee" to "string",
            "payee" to "id",
            "payee_name" to "string",
            "date" to "date",
            "notes" to "string",
            "amount" to "number",
            "category" to "id",
            "category_group" to "id",
            "account" to "id",
            "cleared" to "boolean",
            "reconciled" to "boolean",
            "saved" to "saved",
            "transfer" to "boolean",
            "parent" to "boolean",
        )

    private val typeOps =
        mapOf(
            "date" to setOf("is", "isapprox", "isbetween", "gt", "gte", "lt", "lte"),
            "id" to setOf("is", "contains", "matches", "oneOf", "isNot", "doesNotContain", "notOneOf", "onBudget", "offBudget"),
            "saved" to emptySet(),
            "string" to setOf("is", "contains", "matches", "oneOf", "isNot", "doesNotContain", "notOneOf", "hasTags", "hasAnyTag"),
            "number" to setOf("is", "isapprox", "isbetween", "gt", "gte", "lt", "lte"),
            "boolean" to setOf("is"),
        )
    private val nullableTypes = setOf("id", "string")

    private val disallowedOps =
        mapOf(
            "imported_payee" to setOf("hasTags", "hasAnyTag"),
            "payee" to setOf("onBudget", "offBudget"),
            "notes" to setOf("oneOf", "notOneOf"),
            "category" to setOf("onBudget", "offBudget"),
            "category_group" to setOf("onBudget", "offBudget"),
        )
    private val internalOps = mapOf("category" to setOf("and"), "category_group" to setOf("and"))
    private val actionOps = setOf("set", "set-split-amount", "link-schedule", "prepend-notes", "append-notes", "delete-transaction")

    private class RuleError : Exception()

    fun load(
        rows: List<Row>,
        mappings: Map<String, String>,
    ): List<JsonObject> =
        rows
            .filter { it["conditions"] != null && it["actions"] != null }
            .mapNotNull { row ->
                try {
                    serialize(row, mappings)
                } catch (_: RuleError) {
                    null
                }
            }

    private fun serialize(
        row: Row,
        mappings: Map<String, String>,
    ): JsonObject {
        val stage = (row["stage"] as String?).let { if (it == "cleanup" || it == "modify") "pre" else it }
        val fields = LinkedHashMap<String, JsonElement>()
        fields["id"] = jsonString(row["id"])
        fields["stage"] = jsonString(stage)
        (row["conditions_op"] as String?)?.let { fields["conditionsOp"] = JsonPrimitive(it) }
        fields["conditions"] = JsonArray(parse(row["conditions"]).map { condition(it, mappings) })
        fields["actions"] = JsonArray(parse(row["actions"]).map { action(it, mappings) })
        return JsonObject(fields)
    }

    private fun parse(value: Any?): List<JsonObject> {
        val array = runCatching { Json.parseToJsonElement(value as String).jsonArray }.getOrElse { throw RuleError() }
        return array.map { item ->
            val obj = runCatching { item.jsonObject }.getOrElse { throw RuleError() }
            val field = (obj["field"] as? JsonPrimitive)?.contentOrNull
            if (field != null && field in publicFields) JsonObject(obj + ("field" to JsonPrimitive(publicFields.getValue(field)))) else obj
        }
    }

    private fun condition(
        item: JsonObject,
        mappings: Map<String, String>,
    ): JsonObject {
        val op = (item["op"] as? JsonPrimitive)?.contentOrNull
        val field = (item["field"] as? JsonPrimitive)?.contentOrNull ?: throw RuleError()
        val type = fieldTypes[field] ?: throw RuleError()
        val valid =
            op != null && op !in disallowedOps[field].orEmpty() && (op in typeOps.getValue(type) || op in internalOps[field].orEmpty())
        if (!valid) throw RuleError()
        var value = item["value"] ?: JsonNull
        if (type !in nullableTypes && value is JsonNull) throw RuleError()
        if (type == "date" && op == "isbetween" && !isDateRange(value)) throw RuleError()
        if (type == "id") {
            value =
                when (op) {
                    "is", "isNot" -> mapId(value, mappings)
                    "oneOf", "notOneOf" -> JsonArray((value as? JsonArray ?: throw RuleError()).map { mapId(it, mappings) }.distinct())
                    else -> value
                }
        }
        val out =
            linkedMapOf<String, JsonElement>(
                "op" to JsonPrimitive(op),
                "field" to JsonPrimitive(field),
                "value" to value,
                "type" to JsonPrimitive(type),
            )
        item["options"]?.takeIf { it !is JsonNull }?.let { out["options"] = it }
        return JsonObject(out)
    }

    private fun isDateRange(value: JsonElement): Boolean =
        listOf("num1", "num2").all { key ->
            val bound = (value as? JsonObject)?.get(key) as? JsonPrimitive
            bound != null && bound.isString && isIsoDate(bound.content)
        }

    private fun action(
        item: JsonObject,
        mappings: Map<String, String>,
    ): JsonObject {
        val op = (item["op"] as? JsonPrimitive)?.contentOrNull
        if (op !in actionOps) throw RuleError()
        val requested = (item["field"] as? JsonPrimitive)?.contentOrNull
        val (field, type) =
            when (op) {
                "set" -> requested to (fieldTypes[requested] ?: throw RuleError())
                "set-split-amount" -> null to "number"
                "link-schedule" -> null to "id"
                "prepend-notes", "append-notes" -> "notes" to "id"
                else -> requested to null
            }
        var value = item["value"] ?: JsonNull
        if (requested == "account" && (value is JsonNull || (value as? JsonPrimitive)?.contentOrNull.isNullOrEmpty())) throw RuleError()
        if (type == "id" && op == "set") value = mapId(value, mappings)
        val out = linkedMapOf<String, JsonElement>("op" to JsonPrimitive(op), "field" to jsonString(field), "value" to value)
        type?.let { out["type"] = JsonPrimitive(it) }
        item["options"]?.takeIf { it !is JsonNull }?.let { out["options"] = it }
        return JsonObject(out)
    }

    private fun mapId(
        value: JsonElement,
        mappings: Map<String, String>,
    ): JsonElement {
        val id = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return value
        return mappings[id]?.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: value
    }
}
