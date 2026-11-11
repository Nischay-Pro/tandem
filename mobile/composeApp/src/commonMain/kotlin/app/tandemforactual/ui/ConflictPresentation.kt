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

internal fun conflictReasonMessage(
    conflictType: String?,
    conflictingFields: List<String>,
    action: String,
    actualDeleted: Boolean,
): String? {
    if (actualDeleted || conflictType in setOf("deleted-remotely", "deleted-during-write", "delete-not-visible-after-sync")) {
        return "Actual deleted this transaction while your edit was waiting."
    }
    return when (conflictType) {
        "delete-versus-edit" -> "Actual changed this transaction while you were deleting it."
        "same-field-edit", "changed-during-write", "stale-conflict" ->
            changedFieldReason(conflictingFields)
        "transaction-structure-changed-during-write" ->
            "The transaction’s structure changed in Actual while your edit was waiting."
        "transfer-pair-changed", "transfer-pair-changed-after-write" ->
            "The linked transfer changed in Actual while your edit was waiting."
        "transfer-pair-incomplete", "transfer-pair-incomplete-after-write" ->
            "The other side of this transfer is missing in Actual."
        "transaction-id-already-exists" -> "This transaction already exists in Actual."
        else ->
            if (action == "DELETE" && conflictingFields.isNotEmpty()) {
                "Actual changed this transaction while you were deleting it."
            } else if (conflictingFields.isNotEmpty()) {
                changedFieldReason(conflictingFields)
            } else {
                null
            }
    }
}

internal fun rejectedTransactionReferenceMessage(errorCode: String?): String =
    when (errorCode) {
        "account-not-found" -> "This account no longer exists in Actual. Choose another account and try again."
        "account-closed" -> "This account is closed in Actual. Choose another account and try again."
        "payee-not-found" -> "This payee no longer exists in Actual. Choose another payee and try again."
        "category-not-found" -> "This category no longer exists in Actual. Choose another category and try again."
        "category-hidden" -> "This category is hidden in Actual. Choose another category and try again."
        "split-child-field-not-editable" ->
            "Actual manages this part through the full transaction. Update the full transaction in Actual."
        else -> "Actual kept the previous value. Review the edit and try again."
    }

private fun changedFieldReason(conflictingFields: List<String>): String {
    val labels =
        conflictingFields
            .mapNotNull { field ->
                when (field) {
                    "amount" -> "amount"
                    "payee" -> "payee"
                    "category" -> "category"
                    "date" -> "date"
                    "account" -> "account"
                    "notes" -> "notes"
                    "cleared", "reconciled" -> "cleared status"
                    "transferId" -> "linked transfer"
                    "isParent", "isChild", "parentId", "subtransactions" -> "transaction structure"
                    else -> null
                }
            }.distinct()
    if (labels.isEmpty()) return "Actual changed the same details while your edit was waiting."
    val details =
        when (labels.size) {
            1 -> labels.single()
            2 -> "${labels[0]} and ${labels[1]}"
            else -> labels.dropLast(1).joinToString(", ") + ", and ${labels.last()}"
        }
    return "Actual changed the $details while your edit was waiting."
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
