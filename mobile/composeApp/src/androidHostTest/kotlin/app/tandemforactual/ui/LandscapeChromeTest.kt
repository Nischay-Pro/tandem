package app.tandemforactual.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.tandemforactual.ui.components.MIN_TOUCH_TARGET
import app.tandemforactual.ui.components.PeriodStepper
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
                Column {
                    AppHeader(
                        route = AppRoute.FIRST_SYNC,
                        state = AppState(),
                        showBack = true,
                        onBack = { backRequested = true },
                        onSync = {},
                        syncStatus = MainCloudStatus.OFF,
                        pendingWriteStatuses = emptyList(),
                        onBudget = {},
                        onOpenRoute = {},
                        actualConnected = false,
                        liveAccounts = emptyList(),
                        liveBudgetMonth = null,
                        activitySelectionTotalMinor = null,
                        conflictTitle = null,
                    )
                    PeriodStepper("November 2026", "Previous month", "Next month", {}, {})
                }
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Back")
            .assertWidthIsEqualTo(MIN_TOUCH_TARGET)
            .assertHeightIsEqualTo(MIN_TOUCH_TARGET)
            .performClick()
        composeTestRule
            .onNodeWithContentDescription("Previous month")
            .assertWidthIsEqualTo(MIN_TOUCH_TARGET)
            .assertHeightIsEqualTo(MIN_TOUCH_TARGET)

        assertTrue(backRequested)
    }

    @Test
    fun sideRailExposesAllPrimaryTabsAccessibly() {
        val mode = androidx.compose.runtime.mutableStateOf(InterfaceMode.MINIMAL)
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true, interfaceMode = mode.value) {
                SideNavigationRail(AppRoute.BUDGET, {})
            }
        }

        listOf("Home", "Activity", "Budget", "Settings").forEach { composeTestRule.onNodeWithContentDescription(it).assertExists() }
        composeTestRule.onNodeWithContentDescription("Accounts").assertDoesNotExist()
        composeTestRule.runOnIdle { mode.value = InterfaceMode.ADVANCED }
        listOf(
            "Home",
            "Accounts",
            "Activity",
            "Budget",
            "Reports",
        ).forEach { composeTestRule.onNodeWithContentDescription(it).assertExists() }
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
                    onOpenRoute = {},
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
