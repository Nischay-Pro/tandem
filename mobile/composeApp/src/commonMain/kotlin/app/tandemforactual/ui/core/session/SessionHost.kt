package app.tandemforactual.ui.core.session

import app.tandemforactual.api.client.AuthenticatedDevice
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.BudgetSyncCoordinator
import app.tandemforactual.data.store.BudgetSyncSummary
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.conflicts.ConflictController
import app.tandemforactual.ui.core.session.feedback.BudgetActionRunner
import app.tandemforactual.ui.core.session.planning.PlanningController
import kotlinx.coroutines.CoroutineScope

/**
 * The shared core of the session that feature controllers rely on: the server client, local storage, sync and the
 * action feedback shown to the person. Controllers depend on this instead of the whole session.
 */
internal interface SessionHost {
    val apiClient: TandemApiClient
    val repository: LocalBudgetRepository?
    val sessionScope: CoroutineScope
    val authenticatedDevice: AuthenticatedDevice?
    val isSyncing: Boolean
    val isSigningOut: Boolean
    val isPerformingBudgetAction: Boolean
    val isServerReachable: Boolean
    val pendingWrites: List<OutboxOperationEntity>
    val budgetActionMessage: String?
    val transactionWriteMessage: String?
    val syncError: String?
    val nowEpochMillis: () -> Long

    /** Budget passwords entered this session, kept in memory only, so queued work can be sent later. */
    val scheduleActionEncryptionPasswords: MutableMap<String, String>

    val feedback: BudgetActionRunner
    val budgetQueue: BudgetEditQueue
    val planning: PlanningController
    val conflicts: ConflictController
    val syncCoordinator: BudgetSyncCoordinator?
    val currentDateIso: () -> String
    val accountRecords: List<BudgetRecordEntity>

    fun clearTransactionWriteMessage()

    fun clearCachedBudgetPresentation()

    val isAuthenticated: Boolean
    val isSavingTransaction: Boolean
    val categoryRecords: List<BudgetRecordEntity>
    val categoryGroupRecords: List<BudgetRecordEntity>
    val payeeRecords: List<BudgetRecordEntity>
    val ruleRecords: List<BudgetRecordEntity>
    val preferencesStore: AppPreferencesStore?
    val budgetMutationQueuePersistence: BudgetMutationQueuePersistence?

    fun markSavingTransaction(saving: Boolean)

    fun replacePayeeRecords(records: List<BudgetRecordEntity>)

    fun missingServerFeature(kind: BudgetReorderKind): String?

    suspend fun loadLocalRecords(budgetId: String)

    fun reportBudgetActionMessage(message: String?)

    fun reportTransactionWriteMessage(message: String?)

    fun markServerReachable(reachable: Boolean)

    fun replacePendingWrites(writes: List<OutboxOperationEntity>)

    fun refuseWriteInReadOnlyMode(): Boolean

    suspend fun isCurrentIdentityCacheOwner(): Boolean

    suspend fun activeBudgetCacheOwner(): String?

    suspend fun budgetCacheOwnerForLocalEdit(): String?

    suspend fun <T> withoutBudgetActionFeedback(action: suspend () -> T): T

    suspend fun synchronize(
        budgetId: String,
        encryptionPassword: String?,
        sendQueuedWrites: Boolean = true,
        reconcileUncertainOperationId: String? = null,
        retryQueuedBudgetMutations: Boolean = true,
        background: Boolean = false,
    ): BudgetSyncSummary?
}
