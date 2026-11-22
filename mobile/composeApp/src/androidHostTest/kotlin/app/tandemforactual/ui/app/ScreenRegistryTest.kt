package app.tandemforactual.ui.app

import app.tandemforactual.ui.core.state.AppRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenRegistryTest {
    @Test
    fun everyRouteHasExactlyOneScreen() {
        val registered = screenRoutes.flatMap { it.keys }
        assertEquals(registered.size, registered.toSet().size, "A route is registered by more than one route file")
        val missing = AppRoute.entries.filterNot { it in screenRegistry }
        assertTrue(missing.isEmpty(), "Routes with no screen: $missing")
    }
}
