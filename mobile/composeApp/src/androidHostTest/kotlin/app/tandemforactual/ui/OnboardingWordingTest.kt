package app.tandemforactual.ui

import androidx.compose.ui.test.assertCountEquals
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
        listOf("Connect", "Budget", "Sync").forEach { step ->
            composeTestRule.onNodeWithText(step).assertExists()
        }
        composeTestRule.onNodeWithContentDescription("Step 1 of 3: Connect").assertExists()
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
    fun notificationPromptAppearsInlineOnlyAfterSuccessfulFirstSync() {
        val state = AppState()
        var permissionRequests = 0

        assertFalse(shouldShowFirstSyncNotificationPrompt(syncComplete = false, alreadyCompleted = false, permissionEnabled = false))
        assertTrue(shouldShowFirstSyncNotificationPrompt(syncComplete = true, alreadyCompleted = false, permissionEnabled = false))

        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                FirstSyncNotificationPrompt(
                    onEnable = { finishNotificationOnboarding(state) { permissionRequests += 1 } },
                    onNotNow = { finishNotificationOnboarding(state, onRequestPermission = null) },
                )
            }
        }

        assertEquals(0, permissionRequests)
        composeTestRule.onNodeWithText("Notifications").assertExists()
        composeTestRule.onNodeWithText("Get alerts when a change needs your attention.").assertExists()
        composeTestRule.onNodeWithText("Turn on").performClick()

        assertEquals(1, permissionRequests)
        assertTrue(state.hasCompletedNotificationOnboarding)
    }

    @Test
    fun notNowContinuesWithoutRequestingPermissionAndPersistsChoice() {
        val state = AppState()
        var permissionRequests = 0

        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                FirstSyncNotificationPrompt(
                    onEnable = { finishNotificationOnboarding(state) { permissionRequests += 1 } },
                    onNotNow = { finishNotificationOnboarding(state, onRequestPermission = null) },
                )
            }
        }
        composeTestRule.onNodeWithText("Not now").performClick()

        assertEquals(0, permissionRequests)
        assertTrue(state.hasCompletedNotificationOnboarding)
        val restoredState = AppState().apply { restorePersistentSettings(state.persistentSettings()) }
        assertTrue(restoredState.hasCompletedNotificationOnboarding)
    }

    @Test
    fun notificationPermissionOrPreviousChoiceSkipsOnboarding() {
        assertFalse(shouldShowFirstSyncNotificationPrompt(syncComplete = true, alreadyCompleted = false, permissionEnabled = true))
        assertFalse(shouldShowFirstSyncNotificationPrompt(syncComplete = true, alreadyCompleted = true, permissionEnabled = false))
        assertTrue(shouldShowFirstSyncNotificationPrompt(syncComplete = true, alreadyCompleted = false, permissionEnabled = null))
        assertFalse(firstSyncCanFinish(syncComplete = true, alreadyCompleted = false, permissionEnabled = false))
        assertTrue(firstSyncCanFinish(syncComplete = true, alreadyCompleted = true, permissionEnabled = false))
        assertTrue(firstSyncCanFinish(syncComplete = true, alreadyCompleted = false, permissionEnabled = true))
        assertFalse(firstSyncCanFinish(syncComplete = false, alreadyCompleted = true, permissionEnabled = true))
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
