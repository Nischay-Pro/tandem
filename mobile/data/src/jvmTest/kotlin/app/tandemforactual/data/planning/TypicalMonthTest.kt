package app.tandemforactual.data.planning

import app.tandemforactual.data.database.SearchableBudgetTransaction
import kotlin.test.Test
import kotlin.test.assertEquals

class TypicalMonthTest {
    private val chosen = MonthMoney(1_000, 400)

    private fun tx(
        date: String?,
        amount: Long?,
        category: String?,
        account: String? = "checking",
        transfer: Boolean? = false,
        parent: Boolean = false,
    ) = SearchableBudgetTransaction("id", date, account, category, null, amount, null, null, null, null, null, transfer, parent)

    private fun typical(
        records: List<SearchableBudgetTransaction>,
        chosenMonthId: String = "2026-11",
        today: String = "2026-11-10",
    ) = TypicalMonths.of(records, chosenMonthId, today, chosen, setOf("salary", "starting"), setOf("starting"), setOf("brokerage"))

    @Test
    fun usesAFinishedMonthAsItIs() {
        assertEquals(
            TypicalMonth(chosen, TypicalMonthSource.CHOSEN_MONTH, 0),
            typical(listOf(tx("2026-09-01", -5_000, "food")), chosenMonthId = "2026-10"),
        )
    }

    @Test
    fun fallsBackToTheMonthSoFarWithoutEarlierMonths() {
        val soFar = TypicalMonth(chosen, TypicalMonthSource.MONTH_SO_FAR, 0)
        assertEquals(soFar, typical(emptyList()))
        assertEquals(soFar, typical(listOf(tx("2026-11-02", -300, "food"))))
        assertEquals(soFar, typical(listOf(tx("2026-12-02", -300, "food")), chosenMonthId = "2026-12"))
        assertEquals(soFar, typical(listOf(tx(null, -300, "food"), tx("2026-10", -300, "food"))))
    }

    @Test
    fun averagesRecentFullMonthsSinceTheFirstActivity() {
        val records =
            listOf(
                tx("2026-09-03", 3_000, "salary"),
                tx("2026-09-04", -1_000, "food"),
                tx("2026-10-03", 5_000, "salary"),
                tx("2026-10-04", -2_000, "food"),
                tx("2026-10-05", 200, "food"),
                tx("2026-10-06", 400, null),
                tx("2026-10-06", -700, null),
                tx("2026-10-07", 900, null, transfer = true),
                tx("2026-10-08", -1_500, "savings", transfer = true),
                tx("2026-10-09", 50_000, "starting"),
                tx("2026-10-10", -9_000, "food", account = "brokerage"),
                tx("2026-10-11", 8_000, "salary", account = "brokerage"),
                tx("2026-10-12", -2_500, null, parent = true),
                tx("2026-10-12", null, "food"),
                tx("2026-11-02", 9_999, "salary"),
            )
        assertEquals(
            TypicalMonth(MonthMoney(4_200, 2_150), TypicalMonthSource.RECENT_FULL_MONTHS, 2),
            typical(records),
        )
        assertEquals(
            TypicalMonth(MonthMoney(5_400, 3_300), TypicalMonthSource.RECENT_FULL_MONTHS, 1),
            TypicalMonths.of(records, "2026-11", "2026-11-30", chosen, setOf("salary"), setOf("starting"), setOf("brokerage"), months = 1),
        )
    }

    @Test
    fun neverReportsNegativeMoney() {
        assertEquals(
            TypicalMonth(MonthMoney(0, 0), TypicalMonthSource.RECENT_FULL_MONTHS, 1),
            typical(listOf(tx("2026-10-03", -500, "salary"), tx("2026-10-04", 800, "food"))),
        )
    }
}
