package app.tandemforactual.ui.core.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.parseAmountMinorUnits

internal fun decodeScrollPositions(serialized: String?): Map<String, Int> =
    serialized
        ?.split('|')
        ?.mapNotNull { entry ->
            val delimiter = entry.lastIndexOf(':')
            if (delimiter < 1) {
                null
            } else {
                entry.substring(delimiter + 1).toIntOrNull()?.let { entry.substring(0, delimiter) to it }
            }
        }?.toMap()
        ?: emptyMap()

internal fun decodeLazyScrollPositions(serialized: String?): Map<String, Pair<Int, Int>> =
    serialized
        ?.split('|')
        ?.mapNotNull { entry ->
            val delimiter = entry.lastIndexOf(':')
            if (delimiter < 1) {
                null
            } else {
                val values = entry.substring(delimiter + 1).split(',', limit = 2)
                val index = values.getOrNull(0)?.toIntOrNull()
                val offset = values.getOrNull(1)?.toIntOrNull()
                if (index == null || offset == null || index < 0 || offset < 0) {
                    null
                } else {
                    entry.substring(0, delimiter) to (index to offset)
                }
            }
        }?.toMap()
        ?: emptyMap()

internal fun normalizeHomeLayoutName(layout: String): String =
    when (layout) {
        "Essentials first", "Bills first" -> "Essentials"
        else -> layout
    }

internal data class BudgetSelectionSnapshot(
    val budgetId: String,
    val name: String,
    val isEncrypted: Boolean,
)

internal fun transactionFormHasUnsavedChanges(
    state: AppState,
    route: AppRoute?,
): Boolean {
    val amount = state.parseAmountMinorUnits(state.transactionAmount)
    return when (route) {
        AppRoute.NEW_TRANSACTION -> {
            amount == null ||
                amount != 0L ||
                state.newTransactionAccountId != state.newTransactionInitialAccountId ||
                state.transactionAccount != state.newTransactionInitialAccountName ||
                state.transactionPayee.isNotBlank() ||
                state.newTransactionPayeeId.isNotBlank() ||
                state.transactionCategory.isNotBlank() ||
                state.newTransactionCategoryId.isNotBlank() ||
                state.transactionDate != state.newTransactionInitialDate ||
                state.transactionType != "Spending" ||
                state.transactionNotes.isNotBlank() ||
                state.transactionSplit != null
        }

        AppRoute.EDIT_TRANSACTION -> {
            amount == null ||
                state.selectedTransactionAmountMinor?.let { amount != kotlin.math.abs(it) } != false ||
                state.transactionType !=
                transactionTypeForSelection(
                    state.selectedTransactionIsTransfer,
                    state.selectedTransactionAmountMinor,
                ) ||
                state.transactionNotes != state.selectedTransactionNotes ||
                state.newTransactionAccountId != state.selectedTransactionAccountId ||
                state.newTransactionPayeeId != state.selectedTransactionPayeeId ||
                state.newTransactionCategoryId != state.selectedTransactionCategoryId ||
                state.transactionDate != state.selectedTransactionDate ||
                state.transactionAccount != state.selectedTransactionAccountName ||
                state.transactionPayee != state.selectedTransactionPayeeName ||
                state.transactionCategory != state.selectedTransactionCategoryName
        }

        else -> {
            false
        }
    }
}

internal fun restoreTransactionFormBaseline(
    state: AppState,
    route: AppRoute?,
) {
    when (route) {
        AppRoute.NEW_TRANSACTION -> {
            state.transactionAmount = state.formatAmount(0)
            state.transactionAccount = state.newTransactionInitialAccountName
            state.newTransactionAccountId = state.newTransactionInitialAccountId
            state.transactionPayee = ""
            state.newTransactionPayeeId = ""
            state.transactionCategory = ""
            state.newTransactionCategoryId = ""
            state.transactionDate = state.newTransactionInitialDate
            state.transactionType = "Spending"
            state.transactionNotes = ""
            state.transactionSplit = null
        }

        AppRoute.EDIT_TRANSACTION -> {
            state.selectedTransactionAmountMinor?.let { state.transactionAmount = state.formatAmount(kotlin.math.abs(it)) }
            state.transactionType = transactionTypeForSelection(state.selectedTransactionIsTransfer, state.selectedTransactionAmountMinor)
            state.transactionAccount = state.selectedTransactionAccountName
            state.newTransactionAccountId = state.selectedTransactionAccountId
            state.transactionPayee = state.selectedTransactionPayeeName
            state.newTransactionPayeeId = state.selectedTransactionPayeeId
            state.transactionCategory = state.selectedTransactionCategoryName
            state.newTransactionCategoryId = state.selectedTransactionCategoryId
            state.transactionDate = state.selectedTransactionDate
            state.transactionNotes = state.selectedTransactionNotes
        }

        else -> {
            Unit
        }
    }
}

private fun transactionTypeForSelection(
    isTransfer: Boolean,
    amountMinor: Long?,
): String =
    when {
        isTransfer -> "Transfer"
        (amountMinor ?: 0L) < 0 -> "Spending"
        else -> "Income"
    }
