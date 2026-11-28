@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.rules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import app.tandemforactual.data.store.cleanPayeeName
import app.tandemforactual.data.store.observeRecords
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.createBudgetRule
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.SectionCard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Wand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A transaction's payee and the text its bank sent for it. */
internal data class ImportedText(
    val payeeId: String,
    val text: String,
)

/**
 * A rule that would set [payeeId] whenever the imported text contains [match], tried on the budget's own history: it
 * [catches] that many of the payee's transactions, misses [misses] of them, and touches no other payee's.
 */
internal data class PayeeRuleSuggestion(
    val payeeId: String,
    val payeeName: String,
    val match: String,
    val catches: Int,
    val misses: Int,
)

/** Payees whose bank text doesn't read as their name, with no rule yet, and a rename rule that holds up on history. */
internal fun payeeRuleSuggestions(
    history: List<ImportedText>,
    names: Map<String, String>,
    inRules: Set<String>,
): List<PayeeRuleSuggestion> =
    history
        .groupBy { it.payeeId }
        .mapNotNull { (payeeId, own) ->
            val name = names[payeeId]?.takeIf { payeeId !in inRules } ?: return@mapNotNull null
            val unresolved = own.filterNot { it.text.trim().equals(name, ignoreCase = true) }
            val match = commonCleanedText(unresolved) ?: return@mapNotNull null
            val hits = history.filter { it.text.contains(match, ignoreCase = true) }
            val catches = hits.count { it.payeeId == payeeId }
            if (catches < MIN_CATCHES || hits.any { it.payeeId != payeeId }) return@mapNotNull null
            PayeeRuleSuggestion(payeeId, name, match, catches, own.size - catches)
        }.sortedByDescending { it.catches }

/** The longest run of leading words from the most common cleaned name that the raw bank text still contains. */
private fun commonCleanedText(texts: List<ImportedText>): String? {
    if (texts.size < MIN_CATCHES) return null
    val words =
        texts
            .groupingBy { cleanPayeeName(it.text, emptySet()).name }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?.split(' ')
            .orEmpty()
    return words.indices
        .reversed()
        .map { words.subList(0, it + 1).joinToString(" ") }
        .firstOrNull { match -> texts.count { it.text.contains(match, ignoreCase = true) } >= MIN_CATCHES }
        ?.takeIf { it.length >= MIN_MATCH_LENGTH }
}

internal fun PayeeRuleSuggestion.toRuleDraft(): RuleDraft =
    RuleDraft(
        stage = RuleStage.PRE,
        conditions = listOf(RuleConditionDraft(RuleConditionField.IMPORTED_PAYEE, "contains", text = match)),
        actions = listOf(RuleActionDraft(RuleActionKind.SET_PAYEE, id = payeeId)),
    )

/** Rename rules for payees the bank names differently, each with what it would and wouldn't have caught. */
@Composable
internal fun PayeeRuleSuggestionsCard(
    payees: List<Pair<String, String>>,
    inRules: Set<String>,
    enabled: Boolean,
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
) {
    val budgetId = state.selectedBudgetId
    val suggestions by produceState<List<PayeeRuleSuggestion>>(emptyList(), budgetId, payees, inRules, mobileSession.databaseGeneration) {
        val repository = mobileSession.repository ?: return@produceState
        val records = repository.observeRecords(budgetId, "transaction").first()
        value =
            withContext(Dispatchers.Default) {
                val history =
                    records.mapNotNull { record ->
                        val payeeId = record.payeeId ?: return@mapNotNull null
                        record
                            .recordBody()
                            ?.stringValue("importedPayee")
                            ?.takeIf { it.isNotBlank() }
                            ?.let { ImportedText(payeeId, it) }
                    }
                payeeRuleSuggestions(history, payees.toMap(), inRules)
            }
    }
    if (suggestions.isEmpty()) return
    SectionCard("Payees that need a rule", detail = "The bank’s text doesn’t match these names, so imports won’t find them.") {
        suggestions.take(MAX_SUGGESTIONS).forEachIndexed { index, suggestion ->
            if (index > 0) DividerLine()
            TandemActionButton(
                "“${suggestion.match}” → ${suggestion.payeeName}",
                TandemActionButtonTone.NEUTRAL,
                icon = Lucide.Wand,
                enabled = enabled,
            ) {
                val body = suggestion.toRuleDraft().toRequestBody { null }.getOrNull() ?: return@TandemActionButton
                val password = state.encryptedPassword.takeIf { it.isNotBlank() }
                scope.launch { mobileSession.budgetEdits.createBudgetRule(budgetId, body, password) }
            }
            BodyText(suggestionTally(suggestion))
        }
    }
}

internal fun suggestionTally(suggestion: PayeeRuleSuggestion): String {
    val caught = "Would have caught ${suggestion.catches} past ${if (suggestion.catches == 1) "transaction" else "transactions"}"
    return if (suggestion.misses == 0) "$caught and missed none." else "$caught and missed ${suggestion.misses}."
}

private const val MIN_CATCHES = 2
private const val MIN_MATCH_LENGTH = 3
private const val MAX_SUGGESTIONS = 8
