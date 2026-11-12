package app.tandemforactual.ui.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tandemforactual.ui.AccentChoice
import app.tandemforactual.ui.TandemTheme
import app.tandemforactual.ui.ThemeMode
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DestructiveConfirmationDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun requiredChoiceDisablesConfirmButKeepsCancelEnabled() {
        var dismissCount = 0
        var confirmed = false
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                DestructiveConfirmationDialog(
                    title = "Close account?",
                    message = "Choose where to transfer the balance.",
                    confirmLabel = "Close account",
                    dismissLabel = "Cancel",
                    enabled = false,
                    dismissEnabled = true,
                    onDismissRequest = { dismissCount += 1 },
                    onConfirm = { confirmed = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Close account").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Cancel").assertIsEnabled().performClick()

        assertEquals(1, dismissCount)
        assertFalse(confirmed)
    }

    @Test
    fun closeIconRemainsEnabledWhenConfirmIsUnavailable() {
        var dismissCount = 0
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                DestructiveConfirmationDialog(
                    title = "Close account?",
                    message = "Choose where to transfer the balance.",
                    confirmLabel = "Close account",
                    dismissLabel = "Cancel",
                    enabled = false,
                    dismissEnabled = true,
                    onDismissRequest = { dismissCount += 1 },
                    onConfirm = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Close").assertIsEnabled().performClick()

        assertEquals(1, dismissCount)
    }
}
