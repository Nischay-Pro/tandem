package app.tandemforactual.ui.core.session

internal fun BudgetReorderQueueState.waitForConnection(
    move: QueuedBudgetReorder,
    message: String,
): BudgetReorderQueueState {
    if (moves.firstOrNull() != move) return this
    return copy(
        waitingForConnection = true,
        statuses =
            statuses +
                statusKeysFor(move).associateWith { key ->
                    (statuses[key] ?: BudgetReorderStatus()).copy(message = message, warning = true)
                },
    )
}

internal fun BudgetReorderQueueState.holdForReview(
    move: QueuedBudgetReorder,
    message: String,
): BudgetReorderQueueState {
    if (moves.firstOrNull() != move) return this
    return copy(
        waitingForConnection = true,
        statuses =
            statuses +
                statusKeysFor(move).associateWith { key ->
                    (statuses[key] ?: BudgetReorderStatus()).copy(message = message, warning = true)
                },
    )
}

internal fun BudgetReorderQueueState.discardOperation(operationId: String): BudgetReorderQueueState {
    val move = moves.firstOrNull { it.operationId == operationId } ?: return this
    val discarded = failHead(move, "").copy(waitingForConnection = false)
    return discarded.copy(
        statuses =
            discarded.statuses.mapValues { (_, status) ->
                if (status.message.isNullOrEmpty()) status.copy(message = null, warning = false) else status
            },
    )
}

internal fun BudgetReorderQueueState.resumeAfterConnection(): BudgetReorderQueueState {
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

internal fun BudgetReorderQueueState.clearCompletedVisibilityOverrides(budgetId: String): BudgetReorderQueueState {
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

internal fun BudgetReorderQueueState.failAll(message: String): BudgetReorderQueueState {
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

internal fun BudgetReorderQueueState.failHead(
    move: QueuedBudgetReorder,
    message: String,
): BudgetReorderQueueState {
    if (moves.firstOrNull() != move) return this
    if (move.kind == BudgetReorderKind.SET_ORDER) {
        val remainingMoves = moves.drop(1)
        return copy(
            moves = remainingMoves,
            statuses = statuses + settleOrderBatch(move, remainingMoves, message.ifEmpty { null }, keepOptimisticOrder = false),
            waitingForConnection = false,
        )
    }
    val failedMoves = failedMovesFor(move)
    val failedSet = failedMoves.toSet()
    val remainingMoves = moves.filterNot { it in failedSet }
    val nextStatuses = failedStatuses(failedMoves, remainingMoves, message)
    return copy(
        moves = remainingMoves,
        statuses = statuses + nextStatuses,
        waitingForConnection = false,
    ).clearFailedCategoryOverrides(failedMoves, remainingMoves)
        .clearFailedGroupOverrides(failedMoves, remainingMoves)
}

private fun BudgetReorderQueueState.failedMovesFor(move: QueuedBudgetReorder): List<QueuedBudgetReorder> =
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

private fun BudgetReorderQueueState.failedStatuses(
    failedMoves: List<QueuedBudgetReorder>,
    remainingMoves: List<QueuedBudgetReorder>,
    message: String,
): Map<String, BudgetReorderStatus> {
    val affectedKeys = failedMoves.map { budgetReorderStatusKey(it.budgetId, it.scopeId) }.distinct()
    return affectedKeys.associateWith { key ->
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
}

private fun BudgetReorderQueueState.clearFailedCategoryOverrides(
    failedMoves: List<QueuedBudgetReorder>,
    remainingMoves: List<QueuedBudgetReorder>,
): BudgetReorderQueueState {
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
        categoryVisibilityOverrides = categoryVisibilityOverrides - visibilityKeys,
        categoryNameOverrides = categoryNameOverrides - categoryNameKeys,
        categoryGroupIdOverrides = categoryGroupIdOverrides - categoryMoveKeys,
        deletedCategoryIds = deletedCategoryIds - deletedCategoryKeys,
    )
}

private fun BudgetReorderQueueState.clearFailedGroupOverrides(
    failedMoves: List<QueuedBudgetReorder>,
    remainingMoves: List<QueuedBudgetReorder>,
): BudgetReorderQueueState {
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
    return copy(
        categoryGroupNameOverrides = categoryGroupNameOverrides - groupNameKeys,
        categoryGroupVisibilityOverrides = categoryGroupVisibilityOverrides - groupVisibilityKeys,
    )
}

internal fun BudgetReorderQueueState.clearMessage(
    budgetId: String,
    scopeId: String,
): BudgetReorderQueueState {
    if (waitingForConnection) return this
    val key = budgetReorderStatusKey(budgetId, scopeId)
    val current = statusFor(budgetId, scopeId)
    return copy(statuses = statuses + (key to current.copy(message = null, warning = false)))
}
