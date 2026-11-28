@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.rules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.store.PayeeDuplicateGroup
import app.tandemforactual.data.store.PayeeUsage
import app.tandemforactual.data.store.payeeDuplicateGroups
import app.tandemforactual.data.store.payeeTransactionCounts
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.BudgetEdits
import app.tandemforactual.ui.core.session.budget.manageBudgetPayee
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Merge
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One change a duplicate group makes: a payee merged into the one kept, or the kept payee given the tidy name. */
internal sealed interface PayeeCleanupStep {
    data class Merge(
        val payeeId: String,
        val intoId: String,
    ) : PayeeCleanupStep

    data class Rename(
        val payeeId: String,
        val name: String,
    ) : PayeeCleanupStep

    data class Delete(
        val payeeId: String,
    ) : PayeeCleanupStep
}

/** Every other member merged into the kept payee, then the kept payee renamed when its name isn't the suggested one. */
internal fun payeeCleanupSteps(group: PayeeDuplicateGroup): List<PayeeCleanupStep> {
    val keep = group.members.first { it.id == group.keepId }
    val merges = group.members.filter { it.id != keep.id }.map { PayeeCleanupStep.Merge(it.id, keep.id) }
    return merges + listOfNotNull(PayeeCleanupStep.Rename(keep.id, group.name).takeIf { keep.name != group.name })
}

/** Identifies a group by its members, so a group marked as not duplicates stays hidden until its members change. */
internal fun payeeGroupKey(group: PayeeDuplicateGroup): String =
    group.members
        .map { it.id }
        .sorted()
        .joinToString(",")

internal fun decodePayeeGroupKeys(stored: String?): Set<String> = stored?.split("\n")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

internal fun encodePayeeGroupKeys(keys: Set<String>): String = keys.sorted().joinToString("\n")

private fun notDuplicatesKey(budgetId: String): String = "internal:payee-not-duplicates:$budgetId"

/** Payees that look like the same merchant, each group merged into its most used payee with Actual's own merge. */
@Composable
internal fun PayeeCleanupSheet(
    visible: Boolean,
    payees: List<Pair<String, String>>,
    inRules: Set<String>,
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
    onClose: () -> Unit,
) {
    val budgetId = state.selectedBudgetId
    val counts by produceState<Map<String, Long>?>(null, visible, budgetId, payees, mobileSession.databaseGeneration) {
        value = if (visible) mobileSession.repository?.payeeTransactionCounts(budgetId) else null
    }
    var dismissed by remember(budgetId) { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(visible, budgetId) {
        if (visible) dismissed = decodePayeeGroupKeys(mobileSession.preferencesStore?.read()?.get(notDuplicatesKey(budgetId)))
    }
    val usages =
        remember(payees, counts) {
            counts?.let { used -> payees.map { (id, name) -> PayeeUsage(id, name, (used[id] ?: 0L).toInt()) } }
        }
    val groups =
        remember(usages, dismissed) {
            val hidden = dismissed ?: return@remember null
            usages?.let(::payeeDuplicateGroups)?.filter { payeeGroupKey(it) !in hidden }
        }
    val busy = mobileSession.isPerformingBudgetAction
    val enabled = !state.readOnlyMode && !busy
    val saveHidden: (Set<String>) -> Unit = { kept ->
        dismissed = kept
        scope.launch { mobileSession.preferencesStore?.putValue(notDuplicatesKey(budgetId), encodePayeeGroupKeys(kept)) }
    }
    TandemTaskSheet(visible = visible, onDismissRequest = { if (!busy) onClose() }, onDismissed = {}) {
        SheetCard(
            maxWidth = 560.dp,
            header = { TandemModalHeader(title = "Clean up payees", enabled = !busy, onClose = { if (!busy) onClose() }) },
        ) {
            BodyText("Payees that look like the same merchant once dates, card numbers and store numbers are set aside.")
            when {
                groups == null -> BodyText("Checking payees…")
                groups.isEmpty() -> BodyText("No payees look alike.")
                else -> {
                    val safe = groups.filter { it.safe }
                    if (safe.size > 1) {
                        TandemActionButton(
                            "Merge all ${safe.size} that differ only by bank detail",
                            TandemActionButtonTone.PRIMARY,
                            icon = Lucide.Merge,
                            enabled = enabled,
                            onClick = { merge(safe, state, mobileSession, scope) },
                        )
                    }
                    groups.forEach { group ->
                        PayeeGroupCard(group, usages.orEmpty(), enabled, { merge(listOf(it), state, mobileSession, scope) }) {
                            saveHidden((dismissed ?: emptySet()) + payeeGroupKey(group))
                        }
                    }
                }
            }
            dismissed?.takeIf { it.isNotEmpty() }?.let { hidden ->
                TandemActionButton("Show ${hidden.size} hidden again", TandemActionButtonTone.NEUTRAL, enabled = enabled) {
                    saveHidden(emptySet())
                }
            }
            counts?.let { used -> UnusedPayeesCard(unusedPayees(payees, used, inRules), enabled, state, mobileSession, scope) }
            PayeeRuleSuggestionsCard(payees, inRules, enabled, state, mobileSession, scope)
            budgetActionFeedbackMessage(mobileSession)
        }
    }
}

/** Payees with no transactions that no rule or schedule mentions. */
internal fun unusedPayees(
    payees: List<Pair<String, String>>,
    counts: Map<String, Long>,
    inRules: Set<String>,
): List<Pair<String, String>> = payees.filter { (id, _) -> (counts[id] ?: 0L) == 0L && id !in inRules }

@Composable
private fun UnusedPayeesCard(
    unused: List<Pair<String, String>>,
    enabled: Boolean,
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
) {
    if (unused.isEmpty()) return
    var confirming by remember(unused) { mutableStateOf(false) }
    val names = unused.take(MAX_UNUSED_SHOWN).joinToString(", ") { it.second }
    val more = if (unused.size > MAX_UNUSED_SHOWN) " and ${unused.size - MAX_UNUSED_SHOWN} more" else ""
    SectionCard("No transactions", detail = "$names$more") {
        TandemActionButton(
            if (confirming) "Remove ${unused.size} for good?" else "Remove ${payeeCount(unused.size)} with no transactions",
            TandemActionButtonTone.DESTRUCTIVE,
            icon = Lucide.Trash2,
            enabled = enabled,
        ) {
            if (!confirming) {
                confirming = true
            } else {
                confirming = false
                runSteps(unused.map { PayeeCleanupStep.Delete(it.first) }, state, mobileSession, scope)
            }
        }
    }
}

private fun payeeCount(count: Int): String = if (count == 1) "1 payee" else "$count payees"

private fun merge(
    groups: List<PayeeDuplicateGroup>,
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
) = runSteps(groups.flatMap(::payeeCleanupSteps), state, mobileSession, scope)

private fun runSteps(
    steps: List<PayeeCleanupStep>,
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
) {
    val budgetId = state.selectedBudgetId
    val password = state.encryptedPassword.takeIf { it.isNotBlank() }
    val edits = mobileSession.budgetEdits
    scope.launch {
        for (step in steps) {
            if (!edits.apply(step, budgetId, password)) break
        }
    }
}

private suspend fun BudgetEdits.apply(
    step: PayeeCleanupStep,
    budgetId: String,
    password: String?,
): Boolean =
    when (step) {
        is PayeeCleanupStep.Merge -> manageBudgetPayee(budgetId, step.payeeId, "merge", null, step.intoId, password)
        is PayeeCleanupStep.Rename -> manageBudgetPayee(budgetId, step.payeeId, "rename", step.name, null, password)
        is PayeeCleanupStep.Delete -> manageBudgetPayee(budgetId, step.payeeId, "delete", null, null, password)
    }

private const val MAX_UNUSED_SHOWN = 5
