package app.tandemforactual.ui

import app.tandemforactual.data.planning.CustomReport
import app.tandemforactual.data.planning.ReportFlow
import app.tandemforactual.data.planning.ReportGrouping
import app.tandemforactual.data.planning.ReportRange
import kotlin.test.Test
import kotlin.test.assertEquals

class InsightPresentationTest {
    @Test
    fun describesReportsAndGroups() {
        val report = CustomReport("r", "Trips", ReportRange.LAST_6_MONTHS, ReportGrouping.PAYEE, ReportFlow.INCOME, payeeIds = listOf("p"))
        assertEquals("Income by payee · Last 6 months · 1 filter", report.summary())
        val names = RecordNames(mapOf("c" to "Food"), mapOf("p" to "Market"), mapOf("a" to "Checking"))
        assertEquals("Food", names.group(ReportGrouping.CATEGORY, "c"))
        assertEquals("Uncategorized", names.group(ReportGrouping.CATEGORY, null))
        assertEquals("Uncategorized", names.category(""))
        assertEquals("Deleted category", names.category("x"))
        assertEquals("No payee", names.group(ReportGrouping.PAYEE, null))
        assertEquals("Deleted account", names.group(ReportGrouping.ACCOUNT, "x"))
        assertEquals("13%", wholePercent(1_250))
        assertEquals("50%", wholePercent(-5_000))
    }
}
