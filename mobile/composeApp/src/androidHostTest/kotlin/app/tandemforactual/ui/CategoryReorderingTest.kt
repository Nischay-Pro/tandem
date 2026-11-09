package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryReorderingTest {
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
}
