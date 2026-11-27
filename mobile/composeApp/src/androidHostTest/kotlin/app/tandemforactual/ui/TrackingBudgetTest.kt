package app.tandemforactual.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.isTrackingBudget
import app.tandemforactual.ui.core.state.trackingMonthFigures
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.feature.budget.BudgetPreferencesScreen
import app.tandemforactual.ui.feature.budget.budgetTypeSwitchChanges
import app.tandemforactual.ui.feature.budget.categoryHero
import app.tandemforactual.ui.feature.budget.components.TrackingMonthSummaryCard
import app.tandemforactual.ui.feature.budget.components.budgetBalanceWord
import app.tandemforactual.ui.feature.budget.components.budgetCategoryWords
import app.tandemforactual.ui.feature.budget.components.budgetGroupHeadingAmount
import app.tandemforactual.ui.feature.budget.components.categoryGroupHero
import app.tandemforactual.ui.feature.budget.components.categoryPlanTitle
import app.tandemforactual.ui.feature.budget.components.categoryRowSummary
import app.tandemforactual.ui.feature.budget.components.savedAgainstPlanLabel
import app.tandemforactual.ui.feature.budget.components.savedLabel
import app.tandemforactual.ui.feature.home.homeBudgetFigure
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackingBudgetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val salary = LiveCategorySummary("salary", "Salary", 500_000, 450_000, 50_000)
    private val rent = LiveCategorySummary("rent", "Rent", 200_000, -200_000, 0)
    private val food = LiveCategorySummary("food", "Food", 50_000, -65_000, -15_000)
    private val income = LiveCategoryGroupSummary("income", "Income", listOf(salary), isIncome = true)
    private val living = LiveCategoryGroupSummary("living", "Living", listOf(rent, food))
    private val format: (Long) -> String = { "$it" }

    private fun trackingState() =
        AppState().apply {
            selectedBudgetId = "budget"
            budgetDisplaySettings = BudgetDisplaySettings("USD", "$", 2, null, false, null, false, budgetType = "tracking")
        }

    @Test
    fun monthFiguresFollowActualsTrackingSummary() {
        val figures = trackingMonthFigures(listOf(income, living))

        assertEquals(500_000, figures.expectedIncomeMinor)
        assertEquals(450_000, figures.receivedIncomeMinor)
        assertEquals(250_000, figures.budgetedMinor)
        assertEquals(265_000, figures.spentMinor)
        assertEquals(250_000, figures.plannedSavingsMinor)
        assertEquals(185_000, figures.savedMinor)
        assertEquals(-65_000, figures.savedAgainstPlanMinor)
        assertEquals(1, figures.overspentCategories)
        assertEquals(15_000, figures.overspentMinor)
        assertEquals("Saved", savedLabel(figures))
        assertEquals("Plan 250000 · 65000 behind", savedAgainstPlanLabel(figures, format))
        val windfall = income.copy(categories = listOf(salary.copy(assignedMinor = 0, activityMinor = 10)))
        assertEquals("Plan 0 · 10 ahead", savedAgainstPlanLabel(trackingMonthFigures(listOf(windfall)), format))
        assertEquals("Plan 0 · on plan", savedAgainstPlanLabel(trackingMonthFigures(emptyList()), format))
        assertEquals("Overspent", savedLabel(trackingMonthFigures(listOf(living))))
    }

    @Test
    fun wordingFollowsTheBudgetTypeAndCategoryKind() {
        assertEquals("Budgeted", budgetCategoryWords(isIncome = false, isTrackingBudget = true).plan)
        assertEquals("remaining", budgetCategoryWords(isIncome = false, isTrackingBudget = true).left)
        assertEquals("Assigned", budgetCategoryWords(isIncome = false, isTrackingBudget = false).plan)
        assertEquals("Expected", budgetCategoryWords(isIncome = true, isTrackingBudget = true).plan)
        assertNull(budgetCategoryWords(isIncome = true, isTrackingBudget = false).plan)
        assertEquals("Received", budgetBalanceWord(isIncome = true, isTrackingBudget = true))
        assertEquals("Remaining", budgetBalanceWord(isIncome = false, isTrackingBudget = true))
        assertEquals("Available", budgetBalanceWord(isIncome = false, isTrackingBudget = false))
        assertEquals(500_000L to "expected", budgetGroupHeadingAmount(income, isTrackingBudget = true))
        assertNull(budgetGroupHeadingAmount(income, isTrackingBudget = false))
        assertEquals(250_000L to "budgeted", budgetGroupHeadingAmount(living, isTrackingBudget = true))
        assertEquals("Expected", categoryPlanTitle(isIncome = true, isTrackingBudget = true))
        assertNull(categoryPlanTitle(isIncome = true, isTrackingBudget = false))
        assertEquals("Budgeted", categoryPlanTitle(isIncome = false, isTrackingBudget = true))
        assertEquals(
            "Received 450000 of 500000 expected",
            categoryRowSummary(salary, budgetCategoryWords(isIncome = true, isTrackingBudget = true), format),
        )
        assertEquals(
            "Spent 65000 · -15000 remaining",
            categoryRowSummary(food, budgetCategoryWords(isIncome = false, isTrackingBudget = true), format),
        )
        assertEquals(Triple("Received this month", 450_000L, "500000 expected"), categoryGroupHero(income, true, format))
        assertEquals(Triple("Budgeted this month", 250_000L, "-15000 remaining"), categoryGroupHero(living, true, format))
        assertEquals(Triple("Remaining", -15_000L, "50000 budgeted"), categoryHero(food, false, true, format))
        assertEquals(Triple("Received", 450_000L, "500000 expected"), categoryHero(salary, true, true, format))
    }

    @Test
    fun homeShowsSavingsForTrackingBudgets() {
        val month = LiveBudgetMonthSummary(null, null, 250_000, -265_000, listOf(income, living))

        assertEquals("Saved" to 185_000L, homeBudgetFigure(isTrackingBudget = true, month))
        assertEquals("To budget" to 0L, homeBudgetFigure(isTrackingBudget = false, month))
        assertTrue(trackingState().isTrackingBudget)
        assertFalse(AppState().isTrackingBudget)
    }

    @Test
    fun summaryCardShowsIncomeSpendingAndSavingsWithoutEnvelopeWords() {
        val state = trackingState()
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                TrackingMonthSummaryCard(state, "summary", listOf(income, living), 0, 16.dp)
            }
        }

        composeTestRule.onNodeWithText("Income").assertExists()
        composeTestRule.onNodeWithText("Spending").assertExists()
        composeTestRule.onNodeWithText("Saved").assertExists()
        composeTestRule.onNodeWithText("Over budget").assertExists()
        composeTestRule.onNodeWithText("1 category").assertExists()
        composeTestRule.onAllNodesWithText("To budget").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Hold for next month").assertCountEquals(0)
    }

    @Test
    fun trackingPreferencesOfferRolloverAndAConfirmedSwitch() {
        val state = trackingState()
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                BudgetPreferencesScreen(state, TandemMobileSession(TandemApiClient(), null, { 0L }))
            }
        }

        composeTestRule.onNodeWithText("Roll the balance into next month").assertExists()
        composeTestRule.onNodeWithText("Per category").assertExists()
        composeTestRule.onAllNodesWithText("Read-only").assertCountEquals(0)
        composeTestRule.onNodeWithText("Envelope").performClick()
        composeTestRule.onNodeWithText("Switch to envelope?").assertExists()
        composeTestRule.onNodeWithText(budgetTypeSwitchChanges(toTracking = false)).assertExists()
        composeTestRule.onNodeWithText("Keep tracking").performClick()
        composeTestRule.onAllNodesWithText("Switch to envelope?").assertCountEquals(0)
    }
}
