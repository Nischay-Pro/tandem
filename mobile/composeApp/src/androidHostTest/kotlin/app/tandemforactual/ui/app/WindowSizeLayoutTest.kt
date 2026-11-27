package app.tandemforactual.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.InterfaceMode
import app.tandemforactual.ui.core.state.WorkspaceLayout
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.WindowHeightClass
import app.tandemforactual.ui.design.theme.WindowSizeInfo
import app.tandemforactual.ui.design.theme.WindowWidthClass
import app.tandemforactual.ui.design.theme.currentWindowSizeInfo
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class WindowSizeLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun phonePortraitUsesTheBottomBarAndOnePane() {
        val window = renderShellPanes()
        assertEquals(WindowWidthClass.COMPACT, window.widthClass)
        assertFalse(window.usesRail)
        assertSinglePane()
    }

    @Test
    @Config(qualifiers = "w600dp-h960dp")
    fun mediumWidthUsesTheRailButKeepsOnePane() {
        val window = renderShellPanes()
        assertEquals(WindowWidthClass.MEDIUM, window.widthClass)
        assertTrue(window.usesRail)
        assertSinglePane()
    }

    @Test
    @Config(qualifiers = "w840dp-h1200dp")
    fun expandedWidthShowsListBesideDetail() {
        val window = renderShellPanes()
        assertEquals(WindowWidthClass.EXPANDED, window.widthClass)
        assertTrue(window.usesRail)
        assertTwoPanes(TandemLayout.listPaneWidth)
    }

    @Test
    @Config(qualifiers = "w1200dp-h800dp")
    fun largeWidthWidensTheListPane() {
        val window = renderShellPanes()
        assertEquals(WindowWidthClass.LARGE, window.widthClass)
        assertTrue(window.usesRail)
        assertTwoPanes(TandemLayout.wideListPaneWidth)
    }

    @Test
    @Config(qualifiers = "w800dp-h400dp")
    fun shortWindowsKeepTheBottomBar() {
        val window = renderShellPanes()
        assertEquals(WindowHeightClass.COMPACT, window.heightClass)
        assertFalse(window.usesRail)
        assertTrue(window.compactHeight)
    }

    @Test
    @Config(qualifiers = "w840dp-h1200dp")
    fun railScrollsWhenItsItemsDoNotFit() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true, interfaceMode = InterfaceMode.ADVANCED) {
                Box(Modifier.height(240.dp)) {
                    SideNavigationRail(
                        selected = AppRoute.BUDGET,
                        selectTab = {},
                        layouts = listOf(WorkspaceLayout.SINGLE, WorkspaceLayout.TWO),
                        onOpenMenu = {},
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Screen layout: ${WorkspaceLayout.SINGLE.label}")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun renderShellPanes(): WindowSizeInfo {
        lateinit var window: WindowSizeInfo
        composeTestRule.setContent {
            window = currentWindowSizeInfo()
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                Row(Modifier.fillMaxSize()) {
                    if (window.usesRail) Spacer(Modifier.width(TandemLayout.railWidth))
                    Box(Modifier.weight(1f)) {
                        ListDetailPanes(
                            detailKey = "detail",
                            emptyTitle = "Nothing selected",
                            emptyMessage = "Pick an item",
                            list = { Text("List", Modifier.fillMaxSize().testTag(LIST_TAG)) },
                            detail = { Text("Detail") },
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        return window
    }

    private fun assertSinglePane() {
        composeTestRule.onNodeWithTag(LIST_TAG).assertExists()
        composeTestRule.onNodeWithText("Detail").assertDoesNotExist()
    }

    private fun assertTwoPanes(listWidth: Dp) {
        composeTestRule.onNodeWithText("Detail").assertExists()
        composeTestRule.onNodeWithTag(LIST_TAG).assertWidthIsEqualTo(listWidth)
    }

    private companion object {
        const val LIST_TAG = "list"
    }
}
