package app.tandemforactual.ui
import app.tandemforactual.ui.app.showsBottomNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppRouteEntry
import app.tandemforactual.ui.core.state.decodeAppTabBackStacks
import app.tandemforactual.ui.core.state.encodeAppTabBackStacks
import app.tandemforactual.ui.core.state.routeContentStateKey
import app.tandemforactual.ui.core.state.selectAppTabNavigation
import app.tandemforactual.ui.core.state.selectAppTabRootNavigation
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
    fun routeCountStaysWithinEighty() {
        assertTrue(AppRoute.entries.size <= 80)
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

        assertEquals(listOf(AppRoute.HOME, AppRoute.ACTIVITY), selection.backStack.map { it.route })
        assertEquals(listOf(10L, 11L), selection.backStack.map { it.id })
    }

    @Test
    fun switchingTabsPreservesEachTabsNestedStack() {
        val activityStack =
            listOf(
                AppRouteEntry(AppRoute.BUDGET, 1),
                AppRouteEntry(AppRoute.ACTIVITY, 2),
                AppRouteEntry(AppRoute.ACTIVITY_SEARCH, 3),
            )
        var nextId = 10L
        val accountSelection =
            selectAppTabNavigation(activityStack, "", AppRoute.ACCOUNTS) { route -> AppRouteEntry(route, nextId++) }
        val activitySelection =
            selectAppTabNavigation(
                accountSelection.backStack,
                accountSelection.savedTabBackStacks,
                AppRoute.ACTIVITY,
            ) { route -> AppRouteEntry(route, nextId++) }

        assertEquals(listOf(AppRoute.HOME, AppRoute.ACCOUNTS), accountSelection.backStack.map { it.route })
        assertEquals(activityStack, activitySelection.backStack)
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
    fun openingRecentsReturnsToUnfilteredActivityRoot() {
        val activityStack =
            listOf(
                AppRouteEntry(AppRoute.BUDGET, 1),
                AppRouteEntry(AppRoute.ACTIVITY, 2),
                AppRouteEntry(AppRoute.ACTIVITY_SEARCH, 3),
            )
        var nextId = 10L

        val selection =
            selectAppTabRootNavigation(activityStack, "", AppRoute.ACTIVITY) { route -> AppRouteEntry(route, nextId++) }

        assertEquals(listOf(AppRoute.HOME, AppRoute.ACTIVITY), selection.backStack.map { it.route })
        assertTrue(decodeAppTabBackStacks(selection.savedTabBackStacks)[AppRoute.ACTIVITY] == null)
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
        assertFalse(AppRoute.PERSONALIZE.showsBottomNavigation())
        assertFalse(AppRoute.BUDGET_UNLOCK.showsBottomNavigation())
        assertFalse(AppRoute.SETUP_COMPLETE.showsBottomNavigation())
    }
}
