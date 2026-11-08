package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomNavigationVisibilityTest {
    @Test
    fun browsingRoutesKeepBottomNavigation() {
        assertTrue(AppRoute.ACTIVITY_SEARCH.showsBottomNavigation())
        assertTrue(AppRoute.ACCOUNT_DETAIL.showsBottomNavigation())
        assertTrue(AppRoute.REPORT_DETAIL.showsBottomNavigation())
    }

    @Test
    fun transactionEditorsAndContextPickersHideBottomNavigation() {
        assertFalse(AppRoute.NEW_TRANSACTION.showsBottomNavigation())
        assertFalse(AppRoute.EDIT_TRANSACTION.showsBottomNavigation())
        assertFalse(AppRoute.CHOOSE_CATEGORY.showsBottomNavigation())
        assertFalse(AppRoute.CHOOSE_DATE.showsBottomNavigation())
    }
}
