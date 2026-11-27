package app.tandemforactual.ui.core.session.transactions

import app.tandemforactual.data.store.BulkMergeIssue
import app.tandemforactual.data.store.BulkTransactionEdit
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.bulkDuplicatePatch
import app.tandemforactual.data.store.bulkDuplicateSources
import app.tandemforactual.data.store.planBulkEdit
import app.tandemforactual.data.store.planBulkMerge
import app.tandemforactual.ui.core.format.formatInteger
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Applies [edit] to every selected transaction as one action with one undo, through the outbox like any edit. */
internal suspend fun TransactionController.applyBulkEdit(
    budgetId: String,
    ids: List<String>,
    edit: BulkTransactionEdit,
): Boolean {
    if (!canWriteBulk()) return false
    val selection = readBulkSelection(budgetId, ids) ?: return false
    val plan = planBulkEdit(selection.targets, edit, bulkEditContext())
    val now = host.nowEpochMillis()
    val writes = plan.changes.mapNotNull { (target, fields) -> selection.updateWrite(budgetId, target.id, fields, now) }
    val unchanged = plan.skipped + plan.changes.size - writes.size
    return enqueueBulk(budgetId, writes, bulkMessage(bulkEditVerb(edit), writes.size, unchanged))
}

/** Copies the selected transactions, each split whole and each transfer once, as one action. */
internal suspend fun TransactionController.duplicateTransactions(
    budgetId: String,
    ids: List<String>,
): Boolean {
    if (!canWriteBulk()) return false
    val selection = readBulkSelection(budgetId, ids) ?: return false
    val now = host.nowEpochMillis()
    val writes =
        bulkDuplicateSources(selection.targets).map { source ->
            val id = "tandem:${Uuid.random()}"
            val patch = bulkDuplicatePatch(source.body)
            BulkWrite(selection.bulkRequest(budgetId, id, LocalWriteAction.CREATE, null, null, patch, duplicateOptimistic(id, patch), now))
        }
    return enqueueBulk(budgetId, writes, bulkMessage("Duplicated", writes.size, 0))
}

private fun duplicateOptimistic(
    id: String,
    patch: JsonObject,
): JsonObject =
    JsonObject(
        patch +
            mapOf(
                "id" to JsonPrimitive(id),
                "reconciled" to JsonPrimitive(false),
                "importedId" to JsonNull,
                "importedPayee" to JsonNull,
                "startingBalance" to JsonNull,
                "schedule" to JsonNull,
                "transferId" to JsonNull,
                "isParent" to JsonPrimitive(false),
                "isChild" to JsonPrimitive(false),
                "parentId" to JsonNull,
                "subtransactions" to (patch["subtransactions"] ?: JsonArray(emptyList())),
            ),
    )

/** Merges two transactions as Actual does: one keeps the other's details and the other is deleted, as one action. */
internal suspend fun TransactionController.mergeTransactions(
    budgetId: String,
    ids: List<String>,
): Boolean {
    if (!canWriteBulk()) return false
    val selection = readBulkSelection(budgetId, ids) ?: return false
    val (plan, issue) = planBulkMerge(selection.targets)
    if (plan == null) {
        host.reportTransactionWriteMessage(bulkMergeIssueMessage(issue))
        return false
    }
    val now = host.nowEpochMillis()
    val waiting = listOf(plan.keep, plan.drop).any { selection.pending[it.id].orEmpty().isNotEmpty() }
    val keep = if (plan.keepFields.isEmpty()) null else selection.updateWrite(budgetId, plan.keep.id, plan.keepFields, now)
    val drop = selection.deleteWrite(budgetId, plan.drop.id, now)
    if (waiting || drop == null || (keep == null && plan.keepFields.isNotEmpty())) {
        host.reportTransactionWriteMessage("Sync these transactions before merging them.")
        return false
    }
    return enqueueBulk(budgetId, listOfNotNull(keep, drop), "Merged 2 transactions")
}

/** Why two transactions can't merge, or null when they can. */
internal fun bulkMergeIssueMessage(issue: BulkMergeIssue?): String? =
    when (issue) {
        BulkMergeIssue.NOT_TWO -> "Select exactly 2 transactions to merge."
        BulkMergeIssue.AMOUNTS_DIFFER -> "Only transactions with the same amount can be merged."
        BulkMergeIssue.SPLIT -> "Split transactions can’t be merged."
        null -> null
    }

internal fun bulkEditVerb(edit: BulkTransactionEdit): String =
    when (edit) {
        is BulkTransactionEdit.SetCategory -> if (edit.categoryId == null) "Category cleared" else "Category set"
        is BulkTransactionEdit.AddTag -> "Tag added"
        is BulkTransactionEdit.RemoveTag -> "Tag removed"
        is BulkTransactionEdit.SetCleared -> if (edit.cleared) "Marked cleared" else "Marked uncleared"
        is BulkTransactionEdit.MoveToAccount -> "Moved"
        is BulkTransactionEdit.SetPayee -> if (edit.payeeId == null) "Payee cleared" else "Payee set"
        is BulkTransactionEdit.LinkSchedule -> if (edit.scheduleId == null) "Schedule unlinked" else "Linked to schedule"
    }

/** "Category set · 3 transactions, 1 unchanged". */
internal fun bulkMessage(
    verb: String,
    changed: Int,
    unchanged: Int,
): String {
    val count = if (changed == 1) "1 transaction" else "${formatInteger(changed)} transactions"
    return if (unchanged > 0) "$verb · $count, ${formatInteger(unchanged)} unchanged" else "$verb · $count"
}
