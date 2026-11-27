@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.store.PayeeDuplicateGroup
import app.tandemforactual.data.store.PayeeUsage
import app.tandemforactual.data.store.payeeDuplicateGroups
import app.tandemforactual.data.store.payeeTransactionCounts
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.manageBudgetPayee
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Merge
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
}

/** Every other member merged into the kept payee, then the kept payee renamed when its name isn't the suggested one. */
internal fun payeeCleanupSteps(group: PayeeDuplicateGroup): List<PayeeCleanupStep> {
    val keep = group.members.first { it.id == group.keepId }
    val merges = group.members.filter { it.id != keep.id }.map { PayeeCleanupStep.Merge(it.id, keep.id) }
    return merges + listOfNotNull(PayeeCleanupStep.Rename(keep.id, group.name).takeIf { keep.name != group.name })
}

/** Payees that look like the same merchant, each group merged into its most used payee with Actual's own merge. */
@Composable
internal fun PayeeCleanupSheet(
    visible: Boolean,
    payees: List<Pair<String, String>>,
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
    onClose: () -> Unit,
) {
    val budgetId = state.selectedBudgetId
    val counts by produceState<Map<String, Long>?>(null, visible, budgetId, payees, mobileSession.databaseGeneration) {
        value = if (visible) mobileSession.repository?.payeeTransactionCounts(budgetId) else null
    }
    val groups =
        remember(payees, counts) {
            counts?.let { used -> payeeDuplicateGroups(payees.map { (id, name) -> PayeeUsage(id, name, (used[id] ?: 0L).toInt()) }) }
        }
    val busy = mobileSession.isPerformingBudgetAction
    TandemTaskSheet(visible = visible, onDismissRequest = { if (!busy) onClose() }, onDismissed = {}) {
        SheetCard(
            maxWidth = 560.dp,
            header = { TandemModalHeader(title = "Clean up payees", enabled = !busy, onClose = { if (!busy) onClose() }) },
        ) {
            BodyText("Payees that look like the same merchant once dates, card numbers and store numbers are set aside.")
            when {
                groups == null -> BodyText("Checking payees…")
                groups.isEmpty() -> BodyText("No payees look alike.")
                else ->
                    groups.forEach { group ->
                        PayeeGroupCard(group, !state.readOnlyMode && !busy) { merge(group, state, mobileSession, scope) }
                    }
            }
            budgetActionFeedbackMessage(mobileSession)
        }
    }
}

@Composable
private fun PayeeGroupCard(
    group: PayeeDuplicateGroup,
    enabled: Boolean,
    onMerge: () -> Unit,
) {
    val detail = group.stripped.takeIf { it.isNotEmpty() }?.let { "Set aside: ${it.take(MAX_STRIPPED_SHOWN).joinToString(", ")}" }
    SectionCard(group.name, detail = detail) {
        group.members.forEachIndexed { index, member ->
            if (index > 0) DividerLine()
            ListRow(
                member.name,
                subtitle = if (member.id == group.keepId) "Kept" else "Merged in",
                trailing = transactionCount(member.transactions),
                leading = RowLeading.Payee(member.name),
                options = ListRowOptions(showChevron = false),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
            TandemActionButton(
                "Merge into ${group.name}",
                TandemActionButtonTone.PRIMARY,
                modifier = Modifier.weight(1f),
                icon = Lucide.Merge,
                enabled = enabled,
                onClick = onMerge,
            )
        }
    }
}

private fun merge(
    group: PayeeDuplicateGroup,
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
) {
    val budgetId = state.selectedBudgetId
    val password = state.encryptedPassword.takeIf { it.isNotBlank() }
    val edits = mobileSession.budgetEdits
    scope.launch {
        for (step in payeeCleanupSteps(group)) {
            val done =
                when (step) {
                    is PayeeCleanupStep.Merge -> edits.manageBudgetPayee(budgetId, step.payeeId, "merge", null, step.intoId, password)
                    is PayeeCleanupStep.Rename -> edits.manageBudgetPayee(budgetId, step.payeeId, "rename", step.name, null, password)
                }
            if (!done) break
        }
    }
}

private fun transactionCount(count: Int): String = if (count == 1) "1 transaction" else "$count transactions"

private const val MAX_STRIPPED_SHOWN = 4
