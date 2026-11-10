package app.tandemforactual.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UncertainWriteRecoveryActionsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun successfulUnsettledStatusShowsRetryAndRemoveActions() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                UncertainWriteRecoveryActions(
                    showCheckAgain = false,
                    showRetryAndRemove = true,
                    isSyncing = false,
                    readOnly = false,
                    onCheckAgain = {},
                    onRetry = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Try again").assertExists()
        composeTestRule.onNodeWithText("Remove edit").assertExists()
    }

    @Test
    fun failedStatusCheckShowsOnlyCheckAgain() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                UncertainWriteRecoveryActions(
                    showCheckAgain = true,
                    showRetryAndRemove = false,
                    isSyncing = false,
                    readOnly = false,
                    onCheckAgain = {},
                    onRetry = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Check again").assertExists()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
        composeTestRule.onNodeWithText("Remove edit").assertDoesNotExist()
    }
}
