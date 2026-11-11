package app.tandemforactual.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import app.tandemforactual.api.client.ActualLoginMethods
import app.tandemforactual.api.client.AuthenticatedDevice
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.StatementImportItem
import app.tandemforactual.api.client.StatementImportPreview
import app.tandemforactual.api.client.StatementImportSummary
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
import app.tandemforactual.data.store.BudgetSyncProtocolException
import app.tandemforactual.data.store.BudgetSyncRemoteException
import app.tandemforactual.data.store.BudgetSyncSummary
import app.tandemforactual.data.store.LocalBudgetMutationOutboxItem
import app.tandemforactual.data.store.LocalBudgetMutationQueueSnapshot
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalOperationConflictException
import app.tandemforactual.data.store.LocalRecordProjection
import app.tandemforactual.data.store.LocalScheduleActionOutboxItem
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.model.LiveCategoryGroupSummary
import app.tandemforactual.ui.model.booleanValue
import app.tandemforactual.ui.model.recordBody
import app.tandemforactual.ui.model.stringValue
import app.tandemforactual.ui.reports.ReportsViewModels
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
) {
    fun begin(): SyncPresentationState = copy(isSyncing = true)

    fun succeeded(): SyncPresentationState = SyncPresentationState()

    fun failed(message: String): SyncPresentationState = SyncPresentationState(error = message)

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
    val actionLabel = mobileSession.budgetActionMessageActionLabel
    InlineMessage(
        message,
        mobileSession.budgetActionMessageKind,
        actionLabel = actionLabel,
        onAction = if (actionLabel == null) null else ({ mobileSession.retryBudgetAction() }),
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

private class LocalBudgetRepositoryMutationQueuePersistence(
    private val repository: LocalBudgetRepository,
) : BudgetMutationQueuePersistence {
    override suspend fun read(): LocalBudgetMutationQueueSnapshot = repository.readBudgetMutationQueue()

    override suspend fun replace(
        items: List<LocalBudgetMutationOutboxItem>,
        metadataJson: String,
        supersededOperationIds: Set<String>,
    ) = repository.replaceBudgetMutationQueue(items, metadataJson, supersededOperationIds)
}

/** In-memory session state. Device credentials use a platform store; budget passwords are never persisted. */
internal class TandemMobileSession(
    private val apiClient: TandemApiClient,
    database: TandemDatabase?,
    private val nowEpochMillis: () -> Long,
    private val credentialStore: DeviceCredentialStore? = null,
    budgetMutationQueuePersistenceOverride: BudgetMutationQueuePersistence? = null,
    private val currentDateIso: () -> String = ::platformTodayIsoDate,
    private val openIdLoginProofState: OpenIdLoginProofState = OpenIdLoginProofState(nowEpochMillis),
    private val revokeCurrentDeviceForSignOut: suspend () -> Unit = { apiClient.revokeCurrentDevice() },
) {
    private val repository = database?.let { LocalBudgetRepository(it, nowEpochMillis) }
    private val budgetMutationQueuePersistence =
        budgetMutationQueuePersistenceOverride ?: repository?.let(::LocalBudgetRepositoryMutationQueuePersistence)
    private val preferencesStore = database?.let(::AppPreferencesStore)
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val budgetQueuePersistenceMutex = Mutex()
    private val budgetQueueRestoreMutex = Mutex()
    private var budgetQueueLoaded = false
    internal val reportViewModels = repository?.let(::ReportsViewModels)
    private val syncCoordinator =
        repository?.let {
            BudgetSyncCoordinator(
                it,
                app.tandemforactual.api.client
                    .GeneratedBudgetSyncRemote(apiClient),
            )
        }

    var authenticatedDevice by mutableStateOf<AuthenticatedDevice?>(null)
        private set

    var budgets by mutableStateOf<List<BudgetCatalogItem>>(emptyList())
        private set

    var cachedBudgets by mutableStateOf<List<BudgetCatalogItem>>(emptyList())
        private set

    var budgetCatalogLoaded by mutableStateOf(false)
        private set

    var budgetCatalogVersion by mutableStateOf(0)
        private set

    var isServerReachable by mutableStateOf(false)
        private set

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

    var isSigningOut by mutableStateOf(false)
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

    val isSyncing: Boolean get() = syncPresentation.isSyncing

    var syncComplete by mutableStateOf(false)
        private set

    val syncError: String? get() = syncPresentation.error

    var syncedTransactionCount by mutableStateOf(0)
    var cachedTransactionCount by mutableStateOf(0)
        private set

    var pendingWrites by mutableStateOf<List<OutboxOperationEntity>>(emptyList())
        private set

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

    var isSavingTransaction by mutableStateOf(false)
        private set

    var transactionWriteMessage by mutableStateOf<String?>(null)
        private set

    var conflictResolutionFeedback by mutableStateOf<ConflictResolutionFeedback?>(null)
        private set

    var serverConfirmedConflictOperationId by mutableStateOf<String?>(null)
        private set

    var isPerformingBudgetAction by mutableStateOf(false)
        private set

    private val budgetActionFeedbackState = BudgetActionFeedbackState()
    private var budgetActionRetryAction: (suspend () -> Boolean)? = null
    private var budgetActionRetryInFlight = false
    private var suppressBudgetActionFeedback = false
    private val currentBudgetActionMessageOwner: AppRouteEntry?
        get() = if (suppressBudgetActionFeedback) null else budgetActionFeedbackState.currentRouteEntry

    var budgetActionMessage: String?
        get() = budgetActionFeedbackState.currentRouteEntry?.let { budgetActionFeedbackState.messageFor(it)?.text }
        private set(value) {
            if (value == null) {
                budgetActionFeedbackState.clear(currentBudgetActionMessageOwner)
                budgetActionRetryAction = null
            } else {
                budgetActionRetryAction = null
                budgetActionFeedbackState.show(value, InlineMessageKind.ERROR, currentBudgetActionMessageOwner)
            }
        }

    val budgetActionMessageKind: InlineMessageKind
        get() {
            val routeEntry = budgetActionFeedbackState.currentRouteEntry
            return routeEntry?.let { budgetActionFeedbackState.messageFor(it)?.kind } ?: InlineMessageKind.ERROR
        }

    val budgetActionMessageActionLabel: String?
        get() {
            val routeEntry = budgetActionFeedbackState.currentRouteEntry
            return routeEntry?.let { budgetActionFeedbackState.messageFor(it)?.actionLabel }
        }

    fun retryBudgetAction() {
        if (isPerformingBudgetAction || budgetActionRetryInFlight) return
        val retry = budgetActionRetryAction ?: return
        budgetActionRetryInFlight = true
        sessionScope.launch {
            try {
                retry()
            } finally {
                budgetActionRetryInFlight = false
            }
        }
    }

    var budgetActionWasAppliedButRefreshFailed by mutableStateOf(false)
        private set

    private var budgetActionShouldRetry by mutableStateOf(false)
    private var lastBudgetActionHttpStatus: Int? = null
    private var lastBudgetActionErrorCode: String? = null

    private var budgetReorderQueueState by mutableStateOf(BudgetReorderQueueState())
    var isPersistingBudgetMutation by mutableStateOf(false)
        private set
    var isCorrectingBudgetMutation by mutableStateOf(false)
        private set
    var budgetMutationCorrectionMessage by mutableStateOf<String?>(null)
        private set
    private var budgetReorderQueue: BudgetReorderQueueState
        get() = budgetReorderQueueState
        set(value) {
            budgetReorderQueueState = value
            persistBudgetReorderQueue()
        }
    private var budgetReorderRetrySignal by mutableStateOf(0)
    private var scheduleActionRetrySignal by mutableStateOf(0)
    private val scheduleActionQueueMutex = Mutex()
    private val scheduleActionEncryptionPasswords = mutableMapOf<String, String>()
    private var scheduleActionQueueLoaded = false
    var pendingScheduleActions by mutableStateOf<List<OutboxOperationEntity>>(emptyList())
        private set

    fun pendingScheduleActionFor(
        budgetId: String,
        scheduleId: String,
    ): OutboxOperationEntity? = pendingScheduleActions.firstOrNull { it.budgetId == budgetId && it.recordId == scheduleId }

    fun scheduleActionStatusMessage(operation: OutboxOperationEntity): String {
        val actionLabel = if (operation.action == "skip") "Skip" else "Post"
        return when (operation.status) {
            "queued" ->
                if (operation.lastErrorCode in setOf("invalid_device_credential", "sign_in_required", "sign_in_expired")) {
                    "Sign in again to send this scheduled action to Actual."
                } else {
                    "$actionLabel saved on this device. Waiting for a connection to Tandem."
                }
            "sending" -> "$actionLabel is being sent to Actual."
            "uncertain" -> "Tandem couldn’t confirm this $actionLabel. Check Actual, then check the status here."
            "conflict" -> "The schedule changed in Actual. Refresh it before choosing an action."
            "rejected" -> "Actual rejected this $actionLabel. Check the schedule in Actual."
            else -> ""
        }
    }

    fun scheduleActionCanBeReplaced(
        operation: OutboxOperationEntity,
        currentNextDate: String,
    ): Boolean {
        if (operation.status != "conflict" || operation.lastErrorCode != "schedule_changed") return false
        val pendingDate =
            runCatching {
                Json
                    .parseToJsonElement(operation.patchJson)
                    .jsonObject["expectedNextDate"]
                    ?.jsonPrimitive
                    ?.contentOrNull
            }.getOrNull()
        return !pendingDate.isNullOrBlank() && pendingDate != currentNextDate
    }

    fun retryScheduleActionStatus(operationId: String) {
        sessionScope.launch {
            val operation = repository?.findWrite(operationId) ?: return@launch
            if (operation.recordType != "schedule-action" || operation.status != "uncertain") return@launch
            runCatching { repository.retryScheduleActionCheck(operationId) }
            refreshScheduleActionQueue(operation.budgetId)
            scheduleActionRetrySignal += 1
        }
    }

    private suspend fun restoreScheduleActionQueue(): Boolean =
        scheduleActionQueueMutex.withLock {
            if (scheduleActionQueueLoaded) return@withLock true
            val localRepository = repository
            if (localRepository == null) {
                scheduleActionQueueLoaded = true
                return@withLock true
            }
            return@withLock try {
                localRepository.recoverInterruptedWrites()
                pendingScheduleActions = localRepository.readScheduleActionQueue()
                scheduleActionQueueLoaded = true
                true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
        }

    private suspend fun refreshScheduleActionQueue(budgetId: String? = null) {
        val localRepository = repository ?: return
        scheduleActionQueueMutex.withLock {
            pendingScheduleActions = localRepository.readScheduleActionQueue()
            scheduleActionQueueLoaded = true
            if (!budgetId.isNullOrBlank()) pendingWrites = localRepository.readPendingWrites(budgetId)
        }
    }

    fun retryQueuedScheduleActions() {
        scheduleActionRetrySignal += 1
    }

    /** Sends durable Post/Skip requests after a connection is available. */
    suspend fun processQueuedScheduleActions() {
        if (!restoreScheduleActionQueue()) {
            while (!restoreScheduleActionQueue()) delay(1_000)
        }
        var observedRetrySignal = scheduleActionRetrySignal
        var pauseUntilRetrySignal = false
        while (true) {
            if (pauseUntilRetrySignal) {
                observedRetrySignal =
                    withTimeoutOrNull(SCHEDULE_ACTION_RETRY_INTERVAL_MILLIS) {
                        snapshotFlow { scheduleActionRetrySignal }.first { it != observedRetrySignal }
                    } ?: scheduleActionRetrySignal
                pauseUntilRetrySignal = false
            }
            val request = pendingScheduleActions.firstOrNull { it.status == "queued" }
            if (request == null) {
                observedRetrySignal = snapshotFlow { scheduleActionRetrySignal }.first { it != observedRetrySignal }
                refreshScheduleActionQueue()
                continue
            }
            snapshotFlow {
                !isSigningOut && authenticatedDevice != null && !isSyncing && !isPerformingBudgetAction
            }.first { it }
            if (!isCurrentIdentityCacheOwner()) {
                observedRetrySignal = snapshotFlow { scheduleActionRetrySignal }.first { it != observedRetrySignal }
                continue
            }
            val payload = runCatching { Json.parseToJsonElement(request.patchJson).jsonObject }.getOrNull()
            val expectedOwner = payload?.get("cacheOwner")?.jsonPrimitive?.contentOrNull
            val activeOwner = activeBudgetCacheOwner()
            if (payload == null || expectedOwner.isNullOrBlank() || expectedOwner != activeOwner) {
                observedRetrySignal = snapshotFlow { scheduleActionRetrySignal }.first { it != observedRetrySignal }
                continue
            }
            val encryptionPassword = scheduleActionEncryptionPasswords[request.budgetId]
            val requiresPassword = payload["encrypted"]?.jsonPrimitive?.booleanOrNull == true
            if (requiresPassword && encryptionPassword.isNullOrBlank()) {
                observedRetrySignal = snapshotFlow { scheduleActionRetrySignal }.first { it != observedRetrySignal }
                continue
            }
            val expectedNextDate = payload["expectedNextDate"]?.jsonPrimitive?.contentOrNull
            if (expectedNextDate.isNullOrBlank() || request.action !in setOf("post", "skip")) {
                repository?.markScheduleActionRejected(request.operationId, "invalid-queued-action")
                refreshScheduleActionQueue(request.budgetId)
                continue
            }
            val localRepository = repository ?: continue
            if (!localRepository.beginScheduleAction(request.operationId)) {
                refreshScheduleActionQueue(request.budgetId)
                continue
            }
            refreshScheduleActionQueue(request.budgetId)
            isPerformingBudgetAction = true
            try {
                apiClient.withExclusiveSession {
                    apiClient.manageBudgetSchedule(
                        budgetId = request.budgetId,
                        scheduleId = request.recordId,
                        action = request.action,
                        name = null,
                        encryptionPassword = encryptionPassword,
                        expectedNextDate = expectedNextDate,
                        idempotencyKeyOverride = request.operationId,
                    )
                }
                localRepository.completeScheduleAction(request.operationId)
                refreshScheduleActionQueue(request.budgetId)
                val scheduleRoute = budgetActionFeedbackState.currentRouteEntry
                if (scheduleRoute?.route == AppRoute.SCHEDULES) {
                    val confirmationMessage =
                        if (request.action == "skip") {
                            "Next date skipped in Actual."
                        } else {
                            "Schedule posted in Actual."
                        }
                    setBudgetActionMessage(confirmationMessage, InlineMessageKind.SUCCESS, scheduleRoute)
                }
                withoutBudgetActionFeedback {
                    synchronize(request.budgetId, encryptionPassword, retryQueuedBudgetMutations = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: TandemApiException) {
                when {
                    error.errorCode == "schedule_changed" -> {
                        localRepository.markScheduleActionConflict(request.operationId, "schedule_changed")
                        refreshScheduleActionQueue(request.budgetId)
                    }
                    error.errorCode in setOf("invalid_device_credential", "sign_in_required", "sign_in_expired") -> {
                        localRepository.returnScheduleActionToQueue(request.operationId, error.errorCode)
                        refreshScheduleActionQueue(request.budgetId)
                        setBudgetActionMessage(
                            "Sign in again to send this scheduled action to Actual.",
                            InlineMessageKind.INFO,
                        )
                        observedRetrySignal = scheduleActionRetrySignal
                        pauseUntilRetrySignal = true
                    }
                    error.errorCode in setOf("operation_uncertain", "operation_in_progress", "operation_result_unavailable") -> {
                        localRepository.markScheduleActionUncertain(request.operationId, error.errorCode)
                        refreshScheduleActionQueue(request.budgetId)
                    }
                    shouldKeepBudgetMutationQueued(error.httpStatus) -> {
                        localRepository.returnScheduleActionToQueue(request.operationId, error.errorCode)
                        isServerReachable = error.indicatesTandemReachable()
                        refreshScheduleActionQueue(request.budgetId)
                        setBudgetActionMessage(
                            "Saved on this device. Waiting for a connection to Tandem.",
                            InlineMessageKind.INFO,
                            actionLabel = "Try again",
                            retryAction = {
                                retryQueuedScheduleActions()
                                true
                            },
                        )
                        observedRetrySignal = scheduleActionRetrySignal
                        pauseUntilRetrySignal = true
                    }
                    else -> {
                        localRepository.markScheduleActionRejected(request.operationId, error.errorCode)
                        refreshScheduleActionQueue(request.budgetId)
                    }
                }
            } catch (error: Exception) {
                localRepository.returnScheduleActionToQueue(request.operationId, "network-error")
                isServerReachable = false
                refreshScheduleActionQueue(request.budgetId)
                setBudgetActionMessage(
                    "Saved on this device. Waiting for a connection to Tandem.",
                    InlineMessageKind.INFO,
                    actionLabel = "Try again",
                    retryAction = {
                        retryQueuedScheduleActions()
                        true
                    },
                )
                observedRetrySignal = scheduleActionRetrySignal
                pauseUntilRetrySignal = true
            } finally {
                isPerformingBudgetAction = false
            }
            observedRetrySignal = scheduleActionRetrySignal
        }
    }

    private suspend fun activeBudgetCacheOwner(): String? {
        val device = authenticatedDevice ?: return null
        val serverUrl = apiClient.currentServerUrl ?: return null
        return budgetCacheOwner(serverUrl, device.principalId)
    }

    internal suspend fun restoreBudgetReorderQueue(): Boolean =
        budgetQueueRestoreMutex.withLock {
            if (budgetQueueLoaded) return@withLock true
            val persistence = budgetMutationQueuePersistence
            if (persistence == null) {
                budgetQueueLoaded = true
                return@withLock true
            }

            try {
                val pendingBeforeRestore = budgetReorderQueueState.moves
                val restoredFromRoom = persistence.read()
                val hasStoredRoomQueue = restoredFromRoom.items.isNotEmpty() || restoredFromRoom.metadataJson != null
                val restored =
                    if (hasStoredRoomQueue) {
                        val metadata = decodeBudgetReorderQueue(restoredFromRoom.metadataJson)
                        val orderedItems =
                            orderBudgetMutationQueueItems(
                                items = restoredFromRoom.items,
                                operationOrder = decodeBudgetReorderQueueOperationOrder(restoredFromRoom.metadataJson),
                                operationId = OutboxOperationEntity::operationId,
                                createdAtEpochMs = OutboxOperationEntity::createdAtEpochMs,
                            )
                        val moves =
                            orderedItems.map { operation ->
                                decodeBudgetReorderQueue(
                                    operation.patchJson,
                                ).moves.singleOrNull()?.copy(operationId = operation.operationId)
                                    ?: error("A saved budget change could not be read.")
                            }
                        moves.fold(BudgetReorderQueueState()) { current, move -> current.enqueue(move) }.copy(
                            categoryGroupIdAliases = metadata.categoryGroupIdAliases,
                            categoryIdAliases = metadata.categoryIdAliases,
                            waitingForConnection =
                                moves.isNotEmpty() &&
                                    (
                                        metadata.waitingForConnection ||
                                            restoredFromRoom.items.any { it.status in setOf("uncertain", "conflict") }
                                    ),
                        )
                    } else {
                        val saved = preferencesStore?.read()?.get(AppPreferencesStore.PENDING_BUDGET_REORDERS_KEY)
                        decodeBudgetReorderQueue(saved)
                    }
                val restoredState =
                    pendingBeforeRestore.fold(restored) { current, move ->
                        if (move in current.moves) current else current.enqueue(move)
                    }
                val hasUnpersistedMoves =
                    restoredState.moves.any { move ->
                        restoredFromRoom.items.none { it.operationId == move.operationId }
                    }
                if (hasUnpersistedMoves || (!hasStoredRoomQueue && restoredState.moves.isNotEmpty())) {
                    persistBudgetReorderQueueNow(restoredState)
                    preferencesStore?.putValue(AppPreferencesStore.PENDING_BUDGET_REORDERS_KEY, "")
                }
                budgetReorderQueueState = restoredState
                budgetQueueLoaded = true
                if (budgetActionMessage == BUDGET_QUEUE_RESTORE_ERROR) clearBudgetActionMessage()
                true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                setBudgetActionMessage(
                    text = BUDGET_QUEUE_RESTORE_ERROR,
                    actionLabel = "Try again",
                    retryAction = {
                        val restored = restoreBudgetReorderQueue()
                        if (restored) clearBudgetActionMessage()
                        restored
                    },
                )
                false
            }
        }

    private fun persistBudgetReorderQueue() {
        if (isSigningOut) return
        if (!budgetQueueLoaded) return
        if (budgetMutationQueuePersistence == null) return
        sessionScope.launch {
            persistCurrentBudgetReorderQueue()
        }
    }

    private suspend fun persistCurrentBudgetReorderQueue(supersededOperationIds: Set<String> = emptySet()) {
        budgetQueuePersistenceMutex.withLock {
            persistBudgetReorderQueueNow(budgetReorderQueueState, supersededOperationIds)
        }
    }

    private suspend fun persistBudgetReorderQueueNow(
        state: BudgetReorderQueueState,
        supersededOperationIds: Set<String> = emptySet(),
    ) {
        val persistence = budgetMutationQueuePersistence ?: return
        val persistedAt = nowEpochMillis()
        val items =
            state.moves.mapIndexed { index, move ->
                LocalBudgetMutationOutboxItem(
                    operationId = move.operationId,
                    budgetId = move.budgetId,
                    action = move.kind.name,
                    recordId = move.itemId,
                    patchJson = encodeBudgetReorderQueue(BudgetReorderQueueState().enqueue(move)),
                    createdAtEpochMs = persistedAt + index,
                )
            }
        persistence.replace(items, encodeBudgetReorderQueueMetadata(state), supersededOperationIds)
        runCatching { preferencesStore?.putValue(AppPreferencesStore.PENDING_BUDGET_REORDERS_KEY, "") }
    }

    private suspend fun persistBudgetReorderQueueMutation(transform: (BudgetReorderQueueState) -> BudgetReorderQueueState): Boolean {
        if (isPersistingBudgetMutation) {
            budgetActionMessage = "Saving a category change on this device. Try again in a moment."
            return false
        }
        isPersistingBudgetMutation = true
        try {
            if (!restoreBudgetReorderQueue()) return false
            if (budgetMutationQueuePersistence == null) {
                budgetActionMessage = "Tandem can’t save this change on this device. Restart the app and try again."
                return false
            }
            return budgetQueuePersistenceMutex.withLock {
                val next = transform(budgetReorderQueueState)
                try {
                    persistBudgetReorderQueueNow(next)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    setBudgetActionMessage(
                        text = "Couldn’t save this change on this device.",
                        actionLabel = "Try again",
                        retryAction = { persistBudgetReorderQueueMutation(transform) },
                    )
                    return@withLock false
                }
                budgetReorderQueueState = next
                budgetActionMessage = null
                true
            }
        } finally {
            isPersistingBudgetMutation = false
        }
    }

    fun categoryVisibilityOverridesFor(budgetId: String): Map<String, Boolean> = budgetReorderQueue.categoryVisibilityOverridesFor(budgetId)

    fun categoryGroupNameOverridesFor(budgetId: String): Map<String, String> = budgetReorderQueue.categoryGroupNameOverridesFor(budgetId)

    fun categoryNameOverridesFor(budgetId: String): Map<String, String> = budgetReorderQueue.categoryNameOverridesFor(budgetId)

    fun categoryGroupIdOverridesFor(budgetId: String): Map<String, String> = budgetReorderQueue.categoryGroupIdOverridesFor(budgetId)

    fun categoryGroupVisibilityOverridesFor(budgetId: String): Map<String, Boolean> =
        budgetReorderQueue.categoryGroupVisibilityOverridesFor(budgetId)

    fun deletedCategoryIdsFor(budgetId: String): Set<String> = budgetReorderQueue.deletedCategoryIdsFor(budgetId)

    fun pendingCategoryGroupCreatesFor(budgetId: String): List<LiveCategoryGroupSummary> =
        budgetReorderQueue.pendingCategoryGroupCreatesFor(budgetId)

    fun pendingCategoryCreatesFor(budgetId: String) = budgetReorderQueue.pendingCategoryCreatesFor(budgetId)

    fun resolveCategoryGroupId(
        budgetId: String,
        groupId: String,
    ): String = budgetReorderQueue.resolveCategoryGroupId(budgetId, groupId)

    fun retryQueuedBudgetReorders(
        budgetId: String? = null,
        encryptionPassword: String? = null,
    ) {
        if (!budgetId.isNullOrBlank() && !encryptionPassword.isNullOrBlank()) {
            budgetReorderQueue =
                budgetReorderQueue.copy(
                    moves =
                        budgetReorderQueue.moves.map { move ->
                            if (move.budgetId == budgetId && move.requiresEncryptionPassword) {
                                move.copy(encryptionPassword = encryptionPassword)
                            } else {
                                move
                            }
                        },
                )
        }
        budgetReorderRetrySignal += 1
    }

    private fun hasPendingCategoryCreate(budgetId: String): Boolean =
        budgetReorderQueue.moves.any {
            it.budgetId == budgetId && it.kind == BudgetReorderKind.CREATE_CATEGORY
        }

    fun budgetReorderMovesFor(
        budgetId: String,
        scopeId: String,
    ): List<QueuedBudgetReorder> = budgetReorderQueue.movesFor(budgetId, scopeId)

    fun budgetReorderStatus(
        budgetId: String,
        scopeId: String,
    ): BudgetReorderStatus = budgetReorderQueue.statusFor(budgetId, scopeId)

    fun pendingBudgetMutationCount(budgetId: String): Int = budgetReorderQueue.moves.count { it.budgetId == budgetId }

    fun pendingBudgetMutationsFor(budgetId: String): List<QueuedBudgetReorder> = budgetReorderQueue.moves.filter { it.budgetId == budgetId }

    fun budgetMutationReviewsFor(budgetId: String): List<OutboxOperationEntity> =
        pendingWrites.filter {
            it.budgetId == budgetId &&
                it.recordType == "budget-mutation" &&
                it.status in setOf("uncertain", "conflict", "rejected")
        }

    fun retryBudgetMutation(operationId: String) {
        if (isCorrectingBudgetMutation) return
        sessionScope.launch {
            val localRepository = repository ?: return@launch
            val operation = pendingWrites.firstOrNull { it.operationId == operationId } ?: return@launch
            val request =
                decodeBudgetReorderQueue(operation.patchJson)
                    .moves
                    .singleOrNull()
                    ?: return@launch

            if (budgetMutationNeedsRefresh(operation.lastErrorCode)) {
                val refreshed =
                    synchronize(
                        operation.budgetId,
                        request.encryptionPassword,
                        retryQueuedBudgetMutations = false,
                    )
                if (refreshed == null) return@launch
                runCatching { localRepository.completeBudgetMutationAfterConfirmation(operation.operationId) }
                    .getOrNull() ?: return@launch
                if (budgetReorderQueue.moves.none { it.operationId == operation.operationId }) {
                    budgetReorderQueue = budgetReorderQueue.enqueue(request.copy(operationId = operation.operationId))
                }
                budgetReorderQueue = completeAppliedBudgetMutationAfterRefresh(request.copy(operationId = operation.operationId))
                persistCurrentBudgetReorderQueue()
                pendingWrites = localRepository.readPendingWrites(operation.budgetId)
                retryQueuedBudgetReorders()
                return@launch
            }

            if (budgetMutationRequiresActualCheck(operation.lastErrorCode)) {
                val refreshed =
                    synchronize(
                        operation.budgetId,
                        request.encryptionPassword,
                        retryQueuedBudgetMutations = false,
                    )
                if (refreshed != null) pendingWrites = localRepository.readPendingWrites(operation.budgetId)
                return@launch
            }

            if (budgetMutationRetryNeedsNewKey(operation.lastErrorCode)) {
                if (operation.status !in setOf("conflict", "rejected")) return@launch
                val refreshed =
                    synchronize(
                        operation.budgetId,
                        request.encryptionPassword,
                        retryQueuedBudgetMutations = false,
                    )
                if (refreshed == null) return@launch
                val replacementOperationId = Uuid.random().toString()
                val replacement =
                    budgetMutationRequestForRetry(
                        request,
                        operation.lastErrorCode,
                        replacementOperationId,
                    ) ?: return@launch
                val nextQueue =
                    if (budgetReorderQueue.moves.any { it.operationId == operation.operationId }) {
                        budgetReorderQueue.replaceOperationId(operation.operationId, replacementOperationId)
                    } else {
                        budgetReorderQueue.enqueue(replacement)
                    }
                budgetQueuePersistenceMutex.withLock {
                    persistBudgetReorderQueueNow(nextQueue, setOf(operation.operationId))
                    budgetReorderQueue = nextQueue
                }
            } else {
                val retryRequest =
                    budgetMutationRequestForRetry(
                        request,
                        operation.lastErrorCode,
                        operation.operationId,
                    ) ?: return@launch
                val retried =
                    runCatching { localRepository.retryBudgetMutationAfterReview(operationId) }
                        .getOrNull() ?: return@launch
                val resumedRequest = retryRequest.copy(operationId = retried.operationId)
                if (budgetReorderQueue.moves.none { it.operationId == retried.operationId }) {
                    budgetReorderQueue = budgetReorderQueue.enqueue(resumedRequest)
                } else {
                    budgetReorderQueue = budgetReorderQueue.resumeAfterConnection()
                }
                persistCurrentBudgetReorderQueue()
            }
            pendingWrites = localRepository.readPendingWrites(operation.budgetId)
            retryQueuedBudgetReorders()
        }
    }

    suspend fun correctRejectedCategoryRename(
        budgetId: String,
        operationId: String,
        correctedName: String,
        encryptionPassword: String?,
    ): Boolean {
        budgetMutationCorrectionMessage = null
        if (refuseWriteInReadOnlyMode()) {
            budgetMutationCorrectionMessage = transactionWriteMessage ?: "Read-only mode is on."
            return false
        }
        if (isPersistingBudgetMutation || isCorrectingBudgetMutation) {
            budgetMutationCorrectionMessage = "Another category change is being saved. Try again in a moment."
            return false
        }
        val localRepository = repository
        if (localRepository == null) {
            budgetMutationCorrectionMessage = "Budget data isn’t available on this device. Reopen the budget and try again."
            return false
        }
        if (!restoreBudgetReorderQueue()) {
            budgetMutationCorrectionMessage = BUDGET_QUEUE_RESTORE_ERROR
            return false
        }
        val operation = localRepository.findWrite(operationId)
        if (
            operation == null ||
            operation.budgetId != budgetId ||
            operation.status != "rejected" ||
            operation.action != "RENAME_CATEGORY"
        ) {
            budgetMutationCorrectionMessage = "This rejected category rename is no longer available. Refresh Pending Changes."
            return false
        }
        val request = decodeBudgetReorderQueue(operation.patchJson).moves.singleOrNull()
        if (request == null || request.kind != BudgetReorderKind.RENAME_CATEGORY || request.itemId != operation.recordId) {
            budgetMutationCorrectionMessage = "Tandem couldn’t read this category rename. The rejected change is still available to review."
            return false
        }
        val cleanName = correctedName.trim()
        if (cleanName.isBlank() || cleanName.length > 100) {
            budgetMutationCorrectionMessage = "Enter a category name up to 100 characters."
            return false
        }
        if (cleanName == request.name) {
            budgetMutationCorrectionMessage = "Enter a different category name before trying again."
            return false
        }
        val password = encryptionPassword?.takeIf(String::isNotBlank) ?: request.encryptionPassword
        if (request.requiresEncryptionPassword && password.isNullOrBlank()) {
            budgetMutationCorrectionMessage = "Unlock this encrypted budget before trying again."
            return false
        }

        val replacement =
            request.copy(
                name = cleanName,
                operationId = Uuid.random().toString(),
                encryptionPassword = password,
                refreshBeforeSend = true,
            )
        isCorrectingBudgetMutation = true
        isPersistingBudgetMutation = true
        return try {
            budgetQueuePersistenceMutex.withLock {
                val next = budgetReorderQueueState.replaceRejectedOperation(operationId, replacement)
                persistBudgetReorderQueueNow(next, setOf(operationId))
                budgetReorderQueueState = next
            }
            runCatching { localRepository.readPendingWrites(budgetId) }.onSuccess { pendingWrites = it }
            retryQueuedBudgetReorders()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            budgetMutationCorrectionMessage =
                "Couldn’t save the corrected rename on this device. The rejected change is still available to review."
            false
        } finally {
            isPersistingBudgetMutation = false
            isCorrectingBudgetMutation = false
        }
    }

    fun clearBudgetMutationCorrectionMessage() {
        budgetMutationCorrectionMessage = null
    }

    private fun completeAppliedBudgetMutationAfterRefresh(request: QueuedBudgetReorder): BudgetReorderQueueState {
        val createdGroupId =
            if (request.kind == BudgetReorderKind.CREATE_CATEGORY_GROUP) {
                categoryGroupRecords.firstOrNull { it.displayName == request.name }?.recordId
            } else {
                null
            }
        if (createdGroupId != null) return budgetReorderQueue.completeCategoryGroupCreate(request, createdGroupId)

        val createdCategoryId =
            if (request.kind == BudgetReorderKind.CREATE_CATEGORY) {
                categoryRecords
                    .firstOrNull { record ->
                        record.displayName == request.name &&
                            record.recordBody()?.let { body ->
                                body.stringValue("group") == request.parentGroupId || body.stringValue("group_id") == request.parentGroupId
                            } == true
                    }?.recordId
            } else {
                null
            }
        if (createdCategoryId != null) return budgetReorderQueue.completeCategoryCreate(request, createdCategoryId)

        if (request.kind in setOf(BudgetReorderKind.CREATE_CATEGORY_GROUP, BudgetReorderKind.CREATE_CATEGORY)) {
            return budgetReorderQueue.awaitRefresh(request, "Saved in Actual. Refreshing the new budget item.")
        }
        return budgetReorderQueue.complete(request, refreshFailed = false, message = null)
    }

    fun budgetMutationWaitingForConnection(move: QueuedBudgetReorder): Boolean =
        budgetReorderQueue.waitingForConnection && budgetReorderQueue.moves.firstOrNull() == move

    fun budgetMutationsWaitingForConnection(budgetId: String): Boolean =
        budgetReorderQueue.waitingForConnection && budgetReorderQueue.moves.firstOrNull()?.budgetId == budgetId

    suspend fun enqueueBudgetReorder(
        budgetId: String,
        scopeId: String,
        kind: BudgetReorderKind,
        itemId: String,
        targetId: String?,
        desiredOrder: List<String>,
        encryptionPassword: String?,
    ): Boolean =
        persistBudgetReorderQueueMutation { queue ->
            queue.enqueueIfMissing(
                QueuedBudgetReorder(budgetId, scopeId, kind, itemId, targetId, desiredOrder, encryptionPassword),
            )
        }

    suspend fun enqueueBudgetCategoryVisibilityChange(
        budgetId: String,
        categoryId: String,
        groupId: String,
        hidden: Boolean,
        currentOrder: List<String>,
        encryptionPassword: String?,
    ): Boolean {
        budgetActionMessage = null
        val scopeId = groupId
        return persistBudgetReorderQueueMutation { queue ->
            queue.enqueueIfMissing(
                QueuedBudgetReorder(
                    budgetId = budgetId,
                    scopeId = scopeId,
                    kind = if (hidden) BudgetReorderKind.HIDE_CATEGORY else BudgetReorderKind.SHOW_CATEGORY,
                    itemId = categoryId,
                    targetId = null,
                    desiredOrder = currentOrder,
                    encryptionPassword = encryptionPassword,
                ),
            )
        }
    }

    suspend fun enqueueBudgetCategoryGroupCreate(
        budgetId: String,
        name: String,
        isIncome: Boolean,
        currentOrder: List<String>,
        encryptionPassword: String?,
    ): String? {
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.length > 100) {
            budgetActionMessage = "Enter a category group name up to 100 characters."
            return null
        }
        if (budgetId.isBlank()) {
            budgetActionMessage = "Open a budget before adding a category group."
            return null
        }
        if (refuseWriteInReadOnlyMode()) {
            budgetActionMessage = transactionWriteMessage
            return null
        }
        val localGroupId = "local-${Uuid.random()}"
        budgetActionMessage = null
        val persisted =
            persistBudgetReorderQueueMutation { queue ->
                queue.enqueueIfMissing(
                    QueuedBudgetReorder(
                        budgetId = budgetId,
                        scopeId = BUDGET_GROUP_ORDER_SCOPE,
                        kind = BudgetReorderKind.CREATE_CATEGORY_GROUP,
                        itemId = localGroupId,
                        targetId = null,
                        desiredOrder = currentOrder + localGroupId,
                        encryptionPassword = encryptionPassword,
                        name = cleanName,
                        isIncome = isIncome,
                    ),
                )
            }
        return localGroupId.takeIf { persisted }
    }

    suspend fun enqueueBudgetCategoryGroupRename(
        budgetId: String,
        groupId: String,
        name: String,
        currentOrder: List<String>,
        encryptionPassword: String?,
    ): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.length > 100) {
            budgetActionMessage = "Enter a category group name up to 100 characters."
            return false
        }
        if (budgetId.isBlank() || groupId.isBlank()) {
            budgetActionMessage = "Open a budget before renaming this category group."
            return false
        }
        if (refuseWriteInReadOnlyMode()) {
            budgetActionMessage = transactionWriteMessage
            return false
        }
        budgetActionMessage = null
        return persistBudgetReorderQueueMutation { queue ->
            queue.enqueueIfMissing(
                QueuedBudgetReorder(
                    budgetId = budgetId,
                    scopeId = BUDGET_GROUP_ORDER_SCOPE,
                    kind = BudgetReorderKind.RENAME_CATEGORY_GROUP,
                    itemId = groupId,
                    targetId = null,
                    desiredOrder = currentOrder,
                    encryptionPassword = encryptionPassword,
                    name = cleanName,
                ),
            )
        }
    }

    suspend fun enqueueBudgetCategoryCreate(
        budgetId: String,
        groupId: String,
        name: String,
        currentOrder: List<String>,
        encryptionPassword: String?,
    ): String? {
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.length > 100) {
            budgetActionMessage = "Enter a category name up to 100 characters."
            return null
        }
        if (budgetId.isBlank() || groupId.isBlank()) {
            budgetActionMessage = "Open a budget before adding a category."
            return null
        }
        if (refuseWriteInReadOnlyMode()) {
            budgetActionMessage = transactionWriteMessage
            return null
        }
        val localCategoryId = "local-category-${Uuid.random()}"
        budgetActionMessage = null
        val persisted =
            persistBudgetReorderQueueMutation { queue ->
                val resolvedGroupId = queue.resolveCategoryGroupId(budgetId, groupId)
                queue.enqueueIfMissing(
                    QueuedBudgetReorder(
                        budgetId = budgetId,
                        scopeId = groupId,
                        kind = BudgetReorderKind.CREATE_CATEGORY,
                        itemId = localCategoryId,
                        targetId = null,
                        desiredOrder = currentOrder + localCategoryId,
                        encryptionPassword = encryptionPassword,
                        name = cleanName,
                        parentGroupId = resolvedGroupId,
                    ),
                )
            }
        return localCategoryId.takeIf { persisted }
    }

    suspend fun enqueueBudgetCategoryRename(
        budgetId: String,
        categoryId: String,
        name: String,
        encryptionPassword: String?,
    ): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.length > 100) {
            budgetActionMessage = "Enter a category name up to 100 characters."
            return false
        }
        if (budgetId.isBlank() || categoryId.isBlank()) {
            budgetActionMessage = "Open a budget before renaming this category."
            return false
        }
        if (refuseWriteInReadOnlyMode()) {
            budgetActionMessage = transactionWriteMessage
            return false
        }
        budgetActionMessage = null
        return persistBudgetReorderQueueMutation { queue ->
            queue.enqueueIfMissing(
                QueuedBudgetReorder(
                    budgetId = budgetId,
                    scopeId = "category-$categoryId",
                    kind = BudgetReorderKind.RENAME_CATEGORY,
                    itemId = queue.resolveCategoryId(budgetId, categoryId),
                    targetId = null,
                    desiredOrder = emptyList(),
                    encryptionPassword = encryptionPassword,
                    name = cleanName,
                ),
            )
        }
    }

    suspend fun enqueueBudgetCategoryMoveToGroup(
        budgetId: String,
        categoryId: String,
        groupId: String,
        encryptionPassword: String?,
    ): Boolean {
        if (budgetId.isBlank() || categoryId.isBlank() || groupId.isBlank()) {
            budgetActionMessage = "Choose a category group."
            return false
        }
        if (refuseWriteInReadOnlyMode()) {
            budgetActionMessage = transactionWriteMessage
            return false
        }
        budgetActionMessage = null
        return persistBudgetReorderQueueMutation { queue ->
            queue.enqueueIfMissing(
                QueuedBudgetReorder(
                    budgetId = budgetId,
                    scopeId = "category-$categoryId",
                    kind = BudgetReorderKind.MOVE_CATEGORY_TO_GROUP,
                    itemId = queue.resolveCategoryId(budgetId, categoryId),
                    targetId = null,
                    desiredOrder = emptyList(),
                    encryptionPassword = encryptionPassword,
                    parentGroupId = queue.resolveCategoryGroupId(budgetId, groupId),
                ),
            )
        }
    }

    suspend fun enqueueBudgetCategoryDelete(
        budgetId: String,
        categoryId: String,
        transferCategoryId: String?,
        encryptionPassword: String?,
    ): Boolean {
        if (budgetId.isBlank() || categoryId.isBlank()) {
            budgetActionMessage = "Open a budget before deleting this category."
            return false
        }
        if (refuseWriteInReadOnlyMode()) {
            budgetActionMessage = transactionWriteMessage
            return false
        }
        budgetActionMessage = null
        return persistBudgetReorderQueueMutation { queue ->
            queue.enqueueIfMissing(
                QueuedBudgetReorder(
                    budgetId = budgetId,
                    scopeId = "category-$categoryId",
                    kind = BudgetReorderKind.DELETE_CATEGORY,
                    itemId = queue.resolveCategoryId(budgetId, categoryId),
                    targetId = transferCategoryId?.let { queue.resolveCategoryId(budgetId, it) },
                    desiredOrder = emptyList(),
                    encryptionPassword = encryptionPassword,
                ),
            )
        }
    }

    suspend fun enqueueBudgetCategoryGroupVisibilityChange(
        budgetId: String,
        groupId: String,
        hidden: Boolean,
        encryptionPassword: String?,
    ): Boolean {
        if (budgetId.isBlank() || groupId.isBlank() || refuseWriteInReadOnlyMode()) return false
        budgetActionMessage = null
        return persistBudgetReorderQueueMutation { queue ->
            queue.enqueueIfMissing(
                QueuedBudgetReorder(
                    budgetId = budgetId,
                    scopeId = BUDGET_GROUP_ORDER_SCOPE,
                    kind = if (hidden) BudgetReorderKind.HIDE_CATEGORY_GROUP else BudgetReorderKind.SHOW_CATEGORY_GROUP,
                    itemId = queue.resolveCategoryGroupId(budgetId, groupId),
                    targetId = null,
                    desiredOrder = emptyList(),
                    encryptionPassword = encryptionPassword,
                ),
            )
        }
    }

    fun clearBudgetReorderMessage(
        budgetId: String,
        scopeId: String,
    ) {
        budgetReorderQueue = budgetReorderQueue.clearMessage(budgetId, scopeId)
    }

    private suspend fun <T> withoutBudgetActionFeedback(action: suspend () -> T): T {
        val wasSuppressed = suppressBudgetActionFeedback
        suppressBudgetActionFeedback = true
        return try {
            action()
        } finally {
            suppressBudgetActionFeedback = wasSuppressed
        }
    }

    private fun clearBudgetReorderMessageAfterDelay(
        budgetId: String,
        scopeId: String,
        message: String,
    ) {
        sessionScope.launch {
            delay(6_000)
            val current = budgetReorderQueue.statusFor(budgetId, scopeId)
            if (current.message == message && !budgetReorderQueue.waitingForConnection) {
                budgetReorderQueue = budgetReorderQueue.clearMessage(budgetId, scopeId)
            }
        }
    }

    /** Runs outside editor screens so leaving a route cannot cancel unsent reorder operations. */
    suspend fun processQueuedBudgetReorders() {
        if (isSigningOut) return
        while (!isSigningOut && !restoreBudgetReorderQueue()) delay(1_000)
        if (isSigningOut) return
        var observedRetrySignal = budgetReorderRetrySignal
        while (true) {
            var request = snapshotFlow { budgetReorderQueue.moves.firstOrNull() }.first { it != null } ?: continue
            snapshotFlow { isCorrectingBudgetMutation }.first { !it }
            if (budgetReorderQueue.moves.firstOrNull() != request) continue
            persistCurrentBudgetReorderQueue()
            if (request.requiresEncryptionPassword && request.encryptionPassword.isNullOrBlank()) {
                observedRetrySignal = snapshotFlow { budgetReorderRetrySignal }.first { it != observedRetrySignal }
                continue
            }
            if (budgetReorderQueue.waitingForConnection) {
                observedRetrySignal = snapshotFlow { budgetReorderRetrySignal }.first { it != observedRetrySignal }
                budgetReorderQueue = budgetReorderQueue.resumeAfterConnection()
            } else {
                observedRetrySignal = budgetReorderRetrySignal
            }
            if (!isServerReachable || !apiClient.isAuthenticated) {
                budgetReorderQueue =
                    budgetReorderQueue.waitForConnection(
                        request,
                        "Saved on this device. Waiting for a connection to Tandem.",
                    )
                observedRetrySignal = snapshotFlow { budgetReorderRetrySignal }.first { it != observedRetrySignal }
                budgetReorderQueue = budgetReorderQueue.resumeAfterConnection()
                continue
            }
            snapshotFlow { isCorrectingBudgetMutation || isPerformingBudgetAction || isSyncing }.first { !it }
            if (budgetReorderQueue.moves.firstOrNull() != request) continue

            if (request.refreshBeforeSend) {
                val refreshed =
                    synchronize(
                        budgetId = request.budgetId,
                        encryptionPassword = request.encryptionPassword,
                        sendQueuedWrites = false,
                        retryQueuedBudgetMutations = false,
                    )
                if (refreshed == null) {
                    budgetReorderQueue =
                        budgetReorderQueue.waitForConnection(
                            request,
                            "Saved on this device. Waiting to refresh Actual before trying again.",
                        )
                    persistCurrentBudgetReorderQueue()
                    continue
                }
                val refreshedRequest = budgetReorderQueue.moves.firstOrNull { it.operationId == request.operationId } ?: continue
                val readyRequest = refreshedRequest.copy(refreshBeforeSend = false)
                try {
                    budgetQueuePersistenceMutex.withLock {
                        val next =
                            budgetReorderQueueState.copy(
                                moves =
                                    budgetReorderQueueState.moves.map { move ->
                                        if (move.operationId == request.operationId) readyRequest else move
                                    },
                            )
                        persistBudgetReorderQueueNow(next)
                        budgetReorderQueueState = next
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    budgetReorderQueue =
                        budgetReorderQueue.waitForConnection(
                            request,
                            "Saved on this device. Tandem couldn’t save the refreshed change before retrying.",
                        )
                    persistCurrentBudgetReorderQueue()
                    continue
                }
                request = readyRequest
            }

            var mutationStarted = repository?.beginBudgetMutation(request.operationId) == true
            if (!mutationStarted) {
                persistCurrentBudgetReorderQueue()
                mutationStarted = repository?.beginBudgetMutation(request.operationId) == true
            }
            if (!mutationStarted) {
                val stored = repository?.readPendingWrites(request.budgetId)?.firstOrNull { it.operationId == request.operationId }
                val stateMessage =
                    when (stored?.status) {
                        "uncertain", "conflict", "rejected" -> budgetMutationFailureMessage(stored.lastErrorCode)
                        "discarded", "committed" -> "This change is no longer pending."
                        else -> null
                    }
                if (stateMessage != null) {
                    budgetReorderQueue =
                        if (stored?.status in setOf("uncertain", "conflict")) {
                            budgetReorderQueue.holdForReview(request, stateMessage)
                        } else {
                            budgetReorderQueue.failHead(request, stateMessage)
                        }
                    persistCurrentBudgetReorderQueue()
                } else {
                    delay(100)
                }
                continue
            }

            var createdCategoryGroupId: String? = null
            var createdCategoryId: String? = null
            budgetActionWasAppliedButRefreshFailed = false
            budgetActionShouldRetry = false
            lastBudgetActionHttpStatus = null
            lastBudgetActionErrorCode = null
            val isRefreshOnlyRequest =
                request.refreshOnly ||
                    request.kind in
                    setOf(
                        BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE,
                        BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME,
                        BudgetReorderKind.REFRESH_CATEGORY_CREATE,
                        BudgetReorderKind.REFRESH_CATEGORY_RENAME,
                    )
            val moved =
                withoutBudgetActionFeedback {
                    if (isRefreshOnlyRequest) {
                        synchronize(request.budgetId, request.encryptionPassword) != null
                    } else {
                        when (request.kind) {
                            BudgetReorderKind.CATEGORY ->
                                manageBudgetCategory(
                                    request.budgetId,
                                    request.itemId,
                                    "move",
                                    request.targetId,
                                    request.encryptionPassword,
                                    idempotencyKey = request.operationId,
                                )
                            BudgetReorderKind.HIDE_CATEGORY ->
                                manageBudgetCategory(
                                    request.budgetId,
                                    request.itemId,
                                    "hide",
                                    null,
                                    request.encryptionPassword,
                                    idempotencyKey = request.operationId,
                                )
                            BudgetReorderKind.SHOW_CATEGORY ->
                                manageBudgetCategory(
                                    request.budgetId,
                                    request.itemId,
                                    "show",
                                    null,
                                    request.encryptionPassword,
                                    idempotencyKey = request.operationId,
                                )
                            BudgetReorderKind.CREATE_CATEGORY_GROUP -> {
                                createdCategoryGroupId =
                                    createBudgetCategoryGroup(
                                        request.budgetId,
                                        request.name.orEmpty(),
                                        request.isIncome,
                                        request.encryptionPassword,
                                        request.operationId,
                                    )
                                createdCategoryGroupId != null
                            }
                            BudgetReorderKind.RENAME_CATEGORY_GROUP ->
                                manageBudgetCategoryGroup(
                                    request.budgetId,
                                    request.itemId,
                                    "rename",
                                    request.name,
                                    null,
                                    request.encryptionPassword,
                                    request.operationId,
                                )
                            BudgetReorderKind.CATEGORY_GROUP ->
                                manageBudgetCategoryGroup(
                                    request.budgetId,
                                    request.itemId,
                                    "move",
                                    null,
                                    request.targetId,
                                    request.encryptionPassword,
                                    request.operationId,
                                )
                            BudgetReorderKind.CREATE_CATEGORY -> {
                                createdCategoryId =
                                    createBudgetCategory(
                                        request.budgetId,
                                        request.name.orEmpty(),
                                        request.parentGroupId.orEmpty(),
                                        request.encryptionPassword,
                                        request.operationId,
                                    )
                                createdCategoryId != null
                            }
                            BudgetReorderKind.RENAME_CATEGORY ->
                                renameBudgetCategory(
                                    request.budgetId,
                                    request.itemId,
                                    request.name.orEmpty(),
                                    request.encryptionPassword,
                                    request.operationId,
                                )
                            BudgetReorderKind.MOVE_CATEGORY_TO_GROUP ->
                                manageBudgetCategory(
                                    request.budgetId,
                                    request.itemId,
                                    "move",
                                    null,
                                    request.encryptionPassword,
                                    request.parentGroupId,
                                    request.operationId,
                                )
                            BudgetReorderKind.DELETE_CATEGORY ->
                                deleteBudgetCategory(
                                    request.budgetId,
                                    request.itemId,
                                    request.targetId,
                                    request.encryptionPassword,
                                    request.operationId,
                                )
                            BudgetReorderKind.HIDE_CATEGORY_GROUP ->
                                manageBudgetCategoryGroup(
                                    request.budgetId,
                                    request.itemId,
                                    "hide",
                                    null,
                                    null,
                                    request.encryptionPassword,
                                    request.operationId,
                                )
                            BudgetReorderKind.SHOW_CATEGORY_GROUP ->
                                manageBudgetCategoryGroup(
                                    request.budgetId,
                                    request.itemId,
                                    "show",
                                    null,
                                    null,
                                    request.encryptionPassword,
                                    request.operationId,
                                )
                            BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE,
                            BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME,
                            BudgetReorderKind.REFRESH_CATEGORY_CREATE,
                            BudgetReorderKind.REFRESH_CATEGORY_RENAME,
                            -> false
                        }
                    }
                }
            val createdCategoryGroupIdAfterAction = createdCategoryGroupId
            val createdCategoryIdAfterAction = createdCategoryId
            if (createdCategoryIdAfterAction != null) {
                repository?.remapPendingCategoryReferences(request.budgetId, request.itemId, createdCategoryIdAfterAction)
            }
            val refreshFailed = budgetActionWasAppliedButRefreshFailed
            val failureDisposition = budgetMutationFailureDisposition(lastBudgetActionErrorCode)
            val operationOutcomeUncertain =
                failureDisposition in
                    setOf(
                        BudgetMutationFailureDisposition.OPERATION_IN_PROGRESS,
                        BudgetMutationFailureDisposition.OPERATION_UNCERTAIN,
                    ) ||
                    lastBudgetActionHttpStatus == 408 ||
                    lastBudgetActionHttpStatus in 500..599
            val operationResultUnavailable = budgetMutationNeedsRefresh(lastBudgetActionErrorCode)
            val resultRefreshSucceeded =
                operationResultUnavailable &&
                    withoutBudgetActionFeedback {
                        synchronize(
                            request.budgetId,
                            request.encryptionPassword,
                            retryQueuedBudgetMutations = false,
                        ) != null
                    }
            if (
                shouldCompleteBudgetMutationAfterOutcome(
                    mutationApplied = moved,
                    refreshFailedAfterMutation = refreshFailed,
                    resultUnavailable = operationResultUnavailable,
                    resultRefreshSucceeded = resultRefreshSucceeded,
                )
            ) {
                repository?.completeBudgetMutation(request.operationId)
            } else if (operationOutcomeUncertain) {
                repository?.markBudgetMutationUncertain(
                    request.operationId,
                    lastBudgetActionErrorCode ?: "http-$lastBudgetActionHttpStatus",
                )
            } else if (isRefreshOnlyRequest) {
                repository?.returnBudgetMutationToQueue(request.operationId, "refresh-unavailable")
            } else if (lastBudgetActionErrorCode == "operation_rejected") {
                repository?.markBudgetMutationRejected(request.operationId, lastBudgetActionErrorCode!!)
            } else if (lastBudgetActionHttpStatus == 409) {
                repository?.markBudgetMutationConflict(request.operationId, lastBudgetActionErrorCode ?: "http-409")
            } else if (budgetActionShouldRetry) {
                repository?.returnBudgetMutationToQueue(request.operationId, lastBudgetActionErrorCode ?: "retry-later")
            } else {
                repository?.markBudgetMutationRejected(request.operationId, lastBudgetActionErrorCode ?: "local-validation")
            }
            if (request.kind == BudgetReorderKind.CREATE_CATEGORY_GROUP && refreshFailed && createdCategoryGroupIdAfterAction != null) {
                budgetReorderQueue =
                    budgetReorderQueue.awaitCategoryGroupRefresh(
                        request,
                        createdCategoryGroupIdAfterAction,
                        BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE,
                        "Created in Actual. Waiting for the budget to refresh.",
                    )
            } else if (request.kind == BudgetReorderKind.RENAME_CATEGORY_GROUP && refreshFailed) {
                budgetReorderQueue =
                    budgetReorderQueue.awaitCategoryGroupRefresh(
                        request,
                        request.itemId,
                        BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME,
                        "Renamed in Actual. Waiting for the budget to refresh.",
                    )
            } else if (request.kind == BudgetReorderKind.RENAME_CATEGORY && refreshFailed) {
                budgetReorderQueue = budgetReorderQueue.awaitRefresh(request, "Renamed in Actual. Waiting for the budget to refresh.")
            } else if (request.kind == BudgetReorderKind.CREATE_CATEGORY_GROUP && moved && createdCategoryGroupIdAfterAction != null) {
                budgetReorderQueue = budgetReorderQueue.completeCategoryGroupCreate(request, createdCategoryGroupIdAfterAction)
            } else if (request.kind == BudgetReorderKind.CREATE_CATEGORY && refreshFailed && createdCategoryIdAfterAction != null) {
                budgetReorderQueue =
                    budgetReorderQueue.awaitCategoryCreateRefresh(
                        request,
                        createdCategoryIdAfterAction,
                        "Created in Actual. Waiting for the budget to refresh.",
                    )
            } else if (request.kind == BudgetReorderKind.CREATE_CATEGORY && moved && createdCategoryIdAfterAction != null) {
                budgetReorderQueue = budgetReorderQueue.completeCategoryCreate(request, createdCategoryIdAfterAction)
            } else if (request.refreshOnly && request.kind == BudgetReorderKind.CREATE_CATEGORY_GROUP && moved) {
                val actualGroupId = categoryGroupRecords.firstOrNull { it.displayName == request.name }?.recordId
                budgetReorderQueue =
                    if (actualGroupId != null) {
                        budgetReorderQueue.completeCategoryGroupCreate(request, actualGroupId)
                    } else {
                        budgetReorderQueue.awaitRefresh(request, "Saved in Actual. Refreshing the new category group.")
                    }
            } else if (request.refreshOnly && request.kind == BudgetReorderKind.CREATE_CATEGORY && moved) {
                val actualCategoryId =
                    categoryRecords
                        .firstOrNull { record ->
                            record.displayName == request.name &&
                                record.recordBody()?.let { body ->
                                    body.stringValue("group") == request.parentGroupId ||
                                        body.stringValue("group_id") == request.parentGroupId
                                } == true
                        }?.recordId
                budgetReorderQueue =
                    if (actualCategoryId != null) {
                        budgetReorderQueue.completeCategoryCreate(request, actualCategoryId)
                    } else {
                        budgetReorderQueue.awaitRefresh(request, "Saved in Actual. Refreshing the new category.")
                    }
            } else if (refreshFailed) {
                // The mutation has already been accepted. Keep the optimistic
                // state and show its retryable refresh status in the queue;
                // the sync transport error belongs on the cloud indicator.
                budgetReorderQueue = budgetReorderQueue.awaitRefresh(request, "Saved in Actual. Waiting for the budget to refresh.")
            } else if (operationResultUnavailable) {
                budgetReorderQueue =
                    if (resultRefreshSucceeded) {
                        completeAppliedBudgetMutationAfterRefresh(request)
                    } else {
                        budgetReorderQueue.awaitRefresh(request, "Saved in Actual. Waiting for the budget to refresh.")
                    }
            } else if (operationOutcomeUncertain) {
                val message = budgetMutationFailureMessage(lastBudgetActionErrorCode)
                budgetReorderQueue =
                    budgetReorderQueue.holdForReview(
                        request,
                        message,
                    )
            } else if (lastBudgetActionHttpStatus == 409) {
                withoutBudgetActionFeedback {
                    synchronize(
                        request.budgetId,
                        request.encryptionPassword,
                        retryQueuedBudgetMutations = false,
                    )
                }
                val message = budgetMutationFailureMessage(lastBudgetActionErrorCode)
                budgetReorderQueue = budgetReorderQueue.failHead(request, message)
            } else if ((
                    request.refreshOnly ||
                        request.kind in
                        setOf(
                            BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE,
                            BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME,
                            BudgetReorderKind.REFRESH_CATEGORY_CREATE,
                            BudgetReorderKind.REFRESH_CATEGORY_RENAME,
                        )
                ) &&
                !moved
            ) {
                budgetReorderQueue =
                    budgetReorderQueue.waitForConnection(
                        request,
                        "Waiting for a connection to refresh this category group.",
                    )
            } else if (moved) {
                budgetReorderQueue = budgetReorderQueue.complete(request, refreshFailed, null)
            } else if (budgetActionShouldRetry || !isServerReachable || !apiClient.isAuthenticated) {
                budgetReorderQueue =
                    budgetReorderQueue.waitForConnection(
                        request,
                        "Saved on this device. Waiting for Tandem to recover.",
                    )
            } else {
                val failure = budgetMutationFailureMessage(request.kind, null)
                budgetReorderQueue = budgetReorderQueue.failHead(request, failure)
                clearBudgetReorderMessageAfterDelay(request.budgetId, request.scopeId, failure)
            }
            if (moved &&
                request.kind in setOf(BudgetReorderKind.CREATE_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_CREATE) &&
                !hasPendingCategoryCreate(request.budgetId)
            ) {
                synchronize(request.budgetId, request.encryptionPassword)
            }
            persistCurrentBudgetReorderQueue()
        }
    }

    private var readOnlyMode = false

    fun setReadOnlyMode(enabled: Boolean) {
        readOnlyMode = enabled
    }

    fun clearBudgetActionMessage() {
        budgetActionFeedbackState.clear(budgetActionFeedbackState.currentRouteEntry)
        budgetActionRetryAction = null
    }

    fun setBudgetActionRoute(routeEntry: AppRouteEntry) {
        if (budgetActionFeedbackState.currentRouteEntry != routeEntry) budgetActionRetryAction = null
        budgetActionFeedbackState.changeRoute(routeEntry)
    }

    private fun setBudgetActionMessage(
        text: String,
        kind: InlineMessageKind = InlineMessageKind.ERROR,
        ownerRoute: AppRouteEntry? = currentBudgetActionMessageOwner,
        actionLabel: String? = null,
        retryAction: (suspend () -> Boolean)? = null,
    ) {
        budgetActionRetryAction = retryAction
        budgetActionFeedbackState.show(text, kind, ownerRoute, actionLabel)
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

    private fun refuseWriteInReadOnlyMode(): Boolean {
        if (!readOnlyMode) return false
        transactionWriteMessage = "Read-only mode is on. Turn it off before changing this data."
        return true
    }

    var lastSyncSummary by mutableStateOf<BudgetSyncSummary?>(null)
        private set

    var localSyncState by mutableStateOf<BudgetSyncStateEntity?>(null)
        private set

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

    var payeeRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var categoryRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var categoryGroupRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var budgetMonthIds by mutableStateOf<List<String>>(emptyList())
        private set

    var selectedBudgetMonthRecord by mutableStateOf<BudgetRecordEntity?>(null)
        private set

    var accountRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var accountGroupRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var scheduleRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var ruleRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    val isAuthenticated: Boolean
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
            if (!restoreBudgetReorderQueue()) {
                signOutError = "Couldn’t verify pending changes. Try again before signing out."
                return false
            }
            if (budgetReorderQueue.moves.isNotEmpty()) {
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
                budgetQueuePersistenceMutex.withLock {
                    repository?.clearAllLocalData()
                    cacheTablesCleared = true
                    preferencesStore?.putValue(AppPreferencesStore.PENDING_BUDGET_REORDERS_KEY, "")
                    preferencesStore?.putValue(BUDGET_CACHE_OWNER_KEY, "")
                    budgetReorderQueueState = BudgetReorderQueueState()
                    budgetQueueLoaded = true
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
    ) {
        signInError = null
        signOutNotice = null
        deviceRevocationError = null
        isSigningIn = true
        cachedBudgets = emptyList()
        openIdLoginProofState.clear()
        val previousSession = apiClient.exportStoredSession()
        try {
            val signedInDevice = apiClient.signInWithPassword(serverUrl, password, deviceName)
            if (!acceptAuthenticatedIdentity(signedInDevice, previousSession)) return
            isServerReachable = true
            persistCurrentSession()
            loadBudgets()
            retryQueuedBudgetReorders()
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
    ) {
        signInError = null
        deviceRevocationError = null
        isSigningIn = true
        val previousSession = apiClient.exportStoredSession()
        val codeVerifier = openIdLoginProofState.takeVerifierForCallback(serverUrl, callbackState)
        if (codeVerifier == null) {
            signInError = "This sign-in is no longer active. Start sign-in again."
            isSigningIn = false
            return
        }
        cachedBudgets = emptyList()
        try {
            val signedInDevice = apiClient.completeActualOpenIdSignIn(serverUrl, code, codeVerifier)
            openIdLoginProofState.clear()
            if (!acceptAuthenticatedIdentity(signedInDevice, previousSession)) return
            isServerReachable = true
            persistCurrentSession()
            loadBudgets()
            retryQueuedBudgetReorders()
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
            retryQueuedBudgetReorders()
            retryQueuedScheduleActions()
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

    suspend fun synchronize(
        budgetId: String,
        encryptionPassword: String?,
        sendQueuedWrites: Boolean = true,
        reconcileUncertainOperationId: String? = null,
        retryQueuedBudgetMutations: Boolean = true,
    ): BudgetSyncSummary? {
        if (isSigningOut) return null
        if (!encryptionPassword.isNullOrBlank()) scheduleActionEncryptionPasswords[budgetId] = encryptionPassword
        if (!isCurrentIdentityCacheOwner()) {
            syncPresentation =
                syncPresentation.failed(
                    "These changes belong to another server or account. Sign in there before syncing them.",
                )
            return null
        }
        val budgetQueueRestored = restoreBudgetReorderQueue()
        if (budgetQueueRestored && retryQueuedBudgetMutations && !encryptionPassword.isNullOrBlank()) {
            retryQueuedBudgetReorders(budgetId, encryptionPassword)
        }
        syncComplete = false
        syncedTransactionCount = 0
        lastSyncSummary = null
        val coordinator = syncCoordinator
        val localRepository = repository
        if (coordinator == null || localRepository == null) {
            syncPresentation = syncPresentation.failed("Tandem can’t save this budget on your device. Restart the app and try again.")
            return null
        }

        // Keep an earlier error visible while retrying; clear it only when the retry succeeds.
        syncPresentation = syncPresentation.begin()
        try {
            val summary =
                apiClient.withExclusiveSession {
                    coordinator.synchronize(
                        budgetId,
                        encryptionPassword,
                        sendQueuedWrites && !hasPendingCategoryCreate(budgetId),
                        reconcileUncertainOperationId,
                    )
                }
            isServerReachable = true
            retryQueuedScheduleActions()
            lastSyncSummary = summary
            loadLocalRecords(budgetId)
            syncedTransactionCount =
                localRepository.countVisibleRecords(budgetId, "transaction").coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            syncComplete = true
            syncPresentation = syncPresentation.succeeded()
            if (budgetQueueRestored) {
                budgetReorderQueue = budgetReorderQueue.clearCompletedVisibilityOverrides(budgetId)
                if (retryQueuedBudgetMutations) retryQueuedBudgetReorders(budgetId, encryptionPassword)
            }
            if (activityTransactionPageBudgetId == budgetId) refreshActivityTransactionPages(budgetId)
            return summary
        } catch (error: CancellationException) {
            throw error
        } catch (error: BudgetSyncRemoteException) {
            isServerReachable = error.indicatesTandemReachable()
            syncPresentation =
                syncPresentation.failed(
                    apiFailureMessage(
                        errorCode = error.errorCode,
                        httpStatus = error.httpStatus,
                        context = ApiFailureContext.SYNC,
                        fallback = "Tandem couldn’t sync this budget. Try again shortly.",
                    ),
                )
            return null
        } catch (error: BudgetSyncProtocolException) {
            isServerReachable = true
            syncPresentation = syncPresentation.failed("Tandem couldn’t read this budget. Check the Tandem API address and try again.")
            return null
        } catch (error: Exception) {
            isServerReachable = false
            syncPresentation =
                syncPresentation.failed(
                    apiFailureMessage(
                        errorCode = "network-error",
                        httpStatus = null,
                        context = ApiFailureContext.SYNC,
                        fallback = "Tandem couldn’t sync this budget. Your saved copy is still available.",
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

    suspend fun runBankSync(
        budgetId: String,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(
            budgetId,
            encryptionPassword,
            "Couldn’t sync transactions from Actual. Check the bank connection and try again.",
        ) {
            apiClient.runBudgetBankSync(budgetId, encryptionPassword)
        }

    suspend fun createBudgetAccount(
        budgetId: String,
        name: String,
        offbudget: Boolean,
        accountGroupId: String?,
        encryptionPassword: String?,
    ): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            budgetActionMessage = "Enter an account name."
            return false
        }
        if (cleanName.length > 100) {
            budgetActionMessage = "Account names can be up to 100 characters."
            return false
        }
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t add this account. Try again.") {
            apiClient.createBudgetAccount(budgetId, cleanName, offbudget, accountGroupId, encryptionPassword)
        }
    }

    suspend fun closeBudgetAccount(
        budgetId: String,
        accountId: String,
        transferAccountId: String?,
        transferCategoryId: String?,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t close this account. Check its balance and try again.") {
            apiClient.closeBudgetAccount(
                budgetId,
                accountId,
                transferAccountId,
                transferCategoryId,
                encryptionPassword,
            )
        }

    suspend fun renameBudgetAccount(
        budgetId: String,
        accountId: String,
        name: String,
        encryptionPassword: String?,
    ): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            budgetActionMessage = "Enter an account name."
            return false
        }
        if (cleanName.length > 100) {
            budgetActionMessage = "Account names can be up to 100 characters."
            return false
        }
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t rename this account. Try again.") {
            apiClient.renameBudgetAccount(budgetId, accountId, cleanName, encryptionPassword)
        }
    }

    suspend fun reopenBudgetAccount(
        budgetId: String,
        accountId: String,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t reopen this account. Try again.") {
            apiClient.reopenBudgetAccount(budgetId, accountId, encryptionPassword)
        }

    suspend fun moveBudgetAccount(
        budgetId: String,
        accountId: String,
        targetAccountId: String?,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t reorder this account. Try again.") {
            apiClient.moveBudgetAccount(budgetId, accountId, targetAccountId, encryptionPassword)
        }

    suspend fun markTransactionsReconciled(
        budgetId: String,
        accountId: String,
        transactionIds: Set<String>,
        selectedTransactionIds: Set<String>,
    ): Boolean {
        transactionWriteMessage = null
        if (refuseWriteInReadOnlyMode()) return false
        val localRepository = repository
        if (localRepository == null || !isAuthenticated || budgetId.isBlank()) {
            transactionWriteMessage = "Sign in and open a budget before reconciling transactions."
            return false
        }
        val ids = transactionIds.filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) {
            transactionWriteMessage = "There are no unreconciled transactions to review."
            return false
        }
        val selectedIds = selectedTransactionIds.intersect(ids.toSet())
        if (isSavingTransaction) return false
        isSavingTransaction = true
        try {
            val requests = mutableListOf<LocalWriteRequest>()
            val recordsById =
                localRepository
                    .findRecordsByIds(budgetId, "transaction", ids)
                    .associateBy(BudgetRecordEntity::recordId)
            for (transactionId in ids) {
                val record = recordsById[transactionId]
                val baseJson = record?.remoteJson
                val revision = record?.remoteRevision
                if (
                    record == null ||
                    record.localDeleted ||
                    record.remoteDeleted ||
                    record.accountId != accountId ||
                    record.isParent ||
                    baseJson == null ||
                    revision.isNullOrBlank() ||
                    record.localJson != null
                ) {
                    transactionWriteMessage = "Sync pending edits before reconciling this account."
                    return false
                }
                val base = runCatching { Json.parseToJsonElement(baseJson).jsonObject }.getOrNull()
                if (base == null) {
                    transactionWriteMessage = "Couldn’t read this transaction. Sync the budget and try again."
                    return false
                }
                if (base.booleanValue("reconciled") == true) continue
                val shouldBeCleared = transactionId in selectedIds
                val currentlyCleared = base.booleanValue("cleared") == true
                if (currentlyCleared == shouldBeCleared && !shouldBeCleared) continue
                val patch =
                    buildJsonObject {
                        if (currentlyCleared != shouldBeCleared) put("cleared", shouldBeCleared)
                        if (shouldBeCleared) put("reconciled", true)
                    }
                val optimistic = JsonObject(base + patch)
                val operationId = Uuid.random().toString()
                requests +=
                    LocalWriteRequest(
                        operationId = operationId,
                        idempotencyKey = operationId,
                        budgetId = budgetId,
                        recordType = "transaction",
                        recordId = transactionId,
                        action = LocalWriteAction.UPDATE,
                        baseRevision = revision,
                        baseSnapshotJson = baseJson,
                        patchJson = patch.toString(),
                        optimisticJson = optimistic.toString(),
                        createdAtEpochMs = nowEpochMillis(),
                        optimisticProjection = optimistic.toProjection(),
                    )
            }
            if (requests.isEmpty()) {
                transactionWriteMessage = "No transaction changes to save."
                return true
            }
            localRepository.enqueueWrites(requests)
            transactionWriteMessage = "Reconciliation saved on this device. Sync when you’re ready."
            loadLocalRecords(budgetId)
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            transactionWriteMessage = "Couldn’t queue reconciliation changes. Try again."
            return false
        } finally {
            isSavingTransaction = false
        }
    }

    suspend fun renameBudgetCategory(
        budgetId: String,
        categoryId: String,
        name: String,
        encryptionPassword: String?,
        idempotencyKey: String? = null,
    ): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            budgetActionMessage = "Enter a category name."
            return false
        }
        if (cleanName.length > 100) {
            budgetActionMessage = "Category names can be up to 100 characters."
            return false
        }
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t rename this category. Try again.") {
            apiClient.renameBudgetCategory(budgetId, categoryId, cleanName, encryptionPassword, idempotencyKey)
        }
    }

    suspend fun createBudgetCategory(
        budgetId: String,
        name: String,
        categoryGroupId: String,
        encryptionPassword: String?,
        idempotencyKey: String? = null,
    ): String? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            budgetActionMessage = "Enter a category name."
            return null
        }
        if (cleanName.length > 100) {
            budgetActionMessage = "Category names can be up to 100 characters."
            return null
        }
        var categoryId: String? = null
        val created =
            performBudgetAction(budgetId, encryptionPassword, "Couldn’t create this category. Try again.") {
                categoryId = apiClient.createBudgetCategory(budgetId, categoryGroupId, cleanName, encryptionPassword, idempotencyKey)
            }
        return categoryId.takeIf { created || budgetActionWasAppliedButRefreshFailed }
    }

    suspend fun createBudgetCategoryGroup(
        budgetId: String,
        name: String,
        isIncome: Boolean,
        encryptionPassword: String?,
        idempotencyKey: String? = null,
    ): String? {
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.length > 100) {
            budgetActionMessage = "Enter a category group name up to 100 characters."
            return null
        }
        var groupId: String? = null
        val created =
            performBudgetAction(budgetId, encryptionPassword, "Couldn’t create this category group. Try again.") {
                groupId = apiClient.createBudgetCategoryGroup(budgetId, cleanName, isIncome, encryptionPassword, idempotencyKey)
            }
        return groupId.takeIf { created || budgetActionWasAppliedButRefreshFailed }
    }

    suspend fun manageBudgetCategoryGroup(
        budgetId: String,
        groupId: String,
        action: String,
        name: String?,
        targetId: String?,
        encryptionPassword: String?,
        idempotencyKey: String? = null,
    ): Boolean {
        if (action == "rename" && name.isNullOrBlank()) {
            budgetActionMessage = "Enter a category group name."
            return false
        }
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t update this category group. Try again.") {
            apiClient.manageBudgetCategoryGroup(budgetId, groupId, action, name?.trim(), targetId, encryptionPassword, idempotencyKey)
        }
    }

    suspend fun manageBudgetCategory(
        budgetId: String,
        categoryId: String,
        action: String,
        targetId: String?,
        encryptionPassword: String?,
        targetGroupId: String? = null,
        idempotencyKey: String? = null,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t update this category. Try again.") {
            apiClient.manageBudgetCategory(budgetId, categoryId, action, targetId, encryptionPassword, targetGroupId, idempotencyKey)
        }

    suspend fun createBudgetPayee(
        budgetId: String,
        name: String,
        encryptionPassword: String?,
    ): String? {
        val messageOwner = currentBudgetActionMessageOwner
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            budgetActionMessage = "Enter a payee name."
            return null
        }
        if (cleanName.length > 100) {
            budgetActionMessage = "Payee names can be up to 100 characters."
            return null
        }
        budgetActionMessage = null
        if (refuseWriteInReadOnlyMode()) {
            budgetActionMessage = transactionWriteMessage
            return null
        }
        if (budgetId.isBlank() || !apiClient.isAuthenticated) {
            budgetActionMessage = "Sign in and open a budget before changing it."
            return null
        }
        if (isPerformingBudgetAction) return null
        isPerformingBudgetAction = true
        try {
            val payeeId = apiClient.withExclusiveSession { apiClient.createBudgetPayee(budgetId, cleanName, encryptionPassword) }
            if (synchronize(budgetId, encryptionPassword) == null) {
                setBudgetActionMessage(
                    "Actual saved the payee, but Tandem couldn’t refresh the budget. Sync to load the latest data.",
                    InlineMessageKind.WARNING,
                    messageOwner,
                )
                return null
            }
            return payeeId
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            setBudgetActionMessage(error.userFacingMessage("Couldn’t add this payee. Try again."), ownerRoute = messageOwner)
            return null
        } catch (_: Exception) {
            setBudgetActionMessage("Couldn’t add this payee. Try again.", ownerRoute = messageOwner)
            return null
        } finally {
            isPerformingBudgetAction = false
        }
    }

    suspend fun setBudgetCategoryAmount(
        budgetId: String,
        categoryId: String,
        month: String,
        amountMinor: Long,
        encryptionPassword: String?,
    ): Boolean {
        if (amountMinor < 0L) {
            budgetActionMessage = "Assigned amounts can’t be negative."
            return false
        }
        if (amountMinor > 9_007_199_254_740_991L) {
            budgetActionMessage = "Enter a smaller assigned amount."
            return false
        }
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t update this assigned amount. Try again.") {
            apiClient.setBudgetCategoryAmount(budgetId, categoryId, month, amountMinor, encryptionPassword)
        }
    }

    suspend fun setBudgetCategoryCarryover(
        budgetId: String,
        categoryId: String,
        month: String,
        enabled: Boolean,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t update carryover. Try again.") {
            apiClient.setBudgetCategoryCarryover(budgetId, categoryId, month, enabled, encryptionPassword)
        }

    suspend fun copyBudgetMonth(
        budgetId: String,
        month: String,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t copy last month’s assignments. Try again.") {
            apiClient.copyBudgetMonth(budgetId, month, encryptionPassword)
        }

    suspend fun previewStatementImportTransactions(
        budgetId: String,
        accountId: String,
        importedIds: List<String>,
        encryptionPassword: String?,
    ): StatementImportPreview {
        if (budgetId.isBlank() || !apiClient.isAuthenticated) throw TandemApiException("sign_in_required")
        return apiClient.withExclusiveSession {
            apiClient.previewBudgetAccountStatementImport(budgetId, accountId, importedIds, encryptionPassword)
        }
    }

    suspend fun importStatementTransactions(
        budgetId: String,
        accountId: String,
        transactions: List<StatementImportItem>,
        encryptionPassword: String?,
    ): StatementImportSummary? {
        val messageOwner = currentBudgetActionMessageOwner
        budgetActionMessage = null
        if (refuseWriteInReadOnlyMode()) {
            budgetActionMessage = transactionWriteMessage
            return null
        }
        if (budgetId.isBlank() || !apiClient.isAuthenticated) {
            budgetActionMessage = "Sign in and open a budget before importing a statement."
            return null
        }
        if (isPerformingBudgetAction) return null
        isPerformingBudgetAction = true
        try {
            val result =
                apiClient.withExclusiveSession {
                    apiClient.importBudgetAccountTransactions(budgetId, accountId, transactions, encryptionPassword)
                }
            if (synchronize(budgetId, encryptionPassword) == null) {
                setBudgetActionMessage(
                    syncError
                        ?: "Actual imported the statement, but Tandem couldn’t refresh it. Sync to check the imported transactions.",
                    InlineMessageKind.WARNING,
                    messageOwner,
                )
            } else {
                setBudgetActionMessage(formatStatementImportSummary(result), InlineMessageKind.SUCCESS, messageOwner)
            }
            return result
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            setBudgetActionMessage(error.userFacingMessage("Couldn’t import this statement. Try again."), ownerRoute = messageOwner)
            return null
        } catch (_: Exception) {
            setBudgetActionMessage("Couldn’t import this statement. Try again.", ownerRoute = messageOwner)
            return null
        } finally {
            isPerformingBudgetAction = false
        }
    }

    suspend fun applyBudgetTemplate(
        budgetId: String,
        month: String,
        assignments: List<Pair<String, Long>>,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t apply this template. Try again.") {
            apiClient.applyBudgetTemplate(budgetId, month, assignments, encryptionPassword)
        }

    suspend fun holdBudgetForNextMonth(
        budgetId: String,
        month: String,
        amountMinor: Long,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t hold this money for next month. Try again.") {
            apiClient.holdBudgetForNextMonth(budgetId, month, amountMinor, encryptionPassword)
        }

    suspend fun resetBudgetHold(
        budgetId: String,
        month: String,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t reset the next-month hold. Try again.") {
            apiClient.resetBudgetHold(budgetId, month, encryptionPassword)
        }

    suspend fun moveBudgetMoney(
        budgetId: String,
        month: String,
        fromCategoryId: String,
        toCategoryId: String,
        amountMinor: Long,
        encryptionPassword: String?,
    ): Boolean {
        if (fromCategoryId == toCategoryId || amountMinor <= 0L) {
            budgetActionMessage = "Choose two categories and a positive amount."
            return false
        }
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t move money. Try again.") {
            apiClient.moveMoneyBetweenCategories(
                budgetId,
                month,
                fromCategoryId,
                toCategoryId,
                amountMinor,
                encryptionPassword,
            )
        }
    }

    suspend fun deleteBudgetCategory(
        budgetId: String,
        categoryId: String,
        transferCategoryId: String?,
        encryptionPassword: String?,
        idempotencyKey: String? = null,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t delete this category. Try again.") {
            apiClient.deleteBudgetCategory(budgetId, categoryId, transferCategoryId, encryptionPassword, idempotencyKey)
        }

    suspend fun manageBudgetSchedule(
        budgetId: String,
        scheduleId: String,
        action: String,
        name: String?,
        encryptionPassword: String?,
        fields: JsonObject? = null,
        expectedNextDate: String? = null,
    ): Boolean {
        val cleanName = name?.trim()
        if (action == "rename" && cleanName.isNullOrBlank()) {
            budgetActionMessage = "Enter a schedule name."
            return false
        }
        if (cleanName != null && cleanName.length > 100) {
            budgetActionMessage = "Schedule names can be up to 100 characters."
            return false
        }
        if (action in setOf("post", "skip") && expectedNextDate.isNullOrBlank()) {
            budgetActionMessage = "Refresh the schedule before posting or skipping it."
            return false
        }
        if (action in setOf("post", "skip")) {
            return enqueueScheduleAction(
                budgetId = budgetId,
                scheduleId = scheduleId,
                action = action,
                expectedNextDate = expectedNextDate!!,
                encryptionPassword = encryptionPassword,
            )
        }
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t update this schedule. Try again.") {
            apiClient.manageBudgetSchedule(
                budgetId,
                scheduleId,
                action,
                cleanName,
                encryptionPassword,
                fields,
                expectedNextDate,
            )
        }
    }

    private suspend fun enqueueScheduleAction(
        budgetId: String,
        scheduleId: String,
        action: String,
        expectedNextDate: String,
        encryptionPassword: String?,
    ): Boolean {
        val localRepository = repository
        if (refuseWriteInReadOnlyMode()) {
            budgetActionMessage = transactionWriteMessage
            return false
        }
        if (localRepository == null) {
            budgetActionMessage = "Tandem can’t save this action on this device."
            return false
        }
        if (budgetId.isBlank() || !apiClient.isAuthenticated) {
            budgetActionMessage = "Sign in and open a budget before changing it."
            return false
        }
        val cacheOwner = activeBudgetCacheOwner()
        if (cacheOwner.isNullOrBlank() || !isCurrentIdentityCacheOwner()) {
            budgetActionMessage = "Sign in to the server and account that own this budget before changing it."
            return false
        }
        if (!restoreScheduleActionQueue()) {
            budgetActionMessage = "Couldn’t load saved schedule actions. Try again."
            return false
        }
        val operationId = Uuid.random().toString()
        val payload =
            buildJsonObject {
                put("expectedNextDate", expectedNextDate)
                put("cacheOwner", cacheOwner)
                put("encrypted", !encryptionPassword.isNullOrBlank())
            }.toString()
        return try {
            val operation =
                localRepository.enqueueScheduleAction(
                    LocalScheduleActionOutboxItem(
                        operationId = operationId,
                        budgetId = budgetId,
                        scheduleId = scheduleId,
                        action = action,
                        payloadJson = payload,
                        createdAtEpochMs = nowEpochMillis(),
                    ),
                )
            if (!encryptionPassword.isNullOrBlank()) scheduleActionEncryptionPasswords[budgetId] = encryptionPassword
            refreshScheduleActionQueue(budgetId)
            budgetActionMessage = null
            scheduleActionRetrySignal += 1
            operation.status in setOf("queued", "sending", "committed")
        } catch (error: LocalOperationConflictException) {
            budgetActionMessage = "This schedule already has an action waiting to sync or review."
            false
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            budgetActionMessage = "Couldn’t save this action on this device. Try again."
            false
        }
    }

    suspend fun manageBudgetPayee(
        budgetId: String,
        payeeId: String,
        action: String,
        name: String?,
        targetPayeeId: String?,
        encryptionPassword: String?,
        categoryId: String? = null,
    ): Boolean {
        val cleanName = name?.trim()
        if (action == "rename" && cleanName.isNullOrBlank()) {
            budgetActionMessage = "Enter a payee name."
            return false
        }
        if (cleanName != null && cleanName.length > 100) {
            budgetActionMessage = "Payee names can be up to 100 characters."
            return false
        }
        if (action == "merge" && (targetPayeeId.isNullOrBlank() || targetPayeeId == payeeId)) {
            budgetActionMessage = "Choose another payee to merge into."
            return false
        }
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t update this payee. Try again.") {
            apiClient.manageBudgetPayee(
                budgetId,
                payeeId,
                action,
                cleanName,
                targetPayeeId,
                encryptionPassword,
                categoryId,
            )
        }
    }

    private suspend fun performBudgetAction(
        budgetId: String,
        encryptionPassword: String?,
        failureMessage: String,
        messageOwner: AppRouteEntry? = currentBudgetActionMessageOwner,
        action: suspend () -> Unit,
    ): Boolean {
        budgetActionMessage = null
        budgetActionWasAppliedButRefreshFailed = false
        budgetActionShouldRetry = false
        lastBudgetActionHttpStatus = null
        lastBudgetActionErrorCode = null
        if (refuseWriteInReadOnlyMode()) {
            transactionWriteMessage?.let { setBudgetActionMessage(it, ownerRoute = messageOwner) }
            return false
        }
        if (budgetId.isBlank() || !apiClient.isAuthenticated) {
            setBudgetActionMessage("Sign in and open a budget before changing it.", ownerRoute = messageOwner)
            return false
        }
        if (!isCurrentIdentityCacheOwner()) {
            setBudgetActionMessage(
                "Sign in to the server and account that own this budget before changing it.",
                ownerRoute = messageOwner,
            )
            return false
        }
        if (isPerformingBudgetAction) return false
        isPerformingBudgetAction = true
        var actionApplied = false
        try {
            apiClient.withExclusiveSession {
                action()
                actionApplied = true
            }
            if (synchronize(budgetId, encryptionPassword) == null) {
                budgetActionWasAppliedButRefreshFailed = true
                val refreshMessage =
                    syncError ?: "Actual was updated, but Tandem couldn’t refresh the budget. Sync to load the latest data."
                setBudgetActionMessage(
                    refreshMessage,
                    InlineMessageKind.WARNING,
                    messageOwner,
                    actionLabel = "Refresh",
                    retryAction = { retryBudgetActionRefresh(budgetId, encryptionPassword, messageOwner, refreshMessage) },
                )
                return false
            }
            budgetActionRetryAction = null
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            budgetActionShouldRetry = shouldKeepBudgetMutationQueued(error.httpStatus)
            lastBudgetActionHttpStatus = error.httpStatus
            lastBudgetActionErrorCode = error.errorCode
            isServerReachable = error.httpStatus != null && !budgetActionShouldRetry
            if (actionApplied) {
                budgetActionWasAppliedButRefreshFailed = true
                val refreshMessage = "Actual was updated, but Tandem couldn’t refresh the budget. Sync to load the latest data."
                setBudgetActionMessage(
                    refreshMessage,
                    InlineMessageKind.WARNING,
                    messageOwner,
                    actionLabel = "Refresh",
                    retryAction = { retryBudgetActionRefresh(budgetId, encryptionPassword, messageOwner, refreshMessage) },
                )
            } else if (budgetActionShouldRetry) {
                setBudgetActionMessage(
                    error.userFacingMessage(failureMessage),
                    ownerRoute = messageOwner,
                    actionLabel = "Try again",
                    retryAction = { performBudgetAction(budgetId, encryptionPassword, failureMessage, messageOwner, action) },
                )
            } else {
                // Network failures are handled by the persistent queue. Do not surface the
                // transport exception as if the category edit itself was rejected.
                if (!budgetActionShouldRetry && error.httpStatus != null) {
                    setBudgetActionMessage(error.userFacingMessage(failureMessage), ownerRoute = messageOwner)
                }
            }
            return false
        } catch (_: Exception) {
            isServerReachable = false
            budgetActionShouldRetry = true
            lastBudgetActionErrorCode = "network-error"
            if (actionApplied) {
                budgetActionWasAppliedButRefreshFailed = true
                val refreshMessage = "Actual was updated, but Tandem couldn’t refresh the budget. Sync to load the latest data."
                setBudgetActionMessage(
                    refreshMessage,
                    InlineMessageKind.WARNING,
                    messageOwner,
                    actionLabel = "Refresh",
                    retryAction = { retryBudgetActionRefresh(budgetId, encryptionPassword, messageOwner, refreshMessage) },
                )
            } else {
                setBudgetActionMessage(
                    failureMessage,
                    ownerRoute = messageOwner,
                    actionLabel = "Try again",
                    retryAction = { performBudgetAction(budgetId, encryptionPassword, failureMessage, messageOwner, action) },
                )
            }
            return false
        } finally {
            isPerformingBudgetAction = false
        }
    }

    private suspend fun retryBudgetActionRefresh(
        budgetId: String,
        encryptionPassword: String?,
        messageOwner: AppRouteEntry?,
        failureMessage: String,
    ): Boolean {
        if (synchronize(budgetId, encryptionPassword) == null) {
            setBudgetActionMessage(
                failureMessage,
                InlineMessageKind.WARNING,
                messageOwner,
                actionLabel = "Refresh",
                retryAction = { retryBudgetActionRefresh(budgetId, encryptionPassword, messageOwner, failureMessage) },
            )
            return false
        }
        budgetActionWasAppliedButRefreshFailed = false
        clearBudgetActionMessage()
        return true
    }

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
        if (updateDetails && categoryId != null && categoryRecords.none { it.recordId == categoryId }) {
            transactionWriteMessage = "Choose a category from this budget before saving."
            return false
        }
        val signedAmount = if (transactionType == "Income") enteredAmount else -enteredAmount
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
                            categoryId = categoryId,
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
        } catch (_: Exception) {
            transactionWriteMessage = "Tandem couldn’t save your edit. Check the budget and try again."
            loadLocalRecords(budgetId)
            return false
        } finally {
            isSavingTransaction = false
        }
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
                .filter { it.recordType == "transaction" && it.recordId == transactionId }
                .maxByOrNull { it.updatedAtEpochMs }
        if (pendingForRecord?.action == LocalWriteAction.CREATE.name && pendingForRecord.status == "queued") {
            isSavingTransaction = true
            try {
                localRepository.discardWrite(pendingForRecord.operationId)
                transactionWriteMessage = null
                loadLocalRecords(budgetId)
                return true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                transactionWriteMessage = "Tandem couldn’t remove this transaction. Open Pending Changes to check it."
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
        } catch (_: Exception) {
            transactionWriteMessage = "Tandem couldn’t delete this transaction. Open Pending Changes to check it."
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
        if (amount < 0) {
            transactionWriteMessage = "Enter a positive amount."
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
                budgetReorderQueue.moves.any {
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
            val summary = if (categoryIsPending) null else synchronize(budgetId, encryptionPassword)
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
        } catch (_: Exception) {
            transactionWriteMessage = "Tandem couldn’t confirm the save. Check Activity before adding it again."
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
        if (isSavingTransaction || isSyncing) {
            reportConflictResolution(operationId, "Wait for the current save or sync to finish, then review this change again.")
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
        if (operation?.status == "uncertain") {
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
        if (operation?.recordType == "budget-mutation" && !restoreBudgetReorderQueue()) {
            transactionWriteMessage = "Couldn’t verify this pending change. Try again before removing it."
            return
        }
        localRepository.discardWrite(operationId)
        if (operation?.recordType == "budget-mutation") {
            budgetReorderQueue = budgetReorderQueue.discardOperation(operationId)
            retryQueuedBudgetReorders()
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

    suspend fun loadLocalRecords(budgetId: String) {
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
        payeeRecords = localRepository.findRecords(budgetId, "payee")
        categoryRecords = localRepository.findRecords(budgetId, "category")
        categoryGroupRecords = localRepository.findRecords(budgetId, "category-group")
        budgetMonthIds = localRepository.findRecordIds(budgetId, "budget-month")
        selectedBudgetMonthRecord = null
        pendingWrites = localRepository.readPendingWrites(budgetId)
        conflictRecordsByRecordId = findConflictRecords(budgetId, pendingWrites)
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
                val merged = mergeRefreshedActivityTransactions(previouslyLoaded, refreshed)
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
            launch { localRepository.observeRecords(budgetId, "payee").collect { payeeRecords = it } }
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
        }
    }

    suspend fun observeSelectedBudgetMonth(
        budgetId: String,
        monthId: String,
    ) {
        val localRepository = repository ?: return
        selectedBudgetMonthRecord = null
        if (budgetId.isBlank() || monthId.isBlank()) return
        if (!isCurrentIdentityCacheOwner()) return
        localRepository.observeRecord(budgetId, "budget-month", monthId).collect { selectedBudgetMonthRecord = it }
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
        isCurrentIdentityCacheOwner() && repository?.hasCachedSnapshot(budgetId) == true

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
        if (!restoreBudgetReorderQueue()) {
            restorePreviousSessionAfterSignIn(previousSession)
            signInError = "Couldn’t check pending changes. Try again before switching accounts."
            return false
        }

        val owner = budgetCacheOwner(serverUrl, device.principalId)
        val savedPreferences = store.read()
        val savedOwner = savedPreferences[BUDGET_CACHE_OWNER_KEY]?.takeIf(String::isNotBlank)
        val hasAnyLocalBudgetData = repository?.hasAnyLocalBudgetData() == true
        val hasPendingBudgetChanges = budgetReorderQueue.moves.isNotEmpty()
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
            budgetQueuePersistenceMutex.withLock {
                budgetReorderQueueState = BudgetReorderQueueState()
                budgetQueueLoaded = true
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

    private suspend fun isCurrentIdentityCacheOwner(): Boolean {
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
        "The server still trusts this device. Sign in again to this server and remove it in Settings."
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

private const val ACTIVITY_TRANSACTION_PAGE_SIZE = 50
private const val ACTIVITY_TRANSACTION_REFRESH_LIMIT = 200
private const val SCHEDULE_ACTION_RETRY_INTERVAL_MILLIS = 30_000L
private const val BUDGET_QUEUE_RESTORE_ERROR = "Couldn’t load saved category changes. Try again before editing categories."
private const val BUDGET_CACHE_OWNER_KEY = "internal:budget-cache-owner-v1"

private fun budgetCacheOwner(
    serverUrl: String,
    principalId: String,
): String =
    buildJsonObject {
        put("serverUrl", serverUrl.trim().trimEnd('/'))
        put("principalId", principalId)
    }.toString()

private fun BudgetReorderQueueState.enqueueIfMissing(move: QueuedBudgetReorder): BudgetReorderQueueState =
    if (moves.any { it.operationId == move.operationId }) this else enqueue(move)

internal fun activityTransactionRefreshLimit(loadedRecordCount: Int): Int =
    minOf(
        loadedRecordCount.coerceAtLeast(ACTIVITY_TRANSACTION_PAGE_SIZE),
        ACTIVITY_TRANSACTION_REFRESH_LIMIT,
    )

internal fun mergeRefreshedActivityTransactions(
    previouslyLoaded: List<BudgetRecordEntity>,
    refreshedPage: List<BudgetRecordEntity>,
): List<BudgetRecordEntity> {
    val refreshedIds = refreshedPage.mapTo(mutableSetOf(), BudgetRecordEntity::recordId)
    return (refreshedPage + previouslyLoaded.filterNot { it.recordId in refreshedIds })
        .distinctBy(BudgetRecordEntity::recordId)
        .sortedWith(compareByDescending<BudgetRecordEntity> { it.recordDate ?: "" }.thenBy { it.recordId })
}

private suspend fun LocalBudgetRepository.hasCachedSnapshot(budgetId: String): Boolean {
    val checkpoint = readSyncCheckpoint(budgetId) ?: return false
    return checkpoint.generation.isNotBlank() && checkpoint.cursor != null
}

private fun JsonObject.stringValue(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.longValue(name: String): Long? =
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
