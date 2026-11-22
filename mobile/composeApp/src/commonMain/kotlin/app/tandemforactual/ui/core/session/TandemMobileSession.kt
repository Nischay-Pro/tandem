package app.tandemforactual.ui.core.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.data.store.BudgetSyncCoordinator
import app.tandemforactual.data.store.BudgetSyncSummary
import app.tandemforactual.data.store.LocalBudgetMutationOutboxItem
import app.tandemforactual.data.store.LocalBudgetMutationQueueSnapshot
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalRecordProjection
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.format.signedAmount
import app.tandemforactual.ui.core.session.activity.ActivityController
import app.tandemforactual.ui.core.session.banking.BankingController
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits
import app.tandemforactual.ui.core.session.budget.payeeRecordsWithPendingCreates
import app.tandemforactual.ui.core.session.conflicts.ConflictController
import app.tandemforactual.ui.core.session.feedback.BudgetActionRunner
import app.tandemforactual.ui.core.session.insights.InsightsController
import app.tandemforactual.ui.core.session.planning.PlanningController
import app.tandemforactual.ui.core.session.schedules.ScheduleActionController
import app.tandemforactual.ui.core.session.transactions.TransactionController
import app.tandemforactual.ui.core.state.AppRouteEntry
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.feature.reports.ReportsViewModels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

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
        internal set

    var feedback by mutableStateOf<BudgetActionFeedback?>(null)
        internal set

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
    internal val repository: LocalBudgetRepository,
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
    internal val credentialStore: DeviceCredentialStore? = null,
    budgetMutationQueuePersistenceOverride: BudgetMutationQueuePersistence? = null,
    override val currentDateIso: () -> String = ::platformTodayIsoDate,
    internal val openIdLoginProofState: OpenIdLoginProofState = OpenIdLoginProofState(nowEpochMillis),
    internal val revokeCurrentDeviceForSignOut: suspend () -> Unit = { apiClient.revokeCurrentDevice() },
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
        val reports = ReportsViewModels(repository)
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
    val planning = PlanningController(apiClient, { storage?.database }, nowEpochMillis)

    override val budgetMutationQueuePersistence: BudgetMutationQueuePersistence?
        get() = queuePersistenceOverride ?: storage?.queuePersistence
    internal val queuePersistenceOverride = budgetMutationQueuePersistenceOverride
    override val preferencesStore: AppPreferencesStore? = database?.let(::AppPreferencesStore)
    override val sessionScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal val reportViewModels: ReportsViewModels? get() = storage?.reports
    override val syncCoordinator: BudgetSyncCoordinator? get() = storage?.coordinator

    var databaseGeneration by mutableStateOf(0)
        internal set

    internal val boundEncryptedBudgetId: String? get() = budgetStorage?.budgetId

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
        activity.refreshActivityTransactionPages(budgetId)
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

    override fun clearCachedBudgetPresentation() {
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
        activity.reset()
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
