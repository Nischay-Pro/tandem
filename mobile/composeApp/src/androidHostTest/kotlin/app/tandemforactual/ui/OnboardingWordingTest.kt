package app.tandemforactual.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

        composeTestRule.onNodeWithText("Your budget, your way.").assertExists()
        composeTestRule.onNodeWithText("FOR ACTUAL BUDGET").assertExists()
        composeTestRule.onNodeWithText("Connect to Actual. Keep your finances organized, wherever you are.").assertExists()
        composeTestRule
            .onNodeWithText("How Tandem works")
            .performClick()
        composeTestRule.waitForIdle()
        assertTrue(openedSetupGuide)
        composeTestRule.onAllNodesWithText("Connect to Actual").assertCountEquals(1)
        composeTestRule.onNodeWithText("Connect to Actual").performClick()
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
            .onNodeWithText("Tandem connects straight to your Actual server. Nothing else to install.")
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
    fun firstSyncLeadsToPersonalizationWithoutAskingForNotifications() {
        val state = AppState().apply { selectedBudgetId = "budget-1" }
        val route = mutableStateOf(AppRoute.FIRST_SYNC)
        val navigation = noOpNavigation().copy(open = { route.value = it })
        val syncComplete = mutableStateOf(false)

        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                FirstSyncCompletionAction(state, navigation, syncComplete.value, notificationPermissionEnabled = false, onComplete = {})
            }
        }

        composeTestRule.onNodeWithText("Done").assertIsNotEnabled()
        composeTestRule.runOnIdle { syncComplete.value = true }
        composeTestRule.onNodeWithText("Done").assertIsEnabled().performClick()
        assertEquals(AppRoute.PERSONALIZE, route.value)
        state.updateExperience { it.copy(personalizationDeferred = true) }
        assertEquals(AppRoute.SETUP_COMPLETE, nextSetupRoute(state, notificationPermissionEnabled = false))
    }

    @Test
    fun justConnectingAsksNothingAndLandsOnHome() {
        val state = AppState().apply { selectedBudgetId = "budget-1" }
        var tab: AppRoute? = null
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                PersonalizeScreen(state, noOpNavigation().copy(reset = { tab = it }))
            }
        }

        composeTestRule.onNodeWithText("Want Tandem tailored to you?").assertExists()
        composeTestRule.onNodeWithContentDescription("Step 1 of 5").assertExists()
        composeTestRule.onNodeWithText("Just connect to Actual").performClick()
        composeTestRule.onNodeWithText("Continue").performClick()
        assertEquals(AppRoute.HOME, tab)
        assertEquals(ExperienceSettings(personalizationDeferred = true), state.experience)
    }

    @Test
    fun choosingAllFiveGoalsWalksThroughFocusModeAndTools() {
        val state = AppState().apply { selectedBudgetId = "budget-1" }
        var tab: AppRoute? = null
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                PersonalizeScreen(state, noOpNavigation().copy(reset = { tab = it }))
            }
        }

        composeTestRule.onNodeWithText("Continue").performClick()
        listOf("Stay organized", "Get control of my spending", "Pay down debt", "Save for something", "Analyze my finances")
            .forEach { composeTestRule.onNodeWithText(it).performScrollTo().performClick() }
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("What should Tandem focus on first?").assertExists()
        composeTestRule.onNodeWithText("Recommended").assertExists()
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("Advanced").performClick()
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("Suggested for you").assertExists()
        composeTestRule.onAllNodesWithText("Add")[0].performClick()
        composeTestRule.onNodeWithText("Added").assertExists()
        composeTestRule.onNodeWithText("Open Home").performClick()

        assertEquals(AppRoute.HOME, tab)
        assertEquals(InterfaceMode.ADVANCED, state.interfaceMode)
        val experience = checkNotNull(state.experience)
        assertEquals(FinancialGoal.entries.toSet(), experience.selectedGoals)
        assertEquals(FinancialGoal.ORGANIZE, experience.primaryGoal)
        assertEquals(listOf(HomeModule.REVIEW_INBOX), experience.pinnedModules)
    }

    @Test
    fun oneGoalSkipsTheFocusStep() {
        val state = AppState().apply { selectedBudgetId = "budget-1" }
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) { PersonalizeScreen(state, noOpNavigation()) }
        }
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("Pay down debt").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("How much detail would you like?").assertExists()
        assertEquals(FinancialGoal.DEBT, state.experience?.emphasis)
        composeTestRule.onAllNodesWithContentDescription("Back")[0].performClick()
        composeTestRule.onNodeWithText("What would you like to do with your money?").assertExists()
    }

    @Test
    fun encryptedBudgetPasswordIsRequiredBeforeOpeningAnUncachedBudget() {
        assertTrue(budgetPasswordRequired(isEncrypted = true, hasUnlockedPassword = false))
        assertFalse(budgetPasswordRequired(isEncrypted = true, hasUnlockedPassword = true))
        assertFalse(budgetPasswordRequired(isEncrypted = false, hasUnlockedPassword = false))
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
