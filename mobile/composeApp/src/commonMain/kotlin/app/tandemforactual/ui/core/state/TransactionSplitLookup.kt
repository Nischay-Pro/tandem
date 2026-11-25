package app.tandemforactual.ui.core.state

import app.tandemforactual.data.database.BudgetRecordEntity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** A split as the edit form loads it: the parent transaction and a line for each part. */
internal data class LoadedSplit(
    val parent: BudgetRecordEntity,
    val lines: List<SplitLine>,
)

/**
 * The whole split that [recordId] belongs to, whether it is the parent or one of the parts, or null when it isn't
 * split. A parent Tandem created keeps Tandem's id while its parts point at Actual's, so the parent is found by the
 * parts it lists.
 */
internal fun findSplit(
    records: List<BudgetRecordEntity>,
    recordId: String,
    categoryName: (String) -> String?,
    formatAmount: (Long) -> String,
): LoadedSplit? {
    fun parts(record: BudgetRecordEntity): List<JsonObject> =
        (record.recordBody()?.get("subtransactions") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    val parent =
        records.firstOrNull { it.recordId == recordId && it.isParent }
            ?: records.firstOrNull { record -> record.isParent && parts(record).any { it.id() == recordId } }
            ?: return null
    val lines =
        parts(parent).mapIndexed { index, part ->
            val category = (part["category"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            SplitLine(
                key = index + 1,
                amountText = formatAmount(kotlin.math.abs((part["amount"] as? JsonPrimitive)?.longOrNull ?: 0L)),
                categoryId = category,
                categoryName = categoryName(category).orEmpty(),
                id = part.id(),
            )
        }
    return LoadedSplit(parent, lines).takeIf { lines.isNotEmpty() }
}

private fun JsonObject.id(): String? = (this["id"] as? JsonPrimitive)?.contentOrNull

/** Points the edit form at the split's parent, with its amount, payee and notes and a line for each part. */
internal fun loadSplitForEdit(
    state: AppState,
    split: LoadedSplit,
    payeeName: String,
    formatAmount: (Long) -> String,
) {
    val parent = split.parent
    val amount = parent.amountMinor ?: 0L
    val notes = parent.notes.orEmpty()
    val draft = TransactionSplitDraft(split.lines)
    state.selectedTransactionRecordId = parent.recordId
    state.selectedTransactionAmountMinor = amount
    state.transactionAmount = formatAmount(kotlin.math.abs(amount))
    state.transactionNotes = notes
    state.selectedTransactionNotes = notes
    state.newTransactionPayeeId = parent.payeeId.orEmpty()
    state.selectedTransactionPayeeId = parent.payeeId.orEmpty()
    state.transactionPayee = payeeName
    state.selectedTransactionPayeeName = payeeName
    state.transactionCategory = ""
    state.newTransactionCategoryId = ""
    state.selectedTransactionCategoryId = ""
    state.selectedTransactionCategoryName = ""
    state.transactionSplit = draft
    state.selectedTransactionSplit = draft
}
