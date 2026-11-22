package app.tandemforactual.ui.core.session.budget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.LocalBudgetMutationOutboxItem
import app.tandemforactual.data.store.recoverInterruptedBudgetMutations
import app.tandemforactual.ui.core.session.BUDGET_QUEUE_RESTORE_ERROR
import app.tandemforactual.ui.core.session.BudgetReorderQueueState
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits
import app.tandemforactual.ui.core.session.decodeBudgetReorderQueue
import app.tandemforactual.ui.core.session.decodeBudgetReorderQueueOperationOrder
import app.tandemforactual.ui.core.session.encodeBudgetReorderQueue
import app.tandemforactual.ui.core.session.encodeBudgetReorderQueueMetadata
import app.tandemforactual.ui.core.session.enqueue
import app.tandemforactual.ui.core.session.localStorageFailureMessage
import app.tandemforactual.ui.core.session.orderBudgetMutationQueueItems
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

internal suspend fun BudgetEditQueue.restoreBudgetReorderQueue(): Boolean =
    budgetQueueRestoreMutex.withLock {
        if (budgetQueueLoaded) return@withLock true
        val persistence = host.budgetMutationQueuePersistence
        if (persistence == null) {
            budgetQueueLoaded = true
            return@withLock true
        }

        try {
            // Runs once per session, before this queue sends anything, so any `sending` row
            // left behind belongs to a request from an earlier process.
            host.repository?.recoverInterruptedBudgetMutations()
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
                    val saved = host.preferencesStore?.read()?.get(AppPreferencesStore.PENDING_BUDGET_REORDERS_KEY)
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
                host.preferencesStore?.putValue(AppPreferencesStore.PENDING_BUDGET_REORDERS_KEY, "")
            }
            budgetReorderQueueState = restoredState
            budgetQueueLoaded = true
            if (host.budgetActionMessage == BUDGET_QUEUE_RESTORE_ERROR) host.feedback.clearBudgetActionMessage()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            host.feedback.setBudgetActionMessage(
                text = BUDGET_QUEUE_RESTORE_ERROR,
                actionLabel = "Try again",
                retryAction = {
                    val restored = restoreBudgetReorderQueue()
                    if (restored) host.feedback.clearBudgetActionMessage()
                    restored
                },
            )
            false
        }
    }

internal fun BudgetEditQueue.persistBudgetReorderQueue() {
    if (host.isSigningOut) return
    if (!budgetQueueLoaded) return
    if (host.budgetMutationQueuePersistence == null) return
    host.sessionScope.launch {
        persistCurrentBudgetReorderQueue()
    }
}

internal suspend fun BudgetEditQueue.persistCurrentBudgetReorderQueue(supersededOperationIds: Set<String> = emptySet()) {
    budgetQueuePersistenceMutex.withLock {
        persistBudgetReorderQueueNow(budgetReorderQueueState, supersededOperationIds)
    }
}

internal suspend fun BudgetEditQueue.persistBudgetReorderQueueNow(
    state: BudgetReorderQueueState,
    supersededOperationIds: Set<String> = emptySet(),
) {
    val persistence = host.budgetMutationQueuePersistence ?: return
    val persistedAt = host.nowEpochMillis()
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
    runCatching { host.preferencesStore?.putValue(AppPreferencesStore.PENDING_BUDGET_REORDERS_KEY, "") }
}

internal suspend fun BudgetEditQueue.persistBudgetReorderQueueMutation(
    transform: (BudgetReorderQueueState) -> BudgetReorderQueueState,
): Boolean {
    if (isPersistingBudgetMutation) {
        host.feedback.budgetActionMessage = "Saving a category change on this device. Try again in a moment."
        return false
    }
    isPersistingBudgetMutation = true
    try {
        if (!restoreBudgetReorderQueue()) return false
        if (host.budgetMutationQueuePersistence == null) {
            host.feedback.budgetActionMessage = "Tandem can’t save this change on this device. Restart the app and try again."
            return false
        }
        return budgetQueuePersistenceMutex.withLock {
            val next = transform(budgetReorderQueueState)
            try {
                persistBudgetReorderQueueNow(next)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                host.feedback.setBudgetActionMessage(
                    text = localStorageFailureMessage(error, "Couldn’t save this change on this device."),
                    actionLabel = "Try again",
                    retryAction = { persistBudgetReorderQueueMutation(transform) },
                )
                return@withLock false
            }
            budgetReorderQueueState = next
            host.feedback.budgetActionMessage = null
            true
        }
    } finally {
        isPersistingBudgetMutation = false
    }
}
