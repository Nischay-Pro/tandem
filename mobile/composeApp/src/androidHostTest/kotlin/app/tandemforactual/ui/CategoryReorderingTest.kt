package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CategoryReorderingTest {
    @Test
    fun transientServerResponsesKeepQueuedBudgetChangesForRetry() {
        assertTrue(isRetryableBudgetMutationStatus(408))
        assertTrue(isRetryableBudgetMutationStatus(425))
        assertTrue(isRetryableBudgetMutationStatus(429))
        assertTrue(isRetryableBudgetMutationStatus(502))
        assertTrue(isRetryableBudgetMutationStatus(503))
        assertTrue(isRetryableBudgetMutationStatus(504))
        assertFalse(isRetryableBudgetMutationStatus(400))
        assertFalse(isRetryableBudgetMutationStatus(401))
        assertFalse(isRetryableBudgetMutationStatus(404))
        assertFalse(isRetryableBudgetMutationStatus(null))
        assertTrue(shouldKeepBudgetMutationQueued(null))
        assertTrue(shouldKeepBudgetMutationQueued(503))
        assertFalse(shouldKeepBudgetMutationQueued(401))
    }

    @Test
    fun reorderFailureExplainsThatTheOriginalOrderWasRestored() {
        assertEquals(
            "Couldn’t reach the Tandem server. Check the address and connection. The original order was restored.",
            budgetReorderFailureMessage("Couldn’t reach the Tandem server. Check the address and connection."),
        )
        assertEquals(
            "Couldn’t reorder this item. The original order was restored.",
            budgetReorderFailureMessage(null),
        )
    }

    @Test
    fun movesAnItemDirectlyToTheTopAndTargetsTheFollowingItem() {
        val order = listOf("rent", "mortgage", "maintenance", "utilities")

        val reordered = reorderItemToIndex(order, "utilities", 0)

        assertEquals(listOf("utilities", "rent", "mortgage", "maintenance"), reordered)
        assertEquals("rent", itemTargetIdAfterMove(reordered, "utilities"))
    }

    @Test
    fun movesAnItemToTheEndUsingANullTarget() {
        val order = listOf("rent", "mortgage", "maintenance", "utilities")

        val reordered = reorderItemToIndex(order, "maintenance", order.lastIndex)

        assertEquals(listOf("rent", "mortgage", "utilities", "maintenance"), reordered)
        assertEquals(null, itemTargetIdAfterMove(reordered, "maintenance"))
    }

    @Test
    fun leavesMissingAndUnmovedItemsAlone() {
        val order = listOf("rent", "mortgage", "utilities")

        assertEquals(order, reorderItemToIndex(order, "missing", 0))
        assertEquals(order, reorderItemToIndex(order, "mortgage", 1))
        assertEquals(null, itemTargetIdAfterMove(order, "missing"))
    }

    @Test
    fun queuedMovesReplayInOrderAfterLeavingAndReturningToTheGroup() {
        val originalOrder = listOf("rent", "mortgage", "maintenance", "utilities")
        val firstMove =
            QueuedBudgetReorder(
                budgetId = "budget",
                scopeId = "housing",
                kind = BudgetReorderKind.CATEGORY,
                itemId = "utilities",
                targetId = "rent",
                desiredOrder = listOf("utilities", "rent", "mortgage", "maintenance"),
                encryptionPassword = null,
            )
        val secondMove =
            QueuedBudgetReorder(
                budgetId = "budget",
                scopeId = "housing",
                kind = BudgetReorderKind.CATEGORY,
                itemId = "mortgage",
                targetId = null,
                desiredOrder = listOf("utilities", "rent", "maintenance", "mortgage"),
                encryptionPassword = null,
            )
        val queue =
            BudgetReorderQueueState()
                .enqueue(firstMove)
                .enqueue(secondMove)

        assertEquals(
            listOf("utilities", "rent", "maintenance", "mortgage"),
            optimisticBudgetOrder(originalOrder, queue.movesFor("budget", "housing")),
        )
        assertEquals(2, queue.statusFor("budget", "housing").pendingCount)
    }

    @Test
    fun failedQueueClearsOptimisticOrderAndReportsRollback() {
        val originalOrder = listOf("rent", "mortgage", "maintenance")
        val move =
            QueuedBudgetReorder(
                budgetId = "budget",
                scopeId = "housing",
                kind = BudgetReorderKind.CATEGORY,
                itemId = "maintenance",
                targetId = "rent",
                desiredOrder = listOf("maintenance", "rent", "mortgage"),
                encryptionPassword = null,
            )
        val failed =
            BudgetReorderQueueState()
                .enqueue(move)
                .failAll("Couldn’t reorder this category. The order was restored.")

        assertEquals(emptyList(), failed.moves)
        assertEquals(
            originalOrder,
            budgetOrderForScreen(originalOrder, failed.statusFor("budget", "housing"), failed.movesFor("budget", "housing")),
        )
        assertEquals(0, failed.statusFor("budget", "housing").pendingCount)
        assertEquals(
            "Couldn’t reorder this category. The order was restored.",
            failed.statusFor("budget", "housing").message,
        )
        assertEquals(null, failed.statusFor("budget", "housing").optimisticOrder)
    }

    @Test
    fun refreshFailureKeepsAppliedOrderAndExplainsTheStaleLocalCopy() {
        val originalOrder = listOf("rent", "mortgage", "maintenance")
        val desiredOrder = listOf("maintenance", "rent", "mortgage")
        val move =
            QueuedBudgetReorder(
                budgetId = "budget",
                scopeId = "housing",
                kind = BudgetReorderKind.CATEGORY,
                itemId = "maintenance",
                targetId = "rent",
                desiredOrder = desiredOrder,
                encryptionPassword = null,
            )
        val completed =
            BudgetReorderQueueState()
                .enqueue(move)
                .complete(move, refreshFailed = true, "Actual was updated, but this device couldn’t refresh the budget.")

        assertEquals(emptyList(), completed.moves)
        assertEquals(
            desiredOrder,
            budgetOrderForScreen(originalOrder, completed.statusFor("budget", "housing"), completed.movesFor("budget", "housing")),
        )
        assertEquals(0, completed.statusFor("budget", "housing").pendingCount)
        assertEquals(true, completed.statusFor("budget", "housing").warning)
    }

    @Test
    fun successfulQueueCompletionClearsPendingState() {
        val move =
            QueuedBudgetReorder(
                budgetId = "budget",
                scopeId = "housing",
                kind = BudgetReorderKind.CATEGORY,
                itemId = "maintenance",
                targetId = "rent",
                desiredOrder = listOf("maintenance", "rent", "mortgage"),
                encryptionPassword = null,
            )
        val completed =
            BudgetReorderQueueState()
                .enqueue(move)
                .complete(move, refreshFailed = false, message = null)

        assertEquals(emptyList(), completed.moves)
        assertEquals(BudgetReorderStatus(), completed.statusFor("budget", "housing"))
    }

    @Test
    fun categoryGroupMovesUseTheSameRouteIndependentQueue() {
        val move =
            QueuedBudgetReorder(
                budgetId = "budget",
                scopeId = BUDGET_GROUP_ORDER_SCOPE,
                kind = BudgetReorderKind.CATEGORY_GROUP,
                itemId = "utilities",
                targetId = "housing",
                desiredOrder = listOf("utilities", "housing", "transportation"),
                encryptionPassword = null,
            )
        val queue = BudgetReorderQueueState().enqueue(move)

        assertEquals(listOf(move), queue.movesFor("budget", BUDGET_GROUP_ORDER_SCOPE))
        assertEquals(
            listOf("utilities", "housing", "transportation"),
            budgetOrderForScreen(
                listOf("housing", "transportation", "utilities"),
                queue.statusFor("budget", BUDGET_GROUP_ORDER_SCOPE),
                queue.movesFor("budget", BUDGET_GROUP_ORDER_SCOPE),
            ),
        )
        assertEquals(1, queue.statusFor("budget", BUDGET_GROUP_ORDER_SCOPE).pendingCount)
    }

    @Test
    fun offlineVisibilityEditsStayOptimisticAndSurviveRestartWithoutSavingPasswords() {
        val hide =
            QueuedBudgetReorder(
                budgetId = "budget",
                scopeId = "utilities",
                kind = BudgetReorderKind.HIDE_CATEGORY,
                itemId = "mobile",
                targetId = null,
                desiredOrder = listOf("phone", "internet"),
                encryptionPassword = "not-persisted",
            )
        val queued = BudgetReorderQueueState().enqueue(hide)
        val encoded = encodeBudgetReorderQueue(queued)
        val restored = decodeBudgetReorderQueue(encoded)

        assertEquals(false, encoded.contains("not-persisted"))
        assertEquals(true, queued.categoryVisibilityOverridesFor("budget")["mobile"])
        assertEquals(listOf(hide.copy(encryptionPassword = null, requiresEncryptionPassword = true)), restored.moves)
        assertEquals(true, restored.categoryVisibilityOverridesFor("budget")["mobile"])
        assertEquals(1, restored.statusFor("budget", "utilities").pendingCount)
    }

    @Test
    fun categoryMoveWaitsOfflineWithoutRollingBackTheLocalOrder() {
        val originalOrder = listOf("rent", "utilities", "internet")
        val move =
            QueuedBudgetReorder(
                budgetId = "budget",
                scopeId = "housing",
                kind = BudgetReorderKind.CATEGORY,
                itemId = "internet",
                targetId = "rent",
                desiredOrder = listOf("internet", "rent", "utilities"),
                encryptionPassword = null,
            )
        val waitingMessage = "Saved on this device. Waiting for a connection to Tandem."
        val waiting = BudgetReorderQueueState().enqueue(move).waitForConnection(move, waitingMessage)

        assertEquals(true, waiting.waitingForConnection)
        assertEquals(listOf(move), waiting.moves)
        assertEquals(
            move.desiredOrder,
            budgetOrderForScreen(originalOrder, waiting.statusFor("budget", "housing"), waiting.movesFor("budget", "housing")),
        )
        assertEquals(waitingMessage, waiting.statusFor("budget", "housing").message)

        val resumed = waiting.resumeAfterConnection()

        assertEquals(false, resumed.waitingForConnection)
        assertEquals(listOf(move), resumed.moves)
        assertEquals(move.desiredOrder, resumed.statusFor("budget", "housing").optimisticOrder)
    }

    @Test
    fun appliedCategoryMoveKeepsItsOptimisticOrderWhileBudgetRefreshWaits() {
        val originalOrder = listOf("rent", "utilities", "internet")
        val move =
            QueuedBudgetReorder(
                budgetId = "budget",
                scopeId = "housing",
                kind = BudgetReorderKind.CATEGORY,
                itemId = "internet",
                targetId = "rent",
                desiredOrder = listOf("internet", "rent", "utilities"),
                encryptionPassword = null,
            )
        val status = BudgetReorderQueueState().enqueue(move).awaitRefresh(move, "Saved in Actual. Waiting for the budget to refresh.")

        assertEquals(true, status.waitingForConnection)
        assertEquals(true, status.moves.single().refreshOnly)
        assertEquals(true, status.statusFor("budget", "housing").warning)
        assertEquals("Saved in Actual. Waiting for the budget to refresh.", status.statusFor("budget", "housing").message)
        assertEquals(
            move.desiredOrder,
            budgetOrderForScreen(originalOrder, status.statusFor("budget", "housing"), status.movesFor("budget", "housing")),
        )
    }

    @Test
    fun categoryHideWaitsOfflineAndKeepsTheVisibilityOverride() {
        val hide =
            QueuedBudgetReorder(
                budgetId = "budget",
                scopeId = "utilities",
                kind = BudgetReorderKind.HIDE_CATEGORY,
                itemId = "mobile",
                targetId = null,
                desiredOrder = listOf("internet"),
                encryptionPassword = null,
            )
        val waiting =
            BudgetReorderQueueState()
                .enqueue(hide)
                .waitForConnection(hide, "Saved on this device. Waiting for a connection to Tandem.")

        assertEquals(true, waiting.categoryVisibilityOverridesFor("budget")["mobile"])
        assertEquals(listOf(hide), waiting.moves)

        val resumed = waiting.resumeAfterConnection()

        assertEquals(true, resumed.categoryVisibilityOverridesFor("budget")["mobile"])
        assertEquals(listOf(hide), resumed.moves)
    }

    @Test
    fun categoryRenameQueuedBehindCreateUsesTheActualIdAfterCreateCompletes() {
        val create =
            QueuedBudgetReorder(
                budgetId = "budget",
                scopeId = "utilities",
                kind = BudgetReorderKind.CREATE_CATEGORY,
                itemId = "local-category-new",
                targetId = null,
                desiredOrder = listOf("phone", "local-category-new"),
                encryptionPassword = null,
                name = "New category",
                parentGroupId = "utilities",
            )
        val rename =
            QueuedBudgetReorder(
                budgetId = "budget",
                scopeId = "category-local-category-new",
                kind = BudgetReorderKind.RENAME_CATEGORY,
                itemId = "local-category-new",
                targetId = null,
                desiredOrder = emptyList(),
                encryptionPassword = null,
                name = "Renamed category",
            )
        val queued = BudgetReorderQueueState().enqueue(create).enqueue(rename)

        val created = queued.completeCategoryCreate(create, "actual-category-1")

        assertEquals("actual-category-1", created.moves.single().itemId)
        assertEquals("Renamed category", created.categoryNameOverridesFor("budget")["actual-category-1"])
        assertEquals("actual-category-1", created.resolveCategoryId("budget", "local-category-new"))
    }

    @Test
    fun deletingACategoryCreatedOfflineKeepsTheDeleteUntilTheActualIdIsKnown() {
        val create =
            QueuedBudgetReorder(
                budgetId = "budget",
                scopeId = "utilities",
                kind = BudgetReorderKind.CREATE_CATEGORY,
                itemId = "local-category-temporary",
                targetId = null,
                desiredOrder = listOf("local-category-temporary"),
                encryptionPassword = null,
                name = "Temporary category",
                parentGroupId = "utilities",
            )
        val delete =
            QueuedBudgetReorder(
                budgetId = "budget",
                scopeId = "category-local-category-temporary",
                kind = BudgetReorderKind.DELETE_CATEGORY,
                itemId = "local-category-temporary",
                targetId = null,
                desiredOrder = emptyList(),
                encryptionPassword = null,
            )
        val queued = BudgetReorderQueueState().enqueue(create).enqueue(delete)

        assertEquals(setOf("local-category-temporary"), queued.deletedCategoryIdsFor("budget"))
        val created = queued.completeCategoryCreate(create, "actual-category-2")
        assertEquals(BudgetReorderKind.DELETE_CATEGORY, created.moves.single().kind)
        assertEquals("actual-category-2", created.moves.single().itemId)
        assertEquals(setOf("actual-category-2"), created.deletedCategoryIdsFor("budget"))

        val deleted = created.complete(created.moves.single(), refreshFailed = false, message = null)

        assertEquals(emptyList(), deleted.moves)
        assertEquals(emptySet(), deleted.deletedCategoryIdsFor("budget"))
    }

    @Test
    fun queuedCategoryEditsSurviveDisconnectAndResumeWithoutBeingReplaced() {
        val rename =
            QueuedBudgetReorder(
                budgetId = "budget",
                scopeId = "category-groceries",
                kind = BudgetReorderKind.RENAME_CATEGORY,
                itemId = "groceries",
                targetId = null,
                desiredOrder = emptyList(),
                encryptionPassword = "budget-password",
                name = "Food",
            )
        val queued = BudgetReorderQueueState().enqueue(rename)
        val request = queued.moves.single()
        val waiting = queued.waitForConnection(request, "Waiting for a connection to Tandem.")
        val restored = decodeBudgetReorderQueue(encodeBudgetReorderQueue(waiting))

        assertEquals(true, restored.waitingForConnection)
        assertEquals("Food", restored.moves.single().name)
        assertEquals(null, restored.moves.single().encryptionPassword)
        assertEquals(true, restored.moves.single().requiresEncryptionPassword)

        val resumed = restored.resumeAfterConnection()

        assertEquals(false, resumed.waitingForConnection)
        assertEquals("Food", resumed.moves.single().name)
        assertEquals(1, resumed.statusFor("budget", "category-groceries").pendingCount)
    }
}
