@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.format.formatSignedCurrency
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatSignedCurrency
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** The two versions of the transaction, this device and Actual, as selectable cards. */
@Composable
internal fun ConflictVersionChoice(
    operation: OutboxOperationEntity,
    record: BudgetRecordEntity?,
    review: ConflictReviewState,
    deviceVersion: JsonObject?,
    actualVersion: JsonObject?,
    actualDeleted: Boolean,
    userEditedFields: Set<String>?,
    comparisons: List<ConflictFieldComparison>,
    categoryName: String,
    actualLabel: String,
    keepThisDeviceEnabled: Boolean,
    keepActualEnabled: Boolean,
    state: AppState,
) {
    val amountComparison = comparisons.firstOrNull { it.label == "Amount" }
    val categoryComparison = comparisons.firstOrNull { it.label == "Category" }

    fun amountOf(version: JsonObject?): String =
        (version?.get("amount") as? JsonPrimitive)?.longOrNull?.let(state::formatSignedCurrency) ?: "Unavailable"
    val thisDeviceKeepable = operation.action == "DELETE" || record?.remoteDeleted != true
    LaunchedEffect(operation.operationId, thisDeviceKeepable) {
        if (!thisDeviceKeepable) review.keepThisDevice = false
    }
    val editedFields = conflictEditedFieldLabels(userEditedFields)
    ConflictVersionCard(
        label = "This device",
        amount = if (operation.action == "DELETE") "Delete" else amountComparison?.editedValue ?: amountOf(deviceVersion),
        detail =
            when {
                operation.action == "DELETE" -> "Remove this transaction from Actual"
                !thisDeviceKeepable -> "Can’t be kept because Actual deleted this transaction"
                editedFields.isNotEmpty() && "Category" !in editedFields ->
                    "${editedFields.joinToString(", ")} edited"
                else -> "Category · ${categoryComparison?.editedValue ?: categoryName}"
            },
        selected = review.keepThisDevice,
        enabled = keepThisDeviceEnabled,
    ) { review.keepThisDevice = true }
    ConflictVersionCard(
        label = actualLabel,
        amount = if (actualDeleted) "Deleted" else amountComparison?.actualValue ?: amountOf(actualVersion),
        detail =
            when {
                actualDeleted -> "Already removed in Actual"
                actualVersion == null -> "Not loaded yet"
                else -> "Category · ${categoryComparison?.actualValue ?: categoryName}"
            },
        selected = !review.keepThisDevice,
        enabled = keepActualEnabled,
    ) { review.keepThisDevice = false }
}

private fun conflictEditedFieldLabels(userEditedFields: Set<String>?): List<String> =
    userEditedFields
        .orEmpty()
        .mapNotNull { field ->
            when (field) {
                "amount" -> "Amount"
                "category" -> "Category"
                "payee" -> "Payee"
                "date" -> "Date"
                "notes" -> "Notes"
                "account" -> "Account"
                "cleared" -> "Cleared"
                else -> null
            }
        }.distinct()
