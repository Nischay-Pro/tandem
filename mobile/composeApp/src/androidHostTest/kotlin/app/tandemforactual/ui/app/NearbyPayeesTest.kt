package app.tandemforactual.ui.app

import app.tandemforactual.ui.core.platform.GeoPoint
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.PayeeLocations
import app.tandemforactual.ui.core.state.SelectorItem
import app.tandemforactual.ui.core.state.adding
import app.tandemforactual.ui.core.state.coarse
import app.tandemforactual.ui.core.state.decodePayeeLocations
import app.tandemforactual.ui.core.state.distanceMetres
import app.tandemforactual.ui.core.state.encodePayeeLocations
import app.tandemforactual.ui.core.state.nearby
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NearbyPayeesTest {
    private val cafe = GeoPoint(51.50735, -0.12776)
    private val bakery = GeoPoint(51.5087, -0.1278)
    private val airport = GeoPoint(51.47002, -0.45429)

    @Test
    fun placesAreRoundedToAboutAHundredMetresAndTheNewestFewKept() {
        assertEquals(GeoPoint(51.507, -0.128), cafe.coarse())
        var locations: PayeeLocations = emptyMap()
        (0 until 7).forEach { locations = locations.adding("cafe", GeoPoint(51.5 + it / 100.0, -0.1)) }
        locations = locations.adding("cafe", GeoPoint(51.56, -0.1))
        assertEquals(5, locations.getValue("cafe").size)
        assertEquals(GeoPoint(51.56, -0.1), locations.getValue("cafe").first())
    }

    @Test
    fun nearbyPayeesAreTheOnesPaidCloseByClosestFirst() {
        val locations = emptyMap<String, List<GeoPoint>>().adding("cafe", cafe).adding("bakery", bakery).adding("flights", airport)
        assertEquals(listOf("cafe", "bakery"), locations.nearby(GeoPoint(51.5074, -0.1278)))
        assertTrue(distanceMetres(cafe, airport) > 20_000)
    }

    @Test
    fun locationsRoundTripThroughStorage() {
        val locations = emptyMap<String, List<GeoPoint>>().adding("cafe", cafe).adding("cafe", bakery).adding("flights", airport)
        assertEquals(locations, decodePayeeLocations(encodePayeeLocations(locations)))
        assertEquals(emptyMap(), decodePayeeLocations(null))
        assertEquals(emptyMap(), decodePayeeLocations("broken\ncafe|x,y"))
    }

    @Test
    fun theNearbyGroupListsKnownPayeesOnly() {
        val values = listOf(SelectorItem("cafe", "Cafe"), SelectorItem("bakery", "Bakery"))
        assertEquals(listOf("Cafe"), nearbyPayeeGroup(values, listOf("gone", "cafe")).single().items.map { it.label })
        assertEquals(emptyList(), nearbyPayeeGroup(values, emptyList()))
    }

    @Test
    fun payeeLocationsAreOffAndUnaskedUntilChosen() {
        val state = AppState()
        assertFalse(state.payeeLocationsEnabled)
        assertEquals("false", state.persistentSettings()["payeeLocationsEnabled"])
        state.restorePersistentSettings(mapOf("payeeLocationsEnabled" to "true", "payeeLocationsAsked" to "true"))
        assertTrue(state.payeeLocationsEnabled && state.payeeLocationsAsked)
    }
}
