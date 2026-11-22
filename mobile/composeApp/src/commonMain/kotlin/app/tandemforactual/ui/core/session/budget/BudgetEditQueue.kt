package app.tandemforactual.ui.core.session.budget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.session.BUDGET_ORDER_FLUSH_INTERVAL_MS
import app.tandemforactual.ui.core.session.BudgetReorderQueueState
import app.tandemforactual.ui.core.session.SessionHost
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits
import kotlinx.coroutines.sync.Mutex

/** Budget edits saved on the device while offline or busy, sent to Actual in order and reconciled afterwards. */
internal class BudgetEditQueue(
    internal val host: SessionHost,
    internal val editsProvider: () -> BudgetEdits,
) {
    internal val edits: BudgetEdits get() = editsProvider()

    internal var budgetReorderQueueState by mutableStateOf(BudgetReorderQueueState())

    var isPersistingBudgetMutation by mutableStateOf(false)
        internal set

    var isCorrectingBudgetMutation by mutableStateOf(false)
        internal set

    var budgetMutationCorrectionMessage by mutableStateOf<String?>(null)
        internal set

    internal var budgetReorderQueue: BudgetReorderQueueState
        get() = budgetReorderQueueState
        set(value) {
            budgetReorderQueueState = value
            persistBudgetReorderQueue()
        }

    internal var budgetReorderRetrySignal by mutableStateOf(0)

    internal var budgetOrderFlushSignal by mutableStateOf(0)

    /** When the oldest unsent reorder draft was queued; drafts are flushed [BUDGET_ORDER_FLUSH_INTERVAL_MS] later. */
    internal var orderDraftsHeldSinceEpochMs: Long? = null

    internal val budgetQueuePersistenceMutex = Mutex()

    internal var budgetQueueLoaded = false

    internal val budgetQueueRestoreMutex = Mutex()
}
