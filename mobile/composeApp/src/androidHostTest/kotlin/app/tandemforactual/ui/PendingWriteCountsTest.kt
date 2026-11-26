package app.tandemforactual.ui

import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.app.appMenuItems
import app.tandemforactual.ui.core.state.transactionConflicts
import app.tandemforactual.ui.core.state.unsentWrites
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingWriteCountsTest {
    private val writes =
        listOf(
            operation("queued", "transaction", "queued"),
            operation("rejected", "transaction", "rejected"),
            operation("committed", "transaction", "committed"),
            operation("discarded", "transaction", "discarded"),
            operation("superseded", "transaction", "superseded"),
            operation("transaction-conflict", "transaction", "conflict"),
            operation("budget-conflict", "budget-mutation", "conflict"),
            operation("schedule-conflict", "schedule-action", "conflict"),
        )

    @Test
    fun finishedHistoryIsNotPending() {
        assertEquals(
            listOf("queued", "rejected", "transaction-conflict", "budget-conflict", "schedule-conflict"),
            writes.unsentWrites().map { it.operationId },
        )
        assertEquals(emptyList(), writes.filter { it.status in setOf("committed", "discarded", "superseded") }.unsentWrites())
    }

    @Test
    fun conflictCountsOnlyIncludeTransactionsTheReviewScreenResolves() {
        assertEquals(listOf("transaction-conflict"), writes.transactionConflicts().map { it.operationId })
        assertEquals("1 to review", reviewConflictsHint(writes.transactionConflicts().size))
        assertNull(reviewConflictsHint(writes.filter { it.recordType != "transaction" }.transactionConflicts().size))
    }

    private fun reviewConflictsHint(conflictCount: Int): String? {
        val menu =
            appMenuItems(
                advanced = true,
                ruleCount = 0,
                scheduleCount = 0,
                payeeCount = 0,
                conflictCount = conflictCount,
                reimbursementsOwedMinor = 0,
                formatMoney = { it.toString() },
                readOnly = false,
                open = {},
                onAddTransaction = {},
                onScanReceipt = {},
                onManageBanks = {},
                onFeedback = {},
            )
        return menu.firstOrNull { it.label == "Review conflicts" }?.hint
    }

    private fun operation(
        id: String,
        recordType: String,
        status: String,
    ) = OutboxOperationEntity(
        operationId = id,
        idempotencyKey = id,
        budgetId = "budget",
        recordType = recordType,
        recordId = "record-$id",
        action = "UPDATE",
        baseRevision = null,
        baseSnapshotJson = null,
        patchJson = "{}",
        optimisticJson = null,
        optimisticRecordDate = null,
        optimisticAccountId = null,
        optimisticCategoryId = null,
        optimisticPayeeId = null,
        optimisticAmountMinor = null,
        optimisticBudgetMonth = null,
        status = status,
        attemptCount = 0,
        serverOperationId = null,
        lastErrorCode = null,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )
}
