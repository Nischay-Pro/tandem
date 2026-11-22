package app.tandemforactual.data.planning

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class SavedSearchTest {
    @Test
    fun keepsSearchesAsJson() {
        val search = SavedSearch("s1", "Coffee", "cafe", listOf(SavedSearchFilter("CATEGORY", "food", "Food")))
        assertEquals(search, SavedSearch.fromJson("s1", search.toJson()))
        val messy = Json.parseToJsonElement("""{"filters":[1,{"value":"x"},{"field":"TAG"}]}""").jsonObject
        assertEquals(SavedSearch("s2", "", "", listOf(SavedSearchFilter("TAG", "", ""))), SavedSearch.fromJson("s2", messy))
        assertEquals(SavedSearch("s3", ""), SavedSearch.fromJson("s3", JsonObject(emptyMap())))
    }
}
