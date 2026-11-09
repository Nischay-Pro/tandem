package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomNavigationVisibilityTest {
    @Test
    fun budgetContentStateIsScopedToTheSelectedBudget() {
        val budgetEntry = AppRouteEntry(AppRoute.BUDGET, 1)
        val anotherRouteEntry = AppRouteEntry(AppRoute.ACTIVITY, 2)

        assertEquals("tab-BUDGET-budget-budget-one", routeContentStateKey(budgetEntry, "budget-one"))
        assertEquals("tab-BUDGET-budget-budget-two", routeContentStateKey(budgetEntry, "budget-two"))
        assertEquals("tab-BUDGET", routeContentStateKey(budgetEntry, ""))
        assertEquals(anotherRouteEntry.stateKey, routeContentStateKey(anotherRouteEntry, "budget-one"))
    }

    @Test
    fun fullScreenRoutesStayBelowForty() {
        assertTrue(AppRoute.entries.size < 40)
    }

    @Test
    fun tabBackStacksPreserveNestedRouteEntries() {
        val activityStack =
            listOf(
                AppRouteEntry(AppRoute.BUDGET, 1),
                AppRouteEntry(AppRoute.ACTIVITY, 2),
                AppRouteEntry(AppRoute.ACTIVITY_SEARCH, 3),
            )

        val restored = decodeAppTabBackStacks(encodeAppTabBackStacks(mapOf(AppRoute.ACTIVITY to activityStack)))

        assertEquals(activityStack, restored[AppRoute.ACTIVITY])
    }

    @Test
    fun tappingTheActiveTabReturnsToItsRoot() {
        val currentStack =
            listOf(
                AppRouteEntry(AppRoute.BUDGET, 1),
                AppRouteEntry(AppRoute.ACTIVITY, 2),
                AppRouteEntry(AppRoute.ACTIVITY_SEARCH, 3),
            )
        var nextId = 10L

        val selection =
            selectAppTabNavigation(currentStack, "", AppRoute.ACTIVITY) { route -> AppRouteEntry(route, nextId++) }

        assertEquals(listOf(AppRoute.BUDGET, AppRoute.ACTIVITY), selection.backStack.map { it.route })
        assertEquals(listOf(10L, 11L), selection.backStack.map { it.id })
    }

    @Test
    fun tappingAnActiveTabRootLeavesTheStackAlone() {
        val currentStack = listOf(AppRouteEntry(AppRoute.BUDGET, 1), AppRouteEntry(AppRoute.ACTIVITY, 2))
        var createdEntry = false

        val selection =
            selectAppTabNavigation(currentStack, "", AppRoute.ACTIVITY) { route ->
                createdEntry = true
                AppRouteEntry(route, 3)
            }

        assertEquals(currentStack, selection.backStack)
        assertEquals(false, createdEntry)
    }

    @Test
    fun browsingRoutesKeepBottomNavigation() {
        assertTrue(AppRoute.ACTIVITY_SEARCH.showsBottomNavigation())
        assertTrue(AppRoute.ACCOUNT_DETAIL.showsBottomNavigation())
        assertTrue(AppRoute.REPORT_DETAIL.showsBottomNavigation())
    }

    @Test
    fun transactionEditorsAndSearchPickersHideBottomNavigation() {
        assertFalse(AppRoute.NEW_TRANSACTION.showsBottomNavigation())
        assertFalse(AppRoute.EDIT_TRANSACTION.showsBottomNavigation())
        assertFalse(AppRoute.CHOOSE_PAYEE.showsBottomNavigation())
        assertFalse(AppRoute.CHOOSE_CATEGORY.showsBottomNavigation())
    }
}
