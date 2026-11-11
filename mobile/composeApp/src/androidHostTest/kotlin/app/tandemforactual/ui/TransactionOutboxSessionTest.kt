package app.tandemforactual.ui

import app.tandemforactual.data.database.OutboxOperationEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransactionOutboxSessionTest {
    @Test
    fun preSubmissionOfflineFailureRemainsAmendableButSendingOrServerOwnedWritesDoNot() {
        val failedOfflineEdit = pendingWrite(action = "UPDATE", attemptCount = 2, lastErrorCode = "network-timeout")

        assertEquals(
            failedOfflineEdit.operationId,
            queuedTransactionEditToDiscardForNoOp(listOf(failedOfflineEdit), editingLocalCreate = false),
        )
        assertNull(queuedTransactionEditToDiscardForNoOp(listOf(failedOfflineEdit), editingLocalCreate = true))

        listOf("sending", "uncertain", "conflict").forEach { status ->
            val blocked = failedOfflineEdit.copy(status = status)
            assertNull(queuedTransactionEditToDiscardForNoOp(listOf(blocked), editingLocalCreate = false), status)
        }
        assertNull(
            queuedTransactionEditToDiscardForNoOp(
                listOf(failedOfflineEdit.copy(serverOperationId = "server-operation-1")),
                editingLocalCreate = false,
            ),
        )
    }

    @Test
    fun failedLocalCreateKeepsItsOriginalOperationEligibleForEditing() {
        val failedOfflineCreate = pendingWrite(action = "CREATE", attemptCount = 2, lastErrorCode = "network-timeout")

        assertEquals(failedOfflineCreate, queuedTransactionCreateToEdit(listOf(failedOfflineCreate)))
        listOf("sending", "uncertain", "conflict").forEach { status ->
            assertNull(queuedTransactionCreateToEdit(listOf(failedOfflineCreate.copy(status = status))), status)
        }
        assertNull(
            queuedTransactionCreateToEdit(
                listOf(failedOfflineCreate.copy(serverOperationId = "server-operation-1")),
            ),
        )
    }

    private fun pendingWrite(
        action: String,
        attemptCount: Int,
        lastErrorCode: String?,
    ): OutboxOperationEntity =
        OutboxOperationEntity(
            operationId = "operation-1",
            idempotencyKey = "key-1",
            budgetId = "budget-1",
            recordType = "transaction",
            recordId = "transaction-1",
            action = action,
            baseRevision = "revision-1",
            baseSnapshotJson = "{}",
            patchJson = "{}",
            optimisticJson = "{}",
            optimisticRecordDate = null,
            optimisticAccountId = null,
            optimisticCategoryId = null,
            optimisticPayeeId = null,
            optimisticAmountMinor = null,
            optimisticBudgetMonth = null,
            status = "queued",
            attemptCount = attemptCount,
            serverOperationId = null,
            lastErrorCode = lastErrorCode,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 2L,
        )
}
