@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.state.AppState

internal fun selectTransactionType(
    state: AppState,
    type: String,
) {
    val previousType = state.transactionType
    if (previousType != type) {
        if (type == "Transfer") {
            state.transactionPayee = ""
            state.newTransactionPayeeId = ""
            state.transactionCategory = ""
            state.newTransactionCategoryId = ""
        } else if (previousType == "Transfer") {
            state.transactionPayee = ""
            state.newTransactionPayeeId = ""
        }
    }
    state.transactionType = type
}
