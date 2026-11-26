package app.tandemforactual.ui.core.state

import app.tandemforactual.data.database.SearchableBudgetTransaction
import kotlin.test.Test
import kotlin.test.assertEquals

class MonthMoneyInTest {
    private val month =
        LiveBudgetMonthSummary(
            toBudgetMinor = null,
            incomeAvailableMinor = null,
            totalBudgetedMinor = null,
            totalSpentMinor = null,
            groups =
                listOf(
                    LiveCategoryGroupSummary("income", "Income", listOf(LiveCategorySummary("salary", "Salary", 0, 100_000, 0)), true),
                ),
        )

    @Test
    fun leavesOutSplitParentsWhoseLinesAreAlreadyCounted() {
        val state = AppState().apply { selectedMonthId = "2026-03" }
        val parent = TransactionItem("Employer", "Split", "", 100_000, "E", dateIso = "2026-03-01", isSplitParent = true)
        val line = TransactionItem("Employer", "Salary", "", 100_000, "E", dateIso = "2026-03-01", categoryId = "salary")

        val (income, transactions) = monthMoneyIn(state, month, listOf(parent, line))

        assertEquals(100_000, income)
        assertEquals(listOf(line), transactions)
    }

    @Test
    fun countsTheWholeOnBudgetMonthWithoutSplitParentsOrOffBudgetDeposits() {
        val state = AppState().apply { selectedMonthId = "2026-03" }
        val purchases = (1..60).map { tx("buy$it", "2026-03-${(it % 28 + 1).toString().padStart(2, '0')}", -1_000, category = "food") }
        val records =
            purchases +
                listOf(
                    tx("pay", "2026-03-15", 100_000, isParent = true),
                    tx("pay-a", "2026-03-15", 60_000, category = "salary"),
                    tx("pay-b", "2026-03-15", 40_000, category = "salary"),
                    tx("gift", "2026-03-20", 2_500),
                    tx("broker", "2026-03-20", 500_000, account = "brokerage"),
                    tx("old", "2026-02-20", 9_000),
                )
        val names = RecordNames(emptyMap(), emptyMap(), mapOf("checking" to "Checking", "brokerage" to "Brokerage"))

        val items = monthTransactionItems(records, state.selectedMonthId, names, setOf("brokerage"), todayIso = "2026-03-31")
        val (income, transactions) = monthMoneyIn(state, month, items)

        assertEquals(102_500, income)
        assertEquals(60, transactions.count { it.amountMinorUnits < 0 })
        assertEquals(3, transactions.count { it.amountMinorUnits > 0 })
    }

    private fun tx(
        id: String,
        date: String,
        amount: Long,
        category: String? = null,
        account: String = "checking",
        isParent: Boolean = false,
    ) = SearchableBudgetTransaction(id, date, account, category, null, amount, null, null, null, null, null, false, isParent)
}
