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
    fun httpWarningIsVisibleForSharedPasswordAndOpenIdSignInScreen() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                SignInTransportWarning("http://127.0.0.1:19753")
            }
        }

        composeTestRule
            .onNodeWithText(
                "HTTP does not encrypt passwords or session tokens. Use it only on trusted local networks.",
            ).assertExists()
    }

    @Test
    fun httpsDoesNotShowTheHttpWarning() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                SignInTransportWarning("https://budget.example.com")
            }
        }

        composeTestRule
            .onNodeWithText(
                "HTTP does not encrypt passwords or session tokens. Use it only on trusted local networks.",
            ).assertDoesNotExist()
    }
}
