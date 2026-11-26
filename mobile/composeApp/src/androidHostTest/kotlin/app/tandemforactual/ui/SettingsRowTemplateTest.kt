package app.tandemforactual.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.feature.settings.SettingsScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
                SettingsScreen(
                    AppState(),
                    navigation,
                    TandemMobileSession(
                        app.tandemforactual.api.client
                            .TandemApiClient(),
                        null,
                        { 0L },
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Personalization").assertExists()
        composeTestRule.onAllNodesWithText("Appearance").assertCountEquals(1)
        composeTestRule.onNodeWithText("Appearance").performClick()

        assertEquals(AppRoute.APPEARANCE, openedRoute)
    }

    @Test
    fun yourDataSectionOpensExportAndBackup() {
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
                SettingsScreen(
                    AppState(),
                    navigation,
                    TandemMobileSession(
                        app.tandemforactual.api.client
                            .TandemApiClient(),
                        null,
                        { 0L },
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Your data").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Automatic backups, recovery and exports").assertExists()
        composeTestRule.onNodeWithText("Export & backup").performScrollTo().performClick()

        assertEquals(AppRoute.EXPORT, openedRoute)
    }
}
