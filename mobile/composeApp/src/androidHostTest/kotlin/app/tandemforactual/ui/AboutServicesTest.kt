package app.tandemforactual.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.feature.about.AboutScreen
import app.tandemforactual.ui.feature.about.connectedServices
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AboutServicesTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun aboutListsEveryServiceTandemConnectsTo() {
        val navigation =
            AppNavigation(open = {}, reset = {}, replaceTop = {}, newTransaction = {}, selectTab = {}, back = {}, backToList = {})
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                AboutScreen(null, navigation)
            }
        }

        assertEquals(
            listOf("Your Actual server", "Frankfurter", "Bank websites", "Your WebDAV server", "Pages in your browser"),
            connectedServices.map { it.name },
        )
        composeTestRule.onNodeWithText("Services Tandem connects to").performScrollTo()
        connectedServices.forEach { service ->
            composeTestRule.onNodeWithText(service.name).performScrollTo()
            composeTestRule.onNodeWithText(service.detail).assertExists()
        }
        composeTestRule.onNodeWithText("Only currency codes and dates are sent", substring = true).assertExists()
        composeTestRule.onNodeWithText("nothing about your budget", substring = true).assertExists()
    }
}
