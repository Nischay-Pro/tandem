package app.tandemforactual.ui

import app.tandemforactual.ui.model.booleanValue
import app.tandemforactual.ui.model.longValue
import app.tandemforactual.ui.model.stringValue
import kotlinx.serialization.json.JsonObject

internal data class ConflictFieldComparison(
    val label: String,
    val editedValue: String,
    val actualValue: String,
)

internal data class ConflictFieldNameLookup(
    val payees: Map<String, String> = emptyMap(),
    val accounts: Map<String, String> = emptyMap(),
    val categories: Map<String, String> = emptyMap(),
)

internal fun conflictActualAmountText(
    serverConfirmed: Boolean,
    isSyncing: Boolean,
    actualDeleted: Boolean,
    amount: Long?,
    formatAmount: (Long) -> String,
): String =
    when {
        serverConfirmed && actualDeleted -> "Deleted"
        !serverConfirmed && isSyncing -> "Checking…"
        !serverConfirmed -> "Not checked"
        amount != null -> formatAmount(amount)
        else -> "Unavailable"
    }

internal fun conflictFieldComparisons(
    edited: JsonObject,
    actual: JsonObject,
    names: ConflictFieldNameLookup,
    formatAmount: (Long) -> String,
    formatDate: (String) -> String,
): List<ConflictFieldComparison> {
    val editedFields = conflictFieldValues(edited, names, formatAmount, formatDate)
    val actualFields = conflictFieldValues(actual, names, formatAmount, formatDate)
    return editedFields.mapNotNull { (label, editedValue) ->
        val actualValue = actualFields.getValue(label)
        if (editedValue == actualValue) {
            null
        } else {
            ConflictFieldComparison(label, editedValue, actualValue)
        }
    }
}

private fun conflictFieldValues(
    snapshot: JsonObject,
    names: ConflictFieldNameLookup,
    formatAmount: (Long) -> String,
    formatDate: (String) -> String,
): LinkedHashMap<String, String> =
    linkedMapOf(
        "Amount" to (snapshot.longValue("amount")?.let(formatAmount) ?: "Unavailable"),
        "Payee" to conflictPayeeValue(snapshot, names),
        "Category" to conflictNamedValue(snapshot.stringValue("category"), names.categories, "Uncategorized"),
        "Date" to (snapshot.stringValue("date")?.let(formatDate) ?: "Unavailable"),
        "Account" to conflictNamedValue(snapshot.stringValue("account"), names.accounts, "Unavailable"),
        "Notes" to (snapshot.stringValue("notes") ?: "No notes"),
        "Cleared" to conflictClearedValue(snapshot),
    )

private fun conflictPayeeValue(
    snapshot: JsonObject,
    names: ConflictFieldNameLookup,
): String {
    val importedPayee = snapshot.stringValue("importedPayee") ?: snapshot.stringValue("imported_payee")
    if (importedPayee != null) return importedPayee
    val payeeId = snapshot.stringValue("payee") ?: return "No payee"
    return names.payees[payeeId] ?: "Unavailable"
}

private fun conflictNamedValue(
    id: String?,
    names: Map<String, String>,
    missingValue: String,
): String =
    when {
        id == null -> missingValue
        else -> names[id] ?: "Unavailable"
    }

private fun conflictClearedValue(snapshot: JsonObject): String {
    val cleared = snapshot.stringValue("cleared")?.lowercase()
    return when {
        snapshot.booleanValue("reconciled") == true || cleared == "reconciled" -> "Reconciled"
        snapshot.booleanValue("cleared") == true || cleared == "cleared" -> "Cleared"
        snapshot.booleanValue("cleared") == false || cleared == "uncleared" -> "Uncleared"
        else -> "Unavailable"
    }
}
