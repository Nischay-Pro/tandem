package app.tandemforactual.ui.core.session.activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.store.findTransactionPage
import app.tandemforactual.ui.core.session.ACTIVITY_TRANSACTION_PAGE_SIZE
import app.tandemforactual.ui.core.session.SessionHost
import app.tandemforactual.ui.core.session.activity.ActivityController
import app.tandemforactual.ui.core.session.activityTransactionRefreshLimit
import app.tandemforactual.ui.core.session.mergeRefreshedActivityTransactions
import app.tandemforactual.ui.core.state.AccountActivitySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

/** Activity transactions loaded a page at a time, and per-account activity kept for quick returns. */
internal class ActivityController(
    private val host: SessionHost,
) {
    val activityTransactionRecords = mutableStateListOf<BudgetRecordEntity>()

    var activityTransactionRecordsVersion by mutableStateOf(0)
        private set

    var activityTransactionPageBudgetId by mutableStateOf("")
        private set

    var isLoadingActivityTransactions by mutableStateOf(false)
        private set

    var hasMoreActivityTransactions by mutableStateOf(true)
        private set

    var activityTransactionPageError by mutableStateOf<String?>(null)
        private set

    var activityTransactionPageGeneration by mutableStateOf(0)
        private set

    private var activityCursorDate: String? = null

    private var activityCursorId = ""

    private val activityTransactionPageMutex = Mutex()

    suspend fun loadMoreActivityTransactions(
        budgetId: String,
        reset: Boolean = false,
    ) {
        val localRepository = host.repository ?: return
        if (budgetId.isBlank()) return
        if (!host.isCurrentIdentityCacheOwner()) {
            host.clearCachedBudgetPresentation()
            return
        }
        activityTransactionPageMutex.lock()
        try {
            if (reset || activityTransactionPageBudgetId != budgetId) {
                Snapshot.withMutableSnapshot {
                    activityTransactionPageBudgetId = budgetId
                    activityTransactionRecords.clear()
                    activityTransactionRecordsVersion += 1
                    activityTransactionPageGeneration += 1
                }
                activityCursorDate = null
                activityCursorId = ""
                hasMoreActivityTransactions = true
                activityTransactionPageError = null
            }
            if (!hasMoreActivityTransactions) return
            isLoadingActivityTransactions = true
            activityTransactionPageError = null
            try {
                val page =
                    localRepository.findTransactionPage(
                        budgetId = budgetId,
                        cursorDate = activityCursorDate,
                        cursorId = activityCursorId,
                        limit = ACTIVITY_TRANSACTION_PAGE_SIZE,
                    )
                if (activityTransactionPageBudgetId != budgetId) return
                val knownIds = activityTransactionRecords.asSequence().map(BudgetRecordEntity::recordId).toHashSet()
                val newRecords = page.filterNot { it.recordId in knownIds }
                if (newRecords.isNotEmpty()) {
                    Snapshot.withMutableSnapshot {
                        activityTransactionRecords.addAll(newRecords)
                        activityTransactionRecordsVersion += 1
                    }
                }
                page.lastOrNull()?.let { last ->
                    activityCursorDate = last.recordDate
                    activityCursorId = last.recordId
                }
                hasMoreActivityTransactions = page.size == ACTIVITY_TRANSACTION_PAGE_SIZE
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                activityTransactionPageError = "Couldn’t load older transactions. Try again."
            } finally {
                isLoadingActivityTransactions = false
            }
        } finally {
            activityTransactionPageMutex.unlock()
        }
    }

    internal suspend fun refreshActivityTransactionPages(budgetId: String) {
        if (activityTransactionPageBudgetId != budgetId) return
        val localRepository = host.repository ?: return
        activityTransactionPageMutex.lock()
        try {
            if (activityTransactionPageBudgetId != budgetId) return
            val refreshLimit = activityTransactionRefreshLimit(activityTransactionRecords.size)
            val previouslyLoaded = activityTransactionRecords.toList()
            val previouslyHadMore = hasMoreActivityTransactions
            isLoadingActivityTransactions = true
            try {
                val refreshed = localRepository.findTransactionPage(budgetId = budgetId, limit = refreshLimit)
                if (activityTransactionPageBudgetId != budgetId) return
                val merged = mergeRefreshedActivityTransactions(previouslyLoaded, refreshed, pageIsComplete = refreshed.size < refreshLimit)
                Snapshot.withMutableSnapshot {
                    activityTransactionRecords.clear()
                    activityTransactionRecords.addAll(merged)
                    activityTransactionRecordsVersion += 1
                }
                merged.lastOrNull()?.let { last ->
                    activityCursorDate = last.recordDate
                    activityCursorId = last.recordId
                } ?: run {
                    activityCursorDate = null
                    activityCursorId = ""
                }
                hasMoreActivityTransactions = previouslyHadMore || refreshed.size == refreshLimit
                activityTransactionPageError = null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                activityTransactionPageError = "Couldn’t refresh transactions. Try again."
            } finally {
                isLoadingActivityTransactions = false
            }
        } finally {
            activityTransactionPageMutex.unlock()
        }
    }

    suspend fun ensureActivityTransactionsLoaded(budgetId: String) {
        if (activityTransactionPageBudgetId != budgetId) {
            loadMoreActivityTransactions(budgetId, reset = true)
        } else if (activityTransactionRecords.isEmpty() && hasMoreActivityTransactions && activityTransactionPageError == null) {
            loadMoreActivityTransactions(budgetId)
        }
    }

    fun cachedAccountActivity(
        budgetId: String,
        accountId: String,
    ): AccountActivitySnapshot? = accountActivitySnapshots["$budgetId\u001f$accountId"]

    fun cacheAccountActivity(
        budgetId: String,
        accountId: String,
        snapshot: AccountActivitySnapshot,
    ) {
        if (budgetId.isBlank() || accountId.isBlank()) return
        accountActivitySnapshots["$budgetId\u001f$accountId"] = snapshot
        while (accountActivitySnapshots.size > 8) {
            accountActivitySnapshots.remove(accountActivitySnapshots.keys.first())
        }
    }

    private val accountActivitySnapshots = linkedMapOf<String, AccountActivitySnapshot>()

    /** Forgets the loaded activity pages, for sign-out or switching budgets. */
    fun reset() {
        activityTransactionRecords.clear()
        activityTransactionRecordsVersion += 1
        activityTransactionPageBudgetId = ""
        activityTransactionPageError = null
        hasMoreActivityTransactions = false
    }
}
