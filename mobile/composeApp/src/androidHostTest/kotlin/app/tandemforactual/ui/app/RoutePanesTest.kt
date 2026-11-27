@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.app.shell.opensInWorkspacePane
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
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
class RoutePanesTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun supportingPaneNeedsRoomForThePageBesideIt() {
        assertFalse(usesSupportingPane(411f))
        assertFalse(usesSupportingPane(520f))
        assertFalse(usesSupportingPane(759f))
        assertTrue(usesSupportingPane(760f))
        assertTrue(usesSupportingPane(1_120f))
    }

    @Test
    fun paneRoutesOpenBesideTheListAndBackClosesThePane() {
        val pushed = mutableListOf<AppRoute>()
        val shown = mutableListOf<AppRoute>()
        var backs = 0
        var closes = 0
        val navigation =
            AppNavigation(
                open = { pushed += it },
                reset = {},
                replaceTop = {},
                newTransaction = {},
                selectTab = {},
                back = { backs++ },
                backToList = { backs++ },
            )
        val pane = navigation.openingInPane(setOf(AppRoute.APPEARANCE)) { shown += it }.closingPane { closes++ }

        pane.open(AppRoute.APPEARANCE)
        pane.open(AppRoute.MANAGE_PAYEES)
        pane.back()
        pane.backToList()

        assertEquals(listOf(AppRoute.APPEARANCE), shown)
        assertEquals(listOf(AppRoute.MANAGE_PAYEES), pushed)
        assertEquals(0, backs)
        assertEquals(2, closes)
    }

    @Test
    fun workspacePanesKeepPushedScreensButNotWindowFlows() {
        assertTrue(AppRoute.CATEGORY_DETAIL.opensInWorkspacePane())
        assertTrue(AppRoute.SETTINGS.opensInWorkspacePane())
        assertTrue(AppRoute.BUDGET.opensInWorkspacePane())
        assertFalse(AppRoute.SWITCH_BUDGET.opensInWorkspacePane())
        assertFalse(AppRoute.BUDGET_UNLOCK.opensInWorkspacePane())
        assertFalse(AppRoute.HOME_LAYOUT.opensInWorkspacePane())
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun phoneKeepsTheCategoryOnItsOwnPage() {
        renderSupportingPane { "rent" }
        composeTestRule.onNodeWithText("Page fits pane: false").assertExists()
        composeTestRule.onNodeWithTag(PANE_TAG).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w840dp-h1200dp")
    fun expandedWindowShowsTheCategoryBesideTheMonth() {
        renderSupportingPane { "rent" }
        composeTestRule.onNodeWithText("Page fits pane: true").assertExists()
        composeTestRule.onNodeWithTag(PANE_TAG).assertWidthIsEqualTo(TandemLayout.listPaneWidth)
        composeTestRule
            .onNodeWithTag(MAIN_TAG)
            .assertWidthIsEqualTo(840.dp - TandemLayout.railWidth - TandemLayout.listPaneWidth - 1.dp)
    }

    @Test
    @Config(qualifiers = "w1200dp-h800dp")
    fun largeWindowOpensAndClosesTheSupportingPane() {
        var paneKey by mutableStateOf<String?>(null)
        renderSupportingPane { paneKey }
        composeTestRule.onNodeWithTag(PANE_TAG).assertDoesNotExist()

        composeTestRule.runOnIdle { paneKey = "rent" }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PANE_TAG).assertWidthIsEqualTo(TandemLayout.listPaneWidth)

        composeTestRule.runOnIdle { paneKey = null }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PANE_TAG).assertDoesNotExist()
    }

    private fun renderSupportingPane(paneKey: () -> String?) {
        composeTestRule.setContent {
            val window = currentWindowSizeInfo()
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                Row(Modifier.fillMaxSize()) {
                    if (window.usesRail) Spacer(Modifier.width(TandemLayout.railWidth))
                    Box(Modifier.weight(1f)) { SupportingPaneUnderTest(paneKey()) }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Composable
    private fun SupportingPaneUnderTest(paneKey: String?) {
        SupportingPane(
            paneKey = paneKey,
            main = { fits -> Text("Page fits pane: $fits", Modifier.fillMaxSize().testTag(MAIN_TAG)) },
            pane = { Text("Category", Modifier.fillMaxSize().testTag(PANE_TAG)) },
        )
    }

    private companion object {
        const val MAIN_TAG = "main"
        const val PANE_TAG = "pane"
    }
}
