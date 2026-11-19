package app.tandemforactual.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
    fun welcomeMatchesTheGettingStartedFlowAndOpensItsActions() {
        var openedRoute: AppRoute? = null
        var openedSetupGuide = false
        val navigation = noOpNavigation().copy(open = { openedRoute = it })
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                WelcomeScreen(navigation, onOpenSetupGuide = { openedSetupGuide = true })
            }
        }

        composeTestRule.onNodeWithText("Your budget.\nEverywhere.").assertExists()
        composeTestRule.onNodeWithText("FOR ACTUAL BUDGET").assertExists()
        composeTestRule.onNodeWithText("Access your Actual Budget anywhere. Make changes offline. Sync when ready.").assertExists()
        composeTestRule
            .onNodeWithText("Need help setting up the API Gateway?")
            .performClick()
        composeTestRule.waitForIdle()
        assertTrue(openedSetupGuide)
        composeTestRule.onAllNodesWithText("Get started").assertCountEquals(1)
        composeTestRule.onNodeWithText("Get started").performClick()
        assertEquals(AppRoute.SERVER_ADDRESS, openedRoute)
    }

    @Test
    fun serverAddressStepHasOneShortInstructionAndOneAction() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                ServerAddressScreen(AppState(), noOpNavigation())
            }
        }

        composeTestRule
            .onNodeWithText("Tandem checks for the Actual API Gateway at /api/v1/.")
            .assertExists()
        composeTestRule.onNodeWithContentDescription("Setup progress. Step 1 of 4: Server.").assertExists()
        composeTestRule.onAllNodesWithText("Continue").assertCountEquals(1)
    }

    @Test
    fun signInWaitsUntilTheServerAddressFieldIsDoneBeforeCheckingTheGateway() {
        assertFalse(shouldCheckActualLoginMethods("https://budget.example.com", serverUrlFocused = true))
        assertTrue(shouldCheckActualLoginMethods("https://budget.example.com", serverUrlFocused = false))
        assertFalse(shouldCheckActualLoginMethods("budget.example.com", serverUrlFocused = false))
    }

    @Test
    fun notificationScreenRequestsPermissionOnlyAfterTap() {
        val state = AppState()
        var permissionRequests = 0
        var openedRoute: AppRoute? = null
        val navigation = noOpNavigation().copy(open = { openedRoute = it })

        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                NotificationOnboardingScreen(state, navigation) { permissionRequests += 1 }
            }
        }

        composeTestRule.onNodeWithText("Stay in the loop.").assertExists()
        composeTestRule.onNodeWithText("Sync issues").assertExists()
        composeTestRule.onNodeWithText("Know when your Actual server needs attention.").assertExists()
        composeTestRule.onNodeWithText("Conflicts").assertExists()
        val cardBounds = composeTestRule.onNodeWithTag("notification-onboarding-info").fetchSemanticsNode().boundsInRoot
        val messageBounds =
            composeTestRule
                .onNodeWithText("Know when your Actual server needs attention.")
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(messageBounds.left - cardBounds.left >= 16f)
        assertTrue(cardBounds.right - messageBounds.right >= 16f)
        assertEquals(0, permissionRequests)
        composeTestRule.onNodeWithText("Allow notifications").performClick()

        assertEquals(1, permissionRequests)
        assertTrue(state.hasCompletedNotificationOnboarding)
        assertEquals(AppRoute.SETUP_COMPLETE, openedRoute)
    }

    @Test
    fun notNowContinuesWithoutRequestingPermissionAndPersistsChoice() {
        val state = AppState()
        var permissionRequests = 0
        var openedRoute: AppRoute? = null
        val navigation = noOpNavigation().copy(open = { openedRoute = it })

        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                NotificationOnboardingScreen(state, navigation) { permissionRequests += 1 }
            }
        }
        composeTestRule.onNodeWithText("Not now").performClick()

        assertEquals(0, permissionRequests)
        assertTrue(state.hasCompletedNotificationOnboarding)
        assertEquals(AppRoute.SETUP_COMPLETE, openedRoute)
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
    fun alreadyDeniedSystemPermissionCanOpenSettingsFromTheAllowAction() {
        val state = AppState()
        var openedSettings = false
        val navigation = noOpNavigation()

        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                NotificationOnboardingScreen(state, navigation) {
                    // Native permission handlers open settings when the system has already denied access.
                    openedSettings = true
                }
            }
        }

        composeTestRule.onNodeWithText("Allow notifications").performClick()

        assertTrue(openedSettings)
        assertTrue(state.hasCompletedNotificationOnboarding)
    }

    @Test
    fun firstSyncSkipsNotificationStepWhenSystemPermissionIsAlreadyEnabled() {
        val state = AppState()
        val route = mutableStateOf(AppRoute.FIRST_SYNC)
        val navigation = noOpNavigation().copy(open = { route.value = it })

        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                FirstSyncCompletionAction(
                    state = state,
                    navigation = navigation,
                    syncComplete = true,
                    notificationPermissionEnabled = true,
                    onComplete = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Done").performClick()

        assertEquals(AppRoute.SETUP_COMPLETE, route.value)
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

        composeTestRule.onNodeWithText("Stay in the loop.").assertExists()
        assertEquals(AppRoute.NOTIFICATION_ONBOARDING, route.value)
        composeTestRule.onNodeWithText("Not now").performClick()

        assertEquals(0, permissionRequests)
        assertTrue(state.hasCompletedNotificationOnboarding)
        assertEquals(AppRoute.SETUP_COMPLETE, route.value)
    }

    @Test
    fun encryptedBudgetPasswordIsRequiredBeforeOpeningAnUncachedBudget() {
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
