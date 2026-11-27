@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.activity.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.tandemforactual.data.store.BulkTransactionEdit
import app.tandemforactual.data.store.noteTags
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.search.ActivitySearchRecord
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.transactions.applyBulkEdit
import app.tandemforactual.ui.core.session.transactions.duplicateTransactions
import app.tandemforactual.ui.core.session.transactions.mergeTransactions
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.liveScheduleSummaries
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.rows.RowMenuAction
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2

/** The bulk menu, its pickers, the reconciled check and the running action for the selected transactions. */
@Composable
internal fun ActivityBulkActions(
    state: AppState,
    mobileSession: TandemMobileSession,
    selection: ActivitySelectionState,
    items: List<ActivityBulkItem>,
) {
    ActivityBulkMenu(
        visible = selection.bulkMenuOpen && items.isNotEmpty(),
        items = items,
        totalText = state.formatCurrency(items.sumOf(ActivityBulkItem::amountMinor)),
        onDismiss = { selection.bulkMenuOpen = false },
    ) { choice ->
        selection.bulkMenuOpen = false
        when (choice) {
            is ActivityBulkChoice.Pick -> selection.bulkPicker = choice.picker
            is ActivityBulkChoice.Run -> selection.request(choice.action, items)
            ActivityBulkChoice.Delete -> selection.askingToDelete = true
        }
    }
    val picker = selection.bulkPicker
    val options =
        remember(picker, items, mobileSession.categoryRecords, mobileSession.payeeRecords, mobileSession.accountRecords) {
            picker?.let { activityBulkOptions(it, activityBulkSources(mobileSession), items) }.orEmpty()
        }
    ActivityBulkPickerSheet(picker, options, onDismiss = { selection.bulkPicker = null }) { picked, option ->
        selection.bulkPicker = null
        activityBulkPickedEdit(picked, option)?.let { selection.request(ActivityBulkAction.Edit(it), items) }
    }
    ActivityBulkReconciledDialog(selection, items.count(ActivityBulkItem::reconciled))
    ActivityBulkRunEffect(mobileSession, state.selectedBudgetId, selection)
}

/** The selected transactions in the order they were picked, from the rows the screen has loaded. */
internal fun activityBulkItems(
    selected: Set<String>,
    transactions: List<TransactionItem>,
): List<ActivityBulkItem> {
    val byId = transactions.filter { it.recordId in selected }.associateBy { it.recordId }
    return selected.mapNotNull { id ->
        byId[id]?.let { ActivityBulkItem(id, it.amountMinorUnits, it.cleared == true, it.reconciled == true, it.notes) }
    }
}

/** The same, from search results. */
internal fun activitySearchBulkItems(
    selected: Set<String>,
    records: List<ActivitySearchRecord>,
): List<ActivityBulkItem> {
    val byId = records.filter { it.recordId in selected }.associateBy { it.recordId }
    return selected.mapNotNull { id ->
        byId[id]?.let { ActivityBulkItem(id, it.amountMinorUnits, it.cleared == true, it.reconciled == true, it.notes) }
    }
}

/** Right-click choices for the whole selection, beside the row's own. */
internal fun activitySelectionMenuActions(selection: ActivitySelectionState): List<RowMenuAction> =
    listOf(
        RowMenuAction("Edit selected", Lucide.Pencil) { selection.bulkMenuOpen = true },
        RowMenuAction("Delete selected", Lucide.Trash2, destructive = true) { selection.askingToDelete = true },
    )

private fun activityBulkSources(mobileSession: TandemMobileSession) =
    ActivityBulkSources(
        categories = mobileSession.categoryRecords,
        payees = mobileSession.payeeRecords,
        accounts = mobileSession.accountRecords,
        schedules = liveScheduleSummaries(mobileSession.scheduleRecords, mobileSession.payeeRecords, mobileSession.accountRecords),
        knownTags = mobileSession.activity.activityTransactionRecords.flatMap { noteTags(it.notes) },
    )

/**
 * Runs [action], asking first when it would change reconciled transactions, as Actual does. Clearing leaves
 * reconciled ones as they are, so it doesn't ask.
 */
internal fun ActivitySelectionState.request(
    action: ActivityBulkAction,
    items: List<ActivityBulkItem>,
) {
    val leavesReconciled = action is ActivityBulkAction.Edit && action.edit is BulkTransactionEdit.SetCleared
    if (items.any(ActivityBulkItem::reconciled) && !leavesReconciled) {
        confirmingBulkAction = action
    } else {
        runningBulkAction = action
    }
}

@Composable
private fun ActivityBulkReconciledDialog(
    selection: ActivitySelectionState,
    reconciled: Int,
) {
    val dismiss = { selection.confirmingBulkAction = null }
    TandemChoiceDialog(visible = selection.confirmingBulkAction != null, onDismissRequest = dismiss) {
        SheetCard(
            spacing = TandemSpacing.small,
            header = {
                TandemModalHeader("Change reconciled transactions?", titleSize = TandemTypography.cardTitle, onClose = dismiss)
            },
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    SecondaryButton("Cancel", Modifier.weight(1f), onClick = dismiss)
                    PrimaryButton("Continue", modifier = Modifier.weight(1f)) {
                        selection.runningBulkAction = selection.confirmingBulkAction
                        selection.confirmingBulkAction = null
                    }
                }
            },
        ) {
            val count = if (reconciled == 1) "1 of them is" else "${formatInteger(reconciled)} of them are"
            BodyText("$count reconciled. Changing them can change a balance you already reconciled.")
        }
    }
}

/** Runs the chosen bulk action once, then leaves the selection for the next edit, or clears it after a merge. */
@Composable
private fun ActivityBulkRunEffect(
    mobileSession: TandemMobileSession,
    budgetId: String,
    selection: ActivitySelectionState,
) {
    LaunchedEffect(selection.runningBulkAction) {
        val action = selection.runningBulkAction ?: return@LaunchedEffect
        val ids = selection.selectedTransactionIds.toList()
        val done =
            when (action) {
                is ActivityBulkAction.Edit -> mobileSession.transactions.applyBulkEdit(budgetId, ids, action.edit)
                ActivityBulkAction.Duplicate -> mobileSession.transactions.duplicateTransactions(budgetId, ids)
                ActivityBulkAction.Merge -> mobileSession.transactions.mergeTransactions(budgetId, ids)
            }
        selection.runningBulkAction = null
        if (done && action == ActivityBulkAction.Merge) selection.clear()
    }
}
