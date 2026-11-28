package app.tandemforactual.ui.core.session

import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun BudgetReorderQueueState.enqueue(move: QueuedBudgetReorder): BudgetReorderQueueState {
    val key = budgetReorderStatusKey(move.budgetId, move.scopeId)
    val current = statusFor(move.budgetId, move.scopeId)
    val hiddenOverride = categoryHiddenOverride(move)
    val groupHiddenOverride = categoryGroupHiddenOverride(move)
    val groupNameOverride = categoryGroupNameOverride(move)
    val categoryNameOverride =
        move.name?.takeIf { move.kind in setOf(BudgetReorderKind.RENAME_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_RENAME) }
    val hasOrderingChange = hasOrderingChange(move)
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

private fun categoryHiddenOverride(move: QueuedBudgetReorder): Boolean? =
    when (move.kind) {
        BudgetReorderKind.HIDE_CATEGORY -> true
        BudgetReorderKind.SHOW_CATEGORY -> false
        else -> null
    }

private fun categoryGroupHiddenOverride(move: QueuedBudgetReorder): Boolean? =
    when (move.kind) {
        BudgetReorderKind.HIDE_CATEGORY_GROUP -> true
        BudgetReorderKind.SHOW_CATEGORY_GROUP -> false
        else -> null
    }

private fun categoryGroupNameOverride(move: QueuedBudgetReorder): String? =
    move.name?.takeIf {
        move.kind in
            setOf(
                BudgetReorderKind.RENAME_CATEGORY_GROUP,
                BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME,
            )
    }

private fun hasOrderingChange(move: QueuedBudgetReorder): Boolean =
    move.kind in
        setOf(
            BudgetReorderKind.CATEGORY,
            BudgetReorderKind.CATEGORY_GROUP,
            BudgetReorderKind.HIDE_CATEGORY,
            BudgetReorderKind.SHOW_CATEGORY,
            BudgetReorderKind.CREATE_CATEGORY_GROUP,
            BudgetReorderKind.CREATE_CATEGORY,
            BudgetReorderKind.MOVE_ACCOUNT,
        )

/**
 * Queues a reorder draft. A new drop on a list replaces that list's unsent
 * draft (keeping its starting order), so a burst of drags becomes one change.
 */
internal fun BudgetReorderQueueState.enqueueOrderDraft(move: QueuedBudgetReorder): BudgetReorderQueueState {
    val lastIndex = moves.indexOfLast { it.budgetId == move.budgetId && it.scopeId == move.scopeId }
    val existing = moves.getOrNull(lastIndex)
    if (existing == null || !existing.isOrderDraft() || existing.kind != move.kind || !move.isOrderDraft()) {
        return enqueue(move)
    }
    val merged =
        existing.copy(
            itemId = move.itemId,
            targetId = move.targetId,
            desiredOrder = move.desiredOrder,
            payloadJson = move.payloadJson,
            encryptionPassword = move.encryptionPassword ?: existing.encryptionPassword,
            requiresEncryptionPassword =
                existing.requiresEncryptionPassword || move.requiresEncryptionPassword || move.encryptionPassword != null,
        )
    val key = budgetReorderStatusKey(move.budgetId, move.scopeId)
    return copy(
        moves = moves.toMutableList().also { it[lastIndex] = merged },
        statuses =
            statuses +
                (
                    key to
                        statusFor(move.budgetId, move.scopeId).copy(
                            message = null,
                            warning = false,
                            optimisticOrder = move.desiredOrder,
                        )
                ),
    )
}

/** True when the queue starts with reorder drafts that [foldOrderDrafts] can send together. */
internal fun BudgetReorderQueueState.hasOrderDraftsAtHead(): Boolean = moves.firstOrNull()?.isOrderDraft() == true

/** True when something other than a reorder draft is waiting behind the leading drafts. */
internal fun BudgetReorderQueueState.hasWorkBehindOrderDrafts(): Boolean {
    val head = moves.firstOrNull() ?: return false
    if (!head.isOrderDraft()) return false
    return moves.dropWhile { it.isOrderDraft() && it.budgetId == head.budgetId }.isNotEmpty()
}

/**
 * Replaces the leading reorder drafts with one [BudgetReorderKind.SET_ORDER]
 * entry: one request, one idempotency key. Each list keeps the order it started
 * from and the latest order the user chose. The lists' optimistic orders stay on
 * screen until that entry completes.
 */
internal fun BudgetReorderQueueState.foldOrderDrafts(batchOperationId: String = Uuid.random().toString()): BudgetReorderQueueState {
    val head = moves.firstOrNull() ?: return this
    if (!head.isOrderDraft()) return this
    val drafts =
        moves.takeWhile {
            it.isOrderDraft() && it.budgetId == head.budgetId && it.encryptionPassword == head.encryptionPassword
        }
    val changes = LinkedHashMap<Pair<BudgetReorderKind, String>, Pair<List<String>, List<String>>>()
    drafts.forEach { draft ->
        val key = draft.kind to draft.scopeId
        changes[key] = (changes[key]?.first ?: draft.baseOrder) to draft.desiredOrder
    }

    fun change(
        base: List<String>,
        order: List<String>,
    ) = buildJsonObject {
        val (normalizedBase, normalizedOrder) = normalizeOrderChange(base, order)
        put("baseOrder", buildJsonArray { normalizedBase.forEach { add(JsonPrimitive(it)) } })
        put("order", buildJsonArray { normalizedOrder.forEach { add(JsonPrimitive(it)) } })
    }
    val order =
        buildJsonObject {
            changes.entries.firstOrNull { it.key.first == BudgetReorderKind.CATEGORY_GROUP }?.let { (_, lists) ->
                put("categoryGroups", change(lists.first, lists.second))
            }
            val categoryChanges = changes.entries.filter { it.key.first == BudgetReorderKind.CATEGORY }
            if (categoryChanges.isNotEmpty()) {
                put(
                    "categories",
                    buildJsonArray {
                        categoryChanges.forEach { (key, lists) ->
                            add(
                                buildJsonObject {
                                    // Hidden categories are reordered as a subset of the group's categories.
                                    put("groupId", key.second.removeSuffix(HIDDEN_CATEGORY_SCOPE_SUFFIX))
                                    change(lists.first, lists.second).forEach { (name, value) -> put(name, value) }
                                },
                            )
                        }
                    },
                )
            }
            changes.entries.firstOrNull { it.key.first == BudgetReorderKind.MOVE_ACCOUNT }?.let { (_, lists) ->
                put("accounts", change(lists.first, lists.second))
            }
        }
    val batch =
        QueuedBudgetReorder(
            budgetId = head.budgetId,
            scopeId = ORDER_BATCH_SCOPE,
            kind = BudgetReorderKind.SET_ORDER,
            itemId = head.budgetId,
            targetId = null,
            desiredOrder = emptyList(),
            encryptionPassword = head.encryptionPassword,
            requiresEncryptionPassword = drafts.any { it.requiresEncryptionPassword },
            payloadJson =
                buildJsonObject {
                    put("order", order)
                    put("scopes", buildJsonArray { drafts.map { it.scopeId }.distinct().forEach { add(JsonPrimitive(it)) } })
                }.toString(),
            operationId = batchOperationId,
        )
    return copy(
        moves = listOf(batch) + moves.drop(drafts.size),
        statuses =
            statuses +
                (budgetReorderStatusKey(head.budgetId, ORDER_BATCH_SCOPE) to BudgetReorderStatus(pendingCount = 1)),
    )
}

/** Status keys a move affects: its own list, plus every list a batched reorder covers. */
internal fun BudgetReorderQueueState.statusKeysFor(move: QueuedBudgetReorder): List<String> =
    listOf(budgetReorderStatusKey(move.budgetId, move.scopeId)) +
        move.orderBatchScopes().map { budgetReorderStatusKey(move.budgetId, it) }

/** Updates the lists a finished or failed batch covered, based on what is still queued for each. */
internal fun BudgetReorderQueueState.settleOrderBatch(
    move: QueuedBudgetReorder,
    remainingMoves: List<QueuedBudgetReorder>,
    message: String?,
    keepOptimisticOrder: Boolean,
): Map<String, BudgetReorderStatus> =
    statusKeysFor(move).associateWith { key ->
        val remainingCount = remainingMoves.count { budgetReorderStatusKey(it.budgetId, it.scopeId) == key }
        val current = statuses[key] ?: BudgetReorderStatus()
        when {
            remainingCount == 0 && !keepOptimisticOrder && message == null -> {
                BudgetReorderStatus()
            }

            remainingCount == 0 && !keepOptimisticOrder -> {
                BudgetReorderStatus(message = message, warning = true)
            }

            else -> {
                current.copy(
                    pendingCount = remainingCount,
                    message = message ?: current.message,
                    warning = message != null || current.warning,
                )
            }
        }
    }
