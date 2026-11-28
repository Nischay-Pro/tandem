package app.tandemforactual.ui.feature.accounts

import app.tandemforactual.data.database.BudgetRecordEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoanPaymentTest {
    private fun record(
        type: String,
        id: String,
        json: String,
    ) = BudgetRecordEntity(
        budgetId = "b",
        recordType = type,
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
        displayName = Regex("\"name\":\"([^\"]+)\"").find(json)?.groupValues?.get(1),
    )

    @Test
    fun thePaymentSplitsAsTheScheduleFirstMonthPlusEscrow() {
        val payment = loanPayment(100_000, 1_200, 10_000, 2_500)!!
        assertEquals(LoanPayment(9_000, 1_000, 2_500), payment)
        assertEquals(12_500L, payment.totalMinor)
        assertEquals(0L, loanPayment(100_000, 1_200, 10_000, -5)!!.escrowMinor)
        assertNull(loanPayment(100_000, 2_400, 2_000, 0))
    }

    @Test
    fun theTransferGoesThroughTheLoansOwnPayee() {
        val payees =
            listOf(
                record("payee", "shop", """{"name":"Shop"}"""),
                record("payee", "to-loan", """{"name":"Transfer: Car loan","transfer_acct":"loan"}"""),
            )
        assertEquals("to-loan", transferPayeeFor(payees, "loan"))
        assertNull(transferPayeeFor(payees, "savings"))
    }

    @Test
    fun paymentsComeFromOpenOnBudgetAccountsOtherThanTheLoan() {
        val accounts =
            listOf(
                record("account", "checking", """{"name":"Checking"}"""),
                record("account", "loan", """{"name":"Car loan","offbudget":true}"""),
                record("account", "old", """{"name":"Old","closed":true}"""),
                record("account", "brokerage", """{"name":"Brokerage","offbudget":true}"""),
            )
        assertEquals(listOf("checking" to "Checking"), paymentSources(accounts, "loan"))
    }
}
