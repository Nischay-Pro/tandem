package app.tandemforactual.ui.feature.accounts

import app.tandemforactual.data.database.BudgetRecordEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportApprovalTest {
    private fun transaction(
        id: String,
        accountId: String,
        json: String,
        deleted: Boolean = false,
    ) = BudgetRecordEntity(
        budgetId = "budget-1",
        recordType = "transaction",
        recordId = id,
        remoteJson = json,
        remoteRevision = null,
        localJson = null,
        localDeleted = deleted,
        remoteDeleted = false,
        syncGeneration = "1",
        recordDate = null,
        accountId = accountId,
        categoryId = null,
        payeeId = null,
        amountMinor = null,
        budgetMonth = null,
    )

    @Test
    fun onlyLiveImportedTransactionsInTheAccountCount() {
        val records =
            listOf(
                transaction("t1", "checking", """{"importedId":"bank-1"}"""),
                transaction("t2", "checking", """{"importedId":null}"""),
                transaction("t3", "savings", """{"importedId":"bank-3"}"""),
                transaction("t4", "checking", """{"importedId":"bank-4"}""", deleted = true),
                transaction("t5", "checking", """{"importedId":"bank-5"}"""),
            )
        assertEquals(setOf("t1", "t5"), importedTransactionIds(records, "checking"))
    }

    @Test
    fun settledImportsRoundTripAndOffMeansNoReview() {
        assertEquals(setOf("a", "b"), decodeSettledImports(encodeSettledImports(setOf("b", "a"))))
        assertEquals(emptySet(), decodeSettledImports(encodeSettledImports(emptySet())))
        assertNull(decodeSettledImports(null))
        assertNull(decodeSettledImports(REVIEW_OFF))
        assertEquals("internal:import-review:budget-1:checking", importReviewKey("budget-1", "checking"))
    }
}
