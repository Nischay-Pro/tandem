package app.tandemforactual.ui

import app.tandemforactual.data.planning.CustomReport
import app.tandemforactual.data.planning.ReportFlow
import app.tandemforactual.data.planning.ReportGrouping
import app.tandemforactual.data.planning.ReportRange
import app.tandemforactual.data.planning.SavedSearch
import app.tandemforactual.data.planning.SavedSearchFilter
import kotlin.test.Test
import kotlin.test.assertEquals

class InsightPresentationTest {
    @Test
    fun savedSearchesKeepTheirFiltersAndDescribeThemselves() {
        val legacy =
            SavedActivitySearch(
                "budget",
                "Coffee",
                "cafe",
                listOf(ActivitySearchFilter(ActivitySearchField.TAG, "treat", "#treat")),
            )
        val saved = legacy.toSavedSearch("s1")
        assertEquals(SavedSearch("s1", "Coffee", "cafe", listOf(SavedSearchFilter("TAG", "treat", "#treat"))), saved)
        assertEquals(legacy.filters, saved.activityFilters())
        assertEquals(emptyList(), saved.copy(filters = listOf(SavedSearchFilter("GONE", "", ""))).activityFilters())
        assertEquals("“cafe” · 1 filter", saved.summary())
        assertEquals("All transactions", SavedSearch("s2", "Empty").summary())
        assertEquals("3 filters", SavedSearch("s3", "x", " ", List(3) { SavedSearchFilter("TAG", "$it", "$it") }).summary())
    }

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
