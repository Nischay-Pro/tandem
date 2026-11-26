package app.tandemforactual.ui

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.feature.budget.BudgetPreferencesScreen
import app.tandemforactual.ui.feature.budget.BudgetTemplate
import app.tandemforactual.ui.feature.budget.components.BudgetTemplatesSheet
import app.tandemforactual.ui.feature.budget.components.RolloverRow
import app.tandemforactual.ui.feature.budget.encodeBudgetTemplates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BudgetHonestyWordingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun session() = TandemMobileSession(TandemApiClient(), null, { 0L })

    @Test
    fun rolloverToggleDescribesOverspendingRollover() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                RolloverRow(AppState(), session(), LiveCategorySummary("rent", "Rent", 0L, 0L, 0L), rememberCoroutineScope())
            }
        }

        composeTestRule.onNodeWithText("Roll overspending into next month").assertExists()
        composeTestRule
            .onNodeWithText(
                "When this category goes over, the shortfall carries into next month’s balance instead of coming out of money to budget",
            ).assertExists()
        composeTestRule.onAllNodesWithText("Unused money rolls forward").assertCountEquals(0)
    }

    @Test
    fun envelopePreferencesPointToThePerCategoryToggleInsteadOfReadOnly() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                BudgetPreferencesScreen(AppState(), session())
            }
        }

        composeTestRule.onNodeWithText("Roll overspending into next month").assertExists()
        composeTestRule.onNodeWithText("Per category").assertExists()
        composeTestRule.onAllNodesWithText("Read-only").assertCountEquals(0)
    }

    @Test
    fun trackingPreferencesStayReadOnlyBecauseTheToggleIsNotOffered() {
        val state = AppState()
        state.budgetDisplaySettings = BudgetDisplaySettings("USD", "$", 2, null, false, null, false, budgetType = "tracking")
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                BudgetPreferencesScreen(state, session())
            }
        }

        composeTestRule.onNodeWithText("Read-only").assertExists()
        composeTestRule.onAllNodesWithText("Per category").assertCountEquals(0)
    }

    @Test
    fun applyingATemplateOverExistingAmountsAsksFirst() {
        val state = AppState()
        state.selectedBudgetId = "budget"
        state.budgetTemplatesJson =
            encodeBudgetTemplates(listOf(BudgetTemplate("budget", "Summer", listOf("rent" to 125_000L, "fun" to 5_000L))))
        val groups =
            listOf(
                LiveCategoryGroupSummary(
                    "bills",
                    "Bills",
                    listOf(
                        LiveCategorySummary("rent", "Rent", 100_000L, 0L, 100_000L),
                        LiveCategorySummary("fun", "Fun", 0L, 0L, 0L),
                    ),
                ),
            )
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                BudgetTemplatesSheet(true, state, session(), groups) {}
            }
        }

        composeTestRule.onNodeWithText("Apply").performScrollTo().performClick()

        composeTestRule.onNodeWithText("Replace ${state.selectedMonth} amounts?").assertExists()
        composeTestRule.onNodeWithText("1 category already has an amount this month. Applying “Summer” replaces them.").assertExists()
        composeTestRule.onNodeWithText("Keep amounts").performClick()
        composeTestRule.onAllNodesWithText("Replace ${state.selectedMonth} amounts?").assertCountEquals(0)
    }
}
