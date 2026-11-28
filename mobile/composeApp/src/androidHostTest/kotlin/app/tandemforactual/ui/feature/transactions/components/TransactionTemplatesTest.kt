package app.tandemforactual.ui.feature.transactions.components

import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.planning.TransactionTemplate
import app.tandemforactual.ui.core.state.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransactionTemplatesTest {
    private fun account(
        id: String,
        name: String,
        json: String,
    ) = BudgetRecordEntity(
        budgetId = "b",
        recordType = "account",
        recordId = id,
        remoteJson = json,
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
        displayName = name,
    )

    @Test
    fun aFilledFormBecomesATemplate() {
        val state = AppState()
        assertNull(templateFromForm(state, "t"))
        state.transactionType = "Spending"
        state.transactionAmount = "4.50"
        state.transactionPayee = " Corner Café "
        state.newTransactionPayeeId = "cafe"
        state.newTransactionCategoryId = "dining"
        state.transactionCategory = "Dining"
        state.newTransactionAccountId = "checking"
        val expected = TransactionTemplate("t", "cafe", "Corner Café", 450, false, "dining", "Dining", "checking")
        assertEquals(expected, templateFromForm(state, "t"))
        state.transactionType = "Transfer"
        assertNull(templateFromForm(state, "t"))
    }

    @Test
    fun applyingATemplateFillsTheFormAndKeepsAnOpenAccount() {
        val state = AppState()
        state.newTransactionAccountId = "checking"
        state.transactionAccount = "Checking"
        val accounts = listOf(account("checking", "Checking", "{}"), account("old", "Old card", """{"closed":true}"""))
        state.applyTemplate(TransactionTemplate("t", null, "Salary", 300_000, true, null, null, "old"), accounts)
        assertEquals("Income", state.transactionType)
        assertEquals("Salary", state.transactionPayee)
        assertEquals("checking", state.newTransactionAccountId)
        state.applyTemplate(TransactionTemplate("t", "cafe", "Corner Café", 450, false, "dining", "Dining", "checking"), accounts)
        assertEquals("Spending", state.transactionType)
        assertEquals("dining", state.newTransactionCategoryId)
        assertEquals("Checking", state.transactionAccount)
    }
}
