package app.tandemforactual.ui.core.session
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import app.tandemforactual.api.client.ActualLoginMethods
import app.tandemforactual.api.client.AuthenticatedDevice
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.DirectBudgetSyncRemote
import app.tandemforactual.api.client.ServerCapabilities
import app.tandemforactual.api.client.StatementImportItem
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.BudgetSyncStateEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.database.SearchableBudgetTransaction
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.closeDatabase
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.data.store.BudgetSyncCoordinator
import app.tandemforactual.data.store.BudgetSyncProtocolException
import app.tandemforactual.data.store.BudgetSyncRemoteException
import app.tandemforactual.data.store.BudgetSyncSummary
import app.tandemforactual.data.store.LocalBudgetMutationOutboxItem
import app.tandemforactual.data.store.LocalBudgetMutationQueueSnapshot
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalOperationConflictException
import app.tandemforactual.data.store.LocalRecordProjection
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import app.tandemforactual.ui.core.format.TransactionDateValidationStatus
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.format.transactionAmountInputError
import app.tandemforactual.ui.core.format.transactionDateValidationMessage
import app.tandemforactual.ui.core.format.validateTransactionDate
import app.tandemforactual.ui.core.session.banking.BankingController
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits
import app.tandemforactual.ui.core.session.budget.hasPendingTransactionDependency
import app.tandemforactual.ui.core.session.budget.payeeRecordsWithPendingCreates
import app.tandemforactual.ui.core.session.budget.pendingBudgetMutationCount
import app.tandemforactual.ui.core.session.budget.restoreBudgetReorderQueue
import app.tandemforactual.ui.core.session.budget.retryQueuedBudgetReorders
import app.tandemforactual.ui.core.session.feedback.BudgetActionRunner
import app.tandemforactual.ui.core.session.insights.InsightsController
import app.tandemforactual.ui.core.session.planning.PlanningController
import app.tandemforactual.ui.core.session.schedules.ScheduleActionController
import app.tandemforactual.ui.core.state.AccountActivitySnapshot
import app.tandemforactual.ui.core.state.AppRouteEntry
import app.tandemforactual.ui.core.state.PlanningSyncStatus
import app.tandemforactual.ui.core.state.UNSYNCED_TRANSACTION_WRITE_STATUSES
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.inputs.MAX_TRANSACTION_AMOUNT_MINOR_UNITS
import app.tandemforactual.ui.feature.reports.ReportsViewModels
import app.tandemforactual.ui.feature.rules.signedAmount
import app.tandemforactual.ui.feature.transactions.ConflictResolutionSync
import app.tandemforactual.ui.feature.transactions.RepositoryConflictResolutionStore
import app.tandemforactual.ui.feature.transactions.TransactionConflictResolution
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid

internal data class SyncPresentationState(
    val isSyncing: Boolean = false,
    val error: String? = null,
    val errorCode: String? = null,
    val errorStatus: Int? = null,
) {
    fun begin(): SyncPresentationState = copy(isSyncing = true)

    fun succeeded(): SyncPresentationState = SyncPresentationState()

    fun failed(
        message: String,
        code: String? = null,
        status: Int? = null,
    ): SyncPresentationState = SyncPresentationState(error = message, errorCode = code, errorStatus = status)

    fun stopped(): SyncPresentationState = copy(isSyncing = false)
}

internal data class TransactionEditDetails(
    val accountId: String,
    val payeeId: String?,
    val categoryId: String?,
    val date: String,
)

internal fun pendingTransactionEditMessage(): String = "This transaction is waiting to sync. Try again after it syncs."

internal fun blockedTransactionEditMessage(status: String): String =
    when (status) {
        "conflict" -> "Review this transaction in Pending Changes before editing it."
        "uncertain" -> "This transaction’s status is unknown. Check Pending Changes before editing it again."
        else -> "This transaction is syncing. Wait for it to finish before editing."
    }

internal fun queuedTransactionEditToDiscardForNoOp(
    pendingWrites: List<OutboxOperationEntity>,
    editingLocalCreate: Boolean,
): String? =
    if (editingLocalCreate) {
        null
    } else {
        pendingWrites
            .firstOrNull {
                it.action == LocalWriteAction.UPDATE.name &&
                    it.status == "queued" &&
                    it.serverOperationId == null
            }?.operationId
    }

internal fun queuedTransactionCreateToEdit(pendingWrites: List<OutboxOperationEntity>): OutboxOperationEntity? =
    pendingWrites.firstOrNull {
        it.action == LocalWriteAction.CREATE.name &&
            it.status == "queued" &&
            it.serverOperationId == null
    }

internal data class ConflictResolutionFeedback(
    val operationId: String,
    val message: String,
)

internal data class UndoNotice(
    val id: Long,
    val message: String,
    val budgetId: String,
    val transactionIds: Set<String>,
)

internal data class BudgetActionFeedback(
    val text: String,
    val kind: InlineMessageKind,
    val ownerRoute: AppRouteEntry,
    val actionLabel: String?,
)

internal fun pendingDeviceChangeNotice(): String =
    "Any changes waiting to sync stay on this device. Sign in to the same server and account to sync them."

@Composable
internal fun budgetActionFeedbackMessage(mobileSession: TandemMobileSession) {
    val message = mobileSession.budgetActionMessage ?: return
    val actionLabel = mobileSession.feedback.budgetActionMessageActionLabel
    InlineMessage(
        message,
        mobileSession.feedback.budgetActionMessageKind,
        actionLabel = actionLabel,
        onAction = if (actionLabel == null) null else ({ mobileSession.feedback.retryBudgetAction() }),
    )
}

internal class BudgetActionFeedbackState {
    var currentRouteEntry by mutableStateOf<AppRouteEntry?>(null)
        private set

    var feedback by mutableStateOf<BudgetActionFeedback?>(null)
        private set

    fun changeRoute(routeEntry: AppRouteEntry) {
        if (currentRouteEntry == routeEntry) return
        currentRouteEntry = routeEntry
        feedback = null
    }

    fun show(
        text: String,
        kind: InlineMessageKind,
        ownerRoute: AppRouteEntry?,
        actionLabel: String? = null,
    ) {
        if (ownerRoute != null) feedback = BudgetActionFeedback(text, kind, ownerRoute, actionLabel)
    }

    fun clear() {
        feedback = null
    }

    fun clear(route: AppRouteEntry?) {
        if (feedback?.ownerRoute == route) feedback = null
    }

    fun messageFor(routeEntry: AppRouteEntry): BudgetActionFeedback? = feedback?.takeIf { it.ownerRoute == routeEntry }
}

internal fun buildTransactionEditPatch(
    base: JsonObject,
    signedAmount: Long,
    fallbackCurrentAmount: Long?,
    notes: String,
    details: TransactionEditDetails?,
): JsonObject =
    buildJsonObject {
        val currentAmount = base.longValue("amount") ?: fallbackCurrentAmount
        val currentNotes = base.stringValue("notes") ?: ""
        if (signedAmount != currentAmount) put("amount", JsonPrimitive(signedAmount))
        if (notes != currentNotes) put("notes", JsonPrimitive(notes))
        details?.let { requested ->
            if (requested.accountId != base.stringValue("account")) put("account", JsonPrimitive(requested.accountId))
            if (requested.payeeId != base.stringValue("payee")) {
                put("payee", requested.payeeId?.let(::JsonPrimitive) ?: JsonNull)
            }
            if (requested.categoryId != base.stringValue("category")) {
                put("category", requested.categoryId?.let(::JsonPrimitive) ?: JsonNull)
            }
            if (requested.date != base.stringValue("date")) put("date", JsonPrimitive(requested.date))
        }
    }

internal interface BudgetMutationQueuePersistence {
    suspend fun read(): LocalBudgetMutationQueueSnapshot

    suspend fun replace(
        items: List<LocalBudgetMutationOutboxItem>,
        metadataJson: String,
        supersededOperationIds: Set<String> = emptySet(),
    )
}

internal class LocalBudgetRepositoryMutationQueuePersistence(
    private val repository: LocalBudgetRepository,
) : BudgetMutationQueuePersistence {
    override suspend fun read(): LocalBudgetMutationQueueSnapshot = repository.readBudgetMutationQueue()

    override suspend fun replace(
        items: List<LocalBudgetMutationOutboxItem>,
        metadataJson: String,
        supersededOperationIds: Set<String>,
    ) = repository.replaceBudgetMutationQueue(items, metadataJson, supersededOperationIds)
}

/** One budget's row in the Sync Center. */
internal data class BudgetSyncOverview(
    val budgetId: String,
    val name: String,
    val open: Boolean,
    val locked: Boolean,
    val lastSyncedAtEpochMs: Long?,
    val failed: Boolean,
    val waiting: Int,
    val needsReview: Int,
)

/** In-memory session state. Device credentials use a platform store; budget passwords are never persisted. */
internal class TandemMobileSession(
    override val apiClient: TandemApiClient,
    database: TandemDatabase?,
    override val nowEpochMillis: () -> Long,
    private val credentialStore: DeviceCredentialStore? = null,
    budgetMutationQueuePersistenceOverride: BudgetMutationQueuePersistence? = null,
    private val currentDateIso: () -> String = ::platformTodayIsoDate,
    private val openIdLoginProofState: OpenIdLoginProofState = OpenIdLoginProofState(nowEpochMillis),
    private val revokeCurrentDeviceForSignOut: suspend () -> Unit = { apiClient.revokeCurrentDevice() },
) : SessionHost {
    /** Bank providers, links and bank sync. */
    val banking =
        BankingController(apiClient, nowEpochMillis) { budgetId, password, message, action ->
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
    val budgetQueue: BudgetEditQueue = BudgetEditQueue(this) { budgetEdits }

    /** Budget changes: accounts, categories, payees, rules, templates and moves. */
    val budgetEdits: BudgetEdits = BudgetEdits(this, budgetQueue)

    private val mainStorage = database?.let { BudgetStorage(it) }
    private var budgetStorage by mutableStateOf<BudgetStorage?>(null)
    private val storage: BudgetStorage? get() = budgetStorage ?: mainStorage
    override val repository: LocalBudgetRepository? get() = storage?.repository

    /** Goals, debts, retirement, reimbursements and the other planning records Actual doesn't hold. */
    val planning = PlanningController(apiClient, { storage?.database }, nowEpochMillis)

    override val budgetMutationQueuePersistence: BudgetMutationQueuePersistence?
        get() = queuePersistenceOverride ?: storage?.queuePersistence
    private val queuePersistenceOverride = budgetMutationQueuePersistenceOverride
    override val preferencesStore: AppPreferencesStore? = database?.let(::AppPreferencesStore)
    override val sessionScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal val reportViewModels: ReportsViewModels? get() = storage?.reports
    private val syncCoordinator: BudgetSyncCoordinator? get() = storage?.coordinator

    var databaseGeneration by mutableStateOf(0)
        private set

    internal val boundEncryptedBudgetId: String? get() = budgetStorage?.budgetId

    internal var encryptedStorageRequired by mutableStateOf<Set<String>>(emptySet())

    var isUsingLocalServer by mutableStateOf(false)
        private set

    var serverCapabilities by mutableStateOf<ServerCapabilities?>(null)
        private set

    private var capabilitiesCheckedAtEpochMs = 0L

    suspend fun refreshServerCapabilities(force: Boolean = false) {
        if (!force && nowEpochMillis() - capabilitiesCheckedAtEpochMs < CAPABILITIES_REFRESH_MILLIS) return
        val latest = apiClient.readServerCapabilities() ?: return
        capabilitiesCheckedAtEpochMs = nowEpochMillis()
        if (latest != serverCapabilities) {
            serverCapabilities = latest
            budgetQueue.budgetReorderRetrySignal += 1
        }
    }

    override fun missingServerFeature(kind: BudgetReorderKind): String? = missingServerFeature(kind, serverCapabilities)

    var localServerError by mutableStateOf<String?>(null)
        private set

    suspend fun configureLocalServer(url: String?): Boolean {
        localServerError = null
        try {
            apiClient.setLocalServerUrl(url)
        } catch (_: TandemApiException) {
            localServerError = "Enter a local address like http://192.168.1.10:19753."
            isUsingLocalServer = false
            return false
        }
        isUsingLocalServer = !url.isNullOrBlank() && apiClient.refreshLocalServer()
        return isUsingLocalServer
    }

    internal fun encryptedStorageMissing(budgetId: String): Boolean =
        budgetId in encryptedStorageRequired && budgetStorage?.budgetId != budgetId

    private inner class BudgetStorage(
        val database: TandemDatabase,
        val budgetId: String? = null,
    ) {
        val repository = LocalBudgetRepository(database, nowEpochMillis)
        val queuePersistence = LocalBudgetRepositoryMutationQueuePersistence(repository)
        val reports = ReportsViewModels(repository)
        val coordinator =
            BudgetSyncCoordinator(
                repository,
                DirectBudgetSyncRemote(apiClient),
            )
    }

    internal suspend fun bindEncryptedBudgetDatabase(
        budgetId: String,
        database: TandemDatabase?,
    ) {
        val previous =
            synchronizationMutex.withLock {
                val replaced = budgetStorage
                budgetStorage = database?.let { BudgetStorage(it, budgetId) }
                budgetQueue.budgetQueueLoaded = false
                budgetQueue.budgetReorderQueueState = BudgetReorderQueueState()
                databaseGeneration += 1
                replaced
            }
        if (previous != null && previous.budgetId != null) {
            sessionScope.launch {
                delay(ENCRYPTED_DATABASE_CLOSE_DELAY_MILLIS)
                runCatching { previous.database.closeDatabase() }
            }
        }
        loadLocalRecords(budgetId)
    }

    internal suspend fun unbindEncryptedBudgetDatabase() {
        val budgetId = budgetStorage?.budgetId ?: return
        scheduleActionEncryptionPasswords.remove(budgetId)
        bindEncryptedBudgetDatabase(budgetId, null)
    }

    internal fun synchronizeInBackground(
        budgetId: String,
        encryptionPassword: String,
    ) {
        sessionScope.launch { synchronize(budgetId = budgetId, encryptionPassword = encryptionPassword) }
    }

    internal fun forgetEncryptionPasswords() {
        scheduleActionEncryptionPasswords.clear()
    }

    internal suspend fun removeBudgetFromDevice(
        budgetId: String,
        vault: EncryptedBudgetVault?,
    ): String? {
        val unsynced =
            budgetQueue.pendingBudgetMutationCount(budgetId) > 0 ||
                repository?.readPendingWrites(budgetId).orEmpty().any { it.status in UNSYNCED_TRANSACTION_WRITE_STATUSES }
        if (unsynced) return "Sync or resolve this budget’s changes before removing it from this device."
        if (boundEncryptedBudgetId == budgetId) unbindEncryptedBudgetDatabase()
        mainStorage?.repository?.removeBudget(budgetId)
        apiClient.removeBudget(budgetId)
        vault?.delete(budgetId)
        cachedBudgets = cachedBudgets.filterNot { it.budgetId == budgetId }
        loadLocalRecords(budgetId)
        return null
    }

    /**
     * Recovers from a reset in Actual: the device's copy and any edits it could not send are kept as a backup, and the
     * budget is downloaded fresh. Returns how many unsent edits the backup holds.
     */
    internal suspend fun downloadBudgetAgain(
        budgetId: String,
        vault: EncryptedBudgetVault?,
    ): Int {
        val unsent =
            budgetQueue.pendingBudgetMutationCount(budgetId) +
                repository?.readPendingWrites(budgetId).orEmpty().count { it.status in UNSYNCED_TRANSACTION_WRITE_STATUSES }
        if (boundEncryptedBudgetId == budgetId) unbindEncryptedBudgetDatabase()
        apiClient.retireBudget(budgetId)
        mainStorage?.repository?.removeBudget(budgetId)
        vault?.delete(budgetId)
        cachedBudgets = cachedBudgets.filterNot { it.budgetId == budgetId }
        loadLocalRecords(budgetId)
        loadBudgets()
        return unsent
    }

    internal suspend fun mainBudgetWrites(budgetId: String): List<OutboxOperationEntity> =
        mainStorage?.repository?.budgetWrites(budgetId).orEmpty()

    internal suspend fun removeBudgetFromMainStorage(budgetId: String) {
        mainStorage?.repository?.removeBudget(budgetId)
    }

    override var authenticatedDevice by mutableStateOf<AuthenticatedDevice?>(null)
        private set

    var budgets by mutableStateOf<List<BudgetCatalogItem>>(emptyList())
        private set

    var cachedBudgets by mutableStateOf<List<BudgetCatalogItem>>(emptyList())
        private set

    var budgetCatalogLoaded by mutableStateOf(false)
        private set

    var budgetCatalogVersion by mutableStateOf(0)
        private set

    override var isServerReachable by mutableStateOf(false)
        private set

    override fun markServerReachable(reachable: Boolean) {
        isServerReachable = reachable
    }

    var hasCachedSnapshot by mutableStateOf(false)
        private set

    var cachedSnapshotBudgetId by mutableStateOf("")
        private set

    var isSigningIn by mutableStateOf(false)
        private set

    var signInError by mutableStateOf<String?>(null)
        private set

    var actualLoginMethods by mutableStateOf<ActualLoginMethods?>(null)
        private set

    var isLoadingLoginMethods by mutableStateOf(false)
        private set

    var credentialStorageError by mutableStateOf<String?>(null)
        private set

    var deviceRevocationError by mutableStateOf<String?>(null)
        private set

    var signOutError by mutableStateOf<String?>(null)
        private set

    var signOutNotice by mutableStateOf<String?>(null)
        private set

    override var isSigningOut by mutableStateOf(false)
        private set

    var isRevokingDevice by mutableStateOf(false)
        private set

    var isCredentialStored by mutableStateOf(false)
        private set

    var isRestoringStoredSession by mutableStateOf(false)
        private set

    var isLoadingBudgets by mutableStateOf(false)
        private set

    var budgetError by mutableStateOf<String?>(null)
        private set

    private var syncPresentation by mutableStateOf(SyncPresentationState())
    private val synchronizationMutex = Mutex()

    override val isSyncing: Boolean get() = syncPresentation.isSyncing

    var syncComplete by mutableStateOf(false)
        private set

    override val syncError: String? get() = syncPresentation.error

    suspend fun recentPayeeIds(budgetId: String): List<String> =
        runCatching { repository?.recentPayeeIds(budgetId, RECENT_PAYEE_COUNT) }.getOrNull().orEmpty()

    val syncRecovery: SyncRecovery get() = syncRecovery(syncPresentation.errorCode, syncPresentation.errorStatus)

    suspend fun budgetMonthBody(
        budgetId: String,
        monthId: String,
    ): String? = repository?.findRecord(budgetId, "budget-month", monthId)?.let { it.localJson ?: it.remoteJson }

    var budgetPasswordRejection by mutableStateOf<String?>(null)
        private set

    var syncedTransactionCount by mutableStateOf(0)
    var cachedTransactionCount by mutableStateOf(0)
        private set

    override var pendingWrites by mutableStateOf<List<OutboxOperationEntity>>(emptyList())
        private set

    override fun replacePendingWrites(writes: List<OutboxOperationEntity>) {
        pendingWrites = writes
    }

    var conflictRecordsByRecordId by mutableStateOf<Map<String, BudgetRecordEntity>>(emptyMap())
        private set

    var checkedWriteOperation by mutableStateOf<OutboxOperationEntity?>(null)
        private set

    var checkedWriteOperationId by mutableStateOf<String?>(null)
        private set

    var uncertainWriteCheckError by mutableStateOf<String?>(null)
        private set

    var uncertainWriteCheckErrorOperationId by mutableStateOf<String?>(null)
        private set

    override var isSavingTransaction by mutableStateOf(false)
        private set

    override fun markSavingTransaction(saving: Boolean) {
        isSavingTransaction = saving
    }

    override var transactionWriteMessage by mutableStateOf<String?>(null)
        private set

    override fun reportTransactionWriteMessage(message: String?) {
        transactionWriteMessage = message
    }

    var conflictResolutionFeedback by mutableStateOf<ConflictResolutionFeedback?>(null)
        private set

    var serverConfirmedConflictOperationId by mutableStateOf<String?>(null)
        private set

    override val scheduleActionEncryptionPasswords: MutableMap<String, String> = mutableMapOf()

    override suspend fun activeBudgetCacheOwner(): String? {
        val device = authenticatedDevice ?: return null
        val serverUrl = apiClient.currentServerUrl ?: return null
        return budgetCacheOwner(serverUrl, device.principalId)
    }

    override suspend fun budgetCacheOwnerForLocalEdit(): String? {
        val activeOwner = activeBudgetCacheOwner()
        if (activeOwner != null) return activeOwner
        return try {
            preferencesStore?.read()?.get(BUDGET_CACHE_OWNER_KEY)
        } catch (_: Exception) {
            null
        }
    }

    private var readOnlyMode = false

    fun setReadOnlyMode(enabled: Boolean) {
        readOnlyMode = enabled
    }

    fun clearTransactionWriteMessage() {
        transactionWriteMessage = null
        conflictResolutionFeedback = null
        serverConfirmedConflictOperationId = null
    }

    fun clearConflictResolutionFeedback() {
        conflictResolutionFeedback = null
    }

    private fun reportConflictResolution(
        operationId: String,
        message: String,
    ) {
        transactionWriteMessage = message
        conflictResolutionFeedback = ConflictResolutionFeedback(operationId, message)
    }

    override fun refuseWriteInReadOnlyMode(): Boolean {
        if (!readOnlyMode) return false
        transactionWriteMessage = "Read-only mode is on. Turn it off before changing this data."
        return true
    }

    var lastSyncSummary by mutableStateOf<BudgetSyncSummary?>(null)
        private set

    var localSyncState by mutableStateOf<BudgetSyncStateEntity?>(null)
        private set

    /** Whether the observed records all belong to this budget, so its screens can show without filling in piece by piece. */
    fun hasLoadedBudget(budgetId: String): Boolean =
        localSyncState?.budgetId == budgetId &&
            listOf(accountRecords, categoryRecords, transactionRecords, scheduleRecords).all { records ->
                records.all {
                    it.budgetId ==
                        budgetId
                }
            }

    var budgetDisplaySettings by mutableStateOf<BudgetDisplaySettings?>(null)
        private set

    var budgetDisplaySettingsBudgetId by mutableStateOf<String?>(null)
        private set

    var transactionRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

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

    var activitySearchRecords by mutableStateOf<List<SearchableBudgetTransaction>>(emptyList())
        private set

    var activitySearchRecordsBudgetId by mutableStateOf("")
        private set

    override var payeeRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    override fun replacePayeeRecords(records: List<BudgetRecordEntity>) {
        payeeRecords = records
    }

    override var categoryRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    override var categoryGroupRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var budgetMonthIds by mutableStateOf<List<String>>(emptyList())
        private set

    var selectedBudgetMonthRecord by mutableStateOf<BudgetRecordEntity?>(null)
        private set

    var isLoadingSelectedBudgetMonth by mutableStateOf(true)
        private set

    private var selectedBudgetMonthObservationKey by mutableStateOf("")

    var accountRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var accountGroupRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    private val accountActivitySnapshots = linkedMapOf<String, AccountActivitySnapshot>()

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

    var scheduleRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    override var ruleRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    override val isAuthenticated: Boolean
        get() = apiClient.isAuthenticated

    suspend fun restoreStoredSession(): String? {
        val store = credentialStore ?: return null
        if (isRestoringStoredSession) return null
        isRestoringStoredSession = true
        try {
            credentialStorageError = null
            val storedSession =
                try {
                    withContext(Dispatchers.Default) { store.read() }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    credentialStorageError = "Couldn’t access this device’s saved sign-in. Try again or sign in again."
                    return null
                } ?: return null

            val restoredDevice =
                try {
                    apiClient.restoreStoredSession(storedSession)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    apiClient.forgetSession()
                    authenticatedDevice = null
                    budgets = emptyList()
                    credentialStorageError = "This device’s saved access no longer works. Sign in again."
                    clearStoredCredential()
                    return null
                }
            if (!acceptAuthenticatedIdentity(restoredDevice, previousSession = null, restoringSavedSession = true)) {
                credentialStorageError = signInError
                return null
            }
            isCredentialStored = true
            loadCachedBudgets()
            return apiClient.currentServerUrl.takeIf { apiClient.isAuthenticated }
        } finally {
            isRestoringStoredSession = false
        }
    }

    suspend fun forgetDevice() {
        if (authenticatedDevice == null || isRevokingDevice) return
        deviceRevocationError = null
        isRevokingDevice = true
        try {
            try {
                apiClient.revokeCurrentDevice()
            } catch (error: CancellationException) {
                throw error
            } catch (error: TandemApiException) {
                if (error.httpStatus != 401 && error.httpStatus != 404) {
                    deviceRevocationError = "Check your connection and try again."
                    return
                }
            } catch (_: Exception) {
                deviceRevocationError = "Check your connection and try again."
                return
            }
            clearDeviceSession()
        } finally {
            isRevokingDevice = false
        }
    }

    suspend fun forgetDeviceLocally() {
        if (authenticatedDevice == null || isRevokingDevice) return
        deviceRevocationError = null
        clearDeviceSession()
    }

    suspend fun signOutAndClearLocalData(): Boolean {
        if (isSigningOut) return false
        isSigningOut = true
        signOutError = null
        signOutNotice = null
        var cacheTablesCleared = false
        try {
            if (isSyncing || isSavingTransaction || isPerformingBudgetAction || isLoadingBudgets) {
                signOutError = "Wait for the current sync or change to finish before signing out."
                return false
            }
            if (!budgetQueue.restoreBudgetReorderQueue()) {
                signOutError = "Couldn’t verify pending changes. Try again before signing out."
                return false
            }
            if (budgetQueue.budgetReorderQueue.moves.isNotEmpty()) {
                signOutError = "Sync or resolve pending changes before signing out."
                return false
            }
            if (repository?.hasUnresolvedWrites() == true) {
                signOutError = "Sync or resolve pending changes before signing out."
                return false
            }
            cacheTablesCleared = repository == null
            val store = credentialStore
            val storedCredential =
                try {
                    store?.let { withContext(Dispatchers.Default) { it.read() } }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    signOutError = "Couldn’t check this device’s saved sign-in. Try again."
                    return false
                }
            var deviceRevoked = false
            if (apiClient.isAuthenticated) {
                try {
                    revokeCurrentDeviceForSignOut()
                    deviceRevoked = true
                } catch (error: CancellationException) {
                    throw error
                } catch (error: TandemApiException) {
                    if (error.httpStatus == 401 || error.httpStatus == 404) {
                        deviceRevoked = true
                    } else {
                        signOutNotice = signOutRevocationNotice(error.httpStatus)
                    }
                } catch (_: Exception) {
                    signOutNotice = signOutRevocationNotice(httpStatus = null)
                }
            }

            suspend fun restoreSavedCredential(): Boolean {
                if (store == null || storedCredential == null) {
                    isCredentialStored = false
                    return true
                }
                return try {
                    withContext(NonCancellable + Dispatchers.Default) {
                        store.write(storedCredential)
                    }
                    isCredentialStored = true
                    credentialStorageError = null
                    true
                } catch (_: Exception) {
                    isCredentialStored = false
                    credentialStorageError = "Couldn’t restore this device’s saved sign-in."
                    false
                }
            }

            try {
                if (store != null) withContext(Dispatchers.Default) { store.clear() }
                isCredentialStored = false
                credentialStorageError = null
            } catch (error: CancellationException) {
                withContext(NonCancellable) { restoreSavedCredential() }
                throw error
            } catch (_: Exception) {
                val restored = restoreSavedCredential()
                signOutError =
                    if (storedCredential != null && !restored) {
                        "Couldn’t remove or restore this device’s saved sign-in. Your cached budgets are still here."
                    } else {
                        "Couldn’t remove this device’s saved access. Try again."
                    }
                return false
            }

            suspend fun reportLocalPurgeFailure() {
                val restored = restoreSavedCredential()
                signOutError =
                    when {
                        cacheTablesCleared && storedCredential != null && !restored ->
                            "Cached budgets were cleared, but this device’s saved sign-in couldn’t be restored. Sign in again to continue."
                        cacheTablesCleared ->
                            "Cached budgets were cleared, but sign-out couldn’t finish. Try again."
                        storedCredential != null && !restored ->
                            "Couldn’t clear this device’s cached budgets or restore its saved sign-in. Your cached budgets are still here; sign in again to continue."
                        deviceRevoked ->
                            "Couldn’t clear this device’s cached budgets. They’re still here; the server sign-in was revoked, so sign in again to sync."
                        storedCredential != null ->
                            "Couldn’t clear this device’s cached budgets. They’re still here and saved sign-in was restored; try again."
                        else ->
                            "Couldn’t clear this device’s cached budgets. They’re still here; try again."
                    }
            }

            try {
                budgetQueue.budgetQueuePersistenceMutex.withLock {
                    repository?.clearAllLocalData()
                    cacheTablesCleared = true
                    preferencesStore?.putValue(AppPreferencesStore.PENDING_BUDGET_REORDERS_KEY, "")
                    preferencesStore?.putValue(BUDGET_CACHE_OWNER_KEY, "")
                    budgetQueue.budgetReorderQueueState = BudgetReorderQueueState()
                    budgetQueue.budgetQueueLoaded = true
                }
            } catch (error: CancellationException) {
                withContext(NonCancellable) { reportLocalPurgeFailure() }
                throw error
            } catch (_: Exception) {
                reportLocalPurgeFailure()
                return false
            }
            openIdLoginProofState.clear()
            apiClient.forgetSession()
            authenticatedDevice = null
            budgets = emptyList()
            cachedBudgets = emptyList()
            budgetCatalogLoaded = false
            budgetCatalogVersion += 1
            isServerReachable = false
            hasCachedSnapshot = false
            cachedSnapshotBudgetId = ""
            isCredentialStored = false
            credentialStorageError = null
            deviceRevocationError = null
            budgetError = null
            isLoadingBudgets = false
            localSyncState = null
            budgetDisplaySettings = null
            budgetDisplaySettingsBudgetId = null
            transactionRecords = emptyList()
            activityTransactionRecords.clear()
            activityTransactionRecordsVersion += 1
            activityTransactionPageBudgetId = ""
            activityTransactionPageError = null
            hasMoreActivityTransactions = false
            activitySearchRecords = emptyList()
            activitySearchRecordsBudgetId = ""
            payeeRecords = emptyList()
            categoryRecords = emptyList()
            categoryGroupRecords = emptyList()
            budgetMonthIds = emptyList()
            selectedBudgetMonthRecord = null
            isLoadingSelectedBudgetMonth = false
            selectedBudgetMonthObservationKey = ""
            accountRecords = emptyList()
            accountGroupRecords = emptyList()
            scheduleRecords = emptyList()
            ruleRecords = emptyList()
            pendingWrites = emptyList()
            conflictRecordsByRecordId = emptyMap()
            cachedTransactionCount = 0
            syncedTransactionCount = 0
            checkedWriteOperation = null
            checkedWriteOperationId = null
            uncertainWriteCheckError = null
            uncertainWriteCheckErrorOperationId = null
            transactionWriteMessage = null
            conflictResolutionFeedback = null
            syncComplete = false
            lastSyncSummary = null
            syncPresentation = SyncPresentationState()
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            signOutError =
                if (cacheTablesCleared) {
                    "Cached budgets were cleared, but sign-out couldn’t finish. Try again."
                } else {
                    "Couldn’t clear this device’s cached budgets. Try again."
                }
            return false
        } finally {
            isSigningOut = false
        }
    }

    suspend fun loadActualLoginMethods(serverUrl: String) {
        actualLoginMethods = null
        isLoadingLoginMethods = true
        signInError = null
        try {
            actualLoginMethods = apiClient.readActualLoginMethods(serverUrl)
            isServerReachable = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            isServerReachable = error.indicatesTandemReachable()
            signInError =
                error.userFacingMessage(
                    "Couldn’t check sign-in options. Check the address and try again.",
                    ApiFailureContext.SIGN_IN,
                )
        } catch (_: Exception) {
            isServerReachable = false
            signInError =
                apiFailureMessage(
                    "network-error",
                    null,
                    ApiFailureContext.SIGN_IN,
                    "Couldn’t check sign-in options. Check the address and try again.",
                )
        } finally {
            isLoadingLoginMethods = false
        }
    }

    fun clearActualLoginMethodState() {
        actualLoginMethods = null
        signInError = null
        isLoadingLoginMethods = false
        credentialStorageError = null
    }

    suspend fun signInWithPassword(
        serverUrl: String,
        password: String,
        deviceName: String,
    ): Boolean {
        signInError = null
        signOutNotice = null
        deviceRevocationError = null
        isSigningIn = true
        cachedBudgets = emptyList()
        openIdLoginProofState.clear()
        val previousSession = apiClient.exportStoredSession()
        return try {
            val signedInDevice = apiClient.signInWithPassword(serverUrl, password, deviceName)
            if (!acceptAuthenticatedIdentity(signedInDevice, previousSession)) return false
            isServerReachable = true
            persistCurrentSession()
            loadBudgets()
            budgetQueue.retryQueuedBudgetReorders()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            isServerReachable = error.indicatesTandemReachable()
            signInError =
                error.userFacingMessage(
                    "Couldn’t sign in. Check the address and try again.",
                    ApiFailureContext.SIGN_IN,
                )
            restorePreviousSessionAfterSignIn(previousSession)
            false
        } catch (_: Exception) {
            isServerReachable = false
            signInError =
                apiFailureMessage(
                    "network-error",
                    null,
                    ApiFailureContext.SIGN_IN,
                    "Couldn’t sign in. Check the address and try again.",
                )
            restorePreviousSessionAfterSignIn(previousSession)
            false
        } finally {
            isSigningIn = false
            if (apiClient.isAuthenticated) loadCachedBudgets()
        }
    }

    suspend fun startActualOpenIdSignIn(
        serverUrl: String,
        deviceName: String,
    ): String? {
        signInError = null
        signOutNotice = null
        isSigningIn = true
        openIdLoginProofState.clear()
        var redirectReady = false
        try {
            val requestProof = openIdLoginProofState.begin(serverUrl)
            scheduleOpenIdProofExpiry()
            val redirect =
                apiClient.startActualOpenIdSignIn(
                    serverUrl,
                    deviceName,
                    requestProof.codeChallenge,
                    requestProof.callbackState,
                )
            redirectReady = true
            return redirect
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            isServerReachable = error.indicatesTandemReachable()
            signInError = error.userFacingMessage("Tandem couldn’t start sign-in. Check the server address.", ApiFailureContext.SIGN_IN)
        } catch (_: Exception) {
            isServerReachable = false
            signInError =
                apiFailureMessage(
                    "network-error",
                    null,
                    ApiFailureContext.SIGN_IN,
                    "Tandem couldn’t start sign-in. Check the server address.",
                )
        } finally {
            isSigningIn = false
            if (!redirectReady) openIdLoginProofState.clear()
        }
        return null
    }

    fun reportSignInError(message: String) {
        signInError = message
    }

    fun cancelActualOpenIdSignIn() {
        openIdLoginProofState.clear()
    }

    fun scheduleOpenIdProofExpiry() {
        val remainingTtlMillis = openIdLoginProofState.remainingTtlMillis()
        if (remainingTtlMillis > 0) {
            sessionScope.launch {
                delay(remainingTtlMillis)
                openIdLoginProofState.expireIfNeeded()
            }
        }
    }

    suspend fun completeActualOpenIdSignIn(
        serverUrl: String,
        code: String,
        callbackState: String,
    ): Boolean {
        signInError = null
        deviceRevocationError = null
        isSigningIn = true
        val previousSession = apiClient.exportStoredSession()
        val codeVerifier = openIdLoginProofState.takeVerifierForCallback(serverUrl, callbackState)
        if (codeVerifier == null) {
            signInError = "This sign-in is no longer active. Start sign-in again."
            isSigningIn = false
            return false
        }
        cachedBudgets = emptyList()
        return try {
            val signedInDevice = apiClient.completeActualOpenIdSignIn(serverUrl, code, codeVerifier)
            openIdLoginProofState.clear()
            if (!acceptAuthenticatedIdentity(signedInDevice, previousSession)) return false
            isServerReachable = true
            persistCurrentSession()
            loadBudgets()
            budgetQueue.retryQueuedBudgetReorders()
            true
        } catch (error: CancellationException) {
            openIdLoginProofState.clear()
            throw error
        } catch (error: TandemApiException) {
            isServerReachable = error.indicatesTandemReachable()
            signInError =
                error.userFacingMessage(
                    "Tandem couldn’t finish sign-in. Check the server address.",
                    ApiFailureContext.SIGN_IN,
                )
            restorePreviousSessionAfterSignIn(previousSession)
            false
        } catch (_: Exception) {
            isServerReachable = false
            signInError =
                apiFailureMessage(
                    "network-error",
                    null,
                    ApiFailureContext.SIGN_IN,
                    "Tandem couldn’t finish sign-in. Check the server address.",
                )
            restorePreviousSessionAfterSignIn(previousSession)
            false
        } finally {
            isSigningIn = false
            openIdLoginProofState.clear()
            if (apiClient.isAuthenticated) loadCachedBudgets()
        }
    }

    suspend fun loadBudgets() {
        budgetError = null
        isLoadingBudgets = true
        try {
            budgets = apiClient.listBudgets()
            budgetCatalogLoaded = true
            budgetCatalogVersion += 1
            isServerReachable = true
            budgetQueue.retryQueuedBudgetReorders()
            schedules.retryQueuedScheduleActions()
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            isServerReachable = error.indicatesTandemReachable()
            budgetError = error.userFacingMessage("Tandem couldn’t load your budgets. Check the connection.")
            if (error.errorCode == "invalid_device_credential") {
                apiClient.forgetSession()
                authenticatedDevice = null
                budgets = emptyList()
                credentialStorageError = "Your saved sign-in is no longer active. Sign in again."
                clearStoredCredential()
            }
        } catch (error: Exception) {
            isServerReachable = false
            budgetError =
                apiFailureMessage(
                    "network-error",
                    null,
                    ApiFailureContext.SIGN_IN,
                    "Tandem couldn’t load your budgets. Check the server address.",
                )
        } finally {
            isLoadingBudgets = false
        }
    }

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

    private suspend fun synchronizeSerialized(
        budgetId: String,
        encryptionPassword: String?,
        sendQueuedWrites: Boolean,
        reconcileUncertainOperationId: String?,
        retryQueuedBudgetMutations: Boolean,
        background: Boolean,
    ): BudgetSyncSummary? {
        if (isSigningOut) return null
        if (encryptedStorageMissing(budgetId)) return null

        fun present(next: SyncPresentationState) {
            if (!background || (next.error == null && !next.isSyncing && syncPresentation.error != null)) {
                syncPresentation = next
            }
        }
        if (!encryptionPassword.isNullOrBlank()) scheduleActionEncryptionPasswords[budgetId] = encryptionPassword
        if (!isCurrentIdentityCacheOwner()) {
            present(
                syncPresentation.failed(
                    "These changes belong to another server or account. Sign in there before syncing them.",
                    IDENTITY_MISMATCH_ERROR_CODE,
                ),
            )
            return null
        }
        val budgetQueueRestored = budgetQueue.restoreBudgetReorderQueue()
        if (budgetQueueRestored && retryQueuedBudgetMutations && !encryptionPassword.isNullOrBlank()) {
            budgetQueue.retryQueuedBudgetReorders(budgetId, encryptionPassword)
        }
        if (!background) {
            syncComplete = false
            syncedTransactionCount = 0
            lastSyncSummary = null
        }
        val coordinator = syncCoordinator
        val localRepository = repository
        if (coordinator == null || localRepository == null) {
            present(syncPresentation.failed("Tandem can’t save this budget on your device. Restart the app and try again."))
            return null
        }

        // Keep an earlier error visible while retrying; clear it only when the retry succeeds.
        present(syncPresentation.begin())
        try {
            val summary =
                apiClient.withExclusiveSession {
                    val sendWrites = sendQueuedWrites && !budgetQueue.hasPendingTransactionDependency(budgetId)
                    withContext(Dispatchers.Default) {
                        coordinator.synchronize(budgetId, encryptionPassword, sendWrites, reconcileUncertainOperationId)
                    }
                }
            isServerReachable = true
            refreshServerCapabilities()
            schedules.retryQueuedScheduleActions()
            lastSyncSummary = summary
            loadLocalRecords(budgetId)
            syncedTransactionCount =
                localRepository.countVisibleRecords(budgetId, "transaction").coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            syncComplete = true
            budgetPasswordRejection = null
            present(syncPresentation.succeeded())
            if (budgetQueueRestored) {
                budgetQueue.budgetReorderQueue = budgetQueue.budgetReorderQueue.clearCompletedVisibilityOverrides(budgetId)
                if (retryQueuedBudgetMutations) budgetQueue.retryQueuedBudgetReorders(budgetId, encryptionPassword)
            }
            planning.sync(budgetId)
            return summary
        } catch (error: CancellationException) {
            throw error
        } catch (error: BudgetSyncRemoteException) {
            isServerReachable = error.indicatesTandemReachable()
            budgetPasswordRejection = error.errorCode.takeIf { it in BUDGET_PASSWORD_ERROR_CODES }
            present(
                syncPresentation.failed(
                    apiFailureMessage(
                        errorCode = error.errorCode,
                        httpStatus = error.httpStatus,
                        context = ApiFailureContext.SYNC,
                        fallback = "Tandem couldn’t sync this budget. Try again shortly.",
                    ),
                    error.errorCode,
                    error.httpStatus,
                ),
            )
            return null
        } catch (error: TandemApiException) {
            isServerReachable = error.errorCode != "address_unreachable"
            budgetPasswordRejection = error.errorCode.takeIf { it in BUDGET_PASSWORD_ERROR_CODES }
            present(
                syncPresentation.failed(
                    apiFailureMessage(
                        error.errorCode,
                        error.httpStatus,
                        ApiFailureContext.SYNC,
                        "Tandem couldn’t sync this budget. Try again shortly.",
                    ),
                    error.errorCode,
                    error.httpStatus,
                ),
            )
            return null
        } catch (error: BudgetSyncProtocolException) {
            isServerReachable = true
            present(syncPresentation.failed("Tandem couldn’t read this budget. Try syncing again."))
            return null
        } catch (error: Exception) {
            isServerReachable = false
            if (apiClient.isUsingLocalServer) {
                apiClient.useExternalServerAfterLocalFailure()
                isUsingLocalServer = false
            }
            present(
                syncPresentation.failed(
                    apiFailureMessage(
                        errorCode = "network-error",
                        httpStatus = null,
                        context = ApiFailureContext.SYNC,
                        fallback = "Tandem couldn’t sync this budget. Your saved copy is still available.",
                    ),
                ),
            )
            return null
        } finally {
            if (syncPresentation.isSyncing) syncPresentation = syncPresentation.stopped()
        }
    }

    suspend fun confirmConflictWithServer(
        budgetId: String,
        operationId: String,
        encryptionPassword: String?,
    ): Boolean {
        serverConfirmedConflictOperationId = null
        val summary =
            synchronize(
                budgetId = budgetId,
                encryptionPassword = encryptionPassword,
                sendQueuedWrites = false,
                retryQueuedBudgetMutations = false,
            ) ?: return false
        val operation = repository?.readPendingWrites(budgetId)?.firstOrNull { it.operationId == operationId }
        if (operation?.status != "conflict") return false
        serverConfirmedConflictOperationId = operationId
        return true
    }

    val canEditRules: Boolean get() = serverCapabilities?.supports("rule-edit") == true

    suspend fun updateTransaction(
        budgetId: String,
        transactionId: String,
        amountText: String,
        notes: String,
        originalAmountMinor: Long,
        transactionType: String,
        displaySettings: BudgetDisplaySettings? = null,
        accountId: String? = null,
        payeeId: String? = null,
        categoryId: String? = null,
        date: String? = null,
        updateDetails: Boolean = false,
        confirmedFutureDate: String? = null,
    ): Boolean {
        transactionWriteMessage = null
        if (refuseWriteInReadOnlyMode()) return false
        if (!isCurrentIdentityCacheOwner()) {
            transactionWriteMessage = "Sign in to the server and account that own this budget before editing a transaction."
            return false
        }
        val enteredAmount = parseAmountMinorUnits(amountText, displaySettings)
        if (enteredAmount == null) {
            transactionWriteMessage = transactionAmountInputError(amountText, displaySettings)
            return false
        }
        if (enteredAmount < 0) {
            transactionWriteMessage = "Enter a positive amount."
            return false
        }
        if (enteredAmount > MAX_TRANSACTION_AMOUNT_MINOR_UNITS) {
            transactionWriteMessage = "Largest amount is ${formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS, displaySettings)}."
            return false
        }
        val dateValidation =
            if (updateDetails) {
                validateTransactionDate(date.orEmpty(), currentDateIso(), confirmedFutureDate)
            } else {
                null
            }
        if (dateValidation != null && dateValidation.status != TransactionDateValidationStatus.VALID) {
            transactionWriteMessage = transactionDateValidationMessage(dateValidation)
            return false
        }
        val requestedDate = dateValidation?.date
        val localRepository = repository
        if (localRepository == null) {
            transactionWriteMessage = "Budget data isn’t available on this device. Reopen the budget and try again."
            return false
        }
        val pendingForRecord =
            localRepository
                .readPendingWrites(budgetId)
                .filter { it.recordType == "transaction" && it.recordId == transactionId }
        val blockingWrite = pendingForRecord.firstOrNull { it.status in setOf("sending", "uncertain", "conflict") }
        if (blockingWrite != null) {
            transactionWriteMessage = blockedTransactionEditMessage(blockingWrite.status)
            return false
        }
        val current = localRepository.findRecord(budgetId, "transaction", transactionId)
        val queuedCreate = queuedTransactionCreateToEdit(pendingForRecord)
        val editingLocalCreate = queuedCreate != null
        val baseJson = if (editingLocalCreate) current?.localJson else current?.remoteJson
        val revision = if (editingLocalCreate) null else current?.remoteRevision
        if (current == null || current.remoteDeleted || baseJson == null || (!editingLocalCreate && revision.isNullOrBlank())) {
            transactionWriteMessage = "This transaction isn’t up to date. Sync the budget before editing."
            return false
        }
        val base = runCatching { Json.parseToJsonElement(baseJson).jsonObject }.getOrNull()
        if (base == null) {
            transactionWriteMessage = "Tandem couldn’t open this transaction. Sync the budget before editing."
            return false
        }
        val requestedAccountId = if (updateDetails) accountId?.takeIf(String::isNotBlank) else null
        if (updateDetails && requestedAccountId == null) {
            transactionWriteMessage = "Choose an account from this budget before saving."
            return false
        }
        if (requestedAccountId != null) {
            val account = accountRecords.firstOrNull { it.recordId == requestedAccountId }
            val accountBody = account?.recordBody()
            if (account == null || accountBody?.booleanValue("closed") == true) {
                transactionWriteMessage = "Choose an open account from this budget."
                return false
            }
            if (accountBody?.booleanValue("offbudget") == true || accountBody?.booleanValue("offBudget") == true) {
                transactionWriteMessage = "Choose an on-budget account for this transaction."
                return false
            }
        }
        if (updateDetails && payeeId != null && payeeRecords.none { it.recordId == payeeId }) {
            transactionWriteMessage = "Choose an existing payee or clear the payee field."
            return false
        }
        val transferDestinationId =
            payeeId
                ?.let { id -> payeeRecords.firstOrNull { it.recordId == id } }
                ?.recordBody()
                ?.stringValue("transfer_acct")
        if (updateDetails && transactionType == "Transfer" && transferDestinationId.isNullOrBlank()) {
            transactionWriteMessage = "Choose a destination account for this transfer."
            return false
        }
        if (updateDetails && transactionType == "Transfer" && transferDestinationId == requestedAccountId) {
            transactionWriteMessage = "Choose a different destination account."
            return false
        }
        if (updateDetails && transactionType != "Transfer" && !transferDestinationId.isNullOrBlank()) {
            transactionWriteMessage = "Choose Transfer as the transaction type for this payee."
            return false
        }
        val requestedCategoryId = categoryId.takeUnless { transactionType == "Transfer" }
        if (updateDetails && requestedCategoryId != null && categoryRecords.none { it.recordId == requestedCategoryId }) {
            transactionWriteMessage = "Choose a category from this budget before saving."
            return false
        }
        val signedAmount =
            when (transactionType) {
                "Income" -> enteredAmount
                "Transfer" -> if (originalAmountMinor >= 0L) enteredAmount else -enteredAmount
                else -> -enteredAmount
            }
        val patch =
            buildTransactionEditPatch(
                base = base,
                signedAmount = signedAmount,
                fallbackCurrentAmount = current.amountMinor,
                notes = notes,
                details =
                    if (updateDetails) {
                        TransactionEditDetails(
                            accountId = checkNotNull(requestedAccountId),
                            payeeId = payeeId,
                            categoryId = requestedCategoryId,
                            date = checkNotNull(requestedDate),
                        )
                    } else {
                        null
                    },
            )
        if (patch.isEmpty()) {
            val queuedEditId = queuedTransactionEditToDiscardForNoOp(pendingForRecord, editingLocalCreate)
            if (queuedEditId != null) {
                try {
                    localRepository.discardWrite(queuedEditId)
                    loadLocalRecords(budgetId)
                    transactionWriteMessage = "Your transaction matches Actual. Removed the waiting edit."
                    return true
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    transactionWriteMessage = "This transaction started syncing. Check Pending Changes before changing it."
                    loadLocalRecords(budgetId)
                    return false
                }
            }
            transactionWriteMessage = "There are no transaction changes to save."
            return false
        }

        isSavingTransaction = true
        try {
            val patchJson = JsonObject(patch)
            val optimistic = JsonObject(base + patch)
            val operationId = Uuid.random().toString()
            localRepository.enqueueWrite(
                LocalWriteRequest(
                    operationId = operationId,
                    idempotencyKey = operationId,
                    budgetId = budgetId,
                    recordType = "transaction",
                    recordId = transactionId,
                    action = if (editingLocalCreate) LocalWriteAction.CREATE else LocalWriteAction.UPDATE,
                    baseRevision = revision,
                    baseSnapshotJson = if (editingLocalCreate) null else baseJson,
                    patchJson = if (editingLocalCreate) optimistic.toString() else patchJson.toString(),
                    optimisticJson = optimistic.toString(),
                    createdAtEpochMs = nowEpochMillis(),
                    optimisticProjection = optimistic.toProjection(),
                ),
            )
            transactionWriteMessage = null
            loadLocalRecords(budgetId)
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (_: LocalOperationConflictException) {
            transactionWriteMessage = pendingTransactionEditMessage()
            loadLocalRecords(budgetId)
            return false
        } catch (error: Exception) {
            transactionWriteMessage =
                localStorageFailureMessage(
                    error,
                    "Tandem couldn’t save your edit. Check the budget and try again.",
                )
            loadLocalRecords(budgetId)
            return false
        } finally {
            isSavingTransaction = false
        }
    }

    var undoNotice by mutableStateOf<UndoNotice?>(null)
        private set
    private var undoNoticeCount = 0L

    /** Deletes right away and offers an undo while the deletions are still waiting to sync. */
    suspend fun deleteTransactionsWithUndo(
        budgetId: String,
        transactionIds: Set<String>,
    ): Boolean {
        val deleted = transactionIds.takeWhile { deleteTransaction(budgetId, it) }.toSet()
        if (deleted.isNotEmpty()) {
            val undoable = deleted.filterTo(mutableSetOf()) { queuedTransactionDelete(budgetId, it) != null }
            undoNoticeCount += 1
            undoNotice =
                UndoNotice(
                    id = undoNoticeCount,
                    message = if (deleted.size == 1) "Transaction deleted" else "${deleted.size} transactions deleted",
                    budgetId = budgetId,
                    transactionIds = undoable,
                )
        }
        return deleted.size == transactionIds.size
    }

    suspend fun undoTransactionDeletes(notice: UndoNotice) {
        val localRepository = repository ?: return
        var restoredAll = true
        for (transactionId in notice.transactionIds) {
            val write = queuedTransactionDelete(notice.budgetId, transactionId)
            if (write == null) restoredAll = false else localRepository.discardWrite(write.operationId)
        }
        loadLocalRecords(notice.budgetId)
        undoNoticeCount += 1
        undoNotice =
            if (restoredAll) {
                null
            } else {
                UndoNotice(undoNoticeCount, "Already synced, so it can’t be undone here", notice.budgetId, emptySet())
            }
    }

    fun dismissUndoNotice(id: Long) {
        if (undoNotice?.id == id) undoNotice = null
    }

    private suspend fun queuedTransactionDelete(
        budgetId: String,
        transactionId: String,
    ): OutboxOperationEntity? =
        repository
            ?.readPendingWrites(budgetId)
            ?.lastOrNull {
                it.recordType == "transaction" &&
                    it.recordId == transactionId &&
                    it.action == LocalWriteAction.DELETE.name &&
                    it.status == "queued"
            }

    suspend fun deleteTransaction(
        budgetId: String,
        transactionId: String,
    ): Boolean {
        transactionWriteMessage = null
        if (refuseWriteInReadOnlyMode()) return false
        if (!isCurrentIdentityCacheOwner()) {
            transactionWriteMessage = "Sign in to the server and account that own this budget before deleting a transaction."
            return false
        }
        val localRepository = repository
        if (localRepository == null) {
            transactionWriteMessage = "Budget data isn’t available on this device. Reopen the budget and try again."
            return false
        }
        val pendingForRecord =
            localRepository
                .readPendingWrites(budgetId)
                .filter {
                    it.recordType == "transaction" && it.recordId == transactionId && it.status in UNSYNCED_TRANSACTION_WRITE_STATUSES
                }.maxByOrNull { it.updatedAtEpochMs }
        if (pendingForRecord?.action == LocalWriteAction.CREATE.name && pendingForRecord.status == "queued") {
            isSavingTransaction = true
            try {
                localRepository.discardWrite(pendingForRecord.operationId)
                transactionWriteMessage = null
                loadLocalRecords(budgetId)
                return true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                transactionWriteMessage =
                    localStorageFailureMessage(
                        error,
                        "Tandem couldn’t remove this transaction. Open Pending Changes to check it.",
                    )
                loadLocalRecords(budgetId)
                return false
            } finally {
                isSavingTransaction = false
            }
        }
        if (pendingForRecord != null && pendingForRecord.status != "queued") {
            transactionWriteMessage = "Open Pending Changes to review this transaction before deleting it."
            return false
        }
        val current = localRepository.findRecord(budgetId, "transaction", transactionId)
        val baseJson = current?.remoteJson
        val revision = current?.remoteRevision
        if (current == null || current.remoteDeleted || baseJson == null || revision.isNullOrBlank()) {
            transactionWriteMessage = "This transaction isn’t up to date. Sync the budget before deleting it."
            return false
        }

        isSavingTransaction = true
        try {
            val operationId = Uuid.random().toString()
            localRepository.enqueueWrite(
                LocalWriteRequest(
                    operationId = operationId,
                    idempotencyKey = operationId,
                    budgetId = budgetId,
                    recordType = "transaction",
                    recordId = transactionId,
                    action = LocalWriteAction.DELETE,
                    baseRevision = revision,
                    baseSnapshotJson = baseJson,
                    patchJson = "{}",
                    optimisticJson = null,
                    createdAtEpochMs = nowEpochMillis(),
                    optimisticProjection = null,
                ),
            )
            transactionWriteMessage = null
            loadLocalRecords(budgetId)
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            transactionWriteMessage =
                localStorageFailureMessage(
                    error,
                    "Tandem couldn’t delete this transaction. Open Pending Changes to check it.",
                )
            loadLocalRecords(budgetId)
            return false
        } finally {
            isSavingTransaction = false
        }
    }

    suspend fun createTransaction(
        budgetId: String,
        accountId: String,
        payeeId: String?,
        categoryId: String?,
        amountText: String,
        dateText: String,
        notes: String,
        encryptionPassword: String?,
        displaySettings: BudgetDisplaySettings? = null,
        isTransfer: Boolean = false,
        confirmedFutureDate: String? = null,
    ): Boolean {
        transactionWriteMessage = null
        if (refuseWriteInReadOnlyMode()) return false
        if (!isCurrentIdentityCacheOwner()) {
            transactionWriteMessage = "Sign in to the server and account that own this budget before adding a transaction."
            return false
        }
        val amount = parseAmountMinorUnits(amountText, displaySettings)
        if (amount == null) {
            transactionWriteMessage = transactionAmountInputError(amountText, displaySettings)
            return false
        }
        if (amount == 0L) {
            transactionWriteMessage = "Enter an amount greater than zero."
            return false
        }
        if (amount == Long.MIN_VALUE || kotlin.math.abs(amount) > MAX_TRANSACTION_AMOUNT_MINOR_UNITS) {
            transactionWriteMessage = "Largest amount is ${formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS, displaySettings)}."
            return false
        }
        val dateValidation = validateTransactionDate(dateText, currentDateIso(), confirmedFutureDate)
        if (dateValidation.status != TransactionDateValidationStatus.VALID) {
            transactionWriteMessage = transactionDateValidationMessage(dateValidation)
            return false
        }
        val date = checkNotNull(dateValidation.date)
        val localRepository = repository
        val coordinator = syncCoordinator
        if (localRepository == null || coordinator == null) {
            transactionWriteMessage = "Budget data isn’t available on this device. Reopen the budget and try again."
            return false
        }
        val account = accountRecords.firstOrNull { it.recordId == accountId }
        if (account == null) {
            transactionWriteMessage = "Choose an account from this budget before saving."
            return false
        }
        val accountBody = account.recordBody()
        if (accountBody?.booleanValue("offbudget") == true || accountBody?.booleanValue("offBudget") == true) {
            transactionWriteMessage = "Choose an on-budget account for this transaction."
            return false
        }
        if (payeeId != null && payeeRecords.none { it.recordId == payeeId }) {
            transactionWriteMessage = "Choose an existing payee or clear the payee field."
            return false
        }
        val transferDestinationId =
            payeeId
                ?.let { id -> payeeRecords.firstOrNull { it.recordId == id } }
                ?.recordBody()
                ?.stringValue("transfer_acct")
        if (isTransfer && transferDestinationId.isNullOrBlank()) {
            transactionWriteMessage = "Choose a destination account for this transfer."
            return false
        }
        if (isTransfer && transferDestinationId == accountId) {
            transactionWriteMessage = "Choose a different destination account."
            return false
        }
        if (!isTransfer && !transferDestinationId.isNullOrBlank()) {
            transactionWriteMessage = "Choose Transfer as the transaction type for this payee."
            return false
        }
        val categoryIsPending =
            categoryId != null &&
                budgetQueue.budgetReorderQueue.moves.any {
                    it.budgetId == budgetId &&
                        it.itemId == categoryId &&
                        it.kind in setOf(BudgetReorderKind.CREATE_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_CREATE)
                }
        if (categoryId != null && categoryRecords.none { it.recordId == categoryId } && !categoryIsPending) {
            transactionWriteMessage = "Choose a category from this budget before saving."
            return false
        }
        val transactionId = "tandem:${Uuid.random()}"
        val operationId = Uuid.random().toString()
        val requestedCategoryId = categoryId.takeUnless { isTransfer }
        val patch =
            buildJsonObject {
                put("account", account.recordId)
                put("amount", amount)
                put("date", date)
                put("payee", payeeId?.let(::JsonPrimitive) ?: JsonNull)
                put("category", requestedCategoryId?.let(::JsonPrimitive) ?: JsonNull)
                put("notes", notes)
                put("cleared", false)
            }
        val optimistic =
            buildJsonObject {
                put("id", transactionId)
                put("account", account.recordId)
                put("amount", amount)
                put("date", date)
                put("payee", payeeId?.let(::JsonPrimitive) ?: JsonNull)
                put("category", categoryId?.let(::JsonPrimitive) ?: JsonNull)
                put("notes", notes)
                put("cleared", false)
                put("reconciled", JsonNull)
                put("importedId", JsonNull)
                put("importedPayee", JsonNull)
                put("startingBalance", JsonNull)
                put("schedule", JsonNull)
                put("transferId", JsonNull)
                put("isParent", false)
                put("isChild", false)
                put("parentId", JsonNull)
                put("subtransactions", JsonArray(emptyList()))
            }
        isSavingTransaction = true
        try {
            localRepository.enqueueWrite(
                LocalWriteRequest(
                    operationId = operationId,
                    idempotencyKey = operationId,
                    budgetId = budgetId,
                    recordType = "transaction",
                    recordId = transactionId,
                    action = LocalWriteAction.CREATE,
                    baseRevision = null,
                    baseSnapshotJson = null,
                    patchJson = patch.toString(),
                    optimisticJson = optimistic.toString(),
                    createdAtEpochMs = nowEpochMillis(),
                    optimisticProjection =
                        LocalRecordProjection(
                            recordDate = date,
                            accountId = account.recordId,
                            categoryId = requestedCategoryId,
                            payeeId = payeeId,
                            amountMinor = amount,
                            budgetMonth = date.take(7),
                        ),
                ),
            )
            val summary =
                if (budgetQueue.hasPendingTransactionDependency(budgetId)) {
                    null
                } else {
                    synchronize(budgetId, encryptionPassword)
                }
            val savedWrite = localRepository.findWrite(operationId)
            transactionWriteMessage =
                when (savedWrite?.status) {
                    "committed" -> "Transaction added to Actual."
                    "queued" -> "Saved on this device and waiting to sync."
                    "conflict" -> "This transaction is already in Actual. Sync the budget to check it."
                    "uncertain", "sending" -> "Tandem couldn’t confirm the save. Check Activity before adding it again."
                    "rejected" -> "Actual didn’t add this transaction. Review the details and try again."
                    else ->
                        if (summary == null) {
                            "Tandem couldn’t confirm the save. Check Activity before adding it again."
                        } else {
                            "The transaction is saved on this device and is waiting to sync."
                        }
                }
            loadLocalRecords(budgetId)
            return savedWrite?.status in setOf("committed", "queued")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            transactionWriteMessage =
                localStorageFailureMessage(
                    error,
                    "Tandem couldn’t confirm the save. Check Activity before adding it again.",
                )
            loadLocalRecords(budgetId)
            return false
        } finally {
            isSavingTransaction = false
        }
    }

    suspend fun resolveConflict(
        budgetId: String,
        operationId: String,
        keepDeviceVersion: Boolean,
        encryptionPassword: String?,
        reviewedRevision: String,
    ) {
        clearTransactionWriteMessage()
        if (refuseWriteInReadOnlyMode()) {
            reportConflictResolution(operationId, transactionWriteMessage ?: "Read-only mode is on.")
            return
        }
        if (isSavingTransaction) {
            reportConflictResolution(
                operationId,
                "A decision is already being saved. Wait for it to finish, then review this change again.",
            )
            return
        }
        val localRepository = repository
        if (localRepository == null) {
            reportConflictResolution(operationId, "Budget data isn’t available on this device. Restart the app and try again.")
            return
        }
        val operation = localRepository.readPendingWrites(budgetId).firstOrNull { it.operationId == operationId }
        if (operation == null || operation.status != "conflict") {
            reportConflictResolution(operationId, "This change no longer needs review. Sync the budget to refresh its status.")
            loadLocalRecords(budgetId)
            return
        }
        isSavingTransaction = true
        try {
            val workflow =
                TransactionConflictResolution(
                    store = RepositoryConflictResolutionStore(localRepository),
                    sync =
                        object : ConflictResolutionSync {
                            override suspend fun confirmConflictWithServer(
                                budgetId: String,
                                operationId: String,
                                encryptionPassword: String?,
                            ): Boolean = this@TandemMobileSession.confirmConflictWithServer(budgetId, operationId, encryptionPassword)

                            override suspend fun synchronize(
                                budgetId: String,
                                encryptionPassword: String?,
                                retryQueuedBudgetMutations: Boolean,
                            ): BudgetSyncSummary? =
                                this@TandemMobileSession.synchronize(
                                    budgetId = budgetId,
                                    encryptionPassword = encryptionPassword,
                                    retryQueuedBudgetMutations = retryQueuedBudgetMutations,
                                )
                        },
                    nowEpochMillis = nowEpochMillis,
                )
            reportConflictResolution(
                operationId,
                workflow.resolve(
                    budgetId,
                    operationId,
                    keepDeviceVersion,
                    encryptionPassword,
                    reviewedRevision,
                ),
            )
            loadLocalRecords(budgetId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            reportConflictResolution(
                operationId,
                "Tandem hasn’t confirmed your choice. Your edit remains on this device. Check the connection and try again.",
            )
            loadLocalRecords(budgetId)
        } finally {
            isSavingTransaction = false
        }
    }

    suspend fun discardWrite(
        budgetId: String,
        operationId: String,
        encryptionPassword: String? = null,
    ) {
        transactionWriteMessage = null
        clearUncertainWriteCheck()
        if (refuseWriteInReadOnlyMode()) return
        val localRepository = repository ?: return
        val operation = localRepository.findWrite(operationId)
        if (operation?.status == "uncertain" && operation.recordType != "budget-mutation") {
            if (operation.recordType != "transaction") {
                uncertainWriteCheckError = "Tandem couldn’t confirm that this change was removed. It remains on this device."
                uncertainWriteCheckErrorOperationId = operationId
                return
            }
            val coordinator = syncCoordinator
            if (coordinator == null || !isCurrentIdentityCacheOwner()) {
                uncertainWriteCheckError =
                    "Tandem couldn’t confirm that this edit was removed. It remains on this device. Sign in to the server and try again."
                uncertainWriteCheckErrorOperationId = operationId
                checkedWriteOperation = operation
                checkedWriteOperationId = operationId
                return
            }
            isSavingTransaction = true
            try {
                val result =
                    apiClient.withExclusiveSession {
                        coordinator.discardUncertainWrite(
                            budgetId,
                            operationId,
                        )
                    }
                if (result.status == app.tandemforactual.data.store.RemoteOperationStatus.REJECTED &&
                    result.errorCode == "discarded-by-user"
                ) {
                    loadLocalRecords(budgetId)
                    checkedWriteOperation = localRepository.findWrite(operationId)
                    checkedWriteOperationId = operationId
                    transactionWriteMessage = discardedPendingWriteMessage("uncertain")
                    return
                }

                val summary =
                    synchronize(
                        budgetId = budgetId,
                        encryptionPassword = encryptionPassword,
                        sendQueuedWrites = false,
                        reconcileUncertainOperationId = operationId,
                    )
                checkedWriteOperation = localRepository.findWrite(operationId)
                checkedWriteOperationId = operationId
                if (checkedWriteOperation?.status == "uncertain") {
                    uncertainWriteCheckError =
                        if (summary == null) {
                            "Tandem couldn’t confirm that this edit was removed. " +
                                "It remains on this device. Check the connection and try again."
                        } else {
                            "Tandem still can’t confirm whether Actual saved this edit. " +
                                "It remains on this device. Check status before removing it."
                        }
                    uncertainWriteCheckErrorOperationId = operationId
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                checkedWriteOperation = localRepository.findWrite(operationId)
                checkedWriteOperationId = operationId
                uncertainWriteCheckError =
                    "Tandem couldn’t confirm that this edit was removed. It remains on this device. Check the connection and try again."
                uncertainWriteCheckErrorOperationId = operationId
                loadLocalRecords(budgetId)
            } finally {
                isSavingTransaction = false
            }
            return
        }
        if (operation?.recordType == "budget-mutation" && !budgetQueue.restoreBudgetReorderQueue()) {
            transactionWriteMessage = "Couldn’t verify this pending change. Try again before removing it."
            return
        }
        localRepository.discardWrite(operationId)
        if (operation?.recordType == "budget-mutation") {
            budgetQueue.budgetReorderQueue = budgetQueue.budgetReorderQueue.discardOperation(operationId)
            if (operation.status != "uncertain") budgetQueue.retryQueuedBudgetReorders()
        }
        loadLocalRecords(budgetId)
        transactionWriteMessage = discardedPendingWriteMessage(operation?.status)
    }

    suspend fun retryUncertainWrite(
        budgetId: String,
        operationId: String,
        encryptionPassword: String?,
    ) {
        transactionWriteMessage = null
        clearUncertainWriteCheck()
        if (refuseWriteInReadOnlyMode()) return
        val localRepository = repository ?: return
        when (localRepository.findWrite(operationId)?.status) {
            "uncertain" -> localRepository.retryUncertainWrite(operationId)
            "queued" -> Unit
            else -> {
                transactionWriteMessage = "This edit is no longer waiting for confirmation. Refresh Pending Changes."
                loadLocalRecords(budgetId)
                return
            }
        }
        synchronize(budgetId, encryptionPassword)
        loadLocalRecords(budgetId)
    }

    /** Each budget's sync state on this device; an encrypted budget that isn't open keeps its state locked. */
    suspend fun budgetSyncOverview(openBudgetId: String): List<BudgetSyncOverview> {
        val main = mainStorage?.repository
        return budgets.map { budget ->
            val open = budget.budgetId == openBudgetId
            val writes = if (open) pendingWrites else main?.readPendingWrites(budget.budgetId).orEmpty()
            val sync = if (open) localSyncState else main?.readSyncState(budget.budgetId)
            BudgetSyncOverview(
                budgetId = budget.budgetId,
                name = budget.name,
                open = open,
                locked = budget.isEncrypted && !open,
                lastSyncedAtEpochMs = sync?.lastSyncedAtEpochMs,
                failed = sync?.lastErrorCode != null,
                waiting = writes.count { it.status == "queued" || it.status == "sending" },
                needsReview = writes.count { it.status == "uncertain" || it.status == "conflict" || it.status == "rejected" },
            )
        }
    }

    /** Sends unconfirmed edits again, then retries bank links that failed and planning sync that's waiting. */
    suspend fun retryFailedWork(
        budgetId: String,
        encryptionPassword: String?,
    ) {
        if (refuseWriteInReadOnlyMode()) return
        val localRepository = repository ?: return
        pendingWrites
            .filter { it.budgetId == budgetId && it.status == "uncertain" && it.recordType == "transaction" }
            .forEach { runCatching { localRepository.retryUncertainWrite(it.operationId) } }
        synchronize(budgetId, encryptionPassword)
        loadLocalRecords(budgetId)
        if (banking.bankLinks.any { it.failure != null }) banking.runBankSync(budgetId, encryptionPassword)
        if (planning.syncStatus == PlanningSyncStatus.WAITING) planning.sync(budgetId)
    }

    suspend fun checkUncertainWriteStatus(
        budgetId: String,
        operationId: String,
        encryptionPassword: String?,
    ) {
        if (isSyncing) return
        clearUncertainWriteCheck()
        val summary =
            synchronize(
                budgetId = budgetId,
                encryptionPassword = encryptionPassword,
                sendQueuedWrites = false,
                reconcileUncertainOperationId = operationId,
            )
        if (summary == null) {
            checkedWriteOperationId = operationId
            uncertainWriteCheckError = syncError ?: "Tandem couldn’t check this change. Try again."
            uncertainWriteCheckErrorOperationId = operationId
            return
        }

        checkedWriteOperationId = operationId
        summary.operationStatusErrorCode?.takeIf { it != "operation_not_found" }?.let { statusErrorCode ->
            uncertainWriteCheckError =
                apiFailureMessage(
                    errorCode = statusErrorCode,
                    httpStatus = apiFailureHttpStatus(statusErrorCode, null),
                    context = ApiFailureContext.SYNC,
                    fallback = "Tandem couldn’t confirm this change’s status. Check the connection, then try again.",
                )
            uncertainWriteCheckErrorOperationId = operationId
        }
        try {
            checkedWriteOperation = repository?.findWrite(operationId)
            if (checkedWriteOperation == null) {
                uncertainWriteCheckError = "This edit is no longer waiting to sync."
                uncertainWriteCheckErrorOperationId = operationId
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            uncertainWriteCheckError = "Couldn’t check this edit’s status on this device."
            uncertainWriteCheckErrorOperationId = operationId
        }
    }

    private fun clearUncertainWriteCheck() {
        checkedWriteOperation = null
        checkedWriteOperationId = null
        uncertainWriteCheckError = null
        uncertainWriteCheckErrorOperationId = null
    }

    override suspend fun loadLocalRecords(budgetId: String) {
        val localRepository = repository ?: return
        if (!isCurrentIdentityCacheOwner()) {
            clearCachedBudgetPresentation()
            return
        }
        cachedSnapshotBudgetId = budgetId
        hasCachedSnapshot = localRepository.hasCachedSnapshot(budgetId)
        budgetDisplaySettingsBudgetId = budgetId
        budgetDisplaySettings = localRepository.readBudgetDisplaySettings(budgetId)
        localSyncState = localRepository.readSyncState(budgetId)
        transactionRecords = localRepository.findRecentRecords(budgetId, "transaction", limit = 50)
        cachedTransactionCount =
            localRepository.countVisibleRecords(budgetId, "transaction").coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        accountRecords = localRepository.findRecords(budgetId, "account")
        accountGroupRecords = localRepository.findRecords(budgetId, "account-group")
        scheduleRecords = localRepository.findRecords(budgetId, "schedule")
        ruleRecords = localRepository.findRecords(budgetId, "rule")
        payeeRecords = budgetQueue.payeeRecordsWithPendingCreates(budgetId, localRepository.findRecords(budgetId, "payee"))
        categoryRecords = localRepository.findRecords(budgetId, "category")
        categoryGroupRecords = localRepository.findRecords(budgetId, "category-group")
        budgetMonthIds = localRepository.findRecordIds(budgetId, "budget-month")
        pendingWrites = localRepository.readPendingWrites(budgetId)
        conflictRecordsByRecordId = findConflictRecords(budgetId, pendingWrites)
        refreshActivityTransactionPages(budgetId)
    }

    private suspend fun findConflictRecords(
        budgetId: String,
        writes: List<OutboxOperationEntity>,
    ): Map<String, BudgetRecordEntity> {
        val localRepository = repository ?: return emptyMap()
        val records = mutableMapOf<String, BudgetRecordEntity>()
        writes.filter { it.status == "conflict" }.forEach { operation ->
            localRepository.findRecord(budgetId, operation.recordType, operation.recordId)?.let { record ->
                records[operation.recordId] = record
            }
        }
        return records
    }

    suspend fun loadMoreActivityTransactions(
        budgetId: String,
        reset: Boolean = false,
    ) {
        val localRepository = repository ?: return
        if (budgetId.isBlank()) return
        if (!isCurrentIdentityCacheOwner()) {
            clearCachedBudgetPresentation()
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

    private suspend fun refreshActivityTransactionPages(budgetId: String) {
        if (activityTransactionPageBudgetId != budgetId) return
        val localRepository = repository ?: return
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

    fun observeAccountActivityRecords(
        budgetId: String,
        accountId: String,
        limit: Int = 100,
        offset: Int = 0,
    ): Flow<List<BudgetRecordEntity>>? = repository?.observeRecentTransactionsForAccount(budgetId, accountId, limit, offset)

    fun observeClearedAccountBalance(
        budgetId: String,
        accountId: String,
    ): Flow<Long>? = repository?.observeClearedAccountBalance(budgetId, accountId)

    suspend fun observeLocalRecords(budgetId: String) {
        val localRepository = repository ?: return
        if (!isCurrentIdentityCacheOwner()) {
            clearCachedBudgetPresentation()
            return
        }
        cachedSnapshotBudgetId = budgetId
        hasCachedSnapshot = localRepository.hasCachedSnapshot(budgetId)
        budgetDisplaySettingsBudgetId = budgetId
        budgetDisplaySettings = localRepository.readBudgetDisplaySettings(budgetId)
        coroutineScope {
            launch {
                localRepository.observeRecentRecords(budgetId, "transaction", 50).collect { transactionRecords = it }
            }
            launch {
                localRepository.observeVisibleRecordCount(budgetId, "transaction").collect {
                    cachedTransactionCount = it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                }
            }
            launch { localRepository.observeRecords(budgetId, "account").collect { accountRecords = it } }
            launch { localRepository.observeRecords(budgetId, "account-group").collect { accountGroupRecords = it } }
            launch { localRepository.observeRecords(budgetId, "schedule").collect { scheduleRecords = it } }
            launch { localRepository.observeRecords(budgetId, "rule").collect { ruleRecords = it } }
            launch {
                localRepository
                    .observeRecords(
                        budgetId,
                        "payee",
                    ).collect { payeeRecords = budgetQueue.payeeRecordsWithPendingCreates(budgetId, it) }
            }
            launch { localRepository.observeRecords(budgetId, "category").collect { categoryRecords = it } }
            launch { localRepository.observeRecords(budgetId, "category-group").collect { categoryGroupRecords = it } }
            launch { localRepository.observeRecordIds(budgetId, "budget-month").collect { budgetMonthIds = it } }
            launch {
                localRepository.observePendingWrites(budgetId).collect { writes ->
                    pendingWrites = writes
                    conflictRecordsByRecordId = findConflictRecords(budgetId, writes)
                }
            }
            launch { localRepository.observeSyncState(budgetId).collect { localSyncState = it } }
            with(planning) { observe(budgetId) }
        }
    }

    suspend fun observeSelectedBudgetMonth(
        budgetId: String,
        monthId: String,
    ) {
        val observationKey = "$budgetId/$monthId"
        selectedBudgetMonthObservationKey = observationKey
        selectedBudgetMonthRecord = null
        isLoadingSelectedBudgetMonth = true
        val localRepository = repository
        if (localRepository == null || budgetId.isBlank() || monthId.isBlank() || !isCurrentIdentityCacheOwner()) {
            isLoadingSelectedBudgetMonth = false
            return
        }
        localRepository.observeRecord(budgetId, "budget-month", monthId).collect { record ->
            if (selectedBudgetMonthObservationKey == observationKey) {
                selectedBudgetMonthRecord = record
                isLoadingSelectedBudgetMonth = false
            }
        }
    }

    suspend fun observeActivitySearchRecords(budgetId: String) {
        val localRepository = repository
        if (localRepository == null || budgetId.isBlank()) {
            activitySearchRecords = emptyList()
            activitySearchRecordsBudgetId = budgetId
            return
        }
        if (!isCurrentIdentityCacheOwner()) {
            activitySearchRecords = emptyList()
            activitySearchRecordsBudgetId = budgetId
            return
        }
        if (activitySearchRecordsBudgetId != budgetId) {
            activitySearchRecords = emptyList()
            activitySearchRecordsBudgetId = budgetId
        }
        localRepository.observeSearchableTransactions(budgetId).collect { records ->
            if (records != activitySearchRecords) activitySearchRecords = records
        }
    }

    suspend fun hasCachedBudgetSnapshot(budgetId: String): Boolean =
        budgetId.isNotBlank() &&
            budgetId == budgetId.trim() &&
            isCurrentIdentityCacheOwner() &&
            repository?.hasCachedSnapshot(budgetId) == true

    suspend fun loadCachedBudgets(): List<BudgetCatalogItem> {
        val localRepository = repository ?: return emptyList()
        if (isSigningIn || !isCurrentIdentityCacheOwner()) {
            cachedBudgets = emptyList()
            return emptyList()
        }
        val cached =
            localRepository.findCachedBudgetIds().map { budgetId ->
                val currencyCode = localRepository.readBudgetDisplaySettings(budgetId)?.currencyCode
                BudgetCatalogItem(
                    budgetId = budgetId,
                    name = currencyCode?.let { "Saved budget · $it" } ?: "Saved budget",
                    isEncrypted = false,
                )
            }
        cachedBudgets = cached
        return cached
    }

    /** Saves local cache ownership before any sync can expose another account's records. */
    internal suspend fun acceptAuthenticatedIdentity(
        device: AuthenticatedDevice,
        previousSession: String?,
        restoringSavedSession: Boolean = false,
    ): Boolean {
        authenticatedDevice = device
        val store = preferencesStore ?: return true
        val serverUrl = apiClient.currentServerUrl
        if (serverUrl.isNullOrBlank() || !apiClient.isAuthenticated) {
            restorePreviousSessionAfterSignIn(previousSession)
            signInError = "Couldn’t verify this sign-in. Try again."
            return false
        }
        if (!budgetQueue.restoreBudgetReorderQueue()) {
            restorePreviousSessionAfterSignIn(previousSession)
            signInError = "Couldn’t check pending changes. Try again before switching accounts."
            return false
        }

        val owner = budgetCacheOwner(serverUrl, device.principalId)
        val savedPreferences = store.read()
        val savedOwner = savedPreferences[BUDGET_CACHE_OWNER_KEY]?.takeIf(String::isNotBlank)
        val hasAnyLocalBudgetData = repository?.hasAnyLocalBudgetData() == true
        val hasPendingBudgetChanges = budgetQueue.budgetReorderQueue.moves.isNotEmpty()
        val hasUnresolvedWrites = repository?.hasUnresolvedWrites() == true
        val changingOwner = savedOwner != null && savedOwner != owner
        val ownerUnknownWithLocalData =
            !restoringSavedSession &&
                savedOwner == null &&
                (hasAnyLocalBudgetData || hasPendingBudgetChanges || hasUnresolvedWrites)

        if ((changingOwner || ownerUnknownWithLocalData) && (hasPendingBudgetChanges || hasUnresolvedWrites)) {
            restorePreviousSessionAfterSignIn(previousSession)
            signInError =
                "Pending changes belong to another server or account. Sign in there and sync or resolve them before switching."
            return false
        }

        if (changingOwner || ownerUnknownWithLocalData) {
            budgetQueue.budgetQueuePersistenceMutex.withLock {
                budgetQueue.budgetReorderQueueState = BudgetReorderQueueState()
                budgetQueue.budgetQueueLoaded = true
                repository?.clearAllLocalData()
                store.putValue(AppPreferencesStore.PENDING_BUDGET_REORDERS_KEY, "")
            }
            clearCachedBudgetPresentation()
        }
        store.putValue(BUDGET_CACHE_OWNER_KEY, owner)
        return true
    }

    private suspend fun restorePreviousSessionAfterSignIn(previousSession: String?) {
        if (previousSession.isNullOrBlank()) {
            apiClient.forgetSession()
            authenticatedDevice = null
            return
        }
        try {
            authenticatedDevice = apiClient.restoreStoredSession(previousSession)
        } catch (_: Exception) {
            apiClient.forgetSession()
            authenticatedDevice = null
        }
    }

    override suspend fun isCurrentIdentityCacheOwner(): Boolean {
        if (!apiClient.isAuthenticated) return true
        val device = authenticatedDevice ?: return false
        val serverUrl = apiClient.currentServerUrl ?: return false
        val store = preferencesStore ?: return true
        return try {
            store.read()[BUDGET_CACHE_OWNER_KEY] == budgetCacheOwner(serverUrl, device.principalId)
        } catch (_: Exception) {
            false
        }
    }

    private fun clearCachedBudgetPresentation() {
        budgets = emptyList()
        cachedBudgets = emptyList()
        budgetCatalogLoaded = false
        budgetCatalogVersion += 1
        hasCachedSnapshot = false
        cachedSnapshotBudgetId = ""
        budgetDisplaySettings = null
        budgetDisplaySettingsBudgetId = null
        localSyncState = null
        transactionRecords = emptyList()
        activityTransactionRecords.clear()
        activityTransactionRecordsVersion += 1
        activityTransactionPageBudgetId = ""
        activityTransactionPageError = null
        hasMoreActivityTransactions = false
        activitySearchRecords = emptyList()
        activitySearchRecordsBudgetId = ""
        payeeRecords = emptyList()
        categoryRecords = emptyList()
        categoryGroupRecords = emptyList()
        budgetMonthIds = emptyList()
        selectedBudgetMonthRecord = null
        isLoadingSelectedBudgetMonth = false
        selectedBudgetMonthObservationKey = ""
        accountRecords = emptyList()
        accountGroupRecords = emptyList()
        scheduleRecords = emptyList()
        ruleRecords = emptyList()
        pendingWrites = emptyList()
        conflictRecordsByRecordId = emptyMap()
        cachedTransactionCount = 0
        syncedTransactionCount = 0
    }

    private suspend fun persistCurrentSession() {
        val store = credentialStore ?: return
        val storedSession = apiClient.exportStoredSession() ?: return
        credentialStorageError = null
        try {
            withContext(Dispatchers.Default) { store.write(storedSession) }
            isCredentialStored = true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            isCredentialStored = false
            credentialStorageError = "This sign-in will end when the app closes because saved access is unavailable."
        }
    }

    private suspend fun clearStoredCredential() {
        val store = credentialStore ?: return
        try {
            withContext(Dispatchers.Default) { store.clear() }
            isCredentialStored = false
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            credentialStorageError = "Couldn’t remove this device’s saved access."
        }
    }

    private suspend fun clearDeviceSession() {
        openIdLoginProofState.clear()
        apiClient.forgetSession()
        authenticatedDevice = null
        budgets = emptyList()
        budgetCatalogLoaded = false
        isServerReachable = false
        budgetError = null
        clearStoredCredential()
    }
}

internal fun discardedPendingWriteMessage(operationStatus: String?): String =
    if (operationStatus == "uncertain") {
        "This pending change was removed from this device. Actual may already have saved it; removing it here doesn’t undo it."
    } else {
        "Your edit was discarded. Actual wasn’t changed."
    }

internal fun signOutRevocationNotice(httpStatus: Int?): String =
    if (httpStatus == null) {
        "The server still has this device linked. Sign in again to this server and unlink it in Settings."
    } else {
        "Couldn’t confirm that the server removed this device. Sign in again to this server and check Settings."
    }

internal fun TandemApiException.userFacingMessage(
    fallback: String,
    context: ApiFailureContext = ApiFailureContext.GENERAL,
): String = apiFailureMessage(errorCode, httpStatus, context, fallback)

internal fun budgetApiErrorMessage(
    errorCode: String,
    httpStatus: Int?,
    fallback: String,
): String = apiFailureMessage(errorCode, httpStatus, ApiFailureContext.GENERAL, fallback)

internal const val ACTIVITY_TRANSACTION_PAGE_SIZE = 50
internal const val ACTIVITY_TRANSACTION_REFRESH_LIMIT = 200

/** How long reorders wait on the device before they are sent together, unless Sync now is tapped. */
internal const val BUDGET_ORDER_FLUSH_INTERVAL_MS = 60_000L
internal const val BUDGET_QUEUE_RESTORE_ERROR = "Couldn’t load saved category changes. Try again before editing categories."
internal const val BUDGET_CACHE_OWNER_KEY = "internal:budget-cache-owner-v1"

internal fun budgetCacheOwner(
    serverUrl: String,
    principalId: String,
): String =
    buildJsonObject {
        put("serverUrl", serverUrl.trim().trimEnd('/'))
        put("principalId", principalId)
    }.toString()

internal fun parseQueuePayload(encoded: String?): JsonObject? =
    encoded?.let {
        runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull()
    }

internal fun QueuedBudgetReorder.requirePayload(): JsonObject = parseQueuePayload(payloadJson) ?: JsonObject(emptyMap())

internal fun JsonObject.requiredString(key: String): String = stringValue(key) ?: ""

internal fun JsonObject.requiredLong(key: String): Long = this[key]?.jsonPrimitive?.longOrNull ?: -1L

internal fun JsonObject.requiredBoolean(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull ?: false

internal fun JsonObject.optionalString(key: String): String? = this[key]?.takeUnless { it == JsonNull }?.jsonPrimitive?.contentOrNull

internal fun JsonObject.requiredAssignments(): List<Pair<String, Long>> =
    (this["assignments"] as? JsonArray)
        ?.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val categoryId = item.stringValue("categoryId") ?: return@mapNotNull null
            val amountMinor = item["amountMinor"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            categoryId to amountMinor
        }.orEmpty()

internal fun JsonObject.requiredStatementItems(): List<StatementImportItem> =
    (this["transactions"] as? JsonArray)
        ?.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val date = item.stringValue("date") ?: return@mapNotNull null
            val amount = item["amountMinor"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val payeeName = item.stringValue("payeeName") ?: return@mapNotNull null
            val importedId = item.stringValue("importedId") ?: return@mapNotNull null
            StatementImportItem(date, amount, payeeName, item.optionalString("notes"), importedId)
        }.orEmpty()

internal fun rewriteAccountIds(
    element: JsonElement,
    temporaryId: String,
    actualId: String,
): JsonElement =
    when (element) {
        is JsonObject ->
            JsonObject(
                element.mapValues { (key, value) ->
                    if (key in ACCOUNT_REFERENCE_FIELDS && (value as? JsonPrimitive)?.contentOrNull == temporaryId) {
                        JsonPrimitive(actualId)
                    } else {
                        rewriteAccountIds(value, temporaryId, actualId)
                    }
                },
            )
        is JsonArray -> JsonArray(element.map { rewriteAccountIds(it, temporaryId, actualId) })
        else -> element
    }

internal val ACCOUNT_REFERENCE_FIELDS = setOf("account", "accountId", "targetAccountId", "transferAccountId")

internal fun BudgetReorderQueueState.enqueueIfMissing(move: QueuedBudgetReorder): BudgetReorderQueueState =
    if (moves.any { it.operationId == move.operationId }) this else enqueue(move)

internal fun activityTransactionRefreshLimit(loadedRecordCount: Int): Int =
    minOf(
        loadedRecordCount.coerceAtLeast(ACTIVITY_TRANSACTION_PAGE_SIZE),
        ACTIVITY_TRANSACTION_REFRESH_LIMIT,
    )

/** Rows missing from the refreshed window were deleted; only rows older than the window are kept from before. */
internal fun mergeRefreshedActivityTransactions(
    previouslyLoaded: List<BudgetRecordEntity>,
    refreshedPage: List<BudgetRecordEntity>,
    pageIsComplete: Boolean = false,
): List<BudgetRecordEntity> {
    val order = compareByDescending<BudgetRecordEntity> { it.recordDate ?: "" }.thenBy { it.recordId }
    val refreshed = refreshedPage.distinctBy(BudgetRecordEntity::recordId).sortedWith(order)
    if (pageIsComplete) return refreshed
    val refreshedIds = refreshed.mapTo(mutableSetOf(), BudgetRecordEntity::recordId)
    val oldestRefreshed = refreshed.lastOrNull()
    val older =
        previouslyLoaded.filter { record ->
            record.recordId !in refreshedIds && (oldestRefreshed == null || order.compare(record, oldestRefreshed) > 0)
        }
    return (refreshed + older).distinctBy(BudgetRecordEntity::recordId).sortedWith(order)
}

internal fun JsonObject.stringValue(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.longValue(name: String): Long? =
    (this[name] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

internal fun JsonObject.toProjection(): LocalRecordProjection =
    LocalRecordProjection(
        recordDate = stringValue("date"),
        accountId = stringValue("account"),
        categoryId = stringValue("category"),
        payeeId = stringValue("payee"),
        amountMinor = longValue("amount"),
        budgetMonth = stringValue("month"),
    )

internal const val BUDGET_PASSWORD_ATTEMPTS_EXCEEDED = "budget_password_attempts_exceeded"

internal val BUDGET_PASSWORD_ERROR_CODES =
    setOf("budget_password_required", "budget_password_incorrect", BUDGET_PASSWORD_ATTEMPTS_EXCEEDED)

internal const val ENCRYPTED_DATABASE_CLOSE_DELAY_MILLIS = 3_000L

internal const val CAPABILITIES_REFRESH_MILLIS = 10 * 60_000L

internal fun requiredServerFeature(kind: BudgetReorderKind): String? =
    when (kind) {
        BudgetReorderKind.HOLD_BUDGET_NEXT_MONTH -> "hold-total"
        BudgetReorderKind.SET_ORDER -> "budget-order-merge"
        else -> null
    }

internal fun missingServerFeature(
    kind: BudgetReorderKind,
    capabilities: ServerCapabilities?,
): String? {
    val feature = requiredServerFeature(kind) ?: return null
    return feature.takeUnless { capabilities == null || capabilities.supports(it) }
}

internal const val RECENT_PAYEE_COUNT = 5
