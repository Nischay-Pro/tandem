package app.tandemforactual.ui.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.ActualLoginMethods
import app.tandemforactual.api.client.AuthenticatedDevice
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.DirectBudgetSyncRemote
import app.tandemforactual.api.client.ServerCapabilities
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.BudgetSyncStateEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.database.SearchableBudgetTransaction
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.data.store.BudgetSyncCoordinator
import app.tandemforactual.data.store.BudgetSyncSummary
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.activity.ActivityController
import app.tandemforactual.ui.core.session.banking.BankingController
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits
import app.tandemforactual.ui.core.session.conflicts.ConflictController
import app.tandemforactual.ui.core.session.feedback.BudgetActionRunner
import app.tandemforactual.ui.core.session.insights.InsightsController
import app.tandemforactual.ui.core.session.planning.PlanningController
import app.tandemforactual.ui.core.session.schedules.ScheduleActionController
import app.tandemforactual.ui.core.session.transactions.TransactionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-memory session state. Device credentials use a platform store; budget passwords are never persisted. */
internal class TandemMobileSession(
    override val apiClient: TandemApiClient,
    database: TandemDatabase?,
    override val nowEpochMillis: () -> Long,
    internal val credentialStore: DeviceCredentialStore? = null,
    budgetMutationQueuePersistenceOverride: BudgetMutationQueuePersistence? = null,
    override val currentDateIso: () -> String = ::platformTodayIsoDate,
    internal val openIdLoginProofState: OpenIdLoginProofState = OpenIdLoginProofState(nowEpochMillis),
    internal val revokeCurrentDeviceForSignOut: suspend () -> Unit = { apiClient.revokeCurrentDevice() },
) : SessionHost {
    /** Bank providers, links and bank sync. */
    val banking =
        BankingController(
            apiClient,
            nowEpochMillis,
            { mainStorage?.database?.let(::AppPreferencesStore) },
        ) { budgetId, password, message, action ->
            feedback.performBudgetAction(budgetId, password, message, action = action)
        }

    /** Budget checkup, change history, rule preview and export. */
    val insights =
        InsightsController(apiClient) { budgetId, password, message, action ->
            feedback.performBudgetAction(budgetId, password, message, action = action)
        }

    /** Queued schedule actions and their status. */
    val schedules = ScheduleActionController(this)

    /** Messages, retries and the in-progress flag for budget changes made from any screen. */
    override val feedback = BudgetActionRunner(this)

    override val budgetActionMessage: String? get() = feedback.budgetActionMessage

    override fun reportBudgetActionMessage(message: String?) {
        feedback.budgetActionMessage = message
    }

    override val isPerformingBudgetAction: Boolean get() = feedback.isPerformingBudgetAction

    override suspend fun <T> withoutBudgetActionFeedback(action: suspend () -> T): T = feedback.withoutBudgetActionFeedback(action)

    /** Budget edits saved on the device and sent to Actual in order. */
    override val budgetQueue: BudgetEditQueue = BudgetEditQueue(this) { budgetEdits }

    /** Budget changes: accounts, categories, payees, rules, templates and moves. */
    val budgetEdits: BudgetEdits = BudgetEdits(this, budgetQueue)

    /** Conflicting and uncertain writes. */
    override val conflicts = ConflictController(this)

    /** Transaction writes and undo. */
    val transactions = TransactionController(this)

    /** Activity pages and account activity. */
    val activity = ActivityController(this)

    internal inner class BudgetStorage(
        val database: TandemDatabase,
        val budgetId: String? = null,
    ) {
        val repository = LocalBudgetRepository(database, nowEpochMillis)
        val queuePersistence = LocalBudgetRepositoryMutationQueuePersistence(repository)
        val coordinator =
            BudgetSyncCoordinator(
                repository,
                DirectBudgetSyncRemote(apiClient),
            )
    }

    internal val mainStorage = database?.let { BudgetStorage(it) }
    internal var budgetStorage by mutableStateOf<BudgetStorage?>(null)
    internal val storage: BudgetStorage? get() = budgetStorage ?: mainStorage
    override val repository: LocalBudgetRepository? get() = storage?.repository

    /** Goals, debts, retirement, reimbursements and the other planning records Actual doesn't hold. */
    override val planning = PlanningController(apiClient, { storage?.database }, nowEpochMillis)

    override val budgetMutationQueuePersistence: BudgetMutationQueuePersistence?
        get() = queuePersistenceOverride ?: storage?.queuePersistence
    internal val queuePersistenceOverride = budgetMutationQueuePersistenceOverride
    override val preferencesStore: AppPreferencesStore? = database?.let(::AppPreferencesStore)
    override val sessionScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val syncCoordinator: BudgetSyncCoordinator? get() = storage?.coordinator

    var databaseGeneration by mutableStateOf(0)
        internal set

    internal val boundEncryptedBudgetId: String? get() = budgetStorage?.budgetId

    /** Unsent edits of an encrypted budget downloaded again, held until its new vault is created. */
    internal val writesToReplay = mutableMapOf<String, List<app.tandemforactual.data.database.OutboxOperationEntity>>()

    internal var encryptedStorageRequired by mutableStateOf<Set<String>>(emptySet())

    var isUsingLocalServer by mutableStateOf(false)
        internal set

    var serverCapabilities by mutableStateOf<ServerCapabilities?>(null)
        internal set

    internal var capabilitiesCheckedAtEpochMs = 0L

    override fun missingServerFeature(kind: BudgetReorderKind): String? = missingServerFeature(kind, serverCapabilities)

    var localServerError by mutableStateOf<String?>(null)
        internal set

    override var authenticatedDevice by mutableStateOf<AuthenticatedDevice?>(null)
        internal set

    var budgets by mutableStateOf<List<BudgetCatalogItem>>(emptyList())
        internal set

    var cachedBudgets by mutableStateOf<List<BudgetCatalogItem>>(emptyList())
        internal set

    var budgetCatalogLoaded by mutableStateOf(false)
        internal set

    var budgetCatalogVersion by mutableStateOf(0)
        internal set

    override var isServerReachable by mutableStateOf(false)
        internal set

    override fun markServerReachable(reachable: Boolean) {
        isServerReachable = reachable
    }

    var hasCachedSnapshot by mutableStateOf(false)
        internal set

    var cachedSnapshotBudgetId by mutableStateOf("")
        internal set

    var isSigningIn by mutableStateOf(false)
        internal set

    var signInError by mutableStateOf<String?>(null)
        internal set

    var actualLoginMethods by mutableStateOf<ActualLoginMethods?>(null)
        internal set

    var isLoadingLoginMethods by mutableStateOf(false)
        internal set

    var credentialStorageError by mutableStateOf<String?>(null)
        internal set

    var deviceRevocationError by mutableStateOf<String?>(null)
        internal set

    var signOutError by mutableStateOf<String?>(null)
        internal set

    var signOutNotice by mutableStateOf<String?>(null)
        internal set

    override var isSigningOut by mutableStateOf(false)
        internal set

    var isRevokingDevice by mutableStateOf(false)
        internal set

    var isCredentialStored by mutableStateOf(false)
        internal set

    var isRestoringStoredSession by mutableStateOf(false)
        internal set

    var isLoadingBudgets by mutableStateOf(false)
        internal set

    var budgetError by mutableStateOf<String?>(null)
        internal set

    internal var syncPresentation by mutableStateOf(SyncPresentationState())
    internal val synchronizationMutex = Mutex()

    override val isSyncing: Boolean get() = syncPresentation.isSyncing

    var syncComplete by mutableStateOf(false)
        internal set

    override val syncError: String? get() = syncPresentation.error

    val syncRecovery: SyncRecovery get() = syncRecovery(syncPresentation.errorCode, syncPresentation.errorStatus)

    var budgetPasswordRejection by mutableStateOf<String?>(null)
        internal set

    var syncedTransactionCount by mutableStateOf(0)
    var cachedTransactionCount by mutableStateOf(0)
        internal set

    override var pendingWrites by mutableStateOf<List<OutboxOperationEntity>>(emptyList())
        internal set

    override fun replacePendingWrites(writes: List<OutboxOperationEntity>) {
        pendingWrites = writes
    }

    var conflictRecordsByRecordId by mutableStateOf<Map<String, BudgetRecordEntity>>(emptyMap())
        internal set

    override var isSavingTransaction by mutableStateOf(false)
        internal set

    override fun markSavingTransaction(saving: Boolean) {
        isSavingTransaction = saving
    }

    override var transactionWriteMessage by mutableStateOf<String?>(null)
        internal set

    override fun reportTransactionWriteMessage(message: String?) {
        transactionWriteMessage = message
    }

    override val scheduleActionEncryptionPasswords: MutableMap<String, String> = mutableMapOf()

    override suspend fun activeBudgetCacheOwner(): String? = signedInBudgetCacheOwner()

    override suspend fun budgetCacheOwnerForLocalEdit(): String? = cacheOwnerForLocalEdit()

    internal var readOnlyMode = false

    fun setReadOnlyMode(enabled: Boolean) {
        readOnlyMode = enabled
    }

    override fun clearTransactionWriteMessage() {
        transactionWriteMessage = null
        conflicts.clearFeedback()
    }

    override fun refuseWriteInReadOnlyMode(): Boolean {
        if (!readOnlyMode) return false
        transactionWriteMessage = "Read-only mode is on. Turn it off before changing this data."
        return true
    }

    var lastSyncSummary by mutableStateOf<BudgetSyncSummary?>(null)
        internal set

    var localSyncState by mutableStateOf<BudgetSyncStateEntity?>(null)
        internal set

    var budgetDisplaySettings by mutableStateOf<BudgetDisplaySettings?>(null)
        internal set

    var budgetDisplaySettingsBudgetId by mutableStateOf<String?>(null)
        internal set

    var transactionRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        internal set

    var uncategorizedTransactionRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        internal set

    var activitySearchRecords by mutableStateOf<List<SearchableBudgetTransaction>>(emptyList())
        internal set

    var activitySearchRecordsBudgetId by mutableStateOf("")
        internal set

    override var payeeRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        internal set

    override fun replacePayeeRecords(records: List<BudgetRecordEntity>) {
        payeeRecords = records
    }

    override var categoryRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        internal set

    override var categoryGroupRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        internal set

    var budgetMonthIds by mutableStateOf<List<String>>(emptyList())
        internal set

    var selectedBudgetMonthRecord by mutableStateOf<BudgetRecordEntity?>(null)
        internal set

    var isLoadingSelectedBudgetMonth by mutableStateOf(true)
        internal set

    internal var selectedBudgetMonthObservationKey by mutableStateOf("")

    override var accountRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        internal set

    var accountGroupRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        internal set

    var scheduleRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        internal set

    override var ruleRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        internal set

    override val isAuthenticated: Boolean
        get() = apiClient.isAuthenticated

    override suspend fun synchronize(
        budgetId: String,
        encryptionPassword: String?,
        sendQueuedWrites: Boolean,
        reconcileUncertainOperationId: String?,
        retryQueuedBudgetMutations: Boolean,
        background: Boolean,
    ): BudgetSyncSummary? =
        synchronizationMutex.withLock {
            synchronizeSerialized(
                budgetId = budgetId,
                encryptionPassword = encryptionPassword,
                sendQueuedWrites = sendQueuedWrites,
                reconcileUncertainOperationId = reconcileUncertainOperationId,
                retryQueuedBudgetMutations = retryQueuedBudgetMutations,
                background = background,
            )
        }

    val canEditRules: Boolean get() = serverCapabilities?.supports("rule-edit") == true

    override suspend fun loadLocalRecords(budgetId: String) = loadCachedBudgetRecords(budgetId)

    override suspend fun isCurrentIdentityCacheOwner(): Boolean = ownsBudgetCache()

    override fun clearCachedBudgetPresentation() = clearCachedBudgetRecords()
}
