package app.tandemforactual.ui.core.session

import kotlin.uuid.Uuid

internal fun BudgetReorderQueueState.replaceOperationId(
    currentOperationId: String,
    newOperationId: String,
): BudgetReorderQueueState =
    copy(
        moves =
            moves.map { move ->
                if (move.operationId == currentOperationId) move.copy(operationId = newOperationId) else move
            },
    )

internal fun BudgetReorderQueueState.replaceRejectedOperation(
    rejectedOperationId: String,
    replacement: QueuedBudgetReorder,
): BudgetReorderQueueState {
    val rejectedIndex = moves.indexOfFirst { it.operationId == rejectedOperationId }
    val next =
        if (rejectedIndex < 0) {
            enqueue(replacement).copy(moves = listOf(replacement) + moves)
        } else {
            val rejected = moves[rejectedIndex]
            val key = budgetReorderStatusKey(rejected.budgetId, rejected.scopeId)
            val status = statusFor(rejected.budgetId, rejected.scopeId)
            val withoutRejected =
                copy(
                    moves = moves.filterNot { it.operationId == rejectedOperationId },
                    statuses = statuses + (key to status.copy(pendingCount = (status.pendingCount - 1).coerceAtLeast(0))),
                )
            val enqueued = withoutRejected.enqueue(replacement)
            val remaining = enqueued.moves.filterNot { it.operationId == replacement.operationId }.toMutableList()
            remaining.add(rejectedIndex.coerceAtMost(remaining.size), replacement)
            enqueued.copy(moves = remaining)
        }
    val categoryNameOverrides =
        if (replacement.kind in setOf(BudgetReorderKind.RENAME_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_RENAME)) {
            val key = categoryVisibilityKey(replacement.budgetId, replacement.itemId)
            val latestName =
                next.moves
                    .lastOrNull {
                        it.budgetId == replacement.budgetId &&
                            it.itemId == replacement.itemId &&
                            it.kind in setOf(BudgetReorderKind.RENAME_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_RENAME)
                    }?.name
            if (latestName == null) next.categoryNameOverrides - key else next.categoryNameOverrides + (key to latestName)
        } else {
            next.categoryNameOverrides
        }
    return next.copy(categoryNameOverrides = categoryNameOverrides)
}

internal fun BudgetReorderQueueState.complete(
    move: QueuedBudgetReorder,
    refreshFailed: Boolean,
    message: String?,
): BudgetReorderQueueState {
    if (moves.firstOrNull() != move) return this
    val remainingMoves = moves.drop(1)
    if (move.kind == BudgetReorderKind.SET_ORDER) {
        return copy(
            moves = remainingMoves,
            statuses =
                statuses +
                    settleOrderBatch(
                        move,
                        remainingMoves,
                        message = if (refreshFailed) message ?: REFRESH_FAILED_MESSAGE else null,
                        // Until the budget refreshes, keep showing the order the user chose.
                        keepOptimisticOrder = refreshFailed,
                    ),
        )
    }
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
                    message = message ?: REFRESH_FAILED_MESSAGE,
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

internal fun BudgetReorderQueueState.awaitRefresh(
    move: QueuedBudgetReorder,
    message: String,
): BudgetReorderQueueState {
    if (moves.firstOrNull() != move) return this
    val key = budgetReorderStatusKey(move.budgetId, move.scopeId)
    val current = statusFor(move.budgetId, move.scopeId)
    return copy(
        moves = moves.map { if (it == move) it.copy(operationId = Uuid.random().toString(), refreshOnly = true) else it },
        waitingForConnection = true,
        statuses = statuses + (key to current.copy(message = message, warning = true)),
    )
}

internal fun BudgetReorderQueueState.completeCategoryCreate(
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

internal fun BudgetReorderQueueState.awaitCategoryCreateRefresh(
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
            operationId = Uuid.random().toString(),
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

internal fun BudgetReorderQueueState.awaitCategoryGroupRefresh(
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
            operationId = Uuid.random().toString(),
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

internal fun BudgetReorderQueueState.completeCategoryGroupCreate(
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
