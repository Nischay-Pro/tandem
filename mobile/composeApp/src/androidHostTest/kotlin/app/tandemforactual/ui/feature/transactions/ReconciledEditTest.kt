package app.tandemforactual.ui.feature.transactions

import app.tandemforactual.data.database.BudgetRecordEntity
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReconciledEditTest {
    private fun transaction(
        id: String,
        reconciled: Boolean?,
    ) = BudgetRecordEntity(
        budgetId = "b",
        recordType = "transaction",
        recordId = id,
        remoteJson = "{}",
        remoteRevision = null,
        localJson = null,
        localDeleted = false,
        remoteDeleted = false,
        syncGeneration = "1",
        recordDate = null,
        accountId = null,
        categoryId = null,
        payeeId = null,
        amountMinor = null,
        budgetMonth = null,
        reconciled = reconciled,
    )

    @Test
    fun onlyAReconciledTransactionAsksBeforeEditing() {
        val records = listOf(transaction("a", true), transaction("b", false), transaction("c", null))
        assertTrue(transactionIsReconciled(records, "a"))
        assertFalse(transactionIsReconciled(records, "b"))
        assertFalse(transactionIsReconciled(records, "c"))
        assertFalse(transactionIsReconciled(records, "missing"))
    }
}
