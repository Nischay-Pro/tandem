package app.tandemforactual.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LandscapeChromeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun preparingBudgetHeaderExposesBackAction() {
        var backRequested = false
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                AppHeader(
                    route = AppRoute.FIRST_SYNC,
                    state = AppState(),
                    showBack = true,
                    onBack = { backRequested = true },
                    onSync = {},
                    syncStatus = MainCloudStatus.OFF,
                    pendingWriteStatuses = emptyList(),
                    onBudget = {},
                    actualConnected = false,
                    liveAccounts = emptyList(),
                    liveBudgetMonth = null,
                    activitySelectionTotalMinor = null,
                    conflictTitle = null,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertTrue(backRequested)
    }

    @Test
    fun sideRailExposesAllPrimaryTabsAccessibly() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                SideNavigationRail(AppRoute.BUDGET, {})
            }
        }

        listOf("Budget", "Accounts", "Activity", "Reports", "Settings").forEach { label ->
            composeTestRule.onNodeWithContentDescription(label).assertExists()
        }
    }

    @Test
    fun budgetHeaderAddActionInvokesTransactionCreation() {
        var addRequested = false
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                AppHeader(
                    route = AppRoute.BUDGET,
                    state = AppState(),
                    showBack = false,
                    onBack = {},
                    onSync = {},
                    syncStatus = MainCloudStatus.OFF,
                    pendingWriteStatuses = emptyList(),
                    onBudget = {},
                    actualConnected = true,
                    liveAccounts = emptyList(),
                    liveBudgetMonth = null,
                    activitySelectionTotalMinor = null,
                    conflictTitle = null,
                    compactLandscape = true,
                    onAddTransaction = { addRequested = true },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Add transaction").performClick()

        assertTrue(addRequested)
    }
}
