package app.tandemforactual.ui.feature.settings

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.automation_detail
import app.tandemforactual.ui.generated.resources.automation_detail_assistants
import app.tandemforactual.ui.generated.resources.automation_setup_tasker
import app.tandemforactual.ui.generated.resources.automation_setup_tasker_title
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AutomationScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(sdk = [35])
    fun assistantsAreNotMentionedBeforeAndroid16() {
        assertEquals(Res.string.automation_detail, automationDetail())
        assertEquals(Res.string.automation_setup_tasker_title to Res.string.automation_setup_tasker, automationSetup())
    }

    @Test
    @Config(sdk = [37])
    fun assistantsAreMentionedFromAndroid16() {
        assertEquals(Res.string.automation_detail_assistants, automationDetail())
    }

    @Test
    @Config(sdk = [35])
    fun pageTurnsEachAccessOnSeparatelyAndPointsToTasker() {
        val state = AppState()
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) { AutomationScreen(state) }
        }

        composeTestRule.onNodeWithText("Let apps such as Tasker work with the open budget", substring = true).assertExists()
        composeTestRule.onNodeWithText("assistants", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Allow automation apps to add transactions").assertIsOff().performClick()
        assertTrue(state.automationWritesEnabled)
        assertFalse(state.automationReadsEnabled)
        composeTestRule.onNodeWithText("Allow automation apps to read balances").assertIsOff()
        composeTestRule.onNodeWithText("Allow automation apps to add transactions").assertIsOn()
        composeTestRule.onNodeWithText("Set up in Tasker").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Shortcuts", substring = true).assertDoesNotExist()
    }
}
