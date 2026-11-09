package app.tandemforactual.ui

import app.tandemforactual.ui.model.LiveCategoryGroupSummary
import app.tandemforactual.ui.model.LiveCategorySummary
import app.tandemforactual.ui.model.PendingCategorySummary
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal const val BUDGET_GROUP_ORDER_SCOPE = "category-groups"

internal fun isRetryableBudgetMutationStatus(httpStatus: Int?): Boolean =
    httpStatus == 408 || httpStatus == 425 || httpStatus == 429 || (httpStatus != null && httpStatus in 500..599)

internal fun shouldKeepBudgetMutationQueued(httpStatus: Int?): Boolean =
    httpStatus == null ||
        isRetryableBudgetMutationStatus(httpStatus)

internal enum class BudgetReorderKind {
    CATEGORY,
    CATEGORY_GROUP,
    HIDE_CATEGORY,
    SHOW_CATEGORY,
    CREATE_CATEGORY_GROUP,
    REFRESH_CATEGORY_GROUP_CREATE,
    RENAME_CATEGORY_GROUP,
    REFRESH_CATEGORY_GROUP_RENAME,
    CREATE_CATEGORY,
    REFRESH_CATEGORY_CREATE,
    RENAME_CATEGORY,
    REFRESH_CATEGORY_RENAME,
    MOVE_CATEGORY_TO_GROUP,
    DELETE_CATEGORY,
    HIDE_CATEGORY_GROUP,
    SHOW_CATEGORY_GROUP,
}

internal data class QueuedBudgetReorder(
    val budgetId: String,
    val scopeId: String,
    val kind: BudgetReorderKind,
    val itemId: String,
    val targetId: String?,
    val desiredOrder: List<String>,
    val encryptionPassword: String?,
    val name: String? = null,
    val isIncome: Boolean = false,
    val parentGroupId: String? = null,
    val refreshOnly: Boolean = false,
    val requiresEncryptionPassword: Boolean = false,
)

internal data class BudgetReorderStatus(
    val pendingCount: Int = 0,
    val message: String? = null,
    val warning: Boolean = false,
    val optimisticOrder: List<String>? = null,
)

/** Immutable queue state so ordering, recovery, and route-independent work can be tested without Compose. */
internal data class BudgetReorderQueueState(
    val moves: List<QueuedBudgetReorder> = emptyList(),
    val statuses: Map<String, BudgetReorderStatus> = emptyMap(),
    val categoryVisibilityOverrides: Map<String, Boolean> = emptyMap(),
    val categoryGroupNameOverrides: Map<String, String> = emptyMap(),
    val categoryGroupIdAliases: Map<String, String> = emptyMap(),
    val categoryNameOverrides: Map<String, String> = emptyMap(),
    val categoryIdAliases: Map<String, String> = emptyMap(),
    val categoryGroupIdOverrides: Map<String, String> = emptyMap(),
    val categoryGroupVisibilityOverrides: Map<String, Boolean> = emptyMap(),
    val deletedCategoryIds: Set<String> = emptySet(),
    val waitingForConnection: Boolean = false,
) {
    fun movesFor(
        budgetId: String,
        scopeId: String,
    ): List<QueuedBudgetReorder> = moves.filter { it.budgetId == budgetId && it.scopeId == scopeId }

    fun statusFor(
        budgetId: String,
        scopeId: String,
    ): BudgetReorderStatus = statuses[budgetReorderStatusKey(budgetId, scopeId)] ?: BudgetReorderStatus()

    fun categoryVisibilityOverridesFor(budgetId: String): Map<String, Boolean> =
        categoryVisibilityOverrides
            .filterKeys { it.startsWith("$budgetId::") }
            .mapKeys { (key, _) -> resolveCategoryId(budgetId, key.removePrefix("$budgetId::")) }

    fun categoryGroupNameOverridesFor(budgetId: String): Map<String, String> =
        categoryGroupNameOverrides
            .filterKeys { it.startsWith("$budgetId::") }
            .mapKeys { (key, _) -> resolveCategoryGroupId(budgetId, key.removePrefix("$budgetId::")) }

    fun categoryNameOverridesFor(budgetId: String): Map<String, String> =
        categoryNameOverrides
            .filterKeys { it.startsWith("$budgetId::") }
            .mapKeys { (key, _) -> resolveCategoryId(budgetId, key.removePrefix("$budgetId::")) }

    fun categoryGroupIdOverridesFor(budgetId: String): Map<String, String> =
        categoryGroupIdOverrides
            .filterKeys { it.startsWith("$budgetId::") }
            .mapKeys { (key, _) -> resolveCategoryId(budgetId, key.removePrefix("$budgetId::")) }
            .mapValues { (_, groupId) -> resolveCategoryGroupId(budgetId, groupId) }

    fun categoryGroupVisibilityOverridesFor(budgetId: String): Map<String, Boolean> =
        categoryGroupVisibilityOverrides
            .filterKeys { it.startsWith("$budgetId::") }
            .mapKeys { (key, _) -> resolveCategoryGroupId(budgetId, key.removePrefix("$budgetId::")) }

    fun deletedCategoryIdsFor(budgetId: String): Set<String> =
        deletedCategoryIds
            .filter { it.startsWith("$budgetId::") }
            .map { resolveCategoryId(budgetId, it.removePrefix("$budgetId::")) }
            .toSet()

    fun pendingCategoryGroupCreatesFor(budgetId: String): List<LiveCategoryGroupSummary> =
        moves
            .filter {
                it.budgetId == budgetId &&
                    it.kind in setOf(BudgetReorderKind.CREATE_CATEGORY_GROUP, BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE)
            }.mapNotNull { move ->
                move.name?.let { name ->
                    LiveCategoryGroupSummary(
                        id = move.itemId,
                        name = name,
                        categories = emptyList(),
                        isIncome = move.isIncome,
                    )
                }
            }

    fun pendingCategoryCreatesFor(budgetId: String): List<PendingCategorySummary> =
        moves
            .filter {
                it.budgetId == budgetId &&
                    it.kind in setOf(BudgetReorderKind.CREATE_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_CREATE)
            }.mapNotNull { move ->
                val name = move.name ?: return@mapNotNull null
                val groupId = move.parentGroupId ?: return@mapNotNull null
                PendingCategorySummary(
                    groupId = resolveCategoryGroupId(budgetId, groupId),
                    category = LiveCategorySummary(move.itemId, name, 0, 0, 0),
                )
            }

    fun resolveCategoryGroupId(
        budgetId: String,
        groupId: String,
    ): String {
        var resolved = groupId
        val visited = mutableSetOf<String>()
        while (visited.add(resolved)) {
            resolved = categoryGroupIdAliases[categoryVisibilityKey(budgetId, resolved)] ?: return resolved
        }
        return resolved
    }

    fun resolveCategoryId(
        budgetId: String,
        categoryId: String,
    ): String {
        var resolved = categoryId
        val visited = mutableSetOf<String>()
        while (visited.add(resolved)) {
            resolved = categoryIdAliases[categoryVisibilityKey(budgetId, resolved)] ?: return resolved
        }
        return resolved
    }

    fun enqueue(move: QueuedBudgetReorder): BudgetReorderQueueState {
        val key = budgetReorderStatusKey(move.budgetId, move.scopeId)
        val current = statusFor(move.budgetId, move.scopeId)
        val hiddenOverride =
            when (move.kind) {
                BudgetReorderKind.HIDE_CATEGORY -> true
                BudgetReorderKind.SHOW_CATEGORY -> false
                else -> null
            }
        val groupHiddenOverride =
            when (move.kind) {
                BudgetReorderKind.HIDE_CATEGORY_GROUP -> true
                BudgetReorderKind.SHOW_CATEGORY_GROUP -> false
                else -> null
            }
        val groupNameOverride =
            move.name?.takeIf {
                move.kind in
                    setOf(
                        BudgetReorderKind.RENAME_CATEGORY_GROUP,
                        BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME,
                    )
            }
        val categoryNameOverride =
            move.name?.takeIf { move.kind in setOf(BudgetReorderKind.RENAME_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_RENAME) }
        val hasOrderingChange =
            move.kind in
                setOf(
                    BudgetReorderKind.CATEGORY,
                    BudgetReorderKind.CATEGORY_GROUP,
                    BudgetReorderKind.HIDE_CATEGORY,
                    BudgetReorderKind.SHOW_CATEGORY,
                    BudgetReorderKind.CREATE_CATEGORY_GROUP,
                    BudgetReorderKind.CREATE_CATEGORY,
                )
        return copy(
            moves = moves + move.copy(requiresEncryptionPassword = move.requiresEncryptionPassword || move.encryptionPassword != null),
            statuses =
                statuses +
                    (
                        key to
                            current.copy(
                                pendingCount = current.pendingCount + 1,
                                message = current.message.takeIf { waitingForConnection },
                                warning = current.warning && waitingForConnection,
                                optimisticOrder = move.desiredOrder.takeIf { hasOrderingChange } ?: current.optimisticOrder,
                            )
                    ),
            categoryVisibilityOverrides =
                if (hiddenOverride == null) {
                    categoryVisibilityOverrides
                } else {
                    categoryVisibilityOverrides + (categoryVisibilityKey(move.budgetId, move.itemId) to hiddenOverride)
                },
            categoryGroupNameOverrides =
                if (groupNameOverride == null) {
                    categoryGroupNameOverrides
                } else {
                    categoryGroupNameOverrides + (categoryVisibilityKey(move.budgetId, move.itemId) to groupNameOverride)
                },
            categoryNameOverrides =
                if (categoryNameOverride == null) {
                    categoryNameOverrides
                } else {
                    categoryNameOverrides + (categoryVisibilityKey(move.budgetId, move.itemId) to categoryNameOverride)
                },
            categoryGroupIdOverrides =
                if (move.kind == BudgetReorderKind.MOVE_CATEGORY_TO_GROUP && move.parentGroupId != null) {
                    categoryGroupIdOverrides + (categoryVisibilityKey(move.budgetId, move.itemId) to move.parentGroupId)
                } else {
                    categoryGroupIdOverrides
                },
            categoryGroupVisibilityOverrides =
                if (groupHiddenOverride == null) {
                    categoryGroupVisibilityOverrides
                } else {
                    categoryGroupVisibilityOverrides + (categoryVisibilityKey(move.budgetId, move.itemId) to groupHiddenOverride)
                },
            deletedCategoryIds =
                if (move.kind == BudgetReorderKind.DELETE_CATEGORY) {
                    deletedCategoryIds + categoryVisibilityKey(move.budgetId, move.itemId)
                } else {
                    deletedCategoryIds
                },
        )
    }

    fun complete(
        move: QueuedBudgetReorder,
        refreshFailed: Boolean,
        message: String?,
    ): BudgetReorderQueueState {
        if (moves.firstOrNull() != move) return this
        val remainingMoves = moves.drop(1)
        val key = budgetReorderStatusKey(move.budgetId, move.scopeId)
        val current = statusFor(move.budgetId, move.scopeId)
        val nextCount = (current.pendingCount - 1).coerceAtLeast(0)
        val hasPendingVisibilityChange =
            remainingMoves.any {
                it.budgetId == move.budgetId &&
                    it.itemId == move.itemId &&
                    it.kind in setOf(BudgetReorderKind.HIDE_CATEGORY, BudgetReorderKind.SHOW_CATEGORY)
            }
        val hasPendingGroupRename =
            remainingMoves.any {
                it.budgetId == move.budgetId &&
                    it.itemId == move.itemId &&
                    it.kind in setOf(BudgetReorderKind.RENAME_CATEGORY_GROUP, BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME)
            }
        val hasPendingCategoryRename =
            remainingMoves.any {
                it.budgetId == move.budgetId &&
                    it.itemId == move.itemId &&
                    it.kind in setOf(BudgetReorderKind.RENAME_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_RENAME)
            }
        val hasPendingCategoryMove =
            remainingMoves.any {
                it.budgetId == move.budgetId && it.itemId == move.itemId && it.kind == BudgetReorderKind.MOVE_CATEGORY_TO_GROUP
            }
        val hasPendingGroupVisibilityChange =
            remainingMoves.any {
                it.budgetId == move.budgetId &&
                    it.itemId == move.itemId &&
                    it.kind in setOf(BudgetReorderKind.HIDE_CATEGORY_GROUP, BudgetReorderKind.SHOW_CATEGORY_GROUP)
            }
        val hasPendingCategoryDelete =
            remainingMoves.any { it.budgetId == move.budgetId && it.itemId == move.itemId && it.kind == BudgetReorderKind.DELETE_CATEGORY }
        val nextStatus =
            when {
                refreshFailed ->
                    current.copy(
                        pendingCount = nextCount,
                        message = message ?: "Actual was updated, but this device couldn’t refresh the budget.",
                        warning = true,
                    )
                nextCount == 0 -> BudgetReorderStatus()
                else -> current.copy(pendingCount = nextCount)
            }
        val visibilityOverrides =
            if (move.kind in setOf(BudgetReorderKind.HIDE_CATEGORY, BudgetReorderKind.SHOW_CATEGORY) &&
                !refreshFailed &&
                !hasPendingVisibilityChange
            ) {
                categoryVisibilityOverrides - categoryVisibilityKey(move.budgetId, move.itemId)
            } else {
                categoryVisibilityOverrides
            }
        val groupNameOverrides =
            if (move.kind in setOf(BudgetReorderKind.RENAME_CATEGORY_GROUP, BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME) &&
                !refreshFailed &&
                !hasPendingGroupRename
            ) {
                categoryGroupNameOverrides - categoryVisibilityKey(move.budgetId, move.itemId)
            } else {
                categoryGroupNameOverrides
            }
        val categoryNameOverrides =
            if (move.kind in setOf(BudgetReorderKind.RENAME_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_RENAME) &&
                !refreshFailed &&
                !hasPendingCategoryRename
            ) {
                categoryNameOverrides - categoryVisibilityKey(move.budgetId, move.itemId)
            } else {
                categoryNameOverrides
            }
        val categoryGroupIdOverrides =
            if (move.kind == BudgetReorderKind.MOVE_CATEGORY_TO_GROUP && !refreshFailed && !hasPendingCategoryMove) {
                categoryGroupIdOverrides - categoryVisibilityKey(move.budgetId, move.itemId)
            } else {
                categoryGroupIdOverrides
            }
        val categoryGroupVisibilityOverrides =
            if (move.kind in setOf(BudgetReorderKind.HIDE_CATEGORY_GROUP, BudgetReorderKind.SHOW_CATEGORY_GROUP) &&
                !refreshFailed &&
                !hasPendingGroupVisibilityChange
            ) {
                categoryGroupVisibilityOverrides - categoryVisibilityKey(move.budgetId, move.itemId)
            } else {
                categoryGroupVisibilityOverrides
            }
        val deletedCategoryIds =
            if (move.kind == BudgetReorderKind.DELETE_CATEGORY && !refreshFailed && !hasPendingCategoryDelete) {
                deletedCategoryIds - categoryVisibilityKey(move.budgetId, move.itemId)
            } else {
                deletedCategoryIds
            }
        return copy(
            moves = remainingMoves,
            statuses = statuses + (key to nextStatus),
            categoryVisibilityOverrides = visibilityOverrides,
            categoryGroupNameOverrides = groupNameOverrides,
            categoryNameOverrides = categoryNameOverrides,
            categoryGroupIdOverrides = categoryGroupIdOverrides,
            categoryGroupVisibilityOverrides = categoryGroupVisibilityOverrides,
            deletedCategoryIds = deletedCategoryIds,
        )
    }

    fun awaitRefresh(
        move: QueuedBudgetReorder,
        message: String,
    ): BudgetReorderQueueState {
        if (moves.firstOrNull() != move) return this
        val key = budgetReorderStatusKey(move.budgetId, move.scopeId)
        val current = statusFor(move.budgetId, move.scopeId)
        return copy(
            moves = moves.map { if (it == move) it.copy(refreshOnly = true) else it },
            waitingForConnection = true,
            statuses = statuses + (key to current.copy(message = message, warning = true)),
        )
    }

    fun completeCategoryCreate(
        move: QueuedBudgetReorder,
        serverCategoryId: String,
    ): BudgetReorderQueueState {
        if (moves.firstOrNull() != move) return this
        val key = budgetReorderStatusKey(move.budgetId, move.scopeId)
        val current = statusFor(move.budgetId, move.scopeId)
        val remainingMoves = moves.drop(1).map { queued -> queued.replaceCategoryId(move.budgetId, move.itemId, serverCategoryId) }
        val oldCategoryKey = categoryVisibilityKey(move.budgetId, move.itemId)
        val newCategoryKey = categoryVisibilityKey(move.budgetId, serverCategoryId)
        val nextCount = (current.pendingCount - 1).coerceAtLeast(0)
        return copy(
            moves = remainingMoves,
            statuses =
                statuses +
                    (
                        key to
                            if (nextCount == 0) {
                                BudgetReorderStatus()
                            } else {
                                current.copy(
                                    pendingCount = nextCount,
                                    optimisticOrder = current.optimisticOrder?.map { if (it == move.itemId) serverCategoryId else it },
                                )
                            }
                    ),
            categoryIdAliases = categoryIdAliases + (oldCategoryKey to serverCategoryId),
            categoryNameOverrides = categoryNameOverrides.remapKey(oldCategoryKey, newCategoryKey),
            categoryGroupIdOverrides = categoryGroupIdOverrides.remapKey(oldCategoryKey, newCategoryKey),
            categoryVisibilityOverrides = categoryVisibilityOverrides.remapBooleanKey(oldCategoryKey, newCategoryKey),
            deletedCategoryIds = deletedCategoryIds.remapId(oldCategoryKey, newCategoryKey),
        )
    }

    fun awaitCategoryCreateRefresh(
        move: QueuedBudgetReorder,
        serverCategoryId: String,
        message: String,
    ): BudgetReorderQueueState {
        if (moves.firstOrNull() != move) return this
        val key = budgetReorderStatusKey(move.budgetId, move.scopeId)
        val current = statusFor(move.budgetId, move.scopeId)
        val updatedMove =
            move.copy(
                kind = BudgetReorderKind.REFRESH_CATEGORY_CREATE,
                itemId = serverCategoryId,
                desiredOrder = move.desiredOrder.map { if (it == move.itemId) serverCategoryId else it },
            )
        val oldCategoryKey = categoryVisibilityKey(move.budgetId, move.itemId)
        val newCategoryKey = categoryVisibilityKey(move.budgetId, serverCategoryId)
        return copy(
            moves =
                moves.map { queued ->
                    if (queued == move) updatedMove else queued.replaceCategoryId(move.budgetId, move.itemId, serverCategoryId)
                },
            waitingForConnection = true,
            statuses = statuses + (key to current.copy(message = message, warning = true)),
            categoryIdAliases = categoryIdAliases + (oldCategoryKey to serverCategoryId),
            categoryNameOverrides = categoryNameOverrides.remapKey(oldCategoryKey, newCategoryKey),
        )
    }

    fun awaitCategoryGroupRefresh(
        move: QueuedBudgetReorder,
        serverGroupId: String,
        refreshKind: BudgetReorderKind,
        message: String,
    ): BudgetReorderQueueState {
        if (moves.firstOrNull() != move) return this
        val key = budgetReorderStatusKey(move.budgetId, move.scopeId)
        val updatedMove =
            move.copy(
                kind = refreshKind,
                itemId = serverGroupId,
                desiredOrder = move.desiredOrder.map { if (it == move.itemId) serverGroupId else it },
            )
        val updatedMoves =
            moves.map { queued ->
                if (queued ==
                    move
                ) {
                    updatedMove
                } else {
                    queued.replaceGroupId(move.budgetId, move.itemId, serverGroupId)
                }
            }
        val current = statusFor(move.budgetId, move.scopeId)
        val oldGroupKey = categoryVisibilityKey(move.budgetId, move.itemId)
        val newGroupKey = categoryVisibilityKey(move.budgetId, serverGroupId)
        return copy(
            moves = updatedMoves,
            waitingForConnection = true,
            statuses = statuses + (key to current.copy(message = message, warning = true)),
            categoryGroupNameOverrides =
                categoryGroupNameOverrides[oldGroupKey]?.let { name ->
                    categoryGroupNameOverrides - oldGroupKey + (newGroupKey to name)
                } ?: categoryGroupNameOverrides,
            categoryGroupVisibilityOverrides = categoryGroupVisibilityOverrides.remapBooleanKey(oldGroupKey, newGroupKey),
            categoryGroupIdAliases =
                if (move.itemId == serverGroupId) {
                    categoryGroupIdAliases
                } else {
                    categoryGroupIdAliases + (categoryVisibilityKey(move.budgetId, move.itemId) to serverGroupId)
                },
        )
    }

    fun completeCategoryGroupCreate(
        move: QueuedBudgetReorder,
        serverGroupId: String,
    ): BudgetReorderQueueState {
        if (moves.firstOrNull() != move) return this
        val key = budgetReorderStatusKey(move.budgetId, move.scopeId)
        val current = statusFor(move.budgetId, move.scopeId)
        val remainingMoves = moves.drop(1).map { queued -> queued.replaceGroupId(move.budgetId, move.itemId, serverGroupId) }
        val nextCount = (current.pendingCount - 1).coerceAtLeast(0)
        val nextStatus =
            if (nextCount == 0) {
                BudgetReorderStatus()
            } else {
                current.copy(
                    pendingCount = nextCount,
                    optimisticOrder = current.optimisticOrder?.map { if (it == move.itemId) serverGroupId else it },
                )
            }
        val oldGroupKey = categoryVisibilityKey(move.budgetId, move.itemId)
        val newGroupKey = categoryVisibilityKey(move.budgetId, serverGroupId)
        return copy(
            moves = remainingMoves,
            statuses = statuses + (key to nextStatus),
            categoryGroupNameOverrides =
                categoryGroupNameOverrides[oldGroupKey]?.let { name ->
                    categoryGroupNameOverrides - oldGroupKey + (newGroupKey to name)
                } ?: categoryGroupNameOverrides,
            categoryGroupIdAliases = categoryGroupIdAliases + (categoryVisibilityKey(move.budgetId, move.itemId) to serverGroupId),
            categoryGroupVisibilityOverrides = categoryGroupVisibilityOverrides.remapBooleanKey(oldGroupKey, newGroupKey),
        )
    }

    fun waitForConnection(
        move: QueuedBudgetReorder,
        message: String,
    ): BudgetReorderQueueState {
        if (moves.firstOrNull() != move) return this
        val key = budgetReorderStatusKey(move.budgetId, move.scopeId)
        val current = statusFor(move.budgetId, move.scopeId)
        return copy(
            waitingForConnection = true,
            statuses = statuses + (key to current.copy(message = message, warning = true)),
        )
    }

    fun resumeAfterConnection(): BudgetReorderQueueState {
        val queuedKeys = moves.map { budgetReorderStatusKey(it.budgetId, it.scopeId) }.distinct()
        return copy(
            waitingForConnection = false,
            statuses =
                statuses +
                    queuedKeys.associateWith { key ->
                        (statuses[key] ?: BudgetReorderStatus()).copy(message = null, warning = false)
                    },
        )
    }

    fun clearCompletedVisibilityOverrides(budgetId: String): BudgetReorderQueueState {
        val pendingCategoryIds =
            moves
                .filter { it.budgetId == budgetId && it.kind in setOf(BudgetReorderKind.HIDE_CATEGORY, BudgetReorderKind.SHOW_CATEGORY) }
                .map { it.itemId }
                .toSet()
        val prefix = "$budgetId::"
        return copy(
            categoryVisibilityOverrides =
                categoryVisibilityOverrides.filter { (key, _) ->
                    !key.startsWith(prefix) || key.removePrefix(prefix) in pendingCategoryIds
                },
            statuses =
                statuses.mapValues { (key, status) ->
                    if (key.startsWith(prefix) && status.pendingCount == 0) {
                        status.copy(message = null, warning = false, optimisticOrder = null)
                    } else {
                        status
                    }
                },
        )
    }

    fun failAll(message: String): BudgetReorderQueueState {
        val failedKeys = moves.map { budgetReorderStatusKey(it.budgetId, it.scopeId) }.distinct()
        val failedVisibilityKeys =
            moves
                .filter { it.kind in setOf(BudgetReorderKind.HIDE_CATEGORY, BudgetReorderKind.SHOW_CATEGORY) }
                .map { categoryVisibilityKey(it.budgetId, it.itemId) }
        val failedGroupNameKeys =
            moves
                .filter { it.kind in setOf(BudgetReorderKind.RENAME_CATEGORY_GROUP, BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME) }
                .map { categoryVisibilityKey(it.budgetId, it.itemId) }
        return copy(
            moves = emptyList(),
            waitingForConnection = false,
            categoryVisibilityOverrides = categoryVisibilityOverrides - failedVisibilityKeys,
            categoryGroupNameOverrides = categoryGroupNameOverrides - failedGroupNameKeys,
            statuses =
                statuses +
                    failedKeys.associateWith { key ->
                        (statuses[key] ?: BudgetReorderStatus()).copy(
                            pendingCount = 0,
                            message = message,
                            warning = false,
                            optimisticOrder = null,
                        )
                    },
        )
    }

    fun failHead(
        move: QueuedBudgetReorder,
        message: String,
    ): BudgetReorderQueueState {
        if (moves.firstOrNull() != move) return this
        val failedMoves =
            if (move.kind == BudgetReorderKind.CREATE_CATEGORY_GROUP) {
                moves.filter { queued ->
                    queued == move ||
                        queued.budgetId == move.budgetId &&
                        (
                            queued.itemId == move.itemId ||
                                queued.targetId == move.itemId ||
                                queued.parentGroupId == move.itemId ||
                                move.itemId in queued.desiredOrder
                        )
                }
            } else {
                listOf(move)
            }
        val failedSet = failedMoves.toSet()
        val remainingMoves = moves.filterNot { it in failedSet }
        val affectedKeys = failedMoves.map { budgetReorderStatusKey(it.budgetId, it.scopeId) }.distinct()
        val nextStatuses =
            affectedKeys.associateWith { key ->
                val removedCount = failedMoves.count { budgetReorderStatusKey(it.budgetId, it.scopeId) == key }
                val remaining = remainingMoves.filter { budgetReorderStatusKey(it.budgetId, it.scopeId) == key }
                val latestOrder = remaining.lastOrNull { it.kind in budgetOrderKinds }?.desiredOrder
                val current = statuses[key] ?: BudgetReorderStatus()
                if (remaining.isEmpty()) {
                    BudgetReorderStatus(message = message, warning = true)
                } else {
                    current.copy(
                        pendingCount = (current.pendingCount - removedCount).coerceAtLeast(0),
                        message = message,
                        warning = true,
                        optimisticOrder = latestOrder ?: current.optimisticOrder,
                    )
                }
            }
        val visibilityKeys =
            failedMoves
                .filter { it.kind in setOf(BudgetReorderKind.HIDE_CATEGORY, BudgetReorderKind.SHOW_CATEGORY) }
                .map { categoryVisibilityKey(it.budgetId, it.itemId) }
                .filterNot { key ->
                    remainingMoves.any {
                        categoryVisibilityKey(it.budgetId, it.itemId) == key &&
                            it.kind in setOf(BudgetReorderKind.HIDE_CATEGORY, BudgetReorderKind.SHOW_CATEGORY)
                    }
                }
        val groupNameKeys =
            failedMoves
                .filter { it.kind in setOf(BudgetReorderKind.RENAME_CATEGORY_GROUP, BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME) }
                .map { categoryVisibilityKey(it.budgetId, it.itemId) }
                .filterNot { key ->
                    remainingMoves.any {
                        categoryVisibilityKey(it.budgetId, it.itemId) == key &&
                            it.kind in setOf(BudgetReorderKind.RENAME_CATEGORY_GROUP, BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME)
                    }
                }
        val categoryNameKeys =
            failedMoves
                .filter { it.kind in setOf(BudgetReorderKind.RENAME_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_RENAME) }
                .map { categoryVisibilityKey(it.budgetId, it.itemId) }
                .filterNot { key ->
                    remainingMoves.any {
                        categoryVisibilityKey(it.budgetId, it.itemId) == key &&
                            it.kind in setOf(BudgetReorderKind.RENAME_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_RENAME)
                    }
                }
        val categoryMoveKeys =
            failedMoves
                .filter { it.kind == BudgetReorderKind.MOVE_CATEGORY_TO_GROUP }
                .map { categoryVisibilityKey(it.budgetId, it.itemId) }
                .filterNot { key ->
                    remainingMoves.any {
                        categoryVisibilityKey(it.budgetId, it.itemId) == key &&
                            it.kind == BudgetReorderKind.MOVE_CATEGORY_TO_GROUP
                    }
                }
        val groupVisibilityKeys =
            failedMoves
                .filter { it.kind in setOf(BudgetReorderKind.HIDE_CATEGORY_GROUP, BudgetReorderKind.SHOW_CATEGORY_GROUP) }
                .map { categoryVisibilityKey(it.budgetId, it.itemId) }
                .filterNot { key ->
                    remainingMoves.any {
                        categoryVisibilityKey(it.budgetId, it.itemId) == key &&
                            it.kind in setOf(BudgetReorderKind.HIDE_CATEGORY_GROUP, BudgetReorderKind.SHOW_CATEGORY_GROUP)
                    }
                }
        val deletedCategoryKeys =
            failedMoves
                .filter { it.kind == BudgetReorderKind.DELETE_CATEGORY }
                .map { categoryVisibilityKey(it.budgetId, it.itemId) }
                .filterNot { key ->
                    remainingMoves.any {
                        categoryVisibilityKey(it.budgetId, it.itemId) == key &&
                            it.kind == BudgetReorderKind.DELETE_CATEGORY
                    }
                }
        return copy(
            moves = remainingMoves,
            statuses = statuses + nextStatuses,
            categoryVisibilityOverrides = categoryVisibilityOverrides - visibilityKeys,
            categoryGroupNameOverrides = categoryGroupNameOverrides - groupNameKeys,
            categoryNameOverrides = categoryNameOverrides - categoryNameKeys,
            categoryGroupIdOverrides = categoryGroupIdOverrides - categoryMoveKeys,
            categoryGroupVisibilityOverrides = categoryGroupVisibilityOverrides - groupVisibilityKeys,
            deletedCategoryIds = deletedCategoryIds - deletedCategoryKeys,
            waitingForConnection = false,
        )
    }

    fun clearMessage(
        budgetId: String,
        scopeId: String,
    ): BudgetReorderQueueState {
        if (waitingForConnection) return this
        val key = budgetReorderStatusKey(budgetId, scopeId)
        val current = statusFor(budgetId, scopeId)
        return copy(statuses = statuses + (key to current.copy(message = null, warning = false)))
    }
}

internal fun budgetReorderFailureMessage(message: String?): String {
    val detail = message?.trim()?.trimEnd('.', '!', '?')?.takeIf(String::isNotEmpty)
    return if (detail == null) {
        "Couldn’t reorder this item. The original order was restored."
    } else {
        "$detail. The original order was restored."
    }
}

internal fun budgetMutationFailureMessage(
    kind: BudgetReorderKind,
    message: String?,
): String {
    if (kind == BudgetReorderKind.CATEGORY || kind == BudgetReorderKind.CATEGORY_GROUP) {
        return budgetReorderFailureMessage(message)
    }
    val detail = message?.trim()?.trimEnd('.', '!', '?')?.takeIf(String::isNotEmpty)
    val fallback =
        when (kind) {
            BudgetReorderKind.HIDE_CATEGORY, BudgetReorderKind.SHOW_CATEGORY -> "Couldn’t update this category’s visibility."
            BudgetReorderKind.CREATE_CATEGORY_GROUP -> "Couldn’t create this category group."
            BudgetReorderKind.RENAME_CATEGORY_GROUP, BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME ->
                "Couldn’t rename this category group."
            BudgetReorderKind.CREATE_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_CREATE -> "Couldn’t create this category."
            BudgetReorderKind.RENAME_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_RENAME -> "Couldn’t rename this category."
            BudgetReorderKind.MOVE_CATEGORY_TO_GROUP -> "Couldn’t move this category."
            BudgetReorderKind.DELETE_CATEGORY -> "Couldn’t delete this category."
            BudgetReorderKind.HIDE_CATEGORY_GROUP, BudgetReorderKind.SHOW_CATEGORY_GROUP ->
                "Couldn’t update this category group’s visibility."
            BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE -> "Couldn’t refresh the new category group."
        }
    return if (detail == null) fallback else "$detail."
}

internal fun optimisticBudgetOrder(
    itemIds: List<String>,
    queuedMoves: List<QueuedBudgetReorder>,
): List<String> =
    queuedMoves.fold(itemIds) { order, move ->
        val itemIndex = order.indexOf(move.itemId)
        if (itemIndex < 0) {
            order
        } else {
            val withoutItem = order.toMutableList().apply { removeAt(itemIndex) }
            val targetIndex = move.targetId?.let(withoutItem::indexOf)?.takeIf { it >= 0 } ?: withoutItem.size
            withoutItem.apply { add(targetIndex, move.itemId) }
        }
    }

internal fun budgetOrderForScreen(
    serverOrder: List<String>,
    status: BudgetReorderStatus,
    queuedMoves: List<QueuedBudgetReorder>,
): List<String> = status.optimisticOrder ?: optimisticBudgetOrder(serverOrder, queuedMoves)

internal fun shouldShowBudgetCreateAction(
    isLiveBudget: Boolean,
    isReordering: Boolean,
): Boolean = isLiveBudget && !isReordering

private fun budgetReorderStatusKey(
    budgetId: String,
    scopeId: String,
): String = "$budgetId::$scopeId"

private fun categoryVisibilityKey(
    budgetId: String,
    categoryId: String,
): String = "$budgetId::$categoryId"

private fun QueuedBudgetReorder.replaceGroupId(
    budgetId: String,
    previousId: String,
    nextId: String,
): QueuedBudgetReorder =
    if (this.budgetId != budgetId) {
        this
    } else {
        copy(
            itemId = if (itemId == previousId) nextId else itemId,
            targetId = if (targetId == previousId) nextId else targetId,
            parentGroupId = if (parentGroupId == previousId) nextId else parentGroupId,
            desiredOrder = desiredOrder.map { if (it == previousId) nextId else it },
        )
    }

private fun QueuedBudgetReorder.replaceCategoryId(
    budgetId: String,
    previousId: String,
    nextId: String,
): QueuedBudgetReorder =
    if (this.budgetId != budgetId) {
        this
    } else {
        copy(
            itemId = if (itemId == previousId) nextId else itemId,
            targetId = if (targetId == previousId) nextId else targetId,
            desiredOrder = desiredOrder.map { if (it == previousId) nextId else it },
        )
    }

private fun Map<String, String>.remapKey(
    oldKey: String,
    newKey: String,
): Map<String, String> = get(oldKey)?.let { value -> this - oldKey + (newKey to value) } ?: this

private fun Map<String, Boolean>.remapBooleanKey(
    oldKey: String,
    newKey: String,
): Map<String, Boolean> = get(oldKey)?.let { value -> this - oldKey + (newKey to value) } ?: this

private fun Set<String>.remapId(
    oldId: String,
    newId: String,
): Set<String> = if (oldId in this) (this - oldId) + newId else this

private val budgetOrderKinds =
    setOf(
        BudgetReorderKind.CATEGORY,
        BudgetReorderKind.CATEGORY_GROUP,
        BudgetReorderKind.HIDE_CATEGORY,
        BudgetReorderKind.SHOW_CATEGORY,
        BudgetReorderKind.CREATE_CATEGORY_GROUP,
        BudgetReorderKind.CREATE_CATEGORY,
    )

internal fun encodeBudgetReorderQueue(state: BudgetReorderQueueState): String =
    buildJsonObject {
        put("waitingForConnection", state.waitingForConnection)
        put(
            "moves",
            buildJsonArray {
                state.moves.forEach { move ->
                    add(
                        buildJsonObject {
                            put("budgetId", move.budgetId)
                            put("scopeId", move.scopeId)
                            put("kind", move.kind.name)
                            put("itemId", move.itemId)
                            put("targetId", move.targetId?.let(::JsonPrimitive) ?: JsonNull)
                            put("desiredOrder", buildJsonArray { move.desiredOrder.forEach { add(JsonPrimitive(it)) } })
                            put("name", move.name?.let(::JsonPrimitive) ?: JsonNull)
                            put("isIncome", move.isIncome)
                            put("parentGroupId", move.parentGroupId?.let(::JsonPrimitive) ?: JsonNull)
                            put("refreshOnly", move.refreshOnly)
                            put("requiresEncryptionPassword", move.requiresEncryptionPassword || move.encryptionPassword != null)
                        },
                    )
                }
            },
        )
        put("categoryGroupIdAliases", encodeAliases(state.categoryGroupIdAliases))
        put("categoryIdAliases", encodeAliases(state.categoryIdAliases))
    }.toString()

internal fun decodeBudgetReorderQueue(encoded: String?): BudgetReorderQueueState {
    val body =
        encoded?.let {
            runCatching {
                kotlinx.serialization.json.Json
                    .parseToJsonElement(it)
                    .jsonObject
            }.getOrNull()
        }
            ?: return BudgetReorderQueueState()
    val moves =
        (body["moves"] as? JsonArray)
            ?.mapNotNull { element ->
                val move = element as? JsonObject ?: return@mapNotNull null
                val budgetId = move.queueString("budgetId") ?: return@mapNotNull null
                val scopeId = move.queueString("scopeId") ?: return@mapNotNull null
                val kind =
                    move.queueString("kind")?.let { runCatching { BudgetReorderKind.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
                val itemId = move.queueString("itemId") ?: return@mapNotNull null
                QueuedBudgetReorder(
                    budgetId = budgetId,
                    scopeId = scopeId,
                    kind = kind,
                    itemId = itemId,
                    targetId = move.queueString("targetId"),
                    desiredOrder = (move["desiredOrder"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
                    encryptionPassword = null,
                    name = move.queueString("name"),
                    isIncome = move["isIncome"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false,
                    parentGroupId = move.queueString("parentGroupId"),
                    refreshOnly = move["refreshOnly"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false,
                    requiresEncryptionPassword = move["requiresEncryptionPassword"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false,
                )
            }.orEmpty()
    val reconstructed = moves.fold(BudgetReorderQueueState()) { current, move -> current.enqueue(move) }
    return reconstructed.copy(
        categoryGroupIdAliases = decodeAliases(body["categoryGroupIdAliases"]),
        categoryIdAliases = decodeAliases(body["categoryIdAliases"]),
        waitingForConnection = (body["waitingForConnection"] as? JsonPrimitive)?.booleanOrNull ?: false,
    )
}

private fun encodeAliases(aliases: Map<String, String>): JsonArray =
    buildJsonArray {
        aliases.forEach { (localId, serverId) ->
            add(
                buildJsonObject {
                    put("localId", localId)
                    put("serverId", serverId)
                },
            )
        }
    }

private fun decodeAliases(value: kotlinx.serialization.json.JsonElement?): Map<String, String> =
    (value as? JsonArray)
        ?.mapNotNull { element ->
            val alias = element as? JsonObject ?: return@mapNotNull null
            val localId = alias.queueString("localId") ?: return@mapNotNull null
            val serverId = alias.queueString("serverId") ?: return@mapNotNull null
            localId to serverId
        }?.toMap()
        .orEmpty()

private fun JsonObject.queueString(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
