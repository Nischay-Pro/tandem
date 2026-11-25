package app.tandemforactual.ui

import app.tandemforactual.ui.design.icons.RecentCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MerchantArtworkCacheTest {
    @Test
    fun keepsTheMostRecentlyUsedValues() {
        val cache = RecentCache<Int, String>(3)
        (1..3).forEach { cache.put(it, "logo $it") }
        assertEquals("logo 1", cache.get(1))
        cache.put(4, "logo 4")
        assertEquals(3, cache.size)
        assertNull(cache.get(2), "the least recently used is dropped")
        assertEquals("logo 1", cache.get(1))
        assertEquals("logo 4", cache.get(4))
    }
}
