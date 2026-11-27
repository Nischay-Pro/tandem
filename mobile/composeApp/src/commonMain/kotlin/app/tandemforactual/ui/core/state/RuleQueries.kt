package app.tandemforactual.ui.core.state

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun defaultCategoryRulesByPayee(rules: List<BudgetRecordEntity>): Map<String, List<String>> {
    val categoriesByPayee = mutableMapOf<String, MutableSet<String>>()
    rules.forEach { record ->
        if (record.localDeleted || record.remoteDeleted) return@forEach
        val body = record.recordBody() ?: return@forEach
        if (body.booleanValue("tombstone") == true) return@forEach
        val stage = body["stage"]?.jsonPrimitive?.contentOrNull
        if (stage != null && stage != "default") return@forEach
        if (body.stringValue("conditionsOp") != "and") return@forEach
        val conditions = (body["conditions"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        if (conditions.size != 1) return@forEach
        val condition = conditions.single()
        if (condition.stringValue("field") != "payee" || condition.stringValue("op") != "is") {
            return@forEach
        }
        val payeeId = condition["value"]?.jsonPrimitive?.contentOrNull ?: return@forEach
        val actions = (body["actions"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        actions.forEach { action ->
            if (action.stringValue("field") == "category" && action.stringValue("op") == "set") {
                val categoryId = action["value"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                categoriesByPayee.getOrPut(payeeId, ::mutableSetOf).add(categoryId)
            }
        }
    }
    return categoriesByPayee.mapValues { (_, categories) -> categories.sorted() }
}

/** Narrows the rules list to rules whose conditions or actions name one of [ids]. */
internal data class RulesReferenceFilter(
    val ids: Set<String>,
    val label: String,
)

internal fun RulesReferenceFilter.matches(rule: BudgetRecordEntity): Boolean {
    val body = rule.recordBody() ?: return false
    return listOf("conditions", "actions").any { key ->
        (body[key] as? JsonArray).orEmpty().any { item ->
            when (val value = (item as? JsonObject)?.get("value")) {
                is JsonArray -> value.any { (it as? JsonPrimitive)?.contentOrNull in ids }
                is JsonPrimitive -> value.contentOrNull in ids
                else -> false
            }
        }
    }
}
