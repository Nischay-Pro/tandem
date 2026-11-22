package app.tandemforactual.ui
import app.tandemforactual.data.planning.SavedSearch
import app.tandemforactual.data.planning.SavedSearchFilter
import app.tandemforactual.ui.feature.activity.ActivitySearchField
import app.tandemforactual.ui.feature.activity.ActivitySearchFilter
import app.tandemforactual.ui.feature.activity.SavedActivitySearch
import app.tandemforactual.ui.feature.activity.activityFilters
import app.tandemforactual.ui.feature.activity.summary
import app.tandemforactual.ui.feature.activity.toSavedSearch
import app.tandemforactual.ui.feature.reports.summary
import kotlin.test.Test
import kotlin.test.assertEquals

class SavedSearchPresentationTest {
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
}
