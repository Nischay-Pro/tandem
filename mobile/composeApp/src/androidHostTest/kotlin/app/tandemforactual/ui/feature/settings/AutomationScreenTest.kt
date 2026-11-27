package app.tandemforactual.ui.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.automation_intro
import app.tandemforactual.ui.generated.resources.automation_intro_assistants
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

    private fun show(state: AppState) {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) { AutomationScreen(state) }
        }
    }

    @Test
    @Config(sdk = [35])
    fun assistantsAreNotMentionedBeforeAndroid16() {
        assertEquals(Res.string.automation_intro, automationIntro())
        assertEquals(Res.string.automation_setup_tasker_title, automationSetupTitle())
    }

    @Test
    @Config(sdk = [37])
    fun assistantsAreMentionedFromAndroid16() {
        assertEquals(Res.string.automation_intro_assistants, automationIntro())
    }

    @Test
    @Config(sdk = [35])
    fun pageTurnsEachAccessOnSeparatelyAndPointsToTasker() {
        val state = AppState()
        show(state)

        composeTestRule.onNodeWithText("Let apps like Tasker add transactions or read your budget. Both start off.").assertExists()
        composeTestRule.onNodeWithText("assistants", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Encrypted budget", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Add transactions").assertIsOff().performClick()
        assertTrue(state.automationWritesEnabled)
        assertFalse(state.automationReadsEnabled)
        composeTestRule.onNodeWithText("Read balances").assertIsOff()
        composeTestRule.onNodeWithText("Add transactions").assertIsOn()
        composeTestRule.onNodeWithText("Shortcuts", substring = true).assertDoesNotExist()

        composeTestRule.onNodeWithText("Set up in Tasker").performScrollTo().performClick()
        composeTestRule.onNodeWithText("In Tasker, add a Plugin action, event or state").assertExists()
    }

    @Test
    @Config(sdk = [35])
    fun exposureOpensAsAShortList() {
        show(AppState())

        composeTestRule.onNodeWithText("Nothing can be changed or deleted").assertDoesNotExist()
        composeTestRule.onNodeWithText("What apps can add and see").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Nothing can be changed or deleted").assertExists()
        composeTestRule.onNodeWithText("No transactions, payees or notes").assertExists()
        composeTestRule
            .onNodeWithText("Work only while Schedule background sync keeps the password on this device")
            .assertExists()
    }

    @Test
    @Config(sdk = [35])
    fun encryptedBudgetWithoutAKeptPasswordSaysWhyUnderEachSwitch() {
        show(
            AppState().apply {
                selectedBudgetId = "budget-1"
                selectedBudgetEncrypted = true
            },
        )

        composeTestRule.onAllNodesWithText("Encrypted budget: needs Schedule background sync").assertCountEquals(2)
        composeTestRule.onNodeWithText("Log new transactions from other apps").assertDoesNotExist()
    }
}
