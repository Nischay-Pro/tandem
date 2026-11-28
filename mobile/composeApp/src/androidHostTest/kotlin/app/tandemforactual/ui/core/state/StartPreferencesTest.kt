package app.tandemforactual.ui.core.state

import kotlin.test.Test
import kotlin.test.assertEquals

class StartPreferencesTest {
    private val minimal = listOf(AppRoute.HOME, AppRoute.ACTIVITY, AppRoute.BUDGET, AppRoute.SETTINGS)

    @Test
    fun theAppOpensOnTheSavedTabWhenItIsShown() {
        assertEquals(AppRoute.BUDGET, startPage("BUDGET", minimal))
        assertEquals(AppRoute.HOME, startPage("ACCOUNTS", minimal))
        assertEquals(AppRoute.HOME, startPage("", minimal))
        assertEquals(AppRoute.HOME, startPage("SETTINGS", minimal))
    }

    @Test
    fun startPageAndDefaultAccountAreSaved() {
        val state = AppState()
        state.restorePersistentSettings(mapOf("startPage" to "BUDGET", "defaultTransactionAccountId" to "checking"))
        assertEquals("BUDGET", state.persistentSettings()["startPage"])
        assertEquals("checking", state.persistentSettings()["defaultTransactionAccountId"])
    }
}
