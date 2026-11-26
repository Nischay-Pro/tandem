package app.tandemforactual.ui.design.buttons

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
@Config(sdk = [28])
class PrimaryButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loadingButtonIgnoresTaps() {
        var clicks = 0
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                PrimaryButton("Saving", loading = true) { clicks += 1 }
            }
        }

        composeTestRule.onNodeWithText("Saving").assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun idleButtonStillTakesTaps() {
        var clicks = 0
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                PrimaryButton("Save") { clicks += 1 }
            }
        }

        composeTestRule.onNodeWithText("Save").assertIsEnabled().performClick()

        assertEquals(1, clicks)
    }
}
