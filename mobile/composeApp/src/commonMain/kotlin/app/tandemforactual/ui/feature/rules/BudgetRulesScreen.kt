@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.rules
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.activity.ActivitySearchInput
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Composable
internal fun BudgetRulesScreen(
    rules: List<BudgetRecordEntity>,
    accountRecords: List<BudgetRecordEntity>,
    categoryRecords: List<BudgetRecordEntity>,
    categoryGroupRecords: List<BudgetRecordEntity>,
    payeeRecords: List<BudgetRecordEntity>,
    canAddRules: Boolean,
    onOpenRule: (String) -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val namesByField =
        remember(accountRecords, categoryRecords, categoryGroupRecords, payeeRecords) {
            mapOf(
                "account" to accountRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap(),
                "category" to categoryRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap(),
                "category_group" to categoryGroupRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap(),
                "payee" to payeeRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap(),
            )
        }
    val activeRules = remember(rules) { rules.filterNot { it.recordBody()?.booleanValue("tombstone") == true } }
    val summaries =
        remember(activeRules, namesByField) {
            activeRules.map { rule -> ruleSummary(rule, namesByField) }
        }
    val visibleRules =
        remember(summaries, searchQuery) {
            val needle = searchQuery.trim().lowercase()
            if (needle.isEmpty()) summaries else summaries.filter { "${it.conditions} ${it.actions}".lowercase().contains(needle) }
        }
    PageLazyColumn {
        item(key = "budget-rules-search") {
            ActivitySearchInput(searchQuery, onValueChange = { searchQuery = it }, placeholder = "Find a rule")
        }
        item(key = "budget-rules-summary") {
            RulesCountRow(searchQuery.isBlank(), visibleRules.size, summaries.size, canAddRules) { onOpenRule("") }
        }
        when {
            summaries.isEmpty() ->
                item(key = "budget-rules-empty") {
                    SurfaceCard {
                        EmptyState(
                            "No rules",
                            "Add a rule to categorize, rename, or note transactions automatically.",
                            modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                        )
                    }
                }
            visibleRules.isEmpty() ->
                item(key = "budget-rules-no-matches") {
                    SurfaceCard {
                        EmptyState("No matching rules", modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large))
                    }
                }
            else ->
                items(visibleRules.size, key = { index -> visibleRules[index].id }) { index ->
                    val rule = visibleRules[index]
                    SurfaceCard {
                        ListRow(
                            rule.conditions,
                            rule.actions,
                            rule.stage,
                            leading = RowLeading.Icon(Lucide.ListChecks),
                            options = ListRowOptions(trailingColor = tandemPalette.muted),
                            onClick = { onOpenRule(rule.id) },
                        )
                    }
                }
        }
    }
}

private fun ruleSummary(
    rule: BudgetRecordEntity,
    namesByField: Map<String, Map<String, String>>,
): RuleSummary {
    val body = rule.recordBody()
    val conditions = (body?.get("conditions") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    val actions = (body?.get("actions") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    val conditionSummary = ruleConditionSummary(conditions, namesByField)
    val actionSummary = ruleActionSummary(actions, namesByField)
    val stage =
        when (body?.stringValue("stage")) {
            "pre" -> "Runs first"
            "post" -> "Runs last"
            else -> null
        }
    return RuleSummary(rule.recordId, conditionSummary, actionSummary, stage)
}

private fun ruleConditionSummary(
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
                    is JsonArray ->
                        raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ") {
                            namesByField[wireField]?.get(it)
                                ?: it
                        }
                    is JsonPrimitive -> raw.contentOrNull?.let { namesByField[wireField]?.get(it) ?: it }
                    else -> raw?.toString()
                }
            listOfNotNull(field, operation, value).joinToString(" ")
        }.ifBlank { "Every transaction" }

private fun ruleActionSummary(
    actions: List<JsonObject>,
    namesByField: Map<String, Map<String, String>>,
): String =
    actions
        .joinToString(" · ") { action ->
            val wireField = action.stringValue("field")
            val value =
                (action["value"] as? JsonPrimitive)?.contentOrNull?.let { namesByField[wireField]?.get(it) ?: it }
            when (action.stringValue("op")) {
                "append-notes" -> "Add “$value” to end of notes"
                "prepend-notes" -> "Add “$value” to start of notes"
                "delete-transaction" -> "Delete transaction"
                "link-schedule" -> "Link schedule"
                "set-split-amount" -> "Split transaction"
                else ->
                    when {
                        wireField == "cleared" -> if (value == "true") "Mark cleared" else "Mark not cleared"
                        else -> "Set ${ruleFieldLabel(wireField).lowercase()} to ${value.orEmpty()}"
                    }
            }
        }.ifBlank { "No actions" }

/** How many rules are shown, and the button to add one. */
@Composable
private fun RulesCountRow(
    searchBlank: Boolean,
    visibleCount: Int,
    totalCount: Int,
    canAddRules: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                searchBlank && totalCount == 1 -> "1 rule"
                searchBlank -> "$totalCount rules"
                else -> "$visibleCount of $totalCount rules"
            },
            modifier = Modifier.weight(1f),
            color = tandemPalette.muted,
            fontSize = TandemTypography.caption,
        )
        TandemIconButton(
            Lucide.Plus,
            "Add rule",
            enabled = canAddRules,
            tint = tandemPalette.accent,
            containerColor = tandemPalette.accentSurface,
        ) { onAdd() }
    }
}

private data class RuleSummary(
    val id: String,
    val conditions: String,
    val actions: String,
    val stage: String?,
)
