package app.tandemforactual.ui.feature.accounts

import androidx.compose.foundation.lazy.LazyListScope
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind

/** Asks which account a dropped or shared statement belongs to; opening an account starts its import. */
internal fun LazyListScope.pendingStatementItem(state: AppState) {
    val file = state.pendingStatementFile ?: return
    item(key = "pending-statement") {
        InlineMessage(
            "Choose the account to import ${file.name} into.",
            InlineMessageKind.INFO,
            onDismiss = { state.pendingStatementFile = null },
        )
    }
}
