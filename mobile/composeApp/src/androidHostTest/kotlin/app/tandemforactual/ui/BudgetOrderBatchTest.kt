package app.tandemforactual.ui

import app.tandemforactual.ui.core.session.BUDGET_GROUP_ORDER_SCOPE
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.BudgetReorderQueueState
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.complete
import app.tandemforactual.ui.core.session.decodeBudgetReorderQueue
import app.tandemforactual.ui.core.session.encodeBudgetReorderQueue
import app.tandemforactual.ui.core.session.enqueue
import app.tandemforactual.ui.core.session.enqueueOrderDraft
import app.tandemforactual.ui.core.session.failHead
import app.tandemforactual.ui.core.session.foldOrderDrafts
import app.tandemforactual.ui.core.session.hasWorkBehindOrderDrafts
import app.tandemforactual.ui.core.session.isOrderDraft
import app.tandemforactual.ui.core.session.normalizeOrderChange
import app.tandemforactual.ui.core.session.orderBatchScopes
import app.tandemforactual.ui.core.session.statusFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BudgetOrderBatchTest {
    private val budgetId = "budget-1"

    private fun categoryDraft(
        groupId: String,
        base: List<String>,
        order: List<String>,
        itemId: String = order.first(),
    ) = QueuedBudgetReorder(
        budgetId = budgetId,
        scopeId = groupId,
        kind = BudgetReorderKind.CATEGORY,
        itemId = itemId,
        targetId = null,
        desiredOrder = order,
        encryptionPassword = null,
        baseOrder = base,
    )

    private fun groupDraft(
        base: List<String>,
        order: List<String>,
    ) = QueuedBudgetReorder(
        budgetId = budgetId,
        scopeId = BUDGET_GROUP_ORDER_SCOPE,
        kind = BudgetReorderKind.CATEGORY_GROUP,
        itemId = order.first(),
        targetId = null,
        desiredOrder = order,
        encryptionPassword = null,
        baseOrder = base,
    )

    private fun QueuedBudgetReorder.orderBody(): JsonObject = Json.parseToJsonElement(payloadJson!!).jsonObject["order"]!!.jsonObject

    private fun JsonObject.ids(name: String): List<String> = (this[name] as JsonArray).map { it.jsonPrimitive.contentOrNull!! }

    @Test
    fun repeatedDropsOnOneListBecomeOneDraftThatKeepsTheStartingOrder() {
        val queue =
            BudgetReorderQueueState()
                .enqueueOrderDraft(categoryDraft("fashion", base = listOf("a", "b", "c", "d"), order = listOf("b", "a", "c", "d")))
                .enqueueOrderDraft(categoryDraft("fashion", base = listOf("b", "a", "c", "d"), order = listOf("b", "c", "a", "d")))
                .enqueueOrderDraft(categoryDraft("fashion", base = listOf("b", "c", "a", "d"), order = listOf("b", "a", "d", "c")))

        val draft = queue.moves.single()
        assertEquals(listOf("a", "b", "c", "d"), draft.baseOrder)
        assertEquals(listOf("b", "a", "d", "c"), draft.desiredOrder)
        val status = queue.statusFor(budgetId, "fashion")
        assertEquals(1, status.pendingCount)
        assertEquals(listOf("b", "a", "d", "c"), status.optimisticOrder)
    }

    @Test
    fun aDropAfterAnotherKindOfChangeStartsANewDraft() {
        val hide =
            QueuedBudgetReorder(
                budgetId = budgetId,
                scopeId = "fashion",
                kind = BudgetReorderKind.HIDE_CATEGORY,
                itemId = "a",
                targetId = null,
                desiredOrder = listOf("b", "c"),
                encryptionPassword = null,
            )
        val queue =
            BudgetReorderQueueState()
                .enqueueOrderDraft(categoryDraft("fashion", base = listOf("a", "b", "c"), order = listOf("b", "a", "c")))
                .enqueue(hide)
                .enqueueOrderDraft(categoryDraft("fashion", base = listOf("b", "c"), order = listOf("c", "b")))

        assertEquals(3, queue.moves.size)
        assertTrue(queue.hasWorkBehindOrderDrafts())
    }

    @Test
    fun leadingDraftsFoldIntoOneRequestForEveryList() {
        val queue =
            BudgetReorderQueueState()
                .enqueueOrderDraft(groupDraft(base = listOf("bills", "fashion"), order = listOf("fashion", "bills")))
                .enqueueOrderDraft(categoryDraft("fashion", base = listOf("a", "b"), order = listOf("b", "a")))
                .enqueueOrderDraft(categoryDraft("bills", base = listOf("x", "y"), order = listOf("y", "x")))

        val folded = queue.foldOrderDrafts("batch-1")

        val batch = folded.moves.single()
        assertEquals(BudgetReorderKind.SET_ORDER, batch.kind)
        assertEquals("batch-1", batch.operationId)
        assertEquals(listOf(BUDGET_GROUP_ORDER_SCOPE, "fashion", "bills"), batch.orderBatchScopes())
        val body = batch.orderBody()
        assertEquals(listOf("fashion", "bills"), body["categoryGroups"]!!.jsonObject.ids("order"))
        val categories = body["categories"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("fashion", "bills"), categories.map { it["groupId"]!!.jsonPrimitive.contentOrNull })
        assertEquals(listOf("a", "b"), categories.first().ids("baseOrder"))
        assertEquals(listOf("b", "a"), categories.first().ids("order"))
        assertNull(body["accounts"])
        // Each list keeps showing the chosen order until the batch is saved.
        assertEquals(listOf("b", "a"), folded.statusFor(budgetId, "fashion").optimisticOrder)
    }

    @Test
    fun completingABatchReturnsEveryCoveredListToTheServerOrder() {
        val folded =
            BudgetReorderQueueState()
                .enqueueOrderDraft(groupDraft(base = listOf("bills", "fashion"), order = listOf("fashion", "bills")))
                .enqueueOrderDraft(categoryDraft("fashion", base = listOf("a", "b"), order = listOf("b", "a")))
                .foldOrderDrafts("batch-1")

        val completed = folded.complete(folded.moves.single(), refreshFailed = false, message = null)

        assertTrue(completed.moves.isEmpty())
        assertNull(completed.statusFor(budgetId, "fashion").optimisticOrder)
        assertNull(completed.statusFor(budgetId, BUDGET_GROUP_ORDER_SCOPE).optimisticOrder)
        assertEquals(0, completed.statusFor(budgetId, "fashion").pendingCount)
    }

    @Test
    fun aBatchWhoseRefreshFailedKeepsTheChosenOrderOnScreen() {
        val folded =
            BudgetReorderQueueState()
                .enqueueOrderDraft(categoryDraft("fashion", base = listOf("a", "b"), order = listOf("b", "a")))
                .foldOrderDrafts("batch-1")

        val completed = folded.complete(folded.moves.single(), refreshFailed = true, message = null)

        val status = completed.statusFor(budgetId, "fashion")
        assertEquals(listOf("b", "a"), status.optimisticOrder)
        assertTrue(status.warning)
    }

    @Test
    fun aFailedBatchRestoresEveryCoveredListAndExplainsWhy() {
        val folded =
            BudgetReorderQueueState()
                .enqueueOrderDraft(categoryDraft("fashion", base = listOf("a", "b"), order = listOf("b", "a")))
                .foldOrderDrafts("batch-1")

        val failed = folded.failHead(folded.moves.single(), "Couldn’t save the new order.")

        val status = failed.statusFor(budgetId, "fashion")
        assertNull(status.optimisticOrder)
        assertEquals("Couldn’t save the new order.", status.message)
        assertFalse(failed.waitingForConnection)
    }

    @Test
    fun foldingStopsAtTheFirstChangeThatIsNotADraft() {
        val rename =
            QueuedBudgetReorder(
                budgetId = budgetId,
                scopeId = "fashion",
                kind = BudgetReorderKind.RENAME_CATEGORY,
                itemId = "a",
                targetId = null,
                desiredOrder = emptyList(),
                encryptionPassword = null,
                name = "Apparel",
            )
        val folded =
            BudgetReorderQueueState()
                .enqueueOrderDraft(categoryDraft("fashion", base = listOf("a", "b"), order = listOf("b", "a")))
                .enqueue(rename)
                .enqueueOrderDraft(categoryDraft("bills", base = listOf("x", "y"), order = listOf("y", "x")))
                .foldOrderDrafts("batch-1")

        assertEquals(
            listOf(BudgetReorderKind.SET_ORDER, BudgetReorderKind.RENAME_CATEGORY, BudgetReorderKind.CATEGORY),
            folded.moves.map { it.kind },
        )
        assertEquals(listOf("fashion"), folded.moves.first().orderBatchScopes())
    }

    @Test
    fun legacyMovesWithoutAStartingOrderAreSentOneByOne() {
        val legacy = categoryDraft("fashion", base = emptyList(), order = listOf("b", "a"))
        assertFalse(legacy.isOrderDraft())
        val queue = BudgetReorderQueueState().enqueueOrderDraft(legacy)
        assertEquals(queue, queue.foldOrderDrafts("batch-1"))
    }

    @Test
    fun itemsThatAppearOrDisappearMidBurstAreTreatedAsUnmoved() {
        // "n" was created during the burst and "gone" was deleted.
        val (base, order) = normalizeOrderChange(listOf("a", "gone", "b", "c"), listOf("c", "a", "n", "b"))
        assertEquals(listOf("a", "n", "b", "c"), base)
        assertEquals(listOf("c", "a", "n", "b"), order)
    }

    @Test
    fun aBatchSurvivesARestartWithItsListsAndPayload() {
        val folded =
            BudgetReorderQueueState()
                .enqueueOrderDraft(categoryDraft("fashion", base = listOf("a", "b"), order = listOf("b", "a")))
                .foldOrderDrafts("batch-1")

        val restored = decodeBudgetReorderQueue(encodeBudgetReorderQueue(folded))

        assertEquals(folded.moves, restored.moves)
        val draft =
            decodeBudgetReorderQueue(
                encodeBudgetReorderQueue(
                    BudgetReorderQueueState().enqueueOrderDraft(categoryDraft("fashion", listOf("a", "b"), listOf("b", "a"))),
                ),
            ).moves.single()
        assertEquals(listOf("a", "b"), draft.baseOrder)
    }
}
