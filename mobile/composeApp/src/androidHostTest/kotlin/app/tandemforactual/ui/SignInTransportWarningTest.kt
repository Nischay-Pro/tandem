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
class SignInTransportWarningTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun publicHttpWarningIsVisibleForSharedPasswordAndOpenIdSignInScreen() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                SignInTransportWarning("http://budget.example.com")
            }
        }

        composeTestRule
            .onNodeWithText(
                "Data may not be secure over this public HTTP connection. Use HTTPS.",
            ).assertExists()
    }

    @Test
    fun localHttpDoesNotShowThePublicAddressWarning() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                SignInTransportWarning("http://127.0.0.1:19753")
            }
        }

        composeTestRule
            .onNodeWithText("Data may not be secure over this public HTTP connection. Use HTTPS.")
            .assertDoesNotExist()
    }

    @Test
    fun httpsDoesNotShowTheHttpWarning() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                SignInTransportWarning("https://budget.example.com")
            }
        }

        composeTestRule
            .onNodeWithText("Data may not be secure over this public HTTP connection. Use HTTPS.")
            .assertDoesNotExist()
    }
}
