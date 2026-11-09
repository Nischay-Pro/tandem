package app.tandemforactual.ui

internal const val BUDGET_GROUP_ORDER_SCOPE = "category-groups"

internal enum class BudgetReorderKind {
    CATEGORY,
    CATEGORY_GROUP,
}

internal data class QueuedBudgetReorder(
    val budgetId: String,
    val scopeId: String,
    val kind: BudgetReorderKind,
    val itemId: String,
    val targetId: String?,
    val desiredOrder: List<String>,
    val encryptionPassword: String?,
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
) {
    fun movesFor(
        budgetId: String,
        scopeId: String,
    ): List<QueuedBudgetReorder> = moves.filter { it.budgetId == budgetId && it.scopeId == scopeId }

    fun statusFor(
        budgetId: String,
        scopeId: String,
    ): BudgetReorderStatus = statuses[budgetReorderStatusKey(budgetId, scopeId)] ?: BudgetReorderStatus()

    fun enqueue(move: QueuedBudgetReorder): BudgetReorderQueueState {
        val key = budgetReorderStatusKey(move.budgetId, move.scopeId)
        val current = statusFor(move.budgetId, move.scopeId)
        return copy(
            moves = moves + move,
            statuses =
                statuses +
                    (
                        key to
                            current.copy(
                                pendingCount = current.pendingCount + 1,
                                message = null,
                                warning = false,
                                optimisticOrder = move.desiredOrder,
                            )
                    ),
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
        return copy(moves = remainingMoves, statuses = statuses + (key to nextStatus))
    }

    fun failAll(message: String): BudgetReorderQueueState {
        val failedKeys = moves.map { budgetReorderStatusKey(it.budgetId, it.scopeId) }.distinct()
        return copy(
            moves = emptyList(),
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

    fun clearMessage(
        budgetId: String,
        scopeId: String,
    ): BudgetReorderQueueState {
        val key = budgetReorderStatusKey(budgetId, scopeId)
        val current = statusFor(budgetId, scopeId)
        return copy(statuses = statuses + (key to current.copy(message = null, warning = false)))
    }
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
