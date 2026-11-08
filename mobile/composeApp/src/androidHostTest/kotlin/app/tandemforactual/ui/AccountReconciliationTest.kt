package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountReconciliationTest {
    @Test
    fun selectedTransactionsReplaceTheirPreviousClearedState() {
        val transactions =
            listOf(
                transaction("cleared", -1200, cleared = true),
                transaction("pending", -450),
                transaction("deposit", 900),
            )

        assertEquals(
            11_650L,
            reconciliationClearedBalanceMinor(10_000, transactions, setOf("pending", "deposit")),
        )
    }

    @Test
    fun emptySelectionUnclearsVisibleClearedTransactions() {
        val transactions = listOf(transaction("cleared", -1200, cleared = true))

        assertEquals(10_000L, reconciliationClearedBalanceMinor(8_800, transactions, emptySet()))
    }

    @Test
    fun finishRequiresAnEnteredBalanceAndAnExactMatch() {
        assertEquals(false, canFinishReconciliation(null, null, busy = false))
        assertEquals(false, canFinishReconciliation(10_000, 1, busy = false))
        assertEquals(false, canFinishReconciliation(10_000, 0, busy = true))
        assertEquals(true, canFinishReconciliation(10_000, 0, busy = false))
    }

    private fun transaction(
        id: String,
        amountMinor: Long,
        cleared: Boolean = false,
    ) = TransactionItem(
        payee = "Payee",
        category = "Category",
        dateLabel = "Nov 1",
        amountMinorUnits = amountMinor,
        icon = "",
        recordId = id,
        cleared = cleared,
    )
}
