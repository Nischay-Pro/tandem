package app.tandemforactual.ui.feature.budget.grid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GridBulkTest {
    private val key = GridKey("food", "2026-03")
    private val history =
        mapOf(
            GridKey("food", "2026-02") to MonthFigures(40_000, 30_000),
            GridKey("food", "2026-01") to MonthFigures(40_000, 45_000),
            GridKey("food", "2025-12") to MonthFigures(40_000, 60_000),
            GridKey("food", "2025-03") to MonthFigures(35_000, 20_000),
        )

    private fun fill(
        fill: BulkFill,
        percent: Long,
        amount: Long? = null,
    ) = bulkAmount(fill, key, 50_000, amount, percent) { history[it] }

    @Test
    fun aSetAmountIsUsedAsTypedAndNeverBelowZero() {
        assertEquals(12_300, fill(BulkFill.SET, 10, amount = 12_300))
        assertEquals(0, fill(BulkFill.SET, 0, amount = -5))
        assertNull(fill(BulkFill.SET, 0))
    }

    @Test
    fun aChangeByPercentStartsFromWhatIsBudgeted() {
        assertEquals(55_000, fill(BulkFill.PERCENT, 10))
        assertEquals(45_000, fill(BulkFill.PERCENT, -10))
        assertEquals(0, fill(BulkFill.PERCENT, -150))
    }

    @Test
    fun lastYearUsesTheSameMonthAYearBack() {
        assertEquals(35_000, fill(BulkFill.LAST_YEAR, 0))
        assertEquals(36_750, fill(BulkFill.LAST_YEAR, 5))
    }

    @Test
    fun averagesUseTheMonthsThatHaveFigures() {
        assertEquals(45_000, fill(BulkFill.AVERAGE_3, 0))
        assertEquals(37_500, fill(BulkFill.AVERAGE_12, 0))
        assertNull(bulkAmount(BulkFill.AVERAGE_6, GridKey("rent", "2026-03"), 0, null, 0) { history[it] })
    }

    @Test
    fun theYearBeforeEveryTargetMonthIsLoaded() {
        val months = bulkHistoryMonths(setOf("2026-03"))
        assertEquals(12, months.size)
        assertEquals(setOf("2025-03", "2026-02"), months.filter { it == "2025-03" || it == "2026-02" }.toSet())
        assertEquals("2025-11", monthsBefore("2026-01", 2))
    }
}
