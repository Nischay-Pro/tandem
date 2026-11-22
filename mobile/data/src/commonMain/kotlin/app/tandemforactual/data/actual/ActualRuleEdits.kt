package app.tandemforactual.data.actual

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

internal fun ActualBudgetEdits.loadRules(): List<JsonObject> =
    ActualRules.load(db.query("SELECT * FROM rules WHERE conditions IS NOT NULL AND actions IS NOT NULL AND tombstone = 0"), emptyMap())

private fun ActualBudgetEdits.internal(items: JsonArray): String = RuleWire.internal(items)

private fun ActualBudgetEdits.validateRule(
    conditionsOp: String,
    conditions: JsonArray,
    actions: JsonArray,
) {
    if (conditionsOp !in setOf("and", "or")) fail("Invalid rule conditionsOp: $conditionsOp")
    val probe =
        mapOf(
            "id" to "probe",
            "stage" to null,
            "conditions_op" to conditionsOp,
            "conditions" to internal(conditions),
            "actions" to internal(actions),
        )
    if (ActualRules.load(listOf(probe), emptyMap()).isEmpty()) fail("This rule has an invalid condition or action")
}

internal fun ActualBudgetEdits.insertRule(
    stage: String?,
    conditionsOp: String,
    conditions: JsonArray,
    actions: JsonArray,
): String {
    validateRule(conditionsOp, conditions, actions)
    return writer.insert(
        "rules",
        linkedMapOf(
            "stage" to stage,
            "conditions_op" to conditionsOp,
            "conditions" to internal(conditions),
            "actions" to internal(actions),
        ),
    )
}

internal fun ActualBudgetEdits.updateRuleRow(
    rule: JsonObject,
    actions: JsonArray,
) {
    val conditions = rule.getValue("conditions").jsonArray
    val op = rule.value("conditionsOp") as String? ?: "and"
    validateRule(op, conditions, actions)
    writer.update(
        "rules",
        rule.value("id") as String,
        linkedMapOf(
            "stage" to rule.value("stage"),
            "conditions_op" to op,
            "conditions" to internal(conditions),
            "actions" to internal(actions),
        ),
    )
}

private fun ActualBudgetEdits.stageOf(rule: JsonObject): String? =
    when (val stage = rule["stage"]?.let(::scalar)) {
        null, "default" -> null
        "pre", "post" -> stage as String
        else -> fail("Invalid rule stage: $stage")
    }

fun ActualBudgetEdits.createRule(wire: JsonObject): String =
    writer.batch {
        val rule = RuleWire.toActual(wire)
        insertRule(
            stageOf(rule),
            rule.value("conditionsOp") as String? ?: "and",
            rule["conditions"] as? JsonArray ?: fail("A rule needs conditions"),
            rule["actions"] as? JsonArray ?: fail("A rule needs actions"),
        )
    }

fun ActualBudgetEdits.updateRule(
    id: String,
    wire: JsonObject,
) = writer.batch {
    val rule = RuleWire.toActual(wire)
    if (loadRules().none { it.value("id") == id }) fail("Rule does not exist in this budget")
    val conditions = rule["conditions"] as? JsonArray ?: fail("A rule needs conditions")
    val actions = rule["actions"] as? JsonArray ?: fail("A rule needs actions")
    val op = rule.value("conditionsOp") as String? ?: "and"
    validateRule(op, conditions, actions)
    writer.update(
        "rules",
        id,
        linkedMapOf(
            "stage" to stageOf(rule),
            "conditions_op" to op,
            "conditions" to internal(conditions),
            "actions" to internal(actions),
        ),
    )
}

/** Returns false when the rule belongs to a schedule, which Actual refuses to delete. */
fun ActualBudgetEdits.deleteRule(id: String): Boolean =
    writer.batch {
        if (db.first("SELECT id FROM schedules WHERE rule = ?", id) != null) return@batch false
        writer.delete("rules", id)
        true
    }
