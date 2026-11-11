package app.tandemforactual.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OnboardingWordingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun welcomeUsesOneShortInstructionAndOnePrimaryAction() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                WelcomeScreen(noOpNavigation())
            }
        }

        composeTestRule.onNodeWithText("Have your Tandem server address ready.").assertExists()
        composeTestRule.onNodeWithText("Before you start").assertDoesNotExist()
        composeTestRule.onNodeWithText("How to set up a server").assertDoesNotExist()
        composeTestRule.onAllNodesWithText("Get started").assertCountEquals(1)
    }

    @Test
    fun serverAddressStepHasOneShortInstructionAndOneAction() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                ServerAddressScreen(AppState(), noOpNavigation())
            }
        }

        composeTestRule.onNodeWithText("Enter the web address for your Tandem server.").assertExists()
        composeTestRule.onAllNodesWithText("Continue").assertCountEquals(1)
    }

    private fun noOpNavigation() =
        AppNavigation(
            open = {},
            reset = {},
            replaceTop = {},
            newTransaction = {},
            selectTab = {},
            back = {},
            backToList = {},
        )
}
