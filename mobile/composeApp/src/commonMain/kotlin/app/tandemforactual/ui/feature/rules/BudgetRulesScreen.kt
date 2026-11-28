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
import app.tandemforactual.ui.core.format.ruleNamesByField
import app.tandemforactual.ui.core.format.ruleText
import app.tandemforactual.ui.core.state.RulesReferenceFilter
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.matches
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.ui.ActivitySearchInput
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowAction
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X

@Composable
internal fun BudgetRulesScreen(
    rules: List<BudgetRecordEntity>,
    accountRecords: List<BudgetRecordEntity>,
    categoryRecords: List<BudgetRecordEntity>,
    categoryGroupRecords: List<BudgetRecordEntity>,
    payeeRecords: List<BudgetRecordEntity>,
    canAddRules: Boolean,
    onOpenRule: (String) -> Unit,
    selectedRuleId: String? = null,
    referenceFilter: RulesReferenceFilter? = null,
    onClearReferenceFilter: () -> Unit = {},
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val namesByField =
        remember(accountRecords, categoryRecords, categoryGroupRecords, payeeRecords) {
            ruleNamesByField(accountRecords, categoryRecords, categoryGroupRecords, payeeRecords)
        }
    val activeRules = remember(rules, referenceFilter) { activeRules(rules, referenceFilter) }
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
        referenceFilter?.let { filter ->
            item(key = "budget-rules-reference") { RulesReferenceRow(filter, onClearReferenceFilter) }
        }
        item(key = "budget-rules-summary") {
            RulesCountRow(searchQuery.isBlank(), visibleRules.size, summaries.size, canAddRules) { onOpenRule("") }
        }
        when {
            summaries.isEmpty() -> {
                item(key = "budget-rules-empty") {
                    SurfaceCard {
                        EmptyState(
                            "No rules",
                            "Add a rule to categorize, rename, or note transactions automatically.",
                            modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                        )
                    }
                }
            }

            visibleRules.isEmpty() -> {
                item(key = "budget-rules-no-matches") {
                    SurfaceCard {
                        EmptyState("No matching rules", modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large))
                    }
                }
            }

            else -> {
                items(visibleRules.size, key = { index -> visibleRules[index].id }) { index ->
                    val rule = visibleRules[index]
                    SurfaceCard(highlighted = rule.id == selectedRuleId) {
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
}

private fun activeRules(
    rules: List<BudgetRecordEntity>,
    referenceFilter: RulesReferenceFilter?,
): List<BudgetRecordEntity> =
    rules.filter { rule ->
        rule.recordBody()?.booleanValue("tombstone") != true && referenceFilter?.matches(rule) != false
    }

/** The filter the rules list was opened with from an item's where-used sheet, and the button that clears it. */
@Composable
private fun RulesReferenceRow(
    filter: RulesReferenceFilter,
    onClear: () -> Unit,
) {
    SurfaceCard {
        ListRow(
            filter.label,
            "Only rules that name it",
            leading = RowLeading.Icon(Lucide.Link),
            options =
                ListRowOptions(
                    showChevron = false,
                    trailingAction = ListRowAction(Lucide.X, "Show all rules", onClick = onClear),
                ),
        )
    }
}

private fun ruleSummary(
    rule: BudgetRecordEntity,
    namesByField: Map<String, Map<String, String>>,
): RuleSummary {
    val text = ruleText(rule.recordBody(), namesByField)
    return RuleSummary(rule.recordId, text.conditions, text.actions, text.stage)
}

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
