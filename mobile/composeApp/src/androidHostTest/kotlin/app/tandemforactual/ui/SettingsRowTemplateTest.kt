package app.tandemforactual.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsRowTemplateTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun toggleRowUsesTheSharedListHeightAndRemainsClickable() {
        var checked = false
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                ToggleRow("Schedule background sync", "Refresh the budget regularly", checked) {
                    checked = it
                }
            }
        }

        composeTestRule
            .onNodeWithText("Schedule background sync")
            .assertHeightIsAtLeast(TandemLayout.listRowMinHeight)
            .performClick()
        assertTrue(checked)
    }

    @Test
    fun settingsHomeGroupsAppearanceWithoutRepeatingItsLabel() {
        var openedRoute: AppRoute? = null
        val navigation =
            AppNavigation(
                open = { openedRoute = it },
                reset = {},
                replaceTop = {},
                newTransaction = {},
                selectTab = {},
                back = {},
                backToList = {},
            )
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                SettingsScreen(AppState(), navigation)
            }
        }

        composeTestRule.onNodeWithText("Personalization").assertExists()
        composeTestRule.onAllNodesWithText("Appearance").assertCountEquals(1)
        composeTestRule.onNodeWithText("Appearance").performClick()

        assertEquals(AppRoute.APPEARANCE, openedRoute)
    }
}
