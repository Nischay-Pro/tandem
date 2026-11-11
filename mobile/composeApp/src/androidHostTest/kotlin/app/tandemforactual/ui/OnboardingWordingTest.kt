package app.tandemforactual.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OnboardingWordingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun welcomeExplainsBenefitsAndShowsTheSetupSteps() {
        var openedRoute: AppRoute? = null
        val navigation = noOpNavigation().copy(open = { openedRoute = it })
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                WelcomeScreen(navigation)
            }
        }

        composeTestRule.onNodeWithText("Use your budget with confidence.").assertExists()
        composeTestRule.onNodeWithText("Use your budget offline").assertExists()
        composeTestRule.onNodeWithText("Edits wait on this device until they sync").assertExists()
        composeTestRule.onNodeWithText("Nothing is lost silently").assertExists()
        listOf("Server", "Sign in", "Budget", "First sync").forEach { step ->
            composeTestRule.onNodeWithText(step).assertExists()
        }
        composeTestRule.onNodeWithContentDescription("Step 1 of 4: Server").assertExists()
        composeTestRule.onNodeWithText("Before you start").assertDoesNotExist()
        composeTestRule.onNodeWithText("How to set up a server").assertDoesNotExist()
        composeTestRule.onAllNodesWithText("Get started").assertCountEquals(1)
        composeTestRule.onNodeWithText("Get started").performClick()
        assertEquals(AppRoute.SIGN_IN, openedRoute)
    }

    @Test
    fun serverAddressStepHasOneShortInstructionAndOneAction() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                ServerAddressScreen(AppState(), noOpNavigation())
            }
        }

        composeTestRule.onNodeWithText("Enter the web address for your Tandem server.").assertExists()
        composeTestRule.onAllNodesWithText("Continue").assertCountEquals(1)
    }

    @Test
    fun notificationScreenRequestsPermissionOnlyAfterTap() {
        val state = AppState()
        var permissionRequests = 0
        var selectedTab: AppRoute? = null
        val navigation = noOpNavigation().copy(selectTab = { selectedTab = it })

        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                NotificationOnboardingScreen(state, navigation) { permissionRequests += 1 }
            }
        }

        composeTestRule.onNodeWithText("Stay in the loop").assertExists()
        composeTestRule
            .onNodeWithText("Tandem can alert you when an edit needs review, is rejected, or has an unknown status.")
            .assertExists()
        composeTestRule.onNodeWithText("You can change this later in Settings.").assertExists()
        assertEquals(0, permissionRequests)
        composeTestRule.onNodeWithText("Allow notifications").performClick()

        assertEquals(1, permissionRequests)
        assertTrue(state.hasCompletedNotificationOnboarding)
        assertEquals(AppRoute.BUDGET, selectedTab)
    }

    @Test
    fun notNowContinuesWithoutRequestingPermissionAndPersistsChoice() {
        val state = AppState()
        var permissionRequests = 0
        var selectedTab: AppRoute? = null
        val navigation = noOpNavigation().copy(selectTab = { selectedTab = it })

        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                NotificationOnboardingScreen(state, navigation) { permissionRequests += 1 }
            }
        }
        composeTestRule.onNodeWithText("Not now").performClick()

        assertEquals(0, permissionRequests)
        assertTrue(state.hasCompletedNotificationOnboarding)
        assertEquals(AppRoute.BUDGET, selectedTab)
        val restoredState = AppState().apply { restorePersistentSettings(state.persistentSettings()) }
        assertTrue(restoredState.hasCompletedNotificationOnboarding)
    }

    @Test
    fun notificationPermissionOrPreviousChoiceSkipsOnboarding() {
        assertFalse(shouldShowNotificationOnboarding(alreadyCompleted = false, permissionEnabled = true))
        assertFalse(shouldShowNotificationOnboarding(alreadyCompleted = true, permissionEnabled = false))
        assertTrue(shouldShowNotificationOnboarding(alreadyCompleted = false, permissionEnabled = false))
        assertTrue(shouldShowNotificationOnboarding(alreadyCompleted = false, permissionEnabled = null))
    }

    @Test
    fun firstSyncDoneOpensOptionalNotificationStepAndCanBeSkipped() {
        val state = AppState()
        val route = mutableStateOf(AppRoute.FIRST_SYNC)
        val syncComplete = mutableStateOf(false)
        var permissionRequests = 0
        val navigation =
            noOpNavigation().copy(
                open = { route.value = it },
                selectTab = { route.value = it },
            )

        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                when (route.value) {
                    AppRoute.FIRST_SYNC ->
                        FirstSyncCompletionAction(
                            state = state,
                            navigation = navigation,
                            syncComplete = syncComplete.value,
                            notificationPermissionEnabled = false,
                            onComplete = {},
                        )
                    AppRoute.NOTIFICATION_ONBOARDING ->
                        NotificationOnboardingScreen(state, navigation) { permissionRequests += 1 }
                    else -> Unit
                }
            }
        }

        composeTestRule.onNodeWithText("Done").assertIsNotEnabled()
        composeTestRule.runOnIdle { syncComplete.value = true }
        composeTestRule.onNodeWithText("Done").assertIsEnabled().performClick()

        composeTestRule.onNodeWithText("Stay in the loop").assertExists()
        assertEquals(AppRoute.NOTIFICATION_ONBOARDING, route.value)
        composeTestRule.onNodeWithText("Not now").performClick()

        assertEquals(0, permissionRequests)
        assertTrue(state.hasCompletedNotificationOnboarding)
        assertEquals(AppRoute.BUDGET, route.value)
    }

    @Test
    fun encryptedBudgetPasswordStaysOnTheChooserStep() {
        assertTrue(budgetPasswordRequired(isEncrypted = true, hasCachedSnapshot = false))
        assertFalse(budgetPasswordRequired(isEncrypted = true, hasCachedSnapshot = true))
        assertFalse(budgetPasswordRequired(isEncrypted = false, hasCachedSnapshot = false))
        assertFalse(
            budgetOpenButtonEnabled(
                selectedBudgetAvailable = true,
                loadingBudgets = false,
                hasCachedSnapshot = false,
                passwordRequired = true,
                budgetPassword = "",
            ),
        )
        assertTrue(
            budgetOpenButtonEnabled(
                selectedBudgetAvailable = true,
                loadingBudgets = false,
                hasCachedSnapshot = false,
                passwordRequired = true,
                budgetPassword = "budget password",
            ),
        )
    }

    private fun noOpNavigation() =
        AppNavigation(
            open = {},
            reset = {},
            replaceTop = {},
            newTransaction = {},
            selectTab = {},
            back = {},
            backToList = {},
        )
}
