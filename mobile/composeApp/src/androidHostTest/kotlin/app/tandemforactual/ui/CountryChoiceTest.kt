package app.tandemforactual.ui

import app.tandemforactual.data.regions.Region
import app.tandemforactual.ui.feature.retirement.searchRegions
import app.tandemforactual.ui.feature.retirement.suggestedRegions
import kotlin.test.Test
import kotlin.test.assertEquals

class CountryChoiceTest {
    private val available = listOf(Region.US, Region.CA, Region.IN)

    @Test
    fun suggestsWhereYouLivedThenTheBudgetCurrencySkippingPickedOnes() {
        assertEquals(listOf(Region.IN, Region.US), suggestedRegions(available, listOf(Region.IN, Region.UK), "usd", emptyList()))
        assertEquals(listOf(Region.US), suggestedRegions(available, listOf(Region.IN), "USD", listOf(Region.IN)))
        assertEquals(emptyList(), suggestedRegions(available, emptyList(), "JPY", emptyList()))
    }

    @Test
    fun findsCountriesByNameCodeOrAccount() {
        assertEquals(listOf(Region.CA), searchRegions(available, "rrsp"))
        assertEquals(listOf(Region.US), searchRegions(available, "401k"))
        assertEquals(listOf(Region.IN), searchRegions(available, " ind "))
        assertEquals(emptyList(), searchRegions(available, "japan"))
        assertEquals(emptyList(), searchRegions(available, "  "))
    }
}
