package app.tandemforactual.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.PairedDevice
import app.tandemforactual.api.client.StatementImportItem
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
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalRecordProjection
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.model.LiveCategoryGroupSummary
import app.tandemforactual.ui.model.booleanValue
import app.tandemforactual.ui.model.recordBody
import app.tandemforactual.ui.reports.ReportsViewModels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
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

internal data class BudgetActionFeedback(
    val text: String,
    val kind: InlineMessageKind,
    val ownerRoute: AppRouteEntry,
)

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
    ) {
        if (ownerRoute != null) feedback = BudgetActionFeedback(text, kind, ownerRoute)
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

/** In-memory session state. Device credentials use a platform store; budget passwords are never persisted. */
internal class TandemMobileSession(
    private val apiClient: TandemApiClient,
    database: TandemDatabase?,
    private val nowEpochMillis: () -> Long,
    private val credentialStore: DeviceCredentialStore? = null,
) {
    private val repository = database?.let { LocalBudgetRepository(it, nowEpochMillis) }
    private val preferencesStore = database?.let(::AppPreferencesStore)
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val budgetQueuePersistenceMutex = Mutex()
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

    var pairedDevice by mutableStateOf<PairedDevice?>(null)
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

    var isPairing by mutableStateOf(false)
        private set

    var pairingError by mutableStateOf<String?>(null)
        private set

    var credentialStorageError by mutableStateOf<String?>(null)
        private set

    var deviceRevocationError by mutableStateOf<String?>(null)
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

    var isPerformingBudgetAction by mutableStateOf(false)
        private set

    private val budgetActionFeedbackState = BudgetActionFeedbackState()
    private var suppressBudgetActionFeedback = false
    private val currentBudgetActionMessageOwner: AppRouteEntry?
        get() = if (suppressBudgetActionFeedback) null else budgetActionFeedbackState.currentRouteEntry

    var budgetActionMessage: String?
        get() = budgetActionFeedbackState.currentRouteEntry?.let { budgetActionFeedbackState.messageFor(it)?.text }
        private set(value) {
            if (value == null) {
                budgetActionFeedbackState.clear(currentBudgetActionMessageOwner)
            } else {
                budgetActionFeedbackState.show(value, InlineMessageKind.ERROR, currentBudgetActionMessageOwner)
            }
        }

    val budgetActionMessageKind: InlineMessageKind
        get() {
            val routeEntry = budgetActionFeedbackState.currentRouteEntry
            return routeEntry?.let { budgetActionFeedbackState.messageFor(it)?.kind } ?: InlineMessageKind.ERROR
        }

    var budgetActionWasAppliedButRefreshFailed by mutableStateOf(false)
        private set

    private var budgetActionShouldRetry by mutableStateOf(false)

    private var budgetReorderQueueState by mutableStateOf(BudgetReorderQueueState())
    private var budgetReorderQueue: BudgetReorderQueueState
        get() = budgetReorderQueueState
        set(value) {
            budgetReorderQueueState = value
            persistBudgetReorderQueue()
        }
    private var budgetReorderRetrySignal by mutableStateOf(0)

    private suspend fun restoreBudgetReorderQueue() {
        if (budgetQueueLoaded) return
        val pendingBeforeRestore = budgetReorderQueueState.moves
        val restoredFromRoom =
            runCatching {
                repository?.readBudgetMutationQueue()
            }.getOrNull()
        val restored =
            if (restoredFromRoom != null && (restoredFromRoom.items.isNotEmpty() || restoredFromRoom.metadataJson != null)) {
                val metadata = decodeBudgetReorderQueue(restoredFromRoom.metadataJson)
                val moves =
                    restoredFromRoom.items.mapNotNull { operation ->
                        decodeBudgetReorderQueue(operation.patchJson).moves.singleOrNull()?.copy(operationId = operation.operationId)
                    }
                moves.fold(BudgetReorderQueueState()) { current, move -> current.enqueue(move) }.copy(
                    categoryGroupIdAliases = metadata.categoryGroupIdAliases,
                    categoryIdAliases = metadata.categoryIdAliases,
                    waitingForConnection = metadata.waitingForConnection && moves.isNotEmpty(),
                )
            } else {
                val saved =
                    runCatching {
                        preferencesStore?.read()?.get(AppPreferencesStore.PENDING_BUDGET_REORDERS_KEY)
                    }.getOrNull()
                decodeBudgetReorderQueue(saved)
            }
        budgetReorderQueueState =
            pendingBeforeRestore.fold(restored) { current, move ->
                if (move in current.moves) current else current.enqueue(move)
            }
        budgetQueueLoaded = true
        if (restoredFromRoom == null || (restoredFromRoom.items.isEmpty() && restoredFromRoom.metadataJson == null)) {
            persistBudgetReorderQueueNow(budgetReorderQueueState)
        }
    }

    private fun persistBudgetReorderQueue() {
        val store = preferencesStore ?: return
        if (!budgetQueueLoaded) return
        sessionScope.launch {
            persistCurrentBudgetReorderQueue(store)
        }
    }

    private suspend fun persistCurrentBudgetReorderQueue(store: AppPreferencesStore? = preferencesStore) {
        budgetQueuePersistenceMutex.withLock {
            persistBudgetReorderQueueNow(budgetReorderQueueState)
            store?.putValue(AppPreferencesStore.PENDING_BUDGET_REORDERS_KEY, "")
        }
    }

    private suspend fun persistBudgetReorderQueueNow(state: BudgetReorderQueueState) {
        val localRepository = repository ?: return
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
        localRepository.replaceBudgetMutationQueue(items, encodeBudgetReorderQueueMetadata(state))
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

    fun budgetMutationWaitingForConnection(move: QueuedBudgetReorder): Boolean =
        budgetReorderQueue.waitingForConnection && budgetReorderQueue.moves.firstOrNull() == move

    fun budgetMutationsWaitingForConnection(budgetId: String): Boolean =
        budgetReorderQueue.waitingForConnection && budgetReorderQueue.moves.firstOrNull()?.budgetId == budgetId

    fun enqueueBudgetReorder(
        budgetId: String,
        scopeId: String,
        kind: BudgetReorderKind,
        itemId: String,
        targetId: String?,
        desiredOrder: List<String>,
        encryptionPassword: String?,
    ) {
        budgetReorderQueue =
            budgetReorderQueue.enqueue(
                QueuedBudgetReorder(budgetId, scopeId, kind, itemId, targetId, desiredOrder, encryptionPassword),
            )
    }

    fun enqueueBudgetCategoryVisibilityChange(
        budgetId: String,
        categoryId: String,
        groupId: String,
        hidden: Boolean,
        currentOrder: List<String>,
        encryptionPassword: String?,
    ) {
        budgetActionMessage = null
        val scopeId = groupId
        budgetReorderQueue =
            budgetReorderQueue.enqueue(
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

    fun enqueueBudgetCategoryGroupCreate(
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
        budgetReorderQueue =
            budgetReorderQueue.enqueue(
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
        return localGroupId
    }

    fun enqueueBudgetCategoryGroupRename(
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
        budgetReorderQueue =
            budgetReorderQueue.enqueue(
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
        return true
    }

    fun enqueueBudgetCategoryCreate(
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
        val resolvedGroupId = budgetReorderQueue.resolveCategoryGroupId(budgetId, groupId)
        budgetActionMessage = null
        budgetReorderQueue =
            budgetReorderQueue.enqueue(
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
        return localCategoryId
    }

    fun enqueueBudgetCategoryRename(
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
        budgetReorderQueue =
            budgetReorderQueue.enqueue(
                QueuedBudgetReorder(
                    budgetId = budgetId,
                    scopeId = "category-$categoryId",
                    kind = BudgetReorderKind.RENAME_CATEGORY,
                    itemId = budgetReorderQueue.resolveCategoryId(budgetId, categoryId),
                    targetId = null,
                    desiredOrder = emptyList(),
                    encryptionPassword = encryptionPassword,
                    name = cleanName,
                ),
            )
        return true
    }

    fun enqueueBudgetCategoryMoveToGroup(
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
        budgetReorderQueue =
            budgetReorderQueue.enqueue(
                QueuedBudgetReorder(
                    budgetId = budgetId,
                    scopeId = "category-$categoryId",
                    kind = BudgetReorderKind.MOVE_CATEGORY_TO_GROUP,
                    itemId = budgetReorderQueue.resolveCategoryId(budgetId, categoryId),
                    targetId = null,
                    desiredOrder = emptyList(),
                    encryptionPassword = encryptionPassword,
                    parentGroupId = budgetReorderQueue.resolveCategoryGroupId(budgetId, groupId),
                ),
            )
        return true
    }

    fun enqueueBudgetCategoryDelete(
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
        budgetReorderQueue =
            budgetReorderQueue.enqueue(
                QueuedBudgetReorder(
                    budgetId = budgetId,
                    scopeId = "category-$categoryId",
                    kind = BudgetReorderKind.DELETE_CATEGORY,
                    itemId = budgetReorderQueue.resolveCategoryId(budgetId, categoryId),
                    targetId = transferCategoryId?.let { budgetReorderQueue.resolveCategoryId(budgetId, it) },
                    desiredOrder = emptyList(),
                    encryptionPassword = encryptionPassword,
                ),
            )
        return true
    }

    fun enqueueBudgetCategoryGroupVisibilityChange(
        budgetId: String,
        groupId: String,
        hidden: Boolean,
        encryptionPassword: String?,
    ) {
        if (budgetId.isBlank() || groupId.isBlank() || refuseWriteInReadOnlyMode()) return
        budgetActionMessage = null
        budgetReorderQueue =
            budgetReorderQueue.enqueue(
                QueuedBudgetReorder(
                    budgetId = budgetId,
                    scopeId = BUDGET_GROUP_ORDER_SCOPE,
                    kind = if (hidden) BudgetReorderKind.HIDE_CATEGORY_GROUP else BudgetReorderKind.SHOW_CATEGORY_GROUP,
                    itemId = budgetReorderQueue.resolveCategoryGroupId(budgetId, groupId),
                    targetId = null,
                    desiredOrder = emptyList(),
                    encryptionPassword = encryptionPassword,
                ),
            )
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
        restoreBudgetReorderQueue()
        var observedRetrySignal = budgetReorderRetrySignal
        while (true) {
            val request = snapshotFlow { budgetReorderQueue.moves.firstOrNull() }.first { it != null } ?: continue
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
            snapshotFlow { isPerformingBudgetAction }.first { !it }
            if (budgetReorderQueue.moves.firstOrNull() != request) continue

            var createdCategoryGroupId: String? = null
            var createdCategoryId: String? = null
            budgetActionWasAppliedButRefreshFailed = false
            val moved =
                withoutBudgetActionFeedback {
                    if (request.refreshOnly ||
                        request.kind in
                        setOf(
                            BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE,
                            BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME,
                            BudgetReorderKind.REFRESH_CATEGORY_CREATE,
                            BudgetReorderKind.REFRESH_CATEGORY_RENAME,
                        )
                    ) {
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
                                )
                            BudgetReorderKind.HIDE_CATEGORY ->
                                manageBudgetCategory(
                                    request.budgetId,
                                    request.itemId,
                                    "hide",
                                    null,
                                    request.encryptionPassword,
                                )
                            BudgetReorderKind.SHOW_CATEGORY ->
                                manageBudgetCategory(
                                    request.budgetId,
                                    request.itemId,
                                    "show",
                                    null,
                                    request.encryptionPassword,
                                )
                            BudgetReorderKind.CREATE_CATEGORY_GROUP -> {
                                createdCategoryGroupId =
                                    createBudgetCategoryGroup(
                                        request.budgetId,
                                        request.name.orEmpty(),
                                        request.isIncome,
                                        request.encryptionPassword,
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
                                )
                            BudgetReorderKind.CATEGORY_GROUP ->
                                manageBudgetCategoryGroup(
                                    request.budgetId,
                                    request.itemId,
                                    "move",
                                    null,
                                    request.targetId,
                                    request.encryptionPassword,
                                )
                            BudgetReorderKind.CREATE_CATEGORY -> {
                                createdCategoryId =
                                    createBudgetCategory(
                                        request.budgetId,
                                        request.name.orEmpty(),
                                        request.parentGroupId.orEmpty(),
                                        request.encryptionPassword,
                                    )
                                createdCategoryId != null
                            }
                            BudgetReorderKind.RENAME_CATEGORY ->
                                renameBudgetCategory(request.budgetId, request.itemId, request.name.orEmpty(), request.encryptionPassword)
                            BudgetReorderKind.MOVE_CATEGORY_TO_GROUP ->
                                manageBudgetCategory(
                                    request.budgetId,
                                    request.itemId,
                                    "move",
                                    null,
                                    request.encryptionPassword,
                                    request.parentGroupId,
                                )
                            BudgetReorderKind.DELETE_CATEGORY ->
                                deleteBudgetCategory(request.budgetId, request.itemId, request.targetId, request.encryptionPassword)
                            BudgetReorderKind.HIDE_CATEGORY_GROUP ->
                                manageBudgetCategoryGroup(request.budgetId, request.itemId, "hide", null, null, request.encryptionPassword)
                            BudgetReorderKind.SHOW_CATEGORY_GROUP ->
                                manageBudgetCategoryGroup(request.budgetId, request.itemId, "show", null, null, request.encryptionPassword)
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
            } else if (refreshFailed) {
                // The mutation has already been accepted. Keep the optimistic
                // state and show its retryable refresh status in the queue;
                // the sync transport error belongs on the cloud indicator.
                budgetReorderQueue = budgetReorderQueue.awaitRefresh(request, "Saved in Actual. Waiting for the budget to refresh.")
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
    }

    fun setBudgetActionRoute(routeEntry: AppRouteEntry) {
        budgetActionFeedbackState.changeRoute(routeEntry)
    }

    private fun setBudgetActionMessage(
        text: String,
        kind: InlineMessageKind = InlineMessageKind.ERROR,
        ownerRoute: AppRouteEntry? = currentBudgetActionMessageOwner,
    ) {
        budgetActionFeedbackState.show(text, kind, ownerRoute)
    }

    fun clearTransactionWriteMessage() {
        transactionWriteMessage = null
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
                    credentialStorageError = "Couldn’t access this device’s saved pairing. Try again or pair this device again."
                    return null
                } ?: return null

            pairedDevice =
                try {
                    apiClient.restoreStoredSession(storedSession)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    apiClient.forgetSession()
                    pairedDevice = null
                    budgets = emptyList()
                    credentialStorageError = "This device’s saved pairing is invalid. Pair this device again."
                    clearStoredCredential()
                    return null
                }
            isCredentialStored = true
            return apiClient.currentServerUrl.takeIf { apiClient.isAuthenticated }
        } finally {
            isRestoringStoredSession = false
        }
    }

    suspend fun forgetDevice() {
        if (pairedDevice == null || isRevokingDevice) return
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
        if (pairedDevice == null || isRevokingDevice) return
        deviceRevocationError = null
        clearDeviceSession()
    }

    suspend fun pair(
        serverUrl: String,
        pairingCode: String,
        deviceName: String,
    ) {
        pairingError = null
        deviceRevocationError = null
        isPairing = true
        try {
            pairedDevice = apiClient.pair(serverUrl, pairingCode, deviceName)
            isServerReachable = true
            persistCurrentSession()
            loadBudgets()
            retryQueuedBudgetReorders()
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            pairingError = error.userFacingMessage("Couldn’t pair this device. Check the server and pairing code.")
            pairedDevice = null
        } catch (error: Exception) {
            pairingError = "Couldn’t pair this device. Check the server address and connection."
            pairedDevice = null
        } finally {
            isPairing = false
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
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            isServerReachable = error.httpStatus != null
            budgetError = error.userFacingMessage("Couldn’t load budgets from this server.")
            if (error.errorCode == "invalid_device_credential") {
                apiClient.forgetSession()
                pairedDevice = null
                budgets = emptyList()
                credentialStorageError = "This device pairing is no longer valid. Pair this device again."
                clearStoredCredential()
            }
        } catch (error: Exception) {
            isServerReachable = false
            budgetError = "Couldn’t load budgets from this server. Check the connection and try again."
        } finally {
            isLoadingBudgets = false
        }
    }

    suspend fun synchronize(
        budgetId: String,
        encryptionPassword: String?,
        sendQueuedWrites: Boolean = true,
        reconcileUncertainOperationId: String? = null,
    ): BudgetSyncSummary? {
        restoreBudgetReorderQueue()
        if (!encryptionPassword.isNullOrBlank()) retryQueuedBudgetReorders(budgetId, encryptionPassword)
        syncComplete = false
        syncedTransactionCount = 0
        lastSyncSummary = null
        val coordinator = syncCoordinator
        val localRepository = repository
        if (coordinator == null || localRepository == null) {
            syncPresentation = syncPresentation.failed("Local storage is unavailable. Restart the app and try again.")
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
            lastSyncSummary = summary
            loadLocalRecords(budgetId)
            syncedTransactionCount =
                localRepository.countVisibleRecords(budgetId, "transaction").coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            syncComplete = true
            syncPresentation = syncPresentation.succeeded()
            budgetReorderQueue = budgetReorderQueue.clearCompletedVisibilityOverrides(budgetId)
            retryQueuedBudgetReorders(budgetId, encryptionPassword)
            if (activityTransactionPageBudgetId == budgetId) refreshActivityTransactionPages(budgetId)
            return summary
        } catch (error: CancellationException) {
            throw error
        } catch (error: BudgetSyncRemoteException) {
            isServerReachable = error.httpStatus != null
            syncPresentation =
                syncPresentation.failed(
                    when (error.httpStatus) {
                        401 -> "This device needs to be paired again."
                        403 -> "This device can’t open that budget."
                        404 -> "The budget is no longer available on the server."
                        400, 422 -> "Check the budget password and try again."
                        in 500..599 -> "The server couldn’t read this budget. Try again later."
                        else -> "Couldn’t sync this budget. Check the connection and try again."
                    },
                )
            return null
        } catch (error: BudgetSyncProtocolException) {
            isServerReachable = true
            syncPresentation = syncPresentation.failed("Tandem couldn’t read this budget. Check the server and try again.")
            return null
        } catch (error: Exception) {
            isServerReachable = false
            syncPresentation = syncPresentation.failed("Couldn’t sync this budget. Your saved data is still available.")
            return null
        } finally {
            if (syncPresentation.isSyncing) syncPresentation = syncPresentation.stopped()
        }
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
            transactionWriteMessage = "Open a paired budget before reconciling transactions."
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
            apiClient.renameBudgetCategory(budgetId, categoryId, cleanName, encryptionPassword)
        }
    }

    suspend fun createBudgetCategory(
        budgetId: String,
        name: String,
        categoryGroupId: String,
        encryptionPassword: String?,
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
                categoryId = apiClient.createBudgetCategory(budgetId, categoryGroupId, cleanName, encryptionPassword)
            }
        return categoryId.takeIf { created || budgetActionWasAppliedButRefreshFailed }
    }

    suspend fun createBudgetCategoryGroup(
        budgetId: String,
        name: String,
        isIncome: Boolean,
        encryptionPassword: String?,
    ): String? {
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.length > 100) {
            budgetActionMessage = "Enter a category group name up to 100 characters."
            return null
        }
        var groupId: String? = null
        val created =
            performBudgetAction(budgetId, encryptionPassword, "Couldn’t create this category group. Try again.") {
                groupId = apiClient.createBudgetCategoryGroup(budgetId, cleanName, isIncome, encryptionPassword)
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
    ): Boolean {
        if (action == "rename" && name.isNullOrBlank()) {
            budgetActionMessage = "Enter a category group name."
            return false
        }
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t update this category group. Try again.") {
            apiClient.manageBudgetCategoryGroup(budgetId, groupId, action, name?.trim(), targetId, encryptionPassword)
        }
    }

    suspend fun manageBudgetCategory(
        budgetId: String,
        categoryId: String,
        action: String,
        targetId: String?,
        encryptionPassword: String?,
        targetGroupId: String? = null,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t update this category. Try again.") {
            apiClient.manageBudgetCategory(budgetId, categoryId, action, targetId, encryptionPassword, targetGroupId)
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
            budgetActionMessage = "Pair this device and open a budget before changing it."
            return null
        }
        if (isPerformingBudgetAction) return null
        isPerformingBudgetAction = true
        try {
            val payeeId = apiClient.withExclusiveSession { apiClient.createBudgetPayee(budgetId, cleanName, encryptionPassword) }
            if (synchronize(budgetId, encryptionPassword) == null) {
                setBudgetActionMessage(
                    "The payee was created, but this device couldn’t refresh the budget.",
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
            budgetActionMessage = "Pair this device and open a budget before importing a statement."
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
                    syncError ?: "Actual imported the statement, but this device couldn’t refresh the budget.",
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
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t delete this category. Try again.") {
            apiClient.deleteBudgetCategory(budgetId, categoryId, transferCategoryId, encryptionPassword)
        }

    suspend fun manageBudgetSchedule(
        budgetId: String,
        scheduleId: String,
        action: String,
        name: String?,
        encryptionPassword: String?,
        fields: JsonObject? = null,
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
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t update this schedule. Try again.") {
            apiClient.manageBudgetSchedule(
                budgetId,
                scheduleId,
                action,
                cleanName,
                encryptionPassword,
                fields,
            )
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
        if (refuseWriteInReadOnlyMode()) {
            transactionWriteMessage?.let { setBudgetActionMessage(it, ownerRoute = messageOwner) }
            return false
        }
        if (budgetId.isBlank() || !apiClient.isAuthenticated) {
            setBudgetActionMessage("Pair this device and open a budget before changing it.", ownerRoute = messageOwner)
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
                setBudgetActionMessage(
                    syncError ?: "Actual was updated, but this device couldn’t refresh the budget.",
                    ownerRoute = messageOwner,
                )
                return false
            }
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            budgetActionShouldRetry = shouldKeepBudgetMutationQueued(error.httpStatus)
            isServerReachable = error.httpStatus != null && !budgetActionShouldRetry
            if (actionApplied) {
                budgetActionWasAppliedButRefreshFailed = true
                setBudgetActionMessage(
                    "Actual was updated, but this device couldn’t refresh the budget.",
                    InlineMessageKind.WARNING,
                    messageOwner,
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
            if (actionApplied) {
                budgetActionWasAppliedButRefreshFailed = true
                setBudgetActionMessage(
                    "Actual was updated, but this device couldn’t refresh the budget.",
                    InlineMessageKind.WARNING,
                    messageOwner,
                )
            }
            return false
        } finally {
            isPerformingBudgetAction = false
        }
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
    ): Boolean {
        transactionWriteMessage = null
        if (refuseWriteInReadOnlyMode()) return false
        val enteredAmount = parseAmountMinorUnits(amountText, displaySettings)
        if (enteredAmount == null || enteredAmount < 0) {
            transactionWriteMessage = "Enter a valid positive amount using this device’s number format."
            return false
        }
        if (enteredAmount > MAX_TRANSACTION_AMOUNT_MINOR_UNITS) {
            transactionWriteMessage = "Largest amount is ${formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS, displaySettings)}."
            return false
        }
        val localRepository = repository
        if (localRepository == null) {
            transactionWriteMessage = "Local budget storage is unavailable. Reopen the budget and try again."
            return false
        }
        val current = localRepository.findRecord(budgetId, "transaction", transactionId)
        val baseJson = current?.remoteJson
        val revision = current?.remoteRevision
        if (current == null || current.remoteDeleted || baseJson == null || revision.isNullOrBlank()) {
            transactionWriteMessage = "The latest Actual transaction is unavailable. Sync the budget before editing."
            return false
        }
        val base = runCatching { Json.parseToJsonElement(baseJson).jsonObject }.getOrNull()
        if (base == null) {
            transactionWriteMessage = "Couldn’t read this transaction. Sync the budget before editing."
            return false
        }
        val requestedDate = if (updateDetails) normalizeTransactionDate(date.orEmpty()) else null
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
        if (updateDetails && requestedDate == null) {
            transactionWriteMessage = "Choose a valid transaction date."
            return false
        }
        if (requestedDate != null && requestedDate < "1995-01-01") {
            transactionWriteMessage = "Choose a date on or after ${formatDate(SampleDate(1995, 1, 1))}."
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
                    action = LocalWriteAction.UPDATE,
                    baseRevision = revision,
                    baseSnapshotJson = baseJson,
                    patchJson = patchJson.toString(),
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
        } catch (_: Exception) {
            transactionWriteMessage = "Couldn’t save this edit on this device. Try again."
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
        val localRepository = repository
        if (localRepository == null) {
            transactionWriteMessage = "Local budget storage is unavailable. Reopen the budget and try again."
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
                transactionWriteMessage = "Couldn’t remove this transaction. Review pending changes."
                loadLocalRecords(budgetId)
                return false
            } finally {
                isSavingTransaction = false
            }
        }
        if (pendingForRecord != null && pendingForRecord.status != "queued") {
            transactionWriteMessage = "Review this transaction in Pending Changes before deleting it."
            return false
        }
        val current = localRepository.findRecord(budgetId, "transaction", transactionId)
        val baseJson = current?.remoteJson
        val revision = current?.remoteRevision
        if (current == null || current.remoteDeleted || baseJson == null || revision.isNullOrBlank()) {
            transactionWriteMessage = "The latest Actual transaction is unavailable. Sync the budget before deleting it."
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
            transactionWriteMessage = "Couldn’t delete this transaction. Review pending changes."
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
    ): Boolean {
        transactionWriteMessage = null
        if (refuseWriteInReadOnlyMode()) return false
        val amount = parseAmountMinorUnits(amountText, displaySettings)
        if (amount == null || amount == 0L) {
            transactionWriteMessage = "Enter an amount greater than zero."
            return false
        }
        if (amount == Long.MIN_VALUE || kotlin.math.abs(amount) > MAX_TRANSACTION_AMOUNT_MINOR_UNITS) {
            transactionWriteMessage = "Largest amount is ${formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS, displaySettings)}."
            return false
        }
        val date = normalizeTransactionDate(dateText)
        if (date == null) {
            transactionWriteMessage = "Enter the date as YYYY-MM-DD."
            return false
        }
        val localRepository = repository
        val coordinator = syncCoordinator
        if (localRepository == null || coordinator == null) {
            transactionWriteMessage = "Local budget storage is unavailable. Reopen the budget and try again."
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
        val patch =
            buildJsonObject {
                put("account", account.recordId)
                put("amount", amount)
                put("date", date)
                put("payee", payeeId?.let(::JsonPrimitive) ?: JsonNull)
                put("category", categoryId?.let(::JsonPrimitive) ?: JsonNull)
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
                            categoryId = categoryId,
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
                    "conflict" -> "This transaction is already in Actual. Sync and try again."
                    "uncertain", "sending" -> "Status unknown. Check before retrying."
                    "rejected" -> "Actual didn’t add this transaction. The previous value was kept."
                    else ->
                        if (summary == null) {
                            "Saved on this device. Status unknown; check before retrying."
                        } else {
                            "The transaction is saved on this device and is waiting to sync."
                        }
                }
            loadLocalRecords(budgetId)
            return savedWrite?.status in setOf("committed", "queued")
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            transactionWriteMessage = "The transaction is saved on this device. Status unknown; check before retrying."
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
    ) {
        transactionWriteMessage = null
        if (refuseWriteInReadOnlyMode()) return
        val localRepository = repository
        if (localRepository == null) {
            transactionWriteMessage = "Local storage is unavailable. Restart the app and try again."
            return
        }
        val operation = localRepository.readPendingWrites(budgetId).firstOrNull { it.operationId == operationId }
        if (operation == null || operation.status != "conflict") {
            transactionWriteMessage = "This edit no longer needs review. Sync and try again."
            loadLocalRecords(budgetId)
            return
        }
        if (!keepDeviceVersion) {
            localRepository.discardWrite(operationId)
            loadLocalRecords(budgetId)
            transactionWriteMessage = "Kept the latest Actual version and discarded your edit."
            return
        }
        val current = localRepository.findRecord(budgetId, operation.recordType, operation.recordId)
        val baseJson = current?.remoteJson
        val revision = current?.remoteRevision
        if (current == null || current.remoteDeleted || baseJson == null || revision.isNullOrBlank()) {
            transactionWriteMessage = "This transaction was deleted in Actual. Keep Actual to discard your edit."
            return
        }
        val base = runCatching { Json.parseToJsonElement(baseJson).jsonObject }.getOrNull()
        val patch = runCatching { Json.parseToJsonElement(operation.patchJson).jsonObject }.getOrNull()
        if (base == null || patch == null || operation.action != "UPDATE") {
            transactionWriteMessage = "This edit can’t be applied to the latest Actual version. Keep Actual and discard your edit."
            return
        }
        isSavingTransaction = true
        try {
            val rebased = JsonObject(base + patch)
            val newOperationId = Uuid.random().toString()
            localRepository.enqueueWrite(
                LocalWriteRequest(
                    operationId = newOperationId,
                    idempotencyKey = newOperationId,
                    budgetId = budgetId,
                    recordType = operation.recordType,
                    recordId = operation.recordId,
                    action = LocalWriteAction.UPDATE,
                    baseRevision = revision,
                    baseSnapshotJson = baseJson,
                    patchJson = patch.toString(),
                    optimisticJson = rebased.toString(),
                    createdAtEpochMs = nowEpochMillis(),
                    optimisticProjection = rebased.toProjection(),
                ),
            )
            val summary = synchronize(budgetId, encryptionPassword)
            transactionWriteMessage =
                when {
                    summary == null -> "Your edit is saved on this device. Status unknown; check before retrying."
                    summary.writesCommitted > 0 -> "Your edit was synced to Actual."
                    summary.writesConflicted > 0 -> "Actual changed this transaction again. Review the latest versions."
                    summary.writesUncertain > 0 -> "Status unknown. Check before retrying."
                    summary.writesRejected > 0 -> "Actual didn’t apply your edit. The previous value was restored."
                    else -> "Your edit is waiting to sync."
                }
            loadLocalRecords(budgetId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            transactionWriteMessage = "Your edit is saved on this device. Status unknown; check before retrying."
            loadLocalRecords(budgetId)
        } finally {
            isSavingTransaction = false
        }
    }

    suspend fun discardWrite(
        budgetId: String,
        operationId: String,
    ) {
        transactionWriteMessage = null
        clearUncertainWriteCheck()
        if (refuseWriteInReadOnlyMode()) return
        val localRepository = repository ?: return
        localRepository.discardWrite(operationId)
        loadLocalRecords(budgetId)
        transactionWriteMessage = "Your edit was discarded. Actual wasn’t changed."
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
        localRepository.retryUncertainWrite(operationId)
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
            uncertainWriteCheckError = syncError ?: "Couldn’t check the server. Try again."
            uncertainWriteCheckErrorOperationId = operationId
            return
        }

        checkedWriteOperationId = operationId
        if (summary.operationStatusErrorCode != null && summary.operationStatusErrorCode != "http-404") {
            uncertainWriteCheckError =
                when (summary.operationStatusErrorCode) {
                    "http-401" -> "Pair this device again to check the status."
                    "http-403" -> "This device can’t open this budget."
                    "http-503" -> "Tandem is temporarily unavailable. Try again shortly."
                    "network-error" -> "Couldn’t reach Tandem. Check the connection and try again."
                    else -> "Couldn’t confirm the server status. Try again."
                }
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
    }

    suspend fun loadMoreActivityTransactions(
        budgetId: String,
        reset: Boolean = false,
    ) {
        val localRepository = repository ?: return
        if (budgetId.isBlank()) return
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
            launch { localRepository.observePendingWrites(budgetId).collect { pendingWrites = it } }
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
        localRepository.observeRecord(budgetId, "budget-month", monthId).collect { selectedBudgetMonthRecord = it }
    }

    suspend fun observeActivitySearchRecords(budgetId: String) {
        val localRepository = repository
        if (localRepository == null || budgetId.isBlank()) {
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

    suspend fun hasCachedBudgetSnapshot(budgetId: String): Boolean = repository?.hasCachedSnapshot(budgetId) == true

    suspend fun loadCachedBudgets(): List<BudgetCatalogItem> {
        val localRepository = repository ?: return emptyList()
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
            credentialStorageError = "This pairing will end when the app closes because saved access is unavailable."
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
        apiClient.forgetSession()
        pairedDevice = null
        budgets = emptyList()
        budgetCatalogLoaded = false
        isServerReachable = false
        budgetError = null
        clearStoredCredential()
    }
}

internal fun TandemApiException.userFacingMessage(fallback: String): String = budgetApiErrorMessage(errorCode, httpStatus, fallback)

internal fun budgetApiErrorMessage(
    errorCode: String,
    httpStatus: Int?,
    fallback: String,
): String =
    when (errorCode) {
        "not-paired" -> "Pair this device before opening a budget."
        "invalid_pairing_code" -> "This pairing code is invalid or expired. Request a new code."
        "invalid_device_credential" -> "Pair this device again to continue."
        "bank_sync_failed" -> "Actual couldn’t complete bank sync. Check its provider setup and try again."
        "bank_sync_busy" -> "A bank sync is already running. Try again shortly."
        "duplicate_imported_id" -> "This statement has repeated reference values. Choose a unique reference column."
        "actual_operation_failed" -> "Actual couldn’t complete this change. Check the budget and try again."
        "operation_uncertain" -> "Tandem couldn’t confirm this change. Check Actual before submitting it again."
        "operation_rejected" -> "This change was rejected. Correct it before submitting again."
        "invalid_request" -> "Check the information and try again."
        else ->
            when (httpStatus) {
                400, 422 -> "Check the information and try again."
                401 -> "Pair this device again to continue."
                403 -> "This device can’t change this budget."
                404 -> "This item is no longer available. Refresh the budget."
                408, 425, 429 -> "Tandem is temporarily unavailable. Try again shortly."
                409 -> "The budget changed on the server. Refresh it and try again."
                in 500..599 -> "Tandem is temporarily unavailable. Try again shortly."
                else -> fallback
            }
    }

private const val ACTIVITY_TRANSACTION_PAGE_SIZE = 50
private const val ACTIVITY_TRANSACTION_REFRESH_LIMIT = 200

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

private fun JsonObject.toProjection(): LocalRecordProjection =
    LocalRecordProjection(
        recordDate = stringValue("date"),
        accountId = stringValue("account"),
        categoryId = stringValue("category"),
        payeeId = stringValue("payee"),
        amountMinor = longValue("amount"),
        budgetMonth = stringValue("month"),
    )

private fun normalizeTransactionDate(value: String): String? {
    val text = value.trim()
    if (!Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$").matches(text)) return null
    val year = text.substring(0, 4).toIntOrNull() ?: return null
    val month = text.substring(5, 7).toIntOrNull() ?: return null
    val day = text.substring(8, 10).toIntOrNull() ?: return null
    if (year == 0 || month !in 1..12) return null
    val leapYear = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    val daysInMonth =
        when (month) {
            2 -> if (leapYear) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    return text.takeIf { day in 1..daysInMonth }
}
