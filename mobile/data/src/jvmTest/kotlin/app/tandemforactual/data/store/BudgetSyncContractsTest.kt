package app.tandemforactual.data.store

import app.tandemforactual.data.database.OutboxOperationEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BudgetSyncContractsTest {
    @Test
    fun conflictDetailsSupportLegacyStructuredAndMalformedValues() {
        assertEquals("legacy-error", encodeLocalWriteConflictDetails("legacy-error", null, emptyList()))
        assertNull(parseLocalWriteConflictDetails("legacy-error"))
        assertNull(parseLocalWriteConflictDetails(null))

        val encoded = encodeLocalWriteConflictDetails("field-conflict", "concurrent-edit", listOf("amount", "date"))
        assertEquals(
            LocalWriteConflictDetails("field-conflict", "concurrent-edit", listOf("amount", "date")),
            parseLocalWriteConflictDetails(encoded),
        )
        assertEquals(
            LocalWriteConflictDetails("field-conflict", null, emptyList()),
            parseLocalWriteConflictDetails("tandem-conflict-v1:{\"errorCode\":\"field-conflict\"}"),
        )
        assertNull(parseLocalWriteConflictDetails("tandem-conflict-v1:{"))
    }

    @Test
    fun budgetSyncRemoteEncryptionOverloadDelegatesToLegacyOperationReader() {
        val expected =
            RemoteOperationResult(
                "operation-1",
                "budget-1",
                "transaction-1",
                RemoteOperationStatus.QUEUED,
                null,
            )
        var readOperationId: String? = null
        val remote =
            object : BudgetSyncRemote {
                override suspend fun readSnapshot(
                    budgetId: String,
                    encryptionPassword: String?,
                ) = error("unused")

                override suspend fun readSnapshotPage(
                    budgetId: String,
                    generation: String?,
                    cursor: String?,
                    afterType: String?,
                    afterId: String?,
                    limit: Int,
                    encryptionPassword: String?,
                ) = error("unused")

                override suspend fun readChanges(
                    budgetId: String,
                    generation: String,
                    cursor: String,
                    throughCursor: String?,
                    encryptionPassword: String?,
                ) = error("unused")

                override suspend fun submitOperation(
                    operation: OutboxOperationEntity,
                    encryptionPassword: String?,
                ) = error("unused")

                override suspend fun readOperation(operationId: String): RemoteOperationResult {
                    readOperationId = operationId
                    return expected
                }

                override suspend fun readTransaction(
                    budgetId: String,
                    transactionId: String,
                    encryptionPassword: String?,
                ) = null
            }

        kotlinx.coroutines.runBlocking {
            assertEquals(expected, remote.readOperation("operation-1", "budget-password"))
        }
        assertEquals("operation-1", readOperationId)
    }
}
