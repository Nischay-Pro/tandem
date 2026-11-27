package app.tandemforactual.ui.core.session.transactions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.session.SessionHost
import app.tandemforactual.ui.core.session.UndoNotice
import app.tandemforactual.ui.core.session.transactions.TransactionController
import app.tandemforactual.ui.design.feedback.InlineMessageKind

/** Creating, updating and deleting transactions, with undo for deletes. */
internal class TransactionController(
    internal val host: SessionHost,
) {
    /** Shows the shared undo notice for any action, so every undo in the app looks and behaves the same. */
    fun showUndoNotice(
        message: String,
        budgetId: String,
        undo: suspend () -> Unit,
    ) {
        undoNoticeCount += 1
        undoNotice = UndoNotice(undoNoticeCount, message, budgetId, emptySet(), undo)
    }

    /** Shows a short confirmation in the same place as the undo notice, with nothing to undo. */
    fun showSaveNotice(
        budgetId: String,
        title: String,
        message: String,
        kind: InlineMessageKind,
        detail: String?,
    ) {
        undoNoticeCount += 1
        undoNotice = UndoNotice(undoNoticeCount, message, budgetId, emptySet(), title = title, kind = kind, detail = detail)
    }

    fun dismissUndoNotice(id: Long) {
        if (undoNotice?.id == id) undoNotice = null
    }

    var undoNotice by mutableStateOf<UndoNotice?>(null)
        internal set

    internal var undoNoticeCount = 0L
}
