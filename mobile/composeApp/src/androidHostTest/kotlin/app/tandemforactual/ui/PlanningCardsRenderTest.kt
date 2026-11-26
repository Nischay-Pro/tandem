package app.tandemforactual.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.DebtTerms
import app.tandemforactual.data.planning.PlanningMath
import app.tandemforactual.data.planning.SavingsGoal
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.ui.DebtOverviewCard
import app.tandemforactual.ui.core.ui.SavingsGoalSheet
import app.tandemforactual.ui.core.ui.SavingsGoalsCard
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.tandemPalette
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlanningCardsRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val card = LiveAccountSummary("card", "Credit card", -43_599, null, false)
    private val month =
        LiveBudgetMonthSummary(
            0,
            0,
            0,
            0,
            listOf(LiveCategoryGroupSummary("g", "Savings", listOf(LiveCategorySummary("cat", "Holiday", 0, 0, 42_000)))),
        )

    @Test
    fun cardsShowEstimatesOnlyFromEnteredTerms() {
        var edited: LiveAccountSummary? = null
        val estimate = checkNotNull(PlanningMath.payoff(43_599, 1_999, 3_500))
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                Column(Modifier.fillMaxSize().background(tandemPalette.background).padding(16.dp)) {
                    DebtOverviewCard(
                        listOf(card, card.copy(id = "loan", name = "Car loan", balanceMinor = -900_000)),
                        listOf(DebtTerms("t", "card", 1_999, 3_500, 12)),
                        null,
                        privacy = false,
                        money = { Text(formatCurrency(it)) },
                    ) { edited = it }
                    SavingsGoalsCard(
                        listOf(
                            SavingsGoal("g1", "Holiday", 100_000, "USD", "2027-06-01", categoryId = "cat"),
                            SavingsGoal("g2", "New laptop", 150_000, "USD"),
                        ),
                        month,
                        emptyList(),
                        null,
                        "2026-11-21",
                        privacy = false,
                    ) {}
                }
            }
        }
        composeTestRule.onNodeWithText("Estimate: ${estimate.months} months at the minimum", substring = true).assertExists()
        composeTestRule.onNodeWithText("Add the APR and minimum payment to estimate payoff").performClick()
        assertEquals("loan", edited?.id)
        composeTestRule.onNodeWithText("Link a category or account to track progress").assertExists()
        composeTestRule.onNodeWithText("about", substring = true).assertExists()
        File("build/reports/planning-cards.png").also { it.parentFile.mkdirs() }.outputStream().use {
            composeTestRule
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun goalSheetValidatesAndSaves() {
        var saved: SavingsGoal? = null
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                SavingsGoalSheet(null, visible = true, month, null, onSave = { saved = it }, onDelete = {}, onDismiss = {})
            }
        }
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.onNodeWithText("Enter a name.").assertExists()
        File("build/reports/planning-goal-sheet.png").outputStream().use {
            composeTestRule
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        assertEquals(null, saved)
    }
}
