package app.tandemforactual.ui.feature.reports

import app.tandemforactual.data.database.SearchableBudgetTransaction
import app.tandemforactual.data.planning.CustomReport
import app.tandemforactual.data.planning.MonthStart
import app.tandemforactual.data.planning.ReportRange
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportsInPeriodsTest {
    private fun spent(date: String) =
        SearchableBudgetTransaction("id-$date", date, "checking", "dining", null, -1_000, null, null, null, null, null, false, false)

    @Test
    fun thisMonthRunsFromPaydayWhileFixedDatesStayReal() {
        val transactions = listOf(spent("2026-11-20"), spent("2026-11-26"))
        val payday = MonthStart(25, previousWorkingDay = false)
        val thisMonth = CustomReport("r", "This month", ReportRange.THIS_MONTH)
        assertEquals(1_000, runInPeriods(thisMonth, transactions, emptySet(), "2026-11-28", payday).totalMinor)
        assertEquals(2_000, runInPeriods(thisMonth, transactions, emptySet(), "2026-11-28", MonthStart(1, false)).totalMinor)
        val fixed = CustomReport("r", "Fixed", ReportRange.CUSTOM, startDate = "2026-11-01", endDate = "2026-11-30")
        assertEquals(2_000, runInPeriods(fixed, transactions, emptySet(), "2026-11-28", payday).totalMinor)
    }
}
