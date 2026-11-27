@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.InterfaceMode
import app.tandemforactual.ui.core.state.MainCloudStatus
import app.tandemforactual.ui.design.buttons.MIN_TOUCH_TARGET
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.WindowSizeInfo
import app.tandemforactual.ui.design.theme.currentWindowSizeInfo
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every top-level screen's navigation, menu and add actions stay whole and reachable at every window preset. */
@RunWith(RobolectricTestRunner::class)
class WindowPresetChromeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = WindowPresets.PHONE)
    fun phone() = assertPrimaryActionsFit()

    @Test
    @Config(qualifiers = WindowPresets.MEDIUM)
    fun mediumWidth() = assertPrimaryActionsFit()

    @Test
    @Config(qualifiers = WindowPresets.EXPANDED)
    fun expandedWidth() = assertPrimaryActionsFit()

    @Test
    @Config(qualifiers = WindowPresets.LARGE)
    fun largeWidth() = assertPrimaryActionsFit()

    @Test
    @Config(qualifiers = WindowPresets.SHORT_PHONE)
    fun shortPhone() = assertPrimaryActionsFit()

    @Test
    @Config(qualifiers = WindowPresets.SHORT_WIDE)
    fun shortWideWindow() = assertPrimaryActionsFit()

    @Test
    @Config(qualifiers = WindowPresets.BOOK_FOLD)
    fun unfoldedFoldable() = assertPrimaryActionsFit()

    private fun assertPrimaryActionsFit() {
        val screen = mutableStateOf(InterfaceMode.MINIMAL to AppRoute.HOME)
        lateinit var window: WindowSizeInfo
        composeTestRule.setContent {
            window = currentWindowSizeInfo()
            val (mode, route) = screen.value
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true, interfaceMode = mode) {
                PresetChrome(window, route)
            }
        }
        InterfaceMode.entries.forEach { mode ->
            navigationRoutes(mode).forEach { route ->
                composeTestRule.runOnIdle { screen.value = mode to route }
                composeTestRule.waitForIdle()
                val actions = TAB_LABELS.getValue(mode) + "Menu" + listOfNotNull("Add transaction".takeIf { addsFromHeader(window, route) })
                actions.forEach { assertFits(it, "$mode $route at ${window.widthDp}x${window.heightDp}dp") }
            }
        }
    }

    private fun assertFits(
        description: String,
        where: String,
    ) {
        val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
        val nodes = composeTestRule.onAllNodesWithContentDescription(description)
        val count = nodes.fetchSemanticsNodes().size
        assertTrue(count > 0, "$where: no $description")
        repeat(count) { index ->
            val bounds = nodes[index].assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(
                bounds.left >= root.left - EDGE &&
                    bounds.top >= root.top - EDGE &&
                    bounds.right <= root.right + EDGE &&
                    bounds.bottom <= root.bottom + EDGE,
                "$where: $description at $bounds is cut off by the window $root",
            )
            assertTrue(
                bounds.right - bounds.left >= MIN_TOUCH_TARGET - EDGE && bounds.bottom - bounds.top >= MIN_TOUCH_TARGET - EDGE,
                "$where: $description at $bounds is smaller than a touch target",
            )
        }
    }

    private companion object {
        val EDGE = 0.5.dp
        val TAB_LABELS =
            mapOf(
                InterfaceMode.MINIMAL to listOf("Home", "Activity", "Budget", "Settings"),
                InterfaceMode.ADVANCED to listOf("Home", "Accounts", "Activity", "Budget", "Reports"),
            )
    }
}

private fun addsFromHeader(
    window: WindowSizeInfo,
    route: AppRoute,
) = window.compactHeight && route == AppRoute.BUDGET

@Composable
private fun PresetChrome(
    window: WindowSizeInfo,
    route: AppRoute,
) {
    val openMenu: (() -> Unit)? = if (window.usesRail) null else ({})
    val addTransaction: (() -> Unit)? = if (addsFromHeader(window, route)) ({}) else null
    Row(Modifier.fillMaxSize()) {
        if (window.usesRail) SideNavigationRail(route, {}, onOpenMenu = {})
        Column(Modifier.weight(1f).fillMaxHeight()) {
            AppHeader(
                route = route,
                state = AppState(),
                showBack = false,
                onBack = {},
                onSync = {},
                syncStatus = MainCloudStatus.OFF,
                pendingWriteStatuses = emptyList(),
                onBudget = {},
                onOpenRoute = {},
                actualConnected = true,
                liveAccounts = emptyList(),
                liveBudgetMonth = null,
                activitySelectionTotalMinor = null,
                conflictTitle = null,
                compactHeight = window.compactHeight,
                onAddTransaction = addTransaction,
                onOpenMenu = openMenu,
            )
            Box(Modifier.weight(1f).fillMaxWidth())
            if (!window.usesRail) {
                Box(Modifier.fillMaxWidth().height(TandemLayout.bottomBarHeight), contentAlignment = Alignment.BottomCenter) {
                    BottomNavigation(route, {})
                }
            }
        }
    }
}
