package app.tandemforactual.ui.core.format

import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val ruleFieldLabels =
    mapOf(
        "payee" to "Payee",
        "imported_payee" to "Imported payee",
        "notes" to "Notes",
        "account" to "Account",
        "category" to "Category",
        "amount" to "Amount",
    )

internal fun ruleFieldLabel(wireField: String?): String =
    ruleFieldLabels[wireField]
        ?: wireField?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
        ?: "Field"

internal fun ruleOperatorLabel(op: String): String =
    when (op) {
        "is" -> "is"
        "isNot" -> "is not"
        "oneOf" -> "is one of"
        "notOneOf" -> "is not one of"
        "contains" -> "contains"
        "doesNotContain" -> "doesn’t contain"
        "matches" -> "matches pattern"
        "isapprox" -> "is about"
        "isbetween" -> "is between"
        "gt" -> "is more than"
        "gte" -> "is at least"
        "lt" -> "is less than"
        "lte" -> "is at most"
        else -> op
    }

/** Names of accounts, categories, groups and payees by id, keyed by the rule field that refers to them. */
internal fun ruleNamesByField(
    accountRecords: List<BudgetRecordEntity>,
    categoryRecords: List<BudgetRecordEntity>,
    categoryGroupRecords: List<BudgetRecordEntity>,
    payeeRecords: List<BudgetRecordEntity>,
): Map<String, Map<String, String>> =
    mapOf(
        "account" to accountRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap(),
        "category" to categoryRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap(),
        "category_group" to categoryGroupRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap(),
        "payee" to payeeRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap(),
    )

/** A rule's conditions, actions and stage in words, as the rules list shows it. */
internal data class RuleText(
    val conditions: String,
    val actions: String,
    val stage: String?,
)

internal fun ruleText(
    body: JsonObject?,
    namesByField: Map<String, Map<String, String>>,
): RuleText {
    val conditions = (body?.get("conditions") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    val actions = (body?.get("actions") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    val stage =
        when (body?.stringValue("stage")) {
            "pre" -> "Runs first"
            "post" -> "Runs last"
            else -> null
        }
    return RuleText(ruleConditionSummary(conditions, namesByField), ruleActionSummary(actions, namesByField), stage)
}

internal fun ruleConditionSummary(
    conditions: List<JsonObject>,
    namesByField: Map<String, Map<String, String>>,
): String =
    conditions
        .joinToString(" · ") { condition ->
            val wireField = condition.stringValue("field")
            val options = condition["options"] as? JsonObject
            val field =
                when {
                    wireField == "amount" && options?.stringValue("outflow") == "true" -> "Money out"
                    wireField == "amount" && options?.stringValue("inflow") == "true" -> "Money in"
                    else -> ruleFieldLabel(wireField)
                }
            val operation = ruleOperatorLabel(condition.stringValue("op") ?: "is")
            val value =
                when (val raw = condition["value"]) {
                    is JsonArray -> {
                        raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ") {
                            namesByField[wireField]?.get(it)
                                ?: it
                        }
                    }

                    is JsonPrimitive -> {
                        raw.contentOrNull?.let { namesByField[wireField]?.get(it) ?: it }
                    }

                    else -> {
                        raw?.toString()
                    }
                }
            listOfNotNull(field, operation, value).joinToString(" ")
        }.ifBlank { "Every transaction" }

internal fun ruleActionSummary(
    actions: List<JsonObject>,
    namesByField: Map<String, Map<String, String>>,
): String =
    actions
        .joinToString(" · ") { action ->
            val wireField = action.stringValue("field")
            val value =
                (action["value"] as? JsonPrimitive)?.contentOrNull?.let { namesByField[wireField]?.get(it) ?: it }
            when (action.stringValue("op")) {
                "append-notes" -> {
                    "Add “$value” to end of notes"
                }

                "prepend-notes" -> {
                    "Add “$value” to start of notes"
                }

                "delete-transaction" -> {
                    "Delete transaction"
                }

                "link-schedule" -> {
                    "Link schedule"
                }

                "set-split-amount" -> {
                    "Split transaction"
                }

                else -> {
                    when {
                        wireField == "cleared" -> if (value == "true") "Mark cleared" else "Mark not cleared"
                        else -> "Set ${ruleFieldLabel(wireField).lowercase()} to ${value.orEmpty()}"
                    }
                }
            }
        }.ifBlank { "No actions" }
