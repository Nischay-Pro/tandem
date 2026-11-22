package app.tandemforactual.data.actual

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal fun ActualBudgetEdits.insertPayee(payee: Map<String, Any?>): String {
    val id = writer.insert("payees", payee)
    writer.insert("payee_mapping", linkedMapOf("id" to id, "targetId" to id))
    return id
}

fun ActualBudgetEdits.createPayee(name: String): String =
    writer.batch {
        if (payees().any {
                (it["name"] as String?).orEmpty().trim().lowercase() == name.trim().lowercase()
            }
        ) {
            fail("A payee with this name already exists")
        }
        insertPayee(linkedMapOf("name" to name))
    }

private fun ActualBudgetEdits.regularPayee(id: String) = payees().firstOrNull { it["id"] == id && it["transfer_acct"] == null }

fun ActualBudgetEdits.renamePayee(
    id: String,
    name: String,
) = writer.batch {
    val clean = name.trim()
    if (clean.isEmpty() || clean.length > 100) fail("Payee names must be between 1 and 100 characters")
    regularPayee(id) ?: fail("Choose a payee in this budget")
    writer.update("payees", id, mapOf("name" to clean))
}

fun ActualBudgetEdits.mergePayee(
    id: String,
    targetId: String,
) = writer.batch {
    if (id == targetId) fail("Choose a different payee to merge into")
    if (regularPayee(id) == null || regularPayee(targetId) == null) fail("Choose two payees in this budget")
    db.query("SELECT id FROM payee_mapping WHERE targetId = ?", id).forEach {
        writer.update(
            "payee_mapping",
            it["id"] as String,
            mapOf(
                "targetId" to targetId,
            ),
        )
    }
    writer.update("payee_mapping", id, mapOf("targetId" to targetId))
    writer.delete("payees", id)
}

fun ActualBudgetEdits.deletePayee(id: String) =
    writer.batch {
        regularPayee(id) ?: fail("Choose a payee in this budget")
        writer.delete("payees", id)
    }

fun ActualBudgetEdits.setPayeeDefaultCategory(
    payeeId: String,
    categoryId: String?,
) = writer.batch {
    if (categoryId != null && categoryId.isBlank()) fail("Choose a category or clear the default")
    regularPayee(payeeId) ?: fail("Choose a regular payee in this budget")
    if (categoryId != null && categories().none { it["id"] == categoryId }) fail("Choose an active category in this budget")
    val matching =
        loadRules().filter { rule ->
            val conditions = rule.getValue("conditions").jsonArray.map { it.jsonObject }
            val stage = rule["stage"]?.let(::scalar)
            (stage == null || stage == "default") &&
                rule["conditionsOp"]?.let(::scalar) == "and" &&
                conditions.size == 1 &&
                conditions[0].value("field") == "payee" &&
                conditions[0].value("op") == "is" &&
                conditions[0].value("value") == payeeId &&
                rule.getValue("actions").jsonArray.any {
                    it.jsonObject.value("field") == "category" &&
                        it.jsonObject.value("op") == "set"
                }
        }
    if (matching.size > 1) fail("This payee has multiple category rules. Edit them in Actual first.")
    val rule = matching.firstOrNull()
    if (rule == null) {
        if (categoryId == null) return@batch
        insertRule(
            null,
            "and",
            JsonArray(listOf(condition("payee", "is", JsonPrimitive(payeeId)))),
            JsonArray(listOf(condition("category", "set", JsonPrimitive(categoryId)))),
        )
        return@batch
    }
    val actions = rule.getValue("actions").jsonArray.map { it.jsonObject }
    val indexes = actions.indices.filter { actions[it].value("field") == "category" && actions[it].value("op") == "set" }
    if (indexes.size > 1) fail("This payee has multiple category actions. Edit the rule in Actual first.")
    val index = indexes.single()
    val id = rule.value("id") as String
    if (categoryId == null) {
        val remaining = actions.filterIndexed { i, _ -> i != index }
        if (remaining.isEmpty()) deleteRule(id) else updateRuleRow(rule, JsonArray(remaining))
        return@batch
    }
    updateRuleRow(
        rule,
        JsonArray(
            actions.mapIndexed { i, action ->
                if (i ==
                    index
                ) {
                    JsonObject(action + ("value" to JsonPrimitive(categoryId)))
                } else {
                    action
                }
            },
        ),
    )
}

internal fun ActualBudgetEdits.condition(
    field: String,
    op: String,
    value: JsonElement,
) = JsonObject(linkedMapOf("field" to JsonPrimitive(field), "op" to JsonPrimitive(op), "value" to value))
