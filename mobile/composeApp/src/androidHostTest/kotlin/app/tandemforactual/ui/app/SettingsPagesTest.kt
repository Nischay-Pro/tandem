package app.tandemforactual.ui.app

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SettingsPagesTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val opened = mutableListOf<AppRoute>()

    private fun showSettings(state: AppState = AppState()) {
        val navigation =
            AppNavigation(
                open = { opened += it },
                reset = {},
                replaceTop = {},
                newTransaction = {},
                selectTab = {},
                back = {},
                backToList = {},
            )
        val session = TandemMobileSession(TandemApiClient(), null, { 0L })
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                screenRegistry.getValue(AppRoute.SETTINGS)(sampleScreenContext(AppRoute.SETTINGS, state, session, navigation))
            }
        }
    }

    private fun row(title: String) = composeTestRule.onNode(hasText(title) and hasClickAction(), useUnmergedTree = false)

    @Test
    @Config(qualifiers = WindowPresets.PHONE)
    fun phoneRowsPushAutomationAndConnectedServices() {
        showSettings()

        row("Automation").performScrollTo().performClick()
        row("Connected services").performScrollTo().performClick()

        assertEquals(listOf(AppRoute.AUTOMATION, AppRoute.CONNECTED_SERVICES), opened)
    }

    @Test
    @Config(qualifiers = WindowPresets.LARGE)
    fun wideRowsOpenEachPageBesideTheList() {
        showSettings()

        row("Automation").performScrollTo().performClick()
        composeTestRule.onNodeWithText("What apps can add and see").assertExists()
        row("Automation").assertIsSelected()

        row("Connected services").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Fetch exchange rates").assertExists()
        row("Connected services").assertIsSelected()
        row("Automation").assert(!isSelected())
        assertEquals(emptyList(), opened)

        row("Every service Tandem contacts").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Services Tandem connects to").assertExists()
        row("About, help, and licenses").assertIsSelected()
    }

    @Test
    @Config(qualifiers = WindowPresets.PHONE)
    fun rowSubtitlesFollowTheSwitches() {
        val state = AppState()
        showSettings(state)

        row("Automation").performScrollTo()
        composeTestRule.onAllNodesWithText("Off").assertCountEquals(2)
        composeTestRule.runOnIdle {
            state.automationWritesEnabled = true
            state.bankIconsEnabled = true
        }
        composeTestRule.onNodeWithText("Adding on · Reading off").performScrollTo()
        composeTestRule.onNodeWithText("Bank logos on · Exchange rates off").assertExists()

        composeTestRule.runOnIdle {
            state.automationReadsEnabled = true
            state.exchangeRateFetching = state.exchangeRateFetching.copy(enabled = true)
        }
        composeTestRule.onNodeWithText("Adding on · Reading on").assertExists()
        composeTestRule.onNodeWithText("Bank logos on · Exchange rates on").assertExists()
    }
}
